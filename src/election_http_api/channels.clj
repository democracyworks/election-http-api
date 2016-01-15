(ns election-http-api.channels
  (:require [clojure.core.async :as async]))


(defonce ok-requests (async/chan))
(defonce ok-responses (async/chan))

(defonce election-upcoming-search (async/chan 1000))

(defonce electorate-search-create (async/chan 1000))

(defn close-all! []
  (doseq [c [ok-requests ok-responses
             election-upcoming-search
             electorate-search-create]]
    (async/close! c)))
