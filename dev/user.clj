(ns user
  (:require [mount.core :as mount]
            cemerick.pomegranate
            cemerick.pomegranate.aether
            gach.main))


(defn add-dep [dep]
  (cemerick.pomegranate/add-dependencies
    :coordinates  [dep]
    :repositories (merge cemerick.pomegranate.aether/maven-central
                    {"clojars" "https://clojars.org/repo"})))


(comment
  (add-dep '[cljstache "2.0.6"]))
