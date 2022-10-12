# Шардирование

В качестве алгоритма хэширования для детерминированного определения ноды для ключа выбрал rendezvous hashing.

# wrk тестирование 1 шарда

Вначале решил пострелять в кластер из 1 инстанса, чтобы потом сравнить результаты профилирования с кластером из 3 шардов.
wrk запускал в 6 потоков и 64 соединения. PUT запросами удалось выжать 60000 rps:
```
Running 2m test @ http://localhost:12345
  6 threads and 64 connections
  Thread calibration: mean lat.: 7.977ms, rate sampling interval: 41ms
  Thread calibration: mean lat.: 7.616ms, rate sampling interval: 37ms
  Thread calibration: mean lat.: 7.520ms, rate sampling interval: 34ms
  Thread calibration: mean lat.: 7.912ms, rate sampling interval: 41ms
  Thread calibration: mean lat.: 7.834ms, rate sampling interval: 40ms
  Thread calibration: mean lat.: 7.848ms, rate sampling interval: 40ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency   294.28ms  431.15ms   1.45s    80.98%
    Req/Sec    10.13k   713.20    14.65k    75.27%
  7198532 requests in 2.00m, 459.96MB read
Requests/sec:  59988.38
Transfer/sec:      3.83MB
```

Latency 294.28ms говорит о том, что это близко к точке разлада (всё-таки сервер ответил почти на все запросы),
но уже чуть дальше неё.
С GET запросами получилось нагрузить сервер на 57000 rps:
```
Running 2m test @ http://localhost:12345
  6 threads and 64 connections
  Thread calibration: mean lat.: 3.585ms, rate sampling interval: 21ms
  Thread calibration: mean lat.: 3.547ms, rate sampling interval: 20ms
  Thread calibration: mean lat.: 3.578ms, rate sampling interval: 21ms
  Thread calibration: mean lat.: 3.579ms, rate sampling interval: 20ms
  Thread calibration: mean lat.: 3.649ms, rate sampling interval: 21ms
  Thread calibration: mean lat.: 3.572ms, rate sampling interval: 21ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency   229.42ms  297.90ms 914.94ms   76.76%
    Req/Sec     9.75k     1.01k   13.55k    80.81%
  6838585 requests in 2.00m, 482.61MB read
  Non-2xx or 3xx responses: 1031354
Requests/sec:  56988.74
Transfer/sec:      4.02MB
```

Примерно такой же latency, то есть тоже около точки разлада.
