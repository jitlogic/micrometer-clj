(ns io.resonant.micrometer-test
  (:require
    [clojure.test :refer :all]
    [io.resonant.micrometer :as m]
    clojure.string)
  (:import
    (io.micrometer.core.instrument.simple SimpleMeterRegistry)
    (io.micrometer.core.instrument.composite CompositeMeterRegistry)
    (io.micrometer.prometheus PrometheusMeterRegistry)
    (io.micrometer.core.instrument Timer Counter MeterRegistry LongTaskTimer FunctionCounter DistributionSummary FunctionTimer)
    (io.micrometer.elastic ElasticMeterRegistry)
    (io.micrometer.opentsdb OpenTSDBMeterRegistry)
    (io.micrometer.graphite GraphiteMeterRegistry)
    (io.micrometer.influx InfluxMeterRegistry)
    (java.util.concurrent TimeUnit)
    (io.micrometer.jmx JmxMeterRegistry)
    (io.micrometer.azuremonitor AzureMonitorMeterRegistry)
    (io.micrometer.atlas AtlasMeterRegistry)
    (io.micrometer.statsd StatsdMeterRegistry)
    (io.micrometer.newrelic NewRelicMeterRegistry)
    (io.micrometer.appoptics AppOpticsMeterRegistry)
    (io.micrometer.datadog DatadogMeterRegistry)
    (io.micrometer.ganglia GangliaMeterRegistry)
    (io.micrometer.dynatrace DynatraceMeterRegistry)
    (io.micrometer.wavefront WavefrontMeterRegistry)
    (io.micrometer.humio HumioMeterRegistry)
    (io.micrometer.kairos KairosMeterRegistry)
    (io.micrometer.signalfx SignalFxMeterRegistry)))

(defn setup-fixture [f] (f) (m/configure nil))

(use-fixtures :each setup-fixture)

(def SIMPLE {:type :simple, :jvm-metrics [], :os-metrics [], :tags {:location "WAW"}})

