(ns pogonos.nodes)

(defrecord Variable [keys unescaped?])

(defrecord Section [keys nodes])

(defrecord SectionEnd [keys])

(defrecord Inverted [keys nodes])

(defrecord Partial [name indent])
