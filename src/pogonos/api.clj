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

(defn render [{:keys [string file resource data data-file output] :as opts}]
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

(defn- str->matcher [s]
  (comp boolean (partial re-find (re-pattern s))))

(defn- check-files [dirs {:keys [file-regex file-exclude-regex] :as opts}]
  (let [include? (if file-regex
                   (str->matcher file-regex)
                   (constantly true))
        exclude? (if file-exclude-regex
                   (str->matcher file-exclude-regex)
                   (constantly false))]
    (doseq [dir dirs
            ^File file (file-seq (io/file dir))
            :let [path (.getPath file)]
            :when (and (.isFile file) (include? path) (not (exclude? path)))]
      (pg/check-file file opts))))

(defn check [{:keys [string file dir resource] :as opts}]
  (try
    (cond string (pg/check-string string opts)
          file (pg/check-file (str file) opts)
          dir (check-files (if (sequential? dir)
                             (mapv str dir)
                             (str/split (str dir) path-separator))
                           opts)
          resource (pg/check-resource (str resource) opts)
          :else (pg/check-input (reader/->reader *in*) opts))
    (catch Exception e
      (if (::error/type (ex-data e))
        (throw (ex-info (str "Template parsing failed: " (ex-message e))
                        (ex-data e)))
        (throw e)))))
