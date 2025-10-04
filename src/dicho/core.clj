(ns dicho.core
  "Core functions for creating standardized success and error responses."
  (:require [clojure.spec.alpha :as s]
            [dicho.types :refer [->OkResponse ->ErrorResponse]]
            [dicho.specs :as specs])
  (:import [dicho.types OkResponse ErrorResponse]))

(defn ok?
  "Returns true if x is a valid OkResponse."
  [x]
  (s/valid? ::specs/ok x))

(defn error?
  "Returns true if x is a valid ErrorResponse."
  [x]
  (s/valid? ::specs/error x))

(defn ok
  "Creates a success response"
  ([v]
   {:post [(s/valid? ::specs/ok %)]}
   (->OkResponse :ok v))
  ([v extra]
   {:pre [(map? extra)]
    :post [(s/valid? ::specs/ok %)]}
   (merge (->OkResponse :ok v) extra)))

(defn err
  "Creates an error response"
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