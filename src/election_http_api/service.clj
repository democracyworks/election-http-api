(ns election-http-api.service
  (:require [io.pedestal.http :as bootstrap]
            [io.pedestal.http.route :as route]
            [io.pedestal.http.route.definition :refer [defroutes]]
            [io.pedestal.interceptor :refer [interceptor]]
            [ring.util.response :as ring-resp]
            [turbovote.resource-config :refer [config]]
            [pedestal-toolbox.params :refer :all]
            [pedestal-toolbox.cors :as cors]
            [pedestal-toolbox.content-negotiation :refer :all]
            [kehaar.core :as k]
            [clojure.core.async :refer [go alt! timeout]]
            [bifrost.core :as bifrost]
            [bifrost.interceptors :as bifrost.i]
            [election-http-api.channels :as channels]
            [clojure.tools.logging :as log]
            [clojure.string :as str]))

(def ping
  (interceptor
   {:enter
    (fn [ctx]
      (assoc ctx :response (ring-resp/response "OK")))}))

(defroutes routes
  [[["/"
     ^:interceptors [(body-params)
                     (negotiate-response-content-type ["application/edn"
                                                       "application/transit+json"
                                                       "application/transit+msgpack"
                                                       "application/json"
                                                       "text/plain"])]
     ["/ping" {:get [:ping ping]}]
     ["/upcoming" ^:constraints {:district-divisions #".+"}
      {:get [:search-upcoming-by-district-divisions
             (bifrost/interceptor
              channels/election-upcoming-search 60000)]}
      ^:interceptors [(bifrost.i/update-in-request
                       [:query-params :district-divisions]
                       #(-> %
                            (str/split #",")
                            vec))
                      (bifrost.i/update-in-response
                       [:body :elections] [:body] identity)]]
     ["/upcoming" ^:constraints {:user-id #".+"}
      {:get [:search-upcoming-by-user-id
             (bifrost/interceptor
              channels/electorate-search-create 60000)]}
      ^:interceptors [(bifrost.i/update-in-request
                       [:query-params :user-id]
                       #(when % (java.util.UUID/fromString %)))
                      (bifrost.i/update-in-response
                       [:body :electorates] [:body] identity)]]]]])

(defn service []
  (let [allowed-origins (config [:server :allowed-origins])]
    (log/debug "Allowed Origins Config: " (pr-str allowed-origins))
    {::env :prod
     ::bootstrap/router :linear-search
     ::bootstrap/routes routes
     ::bootstrap/resource-path "/public"
     ::bootstrap/allowed-origins (cors/domain-matcher-fn
                                  (map re-pattern allowed-origins))
     ::bootstrap/host (config [:server :hostname])
     ::bootstrap/type :immutant
     ::bootstrap/port (config [:server :port])}))
