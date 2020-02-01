(ns pogonos.output
  (:require #?(:clj [clojure.java.io :as io])
            [pogonos.protocols :as proto]))

(def ^{:arglists '([output s])} append proto/append)
(def ^{:arglists '([output])} complete proto/complete)

(defrecord StandardOutput []
  proto/IOutput
  (append [this s]
    (print s))
  (complete [this]
    (flush)))

(defn standard-output []
  (->StandardOutput))

#?(:clj
   (defrecord StringOutput [^StringBuilder sb]
     proto/IOutput
     (append [this s]
       (.append sb s))
     (complete [this]
       (.toString sb)))
   :cljs
   (defrecord StringOutput [strs]
     proto/IOutput
     (append [this s]
       (vswap! strs conj s))
     (complete [this]
       (apply str @strs))))

(defn string-output []
  #?(:clj (->StringOutput (StringBuilder.))
     :cljs (->StringOutput (volatile! []))))

#?(:clj
   (defrecord FileOutput [^java.io.Writer writer]
     proto/IOutput
     (append [this s]
       (.write writer ^String s))
     (complete [this]
       (.close writer))))

#?(:clj
   (defn file-output [file]
     (->FileOutput (io/writer file))))

#?(:clj
   (defrecord WriterOutput [^java.io.Writer writer]
     proto/IOutput
     (append [this s]
       (.write writer ^String s))
     (complete [this]
       (.flush writer))))

#?(:clj
   (defn writer-output [w]
     (->WriterOutput (io/writer w))))
