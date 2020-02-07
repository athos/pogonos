(ns pogonos.parse
  (:refer-clojure :exclude [read-line])
  (:require [clojure.string :as str]
            [pogonos.error :refer [error]]
            [pogonos.nodes :as nodes]
            [pogonos.output :as output]
            [pogonos.reader :as reader]
            [pogonos.stringify :as stringify]
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

(defn- current-line [{:keys [in]}]
  (reader/line in))

(defn- line-num [{:keys [in]}]
  (reader/line-num in))

(defn- col-num [{:keys [in]}]
  (reader/col-num in))

(defn- emit [{:keys [out]} x]
  (some-> (not-empty x) out))

(defn- strip-newline [s]
  (str/replace s #"\n$" ""))

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

(defn- stringify-keys [keys]
  (let [out (output/string-output)]
    (stringify/stringify-keys keys #(output/append out %))
    (output/complete out)))

(defn- extract-tag-content
  ([parser] (extract-tag-content parser *close-delim*))
  ([parser close-delim]
   (let [line (current-line parser)
         line-num (line-num parser)]
     (if-let [content (read-until parser close-delim)]
       content
       (let [line (strip-newline line)]
         (error :missing-close-delim
                (str "Missing closing delimiter \"" close-delim "\"")
                line line-num (count line)))))))

(defn- parse-variable [parser pre unescaped?]
  (emit parser pre)
  (let [name (extract-tag-content parser)]
    (emit parser (nodes/->Variable (parse-keys (pstr/trim name)) unescaped?))))

(defn- parse-unescaped-variable [parser pre]
  (emit parser pre)
  (let [name (extract-tag-content parser "}}}")]
    (emit parser (nodes/->UnescapedVariable (parse-keys (pstr/trim name))))))

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
  (let [name (extract-tag-content parser)
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
  (let [name (extract-tag-content parser)
        keys (parse-keys (pstr/trim name))]
    (if (= keys (:section parser))
      (with-surrounding-whitespaces-processed parser pre start
        (fn [pre post]
          (-> (nodes/->SectionEnd keys)
              (cond-> (or pre post) (with-meta {:pre pre :post post}))
              ((:out parser)))))
      (error :inconsistent-section-end
             (str "Expected "
                  *open-delim* "/" (stringify-keys (:section parser)) *close-delim*
                  " tag, but got "
                  *open-delim* "/" (stringify-keys keys) *close-delim*)
             (current-line parser) (line-num parser) start))))

(defn- parse-partial [parser pre start]
  (let [name (extract-tag-content parser)]
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
          (loop [acc (cond-> [] (not post) (conj (read-line parser)))]
            (let [prev-line (current-line parser)
                  prev-line-num (line-num parser)]
              (if-let [comment (read-until parser *close-delim*)]
                (do (when (reader/blank-trailing? (:in parser))
                      (read-line parser))
                    (emit parser (nodes/->Comment (conj acc comment))))
                (if-let [line (read-line parser)]
                  (recur (conj acc line))
                  (let [line (strip-newline prev-line)]
                    (error :missing-close-delim
                           (str "Missing closing delimiter \"" *close-delim* "\""
                                " for comment tag")
                           line prev-line-num (count line))))))))))))

(defn- parse-set-delimiters [parser pre start]
  (let [delims (extract-tag-content parser)
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
  (let [line (current-line parser)
        line-num (line-num parser)
        c (read-char parser)]
    (if (and c (not= c \newline))
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
                   (error :invalid-unescaped-variable-tag
                          (str "Unescaped variable tag \"" *open-delim* "{\" "
                               "cannot be used while changing delimiters")
                          line line-num start))
              (do (unread-char parser)
                  (parse-variable parser pre false)))
            true))
      (error :incomplete-tag
             (str "Found incomplete tag \"" *open-delim* "\"")
             (strip-newline line) line-num start))))

(defn- parse* [parser]
  (loop []
    (let [prev-line (current-line parser)
          prev-line-num (line-num parser)]
      (if-let [pre (read-until parser *open-delim*)]
        (let [start (- (col-num parser) (count *open-delim*))]
          (when (or (parse-tag parser pre start)
                    (nil? (:section parser)))
            (recur)))
        (if-let [line (read-line parser)]
          (do (emit parser line)
              (recur))
          (when (:section parser)
            (error :missing-section-end
                   (str "Missing section-end tag " *open-delim* "/"
                        (stringify-keys (:section parser)) *close-delim*)
                   prev-line prev-line-num (count prev-line))))))))

(defn parse
  ([in out] (parse in out {}))
  ([in out {:keys [open-delim close-delim indent]}]
   (binding [*open-delim* (or open-delim default-open-delim)
             *close-delim* (or close-delim default-close-delim)]
     (let [parser (-> (make-parser in out)
                      (cond-> indent (assoc :indent indent))
                      (enable-indent-insertion))]
       (parse* parser)))))
