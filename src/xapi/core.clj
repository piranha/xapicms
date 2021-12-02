(ns xapi.core
  (:require [clojure.string :as str]
            [clojure.set :as set]
            [clojure.walk :as walk]))


(defn uuid []
  (java.util.UUID/randomUUID))


(def ->LATIN
  {"а" "a" "б" "b" "в" "v" "г" "g" "ґ" "g" "д" "d"
   "е" "e" "ё" "yo" "ж" "zh" "з" "z" "и" "i" "і" "i"
   "й" "y" "к" "k" "л" "l" "м" "m" "н" "n"
   "о" "o" "п" "p" "р" "r" "с" "s" "т" "t"
   "у" "u" "ф" "f" "х" "h" "ц" "c" "ч" "ch"
   "ш" "sh" "щ" "shch" "ъ" "" "ы" "y" "ь" "y"
   "э" "e" "ю" "yu" "я" "ya" "є" "ye" "ї" "yi"})


(defn ->latin [s]
  (str/join (map #(get ->LATIN (str %) %) s)))


(defn slug [s]
  (-> (str/lower-case s)
      ->latin
      (str/replace #"[^\w^\d]+" "-")
      (str/replace #"^-|-$" "")))


(defn ext [s]
  (subs s (str/last-index-of s ".")))


;;; utils

(defn update-some [m k f & args]
  (if (contains? m k)
    (apply update m k f args)
    m))


(defn remove-nils [m]
  (reduce-kv (fn [acc k v]
               (if v
                 (assoc acc k v)
                 acc))
    {}
    m))


;;; Forms

(defn parse-int-or [s]
  (if (every? #(Character/isDigit %) s)
    (Long/parseLong s)
    s))


(defn trans-form
  "Transform lists of values to a list of maps, like this:
  {:a.0.b 1 :a.1.b 2} => {:a [{:b 1} {:b 2}]}"
  [data]
  (->> (reduce-kv
         (fn [acc k v]
           (let [ks (str/split k #"\.")]
             (if (= (count ks) 1)
               (assoc acc k v)
               (assoc-in acc (map parse-int-or ks) v))))
         {}
         data)
       (walk/postwalk (fn [v]
                        (if (and (map? v)
                                 (number? (first (keys v))))
                          (->> v (sort-by first) (mapv second))
                          v)))))
