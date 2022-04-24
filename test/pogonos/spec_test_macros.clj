(ns pogonos.spec-test-macros
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :as t]
            [pogonos.core :as pogonos]))

(defn- expand-spec-test [{:keys [desc expected template data partials]}]
  (let [data' (cond->> data
                (map? data)
                (reduce-kv (fn [m k v]
                             (assoc m k
                                    (if (and (map? v)
                                             (= (:__tag__ v) "code"))
                                      (read-string (str "(do " (:clojure v) ")"))
                                      v)))
                           {}))]
   `(t/testing ~desc
      (t/is
       (= ~expected
          (pogonos/render-string ~template ~data' {:partials ~partials}))))))

(defn- expand-spec-tests [^java.io.File file]
  (with-open [r (io/reader file)]
    (let [json (json/read r :key-fn keyword)]
      `(t/deftest ~(symbol (str/replace (.getName file) #".json" ""))
         ~@(map expand-spec-test (:tests json))))))

(defmacro import-spec-tests []
  `(do ~@(for [file (->> (io/file (io/resource "mustache-spec/specs"))
                         file-seq
                         (filter #(str/ends-with? (.getName %) ".json")))]
           (expand-spec-tests file))))
