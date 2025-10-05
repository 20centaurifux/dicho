(ns dicho.core
  "Core functions for creating standardized success and error responses."
  (:require [clojure.spec.alpha :as s]
            [dicho.types :refer [->OkResponse ->ErrorResponse]]
            [dicho.specs :as specs])
  (:import [dicho.types OkResponse ErrorResponse]))

(defn ok?
  "Returns true if `x` is a valid success response, false otherwise.

  A response is considered valid if it:
  - is an instance of OkResponse record
  - has :status field with value :ok  
  - has :result field (can be any value including nil)
  - all optional metadata fields conform to their specs:
    * :trace-id - non-empty string
    * :timestamp - java.time.Instant
    * custom fields are allowed"
  [x]
  (s/valid? ::specs/ok x))

(defn error?
  "Returns true if `x` is a valid error response, false otherwise.

  A response is considered valid if it:
  - is an instance of ErrorResponse record
  - has :status field with a keyword that is NOT :ok
  - has :title field with a non-empty string
  - all optional metadata fields conform to their specs:
    * :detail - non-empty string with additional error information
    * :retry? - boolean indicating if operation can be retried
    * :cause - non-empty string describing the underlying cause
    * :fields - map of field-specific validation errors
    * :trace-id - non-empty string for request tracing
    * :timestamp - java.time.Instant when error occurred
    * custom fields are allowed"
  [x]
  (s/valid? ::specs/error x))

(defn ok
  "Creates a `OkResponse` record.

  Examples:
   ```
    (ok \"success\")
    ;; => #dicho.types.OkResponse{:status :ok, :result \"success\"}
    
    (ok \"data\" {:trace-id \"abc123\"})
    ;; => #dicho.types.OkResponse{:status :ok, :result \"data\", :trace-id \"abc123\"}
   ```
  
  Throws AssertionError if extra is not a map and result doesn't conform to `:dicho.specs/ok`."
  ([v]
   {:post [(s/valid? ::specs/ok %)]}
   (->OkResponse :ok v))
  ([v extra]
   {:pre [(map? extra)]
    :post [(s/valid? ::specs/ok %)]}
   (merge (->OkResponse :ok v) extra)))

(defn err
  "Creates an `ErrorResponse` record.

  Examples:
   ```
    (err :not-found \"Resource not found\")
    ;; => #dicho.types.ErrorResponse{:status :not-found, :title \"Resource not found\"}

    (err :rate-limited \"Too many requests\" {:retry? true :cause \"Quota exceeded\"})
    ;; => #dicho.types.ErrorResponse{:status :rate-limited, :title \"Too many requests\", :retry? true, :cause \"Quota exceeded\"}
   ```
   
  Throws AssertionError if `extra` is not a map or result doesn't conform to `:dicho.specs/error`."
  ([status msg]
   {:post [(s/valid? ::specs/error %)]}
   (->ErrorResponse status msg))
  ([status msg extra]
   {:pre [(map? extra)]
    :post [(s/valid? ::specs/error %)]}
   (merge (->ErrorResponse status msg) extra)))

;; Protocol for unwrapping response values

(defprotocol Result
  "Protocol for unwrapping response values."
  (result [this] "Unwraps the response value or throws an exception."))

(extend-type OkResponse
  Result
  (result [this] (:result this)))

(extend-type ErrorResponse
  Result
  (result [this]
    (throw (ex-info (:title this)
                    (dissoc this :title)))))