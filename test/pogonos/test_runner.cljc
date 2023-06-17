(ns pogonos.test-runner
  (:require [clojure.test :as t]
            pogonos.spec-test
            #?(:clj pogonos.api-test)
            pogonos.core-test
            pogonos.output-test
            pogonos.parse-test
            pogonos.partials-test
            pogonos.reader-test
            pogonos.render-test
            pogonos.stringify-test))

(defn- exit-with [{:keys [fail error]}]
  (let [succeeded? (zero? (+ fail error))]
    #?(:clj (System/exit (if succeeded? 0 1))
     :cljs (when-not succeeded?
             (throw (ex-info "Tests failed" {:fail fail :error error}))))))

#?(:cljs
   (defmethod t/report [::t/default :end-run-tests] [summary]
     (exit-with summary)))

(defn- clean-up [m]
  #?(:clj (exit-with m)
     :cljs m))

(defn -main []
  (clean-up
   (t/run-tests 'pogonos.spec-test
                #?(:clj 'pogonos.api-test)
                'pogonos.core-test
                'pogonos.output-test
                'pogonos.parse-test
                'pogonos.partials-test
                'pogonos.reader-test
                'pogonos.render-test
                'pogonos.stringify-test)))
