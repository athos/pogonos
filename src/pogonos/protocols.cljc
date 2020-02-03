(ns pogonos.protocols
  (:refer-clojure :exclude [read-line resolve]))

(defprotocol IReader
  (read-line [this]))

(defprotocol IOutput
  (append [this x])
  (complete [this]))

(defprotocol IRenderable
  (render [this ctx out]))

(defprotocol IStringifiable
  (stringify [this out]))

(defprotocol IPartialsResolver
  (resolve [this name]))
