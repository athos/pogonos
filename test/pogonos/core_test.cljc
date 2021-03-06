(ns pogonos.core-test
  (:require [clojure.test :refer [deftest is]]
            #?(:clj [clojure.java.io :as io])
            [pogonos.core :as pg]))

(def ^:private demo-file "templates/demo.mustache")

(def ^:private data
  {:header "Colors"
   :items [{:name "red", :first true, :url "#Red"}
           {:name "green", :link true, :url "#Green"}
           {:name "blue", :link true, :url "#Blue"}]
   :empty false})

#?(:clj
   (deftest parse-test
     (is (= (pg/parse-string (slurp (io/resource demo-file)))
            (pg/parse-file (io/as-file (io/resource demo-file)))
            (pg/parse-resource demo-file)))))

#?(:clj
   (deftest render-test
     (is (= "<h1>Colors</h1>

        <li><strong>red</strong></li>
        <li><a href=\"#Green\">green</a></li>
        <li><a href=\"#Blue\">blue</a></li>

"
            (pg/render (pg/parse-resource demo-file) data)
            (pg/render-string (slurp (io/resource demo-file)) data)
            (pg/render-file (io/as-file (io/resource demo-file)) data)
            (pg/render-resource demo-file data)))))

(deftest perr-test
  (try
    (pg/render-string "Hello, {{name}!" {:name "Clojure"})
    (throw (ex-info "NOT REACHED" {}))
    (catch #?(:clj Throwable :cljs :default) t
      (is (= "Missing closing delimiter \"}}\" (1:16):

  1| Hello, {{name}!
                    ^^\n"
             (with-out-str (pg/perr t)))))))
