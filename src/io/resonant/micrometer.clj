(ns io.resonant.micrometer
  (:import
    (java.util.function Supplier)
    (io.micrometer.core.instrument.composite CompositeMeterRegistry)
    (io.micrometer.core.instrument.simple SimpleMeterRegistry)
    (io.micrometer.core.instrument Clock Timer MeterRegistry Tag Counter Gauge Meter Measurement Meter$Id)
    (io.micrometer.core.instrument.binder.jvm ClassLoaderMetrics JvmMemoryMetrics JvmGcMetrics JvmThreadMetrics JvmCompilationMetrics JvmHeapPressureMetrics)
    (io.micrometer.core.instrument.binder.system FileDescriptorMetrics ProcessorMetrics UptimeMetrics)))


(defn to-string [v]
  (cond
    (keyword? v) (name v)
    (string? v) v
    :else (str v)))


(defn ^Iterable to-tags [tags]
  (for [[k v] tags] (Tag/of (to-string k) (to-string v))))


(defmulti create-registry "Returns raw meter registry object from micrometer library." :type)


(defmethod create-registry :simple [config]
  {:config config, :type :simple,
   :registry (SimpleMeterRegistry.)})


(defmethod create-registry :composite [{:keys [configs] :as config}]
  (let [components (into {} (for [[k v] configs] {k (create-registry v)}))]
    (when (= 0 (count components)) (throw (ex-info "Cannot create empty composite registry" {:config config})))
    {:config   config, :components components, :type :composite,
     :registry (CompositeMeterRegistry. Clock/SYSTEM (map :registry (vals components)))}))


(defmethod create-registry :default [cfg]
  (throw (ex-info "Invalid metrics registry type" {:cfg cfg})))


(def DEFAULT-JVM-METRICS [:classes :memory :gc :threads :jit :heap])


(defn setup-jvm-metrics [{:keys [registry] :as metrics} jvm-metrics]
  (doseq [metric jvm-metrics]
    (case metric
      :classes (.bindTo (ClassLoaderMetrics.) registry)
      :memory (.bindTo (JvmMemoryMetrics.) registry)
      :gc (.bindTo (JvmGcMetrics.) registry)
      :threads (.bindTo (JvmThreadMetrics.) registry)
      :jit (.bindTo (JvmCompilationMetrics.) registry)
      :heap (.bindTo (JvmHeapPressureMetrics.) registry)
      (throw (ex-info "Illegal JVM metric" {:metric metric, :allowed DEFAULT-JVM-METRICS})))))


(def DEFAULT-OS-METRICS [:files :cpu :uptime])


(defn setup-os-metrics [{:keys [registry]} os-metrics]
  (doseq [metric os-metrics]
    (case metric
      :files (.bindTo (FileDescriptorMetrics.) registry)
      :cpu (.bindTo (ProcessorMetrics.) registry)
      :uptime (.bindTo (UptimeMetrics.) registry)
      (throw (ex-info "Illegal OS metric" {:metric metric, :allowed DEFAULT-OS-METRICS})))))


(defn setup-common-tags [{:keys [registry]} tags]
  (when-not (empty? tags)
    (let [cfg (.config registry)]
      (.commonTags cfg (to-tags tags)))))


(defn metrics [cfg]
  (doto
    (assoc
      (create-registry cfg)
      :metrics (atom {}))
    (setup-common-tags (:tags cfg {}))
    (setup-os-metrics (:os-metrics cfg DEFAULT-OS-METRICS))
    (setup-jvm-metrics (:jvm-metrics cfg DEFAULT-JVM-METRICS))))


(defn close [{:keys [^MeterRegistry registry]}]
  (when registry
    (.close registry)))


(defmulti scrape "Returns data as registry-specific data or nil when given registry type cannot be scraped" :type)


(defmethod scrape :default [_] nil)


