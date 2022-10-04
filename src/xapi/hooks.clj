(ns xapi.hooks
  (:require [clojure.string :as str]
            [org.httpkit.client :as http]
            [cheshire.core :as json]

            [xapi.core :as core]
            [xapi.core.db :as db]
            [xapi.log :as log]
            [xapi.config :as config]
            [xapi.auth :as auth]))


(set! *warn-on-reflection* true)


(def REQ-LOG [:request-method :uri :query-string :headers :body])


(defn store-log! [log-table data]
  (db/q {:insert-into log-table
         :values      [(assoc data :user_id (auth/uid))]})
  (db/q {:delete-from log-table
         :where       [:in :id {:from     [log-table]
                                :select   [:id]
                                :where    [:= :user_id (auth/uid)]
                                :order-by [[:id :desc]]
                                :offset   50}]}))


(defn tg-html [post]
  (let [images (re-matches #"<img src=\"(.*?)\"\/?>" (:html post))
        image  (or (:feature_image post)
                  (first images))
        html   (-> (:html post)
                   (str/replace #"<figure>(.*?)<\/figure>\n*" "")
                   (str/replace #"<ul>(.*?)<\/ul>"
                     (fn [[_ m]]
                       (-> m
                           (str/replace "<li>" " • ")
                           (str/replace #"\n*<\/li>\n*" "\n"))))
                   (str/replace #"<ol>(.*?)<\/ol>"
                     (fn [[_ m]]
                       (let [i* (atom 0)]
                         (-> m
                             (str/replace #"<li>" #(str (swap! i* inc) ". "))
                             (str/replace #"\n*<\/li>\n*" "\n")))))
                   (str/replace #"<blockquote>\n*" "&gt; ")
                   (str/replace "</blockquote>" "\n")
                   (str/replace #"<p>\n*" "\n")
                   (str/replace "</p>" "\n")
                   (str/replace #"\n\n+" "\n\n"))]
    (cond->> html
      (:title post) (str (format "<b>%s</b>\n\n" (:title post)))
      image         (str (format "<a href=\"%s\">&#8205;</a>" image)))))


(defn make-hook-req [hook post]
  (let [user     (auth/user)
        post-url (str "https://" (config/DOMAIN) (:url post))]
    (case (:type hook)
      "url"
      {:url     (:url hook)
       :method  :post
       :headers {"Accept"        "application/vnd.github.v3+json"
                 "Authorization" (str "token " (:access_token user))}
       :body    (json/generate-string
                  {:event_type     "xapicms"
                   :client_payload {:github       (:github user)
                                    :slug         (:slug post)
                                    :url          post-url
                                    :draft        (= "draft" (:status post))
                                    :published_at (:published_at post)}})}

      "github"
      {:url     (format "https://api.github.com/repos/%s/%s/dispatches"
                  (:github user) (:url hook))
       :method  :post
       :headers {"Accept"        "application/vnd.github.v3+json"
                 "Authorization" (str "token " (:access_token user))}
       :body    (json/generate-string
                  {:event_type     "xapicms"
                   :client_payload {:github       (:github user)
                                    :slug         (:slug post)
                                    :url          post-url
                                    :draft        (= "draft" (:status post))
                                    :published_at (:published_at post)}})}
      "telegram"
      (let [html (tg-html post)]
        {:url    (str "https://api.telegram.org/bot" #_TGTOKEN)
         :method :post
         :body   {:chat_id    "@bitethebyte"
                  :message_id nil
                  :parse_mode "HTML"
                  :text       html
                  :disable_web_page_preview
                  (not (or (str/includes? html "&#8205;")
                           (some #{"preview"} (:tags post))))}}))))


(defn send-webhooks! [uid post]
  (assert (:url post))
  (let [hooks    (db/q {:from   [:webhook]
                        :select [:id :type :url :headers]
                        :where  [:and
                                 [:= :user_id uid]
                                 :enabled]})
        post     (assoc post :full-url (str "https://" (config/DOMAIN) (:url post)))]
    (doseq [hook hooks]
      (let [req (make-hook-req hook post)
            res @(http/request req)]
        (store-log! :webhook_log
          {:webhook_id (:id hook)
           :post_uuid  (:uuid post)
           :request    [:lift (-> (select-keys req REQ-LOG)
                                  (update :headers dissoc "Authorization"))]
           :response   [:lift (core/update-some res :error pr-str)]})
        (log/info "sent webhook" (-> (select-keys res [:status :body :error])
                                     (assoc :url (-> res :opts :url))))))))
