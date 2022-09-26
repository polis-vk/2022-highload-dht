# Report

Скрипт Lua для put запросов:
```lua
raw_path = "/v0/entity?id="
count = -1
request = function()
    count = count + 1
    path = raw_path .. count
    return wrk.format("PUT", path, {"Content-Type: text/plain"}, string.rep("highload is a best subject!", 322))
end
```

Скрипт Lua для get запросов:
```lua
raw_path = "/v0/entity?id="
count = 0
request = function()
    path = raw_path .. count
    count = count + 1
    return wrk.format("GET", path)
end
```

Команда wrk2 для put запросов:
```shell
wrk2 -d 60 -t 1 -c 1 -R $rate -s put.lua http://localhost:25565
```

Команда wrk2 для get запросов:
```shell
wrk2 -d 60 -t 1 -c 1 -R $rate -s get.lua http://localhost:25565
```

Перед профилированием заполнил хранилище 6GB данных.

Запускаем скрипты с rate = 1000:
```text
michael@MacBook-Pro-3 report % ./wrk2_start.sh 1000 put.lua                                                                                           
Running 1m test @ http://localhost:25565
  1 threads and 1 connections
  Thread calibration: mean lat.: 1.095ms, rate sampling interval: 10ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     1.23ms    2.28ms  74.18ms   99.63%
    Req/Sec     1.05k   164.25     8.00k    94.30%
  60000 requests in 1.00m, 3.83MB read
Requests/sec:    999.99
Transfer/sec:     65.43KB
```

```text
michael@MacBook-Pro-3 report % ./wrk2_start.sh 1000 get.lua
Running 1m test @ http://localhost:25565
  1 threads and 1 connections
  Thread calibration: mean lat.: 1.095ms, rate sampling interval: 10ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     1.07ms  412.66us  10.62ms   64.15%
    Req/Sec     1.06k    72.83     1.22k    89.20%
  60000 requests in 1.00m, 501.19MB read
Requests/sec:   1000.00
Transfer/sec:      8.35MB
```
---
Запускаем скрипты с rate = 5000:
```text
michael@MacBook-Pro-3 report % ./wrk2_start.sh 5000 put.lua
Running 1m test @ http://localhost:25565
1 threads and 1 connections
Thread calibration: mean lat.: 1.795ms, rate sampling interval: 10ms
Thread Stats   Avg      Stdev     Max   +/- Stdev
Latency     1.78ms    5.47ms  79.42ms   98.21%
Req/Sec     5.28k     1.02k   19.00k    93.43%
299997 requests in 1.00m, 19.17MB read
Requests/sec:   4999.97
Transfer/sec:    327.15KB
```

```text
michael@MacBook-Pro-3 report % ./wrk2_start.sh 5000 get.lua
Running 1m test @ http://localhost:25565
  1 threads and 1 connections
  Thread calibration: mean lat.: 1.263ms, rate sampling interval: 10ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     1.17ms  633.63us  10.14ms   61.62%
    Req/Sec     5.27k   505.71    10.00k    66.69%
  299998 requests in 1.00m, 2.45GB read
Requests/sec:   4999.88
Transfer/sec:     41.77MB
```
---
Запускаем скрипты с rate = 7000:
```text
michael@MacBook-Pro-3 report % ./wrk2_start.sh 7000 put.lua
Running 1m test @ http://localhost:25565
  1 threads and 1 connections
  Thread calibration: mean lat.: 0.922ms, rate sampling interval: 10ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     3.64ms   19.31ms 268.54ms   97.35%
    Req/Sec     7.41k     1.42k   20.78k    95.37%
  419995 requests in 1.00m, 26.84MB read
Requests/sec:   6999.81
Transfer/sec:    458.00KB
```

```text
michael@MacBook-Pro-3 report % ./wrk2_start.sh 7000 get.lua 
Running 1m test @ http://localhost:25565
  1 threads and 1 connections
  Thread calibration: mean lat.: 1.258ms, rate sampling interval: 10ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     1.08ms  624.55us  13.88ms   65.00%
    Req/Sec     7.38k   748.80    13.00k    67.61%
  419991 requests in 1.00m, 3.43GB read
Requests/sec:   6999.83
Transfer/sec:     58.47MB
```
---
Запускаем скрипты с rate = 15000:
```text
michael@MacBook-Pro-3 report % ./wrk2_start.sh 15000 put.lua
Running 1m test @ http://localhost:25565
  1 threads and 1 connections
  Thread calibration: mean lat.: 60.043ms, rate sampling interval: 607ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency   245.28ms  264.34ms 891.39ms   76.04%
    Req/Sec    15.17k     1.96k   19.14k    67.07%
  899956 requests in 1.00m, 57.50MB read
Requests/sec:  14999.32
Transfer/sec:      0.96MB

```

```text
michael@MacBook-Pro-3 report % ./wrk2_start.sh 15000 get.lua
Running 1m test @ http://localhost:25565
  1 threads and 1 connections
  Thread calibration: mean lat.: 1.808ms, rate sampling interval: 10ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency    70.58ms  143.21ms 587.78ms   86.00%
    Req/Sec    15.86k     2.90k   29.22k    78.59%
  899987 requests in 1.00m, 7.22GB read
Requests/sec:  14999.84
Transfer/sec:    123.30MB
```
---
Сервер начинает заметно тормозить с rate = 15000.