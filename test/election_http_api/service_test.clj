(ns election-http-api.service-test
  (:require [election-http-api.server :as server]
            [election-http-api.channels :as channels]
            [clj-http.client :as http]
            [clojure.edn :as edn]
            [cognitect.transit :as transit]
            [clojure.core.async :as async]
            [clojure.test :refer :all]
            [clojure.string :as str]
            [clj-time.core :as t]
            [clj-time.coerce :as tc]))

(def test-server-port 56000) ; FIXME: Pick a port unique to this project

(defn start-test-server [run-tests]
  (let [service (server/start-http-server
                 {:io.pedestal.http/port test-server-port})]
    (try
      (run-tests)
      (finally (server/stop-http-server service)))))

(use-fixtures :once start-test-server)

(def root-url (str "http://localhost:" test-server-port))

(deftest ping-test
  (testing "ping responds with 'OK'"
    (let [response (http/get (str root-url "/ping")
                             {:headers {:accept "text/plain"}})]
      (is (= 200 (:status response)))
      (is (= "OK" (:body response))))))

(defn test-election [district-ocd-id date]
  {:id (java.util.UUID/randomUUID)
   :date date
   :district-divisions #{district-ocd-id}})

(def denver-ocd-id "ocd-division/country:us/state:co/county:denver")

(def denver-election (test-election denver-ocd-id
                                    (-> (t/today)
                                        (t/plus (t/days 30))
                                        tc/to-date)))

(defn test-election-works-upcoming-response
  [[response-ch {:keys [district-divisions] :as request}]]
  (condp #(some (set %2) #{%1}) district-divisions
    denver-ocd-id (async/put! response-ch {:status :ok
                                           :elections #{denver-election}})
    (async/put! response-ch {:status :ok, :elections #{}})))

(deftest upcoming-test
  (testing "/upcoming responds with elections when found"
    (async/take! channels/election-upcoming-search
                 test-election-works-upcoming-response)
    (let [co-ocd-id     "ocd-division/country:us/state:co"
          response (http/get
                    (str root-url "/upcoming")
                    {:query-params
                     {:district-divisions
                      (str/join "," #{co-ocd-id denver-ocd-id})}})]
      (is (= 200 (:status response)))
      (is (= #{denver-election}
             (edn/read-string (:body response)))))
    (testing "/upcoming with no elections returns empty success result"
      (async/take! channels/election-upcoming-search
                   test-election-works-upcoming-response)
      (let [response (http/get
                      (str root-url "/upcoming"))]
        (is (= 200 (:status response)))
        (is (empty? (edn/read-string (:body response))))))))
