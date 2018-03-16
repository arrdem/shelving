(ns compile-docs
  "A quick hack for rebuilding docs containing manually situated var references."
  (:require [shelving.core :as sh]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.logging :as log])
  (:import java.io.File))

(def var-doc-pattern
  #"(?ms)^(?<heading>#{2,}) \[(?<name>[^\]]+?)\](\((?<path>[^\#]+?)#L(?<line>\d+)\))?\n(?<body>.*?)((?=^#{2,})|\Z)")

(def var-heading-pattern
  #"(?ms)^#{2,} \[(?<name>[^\]]+?)\]")

(def var-quote-pattern
  #"(?ms)\[?`#'(?<name>[^\`]+?)`(\]\(.*?\))?")

(defn ensure-trailing-newline [s]
  (if-not (.endsWith s "\n")
    (str s "\n") s))

(defn relativize-path [p]
  (str/replace p (.getCanonicalPath (io/file ".")) ""))

(defn document-var [^clojure.lang.Var v heading]
  (binding [*ns* (.ns v)]
    (let [{:keys [categories arglists doc stability line file]
           :as   var-meta} (meta v)]
      (with-out-str
        (printf "%s [%s/%s](%s#L%s)\n"
                heading
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
                            (assoc acc name (format "/%s#%s"
                                                    (.getPath f)
                                                    (str/replace name #"[./?]" ""))))
                          acc (re-seq var-heading-pattern (get fcache f))))
                {} files)]

    (doseq [f files]
      (try (let [buff (get fcache f)
                 buff* (-> buff
                           (str/replace var-doc-pattern
                                        (fn [[original heading name _ path line _body]]
                                          (try (let [sym (symbol name)]
                                                 (require (symbol (namespace sym)))
                                                 (or (some-> sym resolve (document-var heading))
                                                     original))
                                               (catch Exception e
                                                 original))))
                           (str/replace var-quote-pattern
                                        (fn [[original name suffix?]]
                                          (if-let [link (get links-map name)]
                                            (format "[`#'%s`](%s)" name link)
                                            (do (log/warnf "%s: Couldn't find a link for %s!" f name)
                                                original))))
                           (str/replace #"\n{2,}\Z" "\n"))]
             (when  (not= buff buff*)
               (log/infof "Rebuilt %s" f)
               (spit f buff*)))
           (catch Exception e
             (log/errorf "Encountered error while updating %s:\n%s" f e))))))

(defn recompile-docs!
  "Entry point suitable for a lein alias. Usable for automating doc rebuilding."
  [& args]
  (recompile-docs
   (filter #(.endsWith (.getPath ^File %) ".md")
           (file-seq (io/file "docs/")))))
