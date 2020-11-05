(ns mwagit.xml-rpc.value
  (:import [clojure.lang IPersistentVector ISeq APersistentMap]
           [java.time Instant]))


(defprotocol ToValue
  (to [v]))


(extend-protocol ToValue
  String
  (to [v]
    [:value [:string v]])

  Boolean
  (to [v]
    [:value [:boolean (if v 1 0)]])

  IPersistentVector
  (to [v]
    [:value [:array [:data {} (map to v)]]])

  ISeq
  (to [v]
    [:value [:array [:data {} (map to v)]]])

  APersistentMap
  (to [m]
    [:value [:struct {}
             (for [[k v] m]
               [:member [:name k] (to v)])]])

  Instant
  (to [v]
    [:value (str "<dateTime.iso8601>" v "</dateTime.iso8601>")]))


(defmulti parse (fn [v] (case (:tag v)
                          :array :array
                          (-> v :content first :tag))))

(defmethod parse :default [v]
  (-> v :content first))

(defmethod parse :string [v]
  (-> v :content first :content first))

(defmethod parse :i4 [v]
  (Integer/parseInt (-> v :content first :content first)))

(defmethod parse :array [v]
  (mapv parse (-> v :content first :content)))
