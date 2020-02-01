(ns pogonos.protocols
  (:refer-clojure :exclude [read]))

(defprotocol IReader
  (read [this])
  (unread [this s]))

(defprotocol IOutput
  (append [this x])
  (complete [this]))

(defprotocol IRenderable
  (render [this ctx out]))
