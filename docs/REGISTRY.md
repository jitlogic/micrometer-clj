# Constructing meter registry

In `micrometer-clj` metrics registry is created using `metrics` function, for example:

```clojure
(def my-metrics (metrics {:type :simple}))
```

User can now refer to `METRICS` when calling other functions or setup newly created registry as global:

```clojure
(configure my-metrics)        ; use previously declared function
(configure {:type :simple})   ; if you don't want to declare registry as a variable somewhere
```
Meter registry creation function accepts configuration map with following common keys:

* `:type` - selects registry implementation (eg. in-memory, prometheus etc.);

* `:tags` - common tags that will be added to all created metrics map (string-to-string or keyword-to-string);

* `:jvm-metrics` - list of standard JVM metrics that will be added automatically, currently availble are `:classes`, 
`:memory`, `:gc`, `:threads`, `:jit`, `:heap`; all are enabled by default;

* `:os-metrics` - list of standard OS metrics that will be added automatically, currently available are `:files`, 
`:cpu`, `:uptime`; all are enabled by default;   

* `:rename-tags` - list of tag keys renaming rules; each rule is a map with following keys:

  * `:prefix` - prefix of metric names that - if match - will have tags renamed;
  
  * `:from` - tag key that will be renamed (string or keyword);
  
  * `:to` - new key of matching tag (string or keyword);

* `:ignore-tags` - list of tags (strings or keywords);

* `:replace-tags` - list of tag values replacement rules; each rule is a map with following keys:

  * `:from` - tag key to be processed;
  
  * `:proc` - function that accepts string (current tag value) and returns string (new tag value); can be also string constant;
  
  * `except` - tag values that should be excluded from processing;

* `:meter-filters` - list of filters that can either pass/drop or alter streams of values; see section below;

* `:max-metrics` - limits number of metrics that can be created by meter registry;

* `:tag-limits` - list of metric limiting rules; each rule is a map with following keys:

  * `:prefix` - metric names prefix;
  
  * `:tag` - tag key;
  
  *  `:max-vals` - maximum number for different tag values for given tag;
  
  * `:on-limit` - filter that will process all meter IDs with excess tags; 

* `:val-limits` - list of metric value limiting rules; each rule is a map with following keys:

  * `:prefix` - metric names prefix;
  
  * `:min`, `:max` - minimum and maximum values;

Function `metrics` also accepts keys for specific meter registry implementations.  See [REGISTRIES.md](OUTPUTS.md) for
more details.


## Meter filters

Meter filters can intercept metrics data when processed and can either accept/deny passing samples or modify their names
or tags. Meter filters are processed sequentially and processing ends when some filter denies further sample processing.
In configuration data, filters are represented as maps. Most of filters also define predicate function which accepts
objects of `Meter.Id` class and return `true` or `false`.

* `{:accept p}` - filter will only accept samples for which preficate function `p` returns `true`; 

* `{:deny p}` - filter will drop samples if preficate `p` returns `true`;

* `{:deny-unless p}` - filter will drop samples unless `p` returns `true`;

* `{:raw-filter ffn}` - supplied low level filter function `ffn` accepts `Meter.Id` object and returns one of 
`MeterFilterReply/ACCEPT`, `MeterFilterReply/DENY` or `MeterFilter/NEUTRAL` values;

* `{:raw-map mfn}` - supplied low level map function accepts `Meter.Id` object and returns (possibly altered) `Meter.Id` object;

* `{:dist-stats {....}}` - confgures distribution statistics for  timers and distribution summaries; filter configuration
map can contain following keys:

  * `:name`, `:name-re` - metric name (as string or regular expression);
  
  * `:histogram?` - enables or disables histogram;
  
  * `:percentiles` - percentiles published by this histogram (eg. `[0.5, 0.9, 0.95, 0.99]`);
  
  * `:precision` - histogram precision (in number of meaningful digits, eg. for 1% precision it is 2);
  
  * `:sla` - SLO boundaries (in nanoseconds);
  
  * `:min-val`, `:max-val` - minimum and maximum values of samples;
  
  * `:expiry` - duration after which samples accumulated in histogram are dropped;
  
  * `:buf-len` - sample buffer length;


## Querying registry

Function `list-meters` lists all names in meter registry, for example:

