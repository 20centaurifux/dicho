(ns dicho.specs
  "Clojure spec definitions for dicho response types.
  
  This namespace defines specs for validating:
  - Success responses (::ok) with status :ok and optional result data
  - Error responses (::error) with various error statuses and metadata
  - Common metadata fields like trace-id and timestamp
  
  The specs ensure type safety and validation for dicho response records."
  (:require [clojure.spec.alpha :as s])
  (:import [dicho.types OkResponse ErrorResponse]))

;;;; specs

(s/def ::trace-id (s/and string? seq))
(s/def ::timestamp inst?)

;;; error

(s/def :dicho.specs.error/status (s/and keyword?
                                        #(not= % :ok)))
(s/def :dicho.specs.error/title (s/and string? seq))
(s/def :dicho.specs.error/detail (s/and string? seq))
(s/def :dicho.specs.error/retry? boolean?)
(s/def :dicho.specs.error/cause (s/and string? seq))
(s/def :dicho.specs.error/fields (s/map-of keyword? any?))

(s/def ::error
  (s/and (partial instance? ErrorResponse)
         (s/keys :req-un [:dicho.specs.error/status
                          :dicho.specs.error/title]
                 :opt-un [:dicho.specs.error/detail
                          :dicho.specs.error/retry?
                          :dicho.specs.error/cause
                          :dicho.specs.error/fields
                          ::trace-id
                          ::timestamp])))

;;; ok

(s/def :dicho.specs.ok/status #{:ok})
(s/def :dicho.specs.ok/result any?)

(s/def ::ok
  (s/and (partial instance? OkResponse)
         (s/keys :req-un [:dicho.specs.ok/status]
                 :opt-un [:dicho.specs.ok/result
                          ::trace-id
                          ::timestamp])))

;;; response

(s/def ::response (s/or :ok ::ok
                        :error ::error))