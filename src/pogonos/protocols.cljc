(ns pogonos.protocols
  (:refer-clojure :exclude [read-line resolve]))

(defprotocol IReader
  (read-line [this])
  (end? [this])
  (close [this]))

(defprotocol ToReader
  (->reader [this]))

(defprotocol IContext
  (lookup [this keys])
  (push [this val]))

(defprotocol IRenderable
  (render [this ctx out]))

(defprotocol IStringifiable
  (stringify [this out]))

(defprotocol IPartialsResolver
  (resolve [this name])
  (cacheable? [this name]))

(defprotocol ToPartialsResolver
  (->resolver [this]))
