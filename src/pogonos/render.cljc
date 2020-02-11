(ns pogonos.render
  (:require [clojure.string :as str]
            [pogonos.partials-resolver :as pres]
            [pogonos.nodes :as nodes]
            [pogonos.parse :as parse]
            [pogonos.protocols :as proto]
            [pogonos.reader :as reader]
            [pogonos.stringify :as stringify])
  #?(:clj
     (:import [pogonos.nodes
               Inverted Partial Root Section UnescapedVariable Variable])))

(def ^:dynamic *partials-resolver*
  #?(:clj (pres/file-partials-resolver)))

(defn escape [s]
  (str/replace s #"[&<>\"']"
               #({"&" "&amp;", "<" "&lt;", ">" "&gt;"
                  "\"" "&quot;", "'" "&#39;"}
                 (str %))))

(defn lookup [ctx keys]
  (if (seq keys)
    (let [k (first keys)]
      (when-let [x (->> ctx
                        (filter #(and (map? %) (contains? % k)))
                        first)]
        (get-in x keys)))
    (first ctx)))

(defn render* [ctx out x]
  (if (string? x)
    (out x)
    (proto/render x ctx out)))

(defn- render-variable [ctx out var unescaped?]
  (let [val (lookup ctx (:keys var))
        escape-fn (if unescaped? identity escape)]
    (if (fn? val)
      (parse/parse (reader/make-string-reader (str (val)))
                   #(render* ctx (comp out escape-fn) %))
      (out (escape-fn (str val))))))

(defn render
  ([ctx out x]
   (render ctx out x {}))
  ([ctx out x {:keys [partials]}]
   (binding [*partials-resolver* (or partials *partials-resolver*)]
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
              (render* (cons val ctx) out node))

            (and (coll? val) (sequential? val))
            (when (seq val)
              (doseq [e val, node (:nodes this)]
                (render* (cons e ctx) out node)))

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
              (render* (cons val ctx) out node)))))

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
      (when-let [r (pres/resolve *partials-resolver* name)]
        (try
          (parse/parse r #(render* ctx out %)
                       {:source name :indent (:indent this)})
          (finally
            #?(:clj
               (when (instance? java.io.Closeable r)
                 (.close ^java.io.Closeable r)))))))))
