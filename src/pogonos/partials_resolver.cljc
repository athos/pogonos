(ns pogonos.partials-resolver
  (:refer-clojure :exclude [resolve])
  (:require #?(:clj [clojure.java.io :as io])
            [pogonos.protocols :as proto]
            [pogonos.read :as read]))

#?(:clj
   (defn- resolve-from-base-path [base-path name]
     (let [file (io/file base-path (str name ".mustache"))]
       (when (.exists file)
         (read/make-file-reader file)))))

#?(:clj
   (defn- ensure-derefed [x]
     (if (instance? clojure.lang.IDeref x) (deref x) x)))

#?(:clj
   (defrecord FilePartialsResolver [base-paths]
     proto/IPartialsResolver
     (resolve [this name]
       (some #(resolve-from-base-path (ensure-derefed %) name) base-paths))))

#?(:clj
   (def ^:private default-partials-base-path (atom ".")))

#?(:clj
   (defn file-partials-resolver
     ([]
      (file-partials-resolver @default-partials-base-path))
     ([base-path & base-paths]
      (->FilePartialsResolver (cons base-path base-paths)))))

#?(:clj
   (defn set-default-partials-base-path! [base-path]
     (reset! default-partials-base-path base-path)
     nil))

#?(:clj
   (extend-type String
     proto/IPartialsResolver
     (resolve [this name]
       (resolve-from-base-path this name))))

(extend-type #?(:clj clojure.lang.APersistentMap
                ;; FIXME: CLJS does not have APersistentMap??
                :cljs PersistentArrayMap)
  proto/IPartialsResolver
  (resolve [this name]
    (some-> (get this (keyword name)) read/make-string-reader)))

(def ^{:arglists '([resolver name])} resolve proto/resolve)
