(ns io.resonant.micrometer.appoptics
  (:require
    [io.resonant.micrometer :refer [create-registry]])
  (:import (io.micrometer.appoptics AppOpticsMeterRegistry AppOpticsConfig)
           (io.micrometer.core.instrument.step StepRegistryConfig)
           (java.time Duration)
           (io.micrometer.core.instrument Clock)))

(defmethod create-registry :appoptics [cfg]
  (AppOpticsMeterRegistry.
    (reify
      AppOpticsConfig
      (get [_ _] nil)
      (apiToken [_] (:api-token cfg))
      (hostTag [_] (:host-tag cfg "instance"))
      (uri [_] (:url cfg "https://api.appoptics.com/v1/measurements"))
      (floorTimes [_] (:floor-times? cfg false))
      (batchSize [_] (min (:batch-size cfg 500) 1000))
      StepRegistryConfig
      (step [_] (Duration/ofMillis (:step cfg 60000)))
      (enabled [_] (:enabled? cfg true))
      (numThreads [_] (:num-threads cfg 2))
      (connectTimeout [_] (Duration/ofMillis (:connect-timeout cfg 1000)))
      (readTimeout [_] (Duration/ofMillis (:read-timeout cfg 10000))))
    (Clock/SYSTEM)))
