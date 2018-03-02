(ns shelving.common-test
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as sgen]
            [clojure.string :as str]
            [clojure.test :as t]
            [clojure.test.check :as tc]
            [clojure.test.check.properties :as prop]
            [shelving.core :as sh]))

(s/check-asserts true)

(s/def ::foo string?)
(s/def ::bar string?)
(s/def ::baz
  (s/keys :req-un [::foo ::bar]))
(s/def ::qux pos-int?)

(def schema
  (-> sh/empty-schema
      (sh/value-spec ::foo)
      (sh/record-spec ::baz)
      (sh/spec-rel [::baz ::foo])
      (sh/spec-rel [::baz ::bar])))

(defn schema-migrate-tests [->cfg]
  (let [conn    (sh/open (->cfg schema))
        schema* (sh/schema conn)
        _       (t/is (= schema schema*))
        schema* (sh/alter-schema conn sh/record-spec ::qux)
        _       (t/is (= (sh/schema conn) schema*))]
    nil))

(defn check-forwards-backwards-rels [conn]
  (doseq [rel-id (sh/enumerate-rels conn)]
    (t/is (= (set (sh/enumerate-rel conn rel-id))
             (set (->> (sh/enumerate-rel conn (reverse rel-id))
                       (map reverse))))
          (format "Relation %s didn't forwards/backwards check!" rel-id))))

(defn put-get-enumerate-example-tests [->cfg]
  (let [foo-gen (s/gen ::foo)
        baz-gen (s/gen ::baz)]

    (t/testing "Checking schema for errors"
      (let [schema-errors (sh/check-schema schema)]
        (t/is (not schema-errors)
              (str/join "\n" schema-errors))))

    (t/testing "Testing connection ops"
      (let [cfg  (->cfg schema)
            conn (sh/open cfg)]

        (t/testing "Conn has correct spec/schema information"
          (t/is (= #{::foo ::bar ::baz}
                   (set (sh/enumerate-specs conn)))))

        (t/testing "Testing put/get/has? on values"
          (let [v1 (sgen/generate foo-gen)
                r1 (sh/put-spec conn ::foo v1)
                v2 (sgen/generate foo-gen)
                r2 (sh/put-spec conn ::foo v2)]

            (t/is (= v1 (sh/get-spec conn ::foo r1)))
            (t/is (sh/has? conn ::foo r1))
            (t/is (= v2 (sh/get-spec conn ::foo r2)))
            (t/is (sh/has? conn ::foo r2))
            (t/is (= #{r1 r2} (set (sh/enumerate-spec conn ::foo))))
            (t/is (thrown? AssertionError
                           (sh/put-spec conn ::foo r1 (sgen/generate foo-gen))))

            (t/is (>= 2 (sh/count-spec conn ::foo)))))

        (t/testing "Testing put/get on records"
          (let [b1  (sgen/generate baz-gen)
                b2  (sgen/generate baz-gen)
                bi1 (sh/put-spec conn ::baz b1)]
            (t/is (= b1 (sh/get-spec conn ::baz bi1)))
            (t/is (sh/has? conn ::baz bi1))
            (t/is (= #{bi1} (set (sh/enumerate-spec conn ::baz))))

            ;; Upserts should work
            (sh/put-spec conn ::baz bi1 b2)
            (t/is (= b2 (sh/get-spec conn ::baz bi1)))
            (t/is (= #{bi1} (set (sh/enumerate-spec conn ::baz))))))))))

(defn value-rel-tests [->cfg]
  (let [foo-gen (s/gen ::foo)
        baz-gen (s/gen ::baz)
        conn    (sh/open (->cfg schema))]
    (tc/quick-check 100
      (prop/for-all [baz baz-gen]
        (let [the-foo (:foo baz)
              foo-id  (sh/put-spec conn ::foo the-foo)
              baz-id  (sh/put-spec conn ::baz baz)]
          (t/is foo-id)
          (t/is baz-id)
          ;; Relations are bidirectional on read, unidirectional on write.
          (t/is (some #{foo-id} (sh/get-rel conn [::baz ::foo] baz-id)))
          (t/is (some #{baz-id} (sh/get-rel conn [::foo ::baz] foo-id)))

          (check-forwards-backwards-rels conn))))))

(defn record-rel-tests [->cfg]
  (let [foo-gen (s/gen ::foo)
        baz-gen (s/gen ::baz)
        conn    (sh/open (->cfg schema))]
    (tc/quick-check 100
      (prop/for-all [baz  baz-gen
                     baz' baz-gen]
        (let [the-foo  (:foo baz)
              the-foo' (:foo baz')
              foo-id   (sh/put-spec conn ::foo the-foo) 
              baz-id   (sh/put-spec conn ::baz baz)
              foo-id'  (sh/put-spec conn ::foo the-foo')]
          (t/is foo-id)
          (t/is baz-id)

          ;; Relations are bidirectional on read, unidirectional on write.
          (t/is (some #(= % foo-id) (sh/get-rel conn [::baz ::foo] baz-id)))
          (t/is (some #(= % baz-id) (sh/get-rel conn [::foo ::baz] foo-id)))

          ;; Now perform a write which should invalidate the above properties
          (sh/put-spec conn ::baz baz-id baz')

          ;; The old values should no longer be associated as baz has changed.
          ;; But only if foo and foo' are distinct.
          (when (not= the-foo the-foo')
            (t/is (not-any? #{foo-id} (sh/get-rel conn [::baz ::foo] baz-id)))
            (t/is (not-any? #{baz-id} (sh/get-rel conn [::foo ::baz] foo-id))))

          ;; The new values should be in effect
          (t/is (some #{foo-id'} (sh/get-rel conn [::baz ::foo] baz-id)))
          (t/is (some #{baz-id}  (sh/get-rel conn [::foo ::baz] foo-id')))

          ;; The rel should be counted, and equal in cardinality going either way
          (t/is (= (sh/count-rel conn [::baz ::foo])
                   (sh/count-rel conn [::foo ::baz])))

          (check-forwards-backwards-rels conn))))))

(defn rel-tests [->cfg]
  (value-rel-tests ->cfg)
  (record-rel-tests ->cfg))

#_(defn persistence-tests [->cfg]
    (let [;; Test round-tripping
          _    (sh/close conn)
          conn (sh/open cfg)
          _    (t/is (= (sh/enumerate-specs conn) '(::foo ::baz)))
          _    (t/is (= (sh/enumerate-spec conn ::foo) (list r1 r2)))
          _    (t/is (= v1 (sh/get-spec conn ::foo r1)))
          _    (t/is (= v2 (sh/get-spec conn ::foo r2)))]))

(defn common-tests
  "Takes a ctor of fn [schema] -> cfg, applies it and opens the resulting config twice.

  Runs the example usage session in the README against the computed config."
  [->cfg]
  (schema-migrate-tests ->cfg)
  (put-get-enumerate-example-tests ->cfg)
  (rel-tests ->cfg)
  #_(persistence-tests ->cfg))
