# Тестирование при помощи wrk2.

### Тестирование проводилось на MacBook Pro на Intel.

### PUT

Имитируем запрос *put* от пользователя. [Скрипт](./scripts/put.lua).

`wrk2 -t 1 -c 1 -d 30s -R 10000 http://localhost:3000 -s put.lua`

Вывод программы:

```
Running 30s test @ http://localhost:3000 
1 threads and 1 connections
Thread calibration: mean lat.: 47.421ms, rate sampling interval: 307ms
Thread Stats   Avg      Stdev     Max   +/- Stdev
Latency     1.23ms    3.23ms  48.80ms   97.81%
Req/Sec    10.02k   284.69    11.58k    93.85%
299979 requests in 30.00s, 19.17MB read
Requests/sec:   9999.36
Transfer/sec:    654.26KB
```

### GET

Имитируем запрос *get* от пользователя. [Скрипт](./scripts/get.lua).

`wrk2 -t 1 -c 1 -d 30s -R 10000 http://localhost:3000 -s get.lua`

Вывод программы:

```
Running 30s test @ http://localhost:3000
  1 threads and 1 connections
  Thread calibration: mean lat.: 1342.083ms, rate sampling interval: 4358ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     4.39s     1.19s    6.29s    56.81%
    Req/Sec     8.05k   393.65     8.52k    50.00%
  237065 requests in 30.00s, 22.21MB read
  Non-2xx or 3xx responses: 207365
Requests/sec:   7902.27
Transfer/sec:    758.13KB
```

## Вывод

Видно, что чтение работает медленнее, чем запись. Это связано с тем что при сохранении мы не должны искать по всей памяти нужную нам запись, а просто кладем ее, а после флашим пакетом.

