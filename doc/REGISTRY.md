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