(defn list-meters [{:keys [^MeterRegistry registry]}]
  (let [names (into #{} (for [^Meter meter (.getMeters registry)] (.getName (.getId meter))))]
    {:names (into [] names)}))


(defn- merge-stats [stats1 stats2]
  (if (= 1 (count stats1))
    [[(-> stats1 first first) (+ (-> stats1 first second) (-> stats2 first second))]]
    (for [[[ss1 sv1] [_ sv2]] (map vector stats1 stats2)] [ss1 (+ sv1 sv2)])))


(defn- acc-tags [acc [tagk tagv]]
  (assoc acc tagk (conj (get acc tagk #{}) tagv)))


(defn- match-meter [name tagk tagv m]
  (let [^Meter$Id id (.getId m)]
    (and
      (= name (.getName id))
      (or (nil? tagk) (first (for [^Tag t (.getTagsAsIterable id) :when (and (= tagk (.getKey t)) (= tagv (.getValue t)))] true))))))


(defn query-meters [{:keys [^MeterRegistry registry]} name & [tagk tagv]]
  (let [meters (for [^Meter m (.getMeters registry) :when (match-meter name tagk tagv m)] m)]
    (when-not (empty? meters)
      (let [^Meter meter (first meters), mdesc (.getDescription (.getId meter)), munit (.getBaseUnit (.getId meter)),
            stats (for [^Meter m meters] (for [^Measurement s (.measure m)] [(.getStatistic s) (.getValue s) ]))
            tags (for [^Meter m meters, ^Tag t (.getTagsAsIterable (.getId m)) ] [(.getKey t) (.getValue t)])]
        (merge
          {:name          (.getName (.getId meter)),
           :measurements  (for [[s v] (reduce merge-stats stats)] {:statistic (str s), :value v})
           :availableTags (into {} (for [[k v] (reduce acc-tags {} tags)] {k (vec v)}))}
          (when mdesc {:description mdesc})
          (when munit {:baseUnit munit}))))))


(defn get-timer [{:keys [metrics ^MeterRegistry registry]} ^String name tags]
  (when metrics
    (let [timer (get-in @metrics [name tags])]
      (cond
        (instance? Timer timer) timer
        (some? timer) (throw (ex-info "Metric already registered and is not a timer" {:name name, :tags tags, :metric timer}))
        :else
        (let [timer (.timer registry name (to-tags tags))]
          (swap! metrics assoc-in [name tags] timer)
          timer)))))


(defmacro with-timer [^Timer timer & body]
  `(let [f# (fn [] ~@body)]
     (if ~timer (.record ^Timer ~timer f#) (f#))))


(defmacro timed [metrics name tags & body]
  `(let [timer# (get-timer ~metrics ~name ~tags), f# (fn [] ~@body)]
     (if timer# (.record ^Timer timer# ^Runnable f#) (f#))))


(defn get-counter [{:keys [metrics ^MeterRegistry registry]} ^String name tags]
  (when metrics
    (let [counter (get-in @metrics [name tags])]
      (cond
        (instance? Counter counter) counter
        (some? counter) (throw (ex-info "Metric already registered and is not a counter" {:name name, :tags tags, :metric counter}))
        :else
        (let [counter (.counter registry name (to-tags tags))]
          (swap! metrics assoc-in [name tags] counter)
          counter)))))


(defn inc-counter
  ([^Counter counter]
   (when counter (.increment counter)))
  ([^Counter counter n]
   (when counter (.increment counter n)))
  ([metrics name tags]
   (inc-counter metrics name tags 1.0))
  ([metrics name tags n]
   (let [counter (get-counter metrics name tags)]
     (when counter (.increment counter n)))))


(defn get-gauge [{:keys [metrics ^MeterRegistry registry] :as m} name tags gfn]
  (when metrics
    (let [gauge (get-in @metrics [name tags])]
      (cond
        (instance? Gauge gauge) gauge
        (some? gauge) (throw (ex-info "Metric already registered and is not a gauge" {:name name, :tags tags, :metric gauge}))
        :else
        (let [supplier (reify Supplier (get [_] (gfn))),
              gauge (.register (.tags (Gauge/builder name supplier) (to-tags tags)) registry)]
          (swap! metrics assoc-in [name tags] gauge)
          gauge)))))


(defmacro defgauge [metrics name tags & body]
  `(when ~metrics (get-gauge ~metrics ~name ~tags (fn [] ~@body))))

