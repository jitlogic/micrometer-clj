(ns io.resonant.micrometer.prometheus
  (:require
    [io.resonant.micrometer :refer [create-registry]])
  (:import
    (io.micrometer.prometheus PrometheusConfig PrometheusMeterRegistry)
    (java.time Duration)))


(defmethod create-registry :prometheus [cfg]
  {:config cfg,
   :type :prometheus,
   :registry
   (PrometheusMeterRegistry.
     (reify PrometheusConfig
       (get [_ k] nil)
       (prefix [_] (:prefix cfg "resonant.metrics"))
       (step [_#] (Duration/ofMillis (:step cfg 60000)))))})

