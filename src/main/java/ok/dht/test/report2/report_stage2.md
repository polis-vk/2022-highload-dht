# Нарузочное тестирование

## PUT
Нагружаем базу данных put запросами в 8 потоков, 64 соединения в течение 3 минут с rate = 10000 запросов/сек.

```bash
veronika@MacBook-Pro-Veronika anikina % wrk2 -t 8 -c 64 -d 3m -R 10000 -s lua/put.lua http://localhost:8080
Running 3m test @ http://localhost:8080
  8 threads and 64 connections
  Thread calibration: mean lat.: 0.879ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 0.866ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 0.896ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 0.890ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 0.886ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 0.878ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 0.875ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.063ms, rate sampling interval: 10ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     0.91ms  463.90us  16.64ms   69.96%
    Req/Sec     1.31k   178.58     3.10k    74.01%
  1798628 requests in 3.00m, 114.93MB read
Requests/sec:   9992.22
Transfer/sec:    653.79KB
```
Средняя latency составляет 0.879ms, что несильно отличается от предыдущей синхронной версии, 
тогда latency составляло 0.724ms. Это скорее всего связано с тем, что текущая версия работает в 8 потоков, 
а wrk как раз тестирует в 8 потоков. В то же время число активных соединений стало больше, 
поэтому средняя задержка на ответ немного возрасла.

## GET

Нагружаем базу данных get запросами в 8 потоков, 64 соединения в течение 3 минут с rate = 10000 запросов/сек.

```bash
veronika@MacBook-Pro-Veronika anikina % wrk2 -t 8 -c 64 -d 3m -R 10000 -s lua/get.lua http://localhost:8080
Running 3m test @ http://localhost:8080
8 threads and 64 connections
Thread calibration: mean lat.: 2.549ms, rate sampling interval: 10ms
Thread calibration: mean lat.: 2.523ms, rate sampling interval: 10ms
Thread calibration: mean lat.: 2.482ms, rate sampling interval: 10ms
Thread calibration: mean lat.: 2.502ms, rate sampling interval: 10ms
Thread calibration: mean lat.: 2.430ms, rate sampling interval: 10ms
Thread calibration: mean lat.: 2.503ms, rate sampling interval: 10ms
Thread calibration: mean lat.: 2.521ms, rate sampling interval: 10ms
Thread calibration: mean lat.: 2.450ms, rate sampling interval: 10ms
Thread Stats   Avg      Stdev     Max   +/- Stdev
Latency     1.63ms   22.12ms   1.00s    99.86%
Req/Sec     1.31k   127.82     3.56k    75.05%
1799845 requests in 3.00m, 727.78MB read
Requests/sec:   9999.15
Transfer/sec:      4.04MB
```

Средняя latency составляет 1.63ms, что намного быстрее чем в синхронной версии - 2.49m. 
Связано с тем, что теперь сервер работает асинхронно: для того, чтобы начать выполнение следующего запроса 
мы не ждем результата предыдущего. В связи с этим среднее время ответа на один запрос уменьшается.

# Профилирование

## PUT

