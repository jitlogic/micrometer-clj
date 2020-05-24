(ns io.resonant.micrometer.kairos
  (:require
    [io.resonant.micrometer :refer [create-registry to-duration]])
  (:import (io.micrometer.kairos KairosMeterRegistry KairosConfig)
           (io.micrometer.core.instrument.step StepRegistryConfig)
           (io.micrometer.core.instrument Clock)))

(defmethod create-registry :kairos [cfg]
  (KairosMeterRegistry.
    (reify
      KairosConfig
      (get [_ _] nil)
      (uri [_] (:url cfg "http://localhost:8080/api/v1/datapoints"))
      (userName [_] (:username cfg))
      (password [_] (:password cfg))
      StepRegistryConfig
      (step [_] (to-duration (:step cfg 60000)))
      (enabled [_] (:enabled? cfg true))
      (numThreads [_] (:num-threads cfg 2))
      (connectTimeout [_] (to-duration (:connect-timeout cfg 1000)))
      (readTimeout [_] (to-duration (:read-timeout cfg 10000)))
      (batchSize [_] (:batch-size cfg 10000)))
    (Clock/SYSTEM)))
