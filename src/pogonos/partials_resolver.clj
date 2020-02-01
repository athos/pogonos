(ns pogonos.partials-resolver
  (:refer-clojure :exclude [resolve])
  (:require [clojure.java.io :as io]
            [pogonos.protocols :as proto]
            [pogonos.read :as read]))

(defn- resolve-from-base-path [base-path name]
  (let [file (io/file base-path (str name ".mustache"))]
    (when (.exists file)
      (read/make-file-reader file))))

(defn- ensure-derefed [x]
  (if (instance? clojure.lang.IDeref x) (deref x) x))

(defrecord FilePartialsResolver [base-paths]
  proto/IPartialsResolver
  (resolve [this name]
    (some #(resolve-from-base-path (ensure-derefed %) name) base-paths)))

(def ^:private default-partials-base-path (atom "."))

(defn file-partials-resolver
  ([]
   (file-partials-resolver @default-partials-base-path))
  ([base-path & base-paths]
   (->FilePartialsResolver (cons base-path base-paths))))

(defn set-default-partials-base-path! [base-path]
  (reset! default-partials-base-path base-path)
  nil)

(extend-protocol proto/IPartialsResolver
  String
  (resolve [this name]
    (resolve-from-base-path this name))

  clojure.lang.APersistentMap
  (resolve [this name]
    (some-> (get this (keyword name)) read/make-string-reader)))

(def ^{:arglists '([resolver name])} resolve proto/resolve)