(defmacro ccr [clazz type & {:as args}]
  `(with-open [registry# (:registry (m/meter-registry (merge {:type ~type, :jvm-metrics [], :os-metrics [], :enabled? false} ~args)))]
     (is (instance? ~clazz registry#))))

(deftest test-create-registry
  (testing "Meter registries creation multimethod"
    (is (instance? SimpleMeterRegistry (:registry (m/meter-registry SIMPLE))))
    (is (instance? CompositeMeterRegistry (:registry (m/meter-registry {:type :composite, :components {:test1 SIMPLE :test2 SIMPLE}}))))
    (is (instance? PrometheusMeterRegistry (:registry (m/meter-registry {:type :prometheus :jvm-metrics [], :os-metrics []}))))
    (ccr ElasticMeterRegistry :elastic)
    (ccr OpenTSDBMeterRegistry :opentsdb)
    (ccr GraphiteMeterRegistry :graphite)
    (ccr InfluxMeterRegistry :influx)
    (ccr JmxMeterRegistry :jmx)
    (ccr AzureMonitorMeterRegistry :azure)
    (ccr AtlasMeterRegistry :atlas)
    (ccr StatsdMeterRegistry :statsd)
    (ccr NewRelicMeterRegistry :newrelic :api-key "x", :account-id "x")
    (ccr AppOpticsMeterRegistry :appoptics :api-token "x")
    (ccr DatadogMeterRegistry :datadog :api-key "x")
    (ccr GangliaMeterRegistry :ganglia)
    (ccr DynatraceMeterRegistry :dynatrace :api-token "x" :url "http://localhost" :device-id "1")
    (ccr WavefrontMeterRegistry :wavefront :api-token "x")
    (ccr HumioMeterRegistry :humio :api-token "x")
    (ccr KairosMeterRegistry :kairos)
    (ccr SignalFxMeterRegistry :signalfx :access-token "x")))

(deftest test-timer-metrics
  (testing "Timer metrics registration and usage."
    (let [metrics (m/configure SIMPLE), tcnt (atom 0)
          ^Timer timer (m/get-timer metrics "test" {:foo "bar"})
          ^Timer t2 (m/get-timer "test2")]
      ; test timer (directly used)
      (is (= 42 (m/timed [timer] (Thread/sleep 2) 42)))
      (is (= 2 (.size (.getTags (.getId timer)))))
      (is (= 1 (.count timer)))
      ; test null timer
      (m/timed [nil] (swap! tcnt inc))
      (is (= 1 (.count timer)))
      (is (= 1 @tcnt))
      ; test indirectly used timer
      (is (= 42 (m/timed [metrics "test" {:foo "bar"}] (Thread/sleep 1) (Thread/sleep 1) 42)))
      (is (= 2 (.count timer)))
      ; test indireclty used null timer
      (m/timed ["test" {:foo "bar"}] (swap! tcnt inc))
      (is (= 3 (.count timer)))
      (is (= 2 @tcnt))
      (m/add-timer timer 10)
      (is (= 4 (.count timer)))
      (m/add-timer metrics "test" {:foo "bar"} 10)
      (is (= 5 (.count timer)))
      (m/add-timer "test2" 1)
      (is (= 1 (.count t2))))))

(deftest test-long-task-timer-metrics
  (testing "Long task timer registration and usage"
    (let [metrics (m/meter-registry SIMPLE), tcnt (atom 0),
          ^LongTaskTimer timer (m/get-task-timer metrics "test" {:foo "bar"})]
      (is (= 42)
        (m/task-timed [timer]
          (Thread/sleep 2)
          (is (> (.duration timer TimeUnit/MILLISECONDS) 0.0))
          (swap! tcnt inc)
          (is (= 1) (.activeTasks timer))
          42))
      (is (= 1 @tcnt))
      (is (= 2 (.size (.getTags (.getId timer)))))
      (is (= (.duration timer TimeUnit/MILLISECONDS) 0.0))
      (is (= (.activeTasks timer) 0))
      (is (= 42)
        (m/task-timed [metrics "test" {:foo "bar"}]
                      (Thread/sleep 2)
          (is (> (.duration timer TimeUnit/MILLISECONDS) 0.0))
          (swap! tcnt inc)
          (is (= 1) (.activeTasks timer))
          42))
      (is (= 2 @tcnt))
      )))

(deftest test-function-timer-metrics
  (testing "Function timer registration and usage"
    (let [metrics (m/meter-registry SIMPLE), obj (atom 2),
          ^FunctionTimer timer (m/get-function-timer metrics "test" {:foo "bar"} {:description "TEST"} obj deref deref :MILLISECONDS)]
      (is (= 2.0 (.count timer)))
      (is (= 2.0 (.totalTime timer TimeUnit/MILLISECONDS)))
      (reset! obj 4)
      (is (= 4.0 (.count timer)))
      (is (= 4.0 (.totalTime timer TimeUnit/MILLISECONDS))))))

(deftest test-counter-metrics
  (testing "Counter metrics registration and usage"
    (let [metrics (m/configure SIMPLE),
          ^Counter counter (m/get-counter metrics "test" {:foo "bar"})]
      (m/add-counter counter 1.0)
      (is (= 1.0 (.count counter)))
      (m/add-counter nil 1.0)
      (is (= 1.0 (.count counter)))
      (m/add-counter metrics "test" {:foo "bar"} 1.0)
      (is (= 2.0 (.count counter)))
      (m/add-counter "test" {:foo "bar"} 1.0)
      (is (= 3.0 (.count counter))))))

(deftest test-tracking-counter
  (testing "Tracking metrics registration and usage"
    (let [metrics (m/meter-registry SIMPLE), obj (atom 42)
          ^FunctionCounter counter (m/get-function-counter metrics "test" {:foo "bar"} obj deref)]
      (is (= 42.0 (.count counter)))
      (reset! obj 44)
      (is (= 44.0 (.count counter))))))

(deftest test-gauge-metrics
  (testing "Gauge metrics registration and usage"
    (let [data (atom [1 2 3]), metrics (m/meter-registry SIMPLE),
          gauge (m/defgauge [metrics "test" {:foo "bar"} {}] (count @data))
          g1 (m/defgauge ["test" {:foo "bar"}] (count @data))]
      (is (nil? g1))
      (is (= 3.0 (.value gauge)))
      (swap! data conj 4)
      (is (= 4.0 (.value gauge))))))

(deftest test-jvm-os-metrics
  (testing "Register standard JVM and OS metrics"
    (let [{:keys [^MeterRegistry registry]} (m/meter-registry {:type :simple})]
      (is (< 32 (.size (.getMeters registry)))))))

(deftest test-list-query-meters
  (let [metrics (m/meter-registry {:type :simple})
        lm (m/list-meters metrics)
        qm1 (m/inspect-meter metrics "jvm.memory.used" {})
        qm2 (m/inspect-meter metrics "jvm.memory.used" {"area" "heap"})]
    (is (vector? (:names lm)))
    (is (contains? (set (:names lm)) "jvm.memory.used"))
    (is (number? (-> qm1 :measurements first :value)))
    (is (vector? (get-in qm1 [:availableTags "id"])))
    (is (vector? (get-in qm2 [:availableTags "id"])))
    (is (> (count (get-in qm1 [:availableTags "id"])) (count (get-in qm2 [:availableTags "id"]))))))

(deftest test-metrics-apply-filters
  (let [dsc {:histogram? true, :percentiles [50,90,95,99], :precision 2, :sla [90,95],
             :min-val 1, :max-val 100, :expiry 60000, :buf-len 100, :name "foo.bar", :name-re #"jvm.*"}
        cfg {:type :simple
             :rename-tags [{:prefix "jvm", :from "foo", :to "bar"}]
             :ignore-tags ["foo" "bar"]
             :replace-tags [{:from "foo", :to clojure.string/upper-case, :except ["baz" "bag"]}]
             :meter-filters [{:accept #(re-matches #"^foo" (.getName %))}, {:deny (constantly true)},
                             {:deny-unless false}, {:dist-stats dsc},
                             {:raw-map identity}, {:raw-filter identity}]
             :max-metrics 100,
             :tag-limits [{:prefix "jvm", :tag "foo", :max-vals 10, :on-limit {:deny true}}]
             :val-limits [{:prefix "jvm", :min 0, :max 10}]}]
    (is (some? (:registry (m/meter-registry cfg))))))

(deftest test-distribution-summary
  (let [metrics (m/meter-registry {:type :simple})
        ^DistributionSummary summary (m/get-summary metrics "test" {:foo "bar"})]
    (m/add-summary summary 4)
    (m/add-summary metrics "test" {:foo "bar"} 4)
    (is (= 2 (.count summary)))
    (is (= 8.0 (.totalAmount summary)))))

(deftest test-configure
  (testing "Test direct metrics setup"
    (with-open [m (m/meter-registry SIMPLE)]
      (m/configure m)
      (is (identical? m m/*registry*))))
  (testing "Test in-flight metrics creation"
    (m/configure SIMPLE)
    (is (instance? MeterRegistry (:registry m/*registry*)))))
