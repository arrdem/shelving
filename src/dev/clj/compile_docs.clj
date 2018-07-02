(ns compile-docs
  "A quick hack for rebuilding docs containing manually situated var references."
  {:author ["Reid 'arrdem' McKenzie <me@arrdem.com>"]
   :license "https://www.eclipse.org/legal/epl-v10.html"
   :source "https://github.com/arrdem/shelving/blob/f4e9c632c89a45e86064fcea2162d73137043d7e/src/dev/clj/compile_docs.clj"}
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.logging :as log])
  (:import java.io.File
           java.nio.file.Path))

(def var-doc-pattern
  #"(?ms)^(?<heading>#{2,}) \[(?<name>[^\]]*?)\]\(((?<path>[^\#]*?)#L(?<line>\d+))?\)\n(?<body>.*?)((?=^#{2,})|\Z)")

(comment
  (re-find var-doc-pattern
           "### [compile-docs/var-doc-pattern]()\n")

  (re-find var-doc-pattern
           "### [ledger.accounts/real-or-internal-account?](/Users/reid.mckenzie/doc/work/ledger/ledger/src/ledger/accounts.clj#L243)\n")

  (re-find var-doc-pattern
           "### [ledger.accounts/customer-account?](../src/ledger/accounts.clj#L266)\n"))

(def var-heading-pattern
  #"(?ms)^#{2,} \[(?<name>[^\]]+?)\]")

(def var-quote-pattern
  #"(?ms)\[?`#'(?<name>[^\`]+?)`(\]\(.*?\))?")

(defn ensure-trailing-newline [s]
  (if-not (.endsWith s "\n")
    (str s "\n") s))

(defn as-file [file-or-string]
  (if (instance? java.io.File file-or-string)
    file-or-string
    (io/file file-or-string)))

(defn relativize-path
  "Returns a relative path from the directory containing `document` to the `source` location."
  [document source]
  (.relativize (.getParent (.toPath (as-file document)))
               (.toPath (as-file source))))

(defn document-var [^clojure.lang.Var v ^File doc-file heading]
  (binding [*ns* (.ns v)]
    (let [{:keys [categories arglists doc stability line file]
           :as   var-meta} (meta v)]
      (with-out-str
        (printf "%s [%s/%s](%s#L%s)\n"
                heading
                (ns-name (.ns v)) (str/replace (name (.sym v)) #"\*" "\\\\*")
                (relativize-path doc-file (.getFile (io/resource file)))
                line)

        (doseq [params arglists]
          (printf " - `%s`\n" (cons (.sym v) params)))

        (printf "\n")

        (when (= stability :stability/unstable)
          (printf "**UNSTABLE**: This API will probably change in the future\n\n"))

        (printf (ensure-trailing-newline
                 (-> doc
                     (str/replace #"(?<!\n)\n[\s&&[^\n\r]]+" " ")
                     (str/replace #"\n\n[\s&&[^\n\r]]+" "\n\n")
                     (str/replace var-quote-pattern
                                  (fn [[match name]]
                                    (format "`%s`" (ns-resolve *ns* (symbol name))))))))
        (printf "\n")))))

(defn recompile-docs [files]
  (doseq [f files]
    (when (.contains (.getCanonicalPath f) "#")
      (.delete f)))

  (let [fcache
        (->> (map (juxt identity slurp) files)
             (into {}))

        links-map
        (reduce (fn [acc f]
                  (reduce (fn [acc [_ name]]
                            (assoc acc name [(.getCanonicalFile f)
                                             (str/replace name #"[./?]" "")]))
                          acc (re-seq var-heading-pattern (get fcache f))))
                {} files)]

    (doseq [f files]
      (try (let [buff (get fcache f)
                 buff* (-> buff
                           (str/replace var-doc-pattern
                                        (fn [[original heading name _ path line _body :as match]]
                                          (try (let [name (str/replace name #"\\\*" "*")
                                                     sym (symbol name)]
                                                 (require (symbol (namespace sym)))
                                                 (-> sym resolve (document-var f heading)))
                                               (catch Exception e
                                                 (log/fatal e)
                                                 original))))
                           (str/replace var-quote-pattern
                                        (fn [[original name suffix?]]
                                          (if-let [[target-file header] (get links-map name)]
                                            (format "[`#'%s`](%s#%s)" name
                                                    (if (not= f target-file)
                                                      (relativize-path f target-file)
                                                      "")
                                                    header)
                                            (do (log/warnf "%s: Couldn't find a link for %s!" f name)
                                                original))))
                           (str/replace #"\n{2,}\Z" "\n"))]
             (when  (not= buff buff*)
               (log/infof "Rebuilt %s" f)
               (spit f buff*)))

           (catch Exception e
             (log/infof "Encountered error while updating %s:\n%s" f e))))))

(defn recompile-docs!
  "Entry point suitable for a lein alias. Usable for automating doc rebuilding."
  [& args]
  (recompile-docs
   (map #(.getCanonicalFile %)
        (filter #(.endsWith (.getPath ^File %) ".md")
                (cons (io/file "README.md")
                      (file-seq (io/file "docs/")))))))
