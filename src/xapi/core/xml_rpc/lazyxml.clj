(ns xapi.core.xml-rpc.lazyxml
  (:refer-clojure :exclude [find]))


;;; Matchers

(defn tag=
  "finds child nodes under all tags 'tag'"
  [tag]
  (fn [node]
    ;;(prn node)
    (= tag (:tag node))))


(defn attr=
  "finds child nodes under all tags with attribute's key 'k' and value 'v'"
  [kvs]
  (let [ks (keys kvs)]
    (fn [node]
      (= kvs (select-keys (:attrs node) ks)))))


(defn tagattr=
  "find nodes which match tag and its attributes"
  [tag kvs]
  (let [ks (keys kvs)]
    (fn [node]
      (and (= tag (:tag node))
           (= kvs (select-keys (:attrs node) ks))))))


;;; Finder

(defn find-by [nodes pred]
  (for [node  nodes
        child (:content node)
        :when (pred child)]
      child)
  #_(filter pred (mapcat :content nodes)))


;;; API

(defn parse-crumb [item]
  (cond
    (keyword? item)   (tag= item)
    (and (map? item)
         (:TAG item)) (tagattr= (:TAG item) (dissoc item :TAG))
    (map? item)       (attr= item)
    :else             item))


(defn parse-path [path]
  (map parse-crumb path))


(defn find [nodes path]
  (let [nodes (if (seq? nodes) nodes [nodes])
        tpath (parse-path path)]
    (reduce
      (fn [nodes pred]
        (find-by nodes pred))
      nodes
      tpath)))

(defn find1 [nodes path]
  (first (find nodes path)))


;;; Higher-level constructs


(defn -paths&opts [bits]
  (if (map? (last bits))
    [(butlast bits) (last bits)]
    [bits nil]))


(defn map-vals [f m]
  (reduce-kv (fn [m k v]
               (assoc m k (f v)))
    {} m))


(defn -text [node]
  (if (string? node)
    node
    (-> node :content first)))


(defn parse
  ([node path] (parse node path nil))
  ([node path {:keys [mapper validator]}]
   (let [nodes (map-vals #(find1 node %)
                 (if (map? path)
                   path
                   {::_ path}))
         value (map-vals -text nodes)
         res   (cond-> value
                 (= ::_ (key (first value))) (::_)
                 mapper                      (mapper))]
     (when validator
       (try (validator res)
            (catch Exception e
              (throw (ex-info (.getMessage e)
                       (assoc (ex-data e)
                         :path  path
                         :node  (val (first nodes))
                         :value (cond-> value
                                  (= ::_ (key (first value))) (::_))
                         :line  (-> (meta (val (first nodes)))
                                    :clojure.data.xml/location-info
                                    :line-number)))))))
     res)))


(comment

  (def xml {:tag     :a
            :content (repeat 2 {:tag     :b
                                :attrs   {:id 1}
                                :content (repeat 2 {:tag     :c
                                                    :content ["test"]})})
            #_[{:tag     :b
                :attrs   {:id 1}
                :content (repeat 2 {:tag     :c
                                    :content ["test"]})}
               {:tag     :b
                :attrs   {:id 2}
                :content (repeat 2 {:tag     :c
                                    :content ["test"]})}]})

  (-> [xml]
      (find-by (tag= :b))
      (find-by (tag= :c))
      first)

  (find1 xml [:b :c string?]) ;; => "test"

  (def xml2 {:tag :Product
             :content [{:tag :Title :content [3]}
                       {:tag :Variation :content [1]}
                       {:tag :Variation :content [2]}]})

  (find xml2 [(complement (tag= :Variation))])

  [{:tag :a, :content [{:tag :b, :content [{:tag :c, :content ["test"]}]}]}]

  [{:tag :b, :content [{:tag :c, :content ["test"]}]}]

  [{:tag :c, :content ["test"]}])
