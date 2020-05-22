(ns io.resonant.micrometer.opentsdb
  (:require
    [io.resonant.micrometer :refer [create-registry to-duration]])
  (:import (io.micrometer.opentsdb OpenTSDBMeterRegistry OpenTSDBConfig)
           (io.micrometer.core.instrument Clock)
           (io.micrometer.core.instrument.push PushRegistryConfig)))

(defmethod create-registry :opentsdb [cfg]
  (OpenTSDBMeterRegistry.
    (reify
      OpenTSDBConfig
      (get [_ k] nil)
      (uri [_] (:url cfg "http://localhost:4242/api/put"))
      (userName [_] (:username cfg))
      (password [_] (:password cfg))
      PushRegistryConfig
      (step [_] (to-duration (:step cfg 60000)))
      (enabled [_] (:enabled? cfg true))
      (numThreads [_] (:num-threads cfg 2))
      (connectTimeout [_] (to-duration (:connect-timeout cfg 1000)))
      (readTimeout [_] (to-duration (:read-timeout cfg 10000)))
      (batchSize [_] (:batch-size cfg 10000)))
    (Clock/SYSTEM)))
