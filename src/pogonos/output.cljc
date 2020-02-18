(ns pogonos.output
  (:require [clojure.string :as str]
            #?(:clj [clojure.java.io :as io])))

(defn stdout-output []
  (fn
    ([] (flush))
    ([x]
     (print x)
     (when (str/ends-with? x "\n")
       (flush)))))

(defn to-stdout []
  stdout-output)

(defn string-output []
  #?(:clj
     (let [sb (StringBuilder.)]
       (fn
         ([] (.toString sb))
         ([x] (.append sb x))))
     :cljs
     (let [strs (volatile! [])]
       (fn
         ([] (apply str @strs))
         ([x]
          (vswap! strs conj x))))))

(defn to-string []
  string-output)

#?(:clj
   (defn file-output [file]
     (let [w (io/writer file)]
       (fn
         ([] (.close w))
         ([^String x]
          (.write w x))))))

#?(:clj
   (defn to-file [file]
     (fn []
       (file-output file))))

#?(:clj
   (defn writer-output [w]
     (let [w (io/writer w)]
       (fn
         ([] (.flush w))
         ([^String x]
          (.write w x))))))

#?(:clj
   (defn to-writer [w]
     (fn []
       (writer-output w))))
