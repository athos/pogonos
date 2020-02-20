(ns pogonos.partials
  (:refer-clojure :exclude [resolve])
  (:require #?(:clj [clojure.java.io :as io])
            [pogonos.protocols :as proto]
            [pogonos.reader :as reader]))

#?(:clj
   (defn- resolve-resource-from-base-path [base-path partial-name]
     (when-let [res (->> (name partial-name)
                         (format "%s%s%s.mustache" base-path
                                 (System/getProperty "file.separator"))
                         io/resource)]
       (reader/make-file-reader res))))

#?(:clj
   (defrecord ResourcePartialsResolver [base-paths]
     proto/IPartialsResolver
     (resolve [this name]
       (some #(resolve-resource-from-base-path % name) base-paths))
     (cacheable? [this name] true)))

#?(:clj
   (defn resource-partials
     ([] (resource-partials "."))
     ([base-path & base-paths]
      (->ResourcePartialsResolver (cons base-path base-paths)))))

#?(:clj
   (defn- resolve-file-from-base-path [base-path partial-name]
     (let [file (io/file base-path (str (name partial-name) ".mustache"))]
       (when (.exists file)
         (reader/make-file-reader file)))))

#?(:clj
   (defrecord FilePartialsResolver [base-paths]
     proto/IPartialsResolver
     (resolve [this name]
       (some #(resolve-file-from-base-path % name)
             base-paths))
     (cacheable? [this name] true)))

#?(:clj
   (defn file-partials
     ([] (file-partials "."))
     ([base-path & base-paths]
      (->FilePartialsResolver (cons base-path base-paths)))))

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