```clojure
; will use global registry
(list-meters)
{:names ["process.uptime"
         "jvm.gc.max.data.size"
          ...
         "process.start.time"
         "jvm.buffer.count"]}
; will use supplied registry
(list-meters my-registry)
```

Function `query-meters` displays information about specific meter:

```clojure
; use global registry
(query-meters "jvm.memory.used")
{:name "jvm.memory.used",
 :measurements ({:statistic "VALUE", :value 9.7760072E7}),
 :availableTags {"area" ["heap" "nonheap"],
                 "id" ["Compressed Class Space"
                       "G1 Eden Space"
                       "CodeHeap 'non-nmethods'"
                       "CodeHeap 'profiled nmethods'"
                       "CodeHeap 'non-profiled nmethods'"
                       "Metaspace"
                       "G1 Survivor Space"
                       "G1 Old Gen"]},
 :description "The amount of used memory",
 :baseUnit "bytes"}

; filter by tag
(query-meters "jvm.memory.used" {"area" "heap"})
{:name "jvm.memory.used",
 :measurements ({:statistic "VALUE", :value 1.03243072E8}),
 :availableTags {"area" ["heap"], "id" ["G1 Eden Space" "G1 Survivor Space" "G1 Old Gen"]},
 :description "The amount of used memory",
 :baseUnit "bytes"}

; use supplied registry
(query-meters my-registry "jvm.memory.used" {"area" "heap"})
``` 

## Accessing native MeterRegistry object

Function `metrics` returns a map with following keys:

* `:config` - configuration data passed to `metrics` function; 

* `:components` - only for composite registry, it contains map of subordinate registries that make up composite registry;
key are as keys from `:configs` conifguration map and values are similar maps including all keys except `:metrics`; 

* `:metrics` - atom containing cache with created meters; this is used to speed up all meter creating/feeding functions;
cache has two-level structure, where first level is keyed by meter name and second level by tags;

* `:registry` - native registry object (descendant of `MeterRegistry` class);

* `:type` - meter registry type (the same as in `:type` of configuration passed to `metrics` function);

So you can access native `MeterRegistry` object using `(:registry my-registry)` or access individual subordinate registries
using `(-> my-registry :components :some-component :registry)`.

# Meter registry backends

Micrometer library provides ready to use meter registry implementations that integrate directly with various monitoring
systems. Specific backend is selected automatically by `metrics` function but most of them have extra dependencies that
have to be included in your `project.clj`.   

In following sections various types of meter registries will be described along with configuration options specific
for them.


## Common options for push registries

There are registries that actively push their data to monitoring systems. Those registries have to run their own threads
and have following common configuration settings:

* `:step` - reporting frequency (default 60 seconds);

* `:enabled?` - if true, data publishing is enabled (default: true);

* `:num-threads` - number of reporting threads;

* `:connect-timeout` - connection timeout (milliseconds);

* `:read-timeout` - read timeout (milliseconds);

* `:batch-size` - limits number of measurements sent in each request to monitoring system;


## Simple in-memory registry 

This basic implementation is available in core library. Is is simple in-memory implementation that does not integrate
with any specific monitoring system. It can be queried via 

Creating simple registry:

```clojure
(configure 
  {:type :simple})
```

## Composite registry

Composite registry binds several registries together and presents them as single entity: 

```clojure
(configure 
  {:type :composite,
   :components {
     :local {:type :simple},
     :cloud {:type :prometheus}}})  
```

All component registries will be accessible via `:components` key of returned object.


## AppOptics

Sends metric data to AppOptics service.

Add POM dependency: `[io.micrometer/micrometer-registry-appoptics "1.5.1"]`

```clojure
(configure
  {:type :appoptics
   :api-token "79d84fcf-ef37-4297-9c98-9e912fed7556"
   :host-tag "my-app.svc.kubernetes.local" })
```

It is a push registry and accepts common settings: `:step`, `:enabled?`, `:num-threads`, `:connect-timeout`, 
`:read-timeout` and `:batch-size`. 

Custom settings:

* `:api-token` - API token to authorize data submission requests (required);

* `:host-tag` - unique identifier of monitored application (required);

* `:uri` - data collector endpoint URI (default: `https://api.appoptics.com/v1/measurements`);

* `:floor-times?` - if true, all timestamps will be floored to `:step`;


## Atlas

