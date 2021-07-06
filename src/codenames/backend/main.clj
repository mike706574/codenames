(ns codenames.backend.main
  (:require [aleph.http :as http]
            [environ.core :refer [env]]
            [codenames.backend.handler :as handler]))

(defn -main [& [port]]
  (let [port (Integer. (or port (env :port) 5000))]
    (http/start-server handler/handler {:port port})))
