(ns pogonos.output
  (:require [clojure.java.io :as io]
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

(defrecord StringOutput [^StringBuilder sb]
  proto/IOutput
  (append [this s]
    (.append sb s))
  (complete [this]
    (.toString sb)))

(defn string-output []
  (->StringOutput (StringBuilder.)))

(defrecord FileOutput [^java.io.Writer writer]
  proto/IOutput
  (append [this s]
    (.write writer ^String s))
  (complete [this]
    (.close writer)))

(defn file-output [file]
  (->FileOutput (io/writer file)))

(defrecord WriterOutput [^java.io.Writer writer]
  proto/IOutput
  (append [this s]
    (.write writer ^String s))
  (complete [this]
    (.flush writer)))

(defn writer-output [w]
  (->WriterOutput (io/writer w)))
