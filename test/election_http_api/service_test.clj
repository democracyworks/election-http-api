(ns election-http-api.service-test
  (:require [election-http-api.server :as server]
            [election-http-api.channels :as channels]
            [election-http-api.three-scale :as ts]
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

(def co-ocd-id "ocd-division/country:us/state:co")

(def chaffee-ocd-id (str co-ocd-id "/county:chaffee"))

(def chaffee-election (test-election chaffee-ocd-id
                                     (-> (t/today)
                                         (t/minus (t/days 30))
                                         tc/to-date)))

(def denver-ocd-id (str co-ocd-id "/county:denver"))

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

(defn test-election-works-all-upcoming-response
  [[response-ch _]]
  (async/put! response-ch {:status :ok, :elections #{denver-election}}))

(defn test-election-works-all-response
  [[response-ch _]]
  (async/put! response-ch {:status :ok
                           :elections #{chaffee-election
                                        denver-election}}))

(def test-user-1
  {:id #uuid "a9acf0fb-5e74-4afb-822a-0078aec27e37"})

(defn test-electorate-works-upcoming-response
  [[response-ch {:keys [user-id] :as request}]]
  (condp = user-id
    (:id test-user-1)
    (async/put! response-ch {:status :ok
                             :electorates
                             #{{:election denver-election
                                :user test-user-1
                                :election-authority {}
                                :voter-registration-authority {}}}})
    (async/put! response-ch {:status :ok
                             :electorates #{}})))

(deftest upcoming-test
  (testing "/upcoming?district-divisions=... responds with elections when found"
    (async/take! channels/election-upcoming-search
                 test-election-works-upcoming-response)
    (let [response (http/get
                    (str root-url "/upcoming")
                    {:query-params
                     {:district-divisions
                      (str/join "," #{co-ocd-id denver-ocd-id})}})]
      (is (= 200 (:status response)))
      (is (= #{denver-election}
             (edn/read-string (:body response))))))
  (testing "/upcoming?district-divisions=... with no elections returns empty success result"
    (async/take! channels/election-upcoming-search
                 test-election-works-upcoming-response)
    (let [response (http/get
                    (str root-url "/upcoming")
                    {:query-params
                     {:district-divisions co-ocd-id}})]
      (is (= 200 (:status response)))
      (is (empty? (edn/read-string (:body response))))))
  (testing "/upcoming?user-id=... responds with elections when found"
    (async/take! channels/electorate-search-create
                 test-electorate-works-upcoming-response)
    (let [response (http/get
                    (str root-url "/upcoming")
                    {:query-params
                     {:user-id (:id test-user-1)}})]
      (is (= 200 (:status response)))
      (is (= #{{:election denver-election
                :user test-user-1
                :election-authority {}
                :voter-registration-authority {}}}
             (edn/read-string (:body response))))))
  (testing "/upcoming with no query params returns 403"
    (let [response (http/get (str root-url "/upcoming")
                             {:throw-exceptions false})]
      (is (= 403 (:status response)))))
  (testing "/upcoming with valid user-key returns all upcoming elections"
    (async/take! channels/election-all-upcoming
                 test-election-works-all-upcoming-response)
    (with-redefs [ts/authorize-request
                  (fn [req]
                    (when (= (get-in req [:query-params :user-key])
                             "totally-valid")
                      {::ts/status ::ts/authorized}))]
      (let [response (http/get (str root-url "/upcoming")
                               {:query-params
                                {:user-key "totally-valid"}})]
        (is (= 200 (:status response)))
        (is (= #{denver-election}
               (edn/read-string (:body response)))))))
  (testing "/upcoming with not-authorized response from 3scale returns 403"
    (with-redefs [ts/authorize-request (constantly
                                        {::ts/status ::ts/not-authorized
                                         ::ts/message "expected failure"})]
      (let [response (http/get (str root-url "/upcoming")
                               {:query-params
                                {:user-key "totally-valid"}
                                :throw-exceptions false})]
        (is (= 403 (:status response)))
        (is (= "expected failure"
               (-> response
                   :body
                   edn/read-string
                   :message))))))
  (testing "/upcoming with auth error responds with 500"
    (with-redefs [ts/authorize-request (constantly
                                        {::ts/status ::ts/error
                                         ::ts/message "expected error"})]
      (let [response (http/get (str root-url "/upcoming")
                               {:query-params
                                {:user-key "totally-valid"}
                                :throw-exceptions false})]
        (is (= 500 (:status response)))
        (is (= "expected error"
               (-> response
                   :body
                   edn/read-string
                   :message)))))))

(deftest elections-test
  (testing "/elections responds with elections when found"
    (async/take! channels/election-all
                 test-election-works-all-response)
    (let [response (http/get
                    (str root-url "/elections")
                    {:throw-exceptions false})]
      (is (= 200 (:status response)))
      (is (= #{denver-election chaffee-election}
             (edn/read-string (:body response)))))))
