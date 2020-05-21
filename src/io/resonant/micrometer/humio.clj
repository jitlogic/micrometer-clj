(ns io.resonant.micrometer.humio
  (:require
    [io.resonant.micrometer :refer [create-registry]])
  (:import (io.micrometer.humio HumioMeterRegistry HumioConfig)
           (io.micrometer.core.instrument.step StepRegistryConfig)
           (java.time Duration)
           (io.micrometer.core.instrument Clock)))

(defmethod create-registry :humio [cfg]
  (HumioMeterRegistry.
    (reify
      HumioConfig
      (get [_ _] nil)
      (uri [_] (:url cfg "https://cloud.humio.com"))
      (tags [_] (:tags cfg))
      (apiToken [_] (:api-token cfg))
      StepRegistryConfig
      (step [_] (Duration/ofMillis (:step cfg 60000)))
      (enabled [_] (:enabled? cfg true))
      (numThreads [_] (:num-threads cfg 2))
      (connectTimeout [_] (Duration/ofMillis (:connect-timeout cfg 1000)))
      (readTimeout [_] (Duration/ofMillis (:read-timeout cfg 10000))))
    (Clock/SYSTEM)))
