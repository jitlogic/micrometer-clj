(ns io.resonant.micrometer.cloudwatch
  (:require
    [io.resonant.micrometer :refer [create-registry to-duration]])
  (:import
    (io.micrometer.cloudwatch2 CloudWatchMeterRegistry CloudWatchConfig)
    (io.micrometer.core.instrument.push PushRegistryConfig)
    (io.micrometer.core.instrument Clock)))

(defmethod create-registry :cloudwatch [cfg]
  (CloudWatchMeterRegistry.
    (reify
      CloudWatchConfig
      (get [_ _] nil)
      (prefix [_] (:prefix cfg "cloudwatch"))
      (namespace [_] (:namespace cfg "namespace"))
      (^int batchSize [_] (min (:batch-size cfg 20) 20))
      PushRegistryConfig
      (step [_] (to-duration (:step cfg 60000)))
      (enabled [_] (:enabled? cfg true))
      (numThreads [_] (:num-threads cfg 2))
      (connectTimeout [_] (to-duration (:connect-timeout cfg 1000)))
      (readTimeout [_] (to-duration (:read-timeout cfg 10000))))
    (Clock/SYSTEM)
    (:cloudwatch-client cfg)))
