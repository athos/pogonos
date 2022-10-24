(ns pogonos.nodes)

(defprotocol IVisibility
  (visible? [this]))

(extend-protocol IVisibility
  #?(:clj Object :cljs default)
  (visible? [_] true))

(defrecord Root [body])

(defrecord Variable [keys unescaped?])

(defrecord UnescapedVariable [keys])

(defrecord Section [keys nodes])

(defrecord SectionEnd [keys])

(defrecord Inverted [keys nodes])

(defrecord Partial [name indent])

(defrecord Comment [body]
  IVisibility
  (visible? [_] false))

(defrecord SetDelimiter [open close]
  IVisibility
  (visible? [_] false))
