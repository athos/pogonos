(ns pogonos.core-test
  (:require [clojure.test :refer [deftest is]]
            #?(:clj [clojure.java.io :as io])
            [pogonos.core :as pg]))

(def ^:private demo-file "templates/demo.mustache")
(def ^:private broken-file "templates/broken.mustache")

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

(deftest check-test
  (is (nil? (pg/check-string "Hello, {{name}}!")))
  (is (thrown? #?(:clj Exception :cljs js/Error)
               (pg/check-string "Hello, {{name}!")))
  #?(:clj
     (is (nil? (pg/check-file (io/as-file (io/resource demo-file))))))
  #?(:clj
     (is (thrown? Exception (pg/check-file (io/as-file (io/resource broken-file))))))
  #?(:clj
     (is (nil? (pg/check-resource demo-file))))
  #?(:clj
     (is (thrown? Exception (pg/check-resource broken-file)))))

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
