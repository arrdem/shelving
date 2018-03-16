(ns shelving.query-test
  (:require [clojure.spec.alpha :as s]
            [clojure.test :as t]
            [shelving.core :as sh]
            [shelving.query :refer [q q! q* q** q*** q****]]
            [shelving.log-shelf :refer [->LogShelf]]))

(s/def ::foo string?)
(s/def ::qux pos-int?)
(s/def ::bar
  (s/keys :req-un [::foo ::qux]))

(def schema
  (-> sh/empty-schema
      (sh/value-spec ::foo)
      (sh/value-spec ::qux)
      (sh/record-spec ::bar)
      (sh/spec-rel [::bar ::foo])
      (sh/spec-rel [::bar ::qux])))

(t/deftest examples-test 
  (let [*conn (-> (->LogShelf schema "target/query-test.edn"
                              :flush-after-write false
                              :load false)
                  (sh/open))]

    (sh/put-spec *conn ::bar {:foo "a" :qux 1})
    (sh/put-spec *conn ::bar {:foo "a" :qux 2})
    (sh/put-spec *conn ::bar {:foo "a" :qux 3})
    (sh/put-spec *conn ::bar {:foo "b" :qux 1})
    (sh/put-spec *conn ::bar {:foo "c" :qux 1})

    (t/testing "Testing unconstrained selects"
      (t/is (= #{"a" "b" "c"}
               (->> (q! *conn '[:find [[:from ::foo ?foo]]])
                    (map '?foo)
                    set)))

      (t/is (= #{1 2 3}
               (->> (q! *conn '[:find [[:from ::qux ?qux]]])
                    (map '?qux)
                    set))))

    (t/testing "Testing using ::bar as a pivot table between ::foo and ::qux"
      (let [single-q-fn (q *conn '[:find  [?foo]
                                   :in    [?b]
                                   :where [[?bar [::bar ::qux] ?b]
                                           [?bar [::bar ::foo] ?foo]]])
            multi-q-fn  (q *conn '[:find  [?foo]
                                   :in    [?b ...]
                                   :where [[?bar [::bar ::qux] ?b]
                                           [?bar [::bar ::foo] ?foo]]])]
        (doseq [[b s]
                [[3 #{"a"}]
                 [2 #{"a"}]
                 [1 #{"a" "b" "c"}]]]
          (t/is (= s
                   (->> (single-q-fn *conn b) (map '?foo) set)
                   (->> (multi-q-fn *conn [b]) (map '?foo) set)))))

      (t/is (= #{1}
               (->> (q! *conn
                        '[:find  [?qux]
                          :where [[?bar [::bar ::qux] ?qux]
                                  [?bar [::bar ::foo] "b"]]])
                    (map '?qux)
                    set)))

      (t/is (= 3
               (->> (q! *conn
                        '[:find  [?bar]
                          :where [[?bar [::bar ::qux] 1]]])
                    count)

               (->> (q! *conn
                        '[:find ?bar :where [?bar [::bar ::qux] 1]])
                    count))))))
