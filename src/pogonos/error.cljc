(ns pogonos.error)

(def ^:dynamic *show-error-details* true)
(def ^:dynamic *source* nil)

(defn- has-error-details? [{:keys [error-line line column]}]
  (and error-line line column))

(defn- print-error-message [msg {:keys [line column] :as ex-data}]
  (print msg)
  (when (has-error-details? ex-data)
    (print " (")
    (when *source*
      (print *source*)
      (print \:))
    (print line)
    (print \:)
    (print column)
    (print \))
    (when *show-error-details*
      (println \:))))

(defn- print-detailed-error-info [{:keys [error-line line column] :as ex-data}]
  (when (and *show-error-details* (has-error-details? ex-data))
    (print "\n  ")
    (print line)
    (print "| ")
    (println error-line)
    (print "    ")
    (dotimes [_ (+ (count (str line)) (dec column))]
      (print \space))
    (print "^^")))

(defn print-error [msg ex-data]
  (print-error-message msg ex-data)
  (print-detailed-error-info ex-data))

(defn stringify-error [msg ex-data]
  (with-out-str
    (print-error msg ex-data)))

(defn error
  ([type msg line line-num col-num]
   (error type msg line line-num col-num {}))
  ([type msg line line-num col-num data]
   (let [ex-data (cond-> (assoc data :type type :message msg)
                   *source* (assoc :source *source*)
                   line (assoc :error-line line)
                   line-num (assoc :line (some-> line-num inc))
                   col-num (assoc :column (some-> col-num inc)))
         msg' (stringify-error msg ex-data)]
     (throw (ex-info msg' ex-data)))))
