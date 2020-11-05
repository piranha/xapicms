(ns mwagit.app
  (:require [clojure.string :as str]
            [clojure.xml :as xml]
            [hiccup.core :as hi]

            [mwagit.log :as log]
            [mwagit.xml-rpc.lazyxml :as x]
            [mwagit.xml-rpc.value :as value])
  (:import [java.time Instant]))


(set! *warn-on-reflection* true)


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


(defn dispatch [methods req]
  (let [xml         (xml/parse (:body req))
        method-name (not-empty (x/find1 xml [:methodName string?]))
        params      (->> (x/find xml [:params :param])
                         (mapv (comp value/parse first :content)))
        _           (log/info "request" {:method method-name
                                         :params params})
        handler     (get methods method-name)]
    (if handler
      (-> (apply handler params)
          (update :body body->xml))
      (do
        (log/info "unknown method" {:method method-name})
        {:status 404
         :body   "Not Found"}))))


(defn app [req]
  (case (:request-method req)
    :get  {:status 200
           :body   (str/join "\n" (map name (keys METHODS)))}
    :post (dispatch METHODS req)))
