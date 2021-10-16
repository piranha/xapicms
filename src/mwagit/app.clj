(ns mwagit.app
  (:require [clojure.string :as str]
            [clojure.xml :as xml]
            [hiccup.core :as hi]
            [ring.middleware.defaults :as defaults]
            [ring.middleware.json :as json]
            [reitit.core :as reitit]

            [mwagit.log :as log]
            [mwagit.xml-rpc.lazyxml :as x]
            [mwagit.xml-rpc.value :as value]
            [mwagit.ghost :as ghost])
  (:import [java.time Instant]))


(set! *warn-on-reflection* true)

;; (def SECRET (or (not-empty (System/getenv "MWAGIT_SECRET"))
;;                 (throw "Unknown client secret!")))


(def METHODS
  {"system.listMethods"
   (fn methods
     []
     {:status 200
      :body   [(keys METHODS)]})

   "wp.getUsersBlogs"
   (fn user-blogs
     [login pass]
     {:status 200
      :body   [[{:isAdmin  false
                 :url      "/"
                 :blogid   "1"
                 :blogName (str "Blog of " login)
                 :xmlrpc   "/xmlrpc.php"}]]})

   "wp.getProfile"
   (fn user-blogs
     [blogid login pass]
     {:status 200
      :body   [{:username     login
                :email        (str login "@mwagit.com")
                :nicename     (str "Mr. " login)
                :display_name (str "Who are you?")
                :registered   (Instant/now)}]})

   "wp.getOptions"
   (fn options
     [blogid login pass opts]
     {:status 200
      :body   [(for [opt opts]
                 (case opt
                   "software_version" {:desc     "software_version"
                                       :value    "mwagit 1.0"
                                       :readonly true}))]})

   "wp.getTaxonomies"
   (fn taxonomies
     [blogid login pass]
     {:status 204})

   "metaWeblog.getPost"
   (fn get-post
     [postid login pass]
     {:status 200
      :body   [:div {} "wow"]})

   "metaWeblog.getRecentPosts"
   (fn get-recent
     ([blogid login pass]
      (get-recent blogid login pass 10))
     ([blogid login pass limit]
      (let [limit (min limit 20)]
        {:status 200
         :body   [:div {} "limit " limit]})))

   "metaWeblog.newPost"
   (fn new-post
     [blogid login pass content publish?]
     (prn content)
     {:status 200
      :body   "hello"})

   "metaWeblog.editPost"       nil
   "metaWeblog.deletePost"     nil
   "metaWeblog.getCategories"  nil
   "metaWeblog.newMediaObject" nil
   "metaWeblog.getUsersBlogs"  nil})



(defn body->xml [body]
  (str "<?xml version=\"1.0\"?>\n"
    (hi/html
      [:methodResponse
       [:params
        (for [v body]
          [:param (value/to v)])]])))


(defn mwa-dispatch [req]
  (case (:request-method req)
    :get {:status 200
          :body   (str/join "\n" (map name (keys METHODS)))}
    :post
    (let [xml         (xml/parse (:body req))
          method-name (not-empty (x/find1 xml [:methodName string?]))
          params      (->> (x/find xml [:params :param])
                           (mapv (comp value/parse first :content)))
          _           (log/info "request" {:method method-name
                                           :params params})
          handler     (get METHODS method-name)]
      (if handler
        (-> (apply handler params)
            (update :body body->xml))
        (do
          (log/info "unknown method" {:method method-name})
          {:status 404
           :body   "Not Found"})))))


(defn log [req]
  (log/info "request" req)
  {:status 204})


(defn routes []
  [["/xmlrpc.php" mwa-dispatch]
   ["/ghost/api/v4/admin" (ghost/routes)]])


(def dev-router #(reitit/router (routes)))
(def prod-router (constantly (reitit/router (routes))))


(defn -app [req]
  (prn (:request-method req) (:uri req))
  (let [router (if true ; dev
                 (dev-router)
                 (prod-router))
        m      (reitit/match-by-path router (:uri req))]
    (if m
      ((:result m) (assoc req :path-params (:path-params m)))
      (log req))))


(def app
  (-> -app
      (json/wrap-json-response)
      (json/wrap-json-body {:keywords? true})
      (defaults/wrap-defaults {:params    {:urlencoded true
                                           :keywordize true
                                           :multipart  true}
                               :cookies   true
                               :static    {:resources "public"}
                               :responses {:not-modified-responses true
                                           :absolute-redirects     true
                                           :content-types          true
                                           :default-charset        "utf-8"}})))
