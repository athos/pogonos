(ns pogonos.error
  (:require [clojure.string :as str]))

(def ^:dynamic *source* nil)

(defn error
  ([type msg line line-num col-num]
   (error type msg line line-num col-num {}))
  ([type msg line line-num col-num data]
   (let [line-num (some-> line-num inc)
         col-num (some-> col-num inc)
         msg' (str msg
                   (when (and line line-num col-num)
                     (str " (" (when *source* (str *source* \:))
                          line-num \: col-num "):\n"
                          "\n  " line-num "| " line "\n"
                          "    "
                          (->> (repeat (+ (count (str line-num))
                                          (dec col-num))
                                       \space)
                               (apply str))
                          "^^")))]
     (->> (cond-> (assoc data :type type)
            *source* (assoc :source *source*)
            line-num (assoc :line line-num)
            col-num (assoc :column col-num))
          (ex-info msg')
          (throw)))))
