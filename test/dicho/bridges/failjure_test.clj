(ns dicho.bridges.failjure-test
  (:require [clojure.test :refer [deftest is testing]]
            [failjure.core :as f]
            [dicho.core :refer [ok err]]
            [dicho.bridges.failjure]))

(deftest ok-response-hasfailed-test
  (testing "OkResponse implements HasFailed protocol"
    (let [success (ok "test data")]
      (is (not (f/failed? success)))
      (is (= "Success" (f/message success)))))

  (testing "OkResponse with metadata implements HasFailed protocol"
    (let [success (ok {:data 42} {:trace-id "abc123"})]
      (is (not (f/failed? success)))
      (is (= "Success" (f/message success))))))

(deftest error-response-hasfailed-test
  (testing "ErrorResponse implements HasFailed protocol"
    (let [error (err :not-found "Resource not found")]
      (is (f/failed? error))
      (is (= "Resource not found" (f/message error)))))

  (testing "ErrorResponse with metadata implements HasFailed protocol"
    (let [error (err :invalid-params "Bad input" {:detail "Field required"})]
      (is (f/failed? error))
      (is (= "Bad input" (f/message error))))))

(deftest failjure-integration-test
  (testing "failjure attempt-all works with dicho types"
    (let [success-result (ok "success")
          error-result (err :timeout "Request timeout")]
      (is (= "success" (f/attempt-all [result success-result]
                                      (:result result))))

      (is (f/failed? (f/attempt-all [result error-result]
                                    (:result result)))))))