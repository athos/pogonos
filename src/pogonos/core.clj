(ns pogonos.core
  (:require [pogonos.parse :as parse]
            [pogonos.protocols :as proto]
            [pogonos.read :as read]
            [pogonos.render]))

(defn render-string [s data]
  (let [in (read/make-string-reader s)
        sb (StringBuilder.)
        out #(.append sb %)
        ctx [data]]
    (parse/parse in #(proto/render % ctx out))
    (str sb)))
