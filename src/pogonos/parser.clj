(ns pogonos.parser
  (:require [clojure.string :as str]
            [pogonos.protocols :as proto]
            [pogonos.reader :as reader]
            [pogonos.strings :as pstr]))

(def ^:const default-open-delim "{{")
(def ^:const default-close-delim "}}")
(def ^:dynamic *open-delim*)
(def ^:dynamic *close-delim*)

(defn- process-keys [s]
  (->> (str/split s #"\.")
       (mapv keyword)))

(defn- process-variable [pre post unescaped? in out]
  (out pre)
  (if-let [[name post'] (pstr/split post *close-delim*)]
    (do (out {:type :variable :keys (process-keys (pstr/trim name))
              :unescaped? unescaped?})
        (proto/unread in post'))
    (assert false "broken variable tag")))

(defn- process-unescaped-variable [pre post in out]
  (out pre)
  (if-let [[name post'] (pstr/split post "}}}")]
    (do (out {:type :variable :keys (process-keys (pstr/trim name))
              :unescaped? true})
        (proto/unread in post'))
    (assert false "broken variable tag")))

(declare process*)

(defn- process-open-section [pre post inverted? in out]
  (let [[name post'] (pstr/split (subs post 1) *close-delim*)
        keys (process-keys (pstr/trim name))
        children (volatile! [])
        out' (fn [x]
               (if (= (:type x) :section-end)
                 (if (= keys (:keys x))
                   (out {:type (if inverted? :inverted :section)
                         :keys keys :children @children})
                   (assert false (str "Unexpected tag " (:keys x) " occurred")))
                 (vswap! children conj x)))]
    (when-not (and (str/blank? pre) (str/blank? post'))
      (out pre)
      (proto/unread in post'))
    (process* in out')))

(defn- process-close-section [pre post in out]
  (let [[name post'] (pstr/split (subs post 1) *close-delim*)]
    (when-not (and (str/blank? pre) (str/blank? post'))
      (out pre)
      (proto/unread in post'))
    (out {:type :section-end :keys (process-keys (pstr/trim name))})))

(defn- process-partial [pre post in out]
  (let [[name post'] (pstr/split (subs post 1) *close-delim*)]
    (out {:type :partial :name (pstr/trim name) :indent pre})
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

(defn- process-tag [pre post in out]
  (if-let [c (pstr/char-at post 0)]
    (if (= c \/)
      (do (process-close-section pre post in out)
          true)
      (do (case c
            \# (process-open-section pre post false in out)
            \^ (process-open-section pre post true in out)
            \& (process-variable pre post true in out)
            \> (process-partial pre post in out)
            \! (process-comment pre post in out)
            \{ (if (= *open-delim* default-open-delim)
                 (process-unescaped-variable pre post in out)
                 (assert false (str "Unexpected { after changed open delim: " *open-delim*)))
            \= (process-set-delimiter pre post in out)
            (process-variable pre post false in out))
          false))
    (assert false "Unexpected end of line")))

(defn process* [in out]
  (loop []
    (when-let [line (proto/read in)]
      (if-let [[pre post] (pstr/split line *open-delim*)]
        (or (process-tag pre post in out)
            (recur))
        (do (out line)
            (recur))))))

(defn process [in out]
  (binding [*open-delim* default-open-delim
            *close-delim* default-close-delim]
    (process* in out)))
