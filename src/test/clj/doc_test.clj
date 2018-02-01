(ns doc-test
  (:require [clojure.test :as t]
            [clojure.java.shell :refer [sh]]
            [clojure.string :as str]
            [clojure.java.io :as io])
  (:import [java.io StringReader]))

;; Literature:
;;   https://github.com/cldwalker/lein-spell/blob/master/src/leiningen/spell/core.clj
;;   http://aspell.net/0.50-doc/man-html/6_Writing.html#pipe

(def aspell-pattern
  #"& (?<word>\w+) (?<offset>\d+) (?<count>\d+): (?<alternatives>.*?)$")

(defn parse-aspell [text]
  (->> (str/split text #"\n")
       (keep (fn [line]
               (let [matcher (re-matcher aspell-pattern line)]
                 (when (.matches matcher)
                   {:type        :aspell/error
                    :word        (.group matcher "word")
                    :offset      (Long/parseLong (.group matcher "offset"))
                    :count       (Long/parseLong (.group matcher "count"))
                    :corrections (str/split (.group matcher "alternatives") #", ")}))))))

(defn aspell [reader]
  (let [{:keys [exit err out]} (sh "aspell" "-a" "--ignore=3" "list" :in reader)]
    (when-not (and (= 0 out)
                   (or (not-empty err)
                       (not-empty out)))
      (seq (parse-aspell out)))))

(defn format-error [{:keys [word corrections]}]
  (format "Unknown word %s%s" word
          (when corrections (format " possible corrections: %s" (str/join " " (take 3 corrections))))))

(defn format-spelling-errors [entity errors]
  (format "Error checking spelling of %s:\n%s" entity
          (->> errors
               (map #(str "- " (format-error %)))
               (str/join "\n"))))

#_(t/deftest test-docstring-spelling
    (doseq [ns        (all-ns)
            [sym var] (ns-publics ns)
            :when     (and (var? var)
                           (.contains (str var) "shelving")
                           (not (.contains (str var) "-test")))
            :let      [{:keys [doc] :as meta} (meta var)]]
      (t/is doc (format "Var %s is public and doesn't have a docstring!" var))
      (when doc
        (let [spell-errors (aspell (StringReader. doc))]
          (when-not (empty? spell-errors)
            (println
             (format-spelling-errors var spell-errors)))))))

#_(t/deftest test-markdown-spelling
    (doseq [f     (file-seq (io/file "."))
            :when (or (.endsWith (str (.toURI f)) ".md")
                      (.endsWith (str (.toURI f)) ".markdown"))]
      (let [spell-errors (aspell (io/reader f))]
        (t/is (empty? spell-errors)
              (format-spelling-errors f spell-errors)))))

(t/deftest test-markdown-vars
  (t/testing "Do we reference vars that don't exist? Indicates stale docs."
    (doseq [f           (file-seq (io/file "."))
            :when       (or (.endsWith (str (.toURI f)) ".md")
                            (.endsWith (str (.toURI f)) ".markdown"))
            :let        [buff (slurp (io/reader f))
                         symbols (re-seq #"(?:\n## )(?<namespace>[^/]*?)/(?<name>\S+)" buff)]
            [_ ns name] symbols
            :when       (.contains ns "shelving.")
            :let        [the-sym (symbol ns name)]]
      (require (symbol (namespace the-sym)))
      (t/is (find-var the-sym)
            (format "In file %s, couldn't find mentioned var %s/%s" f ns name)))))

