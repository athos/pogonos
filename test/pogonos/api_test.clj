(ns pogonos.api-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is are testing]]
            [pogonos.api :as api]
            [clojure.string :as str]))

(defn test-file [path]
  (str (io/file (io/resource path))))

(deftest render-test
  (are [opts expected] (= expected (with-out-str (api/render opts)))
    {:string "Hello, {{name}}!" :data {:name "Clojurian"}}
    "Hello, Clojurian!"

    {:file (test-file "templates/hello.mustache")
     :data-file (test-file "data.edn")}
    "Hello, Clojurian!\n"

    {:resource "templates/hello.mustache" :data {:name "Clojurian"}}
    "Hello, Clojurian!\n")
  (is (= "Hello, Clojurian!"
         (with-out-str
           (with-in-str "Hello, {{name}}!"
             (api/render {:data {:name "Clojurian"}}))))))

(defn- with-stderr-lines [f]
  (-> (with-out-str
        (binding [*err* *out*]
          (f)))
      (str/split #"\n")))

(defn- join-paths [& paths]
  (str/join (System/getProperty "path.separator") paths))

(deftest check-test
  (testing "basic use cases"
    (is (nil? (api/check {:string "{{foo}}" :quiet true})))
    (is (thrown? Exception
                 (api/check {:string "{{foo" :quiet true :on-failure :throw})))
    (is (nil? (api/check {:file (test-file "templates/hello.mustache")
                          :quiet true})))
    (is (thrown? Exception
                 (api/check {:file (test-file "templates/broken.mustache")
                             :quiet true :on-failure :throw})))
    (is (nil? (api/check {:resource "templates/hello.mustache" :quiet true})))
    (is (thrown? Exception
                 (api/check {:resource "templates/broken.mustache"
                             :quiet true :on-failure :throw})))
    (is (nil? (with-in-str "{{foo}}" (api/check {:quiet true}))))
    (is (thrown? Exception
                 (with-in-str "{{foo"
                   (api/check {:quiet true :on-failure :throw})))))
  (testing "bulk check"
    (testing "files"
      (is (= [(str "Checking template " (test-file "templates/hello.mustache"))
              (str "Checking template " (test-file "templates/main.mustache"))]
             (with-stderr-lines
               #(api/check {:file [(test-file "templates/hello.mustache")
                                   (test-file "templates/main.mustache")]}))))
      (let [lines (with-stderr-lines
                    #(api/check {:file (join-paths
                                        (test-file "templates/hello.mustache")
                                        (test-file "templates/broken.mustache"))
                                 :on-failure nil
                                 :suppress-verbose-errors true}))]
        (is (= (count lines) 3))
        (is (= (str "Checking template " (test-file "templates/hello.mustache"))
               (nth lines 0)))
        (is (= (str "Checking template " (test-file "templates/broken.mustache"))
               (nth lines 1)))
        (is (str/starts-with? (nth lines 2) "[ERROR]"))))
    (testing "resources"
      (is (= ["Checking template templates/main.mustache"
              "Checking template templates/node.mustache"]
             (with-stderr-lines
               #(api/check {:resource ["templates/main.mustache"
                                       "templates/node.mustache"]}))))
      (let [lines (with-stderr-lines
                    #(api/check {:resource (join-paths "templates/broken.mustache"
                                                       "templates/hello.mustache")
                                 :on-failure nil
                                 :suppress-verbose-errors true}))]
        (is (= (count lines) 3))
        (is (= "Checking template templates/broken.mustache" (nth lines 0)))
        (is (str/starts-with? (nth lines 1) "[ERROR]"))
        (is (= "Checking template templates/hello.mustache" (nth lines 2)))))
    (testing "dirs"
      (let [lines (sort
                   (with-stderr-lines
                     #(api/check {:dir (test-file "templates")
                                  :on-failure nil
                                  :suppress-verbose-errors true})))]
        (is (= [(str "Checking template " (test-file "templates/broken.mustache"))
                (str "Checking template " (test-file "templates/demo.mustache"))
                (str "Checking template " (test-file "templates/hello.mustache"))
                (str "Checking template " (test-file "templates/main.mustache"))
                (str "Checking template " (test-file "templates/node.mustache"))]
               (butlast lines)))
        (is (str/starts-with? (last lines) "[ERROR]")))
      (let [lines (with-stderr-lines
                    #(api/check {:dir (test-file "templates")
                                 :only-show-errors true
                                 :on-failure nil
                                 :suppress-verbose-errors true}))]
        (is (= (count lines) 1))
        (is (str/starts-with? (first lines) "[ERROR]")))
      (let [lines (sort
                   (with-stderr-lines
                     #(api/check {:dir [(test-file "templates")]
                                  :include-regex ["broken.mustache$" "hello.mustache$"]
                                  :on-failure nil
                                  :suppress-verbose-errors true})))]
        (is (= [(str "Checking template " (test-file "templates/broken.mustache"))
                (str "Checking template " (test-file "templates/hello.mustache"))]
               (butlast lines)))
        (is (str/starts-with? (last lines) "[ERROR]")))
      (is (= [(str "Checking template " (test-file "templates/hello.mustache"))
              (str "Checking template " (test-file "templates/main.mustache"))]
             (sort
              (with-stderr-lines
                #(api/check {:dir (test-file "templates")
                             :exclude-regex "(broken|demo|node).mustache$"}))))))))
