(ns shelving.juxt-test
  (:require [clojure.test :as t]
            [shelving.trivial-edn :refer [->TrivialEdnShelf]]
            [shelving.juxt :refer [->JuxtShelf]]
            [shelving.common-test :refer [common-tests]])
  (:import [java.io File]))

(t/deftest juxt-test
  (let [f1 (File/createTempFile "juxt-test1" ".edn")
        f2 (File/createTempFile "juxt-test2" ".edn")]
    (.delete f1)
    (.delete f2)
    (common-tests
     #(->JuxtShelf %
                   (fn [schema] (->TrivialEdnShelf schema f1))
                   (fn [schema] (->TrivialEdnShelf schema f2))))))

