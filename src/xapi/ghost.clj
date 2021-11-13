(ns xapi.ghost
  (:import [java.time Instant LocalDate]
           [java.util.zip Adler32]
           [java.time ZonedDateTime]
           [java.time.format DateTimeFormatter])
  (:require [cognitect.aws.client.api :as aws]
            [clojure.java.io :as io]
            [hiccup2.core :as hi]

            [xapi.core :as core]
            [xapi.core.db :as db]
            [xapi.core.idenc :as idenc]
            [xapi.auth :as auth]
            [xapi.log :as log]
            [xapi.config :as config]
            [cheshire.core :as json]))


(set! *warn-on-reflection* true)


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


(defn send-webhook! [post]
  (let [user (auth/user)
        url  (format "/repos/%s/%s/dispatches" (:github user) (:repo user))
        res (auth/gh! url (:access_token user)
              {:method :post
               :body
               (json/generate-string
                 {:event_type     "xapicms"
                  :client_payload {:slug  (:slug post)
                                   :url   (str "https://" (config/DOMAIN) (:url post))
                                   :draft (= "draft" (:status post))}})})]
    (log/info "github webhook" res)))


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


(defn get-file-or-name [orig]
  (if-let [file (db/one {:from   [:images]
                         :select [:id
                                  :path
                                  :hash]
                         :where  [:and
                                  [:= :user_id (auth/uid)]
                                  [:= :id orig]]})]
    file
    {:id   orig
     :path (format "images/%s/%s/%s/%s"
             (idenc/encode (auth/uid))
             (.getYear (LocalDate/now))
             (.getMonthValue (LocalDate/now))
             orig)}))


(defn upload-image [{:keys [params]}]
  (let [file   (:file params)
        ba     (-> (:tempfile file) io/input-stream .readAllBytes)
        hash   (-> (doto (Adler32.)
                     (.update ba))
                   .getValue
                   str)
        record (get-file-or-name (:filename file))]
    (when (not= hash (:hash record))
      (let [res (aws/invoke s3 {:op      :PutObject
                                :request {:Bucket      "xapi"
                                          :Key         (:path record)
                                          :ACL         "public-read"
                                          :ContentType (:content-type file)
                                          :Body        ba}})]
        (log/info "file upload" {:key (:path record) :res res}))
      (db/q {:insert-into   :images
             :values        [{:id         (:id record)
                              :path       (:path record)
                              :hash       hash
                              :user_id    (auth/uid)
                              :updated_at (Instant/now)}]
             :on-conflict   [:user_id :id]
             :do-update-set [:updated_at :hash :path]}))
    {:status 200
     :body   {:images [{:url (str "https://images.solovyov.net/" (:path record))}]}}))


(defn make-dbpost [post]
  (let [slug (or (not-empty (:slug post))
                 (str (core/uuid)))
        html (:html post)]
    {:user_id       (auth/uid)
     :id            (str (idenc/encode (or (auth/uid) 0)) "__" slug)
     :slug          slug
     :title         (not-empty (:title post))
     :uuid          (core/uuid) ;; Ulysses needs that, but does not use it
     :tags          (when (seq (:tags post))
                      [:array (:tags post)])
     :status        (:status post)
     :html          html
     :updated_at    (or (some-> (:updated_at post) parse-dt)
                        (Instant/now))
     :published_at  (some-> (:published_at post) parse-dt)
     :feature_image (:feature_image post)}))


(def DBPOST-KEYS (keys (make-dbpost nil)))


(defn dbres->post [post]
  (-> post
      (update :tags #(mapv make-tag %))
      (dissoc :user_id)
      (assoc :url (format "/posts/%s/%s"
                    (idenc/encode (auth/uid))
                    (:slug post)))))


(defn upload-post [req]
  (let [input   (-> req :body :posts first)
        dbpost (make-dbpost input)
        res    (db/one {:insert-into   :posts
                        :values        [dbpost]
                        :on-conflict   [:user_id :id]
                        :do-update-set (keys dbpost)
                        :returning     (keys dbpost)})
        post (dbres->post res)]
    (if (seq (:repo (auth/user)))
      (send-webhook! post)
      (log/info "user has no repo set, not sending webhook"))
    {:status 200
     :body   {:posts [post]}}))


(defn get-post [{{:keys [id]} :path-params :as req}]
  (if (= (:request-method req) :put)
    (upload-post req)
    (if-let [res (db/one {:from   [:posts]
                          :select DBPOST-KEYS
                          :where  [:and
                                   [:= :user_id (auth/uid)]
                                   [:= :id id]]})]
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
                 window.location = '/posts/' + location.hash.split('/')[3].replace('__', '/');
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
