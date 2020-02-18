(ns pogonos.render
  (:require [clojure.string :as str]
            #?(:cljs [goog.string :as gstr])
            [pogonos.nodes :as nodes]
            [pogonos.parse :as parse]
            [pogonos.partials :as partials]
            [pogonos.protocols :as proto]
            [pogonos.reader :as reader]
            [pogonos.stringify :as stringify])
  #?(:clj
     (:import [pogonos.nodes
               Inverted Partial Root Section UnescapedVariable Variable])))

(def ^:dynamic *partials-resolver*)

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
  (if-let [k (peek keys)]
    (when-let [v (loop [ctx ctx]
                   (when-let [v (peek ctx)]
                     (if (and (map? v)
                              (not #?(:clj (identical? (v k ::none) ::none)
                                      :cljs (keyword-identical? (v k ::none) ::none))))
                       v
                       (recur (next ctx)))))]
      (if (next keys)
        (get-in v keys)
        (v k)))
    (peek ctx)))

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
   (binding [*partials-resolver* (or (some-> partials partials/->resolver)
                                     *partials-resolver*)]
     (render* ctx out x))))

(extend-protocol proto/IRenderable
  #?(:clj Object :cljs object)
  (render [this ctx out])

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
              (render* (conj ctx val) out node))

            (and (coll? val) (sequential? val))
            (when (seq val)
              (doseq [e val, node (:nodes this)]
                (render* (conj ctx e) out node)))

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
              (render* (conj ctx val) out node)))))

  #?(:clj Inverted :cljs nodes/Inverted)
  (render [this ctx out]
    (let [val (lookup ctx (:keys this))]
      (when (or (not val)
                (and (coll? val) (sequential? val) (empty? val)))
        (doseq [node (:nodes this)]
          (render* ctx out node)))))

  #?(:clj Partial :cljs nodes/Partial)
  (render [this ctx out]
    (let [name (:name this)]
      (when-let [r (partials/resolve *partials-resolver* name)]
        (try
          (parse/parse r #(render* ctx out %)
                       {:source name :indent (:indent this)})
          (finally
            (reader/close r)))))))
