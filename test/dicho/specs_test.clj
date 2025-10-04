(ns dicho.specs-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.spec.alpha :as s]
            [dicho.specs :as specs]
            [dicho.core :refer [ok err]]
            [dicho.types :refer [->OkResponse ->ErrorResponse]]))

(deftest ok-spec-test
  (testing "valid ok responses"
    (is (s/valid? ::specs/ok (ok "success")))
    (is (s/valid? ::specs/ok (ok {:data 42})))
    (is (s/valid? ::specs/ok (ok nil)))
    (is (s/valid? ::specs/ok (->OkResponse :ok "test"))))

  (testing "ok responses with metadata"
    (is (s/valid? ::specs/ok (ok "data" {:trace-id "123"})))
    (is (s/valid? ::specs/ok (ok "data" {:timestamp #inst "2024-01-01"}))))

  (testing "ok responses with invalid metadata should throw AssertionError"
    (is (thrown? AssertionError (ok "data" {:trace-id 123}))) ; trace-id must be string
    (is (thrown? AssertionError (ok "data" {:trace-id ""}))) ; trace-id must not be empty
    (is (thrown? AssertionError (ok "data" {:timestamp "2024-01-01"}))) ; timestamp must be inst
    (is (thrown? AssertionError (ok "data" {:timestamp 1234567890})))) ; timestamp must be inst

  (testing "invalid ok responses"
    (is (not (s/valid? ::specs/ok (err :not-found "error"))))
    (is (not (s/valid? ::specs/ok {:result "test"}))) ; missing status
    (is (not (s/valid? ::specs/ok nil)))
    (is (not (s/valid? ::specs/ok {})))))

(deftest error-spec-test
  (testing "valid error responses"
    (is (s/valid? ::specs/error (err :not-found "Not found")))
    (is (s/valid? ::specs/error (err :invalid-params "Bad input")))
    (is (s/valid? ::specs/error (->ErrorResponse :internal "Server error"))))

  (testing "error responses with metadata"
    (is (s/valid? ::specs/error (err :conflict "Conflict" {:detail "Resource exists"})))
    (is (s/valid? ::specs/error (err :timeout "Timeout" {:retry? true})))
    (is (s/valid? ::specs/error (err :internal "Server error" {:cause "Database connection failed"})))
    (is (s/valid? ::specs/error (err :invalid-params "Bad input" {:fields {:name "required" :email "invalid format"}})))
    (is (s/valid? ::specs/error (err :not-found "Not found" {:trace-id "abc123"})))
    (is (s/valid? ::specs/error (err :unauthorized "Access denied" {:timestamp #inst "2024-01-01"})))
    (is (s/valid? ::specs/error (err :rate-limited "Too many requests"
                                     {:detail "Rate limit exceeded"
                                      :retry? true
                                      :cause "User exceeded quota"
                                      :fields {:limit 100 :current 150}
                                      :trace-id "xyz789"
                                      :timestamp #inst "2024-01-02"}))))

  (testing "error responses with invalid metadata should throw AssertionError"
    (is (thrown? AssertionError (err :not-found "Error" {:detail ""}))) ; detail must not be empty
    (is (thrown? AssertionError (err :timeout "Error" {:detail 123}))) ; detail must be string
    (is (thrown? AssertionError (err :internal "Error" {:retry? "true"}))) ; retry? must be boolean
    (is (thrown? AssertionError (err :conflict "Error" {:cause ""}))) ; cause must not be empty
    (is (thrown? AssertionError (err :invalid-params "Error" {:cause 456}))) ; cause must be string
    (is (thrown? AssertionError (err :unauthorized "Error" {:fields "invalid"}))) ; fields must be map
    (is (thrown? AssertionError (err :forbidden "Error" {:trace-id 789}))) ; trace-id must be string
    (is (thrown? AssertionError (err :rate-limited "Error" {:trace-id ""}))) ; trace-id must not be empty
    (is (thrown? AssertionError (err :precondition-failed "Error" {:timestamp "invalid"}))) ; timestamp must be inst
    (is (thrown? AssertionError (err :temporarily-unavailable "Error" {:timestamp 1234567890})))) ; timestamp must be inst

  (testing "invalid error responses"
    (is (not (s/valid? ::specs/error (ok "success"))))
    (is (not (s/valid? ::specs/error {:status :error}))) ; missing title
    (is (not (s/valid? ::specs/error {:title "Error"}))) ; missing status
    (is (not (s/valid? ::specs/error nil)))
    (is (not (s/valid? ::specs/error {}))))

  (testing "error constructor with invalid parameters should throw AssertionError"
    (is (thrown? AssertionError (err :ok "Error"))) ; status cannot be :ok
    (is (thrown? AssertionError (err "not-keyword" "Error"))) ; status must be keyword
    (is (thrown? AssertionError (err :not-found 123))) ; msg must be string
    (is (thrown? AssertionError (err :not-found ""))) ; msg must not be empty
    (is (thrown? AssertionError (err :not-found "Error" "not-map"))))) ; extra must be map