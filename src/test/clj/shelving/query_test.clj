(ns shelving.query-test
  (:require [clojure.spec.alpha :as s]
            [clojure.test :as t]
            [shelving.core :as sh]
            [shelving.query :refer [q]]
            [shelving.trivial-edn :refer [->TrivialEdnShelf]]))

(s/def ::foo string?)
(s/def ::qux pos-int?)
(s/def :query.bar/type #{::bar})
(s/def ::bar
  (s/keys :req-un [::foo
                   ::qux
                   :query.bar/type]))

(def schema
  (-> sh/empty-schema
      (sh/value-spec ::foo)
      (sh/value-spec ::qux)
      (sh/value-spec ::bar)
      (sh/spec-rel [::bar ::foo] :foo)
      (sh/spec-rel [::bar ::qux] :qux)))

(t/deftest examples-test 
  (let [*conn (-> (->TrivialEdnShelf schema "target/query.edn"
                                     :flush-after-write false
                                     :load false)
                  (sh/open))]

    (sh/put *conn ::bar {:type ::bar :foo "a" :qux 1})
    (sh/put *conn ::bar {:type ::bar :foo "a" :qux 2})
    (sh/put *conn ::bar {:type ::bar :foo "a" :qux 3})
    (sh/put *conn ::bar {:type ::bar :foo "b" :qux 1})
    (sh/put *conn ::bar {:type ::bar :foo "c" :qux 1})

    (t/testing "Testing unconstrained selects"
      (t/is (= #{"a" "b" "c"}
               (->> (q *conn '{:select {?foo ::foo}})
                    (map '?foo)
                    set)))

      (t/is (= #{1 2 3}
               (->> (q *conn '{:select {?qux ::qux}})
                    (map '?qux)
                    set))))

    (t/testing "Testing using ::bar as a pivot table between ::foo and ::qux"
      (let [prepared-q '{:select {?foo ::foo}
                         :where  [[?bar [::bar ::qux] ?b]
                                  [?bar [::bar ::foo] ?foo]]}]
        (doseq [[b s]
                [[3 #{"a"}]
                 [2 #{"a"}]
                 [1 #{"a" "b" "c"}]]]
          (t/is (= s
                   (->> (q *conn (assoc-in prepared-q [:params '?b] b))
                        (map '?foo)
                        set)))))

      (t/is (= #{1}
               (->> (q *conn
                       {:select '{?qux ::qux}
                        :where  '[[?bar [::bar ::qux] ?qux]
                                  [?bar [::bar ::foo] "b"]]})
                    (map '?qux)
                    set)))

      (t/is (= 3
               (->> (q *conn
                       {:select '{?bar ::bar}
                        :where  '[[?bar [::bar ::qux] 1]]})
                    count))))))
