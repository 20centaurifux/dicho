(ns dicho.types
  "Core data types for dicho responses.")

;; Represents a successful operation result.
;; Fields:
;; - status: Always :ok
;; - result: The actual result data
;;
;; Optional metadata fields:
;; - trace-id: String identifier for request tracing
;; - timestamp: Instant when the response was created
(defrecord OkResponse [status result])

;; Represents an error operation result.
;; Fields:
;; - status: Error type keyword (e.g., :not-found, :invalid-params)
;; - title: Human-readable error message
;;
;; Optional fields:
;; - detail: Additional error details
;; - retry?: Boolean indicating if the operation can be retried
;; - cause: String describing the underlying cause
;; - fields: Map of field-specific validation errors
;; - trace-id: String identifier for request tracing
;; - timestamp: Instant when the response was created
(defrecord ErrorResponse [status title])