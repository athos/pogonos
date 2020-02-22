(ns pogonos.output-test
  (:require #?(:clj [clojure.java.io :as io])
            [clojure.test :refer [deftest is]]
            [pogonos.output :as output])
  #?(:clj (:import [java.io File])))

(deftest stdout-output-test
  (let [out (output/stdout-output)]
    (is (= "foo\nbar\nbaz"
           (with-out-str
             (out "foo\n")
             (out "bar\n")
             (out "baz")
             (out))))))

(deftest string-output-test
  (let [out (output/string-output)]
    (out "foo\n")
    (out "bar\n")
    (out "baz")
    (is (= "foo\nbar\nbaz" (out)))))

#?(:clj
   (deftest file-output-test
     (let [file (File/createTempFile "tmp" nil)
           out (output/file-output file)]
       (out "foo\n")
       (out "bar\n")
       (out "baz")
       (out)
       (is (= "foo\nbar\nbaz" (slurp file))))))

#?(:clj
   (deftest writer-output-test
     (let [file (File/createTempFile "tmp" nil)]
       (with-open [w (io/writer file)]
         (let [out (output/writer-output w)]
           (out "foo\n")
           (out "bar\n")
           (out "baz")
           (out)))
       (is (= "foo\nbar\nbaz" (slurp file))))))
