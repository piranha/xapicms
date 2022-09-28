(ns xapi.ui.base
  (:require [clojure.pprint]
            [hiccup2.core :as hi]
            [hiccup.page :refer [doctype]]))

(set! *warn-on-reflection* true)


(defn head []
  (hi/html
    [:head
     [:meta {:charset "utf-8"}]
     [:title "XapiCMS"]
     [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]

     [:link {:rel "stylesheet" :href "https://classless.de/classless.css"}]
     [:link {:rel "stylesheet" :href "/static/custom.css"}]
     [:link {:rel "icon" :type "image/png" :href "/favicon.ico"}]
     ;;[:link {:rel "stylesheet" :href "https://unpkg.com/chota@latest"}]
     [:script {:src "/static/twinspark.js"}]]))


(defn footer []
  (hi/html
    [:footer.container
     [:div
      "XapiCMS © 2021 "
      [:a {:href "https://solovyov.net/"} "Alexander Solovyov"]]]))


(defn -wrap [content]
  (hi/html
    (:html5 doctype)
    [:html
     (head)
     [:body
      [:nav
       [:ul
        [:li [:a {:href "/"} "XapiCMS"]]]]
      [:main.container
       content]
      (footer)]]))


(defmacro wrap [& content]
  `(str (-wrap
          (hi/html ~@content))))
