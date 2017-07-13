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
            [clojure.string :as str]
            [election-http-api.three-scale :as ts])
  (:import (java.util UUID)))

(def ping
  (interceptor
   {:enter
    (fn [ctx]
      (assoc ctx :response (ring-resp/response "OK")))}))

(def strip-user-key
  (interceptor
   {:enter
    (fn [ctx]
      (update-in ctx [:request :query-params] dissoc "user-key"))}))

(defn promote-to-body
  "Returns a leave interceptor that pulls `response-key` out of the response
  body and promotes its value as the new response body for successful responses
  (i.e. status is in the 200's). It leaves other responses alone.

  For example:
  (promote-to-body :foo) will return an interceptor that converts this
  response: {:foo {:other \"stuff\"}}
  to this response: {:other \"stuff\"}"
  [response-key]
  (interceptor
   {:leave
    (fn [ctx]
      (let [body (get-in ctx [:response :body])]
        (if (and (<= 200 (get-in ctx [:response :status]) 299)
                 (map? body)
                 (contains? body response-key))
          (assoc-in ctx [:response :body]
                    (get-in ctx [:response :body response-key]))
          ctx)))}))

(defn assoc-response [ctx status body]
  (assoc ctx :response {:status status
                        :headers {}
                        :body body}))

(def authorize-api-request
  (interceptor
   {:enter
    (fn [{:keys [request] :as ctx}]
      (let [authorization (ts/authorize-request request)]
        (log/debug "3scale API authorization response:" (pr-str authorization))
        (case (::ts/status authorization)
          ::ts/authorized ctx
          ::ts/not-authorized (assoc-response ctx 403
                                              {:message
                                               (::ts/message authorization)})
          ::ts/error (assoc-response ctx 500
                                     {:message (::ts/message authorization)})
          (assoc-response ctx 500 {:message "Unknown auth error"}))))}))

(defroutes routes
  [[["/" {:get [:all-elections
                (bifrost/interceptor
                 channels/election-all 60000)]}
     ^:interceptors [(body-params)
                     (negotiate-response-content-type ["application/edn"
                                                       "application/transit+json"
                                                       "application/transit+msgpack"
                                                       "application/json"
                                                       "text/plain"])
                     (promote-to-body :elections)]
     ["/ping" {:get [:ping ping]}]
     ["/upcoming" ^:constraints {:district-divisions #".+"}
      {:get [:search-upcoming-by-district-divisions
             (bifrost/interceptor
              channels/election-upcoming-search 60000)]}
      ^:interceptors [(bifrost.i/update-in-request
                       [:query-params :district-divisions]
                       #(-> %
                            (str/split #",")
                            vec))]]
     ["/upcoming" ^:constraints {:user-id #".+"}
      {:get [:search-upcoming-by-user-id
             (bifrost/interceptor
              channels/electorate-search-create 60000)]}
      ^:interceptors [(bifrost.i/update-in-request
                       [:query-params :user-id]
                       #(when % (UUID/fromString %)))
                      (bifrost.i/update-in-response
                       [:body :electorates]
                       [:body :elections]
                       identity)]]
     ["/upcoming"
      {:get [:all-upcoming
             (bifrost/interceptor
              channels/election-all-upcoming 60000)]}
      ^:interceptors [authorize-api-request
                      strip-user-key]]]]])

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
