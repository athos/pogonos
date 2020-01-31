(ns pogonos.nodes)

(defprotocol Invisible)

(defrecord Variable [keys unescaped?])

(defrecord Section [keys nodes])

(defrecord SectionEnd [keys])

(defrecord Inverted [keys nodes])

(defrecord Partial [name indent])

(defrecord Comment [body]
  Invisible)

(defrecord SetDelimiter [open close]
  Invisible)
