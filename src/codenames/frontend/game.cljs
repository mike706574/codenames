(ns codenames.frontend.game
  (:require [cljs.core.async :refer [chan <!]]
            [cljs-http.client :as http]
            [clojure.string :as str]
            [codenames.core.game :as game]
            [cognitect.transit :as transit]
            [reagent.core :as r]
            [reagent.dom :as rd])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(comment
  (def example-state {:words
                      ["MAIL"
                       "DRAGON"
                       "MOUTH"
                       "POUND"
                       "TRAIN"
                       "SOCK"
                       "FAIR"
                       "MOON"
                       "HOOK"
                       "TOKYO"
                       "PALM"
                       "PARK"
                       "MICROSCOPE"
                       "RACKET"
                       "KING"
                       "SWITCH"
                       "SATELLITE"
                       "COLD"
                       "CIRCLE"
                       "DAY"
                       "WASHINGTON"
                       "WORM"
                       "SINK"
                       "ENGINE"
                       "AFRICA"],
                      :blue-words ["MAIL" "DRAGON" "MOUTH" "POUND" "TRAIN" "SOCK" "FAIR"],
                      :red-words
                      ["MAIL" "DRAGON" "MOUTH" "POUND" "TRAIN" "SOCK" "FAIR" "MOON"],
                      :death-word "MOON",
                      :actions [],
                      :turn "red",
                      :winner nil}))

(defn end-turn-button [{:keys [state end-turn]}]
  (let [{:keys [turn]} state]
    [:button.cn-end-turn-button
     {:on-click (partial end-turn turn)}
     "End turn"]))

(defn score [{:keys [state]}]
  (let [remaining-blue-words (game/remaining-words state "blue")
        remaining-red-words (game/remaining-words state "red")]
    [:span.cn-score
     [:span.cn-red-text (count remaining-red-words)]
     " - "
     [:span.cn-blue-text (count remaining-blue-words)]]))

(defn status [{:keys [state]}]
  (let [{:keys [winner turn]} state]
    [:div.cn-status
     (if winner
       [:span {:class (str "cn-" winner "-text")} (str winner " won")]
       [:span {:class (str "cn-" turn "-text")} (str turn "'s turn")])]))

(defn bar [{:keys [state end-turn] :as props}]
  (let [{:keys [winner]} state]
    [:div.cn-bar
     [:div.cn-sore
      [score props]]
     [:div.cn-score
      [status props]]
     [:div
      (when-not winner [end-turn-button props])]]))

(defn tile [{:keys [word pick-word state master?]}]
  (let [{:keys [winner turn]} state
        inactive-word? (game/inactive-word? state word)
        category (game/categorize-word state word)
        potential-class [["cn-tile" true]
                         [(str "cn-tile-" category) true]
                         ["cn-tile-picked" inactive-word?]
                         ["cn-tile-unpicked" (not inactive-word?)]
                         ["cn-tile-pick" (not (or inactive-word? winner master?))]
                         ["cn-tile-master" (or winner master?)]]
        class (map first (filter second potential-class))
        on-click (when-not (or winner inactive-word? master?) (fn [] (pick-word turn word)))]
    [:div {:class class :on-click on-click} word]))

(defn board [{:keys [state pick-word master?]}]
  (let [{:keys [words turn]} state]
    [:div.cn-board
     (for [word (:words state)]
       ^{:key word}
       [tile {:word word :pick-word pick-word :state state :master? master?}])]))

(defn view [{:keys [id state end-turn master? toggle-master new-game] :as props}]
  [:<>
   [:h1 id]
   (if-not state
     [:p "Loading..."]
     [:<>
      [bar props]
      [board props]
      [:button.cn-toggle-master-button
       {:on-click toggle-master}
       (if master? "Guesser" "Clue Giver")]
      [:button.cn-new-game-button
       {:on-click new-game}
       "New Game"]])])

(defn game-path [id] (str "/api/games/" id))

(defn decode [message]
  (transit/read (transit/reader :json) message))

(defn encode [message]
  (transit/write (transit/writer :json) message))

(defn on-message [state-atom event]
  (let [message (decode (.-data event))
        {:keys [type state]} message]
    (case (:type message)
      "connected" (println "Websocket connectin established.")
      "state" (reset! state-atom state )
      (println (str "Invalid message: " message)))))

(defn on-error [event]
  (println (str "TODO: Websocket error:" event)))

(defn connect! [id state]
  (let [ch (chan)]
    (go (let [secure? (= (.-protocol (.-location js/document)) "https:")
              protocol (if secure? "wss" "ws")
              port (-> js/window .-location .-port)
              host (-> js/window .-location .-hostname )
              base (if (str/blank? port) host (str host ":" port))
              url (str protocol "://" base "/api/game-subscriptions/" id)]
          (println (str "Establishing websocket connection to " url "."))
          (let [socket (js/WebSocket. url)]
            (set! (.-onopen socket)
                  (fn on-open
                    [_]
                    (set! (.-onmessage socket) (partial on-message state))
                    (set! (.-onerror socket) on-error)
                    (go (>! ch {:ok? true :websocket socket})))))))
    ch))

(defn root [id]
  (let [state-atom (r/atom nil)
        master-atom (r/atom false)
        toggle-master #(swap! master-atom not)
        pick-word (fn [team word]
                    (go (let [response (<! (http/post
                                           (game-path id)
                                           {:json-params {:type "pick-word"
                                                          :team team
                                                          :word word}}))]
                          (reset! state-atom (:body response)))))
        end-turn (fn [team]
                   (go (let [response (<! (http/post
                                           (game-path id)
                                           {:json-params {:type "end-turn"
                                                          :team team}}))]
                         (reset! state-atom (:body response)))))
        new-game (fn []
                   (go (let [response (<! (http/post
                                           (game-path id)
                                           {:json-params {:type "new-game"}}))]
                         (reset! state-atom (:body response)))))]
    (go (let [response (<! (http/post
                            (game-path id)
                            {:json-params {:type "get-game"}}))]
          (reset! state-atom (:body response))))
    (connect! id state-atom)
    (fn []
      [view {:id id
             :state @state-atom
             :pick-word pick-word
             :end-turn end-turn
             :new-game new-game
             :master? @master-atom
             :toggle-master toggle-master}])))
