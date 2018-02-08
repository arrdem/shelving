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

(defn relativize-path [p]
  (str/replace p (.getCanonicalPath (io/file ".")) ""))

(defn compile-docs [category-map nss]
  (let [vars (for [ns              nss
                   :let            [ns (if-not (instance? clojure.lang.Namespace ns)
                                         (the-ns ns) ns)]
                   [sym maybe-var] (ns-publics ns)
                   :when           (instance? clojure.lang.Var maybe-var)]
               maybe-var)

        groupings (group-by #(-> % meta :categories first) vars)]
    (println groupings)
    (doseq [[category vars] groupings
            :let            [category-file (get category-map category)]
            :when           category-file
            v               (sort-by #(-> % meta :line) vars) ;; FIXME: better scoring heuristic?
            :let            [{:keys [categories arglists doc stability line file]
                              :as   var-meta} (meta v)]]
      (println v)
      (with-open [w (io/writer category-file :append true)]
        (binding [*out* w]
          (printf "## [%s/%s](%s#L%s)\n"
                  (ns-name (.ns v)) (.sym v)
                  (relativize-path file) line)
          (doseq [params arglists]
            (printf " - `%s`\n" (cons (.sym v) params)))
          (printf "\n")
          (when (= stability :stability/unstable)
            (printf "**UNSTABLE**: This API will probably change in the future\n\n"))
          (printf (ensure-trailing-newline
                   (-> doc
                       (str/replace #"(?<!\n)\n[\s&&[^\n\r]]+" " ")
                       (str/replace #"\n\n[\s&&[^\n\r]]+" "\n\n"))))
          (printf "\n"))))))

(defn recompile-docs [category-map nss]
  (doseq [[_cat f] category-map]
    (let [buff      (slurp f)
          truncated (str/replace buff #"(?s)\n+##.++" "\n\n")]
      (spit f truncated)))

  (compile-docs category-map nss))
