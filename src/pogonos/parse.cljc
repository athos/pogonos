(ns pogonos.parse
  (:refer-clojure :exclude [read])
  (:require [clojure.string :as str]
            [pogonos.nodes :as nodes]
            [pogonos.read :as read]
            [pogonos.strings :as pstr])
  #?(:clj (:import [pogonos.nodes SectionEnd])))

(def ^:const default-open-delim "{{")
(def ^:const default-close-delim "}}")
(def ^:dynamic *open-delim*)
(def ^:dynamic *close-delim*)

(defrecord Parser [in out first?])

(defn- make-parser [in out]
  (->Parser in out true))

(defn- emit [parser x]
  ((:out parser) x))

(defn- read [parser]
  (read/read (:in parser)))

(defn- unread [parser x]
  (read/unread (:in parser) x))

(defn- emit-indent [{:keys [indent first?] :as parser}]
  (when (and indent (not first?))
    (emit parser indent)))

(defn- parse-keys [s]
  (->> (str/split s #"\.")
       (mapv keyword)))

(defn- parse-variable [parser pre post unescaped?]
  (emit-indent parser)
  (emit parser pre)
  (if-let [[name post'] (pstr/split post *close-delim*)]
    (do (emit parser (nodes/->Variable (parse-keys (pstr/trim name)) unescaped?))
        (unread parser post'))
    (assert false "broken variable tag")))

(defn- parse-unescaped-variable [parser pre post]
  (emit parser pre)
  (if-let [[name post'] (pstr/split (subs post 1) "}}}")]
    (do (emit parser (nodes/->UnescapedVariable (parse-keys (pstr/trim name))))
        (unread parser post'))
    (assert false "broken variable tag")))

(defn- standalone? [pre post]
  (and (str/blank? pre) (str/blank? post)))

(defn- process-surrounding-whitespaces [parser pre post]
  (emit-indent parser)
  (emit parser pre)
  (unread parser post))

(declare parse*)

(defn- parse-open-section [parser pre post inverted?]
  (let [[name post'] (pstr/split (subs post 1) *close-delim*)
        keys (parse-keys (pstr/trim name))
        children (volatile! [])
        standalone? (standalone? pre post')
        ;; Delimiters may be changed before SectionEnd arrives.
        ;; So we need to bind them lexically here to remember
        ;; what delimiters were actually used for this section-start tag
        ;; in case of lambdas applied to the section
        open *open-delim*
        close *close-delim*
        out' (fn [x]
               (vswap! children conj x)
               (when (instance? #?(:clj SectionEnd :cljs nodes/SectionEnd) x)
                 (if (= keys (:keys x))
                   (-> ((if inverted? nodes/->Inverted nodes/->Section)
                        keys @children)
                       (cond->
                         (or (not= open default-open-delim)
                             (not= close default-close-delim))
                         (vary-meta assoc :open open :close close))
                       (cond->
                         (and standalone? (or (seq pre) (seq post')))
                         (vary-meta assoc :pre pre :post post'))
                       ((:out parser)))
                   (assert false (str "Unexpected tag " (:keys x) " occurred")))))]
    (when-not standalone?
      (process-surrounding-whitespaces parser pre post'))
    (parse* (assoc parser :out out'))))

(defn- parse-close-section [parser pre post]
  (let [[name post'] (pstr/split (subs post 1) *close-delim*)
        standalone? (standalone? pre post')]
    (when-not standalone?
      (process-surrounding-whitespaces parser pre post'))
    (-> (nodes/->SectionEnd (parse-keys (pstr/trim name)))
        (cond->
          (and standalone? (or (seq pre) (seq post')))
          (with-meta {:pre pre :post post'}))
        ((:out parser)))))

(defn- parse-partial [parser pre post]
  (let [[name post'] (pstr/split (subs post 1) *close-delim*)]
    (process-surrounding-whitespaces parser pre post')
    (emit parser (nodes/->Partial (pstr/trim name) (str/replace pre #"\S" " ")))))

(defn- parse-comment [parser pre post]
  (if-let [[comment post'] (pstr/split (subs post 1) *close-delim*)]
    (let [standalone? (standalone? pre post')]
      (when-not standalone?
        (process-surrounding-whitespaces parser pre post'))
      (-> (nodes/->Comment [comment])
          (cond->
            (and standalone? (or (seq pre) (seq post')))
            (with-meta {:pre pre :post post'}))
          ((:out parser))))
    (do (emit-indent parser)
        (emit parser pre)
        (loop [acc [(subs post 1)]]
          (let [line (read parser)]
            (if-let [[comment post'] (pstr/split line *close-delim*)]
              (do (emit parser (nodes/->Comment (conj acc comment)))
                  (unread parser post'))
              (recur (conj acc line))))))))

(defn- parse-set-delimiter [parser pre post]
  (let [[delims post'] (pstr/split (subs post 1) *close-delim*)
        [open close] (-> delims
                         (subs 0 (dec (count delims)))
                         (pstr/trim)
                         (pstr/split " "))
        open (pstr/trim open)
        close (pstr/trim close)
        standalone? (standalone? pre post')]
    (set! *open-delim* open)
    (set! *close-delim* close)
    (when-not standalone?
      (process-surrounding-whitespaces parser pre post'))
    (-> (nodes/->SetDelimiter open close)
        (cond->
          (and standalone? (or (seq pre) (seq post')))
          (with-meta {:pre pre :post post'}))
        ((:out parser)))))

(defn- parse-tag [parser pre post]
  (if-let [c (pstr/char-at post 0)]
    (if (= c \/)
      (do (parse-close-section parser pre post)
          true)
      (do (case c
            \# (parse-open-section parser pre post false)
            \^ (parse-open-section parser pre post true)
            \& (parse-variable parser pre (subs post 1) true)
            \> (parse-partial parser pre post)
            \! (parse-comment parser pre post)
            \{ (if (= *open-delim* default-open-delim)
                 (parse-unescaped-variable parser pre post)
                 (assert false (str "Unexpected { after changed open delim: " *open-delim*)))
            \= (parse-set-delimiter parser pre post)
            (parse-variable parser pre post false))
          false))
    (assert false "Unexpected end of line")))

(defn- parse* [parser]
  (loop [parser parser]
    (when-let [line (read parser)]
      (let [end? (if-let [[pre post] (pstr/split line *open-delim*)]
                   (parse-tag parser pre post)
                   (do (emit-indent parser)
                       (emit parser line)
                       false))]
        (when-not end?
          (-> (if (:first? parser)
                (assoc parser :first? false)
                parser)
              (recur)))))))

(defn parse
  ([in out] (parse in out {}))
  ([in out {:keys [open-delim close-delim indent]}]
   (binding [*open-delim* (or open-delim default-open-delim)
             *close-delim* (or close-delim default-close-delim)]
     (parse* (cond-> (make-parser in out)
               indent (assoc :indent indent))))))
