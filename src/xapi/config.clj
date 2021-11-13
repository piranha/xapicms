(ns xapi.config
  (:require [clojure.string :as str]))

(set! *warn-on-reflection* true)


(defn get-env [var-name msg]
  (or (some-> (System/getenv var-name) str/trim)
      (binding [*out* *err*]
        (println msg))))


(def PORT     #(or (some-> (System/getenv "PORT") str/trim Integer/parseInt)
                   1298))
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
