(ns pogonos.test-runner
  (:require [clojure.test :as t]
            pogonos.spec-test))

(defn -main []
  (t/run-tests 'pogonos.spec-test))
