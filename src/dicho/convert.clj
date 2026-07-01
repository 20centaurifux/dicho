(ns dicho.convert
  "Conversion utilities for dicho response types.
  
  This namespace provides functions for converting between dicho response
  records and other data structures like maps and exceptions. Useful for
  serialization, deserialization, and interop with other systems."
  (:require [dicho.types :refer [->ErrorResponse map->OkResponse map->ErrorResponse]]
            [dicho.specs :as specs]
            [dicho.utils :refer [validate!]]))

(defn response->map
  "Converts a dicho response record to a plain map.
  
  Throws ExceptionInfo if response does not conform to :dicho.specs/response."
  [response]
  (as-> response r
    (validate! r ::specs/response)
    (into {} r)))

(defn map->response
  "Reconstructs a dicho response record from a plain map.
  
  Throws ExceptionInfo if m is not a map, does not contain :status, or the
  resulting response does not conform to :dicho.specs/response."
  [m]
  (when-not (map? m)
    (throw (ex-info "Input must be a map"
                    {:input m
                     :type (type m)})))
  (when-not (contains? m :status)
    (throw (ex-info "Map must contain :status key"
                    {:arg m})))
  (let [response (if (= :ok (:status m))
                   (map->OkResponse m)
                   (map->ErrorResponse m))]
    (-> response
        (validate! ::specs/response))))

(defn response->ex-info
  "Converts an ErrorResponse to an ex-info exception.
  
  The :title field becomes the exception message, and all other fields
  (including :status, :detail, :retry?, etc.) are preserved in ex-data.
  The :title is removed from ex-data to avoid duplication since it's
  accessible via (.getMessage ex).
  
  Throws ExceptionInfo if error-response does not conform to :dicho.specs/error."
  [error-response]
  (as-> error-response err
    (validate! err ::specs/error)
    (ex-info (:title err) (dissoc err :title))))

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
    (-> (merge (->ErrorResponse status (.getMessage ex))
               (dissoc data :status :title))
        (validate! ::specs/error))))