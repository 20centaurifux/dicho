(ns dicho.utils
  (:require [clojure.spec.alpha :as s]))

(defn validate!
  "Validates that value conforms to spec. Throws ex-info if not valid. Returns
   `value` otherwise.
  
  Parameters:
  - value: The value to validate
  - spec: The spec to validate against"
  [value spec]
  (when-not (s/valid? spec value)
    (throw (ex-info (str "Invalid value for spec " spec)
                    {:arg value
                     :explain (s/explain-data spec value)})))
  value)