(ns election-http-api.three-scale
  (:require [turbovote.resource-config :refer [config]]
            [clojure.tools.logging :as log])
  (:import (threescale.v3.api ParameterMap AuthorizeResponse ServerError)
           (threescale.v3.api.impl ServiceApiDriver)))

(defn authorized? [^AuthorizeResponse three-scale-response]
  (.success three-scale-response))

(defn failure-reason [^AuthorizeResponse three-scale-response]
  (.getReason three-scale-response))

(defn authorize-request [{{:keys [user-key]} :query-params}]
  (log/debug "Authorizing API request for user-key" user-key)
  (let [provider-key (config [:3scale :provider-key])
        service-id (config [:3scale :service-id])
        service-api (ServiceApiDriver. provider-key)
        usage (doto (ParameterMap.)
                (.add "hits" "1"))
        params (doto (ParameterMap.)
                 (.add "user_key" ^String user-key)
                 (.add "service_id" ^String service-id)
                 (.add "usage" usage))]
    (try
      (let [response (.authrep service-api params)]
        (if (authorized? response)
          {::status ::authorized
           ::auth-response response}
          {::status ::not-authorized
           ::message (failure-reason response)
           ::auth-response response}))
      (catch ServerError error
        {::status ::error
         ::message (.getMessage error)}))))
