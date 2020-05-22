(ns io.resonant.micrometer.signalfx
  (:require
    [io.resonant.micrometer :refer [create-registry to-duration]])
  (:import (io.micrometer.signalfx SignalFxMeterRegistry SignalFxConfig)
           (io.micrometer.core.instrument.step StepRegistryConfig)
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
      (step [_] (to-duration (:step cfg 60000)))
      (enabled [_] (:enabled? cfg true))
      (numThreads [_] (:num-threads cfg 2))
      (connectTimeout [_] (to-duration (:connect-timeout cfg 1000)))
      (readTimeout [_] (to-duration (:read-timeout cfg 10000))))
    (Clock/SYSTEM)))
