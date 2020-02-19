(ns pogonos.core
  (:require #?(:clj [clojure.java.io :as io])
            [pogonos.error :as error]
            [pogonos.nodes :as nodes]
            [pogonos.output :as output]
            [pogonos.parse :as parse]
            #?(:clj [pogonos.partials :as partials])
            [pogonos.reader :as reader]
            [pogonos.render :as render])
  #?(:clj (:import [java.io Closeable File FileNotFoundException])))

(def ^:private default-options (atom nil))

(defn set-default-options! [options]
  (reset! default-options options)
  nil)

(defn- fixup-options
  ([opts]
   (fixup-options opts nil))
  ([opts default-partials]
   (let [opts (merge @default-options opts)]
     (cond-> opts
       (and (not (contains? opts :partials))
            (not (nil? default-partials)))
       (assoc :partials (default-partials))

       (not (contains? opts :output))
       (assoc :output (output/to-string))))))

(defn parse-input
  ([in]
   (parse-input in {}))
  ([in opts]
   (let [buf (parse/make-node-buffer)
         out (fn [x]
               (when-not (satisfies? nodes/Invisible x)
                 (buf x)))
         opts (fixup-options opts #?(:clj partials/resource-partials))]
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
          (let [opts (fixup-options opts partials/file-partials)
                in (reader/make-file-reader f)]
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
        (let [opts (fixup-options opts partials/resource-partials)]
          (parse-file res opts))
        (throw (FileNotFoundException. res))))))

(defn render
  ([template data]
   (render template data {}))
  ([template data opts]
   (let [{:keys [output] :as opts} (fixup-options opts)
         out (output)]
     (render/render (list data) out template opts)
     (out))))

(defn render-input
  ([in data]
   (render-input in data {}))
  ([in data opts]
   (let [ctx (list data)
         {:keys [output]
          :as opts} (fixup-options opts #?(:clj partials/resource-partials))
         out (output)]
     (parse/parse in #(render/render ctx out % opts) opts)
     (out))))

(defn render-string
  ([s data]
   (render-string s data {}))
  ([s data opts]
   (render-input (reader/make-string-reader s) data opts)))

#?(:clj
   (defn render-file
     ([file data]
      (render-file file data {}))
     ([file data {:keys [partials] :as opts}]
      (let [f (io/as-file file)]
        (if (.exists f)
          (let [opts (-> opts
                         (fixup-options partials/file-partials)
                         (assoc :source (.getName f)))
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
        (let [opts (fixup-options opts partials/resource-partials)]
          (render-file res data opts))
        (throw (FileNotFoundException. res))))))

(defn perr
  ([]
   (perr *e))
  ([err]
   (let [data (ex-data err)]
     (binding [error/*show-error-details* true]
       (println (error/stringify-error (:message data) data))))))
