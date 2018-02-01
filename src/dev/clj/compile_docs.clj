(ns compile-docs
  "A quick hack for building the doc tree based on `^:category` data."
  (:require [shelving.core :as sh]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def category-map
  {::sh/basic  (io/file "docs/basic.md")
   ::sh/schema (io/file "docs/schema.md")
   ::sh/rel    (io/file "docs/rel.md")
   ::sh/util   (io/file "docs/helpers.md")})

(defn ensure-trailing-newline [s]
  (if-not (.endsWith s "\n")
    (str s "\n") s))

(defn compile-docs [category-map nss]
  (doseq [ns              nss
          :let            [ns (if-not (instance? clojure.lang.Namespace ns)
                                (the-ns ns) ns)]
          [sym maybe-var] (ns-publics ns)
          :when           (instance? clojure.lang.Var maybe-var)
          :let            [{:keys [categories arglists doc unstable]
                            :as   var-meta} (meta maybe-var)]]
    (when-let [category-file (first (keep category-map categories))]
      (println ns sym category-file)
      (with-open [w (io/writer category-file :append true)]
        (binding [*out* w]
          (printf "## %s/%s\n" (ns-name ns) sym)
          (doseq [params arglists]
            (printf " - `%s`\n" (cons sym params)))
          (printf "\n")
          (when unstable
            (printf "**UNSTABLE**: This API will probably change in the future\n\n"))
          (printf (ensure-trailing-newline
                   (-> doc
                       (str/replace #"(?<!\n)\n[\s&&[^\n\r]]+" " ")
                       (str/replace #"\n\n[\s&&[^\n\r]]+" "\n\n"))))
          (printf "\n"))))))
