(ns pogonos.protocols
  (:refer-clojure :exclude [read]))

(defprotocol IReader
  (read [this])
  (unread [this s])
  (end? [this]))

(defprotocol IRenderable
  (render [this ctx out]))
