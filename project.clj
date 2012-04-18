(defproject trabajo "0.1.0-SNAPSHOT"
  :description "Trabajo is a Redis-backed library for creating background jobs with Clojure."
  :url "https://github.com/jeremyheiler/trabajo"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.3.0"]
                 [org.clojars.tavisrudd/redis-clojure "1.3.1"]]
  :plugins [[lein-swank "1.4.4"]
            [lein-tarsier "0.9.1"]])
