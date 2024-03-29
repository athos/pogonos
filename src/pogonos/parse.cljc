(ns pogonos.parse
  (:refer-clojure :exclude [read-line])
  (:require [clojure.string :as str]
            [pogonos.error :as error :refer [error]]
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

(defn- read-to-line-end [{:keys [in]}]
  (reader/read-to-line-end in))

(defn- read-until [{:keys [in]} s]
  (reader/read-until in s))

(defn- end? [{:keys [in]}]
  (reader/end? in))

(defn- blank-trailing? [{:keys [in]}]
  (reader/blank-trailing? in))

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
                           (not (end? parser)))
                  (out indent)))
              out))))

(defn- parse-keys
  ([parser s]
   (parse-keys parser s *close-delim*))
  ([parser s close-delim]
   (let [name (pstr/trim s)
         keys (str/split name #"\.")]
     (if (some (fn [k] (or (str/blank? k) (pstr/index-of k " " 0))) keys)
       (error :invalid-variable-name
              (str "Invalid variable \"" name "\"")
              (current-line parser) (line-num parser)
              (- (col-num parser) (count (str/triml s)) (count close-delim))
              {:variable-name name})
       (apply list (map keyword keys))))))

(defn- stringify-keys [keys]
  (let [out (output/string-output)]
    (stringify/stringify-keys keys out)
    (out)))

(defn- extract-tag-content
  ([parser] (extract-tag-content parser *close-delim*))
  ([parser close-delim]
   (let [line (current-line parser)
         line-num (line-num parser)
         col-num (col-num parser)]
     (if-let [content (read-until parser close-delim)]
       (if-let [i (pstr/index-of content *open-delim* 0)]
         (error :missing-closing-delimiter
                (str "Missing closing delimiter \"" close-delim "\"")
                (strip-newline line) line-num (+ col-num (dec i))
                {:closing-delimiter close-delim})
         content)
       (let [line (strip-newline line)]
         (error :missing-closing-delimiter
                (str "Missing closing delimiter \"" close-delim "\"")
                line line-num (count line)
                {:closing-delimiter close-delim}))))))

(defn- parse-variable [parser pre unescaped?]
  (emit parser pre)
  (let [name (extract-tag-content parser)
        keys (parse-keys parser name)]
    (emit parser (nodes/->Variable keys unescaped?))))

(defn- parse-unescaped-variable [parser pre]
  (emit parser pre)
  (let [name (extract-tag-content parser "}}}")
        keys (parse-keys parser name "}}}")]
    (emit parser (nodes/->UnescapedVariable keys))))

(defn- standalone?
  ([parser pre start]
   (standalone? parser pre start false))
  ([parser pre start ignore-trailing-blank?]
   (and (= start (count pre))
        (str/blank? pre)
        (or ignore-trailing-blank?
            (blank-trailing? parser)
            (end? parser)))))

(defn- with-surrounding-whitespaces-processed [parser pre start f]
  (let [standalone? (standalone? parser pre start)]
    (when-not standalone?
      (emit parser pre))
    (let [pre (when standalone? (not-empty pre))
          post (when standalone? (read-to-line-end parser))]
      (f pre post))))

(declare parse*)

(defn make-node-buffer []
  (let [acc (volatile! nil)
        nodes (volatile! [])]
    (fn
      ([] (cond-> @nodes @acc (conj (@acc))))
      ([x]
       (if (string? x)
         (do (when-not @acc
               (vreset! acc (output/string-output)))
             (@acc x))
         (do (when @acc
               (vswap! nodes conj (@acc))
               (vreset! acc nil))
             (vswap! nodes conj x)))))))

(defn- parse-section-start [parser pre start inverted?]
  (let [name (extract-tag-content parser)
        keys (parse-keys parser name)
        children (make-node-buffer)
        ;; Delimiters may be changed before SectionEnd arrives.
        ;; So we need to bind them lexically here to remember
        ;; what delimiters were actually used for this section-start tag
        ;; in case of lambdas applied to the section
        open *open-delim*
        close *close-delim*]
    (with-surrounding-whitespaces-processed parser pre start
      (fn [pre post]
        (letfn [(out' [x]
                  (children x)
                  (when (instance? #?(:clj SectionEnd :cljs nodes/SectionEnd) x)
                    (-> ((if inverted? nodes/->Inverted nodes/->Section)
                         keys (children))
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

(defn- parse-section-end [parser pre start]
  (let [name (extract-tag-content parser)
        keys (parse-keys parser name)]
    (if (= keys (:section parser))
      (with-surrounding-whitespaces-processed parser pre start
        (fn [pre post]
          (-> (nodes/->SectionEnd keys)
              (cond-> (or pre post) (with-meta {:pre pre :post post}))
              ((:out parser)))))
      (let [expected (stringify-keys (:section parser))
            actual (stringify-keys keys)]
        (error :mismatched-section-end
               (str "Expected "
                    *open-delim* "/" expected *close-delim*
                    " tag, but got "
                    *open-delim* "/" actual *close-delim*)
               (current-line parser) (line-num parser) start
               {:opening-delimiter *open-delim* :closing-delimiter *close-delim*
                :expected expected :actual actual})))))

(defn- parse-partial* [parser pre start ctor]
  (let [standalone? (standalone? parser pre start)
        post (when standalone? (read-to-line-end parser))
        indent (when standalone?
                 ;; if standalone, append current indent to previous one
                 ;; otherwise, ignore both current and previous indents
                 (not-empty (str (:indent parser) pre)))]
    (emit parser (not-empty pre))
    (-> (ctor indent)
        (cond-> post (with-meta {:post post}))
        ((:out parser)))))

(defn- parse-partial [parser pre start]
  (let [name (pstr/trim (extract-tag-content parser))]
    (cond (str/blank? name)
          (error :invalid-partial-name
                 (str "Invalid partial \"" name "\"")
                 (strip-newline (current-line parser))
                 (line-num parser)
                 (+ start (count *open-delim*) 1)
                 {:partial-name name})

          (= (nth name 0) \*)
          (let [name' (pstr/trim (subs name 1))
                keys (parse-keys parser name')]
            (parse-partial* parser pre start #(nodes/->DynamicPartial keys %)))

          :else
          (parse-partial* parser pre start
                          #(nodes/->Partial (keyword nil name) %)))))

(defn- parse-comment [parser pre start]
  (if-let [comment (when-not (end? parser)
                     (read-until parser *close-delim*))]
    (with-surrounding-whitespaces-processed parser pre start
      (fn [pre post]
        (-> (nodes/->Comment [comment])
            (cond-> (or pre post) (with-meta {:pre pre :post post}))
            ((:out parser)))))
    (let [standalone-open? (standalone? parser pre start true)]
      (when-not standalone-open?
        (emit parser pre))
      (loop [acc [(read-to-line-end parser)]]
        (if (end? parser)
          (let [prev-line (current-line parser)
                prev-line-num (line-num parser)
                line (some-> prev-line strip-newline)]
            (error :missing-closing-delimiter
                   (str "Missing closing delimiter \"" *close-delim* "\""
                        " for comment tag")
                   line prev-line-num (count line)
                   {:closing-delimiter *close-delim*}))
          (if-let [comment (read-until parser *close-delim*)]
            (let [post (when (blank-trailing? parser)
                         (read-to-line-end parser))]
              (->> (cond-> (nodes/->Comment (conj acc comment))
                     (or (and standalone-open? (seq pre)) (seq post))
                     (with-meta {:pre pre :post post}))
                   (emit parser)))
            (recur (conj acc (read-to-line-end parser)))))))))

(defn- parse-set-delimiters [parser pre start]
  (let [line (current-line parser)
        line-num (line-num parser)
        delims (extract-tag-content parser (str \= *close-delim*))]
    (if-let [[_ open close] (-> (pstr/trim delims) (pstr/split " "))]
      (let [open (pstr/trim open)
            close (pstr/trim close)]
        (when (or (pstr/index-of open " " 0) (pstr/index-of close " " 0))
          (error :invalid-set-delimiters
                 (str "Invalid set delimiters tag "
                      *open-delim* \= delims \= *close-delim*)
                 line line-num start
                 {:opening-delimiter *open-delim* :closing-delimiter *close-delim*
                  :delimiters delims}))
        (set! *open-delim* open)
        (set! *close-delim* close)
        (with-surrounding-whitespaces-processed parser pre start
          (fn [pre post]
            (-> (nodes/->SetDelimiter open close)
                (cond-> (or pre post) (with-meta {:pre pre :post post}))
                ((:out parser))))))
      (error :invalid-set-delimiters
             (str "Invalid set delimiters tag "
                  *open-delim* \= delims \= *close-delim*)
             line line-num start
             {:opening-delimiter *open-delim* :closing-delimiter *close-delim*
              :delimiters delims}))))

(defn- parse-tag [parser pre start]
  (let [line (current-line parser)
        line-num (line-num parser)
        c (read-char parser)]
    (if (and c (not= c \newline))
      (if (= c \/)
        (do (parse-section-end parser pre start)
            false)
        (do (case c
              \# (parse-section-start parser pre start false)
              \^ (parse-section-start parser pre start true)
              \& (parse-variable parser pre true)
              \> (parse-partial parser pre start)
              \! (parse-comment parser pre start)
              \= (parse-set-delimiters parser pre start)
              \{ (if (and (= *open-delim* default-open-delim)
                          (= *close-delim* default-close-delim))
                   (parse-unescaped-variable parser pre)
                   (error :invalid-unescaped-variable-tag
                          (str "Unescaped variable tag \"" *open-delim* "{\" "
                               "cannot be used while changing delimiters")
                          line line-num start
                          {:opening-delimiter *open-delim*
                           :closing-delimiter *close-delim*}))
              (do (unread-char parser)
                  (parse-variable parser pre false)))
            true))
      (error :incomplete-tag
             (str "Incomplete tag \"" *open-delim* "\" found")
             (strip-newline line) line-num start
             {:opening-delimiter *open-delim*
              :closing-delimiter *close-delim*}))))

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
            (let [line (some-> prev-line strip-newline)
                  expected (stringify-keys (:section parser))]
              (error :missing-section-end
                     (str "Missing section-end tag " *open-delim* "/"
                          expected *close-delim*)
                     line prev-line-num (count line)
                     {:opening-delimiter *open-delim*
                      :closing-delimiter *close-delim*
                      :expected expected}))))))))

(defn parse
  ([in out] (parse in out {}))
  ([in out
    {:keys [source suppress-verbose-errors open-delim close-delim indent]
     :or {suppress-verbose-errors false}}]
   (binding [*open-delim* (or open-delim default-open-delim)
             *close-delim* (or close-delim default-close-delim)
             error/*source* source
             error/*suppress-verbose-errors* suppress-verbose-errors]
     (let [parser (-> (make-parser in out)
                      (assoc :indent indent)
                      (enable-indent-insertion))]
       (parse* parser)))))
