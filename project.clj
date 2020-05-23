(defproject io.resonant/micrometer-clj "0.0.2-SNAPSHOT"
  :description "Clojure wrappers for Micrometer library"
  :url "http://resonant.io/libs/micrometer"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}

  :dependencies
  [[org.clojure/clojure "1.10.1"]
   [io.micrometer/micrometer-core "1.5.1"]
   [io.micrometer/micrometer-registry-prometheus "1.5.1" :scope "provided"]
   [io.micrometer/micrometer-registry-elastic "1.5.1" :scope "provided"]
   [io.micrometer/micrometer-registry-opentsdb "1.5.1" :scope "provided"]
   [io.micrometer/micrometer-registry-graphite "1.5.1" :scope "provided"]
   [io.micrometer/micrometer-registry-influx "1.5.1" :scope "provided"]
   [io.micrometer/micrometer-registry-jmx "1.5.1" :scope "provided"]
   [io.micrometer/micrometer-registry-azure-monitor "1.5.1" :scope "provided"]
   [io.micrometer/micrometer-registry-cloudwatch2 "1.5.1" :scope "provided"]
   [io.micrometer/micrometer-registry-atlas "1.5.1" :scope "provided"]
   [io.micrometer/micrometer-registry-statsd "1.5.1" :scope "provided"]
   [io.micrometer/micrometer-registry-new-relic "1.5.1" :scope "provided"]
   [io.micrometer/micrometer-registry-appoptics "1.5.1" :scope "provided"]
   [io.micrometer/micrometer-registry-datadog "1.5.1" :scope "provided"]
   [io.micrometer/micrometer-registry-stackdriver "1.5.1" :scope "provided"]
   [io.micrometer/micrometer-registry-ganglia "1.5.1" :scope "provided"]
   [io.micrometer/micrometer-registry-dynatrace "1.5.1" :scope "provided"]
   [io.micrometer/micrometer-registry-wavefront "1.5.1" :scope "provided"]
   [io.micrometer/micrometer-registry-humio "1.5.1" :scope "provided"]
   [io.micrometer/micrometer-registry-kairos "1.5.1" :scope "provided"]
   [io.micrometer/micrometer-registry-signalfx "1.5.1" :scope "provided"]]

  :plugins
  [[jonase/eastwood "0.3.6"]
   [lein-kibit "0.1.8"]
   [lein-ancient "0.6.15"]
   [lein-cloverage "1.1.2"]
   [lein-nvd "1.4.0"]]

   :repl-options {:init-ns io.resonant.micrometer})
