(ns pogonos.partials-resolver
  (:refer-clojure :exclude [resolve])
  (:require #?(:clj [clojure.java.io :as io])
            [pogonos.protocols :as proto]
            [pogonos.reader :as reader]))

#?(:clj
   (defn- resolve-resource-from-base-path [base-path name]
     (when-let [res (->> name
                         (format "%s%s%s.mustache" base-path
                                 (System/getProperty "file.separator"))
                         io/resource)]
       (reader/make-file-reader res))))

#?(:clj
   (defrecord ResourcePartialsResolver [base-paths]
     proto/IPartialsResolver
     (resolve [this name]
       (some #(resolve-resource-from-base-path % name) base-paths))))

#?(:clj
   (defn resource-partials-resolver
     ([] (resource-partials-resolver "."))
     ([base-path & base-paths]
      (->ResourcePartialsResolver (cons base-path base-paths)))))

#?(:clj
   (defn- resolve-file-from-base-path [base-path name]
     (let [file (io/file base-path (str name ".mustache"))]
       (when (.exists file)
         (reader/make-file-reader file)))))

#?(:clj
   (defrecord FilePartialsResolver [base-paths]
     proto/IPartialsResolver
     (resolve [this name]
       (some #(resolve-file-from-base-path % name)
             base-paths))))

#?(:clj
   (defn file-partials-resolver
     ([] (file-partials-resolver "."))
     ([base-path & base-paths]
      (->FilePartialsResolver (cons base-path base-paths)))))

(extend-protocol proto/IPartialsResolver
  #?(:clj clojure.lang.APersistentMap
     ;; FIXME: CLJS does not have APersistentMap??
     :cljs PersistentArrayMap)
  (resolve [this name]
    (some-> (get this (keyword name)) reader/->reader))

  #?(:clj clojure.lang.Fn :cljs function)
  (resolve [this name]
    (some-> (this (keyword name)) reader/->reader)))

(defrecord CompositePartialsResolver [resolvers]
  proto/IPartialsResolver
  (resolve [this name]
    (some #(proto/resolve % name) resolvers)))

(defn compose [& resolvers]
  (->CompositePartialsResolver resolvers))

(def ^{:arglists '([resolver name])} resolve proto/resolve)
