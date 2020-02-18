(ns pogonos.core
  (:require #?(:clj [clojure.java.io :as io])
            [pogonos.error :as error]
            [pogonos.nodes :as nodes]
            [pogonos.output :as output]
            [pogonos.parse :as parse]
            #?(:clj [pogonos.partials-resolver :as pres])
            [pogonos.reader :as reader]
            [pogonos.render :as render])
  #?(:clj (:import [java.io Closeable File FileNotFoundException])))

(defn parse-input
  ([in]
   (parse-input in {}))
  ([in opts]
   (let [buf (parse/make-node-buffer)
         out (fn [x]
               (when-not (satisfies? nodes/Invisible x)
                 (buf x)))]
     (parse/parse in out opts)
     (nodes/->Root (buf)))))

(defn parse-string
  ([s]
   (parse-string s {}))
  ([s opts]
   (parse-input (reader/make-string-reader s) opts)))

#?(:clj
   (defn parse-file
     ([file]
      (parse-file file {}))
     ([file opts]
      (let [f (io/as-file file)]
        (if (.exists f)
          (let [in (reader/make-file-reader f)]
            (try
              (parse-input in opts)
              (finally
                (reader/close in))))
          (throw (FileNotFoundException. (.getName f))))))))

#?(:clj
   (defn parse-resource
     ([res]
      (parse-resource res {}))
     ([res opts]
      (if-let [res (io/resource res)]
        (parse-file res opts)
        (throw (FileNotFoundException. res))))))

(defn render
  ([template data]
   (render template data {}))
  ([template data
    {:keys [output] :or {output (output/string-output)} :as opts}]
   (render/render (list data) output template opts)
   (output)))

(defn render-input
  ([in data]
   (render-input in data {}))
  ([in data {:keys [output] :or {output (output/string-output)} :as opts}]
   (let [ctx (list data)]
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
      (let [f (io/as-file file)]
        (if (.exists f)
          (let [opts (assoc opts :source (.getName f))
                in (reader/make-file-reader f)]
            (try
              (render-input in data opts)
              (finally
                (reader/close in))))
          (throw (FileNotFoundException. (.getName f))))))))

#?(:clj
   (defn render-resource
     ([res data]
      (render-resource res data {}))
     ([res data opts]
      (if-let [res (io/resource res)]
        (render-file res data opts)
        (throw (FileNotFoundException. res))))))

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
