(ns pogonos.render
  (:require #?(:cljs [goog.string :as gstr])
            [pogonos.nodes :as nodes]
            [pogonos.parse :as parse]
            [pogonos.partials :as partials]
            [pogonos.protocols :as proto]
            [pogonos.reader :as reader]
            [pogonos.stringify :as stringify])
  #?(:clj
     (:import [pogonos.nodes
               DynamicPartial Inverted Partial Root
               Section UnescapedVariable Variable])))

(def ^:dynamic *partials-resolver*)
;; TODO: Now it's just a mitigation for inefficiency around partials rendering.
;; There should be a more general caching mechanism in the future.
(def ^:dynamic *partials-cache*)

(defn escape [^String s]
  #?(:clj
     (let [len (.length s)
           sb (StringBuilder.)]
       (loop [i 0]
         (when (< i len)
           (let [c (char (.charAt s i))]
             (case c
               \& (.append sb "&amp;")
               \< (.append sb "&lt;")
               \> (.append sb "&gt;")
               \" (.append sb "&quot;")
               \' (.append sb "&#39;")
               (.append sb c)))
           (recur (inc i))))
       (.toString sb))
     :cljs (gstr/htmlEscape s)))

(defn lookup [ctx keys]
  (proto/lookup ctx keys))

(defn render* [ctx out x]
  (if (string? x)
    (out x)
    (proto/render x ctx out)))

(defn- render-variable [ctx out var unescaped?]
  (when-some [val (lookup ctx (:keys var))]
    (let [escape-fn (if unescaped? identity escape)]
      (if (fn? val)
        (parse/parse (reader/make-string-reader (str (val)))
                     #(render* ctx (comp out escape-fn) %))
        (out (escape-fn (str val)))))))

(defn render
  ([ctx out x]
   (render ctx out x {}))
  ([ctx out x {:keys [partials]}]
   (binding [*partials-resolver* (some-> partials partials/->resolver)
             *partials-cache* {}]
     (render* ctx out x))))

(extend-protocol proto/IRenderable
  #?(:clj Object :cljs object)
  (render [_ _ctx _out])

  #?(:clj Root :cljs nodes/Root)
  (render [this ctx out]
    (doseq [node (:body this)]
      (render* ctx out node)))

  #?(:clj Variable :cljs nodes/Variable)
  (render [this ctx out]
    (render-variable ctx out this (:unescaped? this)))

  #?(:clj UnescapedVariable :cljs nodes/UnescapedVariable)
  (render [this ctx out]
    (render-variable ctx out this true))

  #?(:clj Section :cljs nodes/Section)
  (render [this ctx out]
    (let [val (lookup ctx (:keys this))]
      (cond (not val) nil

            (map? val)
            (doseq [node (:nodes this)]
              (render* (proto/push ctx val) out node))

            (and (coll? val) (sequential? val))
            (when (seq val)
              (doseq [e val, node (:nodes this)]
                (render* (proto/push ctx e) out node)))

            (fn? val)
            (let [{:keys [open close]} (meta this)
                  ;; the last element of the section body must be SectionEnd,
                  ;; which has to be omitted prior to stringification
                  body (-> (pop (:nodes this))
                           (stringify/stringify open close)
                           val)]
              (parse/parse (reader/make-string-reader body)
                           #(render* ctx out %)
                           {:open-delim open :close-delim close}))

            :else
            (doseq [node (:nodes this)]
              (render* (proto/push ctx val) out node)))))

  #?(:clj Inverted :cljs nodes/Inverted)
  (render [this ctx out]
    (let [val (lookup ctx (:keys this))]
      (when (or (not val)
                (and (coll? val) (sequential? val) (empty? val)))
        (doseq [node (:nodes this)]
          (render* ctx out node))))))

(defn- render-partial [ctx partial-name indent out]
  (if-let [cached (get *partials-cache* [partial-name indent])]
    (render* ctx out cached)
    (when-let [r (partials/resolve *partials-resolver* partial-name)]
      (let [buf (parse/make-node-buffer)]
        (try
          (parse/parse r buf {:source (name partial-name) :indent indent})
          (let [node (nodes/->Root (buf))]
            (render* ctx out node)
            (when (partials/cacheable? *partials-resolver* partial-name)
              (set! *partials-cache*
                    (assoc *partials-cache* [partial-name indent] node))))
          (finally
            (reader/close r)))))))

(extend-protocol proto/IRenderable
  #?(:clj Partial :cljs nodes/Partial)
  (render [{partial-name :name :keys [indent]} ctx out]
    (render-partial ctx partial-name indent out))

  #?(:clj DynamicPartial :cljs nodes/DynamicPartial)
  (render [{:keys [keys indent]} ctx out]
    (let [val (lookup ctx keys)
          partial (cond (keyword? val) val
                        (string? val) (keyword nil val)
                        (symbol? val) (keyword (namespace val) (name val))
                        :else val)]
      (render-partial ctx partial indent out))))
