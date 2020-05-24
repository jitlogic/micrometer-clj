(ns io.resonant.micrometer.newrelic
  (:require
    [io.resonant.micrometer :refer [create-registry to-duration]])
  (:import (io.micrometer.newrelic NewRelicMeterRegistry NewRelicConfig ClientProviderType)
           (io.micrometer.core.instrument.step StepRegistryConfig)
           (io.micrometer.core.instrument Clock)))

(defmethod create-registry :newrelic [cfg]
  (NewRelicMeterRegistry.
    (reify
      NewRelicConfig
      (get [_ _] nil)
      (meterNameEventTypeEnabled [_] (:meter-name-event-type-enabled? cfg false))
      (eventType [_] (:event-type cfg "MicrometerSample"))
      (clientProviderType [_] (ClientProviderType/valueOf (.toUpperCase (name (:client-provider-type cfg :INSIGHTS_API)))))
      (apiKey [_] (:api-key cfg))
      (accountId [_] (:account-id cfg))
      (uri [_] (:url cfg "https://insights-collector.newrelic.com"))
      StepRegistryConfig
      (step [_] (to-duration (:step cfg 60000)))
      (enabled [_] (:enabled? cfg true))
      (numThreads [_] (:num-threads cfg 2))
      (connectTimeout [_] (to-duration (:connect-timeout cfg 1000)))
      (readTimeout [_] (to-duration (:read-timeout cfg 10000)))
      (batchSize [_] (:batch-size cfg 10000)))
    (Clock/SYSTEM)))
