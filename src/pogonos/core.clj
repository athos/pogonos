(ns pogonos.core
  (:require [pogonos.nodes :as nodes]
            [pogonos.output :as output]
            [pogonos.parse :as parse]
            [pogonos.read :as read]
            [pogonos.render :as render])
  (:import [java.io Closeable]))

(defn parse [s]
  (let [in (read/make-string-reader s)
        buf (volatile! [])
        out (fn [x]
              (when-not (satisfies? nodes/Invisible x)
                (vswap! buf conj x)))]
    (parse/parse in out)
    (nodes/->Root @buf)))

(defn render
  ([template data]
   (render template data {}))
  ([template data {:keys [output] :or {output (output/string-output)}}]
   (let [out #(output/append output %)]
     (render/render [data] out template)
     (output/complete))))

(defn render-input
  ([in data]
   (render-input in data {}))
  ([in data {:keys [output] :or {output (output/string-output)}}]
   (let [out #(output/append output %)
         ctx [data]]
     (parse/parse in #(render/render ctx out %))
     (output/complete output))))

(defn render-string
  ([s data]
   (render-string s data {}))
  ([s data opts]
   (render-input (read/make-string-reader s) data opts)))

(defn render-file
  ([file data]
   (render-file file data {}))
  ([file data opts]
   (with-open [in ^Closeable (read/make-file-reader file)]
     (render-input in data opts))))
