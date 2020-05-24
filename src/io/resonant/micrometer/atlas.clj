(ns io.resonant.micrometer.atlas
  (:require
    [io.resonant.micrometer :refer [create-registry to-duration]])
  (:import (io.micrometer.atlas AtlasMeterRegistry)
           (com.netflix.spectator.api RegistryConfig)
           (com.netflix.spectator.atlas AtlasConfig)))

(defmethod create-registry :atlas [cfg]
  (AtlasMeterRegistry.
    (reify
      AtlasConfig
      (step [_] (to-duration (:step cfg 60000)))
      (meterTTL [_] (to-duration (:meter-ttl cfg 900000)))
      (enabled [_] (:enabled? cfg true))
      (autoStart [_] (:auto-start? cfg true))
      (numThreads [_] (:num-threads cfg 4))
      (uri [_] (:url cfg "http://localhost:7101/api/v1/publish"))
      (lwcStep [_] (to-duration (:lwc-step cfg 5000)))
      (lwcEnabled [_] (:lwc-enabled? cfg false))
      (lwcIgnorePublishStep [_] (:lwc-ignore-publish-step? cfg true))
      (configRefreshFrequency [_] (to-duration (:config-refresh-frequency cfg 10000)))
      (configTTL [_] (to-duration (:config-ttl cfg 150000)))
      (configUri [_] (:config-url cfg "http://localhost:7101/lwc/api/v1/expressions/local-dev"))
      (evalUri [_] (:eval-url cfg "http://localhost:7101/lwc/api/v1/evaluate"))
      (connectTimeout [_] (to-duration (:connect-timeout cfg 1000)))
      (readTimeout [_] (to-duration (:read-timeout cfg 10000)))
      (batchSize [_] (:batch-size cfg 10000))
      (commonTags [_] (:common-tags cfg {}))
      (validTagCharacters [_] (:valid-tag-characters cfg "-._A-Za-z0-9~^"))
      RegistryConfig
      (get [_ _] nil)
      (propagateWarnings [_] (:propagate-warnings? cfg false))
      (maxNumberOfMeters [_] (:max-atlas-meters cfg Integer/MAX_VALUE))
      (gaugePollingFrequency [_] (to-duration (:gauge-polling-frequency cfg 10000))))))

