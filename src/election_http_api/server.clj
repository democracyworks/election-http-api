(ns election-http-api.server
    (:require [election-http-api.service :as service]
              [io.pedestal.http :as http]
              [election-http-api.channels :as channels]
              [election-http-api.queue :as queue]
              [turbovote.resource-config :refer [config]]
              [clojure.tools.logging :as log]
              [immutant.util :as immutant])
  (:gen-class))

(defn shutdown [rabbit-resources]
  (channels/close-all!)
  (queue/close-all! rabbit-resources))

(defn start-http-server [& [options]]
  (-> (service/service)
      (merge options)
      http/create-server
      http/start))

(def stop-http-server
  "This fn takes one argument: the service-map value returned by
  `start-http-server`."
  http/stop)

(defn -main [& args]
  (let [rabbit-resources (queue/initialize)]
    (start-http-server (config [:server]))
    (immutant/at-exit (partial shutdown rabbit-resources))))
