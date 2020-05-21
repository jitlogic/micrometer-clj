(ns io.resonant.micrometer.wavefront
  (:require
    [io.resonant.micrometer :refer [create-registry]])
  (:import (io.micrometer.wavefront WavefrontMeterRegistry WavefrontConfig)
           (io.micrometer.core.instrument.push PushRegistryConfig)
           (java.time Duration)
           (io.micrometer.core.instrument Clock)))

(defmethod create-registry :wavefront [cfg]
  (WavefrontMeterRegistry.
    (reify
      WavefrontConfig
      (get [_ _] nil)
      (uri [_] (:url cfg "https://longboard.wavefront.com"))
      (source [_] (:source cfg "localhost"))
      (apiToken [_] (:api-token cfg))
      (reportMinuteDistribution [_] (:report-minute-distribution? cfg true))
      (reportHourDistribution [_] (:report-hour-distribution? cfg false))
      (reportDayDistribution [_] (:report-day-distribution? cfg false))
      PushRegistryConfig
      (step [_] (Duration/ofMillis (:step cfg 60000)))
      (enabled [_] (:enabled? cfg true))
      (numThreads [_] (:num-threads cfg 2))
      (connectTimeout [_] (Duration/ofMillis (:connect-timeout cfg 1000)))
      (readTimeout [_] (Duration/ofMillis (:read-timeout cfg 10000))))
    (Clock/SYSTEM)))
