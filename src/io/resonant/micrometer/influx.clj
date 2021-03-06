(ns io.resonant.micrometer.influx
  (:require
    [io.resonant.micrometer :refer [create-registry to-duration]])
  (:import (io.micrometer.influx InfluxMeterRegistry InfluxConfig InfluxConsistency)
           (io.micrometer.core.instrument Clock)
           (io.micrometer.core.instrument.push PushRegistryConfig)))

(defmethod create-registry :influx [cfg]
  (InfluxMeterRegistry.
    (reify
      InfluxConfig
      (db [_] (:db cfg "metrics"))
      (consistency [_] (InfluxConsistency/valueOf (.toUpperCase (name (:consistency cfg :ONE)))))
      (userName [_] (:username cfg "metrics"))
      (password [_] (:password cfg "metrics"))
      (retentionPolicy [_] (:retention-policy cfg))
      (retentionDuration [_] (:retention-duration cfg))
      (retentionReplicationFactor [_] (:retention-replication-factor cfg))
      (retentionShardDuration [_] (:retention-shard-duration cfg))
      (uri [_] (:url cfg "http://localhost:8086"))
      (compressed [_] (:compressed? cfg true))
      (autoCreateDb [_] (:auto-create-db? cfg true))
      PushRegistryConfig
      (step [_] (to-duration (:step cfg 60000)))
      (enabled [_] (:enabled? cfg true))
      (numThreads [_] (:num-threads cfg 2))
      (connectTimeout [_] (to-duration (:connect-timeout cfg 1000)))
      (readTimeout [_] (to-duration (:read-timeout cfg 10000)))
      (batchSize [_] (:batch-size cfg 10000)))
    (Clock/SYSTEM)))
