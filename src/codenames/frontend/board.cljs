(ns codenames.frontend.board
  (:require [cljs.core.async :refer [<!]]
            [cljs-http.client :as http]
            [codenames.frontend.game :as game]
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

(defn board [state]
  (let [rows (map-indexed (fn [idx row] [idx row]) (partition 5 (:words state)))]
    [:div.cn-board
     (for [word (:words state)]
       ^{:key word}
       [:div.cn-tile word])]))

(defn status [state]
  (let [{:keys [winner turn]} state]
    (if winner
      [:p (str winner " won")]
      [:p (str turn "'s turn")])))

(defn game [id]
  (let [state (r/atom nil)]
    (go (let [response (<! (http/get (str "/api/games/" id)))]
          (reset! state (:body response))))
    (fn []
      [:<>
       [:h1 id]
       (if (nil? @state)
         [:p "Loading..."]
         [:<>

          [board @state]])])))
