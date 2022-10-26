# wrk тестирование 1 реплики

Вначале решил пострелять в кластер из 1 инстанса (так же, как в прошлой домашке), чтобы потом сравнить результаты
с шардированием. Но и к тому же я начал запускать на другом ноутбуке, поэтому нужно было определить начальную нагрузку.
PUT запросами на 2 минуты сервис выдерживал целых 160000 rps:
```
Running 1m test @ http://localhost:12345
6 threads and 64 connections
Thread calibration: mean lat.: 1.427ms, rate sampling interval: 10ms
Thread calibration: mean lat.: 1.395ms, rate sampling interval: 10ms
Thread calibration: mean lat.: 1.396ms, rate sampling interval: 10ms
Thread calibration: mean lat.: 1.407ms, rate sampling interval: 10ms
Thread calibration: mean lat.: 1.419ms, rate sampling interval: 10ms
Thread calibration: mean lat.: 1.392ms, rate sampling interval: 10ms
Thread Stats   Avg      Stdev     Max   +/- Stdev
Latency     5.48ms   28.72ms 414.72ms   97.52%
Req/Sec    28.13k     4.23k   58.22k    81.77%
9595975 requests in 1.00m, 613.15MB read
Requests/sec: 159936.98
Transfer/sec:     10.22MB
```
