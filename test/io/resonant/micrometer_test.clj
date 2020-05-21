(ns io.resonant.micrometer-test
  (:require
    [clojure.test :refer :all]
    [io.resonant.micrometer :as m]
    io.resonant.micrometer.prometheus
    io.resonant.micrometer.elastic
    io.resonant.micrometer.opentsdb
    io.resonant.micrometer.graphite
    io.resonant.micrometer.influx
    clojure.string)
  (:import
    (io.micrometer.core.instrument.simple SimpleMeterRegistry)
    (io.micrometer.core.instrument.composite CompositeMeterRegistry)
    (io.micrometer.prometheus PrometheusMeterRegistry)
    (io.micrometer.core.instrument Timer Counter MeterRegistry)
    (io.micrometer.elastic ElasticMeterRegistry)
    (io.micrometer.opentsdb OpenTSDBMeterRegistry)
    (io.micrometer.graphite GraphiteMeterRegistry)
    (io.micrometer.influx InfluxMeterRegistry)))

(def SIMPLE {:type :simple, :jvm-metrics [], :os-metrics [], :tags {:location "WAW"}})

(deftest test-create-registry
  (testing "Meter registries creation multimethod"
    (is (instance? SimpleMeterRegistry (:registry (m/metrics SIMPLE))))
    (is (instance? CompositeMeterRegistry (:registry (m/metrics {:type :composite, :configs {:test1 SIMPLE :test2 SIMPLE}}))))
    (is (instance? PrometheusMeterRegistry (:registry (m/metrics {:type :prometheus :jvm-metrics [], :os-metrics []}))))
    (with-open [registry (:registry (m/metrics {:type :elastic, :jvm-metrics [], :os-metrics [], :enabled? false}))]
      (is (instance? ElasticMeterRegistry registry)))
    (with-open [registry (:registry (m/metrics {:type :opentsdb, :jvm-metrics [], :os-metrics [], :enabled? false}))]
      (is (instance? OpenTSDBMeterRegistry registry)))
    (with-open [registry (:registry (m/metrics {:type :graphite, :jvm-metrics [], :os-metrics [], :enabled? false}))]
      (is (instance? GraphiteMeterRegistry registry)))
    (with-open [registry (:registry (m/metrics {:type :influx, :jvm-metrics [], :os-metrics [], :enabled? false}))]
      (is (instance? InfluxMeterRegistry registry)))))

(deftest test-timer-metrics
  (testing "Timer metrics registration and usage."
    (let [metrics (m/metrics SIMPLE), tcnt (atom 0)
          ^Timer timer (m/get-timer metrics "test" {:foo "bar"})]
      ; test timer (directly used)
      (m/with-timer timer (Thread/sleep 2))
      (is (= 2 (.size (.getTags (.getId timer)))))
      (is (= 1 (.count timer)))
      ; test null timer
      (m/with-timer nil (swap! tcnt inc))
      (is (= 1 (.count timer)))
      (is (= 1 @tcnt))
      ; test indirectly used timer
      (m/timed metrics "test" {:foo "bar"} (Thread/sleep 1) (Thread/sleep 1))
      (is (= 2 (.count timer)))
      ; test indireclty used null timer
      (m/timed nil "test" {:foo "bar"} (swap! tcnt inc))
      (is (= 2 (.count timer)))
      (is (= 2 @tcnt)))))

(deftest test-counter-metrics
  (testing "Counter metrics registration and usage"
    (let [metrics (m/metrics SIMPLE),
          ^Counter counter (m/get-counter metrics "test" {:foo "bar"})]
      (m/inc-counter counter)
      (is (= 1.0 (.count counter)))
      (m/inc-counter nil)
      (is (= 1.0 (.count counter)))
      (m/inc-counter metrics "test" {:foo "bar"})
      (is (= 2.0 (.count counter)))
      (m/inc-counter nil "test" {:foo "bar"})
      (is (= 2.0 (.count counter))))))

(deftest test-gauge-metrics
  (testing "Gauge metrics registration and usage"
    (let [data (atom [1 2 3]), metrics (m/metrics SIMPLE),
          gauge (m/defgauge metrics "test" {:foo "bar"} (count @data))
          g1 (m/defgauge nil "test" {:foo "bar"} (count @data))]
      (is (nil? g1))
      (is (= 3.0 (.value gauge)))
      (swap! data conj 4)
      (is (= 4.0 (.value gauge))))))

(deftest test-jvm-os-metrics
  (testing "Register standard JVM and OS metrics"
    (let [{:keys [^MeterRegistry registry]} (m/metrics {:type :simple})]
      (is (< 32 (.size (.getMeters registry)))))))

(deftest test-list-query-meters
  (let [metrics (m/metrics {:type :simple})
        lm (m/list-meters metrics)
        qm1 (m/query-meters metrics "jvm.memory.used")
        qm2 (m/query-meters metrics "jvm.memory.used" "area" "heap")]
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
             :meter-filters [{:accept (partial re-matches #"^foo")}, {:deny (constantly true)},
                             {:deny-unless false}, {:dist-stats dsc},
                             {:raw-map identity}, {:raw-accept identity}]
             :max-metrics 100,
             :tag-limits [{:prefix "jvm", :tag "foo", :max-vals 10, :on-limit {:deny true}}]
             :val-limits [{:prefix "jvm", :min 0, :max 10}]}]
    (is (some? (:registry (m/metrics cfg))))))
