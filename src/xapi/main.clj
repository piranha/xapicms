(ns xapi.main
  (:gen-class)
  (:require [mount.core :as mount]
            [org.httpkit.server :as httpkit]
            [org.httpkit.client :as http]
            [org.httpkit.sni-client :as sni-client]
            [sentry-clj.core :as sentry]

            [xapi.log :as log]
            [xapi.config :as config]
            [xapi.app :as app]))


(set! *warn-on-reflection* true)
(log/set-logger! (log/->Stdout))
(alter-var-root #'http/*default-client* (fn [_] sni-client/default-client))


(mount/defstate server
  :start (do
           (log/info "Starting" {:port (config/PORT)})
           (httpkit/run-server (app/make-app) {:port (config/PORT)}))
  :stop (server))


(mount/defstate sentry
  :start (sentry/init! (config/SENTRY)
           {:enable-uncaught-exception-handler true}))


(defn -main [& args]
  (mount/start)
  (println "Started on port" (config/PORT)))
