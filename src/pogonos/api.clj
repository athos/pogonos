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

(defn- check-files [dir opts]
  (let [dirs (str/split dir path-separator)]
    (doseq [dir dirs
            ^File file (file-seq (io/file dir))
            :when (.isFile file)]
      (pg/check-file file opts))))

(defn check [{:keys [string file dir resource] :as opts}]
  (try
    (cond string (pg/check-string string opts)
          file (pg/check-file (str file) opts)
          dir (check-files (str dir) opts)
          resource (pg/check-resource (str resource) opts)
          :else (pg/check-input (reader/->reader *in*) opts))
    (catch Exception e
      (if (::error/type (ex-data e))
        (throw (ex-info (str "Template parsing failed: " (ex-message e))
                        (ex-data e)))
        (throw e)))))
