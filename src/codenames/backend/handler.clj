(ns codenames.backend.handler
  (:require [aleph.http :as http]
            [codenames.backend.message :as msg]
            [codenames.core.game :as game]
            [compojure.core :refer [defroutes GET POST ANY]]
            [compojure.route :as route]
            [cheshire.core :as json]
            [clojure.string :as str]
            [manifold.bus :as bus]
            [manifold.deferred :as d]
            [manifold.stream :as s]
            [manifold.time :as t]
            [selmer.parser :as selmer]
            [ring.util.response :as resp]
            [ring.middleware.defaults :refer [wrap-defaults
                                              site-defaults
                                              api-defaults]]
            [ring.middleware.json :refer [wrap-json-body]]
            [ring.middleware.resource :refer [wrap-resource]]
            [taoensso.timbre :as log]))

(defn wrap-logging
  [handler]
  (fn [{:keys [uri method] :as request}]
    (let [label (str method " \"" uri "\"")]
      (try
        (log/debug label)
        (let [{:keys [status] :as response} (handler request)]
          (log/debug (str label " -> " status))
          response)
        (catch Exception e
          (log/error e label)
          {:status 500
           :body (selmer/render-file "templates/error.html" {})})))))

(def game-store (atom {}))
(def game-bus (bus/event-bus))
(def conn-counter (atom 0))
(def conn-store (atom {}))

(defn json-response [body]
  (-> body
      json/generate-string
      resp/response
      (resp/content-type "application/json")))

(defroutes site-routes
  (GET "/" []
       (selmer/render-file "templates/index.html" {}))

  (GET "/:id" []
       (selmer/render-file "templates/game.html" {}))

  (GET "/error" []
       (throw (ex-info "A test error." {})))

  (ANY "*" []
       (route/not-found (selmer/render-file "templates/not-found.html" {}))))

(def site-handler (-> #'site-routes
                      (wrap-resource "public")
                      (wrap-defaults site-defaults)))

(defn rswap! [atom f]
  (let [new-val (swap! atom (fn [val]
                              (let [[new-val ret] (f val)]
                                (with-meta new-val {:ret ret}))))]
    [new-val (:ret (meta new-val))]))


(defn game-resp [game]
  (json-response game))

(defn game-not-found-resp [id]
  (-> (json-response {:message (str "game \"" id "\" not found" )})
      (resp/status 404)))

(defn manage-game [games id action]
  (let [{:keys [type]} action
        existing-game (get games id)]
    (case type
      ;; start a new game
      "new-game" (let [new-game (game/initial-state)]
                   [(assoc games id new-game) new-game])

      ;; get game, starting it if necesary
      "get-game" (if existing-game
                   [games existing-game]
                   (let [new-game (game/initial-state)]
                     [(assoc games id new-game) new-game]))

      ;; apply action
      (if existing-game
        (let [updated-game (game/advance-state existing-game action)]
          [(assoc games id updated-game) updated-game])
        [games nil]))))

(def non-websocket-response
  (-> "Expected a websocket request."
      resp/response
      (resp/status 400)
      (resp/content-type "text/plain")))

(defn add-conn! [conn game-id]
  (let [conn-id (swap! conn-counter inc)]
    (swap! conn-store assoc conn-id {:id conn-id
                                     :game-id game-id
                                     :conn conn})
    conn-id))

(defn close-conn! [conn-id]
  (swap! conn-store (fn [conns]
                      (s/close! (:conn (conns conn-id)))
                      (dissoc conns conn-id))))

(defn handle-subscription [game-id req]
  (d/let-flow
   [conn (d/catch
             (http/websocket-connection req {:heartbeats {:send-after-idle 100}})
             (constantly nil))]
   (if-not conn
     non-websocket-response
     (let [conn-id (add-conn! conn game-id)
           conn-label (str "[ws-conn-" conn-id "] ")]
       (s/connect-via
        (bus/subscribe game-bus game-id)
        (fn [message]
          (s/put! conn (msg/encode message)))
        conn)
       (s/put! conn (msg/encode {:type "connected"}))
       {:status 101}))))

(defroutes api-routes
  (GET "/api/games/:id" [id]
       (if-let [game (get @game-store id)]
         (game-resp game)
         (game-not-found-resp id)))

  (POST "/api/games/:id" [id :as {action :body}]
        (let [[_ game] (rswap!
                        game-store
                        (fn [games] (manage-game games id action)))]
          (bus/publish! game-bus id {:type "state" :state game})
          (if game
            (game-resp game)
            (game-not-found-resp id))))

  (GET "/api/game-subscriptions/:id" [id :as req]
       (handle-subscription id req))

  (ANY "*" []
       (route/not-found (-> (json-response {:message "Not found."})
                            (resp/status 404)))))

(def api-handler (-> #'api-routes
                     (wrap-json-body {:keywords? true})
                     (wrap-defaults api-defaults)
                     wrap-logging))

(defn handler [{uri :uri :as request}]
  (if (str/starts-with? uri "/api")
    (api-handler request)
    (site-handler request)))
