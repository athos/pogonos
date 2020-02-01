(ns pogonos.parse
  (:require [clojure.string :as str]
            [pogonos.nodes :as nodes]
            [pogonos.read :as read]
            [pogonos.strings :as pstr])
  #?(:clj (:import [pogonos.nodes SectionEnd])))

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
        (read/unread in post'))
    (assert false "broken variable tag")))

(defn- parse-unescaped-variable [pre post in out]
  (out pre)
  (if-let [[name post'] (pstr/split (subs post 1) "}}}")]
    (do (out (nodes/->Variable (parse-keys (pstr/trim name)) true))
        (read/unread in post'))
    (assert false "broken variable tag")))

(declare parse*)

(defn- parse-open-section [pre post inverted? in out]
  (let [[name post'] (pstr/split (subs post 1) *close-delim*)
        keys (parse-keys (pstr/trim name))
        children (volatile! [])
        standalone? (and (str/blank? pre) (str/blank? post'))
        out' (fn [x]
               (if (instance? #?(:clj SectionEnd :cljs nodes/SectionEnd) x)
                 (if (= keys (:keys x))
                   (-> ((if inverted? nodes/->Inverted nodes/->Section)
                        keys @children)
                       (cond->
                         (and standalone? (or (seq pre) (seq post')))
                         (with-meta {:pre pre :post post'}))
                       out)
                   (assert false (str "Unexpected tag " (:keys x) " occurred")))
                 (vswap! children conj x)))]
    (when-not standalone?
      (out pre)
      (read/unread in post'))
    (parse* in out')))

(defn- parse-close-section [pre post in out]
  (let [[name post'] (pstr/split (subs post 1) *close-delim*)
        standalone? (and (str/blank? pre) (str/blank? post'))]
    (when-not standalone?
      (out pre)
      (read/unread in post'))
    (-> (nodes/->SectionEnd (parse-keys (pstr/trim name)))
        (cond->
          (and standalone? (or (seq pre) (seq post')))
          (with-meta {:pre pre :post post'}))
        out)))

(defn- parse-partial [pre post in out]
  (let [[name post'] (pstr/split (subs post 1) *close-delim*)]
    (out pre)
    (out (nodes/->Partial (pstr/trim name) (str/replace pre #"\S" " ")))
    (read/unread in post')))

(defn- parse-comment [pre post in out]
  (if-let [[comment post'] (pstr/split (subs post 1) *close-delim*)]
    (let [standalone? (and (str/blank? pre) (str/blank? post'))]
      (when-not standalone?
        (out pre)
        (read/unread in post'))
      (-> (nodes/->Comment [comment])
          (cond->
            (and standalone? (or (seq pre) (seq post')))
            (with-meta {:pre pre :post post'}))
          out))
    (do (out pre)
        (loop [acc []]
          (let [line (read/read in)]
            (if-let [[comment post'] (pstr/split line *close-delim*)]
              (do (out (nodes/->Comment (conj acc comment)))
                  (read/unread in post'))
              (recur (conj acc line))))))))

(defn- parse-set-delimiter [pre post in out]
  (let [[delims post'] (pstr/split (subs post 1) *close-delim*)
        [open close] (-> delims
                         (subs 0 (dec (count delims)))
                         (pstr/trim)
                         (pstr/split " "))
        open (pstr/trim open)
        close (pstr/trim close)
        standalone? (and (str/blank? pre) (str/blank? post'))]
    (set! *open-delim* open)
    (set! *close-delim* close)
    (when-not standalone?
      (out pre)
      (read/unread in post'))
    (-> (nodes/->SetDelimiter open close)
        (cond->
          (and standalone? (or (seq pre) (seq post')))
          (with-meta {:pre pre :post post'}))
        out)))

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
            \! (parse-comment pre post in out)
            \{ (if (= *open-delim* default-open-delim)
                 (parse-unescaped-variable pre post in out)
                 (assert false (str "Unexpected { after changed open delim: " *open-delim*)))
            \= (parse-set-delimiter pre post in out)
            (parse-variable pre post false in out))
          false))
    (assert false "Unexpected end of line")))

(defn parse* [in out]
  (loop []
    (when-let [line (read/read in)]
      (if-let [[pre post] (pstr/split line *open-delim*)]
        (or (parse-tag pre post in out)
            (recur))
        (do (out line)
            (recur))))))

(defn parse [in out]
  (binding [*open-delim* default-open-delim
            *close-delim* default-close-delim]
    (parse* in out)))
