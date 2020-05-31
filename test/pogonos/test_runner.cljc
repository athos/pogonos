(ns pogonos.test-runner
  (:require [clojure.test :as t]
            pogonos.spec-test
            pogonos.output-test
            pogonos.parse-test
            pogonos.partials-test
            pogonos.reader-test
            pogonos.render-test
            pogonos.stringify-test))

(defn -main []
  (t/run-tests 'pogonos.spec-test
               'pogonos.output-test
               'pogonos.parse-test
               'pogonos.partials-test
               'pogonos.reader-test
               'pogonos.render-test
               'pogonos.stringify-test))
