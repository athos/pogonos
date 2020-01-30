(ns pogonos.read
  (:require [clojure.string :as str]
            [pogonos.protocols :as proto]))

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
