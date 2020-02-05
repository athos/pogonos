(ns pogonos.spec-test
  (:require clojure.test
            [pogonos.core :as pogonos]
            #?(:clj [pogonos.spec-test-macros :refer [import-spec-tests]]))
  #?(:cljs (:require-macros [pogonos.spec-test-macros :refer [import-spec-tests]])))

(import-spec-tests)
