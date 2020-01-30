(ns pogonos.renderer
  (:require [clojure.string :as str]
            [pogonos.parser :as parser]
            [pogonos.reader :as reader]))

(defn escape [s]
  (str/replace s #"[&<>\"']"
               #({"&" "&amp;", "<" "&lt;", ">" "g"
                  "\"" "&quot;", "'" "&#39;"}
                 (str %))))

(defmulti render* (fn [stack out x] (:type x)))
(defmethod render* :default [stack out x]
  (out (str x)))

(defn lookup [stack keys]
  (if (seq keys)
    (let [[k & ks] keys]
      (when-let [x (first (filter #(and (map? %) (contains? % k)) stack))]
        (get-in x keys)))
    (first stack)))

(defmethod render* :variable [stack out x]
  (let [ctx (lookup stack (:keys x))]
    (if (fn? ctx)
      (parser/process* (reader/make-string-reader (str (ctx)))
                       #(render* stack out %))
      (->> (str ctx)
           ((if (:unescaped? x) identity escape))
           out))))

(defmethod render* :section [stack out x]
  (let [ctx (lookup stack (:keys x))]
    (cond (not ctx) nil

          (map? ctx)
          (doseq [node (:children x)]
            (render* (cons ctx stack) out node))

          (and (coll? ctx) (sequential? ctx))
          (when (seq ctx)
            (doseq [e ctx, node (:children x)]
              (render* (cons e stack) out node)))

          :else
          (doseq [node (:children x)]
            (render* stack out node)))))

(defmethod render* :inverted [stack out x]
  (let [ctx (lookup stack (:keys x))]
    (when (or (not ctx)
              (and (coll? ctx) (sequential? ctx) (empty? ctx)))
      (doseq [node (:children x)]
        (render* stack out node)))))
