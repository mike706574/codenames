(ns user
  (:require [clojure.tools.namespace.repl :as repl]
            [codenames.backend.main :as main]
            [taoensso.timbre :as log]))

(repl/disable-reload!)

(repl/set-refresh-dirs "src")

(defonce system nil)

(defn init []
  :init)

(defn start []
  (try
    (alter-var-root #'system (fn [s] (main/-main)))
    :started
    (catch Exception ex
      (log/error (or (.getCause ex) ex) "Failed to start system.")
      :failed)))

(defn stop []
  (alter-var-root #'system (fn [s] (when s (.close s))))
  :stopped)

(defn go []
  (init)
  (start)
  :ready)

(defn reset []
  (stop)
  (repl/refresh :after `go))
