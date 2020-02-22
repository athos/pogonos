(ns pogonos.partials-test
  (:require [clojure.test :refer [deftest is]]
            [pogonos.partials :as partials]
            [pogonos.protocols :as proto]
            [pogonos.reader :as reader]))

#?(:clj
   (deftest file-resolver-test
     (let [resolver (partials/file-partials)
           r (partials/resolve resolver (keyword nil "test-resources/templates/main"))]
       (is (= "{{>node}}" (proto/read-line r))))
     (let [resolver (partials/file-partials "test-resources/templates")
           r (partials/resolve resolver :main)]
       (is (= "{{>node}}" (proto/read-line r))))))

#?(:clj
   (deftest resource-resolver-test
     (let [resolver (partials/resource-partials)
           r (partials/resolve resolver (keyword nil "templates/main"))]
       (is (= "{{>node}}" (proto/read-line r))))
     (let [resolver (partials/resource-partials "templates")
           r (partials/resolve resolver :main)]
       (is (= "{{>node}}" (proto/read-line r))))))

(deftest map-resolver-test
  (let [resolver (partials/->resolver {:x "foo" :y (reader/make-string-reader "bar")})
        [rx ry] (map (partial partials/resolve resolver) [:x :y])]
    (is (= "foo" (proto/read-line rx)))
    (is (= "bar" (proto/read-line ry)))
    (is (nil? (partials/resolve resolver :z)))))

(deftest fn-resolver-test
  (let [resolver (partials/->resolver
                  (fn [k]
                    (case k
                      :x "foo"
                      :y (reader/make-string-reader "bar")
                      nil)))
        [rx ry] (map (partial partials/resolve resolver) [:x :y])]
    (is (= "foo" (proto/read-line rx)))
    (is (= "bar" (proto/read-line ry)))
    (is (nil? (partials/resolve resolver :z)))))

(deftest with-caching-disabled-test
  (let [resolver (partials/->resolver {:x "foo"})
        resolver' (partials/with-caching-disabled resolver)]
    (is (= (proto/read-line (partials/resolve resolver :x))
           (proto/read-line (partials/resolve resolver' :x))))
    (is (true? (partials/cacheable? resolver :x)))
    (is (false? (partials/cacheable? resolver' :x)))))

(deftest compotise-resolver-test
  (let [resolver (partials/compose {:x "foo"} (fn [k] (when (= k :y) "bar")))
        [rx ry] (map (partial partials/resolve resolver) [:x :y])]
    (is (= "foo" (proto/read-line rx)))
    (is (= "bar" (proto/read-line ry)))
    (is (nil? (partials/resolve resolver :z))))
  (let [resolver (partials/compose
                  {:x "foo"}
                  (partials/with-caching-disabled {:y "bar"}))]
    (is (= "foo" (proto/read-line (partials/resolve resolver :x))))
    (is (= "bar" (proto/read-line (partials/resolve resolver :y))))
    (is (true? (partials/cacheable? resolver :x)))
    (is (false? (partials/cacheable? resolver :y)))))
