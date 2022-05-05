(ns xapi.ui.post
  (:require [clojure.string :as str]
            [hiccup2.core :as hi]

            [xapi.core.db :as db]
            [xapi.ui.base :as base]
            [xapi.auth :as auth]))


(defn post [{{:keys [id]} :path-params headers :headers}]
  (let [post  (db/one {:from   [:posts]
                       :select [:*]
                       :where  [:= :id id]})
        hooks (when (= (:user_id post) (:id (auth/user)))
                (db/q {:from     [:webhook_log]
                       :select   [:*]
                       :where    [:= :post_uuid (:uuid post)]
                       :order-by [[:id :desc]]
                       :limit    10}))]
    (cond
      (nil? post)
      {:status 404
       :body   "Not Found"}

      (str/includes? (get headers "accept" "") "text/html")
      {:status  200
       :headers {"Content-Type" "text/html"}
       :body
       (base/wrap
         [:article
          [:header
           (when (:feature_image post)
             [:img {:src (:feature_image post)}])

           (when (or (:title post)
                     (= (:status post) "draft"))
             [:h1 (:title post)
              (when (= (:status post) "draft")
                [:sup " (DRAFT)"])])

           [:i.float-right "Tags: " (str/join ", " (:tags post))]]

          [:main#post (hi/raw (:html post))]

          [:time.float-right (str (:updated_at post))]

          (when (seq hooks)
            [:footer
             (for [hook hooks]
               [:p (-> hook :response :opts :url) " " (-> hook :response :body)])])])}

      :else
      {:status  200
       :headers {"Content-Type" "application/json"}
       :body    post})))
