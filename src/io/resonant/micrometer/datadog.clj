(ns io.resonant.micrometer.datadog
  (:require
    [io.resonant.micrometer :refer [create-registry to-duration]])
  (:import (io.micrometer.datadog DatadogMeterRegistry DatadogConfig)
           (io.micrometer.core.instrument.step StepRegistryConfig)
           (io.micrometer.core.instrument Clock)))

(defmethod create-registry :datadog [cfg]
  (DatadogMeterRegistry.
    (reify
      DatadogConfig
      (get [_ _] nil)
      (apiKey [_] (:api-key cfg))
      (applicationKey [_] (:application-key cfg))
      (hostTag [_] (:host-tag cfg "instance"))
      (uri [_] (:url cfg "https://api.datadoghq.com"))
      (descriptions [_] (:descriptions cfg true))
      StepRegistryConfig
      (step [_] (to-duration (:step cfg 60000)))
      (enabled [_] (:enabled? cfg true))
      (numThreads [_] (:num-threads cfg 2))
      (connectTimeout [_] (to-duration (:connect-timeout cfg 1000)))
      (readTimeout [_] (to-duration (:read-timeout cfg 10000))))
    (Clock/SYSTEM)))

