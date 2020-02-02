(ns pogonos.stringify
  (:require [pogonos.protocols :as proto]
            [pogonos.nodes :as nodes]
            [pogonos.output :as output])
  #?(:clj
     (:import [pogonos.nodes
               Comment Inverted Partial Section SectionEnd SetDelimiter Variable])))

(def ^:dynamic *open-delim*)
(def ^:dynamic *close-delim*)

(defn- stringify-keys [keys out]
  (if (empty? keys)
    "."
    (do (out (name (first keys)))
        (doseq [k (rest keys)]
          (out ".")
          (out (name k))))))

(defn- stringify-section [sigil section out]
  (when-let [pre (:pre (meta section))]
    (out pre))
  (out *open-delim*)
  (out sigil)
  (stringify-keys (:keys section) out)
  (out *close-delim*)
  (when-let [post (:post (meta section))]
    (out post))
  (binding [*open-delim* (or (:open (meta section)) *open-delim*)
            *close-delim* (or (:close (meta section)) *close-delim*)]
    (doseq [node (:nodes section)]
      (proto/stringify node out))))

(extend-protocol proto/IStringifiable
  #?(:clj String :cljs string)
  (stringify [this out]
    (out this))

  #?(:clj clojure.lang.PersistentVector
     :cljs PersistentVector)
  (stringify [this out]
    (doseq [node this]
      (proto/stringify node out)))

  #?(:clj Variable :cljs nodes/Variable)
  (stringify [this out]
    (out *open-delim*)
    (when (:unescaped? this)
      (out "&"))
    (stringify-keys (:keys this) out)
    (out *close-delim*))

  #?(:clj Section :cljs nodes/Section)
  (stringify [this out]
    (stringify-section "#" this out))

  #?(:clj SectionEnd :cljs nodes/SectionEnd)
  (stringify [this out]
    (stringify-section "/" this out))

  #?(:clj Inverted :cljs nodes/Inverted)
  (stringify [this out]
    (stringify-section "^" this out))

  #?(:clj Partial :cljs nodes/Partial)
  (stringify [this out]
    (out *open-delim*)
    (out (:name this))
    (out *open-delim*))

  #?(:clj Comment :cljs nodes/Comment)
  (stringify [this out]
    (when-let [pre (:pre (meta this))]
      (out pre))
    (out *open-delim*)
    (out "!")
    (doseq [line (:body this)]
      (out line))
    (out *close-delim*)
    (when-let [post (:post (meta this))]
      (out post)))

  #?(:clj SetDelimiter :cljs nodes/SetDelimiter)
  (stringify [this out]
    (when-let [pre (:pre (meta this))]
      (out pre))
    (out *open-delim*)
    (out "=")
    (out (:open this))
    (out " ")
    (out (:close this))
    (out "=")
    (out *close-delim*)
    (when-let [post (:post (meta this))]
      (out post))
    (set! *open-delim* (:open this))
    (set! *close-delim* (:close this))))

(defn stringify [node]
  (let [output (output/string-output)]
    (binding [*open-delim* "{{"
              *close-delim* "}}"]
      (proto/stringify node #(output/append output %))
      (output/complete output))))
