(ns pogonos.stringify
  (:require [pogonos.protocols :as proto]
            [pogonos.nodes :as nodes]
            [pogonos.output :as output])
  #?(:clj
     (:import [pogonos.nodes
               Comment DynamicPartial Inverted Partial Section SectionEnd
               SetDelimiter UnescapedVariable Variable])))

(def ^:dynamic *open-delim*)
(def ^:dynamic *close-delim*)

(defn- stringify-key [key out]
  (when-let [ns (namespace key)]
    (out ns)
    (out "/"))
  (out (name key)))

(defn stringify-keys [keys out]
  (if (empty? keys)
    (out ".")
    (do (stringify-key (first keys) out)
        (doseq [k (rest keys)]
          (out ".")
          (stringify-key k out)))))

(defn stringify* [x out]
  (if (string? x)
    (out x)
    (proto/stringify x out)))

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
      (stringify* node out))))

(extend-protocol proto/IStringifiable
  #?(:clj clojure.lang.PersistentVector
     :cljs PersistentVector)
  (stringify [this out]
    (doseq [node this]
      (stringify* node out)))

  #?(:clj Variable :cljs nodes/Variable)
  (stringify [this out]
    (out *open-delim*)
    (when (:unescaped? this)
      (out "&"))
    (stringify-keys (:keys this) out)
    (out *close-delim*))

  #?(:clj UnescapedVariable :cljs nodes/UnescapedVariable)
  (stringify [this out]
    (out "{{{")
    (stringify-keys (:keys this) out)
    (out "}}}"))

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
    (out ">")
    (out (name (:name this)))
    (out *close-delim*)
    (when-let [post (:post (meta this))]
      (out post)))

  #?(:clj DynamicPartial :cljs nodes/DynamicPartial)
  (stringify [this out]
    (out *open-delim*)
    (out ">*")
    (stringify-keys (:keys this) out)
    (out *close-delim*)
    (when-let [post (:post (meta this))]
      (out post)))

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

(defn stringify [node open-delim close-delim]
  (let [out (output/string-output)]
    (binding [*open-delim* (or open-delim "{{")
              *close-delim* (or close-delim "}}")]
      (stringify* node out)
      (out))))
