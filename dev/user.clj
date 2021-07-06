(ns user
  (:require [codenames.backend.main :as main]))

(defonce server (atom nil))

(defn restart []
  (swap!
   server
   (fn [s]
     (when s (.close s))
     (main/-main)))
  :restarted)
