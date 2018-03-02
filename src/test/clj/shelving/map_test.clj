(ns shelving.map-test
  (:require [clojure.test :as t]
            [shelving.map-shelf :refer [->MapShelf]]
            [shelving.common-test :refer [common-tests]])
  (:import [java.io File]))

(t/deftest map-test
  (let [f (File/createTempFile "map-test" ".edn")]
    (.delete f)
    (common-tests #(->MapShelf % f))))
