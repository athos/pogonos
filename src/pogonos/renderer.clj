(ns pogonos.renderer
  (:require [clojure.string :as str]
            [pogonos.nodes]
            [pogonos.parser :as parser]
            [pogonos.protocols :as proto]
            [pogonos.reader :as reader])
  (:import [pogonos.nodes Inverted Section Variable]))

(defn escape [s]
  (str/replace s #"[&<>\"']"
               #({"&" "&amp;", "<" "&lt;", ">" "&gt;"
                  "\"" "&quot;", "'" "&#39;"}
                 (str %))))

(defn lookup [ctx keys]
  (if (seq keys)
    (let [[k & ks] keys]
      (when-let [x (first (filter #(and (map? %) (contains? % k)) ctx))]
        (get-in x keys)))
    (first ctx)))

(extend-protocol proto/IRenderable
  Object
  (render [this ctx out]
    (out (str this)))

  Variable
  (render [this ctx out]
    (let [val (lookup ctx (:keys this))
          escape-fn (if (:unescaped? this) identity escape)]
      (if (fn? val)
        (parser/parse* (reader/make-string-reader (str (val)))
                       #(proto/render % ctx (comp out escape-fn)))
        (out (escape-fn (str val))))))

  Section
  (render [this ctx out]
    (let [val (lookup ctx (:keys this))]
      (cond (not val) nil

            (map? val)
            (doseq [node (:children this)]
              (proto/render node (cons val ctx) out))

            (and (coll? val) (sequential? val))
            (when (seq val)
              (doseq [e val, node (:children this)]
                (proto/render node (cons e ctx) out)))

            :else
            (doseq [node (:children this)]
              (proto/render node ctx out)))))

  Inverted
  (render [this ctx out]
    (let [val (lookup ctx (:keys this))]
      (when (or (not val)
                (and (coll? val) (sequential? val) (empty? val)))
        (doseq [node (:children this)]
          (proto/render node ctx out))))))
