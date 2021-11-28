(ns xapi.ghost
  (:import [java.time Instant LocalDate]
           [java.util.zip Adler32]
           [java.time ZonedDateTime]
           [java.time.format DateTimeFormatter])
  (:require [cognitect.aws.client.api :as aws]
            [clojure.java.io :as io]
            [org.httpkit.client :as http]
            [hiccup2.core :as hi]

            [xapi.core :as core]
            [xapi.core.db :as db]
            [xapi.core.idenc :as idenc]
            [xapi.auth :as auth]
            [xapi.log :as log]
            [xapi.config :as config]
            [cheshire.core :as json]
            [clojure.string :as str]))


(set! *warn-on-reflection* true)


(def REQ-LOG [:request-method :uri :query-string :headers :body])
(def BASE "resources/public/")
(def s3 (aws/client {:api               :s3
                     :region            "us-east-1"
                     :endpoint-override {:hostname "s3.wasabisys.com"}}))


;;; Utils

(defn make-tag [tag]
  {:slug tag
   :name tag})


(defn parse-dt [s]
  (-> (ZonedDateTime/parse s DateTimeFormatter/ISO_DATE_TIME)
      .toInstant))


(defn store-log! [log-table data]
  (db/q {:insert-into log-table
         :values      [(assoc data :user_id (auth/uid))]})
  (db/q {:delete-from log-table
         :where       [:in :id {:from     [log-table]
                                :select   [:id]
                                :where    [:= :user_id (auth/uid)]
                                :order-by [[:id :desc]]
                                :offset   50}]}))


(defn send-webhooks! [post]
  (assert (:url post))
  (let [hooks    (db/q {:from   [:webhook]
                        :select [:id :type :url :headers]
                        :where  [:and
                                 [:= :user_id (auth/uid)]
                                 :enabled]})
        user     (auth/user)
        post-url (str "https://" (config/DOMAIN) (:url post))
        req      {:method  :post
                  :headers {"Accept"        "application/vnd.github.v3+json"
                            "Authorization" (str "token " (:access_token user))}
                  :body    (json/generate-string
                             {:event_type     "xapicms"
                              :client_payload {:github       (:github user)
                                               :slug         (:slug post)
                                               :url          post-url
                                               :draft        (= "draft" (:status post))
                                               :published_at (:published_at post)}})}]
    (doseq [hook hooks]
      (let [dest (case (:type hook)
                   "github"
                   (format "https://api.github.com/repos/%s/%s/dispatches"
                     (:github user) (:url hook))

                   "url"
                   (:url hook))
            res  @(http/request (assoc req :url dest))]
        (store-log! :webhook_log
          {:webhook_id (:id hook)
           :request    [:lift (select-keys req REQ-LOG)]
           :response   [:lift (core/update-some res :error pr-str)]})
        (log/info "sent webhook" (-> (select-keys res [:status :body :error])
                                     (assoc :url (-> res :opts :url))))))))


;;; Data/Queries

(defn make-dbpost [post]
  (let [title (not-empty (:title post))]
    {:user_id       (auth/uid)
     :slug          (or (not-empty (:slug post))
                        (some-> title core/slug))
     :html          (:html post)
     :title         title
     :tags          (when (seq (:tags post))
                      [:array (:tags post)])
     :status        (:status post)
     :updated_at    (or (some-> (:updated_at post) parse-dt)
                        (Instant/now))
     :published_at  (some-> (:published_at post) parse-dt)
     :feature_image (:feature_image post)}))


(def DBPOST-KEYS (delay (concat [:id :slug :uuid] (keys (make-dbpost nil)))))


(defn upsert-image-q [post]
  {:insert-into   :images
   :values        [{:id         (:id post)
                    :path       (:path post)
                    :hash       (:hash post)
                    :user_id    (auth/uid)
                    :updated_at (Instant/now)}]
   :on-conflict   [:user_id :id]
   :do-update-set [:updated_at :hash :path]})


(defn get-post-q [id]
  {:from   [:posts]
   :select @DBPOST-KEYS
   :where  [:and
            [:= :user_id (auth/uid)]
            [:or
             [:= :id id]
             [:= :slug id]]]})


;;; Resources

(defn login [req]
  (let [{:keys [username password]} (:body req)
        user (auth/by-email username password)]
    (if user
      {:status  201
       :headers {"set-cookie" (format "ghost-admin-api-session=%s; Path=/ghost; Expires=Mon, 26 Aug 2119 19:14:07 GMT; SameSite=Lax"
                                (auth/map->b64 {:user_id (:id user)}))}}
      {:status 403
       :body "Access Denied"})))