Sends metrics to Netflix Atlas server.

Add POM dependency: `[io.micrometer/micrometer-registry-atlas "1.5.1"]`

```clojure
(configure
  {:type :atlas,
   :auto-start? true,
   :url "http://atlas.svc.kubernetes.local:7101/api/v1/publish"})
```

It is a push registry and accepts common settings: `:step`, `:enabled?`, `:num-threads`, `:connect-timeout`  and `:batch-size`. 

Custom settings:

* `:url` - URL to Atlas server metrics publish endpoint (required, default: `http://localhost:7101/api/v1/publish`);

* `:meter-ttl` - time duration after which inactive meters stop reporting (default: `15m`);

* `:auto-start?` - if true, sender threads will start automatically (default: `true`);

* `:lwc-step` - reporting frequency for streaming to atlas LWC (must be less or equal than `:step`, default: `5s`); 

* `:lwc-enabled?` - if true, Atlas LWC streaming is enabled (default: `false`); for more details see Atlas documentation;

* `:lwc-ignore-publish-step?` - if true if expressions with the same step size as Atlas publishing should be ignored
for streaming; for more details see Atlas documentation;

* `:config-refresh-frequency` - config refresh frequency for LWC service (default: `10s`);

* `:config-ttl` - config validity duration (default: `150s`);

* `:config-url` - LWC config information endpoint URL (default: `http://localhost:7101/lwc/api/v1/expressions/local-dev`);

* `:eval-url` - LWC service URL to evaluate data for subscription (default: `http://localhost:7101/lwc/api/v1/evaluate`);

* `:common-tags` - map of common tags that will be attached to all metrics;

* `:propagate-warnings?` - if true, will treat warnings as errors (default: `false`);

* `:max-atlas-metrics` - if set, will limit number of distinct metrics to be sent;

* `:gauge-polling-frequency` - how often registered gauges should be polled (default: `10s`);


## Azure

Sends data to Azure Monitor service in Azure cloud. Uses client library provided by Microsoft and its configuration 
properties, so here we set only instrumentation key for monitored application:

Add POM dependency: `[io.micrometer/micrometer-registry-azure "1.5.1"]`

```clojure
(configure
  {:type :azure
   :instrumentation-key "25678f44-b958-466e-9ccc-5a622484620b"})
```

It is push registry and accepts common settings: `:step`, `:enabled?`, `:num-threads`, `:connect-timeout`, 
`:read-timeout` and `:batch-size`. 

Custom settings:

* `:instrumentation-key` - instrumentation key as configured in azure monitor (required);


## Cloudwatch

Sends data to Amazon Cloudwatch service. As part of configuration it needs CloudWatch client created using
`CloudWatchAsyncClient.create()` method. 

Add POM dependency: `[io.micrometer/micrometer-registry-cloudwatch2 "1.5.1"]`

```clojure
(configure
  {:type :cloudwatch})
```

It is push registry and accepts common settings: `:step`, `:enabled?`, `:num-threads`, `:connect-timeout`, 
`:read-timeout` and `:batch-size`. 

Custom settings:

* `:namespace` - cloud watch namespace;


## Datadog

Sends data to Datadog service. 

Add POM dependency: `[io.micrometer/micrometer-registry-datadog "1.5.1"]`

```clojure
(configure 
  {:type :datadog
   :api-key "f7cf4d9d-0755-4e5b-bc3b-fa0b54f14ea6"
   :application-key "my-app.svc.mycompany.com"})
```
It is push registry and accepts common settings: `:step`, `:enabled?`, `:num-threads`, `:connect-timeout`, 
`:read-timeout` and `:batch-size`. 

Custom settings:

* `:api-key` - datadog API key (required);

* `:application-key` - datadog application key;

* `:host-tag` - tag mapped to `host` when shipping metrics to datadog;

* `:url` - URI to ship metrics to (default: `https://api.datadoghq.com`); 

* `:descriptions?` - when true, meter descriptions will be sent to datadog;


## Dynatrace

Sends data to Dynatrace system.

Add POM dependency: `[io.micrometer/micrometer-registry-dynatrace "1.5.1"]`

```clojure
(configure
  {:type :dynatrace
   :url "https://url-to-dynatrace-collector"
   :api-token "abcdefjhij1234567890"
   :device-id "..."})
```

