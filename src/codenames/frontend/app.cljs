(ns codenames.frontend.app
  (:require [codenames.frontend.game :as game]
            [reagent.dom :as rd]))

(defn app []
  (let [id (subs (-> js/window .-location .-pathname) 1)]
    [:div.container
     [:div.row
      [:div.column
       {:style {"marginTop" "1em"}}
       [game/root id]]]]))

(defn init []
  (rd/render [app] (.getElementById js/document "root")))
