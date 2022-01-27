(ns pogonos.api
  (:require [pogonos.core :as pg]
            [pogonos.output :as out]
            [pogonos.reader :as reader]))

(defn render [{:keys [string file resource data output] :as opts}]
  (let [out (if output
              (out/to-file (str output))
              (out/to-stdout))
        opts' (-> opts
                  (assoc :output out)
                  (dissoc :string :file :resource :data))]
    (cond string (pg/render-string string data opts')
          file (pg/render-file (str file) data opts')
          resource (pg/render-resource (str resource) data opts')
          :else (pg/render-input (reader/->reader *in*) data opts'))))
