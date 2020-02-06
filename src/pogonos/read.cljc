(ns pogonos.read
  (:refer-clojure :exclude [read-line])
  (:require [clojure.string :as str]
            #?(:clj [clojure.java.io :as io])
            [pogonos.protocols :as proto]
            [pogonos.strings :as pstr])
  #?(:clj (:import [java.io Reader Closeable])))

(deftype StringReader [src ^:unsynchronized-mutable offset]
  proto/IReader
  (read-line [this]
    (when (< offset (count src))
      (let [i (str/index-of src "\n" offset)
            offset' (or (some-> i inc) (count src))
            ret (subs src offset offset')]
        (set! offset offset')
        ret))))

(defn make-string-reader [s]
  (StringReader. s 0))

#?(:clj
   (deftype FileReader
       [^Reader reader
        ^chars buf
        ^:unsynchronized-mutable ^int offset
        ^:unsynchronized-mutable ^int size]
     proto/IReader
     (read-line [this]
       (loop [^StringBuilder sb nil]
         (when (>= offset size)
           (set! size (.read reader buf))
           (set! offset (int 0)))
         (if (neg? size)
           (when sb (.toString sb))
           (let [sb (or sb (StringBuilder.))
                 i (int
                    (loop [i offset]
                      (if (< i size)
                        (if (= (aget buf i) \newline)
                          (inc i)
                          (recur (inc i)))
                        -1)))]
             (if (>= i 0)
               (do (.append sb buf offset (- i offset))
                   (set! offset i)
                   (.toString sb))
               (do (.append sb buf offset (- size offset))
                   (set! offset size)
                   (recur sb)))))))
     Closeable
     (close [this]
       (.close reader))))

#?(:clj
   (defn make-file-reader [file]
     (FileReader. (io/reader file) (char-array 256) 0 0)))

(defprotocol ILineBufferingReader
  (set-line! [this l])
  (set-line-num! [this n])
  (set-col-num! [this n])
  (line [this])
  (line-num [this])
  (col-num [this])
  (base-reader [this]))

(declare read-line)

(deftype LineBufferingReader
    [in
     ^:unsynchronized-mutable line
     ^:unsynchronized-mutable lnum
     ^:unsynchronized-mutable cnum]
  ILineBufferingReader
  (set-line! [this l]
    (set! line l))
  (set-line-num! [this n]
    (set! lnum n))
  (set-col-num! [this n]
    (set! cnum n))
  (line [this] line)
  (line-num [this] lnum)
  (col-num [this] cnum)
  (base-reader [this] in)
  proto/IReader
  (read-line [this]
    (read-line this)))

(defn make-line-buffering-reader [in]
  (->LineBufferingReader in nil 0 0))

(defn- read-line* [^LineBufferingReader reader]
  (let [prev (line reader)
        line (proto/read-line (base-reader reader))]
    (set-line! reader line)
    (set-col-num! reader 0)
    (when (and prev line)
      (set-line-num! reader (inc (line-num reader))))))

(defn- with-current-line [reader f]
  (when (>= (col-num reader) (count (line reader)))
    (read-line* reader))
  (when-let [l (line reader)]
    (f l)))

(defn read-line [reader]
  (with-current-line reader
    (fn [line]
      (let [ret (cond-> line
                  (not= (col-num reader) 0)
                  (subs (col-num reader)))]
        (set-col-num! reader (count line))
        ret))))

(defn read-until [reader s]
  (with-current-line reader
    (fn [line]
      (when-let [[i pre] (pstr/split line s (col-num reader))]
        (set-col-num! reader i)
        pre))))

(defn read-char [reader]
  (with-current-line reader
    (fn [line]
      (when-let [c (pstr/char-at line (col-num reader))]
        (set-col-num! reader (inc (col-num reader)))
        c))))

(defn unread-char [reader]
  (set-col-num! reader (dec (col-num reader)))
  nil)

(defn end? [reader]
  (nil? (with-current-line reader identity)))

(defn blank-trailing? [reader]
  ;; the implementation is a little bit tricky since with-current-line
  ;; returns nil when next line is nil
  (-> (with-current-line reader
        (fn [line]
          (->> (subs line (col-num reader))
               (reduce (fn [_ c]
                         (when-not (#{\space \tab \return \newline} c)
                           (reduced true)))
                       false))))
      not))
