(ns pogonos.protocols
  (:refer-clojure :exclude [read]))

(defprotocol IReader
  (read [this])
  (unread [this s]))

(defprotocol IRenderable
  (render [this ctx out]))
