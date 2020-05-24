# Creating and using meters

Micrometer library provides several kinds of meters: timers, counters, gauges, distribution summaries. Each metric
has following attributes:

* registry - registry in which given meter is registered;

* name - string as uniquely identifies metric (which can consist of data from multiple meters with various tags);

* type - timer, function timer, counter, function counter, gauge or distribution summary;

* tags - pairs of keys and values that allow splitting resulting metric into multiple dimensions;

* options - additional options that alter aggregated data / data sent to underlying monitoring system;

Common options that - depending on meter type - can be attached to created metrics are:

* `:description` - human readable metric description;

* `:base-unit` - base units, eg. ` B` (bytes);

* `:histogram?` - enables or disables histogram;
  
* `:percentiles` - percentiles published by this histogram (eg. `[0.5, 0.9, 0.95, 0.99]`);
  
* `:precision` - histogram precision (in number of meaningful digits, eg. for 1% precision it is 2);
  
* `:sla` - SLO boundaries (in nanoseconds);
  
* `:min-val`, `:max-val` - minimum and maximum values of samples;
  
* `:expiry` - duration after which samples accumulated in histogram are dropped;
  
* `:buf-len` - sample buffer length;

Depending on metric type, all or only some of above will be applicable. For more details see sections below.

Note that all constructor functions cache meters using metric name and tags. When constructor function is called  
second time with the same name and tags but different options, it will simply return original meter but ignore supplied
options.
 

## Timers

Timers can be created using `get-timer` function:

```clojure
; will use globl registry
(def my-timer1 (m/get-timer "some.timer" {:foo "BAR"}))

; will use supplied registry
(def my-timer2 (m/get-timer my-registry "other.timer" {:foo "BAR"}))

; as above, with custom options
(def my-timer3 (m/get-timer my-registry "another.timer" {:foo "BAR"} {:description "My custom timer"}))
```

Function accepts metric name (must be unique inside registry) and tags (can be empty). Note that meters are cached, so
calling this function twice with the same parameters will return the same timer object.

All common options except `:base-unit` are available for timers creation.

Resulting timers can be used in `with-timer` macro:

```clojure
(m/with-timer my-timer
  (do-something)
  (do-other-things))
```

Also, there is `timed` macro that combines `get-timer` and `with-timer` in one:

```clojure
; will use global registry
(m/timed ["some.timer" {:foo "BAR"}]
  (Thread/sleep 1000))

; will use supplied registry
(m/timed [my-registry "some.timer" {:foo "BAR"}]
  (Thread/sleep 1000))

; as above, with custom options
(m/timed [my-registry "some.timer" {:foo "BAR"} {:percentiles [0.5,0.9,0.95,0.99]}]
  (Thread/sleep 1000))
```

It is also possible to feed timer manually - this is useful when duration is already known:

```clojure
; using already created timer
(m/add-timer my-timer (- t1 t0))

; will create timer in global registry
(m/add-timer "other.timer" {:baz "BAG"} some-duration)

; will use supplied registry
(m/add-timer my-registry "other.timer" {:baz "BAG"} some-duration)

; as above, with custom options
(m/add-timer my-registry "other.timer" {:baz "BAG"} {:description "Some Duration"} some-duration)
```

Supplied duration can be either number (milliseconds) or `java.time.Duration` object.


## Long running task timers

Long running task timers are specialized to track tasks currently executing (in flight). Then any of those tasks end,
timer will drop any data about it. Task timers can be created using `get-task-timer` function:

```clojure
; will use globl registry
(def my-timer1 (m/get-task-timer "some.timer" {:foo "BAR"}))

; will use supplied registry
(def my-timer2 (m/get-task-timer my-registry "other.timer" {:foo "BAR"}))

; as above, with custom options
(def my-timer3 (m/get-task-timer my-registry "another.timer" {:foo "BAR"} {:description "My custom timer"}))
```

Resulting timers can be used in `with-task-timer` macro:

```clojure
(m/with-task-timer my-timer
  (do-something)
  (do-other-things))
```

Also, there is `task-timed` macro that combines `get-task-timer` and `with-task-timer` into one:

```clojure
; will use global registry
(m/task-timed ["some.timer" {:foo "BAR"}]
  (Thread/sleep 1000))

; will use supplied registry
(m/task-timed [my-registry "some.timer" {:foo "BAR"}]
  (Thread/sleep 1000))

; as above, with custom options
(m/task-timed [my-registry "some.timer" {:foo "BAR"} {:percentiles [0.5,0.9,0.95,0.99]}]
  (Thread/sleep 1000))
```


## Function timers

Function timers can be used to track objects that keep their own timing statistics. It keeps tracked object and 
two functions: count function that returns number of calls handled by tracked object and time function that returns
total time spent by tracked object to handle all calls.

