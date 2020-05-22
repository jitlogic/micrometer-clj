(ns io.resonant.micrometer.prometheus
  (:require
    [io.resonant.micrometer :refer [create-registry scrape to-duration]])
  (:import
    (io.micrometer.prometheus PrometheusConfig PrometheusMeterRegistry)))

(defmethod create-registry :prometheus [cfg]
  (PrometheusMeterRegistry.
    (reify PrometheusConfig
      (get [_ k] nil)
      (prefix [_] (:prefix cfg "resonant.metrics"))
      (step [_#] (to-duration (:step cfg 60000))))))

(defmethod scrape :prometheus [{:keys [^PrometheusMeterRegistry registry]}]
  (.scrape registry))

