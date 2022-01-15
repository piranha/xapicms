(ns xapi.app
  (:import [java.time Instant LocalDateTime ZoneOffset]
           [java.time.format DateTimeFormatter])
  (:require [clojure.string :as str]
            [ring.middleware.defaults :as defaults]
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
             [post :as ui.post]]))


(set! *warn-on-reflection* true)


(def ^DateTimeFormatter iso-fmt
  (-> (DateTimeFormatter/ofPattern "yyyy-MM-dd'T'HH:mm:ss.SSSX")
      (.withZone ZoneOffset/UTC)))


(extend-protocol cheshire.generate/JSONable
  Instant
  (to-json [dt gen] (cheshire.generate/write-string gen (.format iso-fmt dt)))
  LocalDateTime
  (to-json [dt gen] (cheshire.generate/write-string gen (.format iso-fmt dt))))


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
   ["/p/:id" ui.post/post]
   ["/static/{*path}" static]
   ["/settings" ui.settings/form]
   ["/favicon.ico" h404]
   ["/oauth" auth/start]
   ["/oauth/github" auth/github-cb]
   ["/xmlrpc.php" mwb/dispatch]
   ["/ghost" (ghost/routes)]])


(def dev-router #(reitit/router (routes)))
(def prod-router (reitit/router (routes)))


(defn maybe-redirect [router req]
  (let [uri ^String (:uri req)
        uri (if (str/ends-with? uri "/")
                 (.substring uri 0 (-> uri count (- 2)))
                 (str uri "/"))]
    (when (reitit/match-by-path router uri)
      {:status  (if (= (:request-method req) :get) 301 308)
       :headers {"Location" uri}})))


(defn -app [req]
  (let [router   (if (config/DEV)
                   (dev-router)
                   prod-router)
        m        (reitit/match-by-path router (:uri req))
        redirect (when-not m
                   (maybe-redirect router req))]
    (cond
      m        ((:result m) (assoc req :path-params (:path-params m)))
      redirect redirect
      :else    (log req))))


(defn access-log [handler]
  (fn [req]
    (try
      (let [res (handler req)]
        (log/info (:request-method req) {:status (:status res) :uri (:uri req)})
        res)
      (catch Exception e
        (log/error (:request-method req) {:uri (:uri req) :error e})
        (throw e)))))


(defn make-app []
  (-> -app
      (access-log)
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
                     :content-types          true
                     :default-charset        "utf-8"}})
      (sentry/wrap-report-exceptions nil)))
