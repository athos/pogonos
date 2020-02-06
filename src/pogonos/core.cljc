(ns pogonos.core
  (:require [pogonos.nodes :as nodes]
            [pogonos.output :as output]
            [pogonos.parse :as parse]
            #?(:clj [pogonos.partials-resolver :as pres])
            [pogonos.reader :as reader]
            [pogonos.render :as render])
  #?(:clj (:import [java.io Closeable])))

(defn parse [s]
  (let [in (reader/make-string-reader s)
        buf (volatile! [])
        out (fn [x]
              (when-not (satisfies? nodes/Invisible x)
                (vswap! buf conj x)))]
    (parse/parse in out)
    (nodes/->Root @buf)))

(defn render
  ([template data]
   (render template data {}))
  ([template data
    {:keys [output] :or {output (output/string-output)} :as opts}]
   (let [out #(output/append output %)]
     (render/render [data] out template opts)
     (output/complete output))))

(defn render-input
  ([in data]
   (render-input in data {}))
  ([in data {:keys [output] :or {output (output/string-output)} :as opts}]
   (let [out #(output/append output %)
         ctx [data]]
     (parse/parse in #(render/render ctx out % opts))
     (output/complete output))))

(defn render-string
  ([s data]
   (render-string s data {}))
  ([s data opts]
   (render-input (reader/make-string-reader s) data opts)))

#?(:clj
   (defn render-file
     ([file data]
      (render-file file data {}))
     ([file data opts]
      (with-open [in ^Closeable (reader/make-file-reader file)]
        (render-input in data opts)))))

#?(:clj
   (defn set-default-partials-base-path! [base-path]
     (pres/set-default-partials-base-path! base-path)))
