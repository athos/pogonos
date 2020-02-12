(ns pogonos.core
  (:require [pogonos.error :as error]
            [pogonos.nodes :as nodes]
            [pogonos.output :as output]
            [pogonos.parse :as parse]
            #?(:clj [pogonos.partials-resolver :as pres])
            [pogonos.reader :as reader]
            [pogonos.render :as render])
  #?(:clj (:import [java.io Closeable File])))

(defn parse
  ([s]
   (parse s {}))
  ([s opts]
   (let [in (reader/make-string-reader s)
         buf (parse/make-node-buffer)
         out (fn [x]
               (when-not (satisfies? nodes/Invisible x)
                 (buf x)))]
     (parse/parse in out opts)
     (nodes/->Root (buf)))))

(defn render
  ([template data]
   (render template data {}))
  ([template data
    {:keys [output] :or {output (output/string-output)} :as opts}]
   (render/render [data] output template opts)
   (output)))

(defn render-input
  ([in data]
   (render-input in data {}))
  ([in data {:keys [output] :or {output (output/string-output)} :as opts}]
   (let [ctx [data]]
     (parse/parse in #(render/render ctx output % opts) opts)
     (output))))

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
      (let [opts (cond-> opts
                   (string? file) (assoc :source file)
                   (instance? File file) (assoc :source (.getName ^File file)))]
        (with-open [in ^Closeable (reader/make-file-reader file)]
          (render-input in data opts))))))

#?(:clj
   (defn set-default-partials-base-path! [base-path]
     (pres/set-default-partials-base-path! base-path)))

(defn perr
  ([]
   (perr *e))
  ([err]
   (let [data (ex-data err)]
     (binding [error/*show-detailed-error* true]
       (error/print-error (:message data) data))
     (newline))))
