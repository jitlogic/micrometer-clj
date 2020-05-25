# micrometer-clj

Clojure wrapper for java micrometer library. Provides several functions and macros wrapping micrometer meters.

Latest jar:

[![Clojars Project](https://clojars.org/io.resonant/micrometer-clj/latest-version.svg)](https://clojars.org/io.resonant/micrometer-clj) 

Import micrometer namespace:

```clojure
(ns example 
  (:require [io.resonant.micrometer :as m]))
```

## Creating meter registry

Meter registries are created using single `metrics` function supplied with configuration. Creating and using simple 
in-memory meter registry:

```clojure
(m/configure {:type :simple, :tags {:foo "BAR"}})

(defn -main [& args]
  (m/timed ["some.metric" {:baz "BAG"}]
    (Thread/sleep 3000)))
```

When using global meter registry is not desired, it is possible to declare own registry using `metrics` function and
create custom meters using `get-*` functions:

```clojure
(let [registry (m/meter-registry {:type :prometheus})
      timer    (m/get-timer registry "frobnication.time" {:location "WAW"} 
                {:description "Frobnication request handling"})
      errors   (m/get-counter registry "frobnication.errors" {:location "WAW"} 
                {:description "Number of frobnication errors", :base-unit "err"})]
  (m/timed [timer] 
    (try
      (frobnicate)
    (catch Exception _
      (m/add-counter errors 1)))))
``` 

## Listing and querying meters

Function `list-meters` will return names of all meters in a registry:

```clojure
(m/list-meters)  ; there is also variant that accepts "registry" parameter
{:names ["process.uptime" "jvm.gc.max.data.size" "jvm.threads.peak" ... "jvm.threads.daemon"]}
```

In order to query specific meter use `query-meters` function:

```clojure
(m/inspect-meter "jvm.memory.used") ; there is also variant that accepts "registry" parameter
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
(m/query-meter registry "jvm.memory.used" {"area" "heap"})
{:name "jvm.memory.used",
 :measurements ({:statistic "VALUE", :value 2.18463168E8}),
 :availableTags {"area" ["heap"], "id" ["G1 Eden Space" "G1 Survivor Space" "G1 Old Gen"]},
 :description "The amount of used memory",
 :baseUnit "bytes"}
```

## More information

For more detailed documentation, see following documents:

* [REGISTRY](doc/REGISTRY.md) - creating and configuring meter registry, information about all supported implementations;

* [METERS](doc/METERS.md) - creating and using meters of various types;

* [DRIVERS](doc/DRIVERS.md) - various meter registry backends list and configuration options;


## License

Copyright © Rafal Lewczuk 2020 rafal.lewczuk@jitlogic.com

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
