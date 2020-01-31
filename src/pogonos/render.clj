(ns pogonos.render
  (:require [clojure.string :as str]
            [pogonos.nodes]
            [pogonos.parse :as parse]
            [pogonos.protocols :as proto]
            [pogonos.read :as read])
  (:import [pogonos.nodes Inverted Section Variable]))

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

(defn render [ctx out x]
  (proto/render x ctx out))

(extend-protocol proto/IRenderable
  Object
  (render [this ctx out])

  String
  (render [this ctx out]
    (out this))

  Variable
  (render [this ctx out]
    (let [val (lookup ctx (:keys this))
          escape-fn (if (:unescaped? this) identity escape)]
      (if (fn? val)
        (parse/parse* (read/make-string-reader (str (val)))
                      #(render ctx (comp out escape-fn) %))
        (out (escape-fn (str val))))))

  Section
  (render [this ctx out]
    (let [val (lookup ctx (:keys this))]
      (cond (not val) nil

            (map? val)
            (doseq [node (:nodes this)]
              (render (cons val ctx) out node))

            (and (coll? val) (sequential? val))
            (when (seq val)
              (doseq [e val, node (:nodes this)]
                (render (cons e ctx) out node)))

            :else
            (doseq [node (:nodes this)]
              (render ctx out node)))))

  Inverted
  (render [this ctx out]
    (let [val (lookup ctx (:keys this))]
      (when (or (not val)
                (and (coll? val) (sequential? val) (empty? val)))
        (doseq [node (:nodes this)]
          (render ctx out node))))))
