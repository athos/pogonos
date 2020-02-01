(ns pogonos.render
  (:require [clojure.string :as str]
            [pogonos.partials-resolver :as pres]
            [pogonos.nodes]
            [pogonos.parse :as parse]
            [pogonos.protocols :as proto]
            [pogonos.read :as read])
  (:import [pogonos.nodes Inverted Partial Root Section Variable]))

(def ^:dynamic *partials-resolver*
  (pres/file-partials-resolver))

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

(defn render [ctx out x {:keys [partials]}]
  (binding [*partials-resolver* (or partials *partials-resolver*)]
    (proto/render x ctx out)))

(extend-protocol proto/IRenderable
  Object
  (render [this ctx out])

  String
  (render [this ctx out]
    (out this))

  Root
  (render [this ctx out]
    (doseq [node (:body this)]
      (proto/render node ctx out)))

  Variable
  (render [this ctx out]
    (let [val (lookup ctx (:keys this))
          escape-fn (if (:unescaped? this) identity escape)]
      (if (fn? val)
        (parse/parse* (read/make-string-reader (str (val)))
                      #(proto/render % ctx (comp out escape-fn)))
        (out (escape-fn (str val))))))

  Section
  (render [this ctx out]
    (let [val (lookup ctx (:keys this))]
      (cond (not val) nil

            (map? val)
            (doseq [node (:nodes this)]
              (proto/render node (cons val ctx) out))

            (and (coll? val) (sequential? val))
            (when (seq val)
              (doseq [e val, node (:nodes this)]
                (proto/render node (cons e ctx) out)))

            :else
            (doseq [node (:nodes this)]
              (proto/render node ctx out)))))

  Inverted
  (render [this ctx out]
    (let [val (lookup ctx (:keys this))]
      (when (or (not val)
                (and (coll? val) (sequential? val) (empty? val)))
        (doseq [node (:nodes this)]
          (proto/render node ctx out)))))

  Partial
  (render [this ctx out]
    (if-let [r (pres/resolve *partials-resolver* (:name this))]
      (parse/parse r
                   (fn [node]
                     (proto/render node ctx out)
                     ;; FIXME: Should interrupt during reading or parsing time
                     (when (and (string? node) (str/ends-with? node "\n"))
                       (proto/render (:indent this) ctx out))))
      (assert false (str "Partial named \"" (:name this) "\" not found")))))
