(ns pogonos.parse
  (:refer-clojure :exclude [read-line])
  (:require [clojure.string :as str]
            [pogonos.nodes :as nodes]
            [pogonos.reader :as reader]
            [pogonos.strings :as pstr])
  #?(:clj (:import [pogonos.nodes SectionEnd])))

(def ^:const default-open-delim "{{")
(def ^:const default-close-delim "}}")
(def ^:dynamic *open-delim*)
(def ^:dynamic *close-delim*)

(defrecord Parser [in out])

(defn make-parser [in out]
  (->Parser (reader/make-line-buffering-reader in) out))

(defn- read-char [{:keys [in]}]
  (reader/read-char in))

(defn- unread-char [{:keys [in]}]
  (reader/unread-char in))

(defn- read-line [{:keys [in]}]
  (reader/read-line in))

(defn- read-until [{:keys [in]} s]
  (reader/read-until in s))

(defn- line-num [{:keys [in]}]
  (cond-> (reader/line-num in)
    (>= (reader/col-num in) (count (reader/line in)))
    inc))

(defn- col-num [{:keys [in]}]
  (let [col (reader/col-num in)]
    (if (>= col (count (reader/line in)))
      0
      col)))

(defn- emit [{:keys [out]} x]
  (some-> (not-empty x) out))

(defn- enable-indent-insertion [parser]
  (update parser :out
          (fn [out]
            (if-let [indent (:indent parser)]
              (fn [x]
                (out x)
                (when (and (string? x) (str/ends-with? x "\n")
                           (not (reader/end? (:in parser))))
                  (out indent)))
              out))))

(defn- parse-keys [s]
  (->> (str/split s #"\.")
       (mapv keyword)))

(defn- parse-variable [parser pre unescaped?]
  (emit parser pre)
  (if-let [name (read-until parser *close-delim*)]
    (emit parser (nodes/->Variable (parse-keys (pstr/trim name)) unescaped?))
    (assert false "broken variable tag")))

(defn- parse-unescaped-variable [parser pre]
  (emit parser pre)
  (if-let [name (read-until parser "}}}")]
    (emit parser (nodes/->UnescapedVariable (parse-keys (pstr/trim name))))
    (assert false "broken variable tag")))

(defn- standalone? [{:keys [in]} pre start]
  (and (= start (count pre))
       (str/blank? pre)
       (reader/blank-trailing? in)))

(defn- with-surrounding-whitespaces-processed [parser pre start f]
  (let [standalone? (standalone? parser pre start)]
    (when-not standalone?
      (emit parser pre))
    (let [pre (when standalone? (not-empty pre))
          post (when standalone? (not-empty (read-line parser)))]
      (f pre post))))

(declare parse*)

(defn- parse-open-section [parser pre start inverted?]
  (let [name (read-until parser *close-delim*)
        keys (parse-keys (pstr/trim name))
        children (volatile! [])
        ;; Delimiters may be changed before SectionEnd arrives.
        ;; So we need to bind them lexically here to remember
        ;; what delimiters were actually used for this section-start tag
        ;; in case of lambdas applied to the section
        open *open-delim*
        close *close-delim*]
    (with-surrounding-whitespaces-processed parser pre start
      (fn [pre post]
        (letfn [(out' [x]
                  (vswap! children conj x)
                  (when (instance? #?(:clj SectionEnd :cljs nodes/SectionEnd) x)
                    (-> ((if inverted? nodes/->Inverted nodes/->Section)
                         keys @children)
                        (cond->
                            (or (not= open default-open-delim)
                                (not= close default-close-delim))
                          (vary-meta assoc :open open :close close)
                          (or pre post)
                          (vary-meta assoc :pre pre :post post))
                        ((:out parser)))))]
          (-> parser
              (assoc :out out' :section keys)
              (enable-indent-insertion)
              parse*))))))

(defn- parse-close-section [parser pre start]
  (let [name (read-until parser *close-delim*)
        keys (parse-keys (pstr/trim name))]
    (if (= keys (:section parser))
      (with-surrounding-whitespaces-processed parser pre start
        (fn [pre post]
          (-> (nodes/->SectionEnd keys)
              (cond-> (or pre post) (with-meta {:pre pre :post post}))
              ((:out parser)))))
      (assert false
              (str "section-end tag for " (:section parser)
                   " expected, but got " keys)))))

(defn- parse-partial [parser pre start]
  (let [name (read-until parser *close-delim*)]
    (with-surrounding-whitespaces-processed parser pre start
      (fn [pre post]
        (emit parser pre)
        (->> (str/replace (str pre) #"\S" " ")
             (str (:indent parser)) ;; prepend current indent
             (nodes/->Partial (pstr/trim name))
             (emit parser))))))

(defn- parse-comment [parser pre start]
  (let [comment (read-until parser *close-delim*)]
    (with-surrounding-whitespaces-processed parser pre start
      (fn [pre post]
        (if comment
          (-> (nodes/->Comment [comment])
              (cond-> (or pre post) (with-meta {:pre pre :post post}))
              ((:out parser)))
          (loop [acc [(read-line parser)]]
            (if-let [comment (read-until parser *close-delim*)]
              (do (when (reader/blank-trailing? (:in parser))
                    (read-line parser))
                  (emit parser (nodes/->Comment (conj acc comment))))
              (if-let [line (read-line parser)]
                (recur (conj acc line))
                (assert false "}} expected")))))))))

(defn- parse-set-delimiters [parser pre start]
  (let [delims (read-until parser *close-delim*)
        [_ open close] (-> delims
                           (subs 0 (dec (count delims)))
                           (pstr/trim)
                           (pstr/split " "))
        open (pstr/trim open)
        close (pstr/trim close)]
    (set! *open-delim* open)
    (set! *close-delim* close)
    (with-surrounding-whitespaces-processed parser pre start
      (fn [pre post]
        (-> (nodes/->SetDelimiter open close)
            (cond-> (or pre post) (with-meta {:pre pre :post post}))
            ((:out parser)))))))

(defn- parse-tag [parser pre start]
  (if-let [c (read-char parser)]
    (if (= c \/)
      (do (parse-close-section parser pre start)
          false)
      (do (case c
            \# (parse-open-section parser pre start false)
            \^ (parse-open-section parser pre start true)
            \& (parse-variable parser pre true)
            \> (parse-partial parser pre start)
            \! (parse-comment parser pre start)
            \= (parse-set-delimiters parser pre start)
            \{ (if (= *open-delim* default-open-delim)
                 (parse-unescaped-variable parser pre)
                 (assert false (str "Unexpected { after changed open delim: " *open-delim*)))
            (do (unread-char parser)
                (parse-variable parser pre false)))
          true))
    (assert false "Unexpected end of line")))

(defn- parse* [parser]
  (loop []
    (if-let [pre (read-until parser *open-delim*)]
      (let [start (- (col-num parser) (count *open-delim*))]
        (when (or (parse-tag parser pre start)
                  (nil? (:section parser)))
          (recur)))
      (if-let [line (read-line parser)]
        (do (emit parser line)
            (recur))
        (when-let [section (:section parser)]
          (assert false (str "Missing section-end tag for " section)))))))

(defn parse
  ([in out] (parse in out {}))
  ([in out {:keys [open-delim close-delim indent]}]
   (binding [*open-delim* (or open-delim default-open-delim)
             *close-delim* (or close-delim default-close-delim)]
     (let [parser (-> (make-parser in out)
                      (cond-> indent (assoc :indent indent))
                      (enable-indent-insertion))]
       (parse* parser)))))
