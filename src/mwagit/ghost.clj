(ns mwagit.ghost
  (:require [clojure.java.io :as io]
            [cljstache.core :as mustache]

            [mwagit.core :as core])
  (:import [java.util Base64]))


(def BASE "resources/public/")


(let [*b64e (Base64/getEncoder)]
  (defn b64e [s]
    (-> (.encode *b64e (.getBytes s "UTF-8"))
        (String. "UTF-8"))))


(defn login [req]
  (let [login (:body req)]
    {:status  201
     :headers {"set-cookie" (format "ghost-admin-api-session=%s; Path=/ghost; Expires=Mon, 26 Aug 2119 19:14:07 GMT; SameSite=Lax"
                              (b64e (str (:username login) ":" (:password login))))}}))


(defn site [_req]
  {:status 200
   :body   {:site
            {:title       "Blog of wut"
             :description ""
             :logo        ""
             :url         ""
             :version     "6.66"}}})


(defn me [_req]
  {:status 200
   :body   {:users [{:id   "1"
                     :name "Preved"
                     :slug "medved"}]}})

(defn tags [_req]
  {:status 200
   :body   {:tags [{:id   "test"
                    :slug "test"
                    :name "test"}]}})


(defn h404 [_req]
  {:status 404
   :body   "Not Found"})


(defn upload-image [{:keys [params]}]
  (let [file  (:file params)
        fpath (str "images/" (str (core/uuid) ".jpg"))]
    (io/copy (:tempfile file) (io/file BASE fpath))
    {:status 200
     :body   {:images [{:url (str "/media/" fpath)}]}}))


(defn upload-post [req]
  (let [post (-> req :body :posts first)
        slug (or (not-empty (:slug post))
                 (core/slug (:title post)))]
    (spit (io/file BASE (str "posts/" slug ".md"))
      (mustache/render-resource "post.mustache"
        {:draft? (= (:status post) "draft")
         :title  (:title post)
         :tags   (:tags post)
         :html   (:html post)}))
    {:status 200
     :body   {:posts [{:slug   slug
                       :id     slug
                       :uuid   (core/uuid)
                       :title  (:title post)
                       :status (:status post)
                       :url    (str "/media/posts/" slug ".md")}]}}))


(defn get-post [{{:keys [slug]} :path-params}]
  {:status 200
   :body   {:posts [{:id   slug
                     :slug slug}]}})


(defn routes []
  ;; site connect
  [["/session/"       login]
   ["/site/"          site]
   ["/users/me/"      me]
   ["/tags/"          tags]
   ;; post upload
   ["/images/upload/" upload-image]
   ["/posts/"         upload-post]
   ["/posts/:slug/"   get-post]])
