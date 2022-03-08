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
  {:added "0.2.0"}
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
      (with-open [r (reader/->reader input)]
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
  {:added "0.2.0"}
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
