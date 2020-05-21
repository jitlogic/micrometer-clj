(ns io.resonant.micrometer.datadog
  (:require
    [io.resonant.micrometer :refer [create-registry]])
  (:import (io.micrometer.datadog DatadogMeterRegistry DatadogConfig)
           (io.micrometer.core.instrument.step StepRegistryConfig)
           (java.time Duration)
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
      (step [_] (Duration/ofMillis (:step cfg 60000)))
      (enabled [_] (:enabled? cfg true))
      (numThreads [_] (:num-threads cfg 2))
      (connectTimeout [_] (Duration/ofMillis (:connect-timeout cfg 1000)))
      (readTimeout [_] (Duration/ofMillis (:read-timeout cfg 10000))))
    (Clock/SYSTEM)))

