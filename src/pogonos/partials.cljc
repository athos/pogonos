(ns pogonos.partials
  (:refer-clojure :exclude [resolve])
  (:require #?(:clj [clojure.java.io :as io])
            [pogonos.protocols :as proto]
            [pogonos.reader :as reader]))

(extend-protocol proto/IPartialsResolver
  nil
  (resolve [this name])
  (cacheable? [this name] false))

#?(:clj
   (defn- resolve-resource-from-base-path [base-path partial-name]
     (let [filename (cond->> (str (name partial-name) ".mustache")
                      base-path
                      (str base-path (System/getProperty "file.separator")))]
       (when-let [res (io/resource filename)]
         (reader/make-file-reader res)))))

#?(:clj
   (defrecord ResourcePartialsResolver [base-paths]
     proto/IPartialsResolver
     (resolve [this name]
       (if (seq base-paths)
         (some #(resolve-resource-from-base-path % name) base-paths)
         (resolve-resource-from-base-path nil name)))
     (cacheable? [this name] true)))

#?(:clj
   (defn resource-partials [& base-paths]
     (->ResourcePartialsResolver (some-> (not-empty base-paths) vec))))

#?(:clj
   (defn- resolve-file-from-base-path [base-path partial-name]
     (let [filename (str (name partial-name) ".mustache")
           file (if base-path
                  (io/file base-path filename)
                  (io/file filename))]
       (when (.exists file)
         (reader/make-file-reader file)))))

#?(:clj
   (defrecord FilePartialsResolver [base-paths]
     proto/IPartialsResolver
     (resolve [this name]
       (if (seq base-paths)
         (some #(resolve-file-from-base-path % name)
               base-paths)
         (resolve-file-from-base-path nil name)))
     (cacheable? [this name] true)))

#?(:clj
   (defn file-partials [& base-paths]
     (->FilePartialsResolver (some-> (not-empty base-paths) vec))))

(defrecord FnPartialsResolver [f]
  proto/IPartialsResolver
  (resolve [this name]
    (some-> (f name) reader/->reader))
  (cacheable? [this name] true))

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
  (resolve [this name]
    (some #(proto/resolve % name) resolvers))
  (cacheable? [this name]
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
      (resolve [this name]
        (proto/resolve resolver name))
      (cacheable? [this name] false))))

(defn resolve [resolver name]
  (proto/resolve resolver name))

(defn cacheable? [resolver name]
  (proto/cacheable? resolver name))
