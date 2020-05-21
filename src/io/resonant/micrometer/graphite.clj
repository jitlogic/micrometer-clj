(ns io.resonant.micrometer.graphite
  (:require
    [io.resonant.micrometer :refer [create-registry]])
  (:import (io.micrometer.graphite GraphiteMeterRegistry GraphiteConfig GraphiteProtocol)
           (io.micrometer.core.instrument Clock)
           (java.util.concurrent TimeUnit)
           (io.micrometer.core.instrument.dropwizard DropwizardConfig)
           (java.time Duration)))

(defmethod create-registry :graphite [cfg]
  {:config cfg,
   :type (:type cfg),
   :registry
   (GraphiteMeterRegistry.
     (reify
       GraphiteConfig
       (get [_ _] nil)
       (graphiteTagsEnabled [_] (:graphite-tags-enabled? cfg true))
       (tagsAsPrefix [_] (into-array String (:tags-as-prefix cfg [])))
       (rateUnits [_] (TimeUnit/valueOf (.toUpperCase (name (:rate-units cfg :seconds)))))
       (durationUnits [_] (TimeUnit/valueOf (.toUpperCase (name (:duration-units cfg :milliseconds)))))
       (host [_] (:host cfg "localhost"))
       (port [_] (:port cfg 2004))
       (enabled [_] (:enabled? cfg true))
       (protocol [_] (GraphiteProtocol/valueOf (.toUpperCase (name (:protocol cfg :PICKLED)))))
       DropwizardConfig
       (step [_] (Duration/ofMillis (:step cfg 60000))))
     (Clock/SYSTEM))})

