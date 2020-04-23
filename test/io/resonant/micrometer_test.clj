(ns io.resonant.micrometer-test
  (:require
    [clojure.test :refer :all]
    [io.resonant.micrometer :as m])
  (:import
    (io.micrometer.core.instrument.simple SimpleMeterRegistry)
    (io.micrometer.core.instrument.composite CompositeMeterRegistry)
    (io.micrometer.prometheus PrometheusMeterRegistry)
    (io.micrometer.core.instrument Timer Counter)))

(deftest test-create-registry
  (testing "Meter registries creation multimethod"
    (is (instance? SimpleMeterRegistry (:registry (m/metrics {:type :simple}))))
    (is (instance? SimpleMeterRegistry (:registry (m/metrics [{:type :simple}]))))
    (is (instance? CompositeMeterRegistry (:registry (m/metrics [{:type :simple} {:type :simple}]))))
    (is (instance? PrometheusMeterRegistry (:registry (m/metrics {:type :prometheus}))))))

(deftest test-timer-metrics
  (testing "Timer metrics registration and usage."
    (let [metrics (m/metrics {:type :simple, :tags {:location "WAW"}}),
          ^Timer timer (m/get-timer metrics "test" {:foo "bar"})]
      (m/with-timer timer (Thread/sleep 2))
      (is (= 2 (.size (.getTags (.getId timer)))))
      (is (= 1 (.count timer)))
      (m/timed metrics "test" {:foo "bar"} (Thread/sleep 1) (Thread/sleep 1))
      (is (= 2 (.count timer))))))

(deftest test-counter-metrics
  (testing "Counter metrics registration and usage"
    (let [metrics (m/metrics {:type :simple, :tags {:location "WAW"}}),
          ^Counter counter (m/get-counter metrics "test" {:foo "bar"})]
      (m/add counter)
      (is (= 1.0 (.count counter)))
      (m/add metrics "test" {:foo "bar"})
      (is (= 2.0 (.count counter))))))

(deftest test-gauge-metrics
  (testing "Gauge metrics registration and usage"
    (let [data (atom [1 2 3])
          metrics (m/metrics {:type :simple, :tags {:location "WAW"}}),
          gauge (m/defgauge metrics "test" {:foo "bar"} (count @data))]
      (is (= 3.0 (.value gauge)))
      (swap! data conj 4)
      (is (= 4.0 (.value gauge))))))

