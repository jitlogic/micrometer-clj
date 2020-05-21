(ns io.resonant.micrometer.dynatrace
  (:require
    [io.resonant.micrometer :refer [create-registry]])
  (:import
    (io.micrometer.dynatrace DynatraceMeterRegistry DynatraceConfig)
    (io.micrometer.core.instrument.step StepRegistryConfig)
    (java.time Duration)
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
      (step [_] (Duration/ofMillis (:step cfg 60000)))
      (enabled [_] (:enabled? cfg true))
      (numThreads [_] (:num-threads cfg 2))
      (connectTimeout [_] (Duration/ofMillis (:connect-timeout cfg 1000)))
      (readTimeout [_] (Duration/ofMillis (:read-timeout cfg 10000))))
    (Clock/SYSTEM)))
