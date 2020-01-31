(ns pogonos.read
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [pogonos.protocols :as proto])
  (:import [java.io BufferedReader Closeable]))

(deftype StringReader
    [^String src
     ^:unsynchronized-mutable offset
     ^:unsynchronized-mutable ^String pushback]
  proto/IReader
  (read [this]
    (if-let [ret pushback]
      (do (set! pushback nil)
          ret)
      (when (< offset (count src))
        (let [i (str/index-of src "\n" offset)
              offset' (or (some-> i inc) (count src))
              ret (subs src offset offset')]
          (set! offset offset')
          ret))))
  (unread [this s]
    (set! pushback s))
  (end? [this]
    (and (nil? pushback) (>= offset (count src)))))

(defn make-string-reader [s]
  (StringReader. s 0 nil))

(deftype FileReader
    [^BufferedReader reader
     ^:unsynchronized-mutable ^String pushback]
  proto/IReader
  (read [this]
    (if-let [ret pushback]
      (do (set! pushback nil)
          ret)
      (some-> (.readLine reader) (str \newline))))
  (unread [this s]
    (set! pushback s))
  (end? [this]
    (and (nil? pushback) (not (.ready reader))))
  Closeable
  (close [this]
    (.close reader)))

(defn make-file-reader [file]
  (FileReader. (io/reader file) nil))
