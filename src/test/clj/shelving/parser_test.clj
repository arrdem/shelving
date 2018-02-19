(ns shelving.parser-test
  "Tests of the clojure.spec(.alpha) datalog parser."
  (:require [clojure.spec.alpha :as s]
            [clojure.test :as t]
            [clojure.test.check :as tc]
            [clojure.test.check.properties :as prop]
            [shelving.query.parser :as p]))

(def map-datalog
  :shelving.query.parser.map/datalog)

(def seq-datalog
  :shelving.query.parser.seq/datalog)

(t/deftest test-query-parser-examples
  (t/testing "A bunch of hand examples before we get to the test queries"
    (t/are [q] (s/valid? ::p/datalog (quote q)) 
      [:find ?foo]
      [:find [?foo]]
      {:find [?foo]}

      [:find ?foo
       :in ?bar]
      [:find [?foo]
       :in   [?bar]]
      {:find [?foo]
       :in   [?bar]}

      [:find  [?foo]
       :in    [[:from ::bar ?b]]
       :where [[?foo [::foo ::bar] ?b]]]
      {:find  [?foo]
       :in    [[:from ::bar ?b]]
       :where [[?foo [::foo ::bar] ?b]]}

      #_[:find  [?foo]
         :in    [[:from ::bar ?b]]
         :where [[?foo [::foo ::bar] ?b]
                 (:guard #(.endsWith % "foo") ?b)
                 (:not [?foo [::foo ::bar] "c"])]])))

(t/deftest test-query-normal-form
  (t/testing "Seq and Map queries should round-trip through the same normal form."
    (t/testing "Can maps self-round-trip?"
      (tc/quick-check 10
        (prop/for-all [q (s/gen map-datalog)]
          (let [c (s/conform map-datalog q)]
            (t/is (= c (->> (s/unform map-datalog c) (s/conform map-datalog))))))))

    (t/testing "Can maps round-trip through seqs?"
      (tc/quick-check 10
        (prop/for-all [q (s/gen map-datalog)]
          (let [c (s/conform map-datalog q)]
            (t/is (= c (->> (s/unform seq-datalog c) (s/conform seq-datalog))))))))

    (t/testing "Can seqs self-round-trip?"
      (tc/quick-check 10
        (prop/for-all [q (s/gen seq-datalog)]
          (let [c (s/conform seq-datalog q)]
            (t/is (= c (->> (s/unform seq-datalog c) (s/conform seq-datalog))))))))
    
    (t/testing "Can seqs round-trip through maps?"
      (tc/quick-check 10
        (prop/for-all [q (s/gen seq-datalog)]
          (let [c (s/conform seq-datalog q)]
            (t/is (= c (->> (s/unform map-datalog c) (s/conform map-datalog))))))))))
