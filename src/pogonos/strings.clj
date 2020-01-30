(ns pogonos.strings
  (:require [clojure.string :as str]))

(defn index-of [s s']
  (str/index-of s s'))

(defn char-at [^String s i]
  (when (< i (count s))
    (nth s i)))

(defn split [s delim]
  (when-let [i (index-of s delim)]
    [(subs s 0 i) (subs s (+ i (count delim)))]))

(defn trim [s]
  (str/trim s))