It is push registry and accepts common settings: `:step`, `:enabled?`, `:num-threads`, `:connect-timeout`, 
`:read-timeout` and `:batch-size`. 

Custom settings:

* `:api-token` - dynatrace API token;

* `:url` - URL to dynatrace collector;

* `:device-id` - dynatrace device ID;

* `:technology-type` - technology type label, eg. `java` or `clojure`;

* `:group` - application group name;


## Elastic

Sends data to Elastic Search server.

Add POM dependency: `[io.micrometer/micrometer-registry-elastic "1.5.1"]`

```clojure
(configure
  {:type :elastic
   :url "https://elastic.svc.cloud.mycomapny.com:9200"
   :index "metrics"
   :username "micrometer"
   :password "seCR3t"})
```

It is push registry and accepts common settings: `:step`, `:enabled?`, `:num-threads`, `:connect-timeout`, 
`:read-timeout` and `:batch-size`. 

Custom settings:

* `:url` - URL to Elastic server storing data (mandatory);

* `:index` - name of index used to write metrics to (default: `metrics`);

* `:index-date-format` - used when rolling indexes, (default: `yyyy-MM`);

* `:index-date-separator` - string separating index name from date suffix (default: `-`);

* `:username`, `:password` - credentials to use when authentication is enabled in elastic server;

* `:timestamp-field-name` - timestamp field name (default: `@timestamp`); 

* `:auto-create-index?` - if true and index does not exist, it will be created (default: `true`);

* `:pipeline` - ingest pipeline name (default: `pipeline`);

* `:document-type` - document type (default: `doc`); note that this feature is deprecated in newer versions of Elastic;


## Ganglia

Sends data to Ganglia over UDP.

Add POM dependency: `[io.micrometer/micrometer-registry-ganglia "1.5.1"]`

```clojure
(configure
  {:type :ganglia
   :host "224.2.3.4"
   :port 8649})
```

It is push registry and accepts common settings: `:step`, `:enabled?`, `:num-threads`, `:connect-timeout`, 
`:read-timeout` and `:batch-size`. 

Custom settings:

* `:duration-units` - time unit for duration values: `:nanoseconds`, `:microseconds`, `:milliseconds` or `:seconds`,
(default value: `:milliseconds`);

* `:addressing-mode` - UDP sender addressing mode: `:unicast` or `:multicast` (default: `:multicast`);

* `:host`, `:port` - host address (can be multicast address) and port name; 

* `:ttl` - TTL for UDP packets; this is important for multicast communication, eg. when set to `1`, data will not be
transmitted outside of LAN machine is connected to;


## Graphite

Sends data to Graphite server.

Add POM dependency: `[io.micrometer/micrometer-registry-graphite "1.5.1"]`

```clojure
(configure
  {:type :graphite
   :host "graphite.intranet.mycompany.com"})
```

It is push registry and accepts common settings: `:step`, `:enabled?`;

Custom settings:

* `:host`, `:port` - host name and port number (required);

* `:graphite-tags-enabled?` - when true, graphite tags will be used rather than hierarchical naming convention (default: `true`);

* `:tags-as-prefix` - list of tags used to construct metric prefix when hierarchical convention is used;

* `:rate-units` - time units used when submitting rate metrics: `:nanoseconds`, `:microseconds`, `:milliseconds` or
`:seconds` (default: `:milliseconds`);

* `:duration-units` - time units used when submitting duration metrics: `:nanoseconds`, `:microseconds`, `:milliseconds` or
`:seconds` (default: `:milliseconds`);

* `:protocl` - communication protocol: `:plaintext`, `:udp` or `:pickled` (default: `:pickled`);


## Humio

Sends metrics to Humio cloud service.

Add POM dependency: `[io.micrometer/micrometer-registry-humio "1.5.1"]`

```clojure
(configure
  {:type :humio
   :api-token "44f575d5-b591-47c7-a01e-71d6771280b6"})
```

It is push registry and accepts common settings: `:step`, `:enabled?`, `:num-threads`, `:connect-timeout`, 
`:read-timeout` and `:batch-size`. 

Custom settings:

* `:api-token` - API token for authentication in Humio service;

* `:tags` - tags which define which datasource to store metrics in (see Humio documentation);

* `:url` - URL to Humio cloud API service (default: `https://cloud.humio.com`)


