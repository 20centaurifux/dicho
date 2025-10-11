(ns dicho.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [dicho.core :refer [ok err ok? error? result when-ok when-failed either match-status]]
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

(deftest when-ok-test
  (testing "when-ok executes body on successful response"
    (let [success-response (ok "hello")
          result (when-ok [value success-response]
                   (str value " world"))]
      (is (= "hello world" result))))

  (testing "when-ok returns nil on error response"
    (let [error-response (err :not-found "Resource not found")
          result (when-ok [value error-response]
                   (str "This should not execute: " value))]
      (is (nil? result))))

  (testing "when-ok works with nil values"
    (let [nil-response (ok nil)
          result (when-ok [value nil-response]
                   (str "Value is: " value))]
      (is (= "Value is: " result))))

  (testing "when-ok works with complex data structures"
    (let [complex-data {:users [{:name "Alice"} {:name "Bob"}]
                        :count 2}
          success-response (ok complex-data)
          result (when-ok [data success-response]
                   (get-in data [:users 0 :name]))]
      (is (= "Alice" result))))

  (testing "when-ok handles multiple expressions in body"
    (let [success-response (ok 5)
          side-effect (atom 0)
          result (when-ok [num success-response]
                   (swap! side-effect inc)
                   (swap! side-effect inc)
                   (* num 2))]
      (is (= 10 result))
      (is (= 2 @side-effect))))

  (testing "when-ok does not execute side effects on error"
    (let [error-response (err :invalid "Bad request")
          side-effect (atom 0)
          result (when-ok [value error-response]
                   (swap! side-effect inc)
                   "should not execute")]
      (is (nil? result))
      (is (= 0 @side-effect))))

  (testing "when-ok throws assertion error for invalid responses"
    (is (thrown-with-msg? AssertionError
                          #"Response does not conform ok nor error spec"
                          (when-ok [value {:invalid "response"}]
                            value)))
    (is (thrown-with-msg? AssertionError
                          #"Response does not conform ok nor error spec"
                          (when-ok [value nil]
                            value)))
    (is (thrown-with-msg? AssertionError
                          #"Response does not conform ok nor error spec"
                          (when-ok [value "not a response"]
                            value))))

  (testing "when-ok with responses containing metadata"
    (let [response-with-meta (ok "data" {:trace-id "abc123" :timestamp #inst "2024-01-01"})
          result (when-ok [value response-with-meta]
                   (str "Processed: " value))]
      (is (= "Processed: data" result))))

  (testing "when-ok binding works correctly"
    (let [success-response (ok [1 2 3 4 5])
          result (when-ok [numbers success-response]
                   (reduce + numbers))]
      (is (= 15 result)))))

(deftest when-failed-test
  (testing "when-failed executes body on error response"
    (let [error-response (err :not-found "Resource not found")
          result (when-failed [error error-response]
                   (str "Error: " (:title error)))]
      (is (= "Error: Resource not found" result))))

  (testing "when-failed returns nil on success response"
    (let [success-response (ok "success")
          result (when-failed [error success-response]
                   (str "This should not execute: " (:title error)))]
      (is (nil? result))))

  (testing "when-failed works with different error statuses"
    (let [validation-error (err :invalid-params "Validation failed")
          result (when-failed [error validation-error]
                   (:status error))]
      (is (= :invalid-params result)))

    (let [server-error (err :internal-error "Server crashed")
          result (when-failed [error server-error]
                   (:status error))]
      (is (= :internal-error result))))

  (testing "when-failed works with error metadata"
    (let [detailed-error (err :rate-limited "Too many requests"
                              {:retry? true
                               :detail "Try again in 60 seconds"
                               :trace-id "xyz789"})
          result (when-failed [error detailed-error]
                   {:status (:status error)
                    :can-retry (:retry? error)
                    :message (:detail error)})]
      (is (= {:status :rate-limited
              :can-retry true
              :message "Try again in 60 seconds"} result))))

  (testing "when-failed handles multiple expressions in body"
    (let [error-response (err :timeout "Request timed out")
          side-effect (atom 0)
          result (when-failed [error error-response]
                   (swap! side-effect inc)
                   (swap! side-effect inc)
                   (str "Failed with: " (:title error)))]
      (is (= "Failed with: Request timed out" result))
      (is (= 2 @side-effect))))

  (testing "when-failed does not execute side effects on success"
    (let [success-response (ok "all good")
          side-effect (atom 0)
          result (when-failed [error success-response]
                   (swap! side-effect inc)
                   "should not execute")]
      (is (nil? result))
      (is (= 0 @side-effect))))

  (testing "when-failed throws assertion error for invalid responses"
    (is (thrown-with-msg? AssertionError
                          #"Response does not conform ok nor error spec"
                          (when-failed [error {:invalid "response"}]
                            (:title error))))
    (is (thrown-with-msg? AssertionError
                          #"Response does not conform ok nor error spec"
                          (when-failed [error nil]
                            (:title error))))
    (is (thrown-with-msg? AssertionError
                          #"Response does not conform ok nor error spec"
                          (when-failed [error "not a response"]
                            (:title error)))))

  (testing "when-failed binding works with complete error response"
    (let [complex-error (err :validation-failed "Multiple validation errors"
                             {:fields {:name "required" :email "invalid format"}
                              :retry? false
                              :cause "User input validation"})
          result (when-failed [error complex-error]
                   (count (get-in error [:fields])))]
      (is (= 2 result))))

  (testing "when-failed accesses all error response fields"
    (let [full-error (err :database-error "Connection failed"
                          {:detail "Could not connect to database"
                           :cause "Network timeout"
                           :retry? true
                           :trace-id "db-error-123"
                           :timestamp #inst "2024-01-01"})
          result (when-failed [error full-error]
                   {:status (:status error)
                    :title (:title error)
                    :detail (:detail error)
                    :cause (:cause error)
                    :retry (:retry? error)
                    :trace (:trace-id error)})]
      (is (= {:status :database-error
              :title "Connection failed"
              :detail "Could not connect to database"
              :cause "Network timeout"
              :retry true
              :trace "db-error-123"} result)))))

(deftest either-test
  (testing "either executes ok-body on success response"
    (let [success-response (ok "hello world")
          result (either success-response
                   [value] (str "Success: " value)
                   [error] (str "Error: " (:title error)))]
      (is (= "Success: hello world" result))))

  (testing "either executes error-body on error response"
    (let [error-response (err :not-found "Resource not found")
          result (either error-response
                   [value] (str "Success: " value)
                   [error] (str "Error: " (:title error)))]
      (is (= "Error: Resource not found" result))))

  (testing "either works with complex success data"
    (let [complex-data {:users [{:name "Alice"} {:name "Bob"}] :count 2}
          success-response (ok complex-data)
          result (either success-response
                   [data] (get-in data [:users 0 :name])
                   [error] (:status error))]
      (is (= "Alice" result))))

  (testing "either works with complex error data"
    (let [error-response (err :validation-failed "Multiple errors"
                              {:fields {:name "required" :email "invalid"}
                               :retry? false})
          result (either error-response
                   [data] (str "Got data: " data)
                   [error] (count (:fields error)))]
      (is (= 2 result))))

  (testing "either handles multiple expressions in ok-body"
    (let [success-response (ok 10)
          side-effect (atom 0)
          result (either success-response
                   [num] (do
                           (swap! side-effect inc)
                           (swap! side-effect inc)
                           (* num 2))
                   [error] (str "Error: " (:title error)))]
      (is (= 20 result))
      (is (= 2 @side-effect))))

  (testing "either handles multiple expressions in error-body"
    (let [error-response (err :timeout "Request timed out")
          side-effect (atom 0)
          result (either error-response
                   [value] (str "Success: " value)
                   [error] (do
                             (swap! side-effect inc)
                             (swap! side-effect inc)
                             (str "Failed: " (:title error))))]
      (is (= "Failed: Request timed out" result))
      (is (= 2 @side-effect))))

  (testing "either does not execute error-body on success"
    (let [success-response (ok "success")
          error-side-effect (atom 0)
          result (either success-response
                   [value] (str "Got: " value)
                   [error] (do
                             (swap! error-side-effect inc)
                             "should not execute"))]
      (is (= "Got: success" result))
      (is (= 0 @error-side-effect))))

  (testing "either does not execute ok-body on error"
    (let [error-response (err :invalid "Bad input")
          ok-side-effect (atom 0)
          result (either error-response
                   [value] (do
                             (swap! ok-side-effect inc)
                             "should not execute")
                   [error] (str "Error: " (:title error)))]
      (is (= "Error: Bad input" result))
      (is (= 0 @ok-side-effect))))

  (testing "either throws assertion error for invalid responses"
    (is (thrown-with-msg? AssertionError
                          #"Response does not conform ok nor error spec"
                          (either {:invalid "response"}
                            [value] value
                            [error] (:title error))))
    (is (thrown-with-msg? AssertionError
                          #"Response does not conform ok nor error spec"
                          (either nil
                            [value] value
                            [error] (:title error))))
    (is (thrown-with-msg? AssertionError
                          #"Response does not conform ok nor error spec"
                          (either "not a response"
                            [value] value
                            [error] (:title error)))))

  (testing "either works with responses containing metadata"
    (let [success-with-meta (ok "data" {:trace-id "abc123" :timestamp #inst "2024-01-01"})
          result (either success-with-meta
                   [value] (str "Processed: " value)
                   [error] (str "Failed: " (:title error)))]
      (is (= "Processed: data" result)))

    (let [error-with-meta (err :database-error "Connection failed"
                               {:trace-id "xyz789" :retry? true})
          result (either error-with-meta
                   [value] (str "Success: " value)
                   [error] {:status (:status error)
                            :retry (:retry? error)
                            :trace (:trace-id error)})]
      (is (= {:status :database-error
              :retry true
              :trace "xyz789"} result))))

  (testing "either with nil success value"
    (let [nil-response (ok nil)
          result (either nil-response
                   [value] (str "Value is nil: " (nil? value))
                   [error] (str "Error: " (:title error)))]
      (is (= "Value is nil: true" result)))))

(deftest match-status-test
  (testing "match-status matches ok status"
    (let [success-response (ok "success data")
          result (match-status success-response
                   :ok "Operation successful"
                   :not-found "Resource missing"
                   :timeout "Request timed out"
                   "Unknown status")]
      (is (= "Operation successful" result))))

  (testing "match-status matches specific error statuses"
    (let [not-found-response (err :not-found "Resource not found")
          result (match-status not-found-response
                   :ok "Success"
                   :not-found "Resource missing"
                   :timeout "Request timed out"
                   "Unknown status")]
      (is (= "Resource missing" result)))

    (let [timeout-response (err :timeout "Request timed out")
          result (match-status timeout-response
                   :ok "Success"
                   :not-found "Resource missing"
                   :timeout "Request timed out"
                   "Unknown status")]
      (is (= "Request timed out" result))))

  (testing "match-status falls back to default case"
    (let [unknown-error (err :database-error "Connection failed")
          result (match-status unknown-error
                   :ok "Success"
                   :not-found "Resource missing"
                   :timeout "Request timed out"
                   "Unknown status")]
      (is (= "Unknown status" result))))

  (testing "match-status works with expressions as values"
    (let [validation-error (err :invalid-params "Validation failed")
          result (match-status validation-error
                   :ok (str "Success: " "all good")
                   :invalid-params (str "Validation error: " "check input")
                   :not-found (str "Missing: " "resource")
                   (str "Unknown: " "status"))]
      (is (= "Validation error: check input" result))))

  (testing "match-status works with complex expressions"
    (let [rate-limit-error (err :rate-limited "Too many requests")
          counter (atom 0)
          result (match-status rate-limit-error
                   :ok (do (swap! counter inc) "success")
                   :rate-limited (do (swap! counter #(+ % 2)) "rate limited")
                   :timeout (do (swap! counter #(+ % 3)) "timeout")
                   (do (swap! counter #(+ % 10)) "unknown"))]
      (is (= "rate limited" result))
      (is (= 2 @counter))))

  (testing "match-status does not execute non-matching branches"
    (let [success-response (ok "data")
          side-effects (atom [])
          result (match-status success-response
                   :ok (do (swap! side-effects conj :ok) "success")
                   :not-found (do (swap! side-effects conj :not-found) "missing")
                   :timeout (do (swap! side-effects conj :timeout) "timeout")
                   (do (swap! side-effects conj :default) "unknown"))]
      (is (= "success" result))
      (is (= [:ok] @side-effects))))

  (testing "match-status works with numeric and complex return values"
    (let [server-error (err :internal-error "Server crashed")
          result (match-status server-error
                   :ok 200
                   :not-found 404
                   :internal-error 500
                   :timeout 408
                   999)]
      (is (= 500 result)))

    (let [success-response (ok {:data [1 2 3]})
          result (match-status success-response
                   :ok {:status "success" :code 200}
                   :not-found {:status "not-found" :code 404}
                   {:status "unknown" :code 999})]
      (is (= {:status "success" :code 200} result))))

  (testing "match-status throws assertion error for invalid responses"
    (is (thrown-with-msg? AssertionError
                          #"Response does not conform ok nor error spec"
                          (match-status {:invalid "response"}
                            :ok "success"
                            "default")))
    (is (thrown-with-msg? AssertionError
                          #"Response does not conform ok nor error spec"
                          (match-status nil
                            :ok "success"
                            "default")))
    (is (thrown-with-msg? AssertionError
                          #"Response does not conform ok nor error spec"
                          (match-status "not a response"
                            :ok "success"
                            "default"))))

  (testing "match-status works with responses containing metadata"
    (let [success-with-meta (ok "data" {:trace-id "abc123" :timestamp #inst "2024-01-01"})
          result (match-status success-with-meta
                   :ok "successful operation"
                   :error "failed operation"
                   "unknown operation")]
      (is (= "successful operation" result)))

    (let [error-with-meta (err :unauthorized "Access denied"
                               {:trace-id "xyz789" :retry? false})
          result (match-status error-with-meta
                   :ok "allowed"
                   :unauthorized "access denied"
                   :forbidden "permission denied"
                   "unknown auth status")]
      (is (= "access denied" result)))))