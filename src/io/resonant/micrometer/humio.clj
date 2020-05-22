(ns io.resonant.micrometer.humio
  (:require
    [io.resonant.micrometer :refer [create-registry to-duration]])
  (:import (io.micrometer.humio HumioMeterRegistry HumioConfig)
           (io.micrometer.core.instrument.step StepRegistryConfig)
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
      (step [_] (to-duration (:step cfg 60000)))
      (enabled [_] (:enabled? cfg true))
      (numThreads [_] (:num-threads cfg 2))
      (connectTimeout [_] (to-duration (:connect-timeout cfg 1000)))
      (readTimeout [_] (to-duration (:read-timeout cfg 10000))))
    (Clock/SYSTEM)))
