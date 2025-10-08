(ns dicho.convert-test
  (:require [clojure.test :refer [deftest is testing]]
            [dicho.convert :as convert]
            [dicho.core :refer [ok err]])
  (:import [dicho.types OkResponse ErrorResponse]))

(deftest response->map-test
  (testing "ok response conversion"
    (let [response (ok "success")
          result (convert/response->map response)]
      (is (= {:status :ok :result "success"} result))
      (is (map? result))))

  (testing "invalid response throws exception"
    (is (thrown? AssertionError (convert/response->map nil)))
    (is (thrown? AssertionError (convert/response->map {})))
    (is (thrown? AssertionError (convert/response->map "not-a-response"))))

  (testing "ok response with metadata conversion"
    (let [response (ok {:data 42} {:trace-id "abc123" :timestamp #inst "2024-01-01"})
          result (convert/response->map response)]
      (is (= :ok (:status result)))
      (is (= {:data 42} (:result result)))
      (is (= "abc123" (:trace-id result)))
      (is (= #inst "2024-01-01" (:timestamp result)))))

  (testing "error response conversion"
    (let [response (err :not-found "Not found")
          result (convert/response->map response)]
      (is (= {:status :not-found :title "Not found"} result))
      (is (map? result))))

  (testing "error response with metadata conversion"
    (let [response (err :invalid-params "Bad input" {:detail "Missing field" :retry? false})
          result (convert/response->map response)]
      (is (= :invalid-params (:status result)))
      (is (= "Bad input" (:title result)))
      (is (= "Missing field" (:detail result)))
      (is (= false (:retry? result))))))

(deftest map->response-test
  (testing "map to ok response"
    (let [map-data {:status :ok :result "success"}
          response (convert/map->response map-data)]
      (is (instance? OkResponse response))
      (is (= :ok (:status response)))
      (is (= "success" (:result response)))))

  (testing "map to ok response with metadata"
    (let [map-data {:status :ok :result {:data 42} :trace-id "xyz789"}
          response (convert/map->response map-data)]
      (is (instance? OkResponse response))
      (is (= {:data 42} (:result response)))
      (is (= "xyz789" (:trace-id response)))))

  (testing "map to error response"
    (let [map-data {:status :not-found :title "Not found"}
          response (convert/map->response map-data)]
      (is (instance? ErrorResponse response))
      (is (= :not-found (:status response)))
      (is (= "Not found" (:title response)))))

  (testing "map to error response with metadata"
    (let [map-data {:status :invalid-params :title "Bad input" :detail "Missing name" :fields {:name "required"}}
          response (convert/map->response map-data)]
      (is (instance? ErrorResponse response))
      (is (= "Bad input" (:title response)))
      (is (= "Missing name" (:detail response)))
      (is (= {:name "required"} (:fields response)))))

  (testing "roundtrip conversion"
    (let [original (ok {:user-id 123} {:trace-id "test-trace"})
          as-map (convert/response->map original)
          back-to-response (convert/map->response as-map)]
      (is (= original back-to-response))))

  (testing "invalid input throws exceptions"
    (testing "non-map input"
      (is (thrown? AssertionError (convert/map->response nil)))
      (is (thrown? AssertionError (convert/map->response "not-a-map")))
      (is (thrown? AssertionError (convert/map->response 42)))
      (is (thrown? AssertionError (convert/map->response []))))

    (testing "map without :status key"
      (is (thrown? AssertionError (convert/map->response {})))
      (is (thrown? AssertionError (convert/map->response {:result "success"})))
      (is (thrown? AssertionError (convert/map->response {:title "Error"}))))

    (testing "map with invalid spec conformance"
      (testing "error response without required title"
        (is (thrown-with-msg?
             clojure.lang.ExceptionInfo
             #"Response does not conform ok nor error spec\."
             (convert/map->response {:status :not-found}))))

      (testing "error response with empty title"
        (is (thrown-with-msg?
             clojure.lang.ExceptionInfo
             #"Response does not conform ok nor error spec\."
             (convert/map->response {:status :invalid-params :title ""}))))

      (testing "response with invalid metadata"
        (is (thrown-with-msg?
             clojure.lang.ExceptionInfo
             #"Response does not conform ok nor error spec\."
             (convert/map->response {:status :ok :result "test" :trace-id 123})))

        (is (thrown-with-msg?
             clojure.lang.ExceptionInfo
             #"Response does not conform ok nor error spec\."
             (convert/map->response {:status :not-found :title "Error" :timestamp "invalid"})))))))

(deftest response->ex-info-test
  (testing "basic error response to ex-info"
    (let [error-response (err :not-found "User not found")
          exception (convert/response->ex-info error-response)]
      (is (instance? clojure.lang.ExceptionInfo exception))
      (is (= "User not found" (.getMessage exception)))
      (is (= {:status :not-found} (ex-data exception)))))

  (testing "error response with metadata to ex-info"
    (let [error-response (err :invalid-params "Validation failed" {:fields {:name "required" :email "invalid"}})
          exception (convert/response->ex-info error-response)]
      (is (= "Validation failed" (.getMessage exception)))
      (let [data (ex-data exception)]
        (is (= :invalid-params (:status data)))
        (is (= {:name "required" :email "invalid"} (:fields data)))
        (is (not (contains? data :title)))))))

(deftest ex-info->response-test
  (testing "basic ex-info to error response"
    (let [exception (ex-info "Something went wrong" {:status :internal :detail "Database error"})
          response (convert/ex-info->response exception)]
      (is (instance? ErrorResponse response))
      (is (= :internal (:status response)))
      (is (= "Something went wrong" (:title response)))
      (is (= "Database error" (:detail response)))))

  (testing "ex-info without status throws exception"
    (let [exception (ex-info "Generic error" {:some-field "value"})]
      (is (thrown? AssertionError (convert/ex-info->response exception)))))

  (testing "ex-info with status in ex-data"
    (let [exception (ex-info "Custom error" {:status :custom-error})
          response (convert/ex-info->response exception)]
      (is (= :custom-error (:status response)))
      (is (= "Custom error" (:title response)))))

  (testing "ex-info with title in ex-data is removed from extra fields"
    (let [exception (ex-info "Message from exception" {:status :error :title "Title from data" :detail "Extra detail"})
          response (convert/ex-info->response exception)]
      (is (= :error (:status response)))
      (is (= "Message from exception" (:title response))) ; title comes from getMessage, not ex-data
      (is (= "Extra detail" (:detail response))) ; detail is preserved
      (is (not (contains? response :title-from-data))))) ; title from ex-data is not included

  (testing "roundtrip ex-info conversion"
    (let [original (err :timeout "Request timeout" {:retry? true :attempt 3})
          exception (convert/response->ex-info original)
          back-to-response (convert/ex-info->response exception)]
      (is (= original back-to-response)))))