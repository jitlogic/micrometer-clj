(ns io.resonant.micrometer
  (:import
    (java.util.function Supplier Function Predicate ToDoubleFunction ToLongFunction)
    (io.micrometer.core.instrument.composite CompositeMeterRegistry)
    (io.micrometer.core.instrument.simple SimpleMeterRegistry)
    (io.micrometer.core.instrument Clock Timer MeterRegistry Tag Counter Gauge Meter Measurement Meter$Id LongTaskTimer FunctionCounter DistributionSummary Tags LongTaskTimer$Builder FunctionTimer)
    (io.micrometer.core.instrument.binder.jvm ClassLoaderMetrics JvmMemoryMetrics JvmGcMetrics JvmThreadMetrics JvmCompilationMetrics JvmHeapPressureMetrics)
    (io.micrometer.core.instrument.binder.system FileDescriptorMetrics ProcessorMetrics UptimeMetrics)
    (io.micrometer.core.instrument.config MeterFilter MeterFilterReply)
    (io.micrometer.core.instrument.distribution DistributionStatisticConfig)
    (java.time Duration)
    (java.util.concurrent TimeUnit)
    (java.io Closeable)))

(defn- to-string [v]
  (cond
    (keyword? v) (name v)
    (string? v) v
    :else (str v)))

(defn- to-function [v-or-fn]
  (if (fn? v-or-fn)
    (reify Function (apply [_ v] (to-string (v-or-fn v))))
    (reify Function (apply [_ v] (to-string v)))))

(defn- to-predicate [v-or-fn]
  (if (fn? v-or-fn)
    (reify Predicate (test [_ v] (true? (v-or-fn v))))
    (reify Predicate (test [_ v] (true? v)))))

(defn- ^Iterable to-tags [tags]
  (for [[k v] tags] (Tag/of (to-string k) (to-string v))))

