(ns pogonos.parse
  (:require [clojure.string :as str]
            [pogonos.nodes :as nodes]
            [pogonos.protocols :as proto]
            [pogonos.strings :as pstr])
  (:import [pogonos.nodes SectionEnd]))

(def ^:const default-open-delim "{{")
(def ^:const default-close-delim "}}")
(def ^:dynamic *open-delim*)
(def ^:dynamic *close-delim*)

(defn- parse-keys [s]
  (->> (str/split s #"\.")
       (mapv keyword)))

(defn- parse-variable [pre post unescaped? in out]
  (out pre)
  (if-let [[name post'] (pstr/split post *close-delim*)]
    (do (out (nodes/->Variable (parse-keys (pstr/trim name)) unescaped?))
        (proto/unread in post'))
    (assert false "broken variable tag")))

(defn- parse-unescaped-variable [pre post in out]
  (out pre)
  (if-let [[name post'] (pstr/split (subs post 1) "}}}")]
    (do (out (nodes/->Variable (parse-keys (pstr/trim name)) true))
        (proto/unread in post'))
    (assert false "broken variable tag")))

(declare parse*)

(defn- parse-open-section [pre post inverted? in out]
  (let [[name post'] (pstr/split (subs post 1) *close-delim*)
        keys (parse-keys (pstr/trim name))
        children (volatile! [])
        out' (fn [x]
               (if (instance? SectionEnd x)
                 (if (= keys (:keys x))
                   (out ((if inverted? nodes/->Inverted nodes/->Section)
                         keys @children))
                   (assert false (str "Unexpected tag " (:keys x) " occurred")))
                 (vswap! children conj x)))]
    (when-not (and (str/blank? pre) (str/blank? post'))
      (out pre)
      (proto/unread in post'))
    (parse* in out')))

(defn- parse-close-section [pre post in out]
  (let [[name post'] (pstr/split (subs post 1) *close-delim*)]
    (when-not (and (str/blank? pre) (str/blank? post'))
      (out pre)
      (proto/unread in post'))
    (out (nodes/->SectionEnd (parse-keys (pstr/trim name))))))

(defn- parse-partial [pre post in out]
  (let [[name post'] (pstr/split (subs post 1) *close-delim*)]
    (out (nodes/->Partial (pstr/trim name) pre))
    (proto/unread in post')))

(defn- process-comment [pre post in out]
  (if-let [[comment post'] (pstr/split (subs post 1) *close-delim*)]
    (when-not (and (str/blank? pre) (str/blank? post'))
      (out pre)
      (proto/unread in post'))
    (do (out pre)
        (loop []
          (let [line (read in)]
            (if-let [[comment post'] (pstr/split line *close-delim*)]
              (proto/unread in post')
              (recur)))))))

(defn- process-set-delimiter [pre post in out]
  (let [[delims post'] (pstr/split (subs post 1) *close-delim*)
        [open close] (-> delims
                         (subs 0 (dec (count delims)))
                         (pstr/trim)
                         (pstr/split " "))]
    (set! *open-delim* (pstr/trim open))
    (set! *close-delim* (pstr/trim close))
    (when-not (and (str/blank? pre) (str/blank? post'))
      (out pre)
      (proto/unread in post'))))

(defn- parse-tag [pre post in out]
  (if-let [c (pstr/char-at post 0)]
    (if (= c \/)
      (do (parse-close-section pre post in out)
          true)
      (do (case c
            \# (parse-open-section pre post false in out)
            \^ (parse-open-section pre post true in out)
            \& (parse-variable pre post true in out)
            \> (parse-partial pre post in out)
            \! (process-comment pre post in out)
            \{ (if (= *open-delim* default-open-delim)
                 (parse-unescaped-variable pre post in out)
                 (assert false (str "Unexpected { after changed open delim: " *open-delim*)))
            \= (process-set-delimiter pre post in out)
            (parse-variable pre post false in out))
          false))
    (assert false "Unexpected end of line")))

(defn parse* [in out]
  (loop []
    (when-let [line (proto/read in)]
      (if-let [[pre post] (pstr/split line *open-delim*)]
        (or (parse-tag pre post in out)
            (recur))
        (do (out line)
            (recur))))))

(defn parse [in out]
  (binding [*open-delim* default-open-delim
            *close-delim* default-close-delim]
    (parse* in out)))
