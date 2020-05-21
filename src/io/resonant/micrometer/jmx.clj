(ns io.resonant.micrometer.jmx
  (:require
    [io.resonant.micrometer :refer [create-registry]])
  (:import
    (io.micrometer.jmx JmxMeterRegistry JmxConfig)
    (io.micrometer.core.instrument.dropwizard DropwizardConfig)
    (java.time Duration)
    (io.micrometer.core.instrument Clock)))

(defmethod create-registry :jmx [cfg]
  {:config cfg,
   :type :elastic,
   :registry
   (JmxMeterRegistry.
     (reify
       JmxConfig
       (get [_ _] nil)
       (domain [_] (:domain cfg))
       DropwizardConfig
       (step [_] (Duration/ofMillis (:step cfg 60000))))
     (Clock/SYSTEM))})
