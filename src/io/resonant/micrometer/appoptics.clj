(ns io.resonant.micrometer.appoptics
  (:require
    [io.resonant.micrometer :refer [create-registry to-duration]])
  (:import (io.micrometer.appoptics AppOpticsMeterRegistry AppOpticsConfig)
           (io.micrometer.core.instrument.step StepRegistryConfig)
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
      StepRegistryConfig
      (step [_] (to-duration (:step cfg 60000)))
      (enabled [_] (:enabled? cfg true))
      (numThreads [_] (:num-threads cfg 2))
      (connectTimeout [_] (to-duration (:connect-timeout cfg 1000)))
      (readTimeout [_] (to-duration (:read-timeout cfg 10000)))
      (batchSize [_] (:batch-size cfg 1000)))
    (Clock/SYSTEM)))
