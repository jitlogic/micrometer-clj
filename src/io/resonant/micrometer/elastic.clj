(ns io.resonant.micrometer.elastic
  (:require
    [io.resonant.micrometer :refer [create-registry]])
  (:import (io.micrometer.elastic ElasticMeterRegistry ElasticConfig)
           (io.micrometer.core.instrument Clock)))

(defmethod create-registry :elastic [cfg]
  {:config cfg,
   :type :elastic,
   :registry
   (ElasticMeterRegistry.
     (reify ElasticConfig
       (get [_ k] nil)
       (host [_] (:url cfg "http://localhost:9200"))
       (index [_] (:index cfg "metrics"))
       (indexDateFormat [_] (:index-date-format cfg "yyyy-MM"))
       (timestampFieldName [_] (:timestamp-field-name cfg "@timestamp"))
       (autoCreateIndex [_] (:auto-create-index cfg true))
       (userName [_] (:username cfg ""))
       (password [_] (:password cfg ""))
       (pipeline [_] (:pipeline cfg ""))
       (indexDateSeparator [_] (:index-date-separator cfg "-"))
       (documentType [_] (:documentType cfg "doc")))
     (Clock/SYSTEM))})