## Influx

Sends metrics to InfluxDB time series database.

Add POM dependency: `[io.micrometer/micrometer-registry-influx "1.5.1"]`

```clojure
(configure
  {:type :influx
   :url "http://influx.svc.kubernetes.local:8086"
   :username "metrics"
   :password "s3CR3t"})
```

It is push registry and accepts common settings: `:step`, `:enabled?`, `:num-threads`, `:connect-timeout`, 
`:read-timeout` and `:batch-size`. 

Custom settings:

* `:url` - URL to influxdb server (required, default: `http://localhost:8086`);

* `:db` - database name (default: `metrics`);

* `:auto-create-db?` - if set to true, will create database if non-existent (default: `true`);

* `:username`, `:password` - credentials to be used to authenticate to influxdb server;

* `:consistency` - write consistency for submitted data if influxdb is clustered: `:all`, `:one`, `:any`, `:quorum` 
(default: `:one`);

* `:retention-policy` - influx retention policy name for stored data (if not set, `DEFAULT` will be used);

* `:retention-duration` - retention duration for stored data (eg. `2h` or `52w`);

* `:retention-replication-factor` - how many copies of data will be stored (must be `1` for single node instances);

* `:retention-shard-duration` - time range covered by shard group (eg. `2h`, `52w`);

* `:compressed?` - if true, transmitted data will be GZIP compressed (default: `true`);


## JMX

Presents data via JMX in local MBean server.

Add POM dependency: `[io.micrometer/micrometer-registry-jmx "1.5.1"]`

```clojure
(configure
  {:type :jmx
   :domain "micrometer"})
```

Custom settings:

* `:domain` - JMX domain used in object names of published metrics;

* `:step` - reporting frequency;


## Kairos

Sends metrics to Kairos monitoring system.

Add POM dependency: `[io.micrometer/micrometer-registry-jmx "1.5.1"]`

```clojure
(configure
  {:type :kairos
   :url "http://kairos.svc.kubernetes.local:8080/api/v1/datapoints"
   :username "metrics"
   :password "seCR3t"})
```

It is push registry and accepts common settings: `:step`, `:enabled?`, `:num-threads`, `:connect-timeout`, 
`:read-timeout` and `:batch-size`. 

Custom settings:

* `:url` - URI to data submission endpoint (default: `http://localhost:8080/api/v1/datapoints`);

* `:username`, `:password` - credentials to authenticate in kairos system (required); 


## New Relic

Sends metrics to New Relic service.

Add POM dependency: `[io.micrometer/micrometer-registry-newrelic "1.5.1"]`

```clojure
(configure
  {:type :newrelic
   :account-id "123456"
   :api-key "9a4186e2-7fd2-48ec-a9a3-c2b29b5fff27"}) 
```

It is push registry and accepts common settings: `:step`, `:enabled?`, `:num-threads`, `:connect-timeout`, 
`:read-timeout` and `:batch-size`. 

Custom settings:

* `:url` - URL to insights collector (default: `https://insights-collector.newrelic.com`);

* `:api-key` - insigts collector API key (required);

* `:account-id` - New Relic account ID (required);

* `:meter-name-event-type-enabled?` - if true, will use meter names as New Relic eventType values (default: `false`);

* `:event-type` - default eventType (used if meter names are not used);

* `:client-provider-type` - either `:insights_api` or `:insights_agent`;


## OpenTSDB

Sends metrics to OpenTSDB server.

Add POM dependency: `[io.micrometer/micrometer-registry-opentsdb "1.5.1"]`

```clojure
(configure
  {:url "http://opentsdb.intranet.mycompany.com:4242/api/put"
   :username "metrics"
   :password "s3CR3t"})
```

It is push registry and accepts common settings: `:step`, `:enabled?`, `:num-threads`, `:connect-timeout`, 
`:read-timeout` and `:batch-size`. 

Custom settings:

* `:url` - URI to data submission endpoint (default: `http://localhost:4242/api/put`);

* `:username`, `:password` - credentials to authenticate in opentsdb server; 


## Prometheus

Add POM dependency: `[io.micrometer/micrometer-registry-prometheus "1.5.1"]`

Keeps metrics data in memory and provides method to generate Prometheus scrape data. Note that this implementation does
not serve HTTP endpoint for Prometheus scrapper, it provides `scrape` multimethod implementation instead that can be
used by application developer who is responsible for publishing this data via http(s).

