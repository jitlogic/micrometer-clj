(ns io.resonant.micrometer.kairos
  (:require
    [io.resonant.micrometer :refer [create-registry]])
  (:import (io.micrometer.kairos KairosMeterRegistry KairosConfig)
           (io.micrometer.core.instrument.step StepRegistryConfig)
           (java.time Duration)
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
      (step [_] (Duration/ofMillis (:step cfg 60000)))
      (enabled [_] (:enabled? cfg true))
      (numThreads [_] (:num-threads cfg 2))
      (connectTimeout [_] (Duration/ofMillis (:connect-timeout cfg 1000)))
      (readTimeout [_] (Duration/ofMillis (:read-timeout cfg 10000))))
    (Clock/SYSTEM)))
