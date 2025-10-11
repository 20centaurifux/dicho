# dicho

dicho is a Clojure library for creating standardized success and error responses. It provides a structured way to handle responses in your application, ensuring consistency and type safety. The library also integrates with other tools like `failjure` for seamless error handling.

## Features

- **Success Responses (`OkResponse`)**:
  - Includes a `:status` field (always `:ok`) and a `:result` field for the actual data.
  - Supports optional metadata fields like `:trace-id` and `:timestamp`.

- **Error Responses (`ErrorResponse`)**:
  - Includes a `:status` field (error type keyword) and a `:title` field for a human-readable error message.
  - Supports additional fields like `:detail`, `:retry?`, `:cause`, and `:fields`.

- **Protocols**:
  - `Result`: Allows unwrapping of responses. Throws an exception for `ErrorResponse` and returns the result for `OkResponse`.

- **Integration**:
  - Compatible with `failjure` for error handling.

## Installation

Add the following dependency to your `project.clj`:

```clojure
[dicho "0.1.0-SNAPSHOT"]
```

## Usage

### Creating Responses

#### Success Response

```clojure
(require '[dicho.core :refer [ok]])

(def response (ok "Success data"))
(println response) ;; => #dicho.types.OkResponse{:status :ok, :result "Success data"}

;; With metadata
(def response-with-meta (ok {:data 42} {:trace-id "abc123"}))
(println response-with-meta)
;; => #dicho.types.OkResponse{:status :ok, :result {:data 42}, :trace-id "abc123"}
```

#### Error Response

```clojure
(require '[dicho.core :refer [err]])

(def error-response (err :not-found "Resource not found"))
(println error-response)
;; => #dicho.types.ErrorResponse{:status :not-found, :title "Resource not found"}

;; With additional fields
(def detailed-error (err :invalid-params "Invalid input" {:detail "Field 'name' is required"}))
(println detailed-error)
;; => #dicho.types.ErrorResponse{:status :invalid-params, :title "Invalid input", :detail "Field 'name' is required"}
```

### Checking Response Types

```clojure
(require '[dicho.core :refer [ok? error?]])

(ok? (ok "Success")) ;; => true
(error? (ok "Success")) ;; => false

(error? (err :not-found "Not found")) ;; => true
(ok? (err :not-found "Not found")) ;; => false
```

### Pattern Matching on Responses

dicho provides powerful pattern matching macros for handling responses conditionally.

#### `when-ok` - Execute code only on success

Execute code only when the response is successful, binding the result value:

```clojure
(require '[dicho.core :refer [when-ok]])

(when-ok [data (ok {:users ["Alice" "Bob"]})]
  (count (:users data)))
;; => 2

(when-ok [data (err :not-found "No users")]
  (count (:users data)))
;; => nil (body not executed)
```

#### `when-failed` - Execute code only on errors

Execute code only when the response is an error, binding the error response:

```clojure
(require '[dicho.core :refer [when-failed]])

(when-failed [error (err :timeout "Request timeout" {:retry? true})]
  (if (:retry? error)
    "Will retry"
    "Giving up"))
;; => "Will retry"

(when-failed [error (ok "success")]
  "This won't execute")
;; => nil (body not executed)
```

#### `either` - Handle both success and error cases

Pattern match with separate handlers for success and error cases:

```clojure
(require '[dicho.core :refer [either]])

(either (ok 42)
  [result] (* result 2)                   ; success handler
  [error] (str "Error: " (:title error))) ; error handler
;; => 84

(either (err :invalid-params "Bad input")
  [result] (* result 2)
  [error] (str "Error: " (:title error)))
;; => "Error: Bad input"
```

#### `match-status` - Case-like matching on status

Match on specific response statuses with compile-time optimization:

