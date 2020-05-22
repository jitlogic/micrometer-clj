(ns io.resonant.micrometer.jmx
  (:require
    [io.resonant.micrometer :refer [create-registry to-duration]])
  (:import
    (io.micrometer.jmx JmxMeterRegistry JmxConfig)
    (io.micrometer.core.instrument.dropwizard DropwizardConfig)
    (io.micrometer.core.instrument Clock)))

(defmethod create-registry :jmx [cfg]
  (JmxMeterRegistry.
    (reify
      JmxConfig
      (get [_ _] nil)
      (domain [_] (:domain cfg))
      DropwizardConfig
      (step [_] (to-duration (:step cfg 60000))))
    (Clock/SYSTEM)))
