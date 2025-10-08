(ns dicho.convert
  "Conversion utilities for dicho response types.
  
  This namespace provides functions for converting between dicho response
  records and other data structures like maps and exceptions. Useful for
  serialization, deserialization, and interop with other systems."
  (:require [dicho.types :refer [->ErrorResponse map->OkResponse map->ErrorResponse]]
            [dicho.specs :as specs]
            [clojure.spec.alpha :as s]))

(defn response->map
  "Converts a dicho response record to a plain map."
  [response]
  {:pre [(s/valid? ::specs/response response)]}
  (into {} response))

(defn map->response
  "Reconstructs a dicho response record from a plain map."
  [m]
  {:pre [(map? m) (contains? m :status)]}
  (let [response (if (= :ok (:status m))
                   (map->OkResponse m)
                   (map->ErrorResponse m))]
    (when-not (s/valid? ::specs/response response)
      (throw (ex-info "Response does not conform ok nor error spec."
                      {:response response
                       :explain (s/explain-data ::specs/response response)})))
    response))

(defn response->ex-info
  "Converts an ErrorResponse to an ex-info exception."
  [error-response]
  {:pre [#(s/valid? ::specs/error %)]}
  (ex-info (:title error-response)
           (dissoc error-response :title)))

(defn ex-info->response
  "Converts an ExceptionInfo exception to an ErrorResponse."
  [ex]
  {:pre [(instance? clojure.lang.ExceptionInfo ex)]
   :post [(s/valid? ::specs/error %)]}
  (let [data (ex-data ex)
        status (:status data)
        title (.getMessage ex)
        extra-data (dissoc data :status :title)]
    (if (empty? extra-data)
      (->ErrorResponse status title)
      (merge (->ErrorResponse status title) extra-data))))