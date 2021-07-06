(ns codenames.frontend.app
  (:require [cljs.core.async :refer [<!]]
            [cljs-http.client :as http]
            [codenames.frontend.game :as game]
            [reagent.core :as r]
            [reagent.dom :as rd])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn app []
  (let [id (subs (-> js/window .-location .-pathname) 1)]
    [:div.container
     [:div.row
      [:div.column
       {:style {"marginTop" "1em"}}
       [game/root id]]]]))

(defn init []
  (rd/render [app] (.getElementById js/document "root")))
