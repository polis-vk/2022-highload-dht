# Отчет

## GET t=5 c=64 R=5000 d=1m
wrk2 output:
```
Running 1m test @ http://localhost:8000
  5 threads and 64 connections
  Thread calibration: mean lat.: 0.904ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 0.876ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 0.872ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 0.878ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 0.877ms, rate sampling interval: 10ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     0.86ms  426.76us   3.41ms   66.96%
    Req/Sec     1.07k    89.56     1.33k    78.32%
  299891 requests in 1.00m, 19.73MB read
  Non-2xx or 3xx responses: 299891
Requests/sec:   4998.16
Transfer/sec:    336.79KB
```

[cpu heatmap & flame graph](profiles/2022-10-03-10-54-31_get_t5_c64_R5000_d1m/cpu.html)
![image](profiles/2022-10-03-10-54-31_get_t5_c64_R5000_d1m/cpu.png)

[alloc heatmap & flame graph](profiles/2022-10-03-10-54-31_get_t5_c64_R5000_d1m/alloc.html)
![image](profiles/2022-10-03-10-54-31_get_t5_c64_R5000_d1m/alloc.png)

## PUT t=5 c=64 R=5000 d=1m
TODO

## Выводы

* Я хочу пиццу
