(ns xapi.core.idenc
  (:require [xapi.config :as config]))


(defn swap [v i1 i2]
  (assoc v i2 (v i1) i1 (v i2)))


(defn consistent-shuffle [alphabet salt]
  (if (empty? salt)
    alphabet
    (apply str (reduce (fn [[alph p] [idx a]]
                         (let [i (- (count alph) idx 1)
                               v (mod idx (count salt))
                               n (long (nth salt v))
                               p (+ p n)]
                           (if (zero? i)
                             alph
                             [(swap alph i (mod (+ n v p) i)) p])))
                       [(vec alphabet) 0]
                       (map-indexed (fn [idx a] [idx a]) alphabet)))))


(defn decoding-alphabet [alphabet]
  (->> alphabet
       (map-indexed #(vector %2 %1))
       (into {})))


(defn make-config [prefix alphabet salt]
  (let [ab (consistent-shuffle alphabet salt)]
    {:prefix   prefix
     :base     (count ab)
     :alphabet ab
     :decoding (decoding-alphabet ab)}))


(def ALPHABET (make-config "" "0123456789abcdefghijklmnopqrstuvwxyz" (config/SECRET)))


(defn -encode [base alphabet value]
  (loop [value value
         code  nil]
    (if (zero? value)
      (apply str (or code (list \0)))
      (recur (quot value base)
             (cons (nth alphabet (rem value base)) code)))))


(defn -decode [base decoding value]
  (->> (reverse value)
       (map-indexed (fn [i v] (* (get decoding v)
                                 (int (Math/pow base i)))))
       (apply +)))


(defn encode [value]
  (let [{:keys [prefix base alphabet]} ALPHABET]
    (str prefix (-encode base alphabet value))))


(defn decode [value]
  (let [{:keys [prefix base decoding]} ALPHABET]
    (-decode base decoding (.substring value (count prefix)))))
