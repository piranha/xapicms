(ns xapi.main
  (:gen-class)
  (:require [mount.core :as mount]
            [org.httpkit.server :as httpkit]

            [xapi.log :as log]
            [xapi.app :as app]))


(set! *warn-on-reflection* true)
(log/set-logger! (log/->Stdout))


(def port (delay
            (Integer/parseInt
              (or (System/getenv "PORT") "1298"))))


(mount/defstate server
  :start (do
           (log/info "Starting" {:port @port})
           (httpkit/run-server app/app {:port @port}))
  :stop (server))


(defn -main [& args]
  (mount/start)
  (println "Started on port" @port))
