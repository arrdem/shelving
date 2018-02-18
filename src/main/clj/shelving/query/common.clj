(ns shelving.query.common
  "Common implementation details of the shelving query system."
  {:authors ["Reid \"arrdem\" McKenzie <me@arrdem.com>"],
   :license "Eclipse Public License 1.0",
   :added   "0.0.0"}
  (:require [shelving.core :as sh]))

(defn lvar?
  "Predicate. True if and only if the given object is a ?-prefixed
  symbol representing a logic variable."
  {:stability  :stability/unstable
   :categories #{::sh/query}
   :added      "0.0.0"}
  [obj]
  (and (symbol? obj)
       (.startsWith (name obj) "?")
       (not (namespace obj))))
