(ns pogonos.nodes)

(defprotocol IVisibility
  (visible? [this]))

(extend-protocol IVisibility
  #?(:clj Object :cljs default)
  (visible? [thihs] true))

(defrecord Root [body])

(defrecord Variable [keys unescaped?])

(defrecord UnescapedVariable [keys])

(defrecord Section [keys nodes])

(defrecord SectionEnd [keys])

(defrecord Inverted [keys nodes])

(defrecord Partial [name indent])

(defrecord Comment [body]
  IVisibility
  (visible? [this] false))

(defrecord SetDelimiter [open close]
  IVisibility
  (visible? [this] false))
