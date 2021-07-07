(ns codenames.core.game
  (:require [codenames.core.words :as words]))

(defn take-rand [n coll]
  (loop [taken []
         taken-count 0
         untaken (vec coll)
         untaken-count (count coll)]
    (if (= n (count taken))
      taken
      (let [idx (rand-int untaken-count)
            item (nth untaken idx)]
        (recur (conj taken item)
               (inc taken-count)
               (into (subvec untaken 0 idx) (subvec untaken (inc idx)))
               (dec untaken-count))))))

(defn rand-bool []
  (= 0 (rand-int 2)))

(def board-words (partial take-rand 25 words/words))

(def start-word-count 8)

;; board:
;;   {:words ["WORD"...],
;;    :blue-words ["BLUE"...],
;;    :red-words ["RED"...],
;;    :death-word "DEATH"
;;    :return red
;;    :actions []}

;; actions:
;;   {:type "move" :team "red" :word "WORD"}
;;   {:type "end-turn" :team "red"}
(defn initial-state []
  (let [words (board-words)
        [first-team-words remaining-words] (split-at start-word-count words)
        [second-team-words remaining-words] (split-at (dec start-word-count) remaining-words)
        death-word (first remaining-words)
        red-starts? (rand-bool)
        [turn red-words blue-words] (if red-starts?
                                      ["red" first-team-words second-team-words]
                                      ["blue" second-team-words first-team-words])]
    {:words (shuffle words)
     :blue-words (vec blue-words)
     :red-words (vec red-words)
     :death-word death-word
     :actions []
     :turn turn
     :winner nil}))

(defn add-error [state error]
  (assoc state :error error))

(defn clear-error [state]
  (dissoc state :error))

(defn add-action [state action]
  (update state :actions conj action))

(defn inactive-words [state]
  (->> state
       :actions
       (filter #(= "pick-word" (:type %)))
       (map :word)
       set))

(defn team-words [state team]
  (state (keyword (str team "-words"))))

(defn remaining-words [state team]
  (vec
   (remove
    (set (inactive-words state))
    (team-words state team))))

(defn other-team [team]
  (if (= team "red") "blue" "red"))

(defn swap-turn [state]
  (update state :turn other-team))

(defn declare-winner [state team]
  (assoc state :winner team :turn nil))

(defn check-winner [state team]
  (if (seq (remaining-words state team))
    state
    (declare-winner state team)))

(defn end-turn [state end-turn]
  (-> state
      clear-error
      swap-turn
      (add-action end-turn)))

(defn in? [coll item]
  (boolean (some #{item} coll)))

(defn categorize-word [state word]
  (let [{:keys [death-word red-words blue-words]} state]
    (cond
      (= death-word word) "death"
      (in? blue-words word) "blue"
      (in? red-words word) "red"
      :else "neutral")))

(defn inactive-word? [state word]
  (in? (inactive-words state) word))

(defn categorize-word-pick [state word]
  (let [{:keys [turn]} state
        category (categorize-word state word)
        inactive-words (inactive-words state)]
    (cond
      (in? inactive-words word) "invalid"
      (= category turn) "good"
      (= category (other-team turn)) "bad"
      :else category)))

(defn pick-word [state move]
  (let [{:keys [word team]} move
        new-state (add-action state move)]
    (case (categorize-word-pick state word)
      "invalid" (add-error new-state "word is invalid")
      "death" (declare-winner new-state (other-team team))
      "good" (check-winner new-state team)
      "bad" (-> new-state
                swap-turn
                (check-winner (other-team team)))
      "neutral" (swap-turn new-state))))

(defn advance-state [state action]
  (let [{:keys [turn]} state
        {:keys [type team]} action]
    (if-not (= turn team)
      (add-error state (str "it's not " team "'s turn"))
      (case type
        "end-turn" (end-turn state action)
        "pick-word" (pick-word state action)
        (add-error state (str "invalid action type: " type))))))

(comment
  (def e {:words
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
          :actions [{:type "pick-word" :team "red" :word "MAIL"}],
          :turn "red",
          :winner nil})

  (def s (atom e))

  (defn go [action] (swap! s advance-state action))

  @s
  (go {:type "end-turn" :team "blue"})
  (go {:type "pick-word" :team "red" :word "MOON"}))
