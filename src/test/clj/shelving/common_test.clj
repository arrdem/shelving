(ns shelving.common-test
  (:require [shelving.core :as sh]
            [shelving.trivial-edn :refer [->TrivialEdnShelf]]
            [clojure.test :as t]
            [clojure.test.check :as tc] 
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as sgen]
            [clojure.test.check.clojure-test :refer [defspec]]))

(s/def ::foo string?)

(defn run-example
  "Takes a ctor of fn [schema] -> cfg, applies it and opens the resulting config twice.

  Runs the example usage session in the README against the computed config."
  [->cfg]
  (let [foo-gen (s/gen ::foo)
        schema  (-> sh/empty-schema
                    ;; Using content hashing for ID generation
                    (sh/shelf-spec ::foo sh/texts->sha-uuid))
        cfg     (->cfg schema)
        conn    (sh/open cfg)
        _       (t/is (= (sh/enumerate-specs conn) '(::foo)))

        v1 (sgen/generate foo-gen)
        r1 (sh/put conn ::foo v1)
        _  (t/is (= v1 (sh/get conn ::foo r1)))

        v2 (sgen/generate foo-gen)
        r2 (sh/put conn ::foo v2)
        _  (t/is (= (sh/enumerate-records conn ::foo) (list r1 r2)))

        v3 (sgen/generate foo-gen)
        _  (sh/put conn ::foo r1 v3)
        _  (t/is (= v3 (sh/get conn ::foo r1)))

        ;; Test round-tripping
        _    (sh/close conn)
        conn (sh/open cfg)
        _    (t/is (= (sh/enumerate-specs conn) '(::foo)))
        _    (t/is (= (sh/enumerate-records conn ::foo) (list r1 r2)))
        _    (t/is (= v2 (sh/get conn ::foo r2)))
        _    (t/is (= v3 (sh/get conn ::foo r1)))]))
