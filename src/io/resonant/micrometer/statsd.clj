(ns io.resonant.micrometer.statsd
  (:require
    [io.resonant.micrometer :refer [create-registry]])
  (:import
    (io.micrometer.statsd StatsdMeterRegistry StatsdConfig StatsdFlavor StatsdProtocol)
    (java.time Duration)
    (io.micrometer.core.instrument.util HierarchicalNameMapper)
    (io.micrometer.core.instrument Clock)))

(defmethod create-registry :statsd [cfg]
  {:config cfg,
   :type (:type cfg),
   :registry
   (StatsdMeterRegistry.
     (reify
       StatsdConfig
       (get [_ _] nil)
       (flavor [_] (StatsdFlavor/valueOf (.toUpperCase (name (:flavor cfg :DATADOG)))))
       (enabled [_] (:enabled? cfg true))
       (host [_] (:host cfg "localhost"))
       (port [_] (:port cfg 8125))
       (protocol [_] (StatsdProtocol/valueOf (.toUpperCase (name (:protocol cfg :UDP)))))
       (maxPacketLength [_] (:max-packet-length cfg 1400))
       (pollingFrequency [_] (Duration/ofMillis (:polling-frequency cfg 10000)))
       (queueSize [_] (:queue-size cfg Integer/MAX_VALUE))
       (step [_] (Duration/ofMillis (:step cfg 60000)))
       (publishUnchangedMeters [_] (:publish-unchanged-meters? cfg true))
       (buffered [_] (:buffered? cfg true)))
     (:hierarchical-name-mapper cfg (HierarchicalNameMapper/DEFAULT))
     (Clock/SYSTEM))})

