(ns pogonos.nodes)

(defprotocol Invisible)

(defrecord Root [body])

(defrecord Variable [keys unescaped?])

(defrecord UnescapedVariable [keys])

(defrecord Section [keys nodes])

(defrecord SectionEnd [keys])

(defrecord Inverted [keys nodes])

(defrecord Partial [name indent])

(defrecord Comment [body]
  Invisible)

(defrecord SetDelimiter [open close]
  Invisible)
