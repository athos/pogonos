(ns pogonos.api
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [pogonos.core :as pg]
            [pogonos.error :as error]
            [pogonos.output :as out]
            [pogonos.reader :as reader])
  (:import [java.io File PushbackReader]
           [java.util.regex Pattern]))

(defn render
  "Renders the given Mustache template.

  One of the following option can be specified as a template source:
   - :string    Renders the given template string
   - :file      Renders the specified template file
   - :resource  Renders the specified template resource on the classpath

  If none of these are specified, the template will be read from stdin.

  The following options can also be specified:
   - :output     Path to the output file. If not specified, the rendering result
                 will be emitted to stdout by default.
   - :data       Map of the values passed to the template
   - :data-file  If specified, reads an edn map from the file specified by that
                 path and pass it to the template"
  {:added "0.2.0"
   :org.babashka/cli
   {:spec
    {:string
     {:desc "Renders the given template string",
      :ref "<template string>", :coerce :string, :alias :s}
     :file
     {:desc "Renders the specified template file",
      :ref "<file>", :coerce :string, :alias :f}
     :resource
     {:desc "Renders the specified template resource on the classpath",
      :ref "<resource path>", :coerce :string, :alias :r}
     :output
     {:desc "Path to the output file. If not specified, the rendering result will be emitted to stdout by default."
      :ref "<file>", :coerce :string, :alias :o}
     :data
     {:desc "Map of the values passed to the template",
      :ref "<edn>", :coerce :edn, :alias :d}
     :data-file
     {:desc "If specified, reads an edn map from the file specified by that path and pass it to the template",
      :ref "<file>", :coerce :string, :alias :D}}
    :order [:string :file :resource :output :data :data-file]}}
  [{:keys [string file resource data data-file output] :as opts}]
  (let [data (or (when data-file
                   (with-open [r (-> (io/reader (str data-file))
                                     PushbackReader.)]
                     (edn/read r)))
                 data)
        out (if output
              (out/to-file (str output))
              (out/to-stdout))
        opts' (-> opts
                  (assoc :output out)
                  (dissoc :string :file :resource :data :data-file))]
    (cond string (pg/render-string string data opts')
          file (pg/render-file (str file) data opts')
          resource (pg/render-resource (str resource) data opts')
          :else (pg/render-input (reader/->reader *in*) data opts'))))

(def ^:private ^:const path-separator
  (re-pattern (Pattern/quote (System/getProperty "path.separator"))))

(defn- ->matcher [x]
  (let [regexes (if (coll? x)
                  (mapv (comp re-pattern str) x)
                  [(re-pattern (str x))])]
    (fn [s]
      (boolean (some #(re-find % s) regexes)))))

(def ^:private ^:dynamic *errors*)

(defn- with-error-handling [opts f]
  (try
    (f)
    (catch Exception e
      (if (::error/type (ex-data e))
        (do (when (or (not (:quiet opts)) (:only-show-errors opts))
              (binding [*out* *err*]
                (println "[ERROR]" (ex-message e))))
            (set! *errors* (conj *errors* e)))
        (throw e)))))

(defn- check-inputs [inputs {:keys [include-regex exclude-regex] :as opts}]
  (let [include? (if include-regex
                   (->matcher include-regex)
                   (constantly true))
        exclude? (if exclude-regex
                   (->matcher exclude-regex)
                   (constantly false))]
    (doseq [{:keys [name input]} inputs
            :when (and (include? name) (not (exclude? name)))]
      (when (and (not (:quiet opts)) (not (:only-show-errors opts)))
        (binding [*out* *err*]
          (println "Checking template" name)))
      (reader/with-open [r (reader/->reader input)]
        (with-error-handling opts
          #(pg/check-input r (assoc opts :source name)))))))

(defn- check-files [files opts]
  (-> (for [file files
            :let [file (io/file file)]]
        {:name (.getPath file) :input file})
      (check-inputs opts)))

(defn- check-resources [resources opts]
  (-> (for [res resources]
        {:name res :input (io/resource res)})
      (check-inputs opts)))

(defn- check-dirs [dirs opts]
  (-> (for [dir dirs
            ^File file (file-seq (io/file dir))
            :when (.isFile file)]
        file)
      (check-files opts)))

(defn- split-path [path]
  (if (sequential? path)
    (mapv str path)
    (str/split (str path) path-separator)))

(defn check
  "Checks if the given Mustache template contains any syntax error.

  The following options cab be specified as a template source:
   - :string    Checks the given template string
   - :file      Checks the specified template file
   - :dir       Checks the template files in the specified directory
   - :resource  Checks the specified template resource on the classpath

  If none of these are specified, the template will be read from stdin.

  For the :file/:dir/:resource options, two or more files/directories/resources
  may be specified by delimiting them with the file path separator (i.e. ':' (colon)
  on Linux/macOS and ';' (semicolon) on Windows).

  When multiple templates are checked using the :file/:dir/:resource options,
  they can be filtered with the :include-regex and/or exclude-regex options.

  The verbosity of the syntax check results may be adjusted to some extent with
  the following options:
   - :only-show-errors         Hides progress messages
   - :suppress-verbose-errors  Suppresses verbose error messages"
  {:added "0.2.0"
   :org.babashka/cli
   {:spec
    {:string
     {:desc "Checks the given template string",
      :ref "<template string>", :coerce :string, :alias :s}
     :file
     {:desc "Checks the specified template file(s)",
      :ref "<file>", :coerce :string, :alias :f}
     :dir
     {:desc "Checks the template files in the specified directory",
      :ref "<dir>", :coerce :string, :alias :d}
     :resource
     {:desc "Checks the specified template resource(s) on the classpath",
      :ref "<resource path>", :coerce :string, :alias :r}
     :include-regex
     {:desc "Regex pattern for paths of templates to be checked",
      :ref "<regex>", :coerce :string, :alias :i}
     :exclude-regex
     {:desc "Regex pattern for paths of templates not to be checked",
      :ref "<regex>", :coerce :string, :alias :e}
     :only-show-errors
     {:desc "Hides progress messages", :coerce :boolean, :alias :S}
     :suppress-verbose-errors
     {:desc "Suppress verbose error messages" :coerce :boolean, :alias :E}}
    :order [:string :file :dir :resource
            :only-show-errors :suppress-verbose-errors]}}
  [{:keys [string file dir resource on-failure] :or {on-failure :exit} :as opts}]
  (binding [*errors* []]
    (cond string (with-error-handling opts #(pg/check-string string opts))
          file (check-files (split-path file) opts)
          resource (check-resources (split-path resource) opts)
          dir (check-dirs (split-path dir) opts)
          :else (with-error-handling opts
                  #(pg/check-input (reader/->reader *in*) opts)))
    (when (seq *errors*)
      (case on-failure
        :exit (System/exit 1)
        :throw (throw (ex-info "Template checking failed" {:errors *errors*}))
        nil))))
