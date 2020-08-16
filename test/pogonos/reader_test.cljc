(ns pogonos.reader-test
  (:require [clojure.test :refer [deftest is testing]]
            [pogonos.protocols :as proto]
            [pogonos.reader :as reader])
  #?(:clj
     (:import [java.io File]
              [pogonos.reader FileReader])))

(deftest string-reader-test
  (let [r (reader/make-string-reader "")]
    (is (nil? (proto/read-line r))))
  (let [r (reader/make-string-reader "foo")]
    (is (= "foo" (proto/read-line r)))
    (is (nil? (proto/read-line r))))
  (let [r (reader/make-string-reader "\n")]
    (is (= "\n" (proto/read-line r)))
    (is (nil? (proto/read-line r))))
  (let [r (reader/make-string-reader "foo\n")]
    (is (= "foo\n" (proto/read-line r)))
    (is (nil? (proto/read-line r))))
  (let [r (reader/make-string-reader "foo\nbar\nbaz")]
    (is (= "foo\n" (proto/read-line r)))
    (is (= "bar\n" (proto/read-line r)))
    (is (= "baz" (proto/read-line r)))
    (is (nil? (proto/read-line r))))
  (let [r (reader/make-string-reader "foo\nbar\nbaz\n")]
    (is (= "foo\n" (proto/read-line r)))
    (is (= "bar\n" (proto/read-line r)))
    (is (= "baz\n" (proto/read-line r)))
    (is (nil? (proto/read-line r)))))

#?(:clj
   (defn- ^FileReader make-file-reader [content]
     (-> (doto (File/createTempFile "tmp" nil)
           (spit content))
         (reader/make-file-reader))))

#?(:clj
   (deftest file-reader-test
     (with-open [r (make-file-reader "")]
       (is (nil? (proto/read-line r))))
     (with-open [r (make-file-reader "foo")]
       (is (= "foo" (proto/read-line r)))
       (is (nil? (proto/read-line r))))
     (with-open [r (make-file-reader "\n")]
       (is (= "\n" (proto/read-line r)))
       (is (nil? (proto/read-line r))))
     (with-open [r (make-file-reader "foo\n")]
       (is (= "foo\n" (proto/read-line r)))
       (is (nil? (proto/read-line r))))
     (with-open [r (make-file-reader "foo\nbar\nbaz")]
       (is (= "foo\n" (proto/read-line r)))
       (is (= "bar\n" (proto/read-line r)))
       (is (= "baz" (proto/read-line r)))
       (is (nil? (proto/read-line r))))
     (with-open [r (make-file-reader "foo\nbar\nbaz\n")]
       (is (= "foo\n" (proto/read-line r)))
       (is (= "bar\n" (proto/read-line r)))
       (is (= "baz\n" (proto/read-line r)))
       (is (nil? (proto/read-line r))))))

(defn- make-line-buffering-reader [content]
  (reader/make-line-buffering-reader (reader/make-string-reader content)))

