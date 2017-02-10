(ns election-http-api.three-scale
  (:require [turbovote.resource-config :refer [config]]
            [clojure.tools.logging :as log])
  (:import (threescale.v3.api ParameterMap AuthorizeResponse ServerError)
           (threescale.v3.api.impl ServiceApiDriver)))

(def api-client (ServiceApiDriver/createApi))

(defn authorized? [^AuthorizeResponse three-scale-response]
  (.success three-scale-response))

(defn failure-reason [^AuthorizeResponse three-scale-response]
  (.getReason three-scale-response))

(defn authorize-request [{{:keys [user-key]} :query-params}]
  (log/debug "Authorizing API request for user-key" user-key)
  (let [service-token (config [:3scale :service-token])
        service-id (config [:3scale :service-id])
        usage (doto (ParameterMap.)
                (.add "hits" "1"))
        params (doto (ParameterMap.)
                 (.add "user_key" ^String user-key)
                 (.add "usage" usage))]
    (try
      (let [response (.authrep api-client service-token service-id params)]
        (if (authorized? response)
          {::status ::authorized
           ::auth-response response}
          {::status ::not-authorized
           ::message (failure-reason response)
           ::auth-response response}))
      (catch ServerError error
        {::status ::error
         ::message (.getMessage error)}))))
