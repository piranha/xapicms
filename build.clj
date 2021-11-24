(ns build
  (:require [clojure.tools.build.api :as b]))


(def class-dir "target/classes")
(def basis (b/create-basis {:project "deps.edn"}))
;; (def version (format "1.%s" (b/git-count-revs nil)))
;; (def uber-file (format "target/xapi-%s.jar" version))
(def uber-file "target/xapi.jar")

(defn clean [_]
  (b/delete {:path "target"}))


(defn uber [_]
  (clean nil)
  (b/copy-dir {:src-dirs   ["src" "resources"]
               :target-dir class-dir})
  (b/compile-clj {:basis     basis
                  :src-dirs  ["src"]
                  :class-dir class-dir})
  (b/uber {:class-dir class-dir
           :uber-file uber-file
           :basis     basis
           :main      'xapi.main}))
