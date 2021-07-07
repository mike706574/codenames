(ns codenames.frontend.game
  (:require [cljs.core.async :refer [chan <! >!]]
            [cljs-http.client :as http]
            [clojure.string :as str]
            [codenames.core.game :as game]
            [cognitect.transit :as transit]
            [reagent.core :as r]
            [taoensso.timbre :as log])
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
                      :assassin-word "MOON",
                      :actions [],
                      :turn "red",
                      :winner nil}))

(defn end-turn-button [{:keys [state end-turn!]}]
  (let [{:keys [turn]} state]
    [:button.cn-end-turn-button
     {:on-click (partial end-turn! turn)}
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

(defn bar [{:keys [state] :as props}]
  (let [{:keys [winner]} state]
    [:div.cn-bar
     [:div.cn-sore
      [score props]]
     [:div.cn-score
      [status props]]
     [:div
      (when-not winner [end-turn-button props])]]))

(defn classes [entries]
  (->> entries
       (filter (fn [entry]
                 (or (string? entry)
                     (= (count entry) 1)
                     (second entry))))
       (map (fn [entry]
              (if (string? entry)
                entry
                (first entry))))))

(defn tile [{:keys [word state select-word! spymaster?]}]
  (let [{:keys [winner turn]} state
        inactive-word? (game/inactive-word? state word)
        category (game/categorize-word state word)
        class (classes ["cn-tile"
                        (str "cn-tile-" category)
                        ["cn-tile-inactive" inactive-word?]
                        ["cn-tile-active" (not inactive-word?)]
                        ["cn-tile-selectable" (not (or inactive-word? winner spymaster?))]
                        ["cn-tile-spymaster" (or winner spymaster?)]])
        on-click (when-not (or winner inactive-word? spymaster?) (fn [] (select-word! turn word)))]
    [:div {:class class :on-click on-click} word]))

(defn board [{:keys [state select-word! spymaster?]}]
  [:div.cn-board
   (for [word (:words state)]
     ^{:key word}
     [tile {:word word :select-word! select-word! :state state :spymaster? spymaster?}])])

(defn view [{:keys [id state spymaster? set-spymaster! new-game!] :as props}]
  [:<>
   [:h1 id]
   (if-not state
     [:p "Loading..."]
     [:<>
      [bar props]
      [board props]
      [:button.cn-operative-button
       {:on-click #(set-spymaster! false)
        :class ["cn-operative-button" (str "cn-role-button-" (if spymaster? "disabled" "enabled"))]
        :disabled (not spymaster?)}
       "Operative"]
      [:button.cn-spymaster-button
       {:on-click #(set-spymaster! true)
        :class ["cn-spymaster-button" (str "cn-role-button-" (if spymaster? "enabled" "disabled"))]
        :disabled spymaster?}
       "Spymaster"]
      [:button.cn-new-game-button
       {:on-click new-game!}
       "New Game"]])])

(defn game-path [id] (str "/api/games/" id))

(defn decode [message]
  (transit/read (transit/reader :json) message))

(defn encode [message]
  (transit/write (transit/writer :json) message))

(defn on-message [state-atom event]
  (let [message (decode (.-data event))
        {:keys [state]} message]
    (case (:type message)
      "connected" (log/debug "Websocket connection established.")
      "state" (reset! state-atom state )
      (log/debug (str "Invalid message: " message)))))

(defn on-error [event]
  (log/debug (str "TODO: Websocket error:" event)))

(defn connect! [id state]
  (let [ch (chan)]
    (go (let [secure? (= (.-protocol (.-location js/document)) "https:")
              protocol (if secure? "wss" "ws")
              port (-> js/window .-location .-port)
              host (-> js/window .-location .-hostname )
              base (if (str/blank? port) host (str host ":" port))
              url (str protocol "://" base "/api/game-subscriptions/" id)]
          (log/debug (str "Establishing websocket connection to " url "."))
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
        spymaster-atom (r/atom false)
        set-spymaster! #(reset! spymaster-atom %)
        select-word! (fn [team word]
                       (go (let [response (<! (http/post
                                               (game-path id)
                                               {:json-params {:type "select-word"
                                                              :team team
                                                              :word word}}))]
                             (reset! state-atom (:body response)))))
        end-turn! (fn [team]
                    (go (let [response (<! (http/post
                                            (game-path id)
                                            {:json-params {:type "end-turn"
                                                           :team team}}))]
                          (reset! state-atom (:body response)))))
        new-game! (fn []
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
             :select-word! select-word!
             :end-turn! end-turn!
             :new-game! new-game!
             :spymaster? @spymaster-atom
             :set-spymaster! set-spymaster!}])))
