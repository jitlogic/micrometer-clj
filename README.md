# micrometer-clj

Clojure wrapper for java micrometer library. Provides several functions and macros wrapping micrometer meters.

## Creating meter registry

Meter registries are created using single `metrics` function supplied with configuration. Creating and using simple 
in-memory meter registry:

```clojure
(ns example 
  (:require [io.resonant.micrometer :as m]))

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

* [REGISTRY](docs/REGISTRY.md) - creating and configuring meter registry, information about all supported implementations;

* [METERS](docs/METERS.md) - creating and using meters of various types;

* [DRIVERS](docs/DRIVERS.md) - various meter registry backends list and configuration options;


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
