(ns xapi.ui.post
  (:require [clojure.string :as str]
            [hiccup2.core :as hi]

            [xapi.core.db :as db]
            [xapi.ui.base :as base]))


(defn post [{{:keys [id]} :path-params headers :headers}]
  (let [res (db/one {:from   [:posts]
                     :select [:*]
                     :where  [:= :id id]})]
    (cond
      (nil? res)
      {:status 404
       :body   "Not Found"}

      (str/includes? (get headers "accept" "") "text/html")
      {:status  200
       :headers {"Content-Type" "text/html"}
       :body
       (base/wrap
         [:article
          [:header
           (when (:feature_image res)
             [:img {:src (:feature_image res)}])

           (when (or (:title res)
                     (= (:status res) "draft"))
             [:h1 (:title res)
              (when (= (:status res) "draft")
                [:sup " (DRAFT)"])])

           [:i.float-right "Tags: " (str/join ", " (:tags res))]]

          [:main#post (hi/raw (:html res))]

          [:time.float-right (str (:updated_at res))]])}

      :else
      {:status  200
       :headers {"Content-Type" "application/json"}
       :body    res})))