```clojure
(require '[dicho.core :refer [match-status]])

(defn handle-api-response [response]
  (match-status response
    :ok "Operation successful"
    :not-found "Resource not found"
    :timeout "Request timed out" 
    :rate-limited "Too many requests"
    "Unknown error"))

(handle-api-response (ok "data")) ;; => "Operation successful"
(handle-api-response (err :timeout "Slow")) ;; => "Request timed out"
(handle-api-response (err :custom-error "Unknown")) ;; => "Unknown error"
```

### Unwrapping Responses

```clojure
(require '[dicho.core :refer [result]])

(def success-response (ok "Success data"))
(result success-response) ;; => "Success data"

(def error-response (err :not-found "Resource not found"))
(result error-response) ;; Throws ExceptionInfo with message "Resource not found"
```

### Integration with Failjure

dicho integrates with the `failjure` library for error handling. `OkResponse` and `ErrorResponse` implement the `HasFailed` protocol.

```clojure
(require '[failjure.core :as f]
         '[dicho.core :refer [ok err]])

(def success-response (ok "Success data"))
(def error-response (err :not-found "Resource not found"))

(f/failed? success-response) ;; => false
(f/failed? error-response) ;; => true

(f/message success-response) ;; => "Success"
(f/message error-response) ;; => "Resource not found"
```

## Specs

dicho uses `clojure.spec` to validate responses. The specs ensure that responses conform to the expected structure.

### Success Response Spec

```clojure
(require '[clojure.spec.alpha :as s]
         '[dicho.specs :as specs]
         '[dicho.core :refer [ok]])

;; Valid response with a valid trace-id
(s/valid? ::specs/ok (ok {:data 42} {:trace-id "valid-trace-id"}))
;; => true
```

### Error Response Spec

```clojure
(require '[clojure.spec.alpha :as s]
         '[dicho.specs :as specs]
         '[dicho.core :refer [err]])

;; Valid error response
(s/valid? ::specs/error (err :not-found "Not found"))
;; => true
```

## Conversion Utilities

The `dicho.convert` namespace provides utilities for converting between dicho response records and other data structures like maps and exceptions. This is particularly useful for serialization, deserialization, and interoperability with other systems.

### Converting Responses to Maps

Use `response->map` to convert any dicho response to a plain map:

```clojure
(require '[dicho.convert :as convert]
         '[dicho.core :refer [ok err]])

;; Convert success response
(def success-response (ok {:data 42} {:trace-id "abc123"}))
(convert/response->map success-response)
;; => {:status :ok, :result {:data 42}, :trace-id "abc123"}

;; Convert error response
(def error-response (err :not-found "Resource not found" {:detail "User ID 123 not found"}))
(convert/response->map error-response)
;; => {:status :not-found, :title "Resource not found", :detail "User ID 123 not found"}
```

### Converting Maps to Responses

Use `map->response` to reconstruct dicho responses from maps. The function validates the resulting response against dicho specs:

```clojure
;; Convert map to success response
(def success-map {:status :ok :result "Success data"})
(convert/map->response success-map)
;; => #dicho.types.OkResponse{:status :ok, :result "Success data"}

;; Convert map to error response
(def error-map {:status :invalid-params :title "Validation failed"})
(convert/map->response error-map)
;; => #dicho.types.ErrorResponse{:status :invalid-params, :title "Validation failed"}

;; Invalid maps throw exceptions
(convert/map->response {:status :not-found}) ; Throws ExceptionInfo - missing title
```

### Exception Conversion

Convert between error responses and `ExceptionInfo` objects for seamless integration with Clojure's exception handling:

```clojure
;; Convert error response to exception
(def error-response (err :timeout "Request timeout" {:retry? true}))
(def exception (convert/response->ex-info error-response))

(.getMessage exception) ;; => "Request timeout"
(ex-data exception) ;; => {:status :timeout, :retry? true}

;; Convert exception back to error response
(convert/ex-info->response exception)
;; => #dicho.types.ErrorResponse{:status :timeout, :title "Request timeout", :retry? true}
```