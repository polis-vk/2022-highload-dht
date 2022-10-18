# Stage 3

Все профилирование будет проводится с тремя нодами, а строиться профили для ноды, на которую посылаются запросы. 
Конфигурация запросов будет аналогична конфигурации прошлого этапа для честного сравнения изменений.

## PUT

Проведем нагрузочное тестирование:

```
i.panov@macbook-i stage_1 % wrk2 -d 1m -t 8 -c 32 -R 10000 -s PutStableLoad.lua "http://localhost:19234"
Running 1m test @ http://localhost:19234
  8 threads and 32 connections
  Thread calibration: mean lat.: 114.830ms, rate sampling interval: 1162ms
  Thread calibration: mean lat.: 113.133ms, rate sampling interval: 1148ms
  Thread calibration: mean lat.: 112.489ms, rate sampling interval: 1141ms
  Thread calibration: mean lat.: 113.425ms, rate sampling interval: 1149ms
  Thread calibration: mean lat.: 113.817ms, rate sampling interval: 1153ms
  Thread calibration: mean lat.: 112.116ms, rate sampling interval: 1140ms
  Thread calibration: mean lat.: 112.023ms, rate sampling interval: 1140ms
  Thread calibration: mean lat.: 112.817ms, rate sampling interval: 1144ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     1.24ms    1.57ms  37.70ms   98.48%
    Req/Sec     1.25k     2.25     1.27k    98.26%
  599800 requests in 1.00m, 36.01MB read
Requests/sec:   9997.12
Transfer/sec:    614.57KB
```

```
i.panov@macbook-i stage_1 % du -sh /var/folders/0y/h0j9xdp567j7f7y_f9jf08qm0000gp/T/server3531925502667129736 
 44M    /var/folders/0y/h0j9xdp567j7f7y_f9jf08qm0000gp/T/server3531925502667129736
i.panov@macbook-i stage_1 % du -sh /var/folders/0y/h0j9xdp567j7f7y_f9jf08qm0000gp/T/server12492733374320666327
 16M    /var/folders/0y/h0j9xdp567j7f7y_f9jf08qm0000gp/T/server12492733374320666327
i.panov@macbook-i stage_1 % du -sh /var/folders/0y/h0j9xdp567j7f7y_f9jf08qm0000gp/T/server6025840628954999856 
 32M    /var/folders/0y/h0j9xdp567j7f7y_f9jf08qm0000gp/T/server6025840628954999856
```

## GET

Проведем нагрузочное тестирование:

```
i.panov@macbook-i stage_1 % wrk2 -d 1m -t 8 -c 32 -R 10000 -s GetStableLoad.lua "http://localhost:19234"
Running 1m test @ http://localhost:19234
  8 threads and 32 connections
  Thread calibration: mean lat.: 18.181ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 17.533ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 17.663ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 17.570ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 17.653ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 17.453ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 17.461ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 17.322ms, rate sampling interval: 10ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     1.04ms  454.33us   3.34ms   65.20%
    Req/Sec     1.32k    99.00     1.78k    58.73%
  599796 requests in 1.00m, 722.72MB read
Requests/sec:   9997.59
Transfer/sec:     12.05MB
```


