(ns xapi.config
  (:require [clojure.string :as str]))

(set! *warn-on-reflection* true)


(defn get-env
  ([var-name desc]
   (get-env var-name nil desc))
  ([var-name default desc]
   (or (some-> (System/getenv var-name) str/trim)
       default
       (binding [*out* *err*]
         (println desc)
         (System/exit 1)))))


(def PORT     #(-> (get-env "PORT" "1298"
                     "PORT to start on")
                   str/trim
                   Integer/parseInt))
(def PGURL    #(get-env "PGURL"
                 "PGURL env var is empty, please set to Postgres URL"))
(def DOMAIN   #(get-env "DOMAIN"
                 "DOMAIN env var is empty, please set to site domain"))
(def GHID     #(get-env "GHID"
                 "GHID env var is empty, please set to Github app OAuth id"))
(def GHSECRET #(get-env "GHSECRET"
                 "GHSECRET env var is empty, please set to Github app OAuth secret"))
(def SECRET   #(get-env "SECRET"
                 "SECRET key to sign session cookies and other"))
(def SENTRY   #(get-env "SENTRY"
                 "Sentry DSN"))
