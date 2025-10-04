(ns dicho.bridges.failjure
  "Bridge to integrate dicho with failjure library."
  (:require [failjure.core :as f]
            [dicho.types])
  (:import [dicho.types OkResponse ErrorResponse]))

(extend-type OkResponse
  f/HasFailed
  (failed? [_] false)
  (message [_] "Success"))

(extend-type ErrorResponse
  f/HasFailed
  (failed? [_] true)
  (message [this] (:title this)))