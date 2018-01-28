(ns shelving.trivial-test
  (:require [clojure.test :as t]
            [shelving.trivial-edn :refer [->TrivialEdnShelf]]
            [shelving.common-test :refer [run-example]])
  (:import [java.io File]))

(t/deftest trivial-test
  (let [f (File/createTempFile "trivial-test" ".edn")]
    (.delete f)
    (run-example #(->TrivialEdnShelf % f))))
