(ns xapi.app
  (:import [java.time Instant LocalDateTime])
  (:require [ring.middleware.defaults :as defaults]
            [ring.middleware.json :as json]
            [ring.middleware.session.cookie :as session-cookie]
            [ring.util.response :as response]
            [reitit.core :as reitit]
            [cheshire.generate]
            [sentry-clj.ring :as sentry]

            [xapi.log :as log]
            [xapi.config :as config]
            [xapi.auth :as auth]
            [xapi.metaweblog :as mwb]
            [xapi.ghost :as ghost]
            [xapi.ui
             [settings :as ui.settings]
             [post :as ui.post]] ))


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


(defn static [{{:keys [path]} :path-params}]
  (response/resource-response path {:root "public"}))


(defn routes []
  [["/" ui.settings/index]
   ["/posts/:id/:slug" ui.post/post]
   ["/static/{*path}" static]
   ["/settings" ui.settings/form]
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


(defn make-app []
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
         :responses {:not-modified-responses true
                     :absolute-redirects     true
                     :content-types          true
                     :default-charset        "utf-8"}})
      (sentry/wrap-report-exceptions nil)))
