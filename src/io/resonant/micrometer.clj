(ns io.resonant.micrometer
  (:import
    (java.time Duration)
    (java.util.function Supplier)
    (io.micrometer.core.instrument.composite CompositeMeterRegistry)
    (io.micrometer.core.instrument.simple SimpleMeterRegistry)
    (io.micrometer.core.instrument Clock Timer MeterRegistry Tag Counter Gauge)
    (io.micrometer.core.instrument.binder.jvm ClassLoaderMetrics JvmMemoryMetrics JvmGcMetrics JvmThreadMetrics JvmCompilationMetrics JvmHeapPressureMetrics)
    (io.micrometer.core.instrument.binder.system FileDescriptorMetrics ProcessorMetrics UptimeMetrics)))


(defn- found? [name]
  (try
    (Class/forName name true (.getContextClassLoader (Thread/currentThread)))
    true
    (catch ClassNotFoundException _ false)))


(defn to-string [v]
  (cond
    (keyword? v) (name v)
    (string? v) v
    :else (str v)))


(defn to-tags [tags]
  (for [[k v] tags] (Tag/of (to-string k) (to-string v))))


(defmulti create-registry "Returns raw meter registry object from micrometer library." :type)


(defmethod create-registry :simple [_]
  (SimpleMeterRegistry.))


(defmethod create-registry :composite [{:keys [configs]}]
  (cond
    (= 0 (count configs)) (throw (ex-info "Cannot create empty composite registry" {}))
    (= 1 (count configs)) (create-registry (first configs))
    :else (CompositeMeterRegistry. Clock/SYSTEM (map create-registry configs))))


(defmethod create-registry :prometheus [cfg]
  (when-not (found? "io.micrometer.prometheus.PrometheusMeterRegistry")
    (throw (ex-info "Missing jar dependency: io.micrometer/micrometer-registry-prometheus" {:cfg cfg})))
  (eval
    `(do
       (let [config#
             (reify io.micrometer.prometheus.PrometheusConfig
               (get [_# k#] nil)
               (prefix [_#] ~(:prefix cfg "resonant.metrics"))
               (step [_#] (Duration/ofMillis ~(:step cfg 60000))))]
         (io.micrometer.prometheus.PrometheusMeterRegistry. config#)))))



(defmethod create-registry :default [cfg]
  (throw (ex-info "Invalid metrics registry type" {:cfg cfg})))


(def DEFAULT-JVM-METRICS [:classes :memory :gc :threads :jit :heap])


(defn setup-jvm-metrics [{:keys [registry tags] :as metrics} jvm-metrics]
  (doseq [metric jvm-metrics]
    (case metric
      :classes (.bindTo (ClassLoaderMetrics. (to-tags tags)) registry)
      :memory (.bindTo (JvmMemoryMetrics. (to-tags tags)) registry)
      :gc (.bindTo (JvmGcMetrics. (to-tags tags)) registry)
      :threads (.bindTo (JvmThreadMetrics. (to-tags tags)) registry)
      :jit (.bindTo (JvmCompilationMetrics. (to-tags tags)) registry)
      :heap (.bindTo (JvmHeapPressureMetrics. (to-tags tags) (Duration/ofMinutes 5) (Duration/ofMinutes 5)) registry)
      (throw (ex-info "Illegal JVM metric" {:metric metric, :allowed DEFAULT-JVM-METRICS})))))


(def DEFAULT-OS-METRICS [:files :cpu :uptime])


(defn setup-os-metrics [{:keys [registry tags]} os-metrics]
  (doseq [metric os-metrics]
    (case metric
      :files (.bindTo (FileDescriptorMetrics. (to-tags tags)) registry)
      :cpu (.bindTo (ProcessorMetrics. (to-tags tags)) registry)
      :uptime (.bindTo (UptimeMetrics. (to-tags tags)) registry)
      (throw (ex-info "Illegal OS metric" {:metric metric, :allowed DEFAULT-OS-METRICS})))))


(defn metrics [cfg]
  (doto
    {:metrics  (atom {})
     :tags     (:tags cfg {})
     :registry (create-registry cfg)}
    (setup-os-metrics (:os-metrics cfg DEFAULT-OS-METRICS))
    (setup-jvm-metrics (:jvm-metrics cfg DEFAULT-JVM-METRICS))))


(defn get-timer [{:keys [metrics ^MeterRegistry registry] :as m} name tags]
  (let [timer (get-in @metrics [name tags])]
    (cond
      (instance? Timer timer) timer
      (some? timer) (throw (ex-info "Metric already registered and is not a timer" {:name name, :tags tags, :metric timer}))
      :else
      (let [timer (.timer registry name ^Iterable (for [[k v] (merge (:tags m {}) tags)] (Tag/of (to-string k) (to-string v))))]
        (swap! metrics assoc-in [name tags] timer)
        timer))))


(defmacro with-timer [^Timer timer & body]
  `(.record ^Timer ~timer ^Runnable (fn [] ~@body)))


(defmacro timed [metrics name tags & body]
  `(let [timer# (get-timer ~metrics ~name ~tags)]
     (.record ^Timer timer# ^Runnable (fn [] ~@body))))


(defn get-counter [{:keys [metrics ^MeterRegistry registry] :as m} name tags]
  (let [counter (get-in @metrics [name tags])]
    (cond
      (instance? Counter counter) counter
      (some? counter) (throw (ex-info "Metric already registered and is not a counter" {:name name, :tags tags, :metric counter}))
      :else
      (let [counter (.counter registry name ^Iterable (for [[k v] (merge (:tags m {}) tags)] (Tag/of (to-string k) (to-string v))))]
        (swap! metrics assoc-in [name tags] counter)
        counter))))


(defn add
  ([^Counter counter]
   (.increment counter))
  ([^Counter counter n]
   (.increment counter n))
  ([metrics name tags]
   (add metrics name tags 1.0))
  ([metrics name tags n]
   (let [counter (get-counter metrics name tags)]
     (.increment counter n))))


(defn get-gauge [{:keys [metrics ^MeterRegistry registry] :as m} name tags gfn]
  (let [gauge (get-in @metrics [name tags])]
    (cond
      (instance? Gauge gauge) gauge
      (some? gauge) (throw (ex-info "Metric already registered and is not a gauge" {:name name, :tags tags, :metric gauge}))
      :else
      (let [supplier (reify Supplier (get [_] (gfn))),
            tags ^Iterable (for [[k v] (merge (:tags m {}) tags)] (Tag/of (to-string k) (to-string v)))
            gauge (.register (.tags (Gauge/builder name supplier) tags) registry)]
        (swap! metrics assoc-in [name tags] gauge)
        gauge))))


(defmacro defgauge [metrics name tags & body]
  `(get-gauge ~metrics ~name ~tags (fn [] ~@body)))


