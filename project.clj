(defproject teamwall "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :min-lein-version "2.0.0"
  :dependencies [[cheshire "5.3.1"]
                 [clj-http "1.0.0"]
                 [clojurewerkz/scrypt "1.2.0"]
                 [com.novemberain/monger "2.0.0"]
                 [com.novemberain/pantomime "2.3.0"]
                 [compojure "1.2.1"]
                 [crate "0.2.5"]
                 [crypto-random "1.2.0"]
                 [org.clojure/clojure "1.6.0"]
                 [org.clojure/clojurescript "0.0-2234"]
                 [prismatic/dommy "1.0.0"]
                 [ring/ring-codec "1.0.0"]
                 [slingshot "0.12.1"]]
  :plugins [[lein-ring "0.8.13"]
            [lein-cljsbuild "1.0.3"]]
  :ring {:handler teamwall.handler/app}
  :hooks [leiningen.cljsbuild]
  :source-paths ["src/server"]
  :cljsbuild {
    :builds {
      :main {
        :source-paths ["src/client"]
        :compiler {:output-to "resources/public/js/cljs.js"
                   :output-dir "resources/public/js/"
                   :optimizations :simple
;;                    :optimizations :none
                   :source-map "resources/public/js/cljs.js.map"
                   :pretty-print true}
        :jar true}}}
  :profiles
  {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                        [ring/ring-codec "1.0.0"]
                        [ring-mock "0.1.5"]]}})
