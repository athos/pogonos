(ns pogonos.core
  (:require [pogonos.parser :as parser]
            [pogonos.protocols :as proto]
            [pogonos.reader :as reader]))

(defn render-string [s data]
  (let [in (reader/make-string-reader s)
        sb (StringBuilder.)
        out #(.append sb %)
        ctx [data]]
    (parser/parse in #(proto/render % ctx out))
    (str sb)))
