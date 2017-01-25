(defproject election-http-api "0.1.0-SNAPSHOT"
  :description "HTTP API gateway for election data"
  :url "https://github.com/democracyworks/election-http-api"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/tools.logging "0.3.1"]
                 [turbovote.resource-config "0.2.1"]
                 [com.novemberain/langohr "3.7.0"]
                 [prismatic/schema "1.1.3"]
                 [ch.qos.logback/logback-classic "1.1.9"]

                 ;; core.async has to come before pedestal or kehaar.wire-up will
                 ;; not compile. Something to do with the try-catch in
                 ;; kehaar.core/go-handler. (This may not be true anymore in
                 ;; core.async 0.2.x; need to test.)
                 [org.clojure/core.async "0.2.395"]
                 <<<<<<< HEAD
                 [democracyworks/kehaar "0.9.0"]
                 =======
                 [democracyworks/kehaar "0.8.1"]
                 >>>>>>> Update deps

                 [io.pedestal/pedestal.service "0.5.2"]
                 [io.pedestal/pedestal.service-tools "0.5.2"]
                 [democracyworks/pedestal-toolbox "0.7.0"]
                 [org.immutant/web "2.1.6"]
                 [io.pedestal/pedestal.immutant "0.5.2"]
                 [org.immutant/core "2.1.6"]
                 [democracyworks/bifrost "0.1.5"]
                 [~(symbol "net.3scale" "3scale-api") "3.0.2"]] ; ewww, but symbols can't start w/ a 3
  :plugins [[lein-immutant "2.1.0"]
            [com.carouselapps/jar-copier "0.3.1"]]
  :java-agents [[com.newrelic.agent.java/newrelic-agent "3.35.1"]]
  :jar-copier {:java-agents true
               :destination "resources/jars"}
  :prep-tasks ["javac" "compile" "jar-copier"]
  :main ^:skip-aot election-http-api.server
  :uberjar-name "election-http-api.jar"
  :profiles {:uberjar {:aot :all}
             :dev {:resource-paths ["dev-resources"]}
             :test {:dependencies [[clj-http "3.4.1"]]
                    :jvm-opts ["-Dlog-level=OFF"]}})
