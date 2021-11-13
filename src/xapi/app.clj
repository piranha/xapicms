(ns xapi.app
  (:import [java.time Instant LocalDateTime])
  (:require [ring.middleware.defaults :as defaults]
            [ring.middleware.json :as json]
            [ring.middleware.session.cookie :as session-cookie]
            [reitit.core :as reitit]
            [cheshire.generate]

            [xapi.log :as log]
            [xapi.auth :as auth]
            [xapi.metaweblog :as mwb]
            [xapi.ghost :as ghost]
            [xapi.ui :as ui]
            [xapi.config :as config]))


(set! *warn-on-reflection* true)


(extend-protocol cheshire.generate/JSONable
  Instant
  (to-json [dt gen] (cheshire.generate/write-string gen (str dt)))
  LocalDateTime
  (to-json [dt gen] (cheshire.generate/write-string gen (str dt))))


(defn log [req]
  (log/info "request" req)
  {:status 204})


(defn h404 [_req]
  {:status 404
   :body   "Not Found"})


(defn routes []
  [["/" ui/index]
   ["/posts/:id/:slug" ui/post]
   ["/settings" auth/settings]
   ["/favicon.ico" h404]
   ["/oauth" auth/start]
   ["/oauth/github" auth/github-cb]
   ["/xmlrpc.php" mwb/dispatch]
   ["/ghost" (ghost/routes)]])


(def dev-router #(reitit/router (routes)))
(def prod-router (reitit/router (routes)))


(defn -app [req]
  (log/info (:request-method req) {:uri (:uri req)})
  (let [router (if true ; dev
                 (dev-router)
                 prod-router)
        m      (reitit/match-by-path router (:uri req))]
    (if m
      ((:result m) (assoc req :path-params (:path-params m)))
      (log req))))


(def app
  (-> -app
      (auth/wrap-auth)
      (json/wrap-json-response)
      (json/wrap-json-body {:keywords? true})
      (defaults/wrap-defaults
        {:params    {:urlencoded true
                     :keywordize true
                     :multipart  true}
         :cookies   true
         :session   {:store (session-cookie/cookie-store
                              {:key (.getBytes ^String (config/SECRET) "UTF-8")})}
         :static    {:resources "public"}
         :responses {:not-modified-responses true
                     :absolute-redirects     true
                     :content-types          true
                     :default-charset        "utf-8"}})))
