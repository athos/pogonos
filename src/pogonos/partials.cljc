(ns pogonos.partials
  (:refer-clojure :exclude [resolve])
  (:require #?(:clj [clojure.java.io :as io])
            [pogonos.protocols :as proto]
            [pogonos.reader :as reader]))

(extend-protocol proto/IPartialsResolver
  nil
  (resolve [_ _name])
  (cacheable? [_ _name] false))

#?(:clj
   (defn- resolve-resource-from-base-path [base-path filename]
     (let [file-path (cond->> (name filename)
                       base-path
                       (str base-path (System/getProperty "file.separator")))]
       (when-let [res (io/resource file-path)]
         (reader/make-file-reader res)))))

#?(:clj
   (defrecord ResourcePartialsResolver [base-paths suffix]
     proto/IPartialsResolver
     (resolve [_ partial-name]
       (let [filename (cond-> (name partial-name)
                        suffix (str suffix))]
         (if (seq base-paths)
           (some #(resolve-resource-from-base-path % filename) base-paths)
           (resolve-resource-from-base-path nil filename))))
     (cacheable? [_ _partial-name] true)))

#?(:clj
   (defn resource-partials
     ([] (resource-partials []))
     ([base-path-or-base-paths]
      (resource-partials base-path-or-base-paths ".mustache"))
     ([base-path-or-base-paths suffix]
      (let [base-paths (if (coll? base-path-or-base-paths)
                         base-path-or-base-paths
                         [base-path-or-base-paths])]
        (->ResourcePartialsResolver base-paths suffix)))))

#?(:clj
   (defn- resolve-file-from-base-path [base-path filename]
     (let [file (if base-path
                  (io/file base-path filename)
                  (io/file filename))]
       (when (.exists file)
         (reader/make-file-reader file)))))

#?(:clj
   (defrecord FilePartialsResolver [base-paths suffix]
     proto/IPartialsResolver
     (resolve [_ partial-name]
       (let [filename (cond-> (name partial-name)
                        suffix (str suffix))]
         (if (seq base-paths)
           (some #(resolve-file-from-base-path % filename)
                 base-paths)
           (resolve-file-from-base-path nil filename))))
     (cacheable? [_ _partial-name] true)))

#?(:clj
   (defn file-partials
     ([] (file-partials []))
     ([base-path-or-base-paths]
      (file-partials base-path-or-base-paths ".mustache"))
     ([base-path-or-base-paths suffix]
      (let [base-paths (if (coll? base-path-or-base-paths)
                         base-path-or-base-paths
                         [base-path-or-base-paths])]
        (->FilePartialsResolver base-paths suffix)))))

(defrecord FnPartialsResolver [f]
  proto/IPartialsResolver
  (resolve [_ name]
    (some-> (f name) reader/->reader))
  (cacheable? [_ _name] true))

(defn fn-partials [f]
  (->FnPartialsResolver f))

(extend-protocol proto/ToPartialsResolver
  #?(:clj clojure.lang.APersistentMap
     ;; FIXME: CLJS does not have APersistentMap??
     :cljs PersistentArrayMap)
  (->resolver [this]
    (fn-partials this))

  #?(:clj clojure.lang.AFn :cljs function)
  (->resolver [this]
    (fn-partials this)))

(defrecord CompositePartialsResolver [resolvers]
  proto/IPartialsResolver
  (resolve [_ name]
    (some #(proto/resolve % name) resolvers))
  (cacheable? [_ name]
    (when-first [resolver (filter #(proto/resolve % name) resolvers)]
      (proto/cacheable? resolver name))))

(defn ->resolver [x]
  (if (satisfies? proto/IPartialsResolver x)
    x
    (proto/->resolver x)))

(defn compose [& resolvers]
  (->CompositePartialsResolver (mapv ->resolver resolvers)))

(defn with-caching-disabled [resolver]
  (let [resolver (->resolver resolver)]
    (reify proto/IPartialsResolver
      (resolve [_ name]
        (proto/resolve resolver name))
      (cacheable? [_ _name] false))))

(defn resolve [resolver name]
  (proto/resolve resolver name))

(defn cacheable? [resolver name]
  (proto/cacheable? resolver name))
