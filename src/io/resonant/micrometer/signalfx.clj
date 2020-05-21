(ns io.resonant.micrometer.signalfx
  (:require
    [io.resonant.micrometer :refer [create-registry]])
  (:import (io.micrometer.signalfx SignalFxMeterRegistry SignalFxConfig)
           (io.micrometer.core.instrument.step StepRegistryConfig)
           (java.time Duration)
           (io.micrometer.core.instrument Clock)))

(defmethod create-registry :signalfx [cfg]
  (SignalFxMeterRegistry.
    (reify
      SignalFxConfig
      (get [_ _] nil)
      (accessToken [_] (:access-token cfg))
      (uri [_] (:url cfg "https://ingest.signalfx.com"))
      (source [_] (:source cfg "clojure"))
      StepRegistryConfig
      (step [_] (Duration/ofMillis (:step cfg 60000)))
      (enabled [_] (:enabled? cfg true))
      (numThreads [_] (:num-threads cfg 2))
      (connectTimeout [_] (Duration/ofMillis (:connect-timeout cfg 1000)))
      (readTimeout [_] (Duration/ofMillis (:read-timeout cfg 10000))))
    (Clock/SYSTEM)))

