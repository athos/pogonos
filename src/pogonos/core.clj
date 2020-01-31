(ns pogonos.core
  (:require [pogonos.nodes :as nodes]
            [pogonos.parse :as parse]
            [pogonos.read :as read]
            [pogonos.render :as render]))

(defn parse [s]
  (let [in (read/make-string-reader s)
        buf (volatile! [])
        out (fn [x]
              (when-not (satisfies? nodes/Invisible x)
                (vswap! buf conj x)))]
    (parse/parse in out)
    (nodes/->Root @buf)))

(defn render [template data]
  (let [sb (StringBuilder.)
        out #(.append sb %)]
    (render/render [data] out template)
    (str sb)))

(defn render-string [s data]
  (let [in (read/make-string-reader s)
        sb (StringBuilder.)
        out #(.append sb %)
        ctx [data]]
    (parse/parse in #(render/render ctx out %))
    (str sb)))

(defn render-file [file data]
  (with-open [in (read/make-file-reader file)]
    (let [sb (StringBuilder.)
          out #(.append sb %)
          ctx [data]]
      (parse/parse in #(render/render ctx out %))
      (str sb))))