```clojure
(configure
  {:type :prometheus})
```

In order to generate data for prometheus, use `scrape` multimethod:

```clojure
; implement this as URI in web handler instead of using println
(println (scrape *registry*))
```

Custom settings:

* `:prefix` - string that will be prepended to names of all metrics sent (default: `metrics`);

* `:step` - window size when computing timed or distribution statistics (default: `1m`);

* `:descriptions?` - if true, description hints will be generated;

* `:histogram-flavor` - histogram type backing DistributionSummary and Timer meters: `:Prometheus` or `:VictoriaMetrics`,
note that these keyword are case sensitive (default: `Prometheus`);


## SignalFX

Sends metrics to SignalFX service.

Add POM dependency: `[io.micrometer/micrometer-registry-signalfx "1.5.1"]`

```clojure
(configure
  {:type :signalfx
   :access-token "3d0146bb-56e5-46c0-8768-64508e2914fb"
   :source "myapp"})
```

It is push registry and accepts common settings: `:step`, `:enabled?`, `:num-threads`, `:connect-timeout`, 
`:read-timeout` and `:batch-size`. 

Custom settings:

* `:access-token` - access token for SignalFX service (required);

* `:url` - URL to SignalFX ingestion service (default: `https://ingest.signalfx.com`);

* `:source` - application identifier (required, should be unique for each application); 


## Stackdriver

Sends metrics to Google Stackdriver service.

Add POM dependency: `[io.micrometer/micrometer-registry-stackdriver "1.5.1"]`

```clojure
(configure
  {:type :stackdriver
   :project-id "myapp"
   :credentials "/tmp/credentials.dat"})
```

It is push registry and accepts common settings: `:step`, `:enabled?`, `:num-threads`, `:connect-timeout`, 
`:read-timeout` and `:batch-size`. 

Custom settings:

* `:credentials` - path to credentials file;

* `:project-id` - project ID (as reported to stackdriver);

* `:resource-labels` - map of resource labels; see stackdriver documentation;

* `:resource-type` - resource type; see stackdriver documentation;


## Statsd

Sends metrics to statsd daemon

Add POM dependency: `[io.micrometer/micrometer-registry-statsd "1.5.1"]`

```clojure
(configure
 {:type :statsd
   :host "statsd.svc.kubernetes.local"
   :port 8125})
```

Custom settings:

* `:host` - statd server address (required);

* `:port` - statsd service port number (default: `8125`);

* `:protocol` - either `:udp` or `:tcp` (default: `:udp`);

* `:enabled?` - enables or disables data submission;

* `:flavor` - one of `:etsy`, `:datadog`, `:telegraf`, `:sysdig` (default: `:datadog`);

* `:max-packet-length` - limits packet length for UDP communication; should be less than MTU between monitored application
and statsd server - `1431` for fast ethernet, `8932` for gigabit ethernet, `512` for public internet (default: `1400`);

* `:polling-frequency` - determines how often gauges will be polled (default: `10s`);

* `:queue-size` - send queue size (default: no limits);

* `:step` - step size to use in computing windowed statistics (default: `60s`);

* `:publish-unchanged-meters?` - if true, unchanged meters will be sent each time (default: `true`);

* `:buffered?` - if true, measurements will be buffered and sent when max packet length is reached or until polling
frequency is reached (default: `true`);


## Wavefront

Sends metrics to wavefront service.

Add POM dependency: `[io.micrometer/micrometer-registry-wavefront "1.5.1"]`

```clojure
(configure
  {:type :wavefront,
   :api-token "e6cb63cd-5b14-4bd4-9486-8ff6cfddd830",
   :source "myapp"})
```

It is push registry and accepts common settings: `:step`, `:enabled?`, `:num-threads`, `:connect-timeout`, 
`:read-timeout` and `:batch-size`. 

Custom settings:

* `:api-token` - wavefront API token (required);

* `:url` - URL to wavefront service (default: `https://longboard.wavefront.com`);

* `:source` - application identifier (required);

* `:report-minute-distribution?` - report histogram distributions into minute intervals;

* `:report-hour-distribution?` - report histogram distributions into hour intervals;

* `:report-day-distributeion?` - report histogram distributions into day intervals;




