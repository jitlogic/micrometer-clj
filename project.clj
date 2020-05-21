(defproject io.resonant/micrometer-clj "0.0.2-SNAPSHOT"
  :description "Clojure wrappers for Micrometer library"
  :url "http://resonant.io/micrometer"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies
  [[org.clojure/clojure "1.10.1"]
   [io.micrometer/micrometer-core "1.4.1"]
   [io.micrometer/micrometer-registry-prometheus "1.4.1" :scope "provided"]
   [io.micrometer/micrometer-registry-elastic "1.4.1" :scope "provided"]
   [io.micrometer/micrometer-registry-opentsdb "1.4.1" :scope "provided"]
   [io.micrometer/micrometer-registry-graphite "1.4.1" :scope "provided"]
   [io.micrometer/micrometer-registry-influx "1.4.1" :scope "provided"]]
  :repl-options {:init-ns io.resonant.micrometer})
