(ns shelving.query.parser
  "Implementation details of the shelving query parser."
  {:authors ["Reid \"arrdem\" McKenzie <me@arrdem.com>"],
   :license "Eclipse Public License 1.0",
   :added   "0.0.0"}
  (:require [clojure.spec.alpha :as s]
            [shelving.specs.query :as specs]))

(defn parse
  ""
  [map-or-seq-datalog]
  (s/conform ::specs/datalog map-or-seq-datalog))


