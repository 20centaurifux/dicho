(ns dicho.specs-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.spec.alpha :as s]
            [dicho.specs :as specs]
            [dicho.types :refer [->OkResponse ->ErrorResponse]]))

;; helper functions

(defn- valid-ok?
  "Creates an OkResponse and checks if it's valid according to ::specs/ok"
  ([result]
   (s/valid? ::specs/ok (->OkResponse :ok result)))
  ([result metadata]
   (s/valid? ::specs/ok (merge (->OkResponse :ok result) metadata))))

(defn- invalid-ok?
  "Creates an OkResponse and checks if it's invalid according to ::specs/ok"
  ([result]
   (not (s/valid? ::specs/ok (->OkResponse :ok result))))
  ([result metadata]
   (not (s/valid? ::specs/ok (merge (->OkResponse :ok result) metadata)))))

(defn- valid-error?
  "Creates an ErrorResponse and checks if it's valid according to ::specs/error"
  ([status title]
   (s/valid? ::specs/error (->ErrorResponse status title)))
  ([status title metadata]
   (s/valid? ::specs/error (merge (->ErrorResponse status title) metadata))))

(defn- invalid-error?
  "Creates an ErrorResponse and checks if it's invalid according to ::specs/error"
  ([status title]
   (not (s/valid? ::specs/error (->ErrorResponse status title))))
  ([status title metadata]
   (not (s/valid? ::specs/error (merge (->ErrorResponse status title) metadata)))))

;; tests

