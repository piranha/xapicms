(ns xapi.core.db
  (:require [clojure.string :as str]
            [mount.core :as mount]
            [ring.util.codec :as codec]
            [cheshire.core :as json]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as jdbc-rs]
            [next.jdbc.prepare :as prepare]
            [next.jdbc.date-time :as jdbc-dt]
            [honey.sql :as sql]

            [xapi.config :as config])
  (:import [java.net URI]
           [java.sql PreparedStatement]
           [org.postgresql.ds PGSimpleDataSource]
           [org.postgresql.jdbc PgArray]
           [org.postgresql.util PGobject]))


(set! *warn-on-reflection* true)
(jdbc-dt/read-as-instant)


(def call sql/call)
(def fmt sql/format)


(defn set-pg-opts [^PGSimpleDataSource ds opts]
  (doseq [[k v] opts]
    (case k
      "sslmode"     (.setSslMode ds v)
      "sslrootcert" (.setSslRootCert ds v)
      "options"     (.setOptions ds v))))


(defn make-ds [url]
  (let [uri        (URI. url)
        [user pwd] (str/split (.getUserInfo uri) #":")
        port       (if (= -1 (.getPort uri))
                     5432
                     (.getPort uri))
        opts       (some-> (.getQuery uri) codec/form-decode)]
    (doto (PGSimpleDataSource.)
      (.setServerName (.getHost uri))
      (.setPortNumber port)
      (.setDatabaseName (.substring (.getPath uri) 1))
      (.setUser user)
      (.setPassword pwd)
      (.setPrepareThreshold 0)
      (set-pg-opts opts))))


(mount/defstate conn
  :start (make-ds (config/PGURL)))


(defn format-query [query]
  (if (string? query)
    [query]
    (sql/format query {:dialect :ansi})))


(defn q [query]
  (jdbc/execute! conn (format-query query)
    {:builder-fn jdbc-rs/as-unqualified-lower-maps}))


(defn one [query]
  (first (q query)))


;;; extensions


(defn <-pgobject
  "Transform PGobject containing `json` or `jsonb` value to Clojure
  data."
  [^PGobject v]
  (let [type  (.getType v)
        value (.getValue v)]
    (if (#{"jsonb" "json"} type)
      (when value
        (with-meta (json/parse-string value keyword) {:pgtype type}))
      value)))


(defn ->pgobject
  "Transforms Clojure data to a PGobject that contains the data as
  JSON. PGObject type defaults to `jsonb` but can be changed via
  metadata key `:pgtype`"
  [x]
  (let [pgtype (or (:pgtype (meta x)) "jsonb")]
    (doto (PGobject.)
      (.setType pgtype)
      (.setValue (json/generate-string x)))))


(extend-protocol jdbc-rs/ReadableColumn
  PgArray
  (read-column-by-label [v label]
    (mapv #(jdbc-rs/read-column-by-label % label) (.getArray v)))
  (read-column-by-index [v rsmeta idx]
    (mapv #(jdbc-rs/read-column-by-index % rsmeta idx) (.getArray v)))

  PGobject
  (read-column-by-label [^org.postgresql.util.PGobject v _]
    (<-pgobject v))
  (read-column-by-index [^org.postgresql.util.PGobject v _2 _3]
    (<-pgobject v)))


;; if a SQL parameter is a Clojure hash map or vector, it'll be transformed
;; to a PGobject for JSON/JSONB:
(extend-protocol prepare/SettableParameter
  clojure.lang.IPersistentMap
  (set-parameter [m ^PreparedStatement s i]
    (.setObject s i (->pgobject m)))

  clojure.lang.IPersistentVector
  (set-parameter [v ^PreparedStatement s i]
    (.setObject s i (->pgobject v))))
