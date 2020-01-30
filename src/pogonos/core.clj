(ns pogonos.core
  (:require [pogonos.parser :as parser]
            [pogonos.reader :as reader]
            [pogonos.renderer :as renderer]))

(defn render-string [s data]
  (let [in (reader/make-string-reader s)
        sb (StringBuilder.)
        out #(.append sb %)
        stack [data]]
    (parser/process in (fn [x] (renderer/render* stack out x)))
    (str sb)))
