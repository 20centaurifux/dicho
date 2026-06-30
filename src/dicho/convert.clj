(ns dicho.convert
  "Conversion utilities for dicho response types.
  
  This namespace provides functions for converting between dicho response
  records and other data structures like maps and exceptions. Useful for
  serialization, deserialization, and interop with other systems."
  (:require [dicho.types :refer [->ErrorResponse map->OkResponse map->ErrorResponse]]
            [dicho.specs :as specs]
            [clojure.spec.alpha :as s]))

(defn response->map
  "Converts a dicho response record to a plain map.
  
  Throws ExceptionInfo if response does not conform to ::specs/response."
  [response]
  (when-not (s/valid? ::specs/response response)
    (throw (ex-info "Response does not conform to ::specs/response"
                    {:response response
                     :explain (s/explain-data ::specs/response response)})))
  (into {} response))

(defn map->response
  "Reconstructs a dicho response record from a plain map.
  
  Throws ExceptionInfo if m is not a map, does not contain :status, or the
  resulting response does not conform to ::specs/response."
  [m]
  (when-not (map? m)
    (throw (ex-info "Input must be a map"
                    {:input m
                     :type (type m)})))
  (when-not (contains? m :status)
    (throw (ex-info "Map must contain :status key"
                    {:map m})))
  (let [response (if (= :ok (:status m))
                   (map->OkResponse m)
                   (map->ErrorResponse m))]
    (when-not (s/valid? ::specs/response response)
      (throw (ex-info "Response does not conform ok nor error spec."
                      {:response response
                       :explain (s/explain-data ::specs/response response)})))
    response))

(defn response->ex-info
  "Converts an ErrorResponse to an ex-info exception.
  
  The :title field becomes the exception message, and all other fields
  (including :status, :detail, :retry?, etc.) are preserved in ex-data.
  The :title is removed from ex-data to avoid duplication since it's
  accessible via (.getMessage ex).
  
  Throws ExceptionInfo if error-response does not conform to ::specs/error."
  [error-response]
  (when-not (s/valid? ::specs/error error-response)
    (throw (ex-info "Input must be a valid ErrorResponse"
                    {:error-response error-response
                     :explain (s/explain-data ::specs/error error-response)})))
  (ex-info (:title error-response)
           (dissoc error-response :title)))

(defn ex-info->response
  "Converts an ExceptionInfo exception to an ErrorResponse.
  
  The exception message becomes the :title field, and :status must be present
  in ex-data. Any :title in ex-data is ignored in favor of the exception message.
  All other fields from ex-data are preserved as additional error metadata.
  
  Throws ExceptionInfo if ex is not an ExceptionInfo or if ex-data does not
  contain :status."
  [ex]
  (when-not (instance? clojure.lang.ExceptionInfo ex)
    (throw (ex-info "Input must be an ExceptionInfo"
                    {:input ex
                     :type (type ex)})))
  (let [data (ex-data ex)
        status (:status data)]
    (when-not status
      (throw (ex-info "ExceptionInfo ex-data must contain :status"
                      {:ex-data data})))
    (let [title (.getMessage ex)
          extra-data (dissoc data :status :title)]
      (if (empty? extra-data)
        (->ErrorResponse status title)
        (merge (->ErrorResponse status title) extra-data)))))