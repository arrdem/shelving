(ns shelving.query.common
  "Common implementation details of the shelving query system."
  {:authors ["Reid \"arrdem\" McKenzie <me@arrdem.com>"],
   :license "Eclipse Public License 1.0",
   :added   "0.0.0"})

(defn lvar?
  "Predicate. True if and only if the given object is a ?-prefixed
  symbol representing a logic variable."
  {:stability  :stability/unstable
   :added      "0.0.0"}
  [obj]
  (and (symbol? obj)
       (.startsWith (name obj) "?")
       (not (namespace obj))))

(def ^{:stability  :stability/unstable
       :added      "0.0.0"
       :doc        "Predicate indicating whether Shelving recognizes this value as a possible spec."
       :arglists   (:arglists (meta #'qualified-keyword?))}
  spec?
  #'qualified-keyword?)
