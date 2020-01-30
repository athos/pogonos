(ns pogonos.core
  (:require [pogonos.parse :as parse]
            [pogonos.read :as read]
            [pogonos.render :as render]))

(defn render-string [s data]
  (let [in (read/make-string-reader s)
        sb (StringBuilder.)
        out #(.append sb %)
        ctx [data]]
    (parse/parse in #(render/render ctx out %))
    (str sb)))
