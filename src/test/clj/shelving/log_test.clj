(ns shelving.log-test
  (:require [clojure.test :as t]
            [shelving.log-shelf :refer [->LogShelf]]
            [shelving.common-test :refer [common-tests]])
  (:import [java.io File]))

(t/deftest log-test
  (let [f (File/createTempFile "log-test" ".edn")]
    (.delete f)
    (common-tests #(->LogShelf % f))))
