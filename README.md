# Dicho

Dicho is a Clojure library for creating standardized success and error responses. It provides a structured way to handle responses in your application, ensuring consistency and type safety. The library also integrates with other tools like `failjure` for seamless error handling.

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

### Unwrapping Responses

```clojure
(require '[dicho.core :refer [result]])

(def success-response (ok "Success data"))
(result success-response) ;; => "Success data"

(def error-response (err :not-found "Resource not found"))
(result error-response) ;; Throws ExceptionInfo with message "Resource not found"
```

### Integration with Failjure

Dicho integrates with the `failjure` library for error handling. `OkResponse` and `ErrorResponse` implement the `HasFailed` protocol.

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

Dicho uses `clojure.spec` to validate responses. The specs ensure that responses conform to the expected structure.

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