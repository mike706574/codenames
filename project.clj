(defproject codenames "1.0.0-SNAPSHOT"
  :description "Codenames in Clojure/Script"
  :url "http://mike-codenames.herokuapp.com"
  :license {:name "Eclipse Public License v1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.10.3"]
                 [aleph "0.4.7-alpha7"]
                 [cheshire "5.10.0"]
                 [compojure "1.6.2"]
                 [environ "1.2.0"]
                 [ring/ring-anti-forgery "1.0.1"]
                 [ring/ring-defaults "0.2.3"]
                 [ring/ring-json "0.5.1"]
                 [selmer "1.12.40"]
                 [com.cognitect/transit-clj "1.0.324"]
                 [com.taoensso/timbre "5.1.2"]]
  :min-lein-version "2.0.0"
  :plugins [[lein-shell "0.5.0"]]
  :uberjar-name "codenames.jar"
  :profiles {:uberjar {:env {:production true}
                       :prep-tasks ["frontend"]}}
  :aliases {"frontend" ["shell" "npx" "shadow-cljs" "release" "frontend"]})