(defn site [_req]
  (let [user (auth/user)]
    {:status 200
     :body   {:site
              {:title (str "Blog of " (:name user))}}}))


(defn me [_req]
  (let [user (auth/user)]
    {:status 200
     :body   {:users [{:id   (str (:id user))
                       :name (:name user)
                       :slug (:github user)}]}}))

(defn tags [_req]
  (let [tags (db/q {:from            [:posts]
                    :select-distinct [[:%unnest.tags :tag]]
                    :where           [:= :user_id (auth/uid)]})]
    {:status 200
     :body   {:tags (mapv #(make-tag (:tag %)) tags)}}))


(defn get-file-or-name [orig hashsum]
  (if-let [file (db/one {:from   [:images]
                         :select [:id
                                  :path
                                  :hash]
                         :where  [:and
                                  [:= :user_id (auth/uid)]
                                  [:= :id orig]]})]
    file
    {:id   orig
     :hash hashsum
     :path (format "images/%s/%s/%s/%s.%s"
             (idenc/encode (auth/uid))
             (.getYear (LocalDate/now))
             (.getMonthValue (LocalDate/now))
             hashsum
             orig)}))


(defn upload-image [{:keys [params]}]
  (let [file    (:file params)
        ba      (-> (:tempfile file) io/input-stream .readAllBytes)
        hashsum (-> (doto (Adler32.)
                      (.update ba))
                    .getValue
                    str)
        record  (get-file-or-name (:filename file) hashsum)]
    (when (not= hashsum (:hash record))
      (let [res (aws/invoke s3 {:op      :PutObject
                                :request {:Bucket      "xapi"
                                          :Key         (:path record)
                                          :ACL         "public-read"
                                          :ContentType (:content-type file)
                                          :Body        ba}})]
        (log/info "file upload" {:key (:path record) :res res}))
      (db/q (upsert-image-q record)))
    {:status 200
     :body   {:images [{:url (str "https://images.solovyov.net/" (:path record))}]}}))


(defn dbres->post [post]
  (-> post
      (update :tags #(mapv make-tag %))
      (dissoc :user_id)
      (assoc :url (format "/posts/%s/%s"
                    (idenc/encode (:user_id post))
                    (:slug post)))))


(defn upload-post [req]
  (store-log! :post_log {:request [:lift (select-keys req REQ-LOG)]})

  (let [input   (-> req :body :posts first)
        current (or (db/one (get-post-q (:slug input)))
                    (let [uuid (core/uuid)]
                      {:id   uuid
                       :uuid uuid
                       :slug uuid}))
        dbpost  (merge current (make-dbpost input))
        res     (db/one {:insert-into   :posts
                         :values        [dbpost]
                         :on-conflict   [:user_id :id]
                         :do-update-set (keys dbpost)
                         :returning     (keys dbpost)})
        post    (dbres->post res)]
    (future (send-webhooks! post))
    {:status 200
     :body   {:posts [post]}}))


(defn get-post [{{:keys [id]} :path-params :as req}]
  (if (= (:request-method req) :put)
    (upload-post req)
    (if-let [res (db/one (get-post-q id))]
      {:status 200
       :body   {:posts [(dbres->post res)]}}
      {:status 422 ;; really, Ghost?
       :body   {:errors [{:message "Post not found"}]}})))


(defn redirect-to-post [_req]
  {:status  200
   :headers {"Content-Type" "text/html"}
   :body
   (str
     (hi/html
       [:html
        [:head [:title "Redirecting..."]]
        [:body
         [:script
          (hi/raw
            (format
              "if (location.hash.startsWith('#/editor/')) {
                 window.location = '/posts/' + location.hash.split('/')[3];
               }"))]]]))})


(defn routes []
  ;; site connect
  [["/" redirect-to-post]
   ["/api/:version/admin"
    ["/session/"       login]
    ["/site/"          site]
    ["/users/me/"      me]
    ["/tags/"          tags]
    ;; post upload
    ["/images/upload/" upload-image]
    ["/posts/"         upload-post]
    ["/posts/:id/"     get-post]
    ;; ulysses has buggy url generation :(
    ["//posts/:id/"    get-post]
    ["/posts/:uid/:id/"    get-post]]])
