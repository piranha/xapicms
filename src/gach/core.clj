(ns gach.core
  (:require [clojure.string :as str]))


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
