(ns user
  (:require [clojure.java.io :as io]
            [mount.core :as mount]
            cemerick.pomegranate
            cemerick.pomegranate.aether
            xapi.main
            [clojure.tools.namespace.repl :as tn]))


(clojure.tools.namespace.repl/set-refresh-dirs "src" "test")


(defn refresh []
  (let [real-out *out*
        sw       (java.io.StringWriter.)]
    (binding [*out* sw]
      (let [res (tn/refresh)]
        (binding [*out* real-out]
          (if (instance? java.io.FileNotFoundException res)
            (do
              (tn/clear)
              (tn/refresh))
            (do
              (print (str sw))
              res)))))))


(defn add-dep [dep]
  (cemerick.pomegranate/add-dependencies
    :coordinates  [dep]
    :repositories (merge cemerick.pomegranate.aether/maven-central
                    {"clojars" "https://clojars.org/repo"})))


(comment
  (add-dep '[hiccup "2.0.0-alpha2"])
  (add-dep '[com.clojure-goes-fast/clj-async-profiler "0.5.1"]))
