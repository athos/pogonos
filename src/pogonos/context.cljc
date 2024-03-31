(ns pogonos.context
  (:require [clojure.string :as str]
            [pogonos.protocols :as proto]))

(defn- lookup* [stack keys]
  (if-let [k (peek keys)]
    (when-let [v (loop [stack stack]
                   (when-let [v (peek stack)]
                     (if (and (map? v)
                              (not #?(:clj (identical? (v k ::none) ::none)
                                      :cljs (keyword-identical? (v k ::none) ::none))))
                       v
                       (recur (next stack)))))]
      (if (next keys)
        (get-in v keys)
        (v k)))
    (peek stack)))

(defrecord NonCheckingContext [stack]
  proto/IContext
  (lookup [_ keys]
    (lookup* stack keys))
  (push [_ val]
    (NonCheckingContext. (conj stack val))))

(defrecord CheckingContext [stack on-missing-key]
  proto/IContext
  (lookup [_ keys]
    (or (lookup* stack keys)
        (on-missing-key stack keys)))
  (push [_ val]
    (CheckingContext. (conj stack val) on-missing-key)))

(defmulti ^:private ->on-missing-key-fn (fn [x] x))

(defmethod ->on-missing-key-fn :default [x]
  (if (fn? x)
    x
    (throw
     (ex-info (str ":on-missing-key must be :error or a function, but got " (type x)) {}))))

(defmethod ->on-missing-key-fn :error [_]
  (fn [stack keys]
    (let [k (->> keys (map name) (str/join \.))]
      (throw (ex-info (str "Key \"" k "\" not found in the given context")
                      {:key k :context (last stack)})))))

(defn make-context
  ([data] (make-context data {}))
  ([data {:keys [on-missing-key]}]
   (let [stack (list data)]
     (if on-missing-key
       (->CheckingContext stack (->on-missing-key-fn on-missing-key))
       (->NonCheckingContext stack)))))
