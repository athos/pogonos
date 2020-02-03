(ns pogonos.strings
  (:require [clojure.string :as str]))

(defn index-of [s s' index]
  (str/index-of s s' index))

(defn char-at [^String s i]
  (when (< i (count s))
    (nth s i)))

(defn split
  ([s delim]
   (split s delim 0))
  ([s delim index]
   (when-let [i (index-of s delim index)]
     (let [i' (+ i (count delim))]
       [i' (subs s index i) (subs s i')]))))

(defn trim [s]
  (str/trim s))
