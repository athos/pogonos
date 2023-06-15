(ns pogonos.core
  (:require #?(:clj [clojure.java.io :as io])
            [pogonos.error :as error]
            [pogonos.nodes :as nodes]
            [pogonos.output :as output]
            [pogonos.parse :as parse]
            #?(:clj [pogonos.partials :as partials])
            [pogonos.reader :as reader]
            [pogonos.render :as render])
  #?(:clj (:import [java.io FileNotFoundException])))

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
               (when (nodes/visible? x)
                 (buf x)))
         opts (fixup-options opts #?(:clj partials/resource-partials))]
     (parse/parse in out opts)
     (with-meta
       (nodes/->Root (buf))
       {:options opts}))))

(defn parse-string
  "Parses the given template string and returns the parsed template.

  Optionally takes an option map. The option map may have the following keys:

  - :suppress-verbose-errors  If set to true, suppress verbose error messages.
                              Defaults to false."
  ([s]
   (parse-string s {}))
  ([s opts]
   (parse-input (reader/make-string-reader s) opts)))

#?(:clj
   (defn parse-file
     "Takes a file name that contains a Mustache template, and parses the template.

  Optionally takes an option map. See the docstring of `parse-string` for
  the available options."
     ([file]
      (parse-file file {}))
     ([file opts]
      (let [f (io/as-file file)]
        (if (.exists f)
          (let [opts (fixup-options opts partials/file-partials)]
            (reader/with-open [in (reader/make-file-reader f)]
              (parse-input in opts)))
          (throw (FileNotFoundException. (.getName f))))))))

#?(:clj
   (defn parse-resource
     "Takes a resource name that contains a Mustache template, and parses the template.

  Optionally takes an option map. See the docstring of `parse-string` for
  the available options."
     ([res]
      (parse-resource res {}))
     ([res opts]
      (if-let [res (io/resource res)]
        (let [opts (fixup-options opts partials/resource-partials)]
          (reader/with-open [in (reader/make-file-reader res)]
            (parse-input in opts)))
        (throw (FileNotFoundException. res))))))

(defn render
  "Takes a parsed template (generated from parse-* functions)
  and a context map, and renders the template.

  Optionally takes an option map. The option map may have the following keys:

  - :output    Specify where to output the rendering result. Defaults to
               pogonos.output/to-string (i.e. generating a string).
  - :partials  Specify where to look for partials. Either a map or a partials
               resolver (see pogonos.partials) can be specified. Defaults to
               pogonos.partials/resource-partials."
  ([template data]
   (render template data {}))
  ([template data opts]
   (let [{:keys [output] :as opts} (-> (:options (meta template))
                                       (merge opts)
                                       fixup-options)
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
  "Takes a Mustache template string and a context map, and renders the template.

  Optionally takes an option map. See the docstring of `parse-string` and `render`
  for the available options."
  ([s data]
   (render-string s data {}))
  ([s data opts]
   (render-input (reader/make-string-reader s) data opts)))

#?(:clj
   (defn render-file
     "Takes a file name that contains a Mustache template, and renders the template.

  Optionally takes an option map. See the docstring of `parse-string` and `render`
  for the available options."
     ([file data]
      (render-file file data {}))
     ([file data opts]
      (let [f (io/as-file file)]
        (if (.exists f)
          (let [opts (-> opts
                         (fixup-options partials/file-partials)
                         (assoc :source (.getName f)))]
            (reader/with-open [in (reader/make-file-reader f)]
              (render-input in data opts)))
          (throw (FileNotFoundException. (.getName f))))))))

#?(:clj
   (defn render-resource
     "Takes a resource name that contains a Mustache template, and renders the template.

  Optionally takes an option map. See the docstring of `parse-string` and `render`
  for the available options."
     ([res data]
      (render-resource res data {}))
     ([res data opts]
      (if-let [res (io/resource res)]
        (let [opts (cond-> (fixup-options opts partials/resource-partials)
                     (string? res) (assoc :source res))]
          (reader/with-open [in (reader/make-file-reader res)]
            (render-input in data opts)))
        (throw (FileNotFoundException. res))))))

(defn check-input
  ([in] (check-input in {}))
  ([in opts]
   (parse/parse in (fn [_]) opts)))

(defn check-string
  "Parses the given template string and throws if it contains a syntax error.
  Otherwise return nil.

  Optionally takes an option map. The option map may have the following keys:

  - :suppress-verbose-errors  If set to true, suppress verbose error messages.
                              Defaults to false."
  {:added "0.2.0"}
  ([s] (check-string s {}))
  ([s opts]
   (check-input (reader/make-string-reader s) opts)))

#?(:clj
   (defn check-file
     "Takes a file name that contains a Mustache template, and performs syntax check.
Throws if there is a syntax error, otherwise returns nil.

Optionally takes an option map. See the docstring of `check-string` for
the available options."
     {:added "0.2.0"}
     ([file] (check-file file {}))
     ([file opts]
      (let [f (io/as-file file)]
        (if (.exists f)
          (reader/with-open [in (reader/make-file-reader f)]
            (check-input in (assoc opts :source (.getName f))))
          (throw (FileNotFoundException. (.getName f))))))))

#?(:clj
   (defn check-resource
     "Takes a resource name that contains a Mustache template, and performs syntax check.

Optionally takes an option map. See the docstring of `check-string` for
the available options."
     {:added "0.2.0"}
     ([res] (check-resource res {}))
     ([res opts]
      (if-let [res (io/resource res)]
        (let [opts (cond-> opts (string? res) (assoc :source res))]
          (reader/with-open [in (reader/make-file-reader res)]
            (check-input in opts)))
        (throw (FileNotFoundException. res))))))

(defn perr
  "Prints detailed error message. The argument `err` must be a parse-time exception
  thrown in render or parse functions. Otherwise, nothing will be displayed."
  ([]
   (perr *e))
  ([err]
   (let [data (ex-data err)]
     (when-let [message (::error/message data)]
       (binding [error/*suppress-verbose-errors* false]
         (println (error/stringify-error message data)))))))