```clojure
(def my-service (MyService.)) ; here is our custom service object that has getTotalCalls() and getTotalTime() methods
(defn my-cfn [svc] (.getTotalCalls svc)) ; call count will be coerced to long integer
(defn my-tfn [svc] (.getTotalTime svc))  ; total time will be coerced to double

; will use global registry and functions defined above and global registry
(def my-timer1 (m/get-function-timer "some.timer" {:foo "BAR"} my-service my-cfn my-tfn :MILLISECONDS))

; as above, will use supplied registry
(def my-timer2 (m/get-function-timer my-registry "other.timer" {:foo "BAR"} my-service my-cfn my-tfn :MILLISECONDS))

; as above, will use supplied registry and custom options
(def my-timer3 (m/get-function-timer my-registry "another.timer" {:foo "BAR"} {:description "Very important metric"} 
  my-service my-cfn my-tfn :MILLISECONDS))
``` 

There is no need to manually feed function timer with data, so no more functions for timer handling are defined.

## Counters

Counters are created using `get-counter` function:

```clojure
; will use global registry
(def my-counter1 (m/get-counter "some.counter" {:baz "BAG"}))

; as above, will use supplied registry
(def my-counter2 (m/get-counter my-registry "some.counter" {:baz "BAG"}))

; as aobve, will use supplied registry and set custom options
(def my-counter3 (m/get-counter my-registry "some.counter" {:baz "BAG"} {:description "Incoming traffic", :base-unit "B"}))
```

Counters can be used by `add-counter` function:

```clojure
; uses previously created counter
(m/add-counter my-counter 42)

; will use global registry; note that counter is represented as double and can be incremented fraction at a time
(m/add-counter "other.counter" {:foo "BAR"} 0.42)

; will use supplied registry; 
(m/add-counter my-registry "other.counter" {:foo "BAR"} 0.42)

; will set 
(m/add-counter my-registry "other.counter" {:foo "BAR"} {:description "Storage utilization", :base-unit "MB"} 0.42)
```

Two variants of `add-counter` function can dynamically create necessary counters. 


## Function counters

Function counters can be used to track objects that maintain their own counters:

```clojure
(def my-service (MyService.)) ; here is our custom service object that has getTotalCalls() and getTotalTime() methods
(defn my-cfn [svc] (.getTotalCalls svc)) ; call count will be coerced to long integer

; will use global registry and functions defined above and global registry
(def my-counter (m/get-function-counter "some.counter" {:foo "BAR"} my-service my-cfn))

; will use global registry and function defined above and global registry
(def my-counter (m/get-function-counter my-registry "some.counter" {:foo "BAR"} my-service my-cfn))

; will use global registry and functions defined above and global registry, set custom options
(def my-counter (m/get-function-counter my-registry "some.counter" {:foo "BAR"} 
  {:description "Service calls", :base-unit "calls"} my-service my-cfn))
```

There is no need to manually feed function timer with data, so no more functions for timer handling are defined.

## Using gauges

Gauges always return current state of some component. Gauges can be created using `get-gauge` function:

```clojure
(def tracked-value (atom 0))
(defn gfn [] @tracked-value)

; use global registry
(def my-gauge (m/get-gauge "some.gauge" {:foo "BAR"} gfn)) 

; use supplied registry
(def my-gauge (m/get-gauge my-registry "some.gauge" {:foo "BAR"} gfn)) 

; use supplied registry, additional options
(def my-gauge (m/get-gauge my-registry "some.gauge" {:foo "BAR"} 
  {:description "Current water level", :base-unit "m"} gfn)) 
```

Gauge will always return current return value of supplied function. 

A convenience macro `defgauge` allows defining gauges providing expression that will be treated as function body:

```clojure
(defgauge ["some.gauge" {:foo "BAR"}] @tracked-value)
(defgauge [my-registry "some.gauge" {:foo "BAR"}] @tracked-value)
(defgauge [my-registry "some.gauge" {:foo "BAR"} {:description "Waterlevel"}] @tracked-value)
```

## Distribution summary

Distribution summaries maintain sample distributions of events and implement some functionalities of histograms.

```clojure
; use global registry
(def my-summary1 (get-summary "some.summary" {:foo "BAR"}))

; use supplied registry
(def my-summary2 (get-summary "some.summary" {:foo "BAR"}))

; use supplied registry and set custom options
(def my-summary3 (get-summary "some.summary" {:foo "BAR"} {:description "Execution times"}))
```

Distriubtion summaries can be fed using `add-summary` function:

```clojure
(add-summary my-summary1 42)
(add-summary "some.summary" {:foo "BAR"} 24)
(add-summary my-registry "some.summary" {:foo "BAR"} 24)
(add-summary my-registry "some.summary" {:foo "BAR"} {:description "FizzBuzz"} 24)
```

