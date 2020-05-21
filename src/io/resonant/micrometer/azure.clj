(ns io.resonant.micrometer.azure
  (:require
    [io.resonant.micrometer :refer [create-registry]])
  (:import
    (io.micrometer.azuremonitor AzureMonitorMeterRegistry AzureMonitorConfig)
    (io.micrometer.core.instrument.push PushRegistryConfig)
    (java.time Duration)
    (io.micrometer.core.instrument Clock)))

(defmethod create-registry :azure [cfg]
  (AzureMonitorMeterRegistry.
    (reify
      AzureMonitorConfig
      (get [_ _] nil)
      (prefix [_] (:prefix cfg "azuremonitor"))
      (instrumentationKey [_] (:instrumentation-key cfg "instrumentationkey"))
      PushRegistryConfig
      (step [_] (Duration/ofMillis (:step cfg 60000)))
      (enabled [_] (:enabled? cfg true))
      (numThreads [_] (:num-threads cfg 2))
      (connectTimeout [_] (Duration/ofMillis (:connect-timeout cfg 1000)))
      (readTimeout [_] (Duration/ofMillis (:read-timeout cfg 10000)))
      (batchSize [_] (:batch-size cfg 10000)))
    (Clock/SYSTEM)))
