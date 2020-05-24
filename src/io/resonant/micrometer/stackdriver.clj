(ns io.resonant.micrometer.stackdriver
  (:require
    [io.resonant.micrometer :refer [create-registry to-duration]]
    [clojure.java.io :as io])
  (:import
    (io.micrometer.stackdriver StackdriverMeterRegistry StackdriverConfig)
    (com.google.auth.oauth2 GoogleCredentials)
    (com.google.api.gax.core FixedCredentialsProvider)
    (com.google.cloud.monitoring.v3 MetricServiceSettings)
    (io.micrometer.core.instrument.step StepRegistryConfig)
    (io.micrometer.core.instrument Clock)))

(defn- read-credentials [path]
  (when (nil? path)
    (throw (ex-info "missing :credentials argument" {})))
  (FixedCredentialsProvider/create
    (.createScoped (GoogleCredentials/fromStream (io/input-stream path))
      (MetricServiceSettings/getDefaultServiceScopes))))

(defmethod create-registry :stackdriver [cfg]
  (let [creds (read-credentials (:credentials cfg))]
    (StackdriverMeterRegistry.
      (reify
        StackdriverConfig
        (get [_ _] nil)
        (projectId [_] (:project-id cfg))
        (resourceLabels [_] (:resource-labels cfg {}))
        (resourceType [_] (:resource-type cfg "global"))
        (credentials [_] creds)
        StepRegistryConfig
        (step [_] (to-duration (:step cfg 60000)))
        (enabled [_] (:enabled? cfg true))
        (numThreads [_] (:num-threads cfg 2))
        (connectTimeout [_] (to-duration (:connect-timeout cfg 1000)))
        (readTimeout [_] (to-duration (:read-timeout cfg 10000)))
        (batchSize [_] (:batch-size cfg 10000)))
      (Clock/SYSTEM))))
