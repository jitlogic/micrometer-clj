(ns io.resonant.micrometer.opentsdb
  (:require
    [io.resonant.micrometer :refer [create-registry]])
  (:import (io.micrometer.opentsdb OpenTSDBMeterRegistry OpenTSDBConfig)
           (io.micrometer.core.instrument Clock)
           (io.micrometer.core.instrument.push PushRegistryConfig)
           (java.time Duration)))

(defmethod create-registry :opentsdb [cfg]
  {:config cfg,
   :type :elastic,
   :registry
   (OpenTSDBMeterRegistry.
     (reify
       OpenTSDBConfig
       (get [_ k] nil)
       (uri [_] (:url cfg "http://localhost:4242/api/put"))
       (userName [_] (:username cfg))
       (password [_] (:password cfg))
       PushRegistryConfig
       (step [_] (Duration/ofMillis (:step cfg 60000)))
       (enabled [_] (:enabled cfg true))
       (numThreads [_] (:num-threads cfg 2))
       (connectTimeout [_] (Duration/ofMillis (:connect-timeout cfg 1000)))
       (readTimeout [_] (Duration/ofMillis (:read-timeout cfg 10000)))
       (batchSize [_] (:batch-size cfg 10000)))
     (Clock/SYSTEM))})
