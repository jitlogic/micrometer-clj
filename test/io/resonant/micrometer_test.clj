(ns io.resonant.micrometer-test
  (:require
    [clojure.test :refer :all]
    [io.resonant.micrometer :as m])
  (:import
    (io.micrometer.core.instrument.simple SimpleMeterRegistry)
    (io.micrometer.core.instrument.composite CompositeMeterRegistry)
    (io.micrometer.prometheus PrometheusMeterRegistry)
    (io.micrometer.core.instrument Timer Counter MeterRegistry)))

(def SIMPLE {:type :simple, :jvm-metrics [], :os-metrics [], :tags {:location "WAW"}})


(deftest test-create-registry
  (testing "Meter registries creation multimethod"
    (is (instance? SimpleMeterRegistry (:registry (m/metrics SIMPLE))))
    (is (instance? SimpleMeterRegistry (:registry (m/metrics {:type :composite, :configs [SIMPLE]}))))
    (is (instance? CompositeMeterRegistry (:registry (m/metrics {:type :composite, :configs [SIMPLE SIMPLE]}))))
    (is (instance? PrometheusMeterRegistry (:registry (m/metrics {:type :prometheus :jvm-metrics [], :os-metrics []}))))))


(deftest test-timer-metrics
  (testing "Timer metrics registration and usage."
    (let [metrics (m/metrics SIMPLE),
          ^Timer timer (m/get-timer metrics "test" {:foo "bar"})]
      (m/with-timer timer (Thread/sleep 2))
      (is (= 2 (.size (.getTags (.getId timer)))))
      (is (= 1 (.count timer)))
      (m/timed metrics "test" {:foo "bar"} (Thread/sleep 1) (Thread/sleep 1))
      (is (= 2 (.count timer))))))


(deftest test-counter-metrics
  (testing "Counter metrics registration and usage"
    (let [metrics (m/metrics SIMPLE),
          ^Counter counter (m/get-counter metrics "test" {:foo "bar"})]
      (m/add counter)
      (is (= 1.0 (.count counter)))
      (m/add metrics "test" {:foo "bar"})
      (is (= 2.0 (.count counter))))))


(deftest test-gauge-metrics
  (testing "Gauge metrics registration and usage"
    (let [data (atom [1 2 3]), metrics (m/metrics SIMPLE),
          gauge (m/defgauge metrics "test" {:foo "bar"} (count @data))]
      (is (= 3.0 (.value gauge)))
      (swap! data conj 4)
      (is (= 4.0 (.value gauge))))))


(deftest test-jvm-os-metrics
  (testing "Register standard JVM and OS metrics"
    (let [{:keys [^MeterRegistry registry]} (m/metrics {:type :simple})]
      (is (< 32 (.size (.getMeters registry)))))))