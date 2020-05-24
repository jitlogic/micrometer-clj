(ns io.resonant.micrometer.dynatrace
  (:require
    [io.resonant.micrometer :refer [create-registry to-duration]])
  (:import
    (io.micrometer.dynatrace DynatraceMeterRegistry DynatraceConfig)
    (io.micrometer.core.instrument.step StepRegistryConfig)
    (io.micrometer.core.instrument Clock)))

(defmethod create-registry :dynatrace [cfg]
  (DynatraceMeterRegistry.
    (reify
      DynatraceConfig
      (get [_ _] nil)
      (apiToken [_] (:api-token cfg))
      (uri [_] (:url cfg))
      (deviceId [_] (:device-id cfg))
      (technologyType [_] (:technology-type cfg "clojure"))
      (group [_] (:group cfg))
      StepRegistryConfig
      (step [_] (to-duration (:step cfg 60000)))
      (enabled [_] (:enabled? cfg true))
      (numThreads [_] (:num-threads cfg 2))
      (connectTimeout [_] (to-duration (:connect-timeout cfg 1000)))
      (readTimeout [_] (to-duration (:read-timeout cfg 10000)))
      (batchSize [_] (:batch-size cfg 10000)))
    (Clock/SYSTEM)))
