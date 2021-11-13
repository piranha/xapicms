(ns xapi.ui
  (:require [clojure.pprint]
            [hiccup2.core :as hi]

            [xapi.auth :as auth]
            [xapi.core.db :as db]
            [clojure.string :as str]
            [xapi.core.idenc :as idenc]))


(defn index [_req]
  (let [user (auth/user)]
    {:status  200
     :headers {"Content-Type" "text/html"}
     :body
     (str
       (hi/html
         [:html
          [:head
           [:title "Xapi CMS"]]
          [:body
           "Hello!"
           [:br]
           (if user
             [:div
              [:p
               "You can now post stuff. Your email is "
               [:code (:email user)]
               " and your password is following:"]
              [:pre [:code (:apikey user)]]
              [:hr]
              [:h2 "Settings"]
              [:form {:method "post" :action "/settings"}
               [:div
                [:label
                 "Repo to notify"
                 [:input {:type "text" :name "repo" :value (:repo user)}]]]
               [:div
                [:label
                 "Regenerate password"
                 [:input {:type "checkbox" :name "apikey-new"}]]]
               [:div
                [:input {:type "submit" :value "Send"}]]]]

             [:a {:href "/oauth"} "Start login"])]]))}))



(defn post [{{:keys [id slug]} :path-params headers :headers}]
  (let [res (db/one {:from   [:posts]
                     :select [:*]
                     :where  [:and
                              [:= :user_id (idenc/decode id)]
                              [:= :slug slug]]})]

    (cond
      (nil? res)
      {:status 404
       :body   "Not Found"}

      (str/includes? (get headers "accept" "") "text/html")
      {:status  200
       :headers {"Content-Type" "text/html"}
       :body
       (str
         (hi/html
           [:html
            [:head
             [:title (:title res)]
             [:link {:rel "stylesheet" :href "https://solovyov.net/static/blog.css"}]]
            [:body
             [:article
              (when (:feature_image res)
                [:img {:src (:feature_image res)}])
              [:h1 (:title res)
               (when (= (:status res) "draft")
                 [:span.grey " (DRAFT)"])]
              [:div.grey
               "Tags: " (str/join ", " (:tags res))]
              [:div#post.clearfix (hi/raw (:html res))]
              [:div.right
               [:time.mono (str (:updated_at res))]]]]]))}

      :else
      {:status  200
       :headers {"Content-Type" "application/json"}
       :body    res})))
