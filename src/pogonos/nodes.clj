(ns pogonos.nodes)

(defrecord Variable [keys unescaped?])

(defrecord Section [keys children])

(defrecord SectionEnd [keys])

(defrecord Inverted [keys children])

(defrecord Partial [name indent])
