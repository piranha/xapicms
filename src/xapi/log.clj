(ns xapi.log
  "Derived from https://github.com/duct-framework/logger"
  (:import [java.time Instant]))


(def *logger
  "This should be a logger instance, do this at the start of the app:

  (set-logger! (log/->Stdout))"
  nil)


(defprotocol Logger
  "Protocol for abstracting logging. Used by the log macro."
  (-log [logger level file line event data]))


(extend-protocol Logger
  nil
  (-log [_ _ _ _ _ _] nil))


(defrecord Stdout []
  Logger
  (-log [_ level file line event data]
    (let [time  (:log/time @data)
          data' (dissoc @data :log/time)]
      (println
        (format "%-5s %s %s:%s %s %s" (.toUpperCase (name level)) time file line event (pr-str data'))))))


(defn- log-form [level event data form]
  `(-log *logger
         ~level
         ~*ns* ~(:line (meta form))
         ~event
         (delay (assoc ~data
                  :log/time (Instant/now)
                  :log/id   (java.util.UUID/randomUUID)))))


;;; Public API

(defmacro log
  "Log an event and optional data structure at the supplied severity level."
  ([level event]      (log-form level event nil &form))
  ([level event data] (log-form level event data &form)))


(doseq [level '(report fatal error warn info debug)]
  (eval
   `(defmacro ~level
      ~(format "Log an event with %s logging level. See [[log]]." level)
      (~'[event]
       (log-form ~(keyword level) ~'event nil ~'&form))
      (~'[event data]
       (log-form ~(keyword level) ~'event ~'data ~'&form)))))


(defn set-logger! [logger]
  (alter-var-root #'*logger (constantly logger)))
