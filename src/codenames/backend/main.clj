(ns codenames.backend.main
  (:require [aleph.http :as http]
            [codenames.backend.handler :as handler]
            [environ.core :refer [env]]
            [taoensso.timbre :as log]))

(defn -main [& [port]]
  (let [port (Integer. (or port (env :port) 5000))]
    (log/info "Starting server." {:port port})
    (http/start-server handler/handler {:port port})))
