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

;; Pattern matching

(defmacro when-ok
  "Execute body with result bound to symbol when response is ok, otherwise nil.
  Similar to when-let but for dicho ok responses.
  
  Examples:
   ```
   (when-ok [result (ok 42)]
     (* result 2))
   ```"
  [[binding response] & body]
  `(let [response# ~response]
     (assert (s/valid? ::specs/response response#)
             "Response does not conform ok nor error spec.")
     (when (= :ok (:status response#))
       (let [~binding (:result response#)]
         ~@body))))

(defmacro when-failed
  "Execute body with error response bound to symbol when response is failed, otherwise nil.
  Similar to when-let but for dicho error responses.
  
  Examples:
   ```
   (when-failed [error (err :not-found \"Document not found\")]
     (str \"Failed: \" (:title error)))
   ```"
  [[binding response] & body]
  `(let [response# ~response]
     (assert (s/valid? ::specs/response response#)
             "Response does not conform ok nor error spec.")
     (when (not= :ok (:status response#))
       (let [~binding response#]
         ~@body))))

(defmacro either
  "Pattern match on response with handlers for success and error cases.
  
  Examples:
   ```
   (either (ok 42)
     [result] (* result 2)
     [error] (str \"Failed: \" (:title error)))
   ```"
  [response ok-binding ok-body error-binding error-body]
  (let [response-sym (gensym "response")]
    `(let [~response-sym ~response]
       (assert (s/valid? ::specs/response ~response-sym)
               "Response does not conform ok nor error spec.")
       (if (= :ok (:status ~response-sym))
         (let [~@ok-binding (:result ~response-sym)]
           ~ok-body)
         (let [~@error-binding ~response-sym]
           ~error-body)))))

(defmacro match-status
  "Case-like matching on response status with compile-time optimization.
  
  Examples:
   ```
   (match-status some-response
     :ok \"Success!\"
     :not-found \"Missing resource\"
     :timeout \"Request timed out\"
     \"Unknown error\")
   ```"
  [response & cases]
  (let [response-sym (gensym "response")]
    `(let [~response-sym ~response]
       (assert (s/valid? ::specs/response ~response-sym)
               "Response does not conform ok nor error spec.")
       (case (:status ~response-sym)
         ~@cases))))