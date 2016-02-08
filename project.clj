(defproject election-http-api "0.1.0-SNAPSHOT"
  :description "HTTP API gateway for election data"
  :url "https://github.com/democracyworks/election-http-api"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/tools.logging "0.3.1"]
                 [turbovote.resource-config "0.2.0"]
                 [com.novemberain/langohr "3.4.2"]
                 [prismatic/schema "1.0.4"]
                 [ch.qos.logback/logback-classic "1.1.3"]

                 ;; core.async has to come before pedestal or kehaar.wire-up will
                 ;; not compile. Something to do with the try-catch in
                 ;; kehaar.core/go-handler. (This may not be true anymore in
                 ;; core.async 0.2.x; need to test.)
                 [org.clojure/core.async "0.2.374"]
                 [democracyworks/kehaar "0.5.0"]

                 [io.pedestal/pedestal.service "0.4.1"]
                 [io.pedestal/pedestal.service-tools "0.4.1"]
                 [democracyworks/pedestal-toolbox "0.7.0"]
                 [org.immutant/web "2.1.1"]
                 [io.pedestal/pedestal.immutant "0.4.1"]
                 [org.immutant/core "2.1.1"]
                 [democracyworks/bifrost "0.1.4"]]
  :plugins [[lein-immutant "2.1.0"]]
  :main ^:skip-aot election-http-api.server
  :target-path "target/%s"
  :uberjar-name "election-http-api.jar"
  :profiles {:uberjar {:aot :all}
             :dev {:resource-paths ["dev-resources"]}
             :test {:dependencies [[clj-http "2.0.0"]]
                    :jvm-opts ["-Dlog-level=INFO"]}})
