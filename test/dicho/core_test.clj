(ns dicho.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [dicho.core :refer [ok err ok? error? result]]
            [dicho.types :refer [->OkResponse ->ErrorResponse]]))

(deftest ok-test
  (testing "ok function with single argument"
    (let [result (ok "success")]
      (is (= (->OkResponse :ok "success") result))
      (is (ok? result))))

  (testing "ok function with extra metadata"
    (let [result (ok {:data 42} {:trace-id "abc123" :timestamp #inst "2024-01-01"})]
      (is (= :ok (:status result)))
      (is (= {:data 42} (:result result)))
      (is (= "abc123" (:trace-id result)))
      (is (= #inst "2024-01-01" (:timestamp result)))
      (is (ok? result))))

  (testing "ok function with nil value"
    (let [result (ok nil)]
      (is (= (->OkResponse :ok nil) result))
      (is (ok? result))))

  (testing "ok function with empty extra map"
    (let [result (ok "test" {})]
      (is (= (->OkResponse :ok "test") result))
      (is (ok? result)))))

(deftest err-test
  (testing "err function with status and message"
    (let [result (err :not-found "Resource not found")]
      (is (= (->ErrorResponse :not-found "Resource not found") result))
      (is (error? result))))

  (testing "err function with extra metadata"
    (let [result (err :invalid-params "Bad input" {:detail "Field 'name' is required"
                                                   :retry? false})]
      (is (= :invalid-params (:status result)))
      (is (= "Bad input" (:title result)))
      (is (= "Field 'name' is required" (:detail result)))
      (is (= false (:retry? result)))
      (is (error? result))))

  (testing "err function with trace-id and timestamp"
    (let [result (err :internal "Server error" {:trace-id "xyz789"
                                                :timestamp #inst "2024-01-02"})]
      (is (= :internal (:status result)))
      (is (= "Server error" (:title result)))
      (is (= "xyz789" (:trace-id result)))
      (is (= #inst "2024-01-02" (:timestamp result)))
      (is (error? result))))

  (testing "err function with empty extra map"
    (let [result (err :conflict "Conflict occurred" {})]
      (is (= (->ErrorResponse :conflict "Conflict occurred") result))
      (is (error? result)))))

(deftest ok?-test
  (testing "ok? returns true for success responses"
    (is (ok? (ok "success")))
    (is (not (ok? {:status :ok :result "test"})))
    (is (not (ok? {:status :ok :result nil :trace-id "123"}))))

  (testing "ok? returns false for error responses"
    (is (not (ok? (err :not-found "Not found"))))
    (is (not (ok? {:status :error :title "Error"})))
    (is (not (ok? {:status :invalid-params :title "Bad params"}))))

  (testing "ok? handles edge cases"
    (is (not (ok? nil)))
    (is (not (ok? {})))
    (is (not (ok? {:status nil})))))

(deftest error?-test
  (testing "error? returns true for error responses"
    (is (error? (err :not-found "Not found")))
    (is (not (error? {:status :conflict :title "Error"})))
    (is (not (error? {:status :invalid-params :title "Bad params"}))))

  (testing "error? returns false for success responses"
    (is (not (error? (ok "success"))))
    (is (not (error? {:status :ok :result "test"})))
    (is (not (error? {:status :ok :result nil :trace-id "123"}))))

  (testing "error? handles edge cases"
    (is (not (error? nil)))
    (is (not (error? {})))
    (is (not (error? {:status nil})))))

(deftest integration-test
  (testing "ok and error functions are opposites"
    (let [success-result (ok "data")
          error-result (err :not-found "Not found")]
      (is (and (ok? success-result) (not (error? success-result))))
      (is (and (error? error-result) (not (ok? error-result))))))

  (testing "functions work with complex data"
    (let [complex-data {:users [{:id 1 :name "Alice"}
                                {:id 2 :name "Bob"}]
                        :meta {:count 2}}
          success-result (ok complex-data {:trace-id "complex-123"})
          error-result (err :invalid-params "Invalid user data"
                            {:fields {:name "required"}
                             :retry? true})]
      (is (ok? success-result))
      (is (= complex-data (:result success-result)))
      (is (= "complex-123" (:trace-id success-result)))

      (is (error? error-result))
      (is (= :invalid-params (:status error-result)))
      (is (= "Invalid user data" (:title error-result)))
      (is (= {:name "required"} (:fields error-result)))
      (is (= true (:retry? error-result))))))

(deftest result-protocol-test
  (testing "result protocol for OkResponse"
    (let [response (ok "success")]
      (is (= "success" (result response))))
    (let [response (ok {:data 42})]
      (is (= {:data 42} (result response)))))

  (testing "result protocol for ErrorResponse"
    (let [response (err :not-found "Resource not found")]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo
                            #"Resource not found"
                            (result response))))
    (let [response (err :invalid-params "Invalid input" {:detail "Field 'name' is required"})]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo
                            #"Invalid input"
                            (result response))))))

(deftest validation-test
  (testing "ok function with invalid extra parameter should throw AssertionError"
    (is (thrown? AssertionError (ok "data" "not-a-map")))
    (is (thrown? AssertionError (ok "data" 123)))
    (is (thrown? AssertionError (ok "data" []))))

  (testing "ok function with invalid metadata should throw AssertionError"
    (is (thrown? AssertionError (ok "data" {:trace-id 123}))) ; trace-id must be string
    (is (thrown? AssertionError (ok "data" {:trace-id ""}))) ; trace-id must not be empty
    (is (thrown? AssertionError (ok "data" {:timestamp "not-an-instant"}))) ; timestamp must be inst
    (is (thrown? AssertionError (ok "data" {:timestamp 1234567890})))) ; timestamp must be inst

  (testing "err function with invalid parameters should throw AssertionError"
    (is (thrown? AssertionError (err :ok "Error"))) ; status cannot be :ok
    (is (thrown? AssertionError (err "not-keyword" "Error"))) ; status must be keyword
    (is (thrown? AssertionError (err :not-found 123))) ; msg must be string
    (is (thrown? AssertionError (err :not-found ""))) ; msg must not be empty
    (is (thrown? AssertionError (err :not-found "Error" "not-map"))) ; extra must be map
    (is (thrown? AssertionError (err :not-found "Error" 123)))) ; extra must be map

  (testing "err function with invalid metadata should throw AssertionError"
    (is (thrown? AssertionError (err :not-found "Error" {:detail ""}))) ; detail must not be empty  
    (is (thrown? AssertionError (err :not-found "Error" {:detail 123}))) ; detail must be string
    (is (thrown? AssertionError (err :not-found "Error" {:retry? "true"}))) ; retry? must be boolean
    (is (thrown? AssertionError (err :not-found "Error" {:cause ""}))) ; cause must not be empty
    (is (thrown? AssertionError (err :not-found "Error" {:cause 456}))) ; cause must be string
    (is (thrown? AssertionError (err :not-found "Error" {:fields "invalid"}))) ; fields must be map
    (is (thrown? AssertionError (err :not-found "Error" {:trace-id 789}))) ; trace-id must be string
    (is (thrown? AssertionError (err :not-found "Error" {:trace-id ""}))) ; trace-id must not be empty
    (is (thrown? AssertionError (err :not-found "Error" {:timestamp "invalid"}))) ; timestamp must be inst
    (is (thrown? AssertionError (err :not-found "Error" {:timestamp 1234567890})))))