(defn ^Duration to-duration [d]
  (cond
    (instance? Duration d) d
    (number? d) (Duration/ofMillis d)
    (string? d)
    (let [[_ n s] (re-matches #"(\d+)(ms|s|m|h|d)" d)]
      (case s
        "ms" (Duration/ofMillis (Long/parseLong n))
        "s" (Duration/ofSeconds (Long/parseLong n))
        "m" (Duration/ofMinutes (Long/parseLong n))
        "h" (Duration/ofHours (Long/parseLong n))
        "d" (Duration/ofDays (Long/parseLong n)))
      (throw (ex-info "invalid duration string" {:duration d})))
    :else
    (throw (ex-info "invalid duration value" {:duration d}))))

(defrecord Registry [type registry config components meters]
  Closeable
  (close [_]
    (when registry
      (.close ^MeterRegistry registry))))

(defmacro registry? [obj]
  `(instance? Registry ~obj))

(defn- to-registry
  ([registry config]
   (to-registry registry config nil))
  ([registry config components]
   (cond
     (registry? registry) registry
     (instance? MeterRegistry registry) (Registry. (:type config) registry config components (atom {})))))


(def REGISTRY-TYPES #{:appoptics :atlas :azure :cloudwatch :datadog :dynatrace :elastic :ganglia :graphite :humio
                   :influx :jmx :kairos :newrelic :opentsdb :prometheus :signalfx :stackdriver :statsd :wavefront})

(defonce ^:dynamic *registry* nil)

(defmulti create-registry "Returns raw meter registry object from micrometer library." :type)

(defmethod create-registry :simple [_]
  (SimpleMeterRegistry.))

(defmethod create-registry :composite [{:keys [components] :as config}]
  (let [components (into {} (for [[k v] components] {k (to-registry (create-registry v) v)}))]
    (when (= 0 (count components)) (throw (ex-info "Cannot create empty composite registry" {:config config})))
    (to-registry (CompositeMeterRegistry. Clock/SYSTEM (map :registry (vals components))) config)))

(defmethod create-registry :default [{:keys [type] :as cfg}]
  (if (contains? REGISTRY-TYPES type)
    (do
      (require (symbol (str "io.resonant.micrometer." (name type))))
      (create-registry cfg))
    (throw (ex-info "Unknown metrics registry type" {:cfg cfg}))))

(def ^:private DEFAULT-JVM-METRICS [:classes :memory :gc :threads :jit :heap])

(defn- setup-jvm-metrics [{:keys [registry]} jvm-metrics]
  (doseq [metric jvm-metrics]
    (case metric
      :classes (.bindTo (ClassLoaderMetrics.) registry)
      :memory (.bindTo (JvmMemoryMetrics.) registry)
      :gc (.bindTo (JvmGcMetrics.) registry)
      :threads (.bindTo (JvmThreadMetrics.) registry)
      :jit (.bindTo (JvmCompilationMetrics.) registry)
      :heap (.bindTo (JvmHeapPressureMetrics.) registry)
      (throw (ex-info "Illegal JVM metric" {:metric metric, :allowed DEFAULT-JVM-METRICS})))))

(def ^:private DEFAULT-OS-METRICS [:files :cpu :uptime])

(defn- setup-os-metrics [{:keys [registry]} os-metrics]
  (doseq [metric os-metrics]
    (case metric
      :files (.bindTo (FileDescriptorMetrics.) registry)
      :cpu (.bindTo (ProcessorMetrics.) registry)
      :uptime (.bindTo (UptimeMetrics.) registry)
      (throw (ex-info "Illegal OS metric" {:metric metric, :allowed DEFAULT-OS-METRICS})))))

(defn- distribution-statistics-config [{:keys [histogram? percentiles precision sla min-val max-val expiry buf-len]}]
  (let [b (DistributionStatisticConfig/builder)]
    (when (some? histogram?) (.percentilesHistogram b histogram?))
    (when percentiles (.percentiles b (double-array percentiles)))
    (when precision (.percentilePrecision b (int precision)))
    (when sla (.serviceLevelObjectives b (double-array (map double sla))))
    (when min-val (.minimumExpectedValue b ^Double (double min-val)))
    (when max-val (.maximumExpectedValue b ^Double (double max-val)))
    (when expiry (.expiry b (to-duration expiry)))
    (when buf-len (.bufferLength b (int buf-len)))))

(defn- meter-filter [{:keys [^MeterRegistry registry]} mf]
  (.meterFilter (.config registry) mf))

(defn- dist-stats-filter [{:keys [name name-re] :as cfg}]
  (let [dsc (distribution-statistics-config cfg)]
    (reify MeterFilter
      (^DistributionStatisticConfig configure [_ ^Meter$Id id ^DistributionStatisticConfig cfg]
        (if (or (= name (.getName id)) (and name-re (re-matches name-re (.getName id)))) dsc cfg)))))

(defn- make-filter [cfg]
  (let [mode (key (first cfg)), ffn (val (first cfg))]
    (case mode
      :deny-unless (MeterFilter/denyUnless (to-predicate ffn))
      :accept (MeterFilter/accept (to-predicate ffn))
      :deny (MeterFilter/deny (to-predicate ffn))
      :raw-map (reify MeterFilter  (^Meter$Id map [_ ^Meter$Id id] (ffn id)))
      :raw-filter (reify MeterFilter (^MeterFilterReply accept [_ ^Meter$Id id]  (ffn id)))
      :dist-stats (dist-stats-filter ffn)
      (throw (ex-info "unknown filter mode" {:mode mode})))))

(defn- setup-rename-tags [registry rename-tags]
  (doseq [{:keys [prefix from to]} rename-tags]
    (meter-filter registry (MeterFilter/renameTag (to-string prefix) (to-string from) (to-string to)))))

(defn- setup-ignore-tags [registry ignore-tags]
  (when ignore-tags (meter-filter registry (MeterFilter/ignoreTags (into-array String (map to-string ignore-tags))))))

(defn- setup-replace-tags [registry replace-tags]
  (doseq [{:keys [from procfn except]} replace-tags]
    (meter-filter registry (MeterFilter/replaceTagValues from (to-function procfn) (into-array String (map to-string except))))))

(defn- setup-filters [registry filters]
  (doseq [f filters] (meter-filter registry (make-filter f))))

(defn- setup-limits [registry {:keys [max-metrics tag-limits val-limits]}]
  (when max-metrics (meter-filter registry (MeterFilter/maximumAllowableMetrics max-metrics)))
  (doseq [{:keys [prefix tag max-vals on-limit]} tag-limits]
    (meter-filter registry (MeterFilter/maximumAllowableTags prefix tag max-vals (make-filter on-limit))))
  (doseq [{:keys [prefix max min]} val-limits]
    (when max (meter-filter registry (MeterFilter/maxExpected ^String prefix (double max))))
    (when min (meter-filter registry (MeterFilter/minExpected ^String prefix (double min))))))

(defn- setup-common-tags [{:keys [^MeterRegistry registry]} tags]
  (when-not (empty? tags)
    (let [cfg (.config registry)]
      (.commonTags cfg (to-tags tags)))))

(defn meter-registry [{:keys [rename-tags ignore-tags replace-tags meter-filters] :as cfg}]
  (doto
    (to-registry (create-registry cfg) cfg)
    (setup-common-tags (:tags cfg {}))
    (setup-os-metrics (:os-metrics cfg DEFAULT-OS-METRICS))
    (setup-jvm-metrics (:jvm-metrics cfg DEFAULT-JVM-METRICS))
    (setup-rename-tags rename-tags)
    (setup-ignore-tags ignore-tags)
    (setup-replace-tags replace-tags)
    (setup-filters meter-filters)
    (setup-limits (select-keys cfg [:max-metrics :tag-limits :val-limits]))))

(defn configure [m]
  (alter-var-root
    #'*registry*
    (constantly
      (cond
        (nil? m) nil
        (registry? m) m
        :else (meter-registry m)))))

(defmulti scrape "Returns data as registry-specific data or nil when given registry type cannot be scraped" :type)

(defmethod scrape :default [_] nil)

(defn list-meters
  ([] (list-meters *registry*))
  ([{:keys [^MeterRegistry registry]}]
   (let [names (into #{} (for [^Meter meter (.getMeters registry)] (.getName (.getId meter))))]
     {:names (into [] names)})))

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

(defn query-meters
  ([name]
   (query-meters *registry* name {}))
  ([arg1 arg2]
   (if (registry? arg1)
     (query-meters arg1 arg2 {})
     (query-meters *registry* arg1 arg2)))
  ([{:keys [^MeterRegistry registry]} name tags]
   (let [[tagk tagv] (first tags), tagk (when tagk (to-string tagk)), tagv (when tagv (to-string tagv)),
         meters (for [^Meter m (.getMeters registry) :when (match-meter name tagk tagv m)] m)]
     (when-not (empty? meters)
       (let [^Meter meter (first meters), mdesc (.getDescription (.getId meter)), munit (.getBaseUnit (.getId meter)),
             stats (for [^Meter m meters] (for [^Measurement s (.measure m)] [(.getStatistic s) (.getValue s)]))
             tags (for [^Meter m meters, ^Tag t (.getTagsAsIterable (.getId m))] [(.getKey t) (.getValue t)])]
         (merge
           {:name (.getName (.getId meter)),
            :measurements (for [[s v] (reduce merge-stats stats)] {:statistic (str s), :value v})
            :availableTags (into {} (for [[k v] (reduce acc-tags {} tags)] {k (vec v)}))}
           (when mdesc {:description mdesc})
           (when munit {:baseUnit munit})))))))

(defn get-timer
  ([name tags]
   (get-timer *registry* name tags {}))
  ([arg1 arg2 arg3]
   (if (registry? arg1)
     (get-timer arg1 arg2 arg3 {})
     (get-timer *registry* arg1 arg2 arg3)))
  ([registry ^String name tags {:keys [description percentiles precision histogram? sla min-val max-val expiry buf-len]}]
   (when-let [{:keys [meters ^MeterRegistry registry]} registry]
     (let [tags (or tags {}), timer (get-in @meters [name tags])]
       (cond
         (instance? Timer timer) timer
         (some? timer) (throw (ex-info "Metric already registered and is not a timer" {:name name, :tags tags, :metric timer}))
         :else
         (let [timer (cond->
                       (Timer/builder name)
                       (map? tags) (.tags (to-tags tags))
                       (string? description) (.description description)
                       (some? percentiles) (.publishPercentiles (double-array percentiles))
                       (number? precision) (.percentilePrecision precision)
                       (boolean? histogram?) (.publishPercentileHistogram histogram?)
                       (some? sla) (.serviceLevelObjectives (into-array (for [s sla] (to-duration s))))
                       (number? min-val) (.minimumExpectedValue (to-duration min-val))
                       (number? max-val) (.maximumExpectedValue (to-duration max-val))
                       (number? expiry) (.distributionStatisticExpiry (to-duration expiry))
                       (number? buf-len) (.distributionStatisticBufferLength buf-len)
                       true (.register registry))]
           (swap! meters assoc-in [name tags] timer)
           timer))))))

(defmacro with-timer [^Timer timer & body]
  `(let [f# (fn [] ~@body)]
     (if ~timer (.recordCallable ^Timer ~timer f#) (f#))))

(defn- parse-meter-args
  ([name tags] ['io.resonant.micrometer/*registry* name tags {}])
  ([registry name tags] [registry name tags {}])
  ([registry name tags options] [registry name tags options]))

(defmacro timed [args & body]
  (let [[registry name tags options] (apply parse-meter-args args)]
    `(let [timer# (get-timer (or ~registry *registry*) ~name ~tags ~options), f# (fn [] ~@body)]
       (if timer# (.recordCallable ^Timer timer# ^Runnable f#) (f#)))))

(defn add-timer
  ([timer duration]
   (when timer
     (.record ^Timer timer (to-duration duration))))
  ([name tags duration]
   (when-let [timer (get-timer *registry* name tags)]
     (.record ^Timer timer (to-duration duration))))
  ([registry name tags duration]
   (when-let [timer (get-timer registry name tags)]
     (.record ^Timer timer (to-duration duration))))
  ([registry name tags options duration]
   (when-let [timer (get-timer registry name tags options)]
     (.record ^Timer timer (to-duration duration)))))

(defn get-task-timer
  ([name tags]
   (get-task-timer *registry* name tags))
  ([arg1 arg2 arg3]
   (if (registry? arg1)
     (get-task-timer arg1 arg2 arg3 {})
     (get-task-timer *registry* arg1 arg2 arg3)))
  ([registry ^String name tags {:keys [description percentiles precision histogram? sla min-val max-val expiry buf-len]}]
   (when-let [{:keys [meters ^MeterRegistry registry]} registry]
     (let [tags (or tags {}), timer (get-in @meters [name tags])]
       (cond
         (instance? LongTaskTimer timer) timer
         (some? timer) (throw (ex-info "Metric already registered and is not long task timer" {:name name, :tags tags, :metric timer}))
         :else
         (let [timer
               (cond->
                 (LongTaskTimer/builder  name)
                 (map? tags) (.tags (to-tags tags))
                 (string? description) (.description description)
                 (some? percentiles) (.publishPercentiles (double-array percentiles))
                 (number? precision) (.percentilePrecision precision)
                 (boolean? histogram?) (.publishPercentileHistogram histogram?)
                 (some? sla) (.serviceLevelObjectives (into-array (for [s sla] (to-duration s))))
                 (number? min-val) (.minimumExpectedValue (to-duration min-val))
                 (number? max-val) (.maximumExpectedValue (to-duration max-val))
                 (number? expiry) (.distributionStatisticExpiry (to-duration expiry))
                 (number? buf-len) (.distributionStatisticBufferLength buf-len)
                 true (.register registry))]
           (swap! meters assoc-in [name tags] timer)
           timer))))))

(defmacro with-task-timer [^LongTaskTimer timer & body]
  `(let [f# (fn [] ~@body)]
     (if ~timer (.recordCallable ^LongTaskTimer ~timer f#) (f#))))

(defmacro task-timed [args & body]
  (let [[registry name tags options] (apply parse-meter-args args)]
    `(let [timer# (get-task-timer (or ~registry *registry*) ~name ~tags ~options), f# (fn [] ~@body)]
       (if timer# (.recordCallable ^LongTaskTimer timer# ^Runnable f#) (f#)))))

(defn get-function-timer
  ([name tags obj cfn tfn time-unit]
   (get-function-timer *registry* name tags {} obj cfn tfn time-unit))
  ([arg1 arg2 arg3 obj cfn tfn time-unit]
   (if (instance? Registry arg1)
     (get-function-timer arg1 arg2 arg3 {} obj cfn tfn time-unit)
     (get-function-timer *registry* arg1 arg2 arg3 obj cfn tfn time-unit)))
  ([registry name tags {:keys [description]} obj cfn tfn time-unit]
   (when-let [{:keys [meters ^MeterRegistry registry]} registry]
     (let [tags (or tags {}), timer (get-in @meters [name tags])]
       (cond
         (instance? FunctionTimer timer) timer
         (some? timer) (throw (ex-info "Metric already registered and is not function timer" {:name name, :tags tags, :metric timer}))
         :else
         (let [cntfn (reify ToLongFunction (applyAsLong [_ obj] (long (cfn obj))))
               timfn (reify ToDoubleFunction (applyAsDouble [_ obj] (double (tfn obj))))
               timer (cond->
                       (FunctionTimer/builder name obj cntfn timfn (TimeUnit/valueOf (.toUpperCase (clojure.core/name time-unit))))
                       (map? tags) (.tags (to-tags tags))
                       (string? description) (.description description)
                       true (.register registry))]
           (swap! meters assoc-in [name tags] timer)
           timer))))))

(defn get-counter
  ([name tags]
   (get-counter *registry* name tags {}))
  ([arg1 arg2 arg3]
   (if (registry? arg1)
     (get-counter arg1 arg2 arg3 {})
     (get-counter *registry* arg1 arg2 arg3)))
  ([registry ^String name tags {:keys [description base-unit]}]
   (when-let [{:keys [meters ^MeterRegistry registry]} registry]
     (let [tags (or tags {}), counter (get-in @meters [name tags])]
       (cond
         (instance? Counter counter) counter
         (some? counter) (throw (ex-info "Metric already registered and is not a counter" {:name name, :tags tags, :metric counter}))
         :else
         (let [counter (cond->
                         (Counter/builder name)
                         (map? tags) (.tags (to-tags tags))
                         (string? description) (.description description)
                         (string? base-unit) (.baseUnit base-unit)
                         true (.register registry))]
           (swap! meters assoc-in [name tags] counter)
           counter))))))

(defn add-counter
  ([^Counter counter n]
   (when counter (.increment counter n)))
  ([name tags n]
   (add-counter *registry* name tags n))
  ([arg1 arg2 arg3 n]
   (let [counter (get-counter arg1 arg2 arg3)]
     (when counter (.increment counter n))))
  ([registry name tags options n]
   (let [counter (get-counter registry name tags options)]
     (when counter (.increment counter n)))))

(defn get-function-counter
  ([name tags obj cfn]
   (get-function-counter *registry* name tags {} obj cfn))
  ([arg1 arg2 arg3 obj cfn]
   (if (registry? arg1)
     (get-function-counter arg1 arg2 arg3 {} obj cfn)
     (get-function-counter *registry* arg1 arg2 arg3)))
  ([registry name tags {:keys [description base-unit]} obj cfn]
   (when-let [{:keys [meters ^MeterRegistry registry]} registry]
     (let [tags (or tags {}), counter (get-in @meters [name tags])]
       (cond
         (instance? FunctionCounter counter) counter
         (some? counter) (throw (ex-info "Metric already registered and is not function counter" {:name name, :tags tags, :metric counter}))
         :else
         (let [cntfn (reify ToDoubleFunction (applyAsDouble [_ v] (double (cfn v))))
               counter (cond->
                         (FunctionCounter/builder ^String name obj cntfn)
                         (map? tags) (.tags (to-tags tags))
                         (string? description) (.description description)
                         (string? base-unit) (.baseUnit base-unit)
                         true (.register registry))]
           (swap! meters assoc-in [name tags] counter)
           counter))))))

(defn get-gauge
  ([name tags gfn]
   (get-gauge *registry* name tags {} gfn))
  ([arg1 arg2 arg3 gfn]
   (if (registry? arg1)
     (get-gauge arg1 arg2 arg3 {} gfn)
     (get-gauge *registry* arg1 arg2 arg3 gfn)))
  ([registry name tags {:keys [description base-unit strong-ref?]} gfn]
   (when-let [{:keys [meters ^MeterRegistry registry]} (or registry *registry*)]
     (let [tags (or tags {}), gauge (get-in @meters [name tags])]
       (cond
         (instance? Gauge gauge) gauge
         (some? gauge) (throw (ex-info "Metric already registered and is not a gauge" {:name name, :tags tags, :metric gauge}))
         :else
         (let [supplier (reify Supplier (get [_] (gfn))),
               gauge (cond->
                       (Gauge/builder name supplier)
                       (map? tags) (.tags (to-tags tags))
                       (string? description) (.description description)
                       (string? base-unit) (.baseUnit base-unit)
                       (boolean? strong-ref?) (.strongReference strong-ref?)
                       true (.register registry))]
           (swap! meters assoc-in [name tags] gauge)
           gauge))))))

(defmacro defgauge [args & body]
  (let [[registry name tags options] (apply parse-meter-args args)]
    `(get-gauge ~registry ~name ~tags ~options (fn [] ~@body))))

(defn get-summary
  ([name tags]
   (get-summary *registry* name tags {}))
  ([arg1 arg2 arg3]
   (if (registry? arg1)
     (get-summary arg1 arg2 arg3 {})
     (get-summary *registry* arg1 arg2 arg3)))
  ([registry name tags {:keys [description base-unit percentiles precision histogram? sla min-val max-val expiry buf-len scale]}]
   (when-let [{:keys [meters ^MeterRegistry registry]} (or registry *registry*)]
     (let [tags (or tags {}), summary (get-in @meters [name tags])]
       (cond
         (instance? DistributionSummary summary) summary
         (some? summary) (throw (ex-info "Metric already registered and is not a summary" {:name name, :tags tags, :metric summary}))
         :else
         (let [summary (cond->
                         (DistributionSummary/builder name)
                         (map? tags) (.tags (to-tags tags))
                         (string? description) (.description description)
                         (string? base-unit) (.baseUnit base-unit)
                         (some? percentiles) (.publishPercentiles (double-array percentiles))
                         (number? precision) (.percentilePrecision precision)
                         (boolean? histogram?) (.publishPercentileHistogram histogram?)
                         (some? sla) (.serviceLevelObjectives (into-array (for [s sla] (to-duration s))))
                         (number? min-val) (.minimumExpectedValue ^Double (double min-val))
                         (number? max-val) (.maximumExpectedValue ^Double (double max-val))
                         (number? expiry) (.distributionStatisticExpiry (to-duration expiry))
                         (number? buf-len) (.distributionStatisticBufferLength buf-len)
                         (number? scale) (.scale (double scale))
                         true (.register registry))]
           (swap! meters assoc-in [name tags] summary)
           summary))))))

(defn add-summary
  ([^DistributionSummary summary v]
   (when summary (.record summary (double v))))
  ([name tags v]
   (when-let [summary (get-summary *registry* name tags)]
     (.record summary (double v))))
  ([registry name tags v]
   (when-let [summary (get-summary registry name tags)]
     (.record summary (double v))))
  ([registry name tags options v]
   (when-let [summary (get-summary registry name tags options)]
     (.record summary (double v)))))