(deftest ok-spec-test
  (testing "valid ok responses"
    (is (valid-ok? "success"))
    (is (valid-ok? {:data 42}))
    (is (valid-ok? nil))
    (is (valid-ok? "test")))

  (testing "ok responses with metadata"
    (is (valid-ok? "data" {:trace-id "123"}))
    (is (valid-ok? "data" {:timestamp #inst "2024-01-01"})))

  (testing "ok responses with invalid metadata should not conform to spec"
    (is (invalid-ok? "data" {:trace-id 123})) ; trace-id must be string
    (is (invalid-ok? "data" {:trace-id ""})) ; trace-id must not be empty
    (is (invalid-ok? "data" {:timestamp "2024-01-01"})) ; timestamp must be inst
    (is (invalid-ok? "data" {:timestamp 1234567890})))

  (testing "invalid ok responses"
    (is (not (s/valid? ::specs/ok (->ErrorResponse :not-found "error"))))
    (is (not (s/valid? ::specs/ok {:result "test"}))) ; missing status
    (is (not (s/valid? ::specs/ok nil)))
    (is (not (s/valid? ::specs/ok {})))))

(deftest error-spec-test
  (testing "valid error responses"
    (is (valid-error? :not-found "Not found"))
    (is (valid-error? :invalid-params "Bad input"))
    (is (valid-error? :internal "Server error")))

  (testing "error responses with metadata"
    (is (valid-error? :conflict "Conflict" {:detail "Resource exists"}))
    (is (valid-error? :timeout "Timeout" {:retry? true}))
    (is (valid-error? :internal "Server error" {:cause "Database connection failed"}))
    (is (valid-error? :invalid-params "Bad input" {:fields {:name "required" :email "invalid format"}}))
    (is (valid-error? :not-found "Not found" {:trace-id "abc123"}))
    (is (valid-error? :unauthorized "Access denied" {:timestamp #inst "2024-01-01"}))
    (is (valid-error? :rate-limited "Too many requests"
                      {:detail "Rate limit exceeded"
                       :retry? true
                       :cause "User exceeded quota"
                       :fields {:limit 100 :current 150}
                       :trace-id "xyz789"
                       :timestamp #inst "2024-01-02"})))

  (testing "error responses with invalid metadata should not conform to spec"
    (is (invalid-error? :not-found "Error" {:detail ""})) ; detail must not be empty
    (is (invalid-error? :timeout "Error" {:detail 123})) ; detail must be string
    (is (invalid-error? :internal "Error" {:retry? "true"})) ; retry? must be boolean
    (is (invalid-error? :conflict "Error" {:cause ""})) ; cause must not be empty
    (is (invalid-error? :invalid-params "Error" {:cause 456})) ; cause must be string
    (is (invalid-error? :unauthorized "Error" {:fields "invalid"})) ; fields must be map
    (is (invalid-error? :forbidden "Error" {:trace-id 789})) ; trace-id must be string
    (is (invalid-error? :rate-limited "Error" {:trace-id ""})) ; trace-id must not be empty
    (is (invalid-error? :precondition-failed "Error" {:timestamp "invalid"})) ; timestamp must be inst
    (is (invalid-error? :temporarily-unavailable "Error" {:timestamp 1234567890})))

  (testing "invalid error responses"
    (is (not (s/valid? ::specs/error (->OkResponse :ok "success"))))
    (is (not (s/valid? ::specs/error {:status :error}))) ; missing title
    (is (not (s/valid? ::specs/error {:title "Error"}))) ; missing status
    (is (not (s/valid? ::specs/error nil)))
    (is (not (s/valid? ::specs/error {}))))

  (testing "error responses with :ok status should not conform to spec"
    (is (invalid-error? :ok "Error")) ; status cannot be :ok
    (is (invalid-error? :not-found "Error" {:status :ok})))) ; changed status to :ok

(deftest response-spec-test
  (testing "valid responses - ok type"
    (is (s/valid? ::specs/response (->OkResponse :ok "success")))
    (is (s/valid? ::specs/response (->OkResponse :ok {:data 42})))
    (is (s/valid? ::specs/response (->OkResponse :ok nil)))
    (is (s/valid? ::specs/response (merge (->OkResponse :ok "test")
                                          {:trace-id "abc123"
                                           :timestamp #inst "2024-01-01"}))))

  (testing "valid responses - error type"
    (is (s/valid? ::specs/response (->ErrorResponse :not-found "Not found")))
    (is (s/valid? ::specs/response (->ErrorResponse :invalid-params "Bad input")))
    (is (s/valid? ::specs/response (merge (->ErrorResponse :internal "Server error")
                                          {:detail "Database connection failed"
                                           :retry? true
                                           :cause "Connection timeout"
                                           :fields {:connection "timeout"}
                                           :trace-id "xyz789"
                                           :timestamp #inst "2024-01-02"}))))

  (testing "invalid responses"
    (is (not (s/valid? ::specs/response nil)))
    (is (not (s/valid? ::specs/response {})))
    (is (not (s/valid? ::specs/response {:status :ok}))) ; missing OkResponse record type
    (is (not (s/valid? ::specs/response {:status :not-found :title "Error"}))) ; missing ErrorResponse record type
    (is (not (s/valid? ::specs/response "not a response")))
    (is (not (s/valid? ::specs/response 42))))

  (testing "responses with invalid metadata"
    (is (not (s/valid? ::specs/response (merge (->OkResponse :ok "test")
                                               {:trace-id 123})))) ; trace-id must be string
    (is (not (s/valid? ::specs/response (merge (->ErrorResponse :internal "Error")
                                               {:timestamp "invalid"})))) ; timestamp must be inst
    (is (not (s/valid? ::specs/response (merge (->ErrorResponse :not-found "Error")
                                               {:retry? "true"})))) ; retry? must be boolean
    (is (not (s/valid? ::specs/response (merge (->ErrorResponse :conflict "Error")
                                               {:detail ""})))) ; detail must not be empty
    (is (not (s/valid? ::specs/response (merge (->ErrorResponse :internal "Error")
                                               {:fields "not-a-map"}))))) ; fields must be map

  (testing "conform returns tagged values"
    (let [ok-response (->OkResponse :ok "success")
          error-response (->ErrorResponse :not-found "Not found")]
      (is (= [:ok ok-response] (s/conform ::specs/response ok-response)))
      (is (= [:error error-response] (s/conform ::specs/response error-response))))

    (let [invalid-response {:not "a response"}]
      (is (= ::s/invalid (s/conform ::specs/response invalid-response))))))