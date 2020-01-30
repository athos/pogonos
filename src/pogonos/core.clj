(ns pogonos.core
  (:refer-clojure :exclude [read])
  (:require [clojure.string :as str]))

(def ^:const default-open-delim "{{")
(def ^:const default-close-delim "}}")
(def ^:dynamic *open-delim*)
(def ^:dynamic *close-delim*)

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

(defprotocol IReader
  (read [this])
  (unread [this s])
  (end? [this]))

(deftype StringReader
    [^String src
     ^:unsynchronized-mutable offset
     ^:unsynchronized-mutable ^String pushback]
  IReader
  (read [this]
    (if-let [ret pushback]
      (do (set! pushback nil)
          ret)
      (when (< offset (count src))
        (let [i (str/index-of src "\n" offset)
              offset' (or (some-> i inc) (count src))
              ret (subs src offset offset')]
          (set! offset offset')
          ret))))
  (unread [this s]
    (set! pushback s))
  (end? [this]
    (and (nil? pushback) (>= offset (count src)))))

(defn make-string-reader [s]
  (StringReader. s 0 nil))

(defn process-keys [s]
  (->> (str/split s #"\.")
       (mapv keyword)))

(defn process-variable [pre post unescaped? in out]
  (out pre)
  (if-let [[name post'] (split post *close-delim*)]
    (do (out {:type :variable :keys (process-keys (trim name))
              :unescaped? unescaped?})
        (unread in post'))
    (assert false "broken variable tag")))

(defn process-unescaped-variable [pre post in out]
  (out pre)
  (if-let [[name post'] (split post "}}}")]
    (do (out {:type :variable :keys (process-keys (trim name))
              :unescaped? true})
        (unread in post'))
    (assert false "broken variable tag")))

(declare process*)

(defn process-open-section [pre post in out]
  (let [[name post'] (split (subs post 1) *close-delim*)
        keys (process-keys (trim name))
        children (volatile! [])
        out' (fn [x]
               (if (= (:type x) :section-end)
                 (if (= keys (:keys x))
                   (out {:type :section :keys keys :children @children})
                   (assert false (str "Unexpected tag " (:keys x) " occurred")))
                 (vswap! children conj x)))]
    (when-not (and (str/blank? pre) (str/blank? post'))
      (out pre)
      (unread in post'))
    (process* in out')))

(defn process-close-section [pre post in out]
  (let [[name post'] (split (subs post 1) *close-delim*)]
    (when-not (and (str/blank? pre) (str/blank? post'))
      (out pre)
      (unread in post'))
    (out {:type :section-end :keys (process-keys (trim name))})))

(defn process-partial [pre post in out]
  (let [[name post'] (split (subs post 1) *close-delim*)]
    (out {:type :partial :name (trim name) :indent pre})
    (unread in post')))

(defn process-comment [pre post in out]
  (if-let [[comment post'] (split (subs post 1) *close-delim*)]
    (when-not (and (str/blank? pre) (str/blank? post'))
      (out pre)
      (unread in post'))
    (do (out pre)
        (loop []
          (let [line (read in)]
            (if-let [[comment post'] (split line *close-delim*)]
              (unread in post')
              (recur)))))))

(defn process-set-delimiter [pre post in out]
  (let [[delims post'] (split (subs post 1) *close-delim*)
        [open close] (-> delims
                         (subs 0 (dec (count delims)))
                         (trim)
                         (split " "))]
    (set! *open-delim* (trim open))
    (set! *close-delim* (trim close))
    (when-not (and (str/blank? pre) (str/blank? post'))
      (out pre)
      (unread in post'))))

(defn process-tag [pre post in out]
  (if-let [c (char-at post 0)]
    (if (= c \/)
      (do (process-close-section pre post in out)
          true)
      (do (case c
            \# (process-open-section pre post in out)
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
    (when-let [line (read in)]
      (if-let [[pre post] (split line *open-delim*)]
        (or (process-tag pre post in out)
            (recur))
        (do (out line)
            (recur))))))

(defn process [in out]
  (binding [*open-delim* default-open-delim
            *close-delim* default-close-delim]
    (process* in out)))

(defn escape [s]
  (str/replace s #"[&<>\"']"
               #({"&" "&amp;", "<" "&lt;", ">" "g"
                  "\"" "&quot;", "'" "&#39;"}
                 (str %))))

(defmulti render* (fn [stack out x] (:type x)))
(defmethod render* :default [stack out x]
  (out (str x)))

(defn lookup [stack keys]
  (if (seq keys)
    (let [[k & ks] keys]
      (when-let [x (first (filter #(and (map? %) (contains? % k)) stack))]
        (get-in x keys)))
    (first stack)))

(defmethod render* :variable [stack out x]
  (let [ctx (lookup stack (:keys x))]
    (if (fn? ctx)
      (process* (make-string-reader (str (ctx))) #(render* stack out %))
      (->> (str ctx)
           ((if (:unescaped? x) identity escape))
           out))))

(defmethod render* :section [stack out x]
  (let [ctx (lookup stack (:keys x))]
    (cond (not ctx) nil

          (map? ctx)
          (doseq [node (:children x)]
            (render* (cons ctx stack) out node))

          (and (coll? ctx) (sequential? ctx))
          (when (seq ctx)
            (doseq [e ctx, node (:children x)]
              (render* (cons e stack) out node)))

          :else
          (doseq [node (:children x)]
            (render* stack out node)))))

(defn render-string [s data]
  (let [in (make-string-reader s)
        sb (StringBuilder.)
        out #(.append sb %)
        stack [data]]
    (process in (fn [x] (render* stack out x)))
    (str sb)))
