# micrometer-clj

Clojure wrapper for java micrometer library. Provides several functions and macros wrapping micrometer meters.

## Creating meter registry

Meter registries are created using single `metrics` function supplied with configuration. Creating and using simple 
in-memory meter registry:

```clojure
(ns example 
  (:require [io.resonant.micrometer :as m]))

(def registry (m/metrics {:type :simple, :tags {:foo "BAR"}}))

(defn -main [& args]
  (m/timed registry "some.metric" {:baz "BAG"}
    (Thread/sleep 3000)))
```

Registry implementation and parameters are chosen solely from configuration data, following keys can be set:

* `:type` - registry implementation: `:simple`, `:prometheus`, `:composite`;

* `:jvm-metrics` - list of standard JVM metrics to be added: `:classes`, `:memory`, `:gc`, `:threads`, `:jit`, `:heap`
are actually available, if parameter not provided all will be included by default;  

* `:os-metrics` - list of OS-related metrics to be added: `:files`, `:cpu`, `:uptime` are actually available, if 
parameter not provided all will be included by default;

* `:tags` - tags that will be added to all meters in this registry (map keywords -> strings);


## Prometheus meter registry

Prometheus meter registry has `:type` set to `:prometheus`. It accepts two additional configuration parameters:

* `:step` - step size (reporting frequency);

Note that prometheus registry will not expose metrics by itself, it os up to hosting application to expose endpoint
where data will scraped. There is `scrape` function that will return formatted data to be returned:

```clojure
(println (m/scrape registry))
```  

## Composite meter registry

This one can be used to compose multiple registries into a single one. Configuration looks like this:

```clojure
{:type :composite
 :components {
   :foo {:type :simple}
   :bar {:type :prometheus}
 }
}
```

## Using timers

Timers can be created using `get-timer` function:

```clojure
(def my-timer (m/get-timer registry "some.timer" {:foo "BAR"}))
```

Function accepts metric name (must be unique inside registry) and tags (can be empty). Note that meters are cached, so
calling this function twice with the same parameters will return the same timer object.

Resulting meter can be used in `with-timer` macro:

```clojure
(m/with-timer my-timer
  (Thread/sleep 1000))
```

Also, there is `timed` macro that combines both:

```clojure
(m/timed registry "some.timer" {:foo "BAR"}
  (Thread/sleep 1000))
```

## Using counters

Counters are created using `get-counter` function:

```clojure
(def my-counter (m/get-counter registry "some.counter" {:baz "BAG"}))
```

Counters are consumed by `inc-counter` function:

```clojure
(m/inc-counter my-counter)
(m/inc-counter my-counter 42)
(m/inc-counter registry "other.counter" {:foo "BAR"})
(m/inc-counter registry "other.counter" {:foo "BAR"} 0.42)
```

Two variants of `inc-counter` function can dynamically create necessary counters. 


## Using gauges

Gauges always return current state of some component. Gauges can be created using `get-gauge` function:

```clojure
(def tracked-value (atom 0))
(def my-gauge (m/get-gauge registry "some.gauge" {:foo "BAR"} (fn [] @tracked-value))) 
```

Gauge will always return current value of 


## Listing and querying meters

Function `list-meters` will return names of all meters in a registry:

```clojure
(list-meters registry)
{:names ["process.uptime" "jvm.gc.max.data.size" "jvm.threads.peak" ... "jvm.threads.daemon"]}
```

In order to query specific meter use `query-meters` function:

```clojure
(query-meters registry "jvm.memory.used")
{:name "jvm.memory.used",
 :measurements ({:statistic "VALUE", :value 2.90950008E8}),
 :availableTags {"area" ["heap" "nonheap"],
                 "id" ["Compressed Class Space" "G1 Eden Space" "CodeHeap 'non-nmethods'" "CodeHeap 'profiled nmethods'"
                       "CodeHeap 'non-profiled nmethods'" "Metaspace" "G1 Survivor Space" "G1 Old Gen"]},
 :description "The amount of used memory",
 :baseUnit "bytes"}
```

It is possible to query meters for specific tag:

```clojure
(query-meters registry "jvm.memory.used" "area" "heap")
{:name "jvm.memory.used",
 :measurements ({:statistic "VALUE", :value 2.18463168E8}),
 :availableTags {"area" ["heap"], "id" ["G1 Eden Space" "G1 Survivor Space" "G1 Old Gen"]},
 :description "The amount of used memory",
 :baseUnit "bytes"}
```


## License

Copyright © Rafal Lewczuk 2020 rafal.lewczuk@jitlogic.com

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
