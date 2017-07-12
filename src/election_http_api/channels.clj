(ns election-http-api.channels
  (:require [clojure.core.async :as async]))

(defonce election-upcoming-search (async/chan 1000))
(defonce election-all-upcoming (async/chan 1000))
(defonce election-all (async/chan 1000))
(defonce electorate-search-create (async/chan 1000))

(defn close-all! []
  (doseq [c [election-upcoming-search
             election-all-upcoming
             election-all
             electorate-search-create]]
    (async/close! c)))
