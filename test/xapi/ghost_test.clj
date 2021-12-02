(ns xapi.ghost-test
  (:require [clojure.test :as t]
            [xapi.ghost :as g]))



(def NEW-POST-INPUT
  {:status       "published"
   :tags         ["channel"]
   :title        ""
   :published_at nil
   :featured     false
   :html         "<p>Це всього лише тест</p>"})


(t/deftest generate-valid-post
  (t/testing "Incoming new post should generate correct data to insert into DB"
    (let [dbpost (g/input->dbpost NEW-POST-INPUT)]
      (prn dbpost)
      (t/are [y] (some? y)
        (:slug dbpost)
        (:id dbpost)
        (:uuid dbpost))
      (t/is (vector? (:tags dbpost))))))