(deftest line-buffering-reader-test
  (testing "read-line"
    (let [r (make-line-buffering-reader "")]
      (is (nil? (reader/line r)))
      (is (= 0 (reader/line-num r)))
      (is (= 0 (reader/col-num r)))
      (is (nil? (reader/read-line r))))
    (let [r (make-line-buffering-reader "foo")]
      (is (= "foo" (reader/read-line r)))
      (is (= "foo" (reader/line r)))
      (is (= 0 (reader/line-num r)))
      (is (= 3 (reader/col-num r)))
      (is (nil? (reader/read-line r))))
    (let [r (make-line-buffering-reader "\n")]
      (is (= "\n" (reader/read-line r)))
      (is (= "\n" (reader/line r)))
      (is (= 0 (reader/line-num r)))
      (is (= 1 (reader/col-num r)))
      (is (nil? (reader/read-line r))))
    (let [r (make-line-buffering-reader "foo\n")]
      (is (= "foo\n" (reader/read-line r)))
      (is (= "foo\n" (reader/line r)))
      (is (= 0 (reader/line-num r)))
      (is (= 4 (reader/col-num r)))
      (is (nil? (reader/read-line r))))
    (let [r (make-line-buffering-reader "foo\nbar\nbaz")]
      (is (= "foo\n" (reader/read-line r)))
      (is (= "foo\n" (reader/line r)))
      (is (= 0 (reader/line-num r)))
      (is (= 4 (reader/col-num r)))
      (is (= "bar\n" (reader/read-line r)))
      (is (= "bar\n" (reader/line r)))
      (is (= 1 (reader/line-num r)))
      (is (= 4 (reader/col-num r)))
      (is (= "baz" (reader/read-line r)))
      (is (= "baz" (reader/line r)))
      (is (= 2 (reader/line-num r)))
      (is (= 3 (reader/col-num r)))
      (is (nil? (reader/read-line r)))))
  (testing "read-until"
    (let [r (make-line-buffering-reader "")]
      (is (nil? (reader/read-until r " ")))
      (is (nil? (reader/line r)))
      (is (= 0 (reader/line-num r)))
      (is (= 0 (reader/col-num r))))
    (let [r (make-line-buffering-reader "foo bar")]
      (is (= "foo" (reader/read-until r " ")))
      (is (= "foo bar" (reader/line r)))
      (is (= 0 (reader/line-num r)))
      (is (= 4 (reader/col-num r)))
      (is (nil? (reader/read-until r " ")))
      (is (= "foo bar" (reader/line r)))
      (is (= 0 (reader/line-num r)))
      (is (= 4 (reader/col-num r)))
      (is (= "bar" (reader/read-line r)))
      (is (= "foo bar" (reader/line r)))
      (is (= 0 (reader/line-num r)))
      (is (= 7 (reader/col-num r)))
      (is (nil? (reader/read-line r))))
    (let [r (make-line-buffering-reader "foo  foo\nbar  bar\nbaz")]
      (is (= "foo" (reader/read-until r "  ")))
      (is (= "foo  foo\n" (reader/line r)))
      (is (= 0 (reader/line-num r)))
      (is (= 5 (reader/col-num r)))
      (is (nil? (reader/read-until r "  ")))
      (is (= "foo  foo\n" (reader/line r)))
      (is (= "foo\n" (reader/read-line r)))
      (is (= "foo  foo\n" (reader/line r)))
      (is (= 0 (reader/line-num r)))
      (is (= 9 (reader/col-num r)))
      (is (= "bar" (reader/read-until r "  ")))
      (is (= "bar  bar\n" (reader/line r)))
      (is (= 1 (reader/line-num r)))
      (is (= 5 (reader/col-num r)))
      (is (nil? (reader/read-until r "  ")))
      (is (= "bar\n" (reader/read-line r)))
      (is (= "bar  bar\n" (reader/line r)))
      (is (= 1 (reader/line-num r)))
      (is (= 9 (reader/col-num r)))
      (is (nil? (reader/read-until r "  ")))
      (is (= "baz" (reader/line r)))
      (is (= 2 (reader/line-num r)))
      (is (= 0 (reader/col-num r)))
      (is (= "baz" (reader/read-line r)))
      (is (= "baz" (reader/line r)))
      (is (= 2 (reader/line-num r)))
      (is (= 3 (reader/col-num r)))
      (is (nil? (reader/read-until r "  ")))
      (is (nil? (reader/read-line r)))))
  (testing "read-char & unread-char"
    (let [r (make-line-buffering-reader "ab\nc")]
      (is (= \a (reader/read-char r)))
      (is (= "ab\n" (reader/line r)))
      (is (= 0 (reader/line-num r)))
      (is (= 1 (reader/col-num r)))
      (is (= \b (reader/read-char r)))
      (is (= "ab\n" (reader/line r)))
      (is (= 0 (reader/line-num r)))
      (is (= 2 (reader/col-num r)))
      (reader/unread-char r)
      (is (= "ab\n" (reader/line r)))
      (is (= 0 (reader/line-num r)))
      (is (= 1 (reader/col-num r)))
      (is (= "b\n" (reader/read-line r)))
      (is (= \c (reader/read-char r)))
      (is (= "c" (reader/line r)))
      (is (= 1 (reader/line-num r)))
      (is (= 1 (reader/col-num r)))))
  (testing "end?"
    (let [r (make-line-buffering-reader "foo bar\nbaz")]
      (is (not (reader/end? r)))
      (is (= "foo" (reader/read-until r " ")))
      (is (not (reader/end? r)))
      (is (= "bar\n" (reader/read-line r)))
      (is (not (reader/end? r)))
      (is (= "b" (reader/read-until r "a")))
      (is (not (reader/end? r)))
      (is (= \z (reader/read-char r)))
      (is (reader/end? r))))
  (testing "blank-trailing?"
    (let [r (make-line-buffering-reader "foo| \t\nbar| a\nbaz|")]
      (is (not (reader/blank-trailing? r)))
      (is (= "foo" (reader/read-until r "|")))
      (is (reader/blank-trailing? r))
      (reader/read-line r)
      (is (= "bar" (reader/read-until r "|")))
      (is (not (reader/blank-trailing? r)))
      (reader/read-line r)
      (is (= "baz" (reader/read-until r "|")))
      (is (reader/blank-trailing? r)))))
