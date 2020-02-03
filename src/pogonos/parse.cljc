(ns pogonos.parse
  (:refer-clojure :exclude [read-line])
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

(defn make-parser [in out]
  (->Parser (read/make-line-buffered-reader in) out true))

(defn- read-char [{:keys [in]}]
  (read/read-char in))

(defn- unread-char [{:keys [in]}]
  (read/unread-char in))

(defn- read-line [{:keys [in]}]
  (read/read-line in))

(defn- read-until [{:keys [in]} s]
  (read/read-until in s))

(defn- emit [parser x]
  ((:out parser) x))

(defn- emit-indent [{:keys [indent first?] :as parser}]
  (when (and indent (not first?))
    (emit parser indent)))

(defn- parse-keys [s]
  (->> (str/split s #"\.")
       (mapv keyword)))

(defn- parse-variable [parser pre unescaped?]
  (emit-indent parser)
  (emit parser pre)
  (if-let [name (read-until parser *close-delim*)]
    (emit parser (nodes/->Variable (parse-keys (pstr/trim name)) unescaped?))
    (assert false "broken variable tag")))

(defn- parse-unescaped-variable [parser pre]
  (emit-indent parser)
  (emit parser pre)
  (if-let [name (read-until parser "}}}")]
    (emit parser (nodes/->UnescapedVariable (parse-keys (pstr/trim name))))
    (assert false "broken variable tag")))

(defn- standalone? [parser pre]
  (and (str/blank? pre)
       ;; FIXME
       (str/blank? (subs (read/line (:in parser))
                         (read/col-num (:in parser))))))

(defn- with-surrounding-whitespaces-processed [parser pre f]
  (let [standalone? (standalone? parser pre)]
    (when-not standalone?
      (emit-indent parser)
      (emit parser pre))
    (let [pre (when standalone? (not-empty pre))
          post (when standalone? (not-empty (read-line parser)))]
      (f pre post))))

(declare parse*)

(defn- parse-open-section [parser pre inverted?]
  (let [name (read-until parser *close-delim*)
        keys (parse-keys (pstr/trim name))
        children (volatile! [])
        ;; Delimiters may be changed before SectionEnd arrives.
        ;; So we need to bind them lexically here to remember
        ;; what delimiters were actually used for this section-start tag
        ;; in case of lambdas applied to the section
        open *open-delim*
        close *close-delim*]
    (with-surrounding-whitespaces-processed parser pre
      (fn [pre post]
        (letfn [(out' [x]
                  (vswap! children conj x)
                  (when (instance? #?(:clj SectionEnd :cljs nodes/SectionEnd) x)
                    (if (= keys (:keys x))
                      (-> ((if inverted? nodes/->Inverted nodes/->Section)
                           keys @children)
                          (cond->
                            (or (not= open default-open-delim)
                                (not= close default-close-delim))
                            (vary-meta assoc :open open :close close)
                            (or pre post)
                            (vary-meta assoc :pre pre :post post))
                          ((:out parser)))
                      (assert false (str "Unexpected tag " (:keys x) " occurred")))))]
         (parse* (assoc parser :out out')))))))

(defn- parse-close-section [parser pre]
  (let [name (read-until parser *close-delim*)]
    (with-surrounding-whitespaces-processed parser pre
      (fn [pre post]
        (-> (nodes/->SectionEnd (parse-keys (pstr/trim name)))
            (cond-> (or pre post) (with-meta {:pre pre :post post}))
            ((:out parser)))))))

(defn- parse-partial [parser pre]
  (let [name (read-until parser *close-delim*)]
    (emit-indent parser)
    (emit parser pre)
    (emit parser (nodes/->Partial (pstr/trim name) (str/replace pre #"\S" " ")))))

(defn- parse-comment [parser pre]
  (if-let [comment (read-until parser *close-delim*)]
    (with-surrounding-whitespaces-processed parser pre
      (fn [pre post]
        (-> (nodes/->Comment [comment])
            (cond-> (or pre post) (with-meta {:pre pre :post post}))
            ((:out parser)))))
    (do (emit-indent parser)
        (emit parser pre)
        (loop [acc [(read-line parser)]]
          (if-let [comment (read-until parser *close-delim*)]
            (emit parser (nodes/->Comment (conj acc comment)))
            (if-let [line (read-line parser)]
              (recur (conj acc line))
              (assert false "}} expected")))))))

(defn- parse-set-delimiters [parser pre]
  (let [delims (read-until parser *close-delim*)
        [_ open close] (-> delims
                           (subs 0 (dec (count delims)))
                           (pstr/trim)
                           (pstr/split " "))
        open (pstr/trim open)
        close (pstr/trim close)]
    (set! *open-delim* open)
    (set! *close-delim* close)
    (with-surrounding-whitespaces-processed parser pre
      (fn [pre post]
        (-> (nodes/->SetDelimiter open close)
            (cond-> (or pre post) (with-meta {:pre pre :post post}))
            ((:out parser)))))))

(defn- parse-tag [parser pre]
  (if-let [c (read-char parser)]
    (if (= c \/)
      (do (parse-close-section parser pre)
          false)
      (do (case c
            \# (parse-open-section parser pre false)
            \^ (parse-open-section parser pre true)
            \& (parse-variable parser pre true)
            \> (parse-partial parser pre)
            \! (parse-comment parser pre)
            \= (parse-set-delimiters parser pre)
            \{ (if (= *open-delim* default-open-delim)
                 (parse-unescaped-variable parser pre)
                 (assert false (str "Unexpected { after changed open delim: " *open-delim*)))
            (do (unread-char parser)
                (parse-variable parser pre false)))
          true))
    (assert false "Unexpected end of line")))

(defn- parse* [parser]
  (loop [parser parser]
    (let [continue? (if-let [pre (read-until parser *open-delim*)]
                      (parse-tag parser pre)
                      (when-let [line (read-line parser)]
                        (emit-indent parser)
                        (emit parser line)
                        true))]
      (when continue?
        (-> (if (:first? parser)
              (assoc parser :first? false)
              parser)
            (recur))))))

(defn parse
  ([in out] (parse in out {}))
  ([in out {:keys [open-delim close-delim indent]}]
   (binding [*open-delim* (or open-delim default-open-delim)
             *close-delim* (or close-delim default-close-delim)]
     (parse* (cond-> (make-parser in out)
               indent (assoc :indent indent))))))
