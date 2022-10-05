# Тестирование при помощи wrk2.

### Тестирование проводилось на MacBook Pro на Intel.

### PUT

Имитируем запрос *put* от пользователя. [Скрипт](./scripts/put.lua).

`wrk2 -t 1 -c 1 -d 180s -R 10000 http://localhost:3000 -s put.lua`

Вывод программы:

```
Running 3m test @ http://localhost:3000
  1 threads and 1 connections
  Thread calibration: mean lat.: 5.836ms, rate sampling interval: 10ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency   787.43us    0.90ms  29.44ms   96.28%
    Req/Sec    10.49k   791.56    20.33k    74.36%
  1799994 requests in 3.00m, 115.01MB read
Requests/sec:   9999.97
Transfer/sec:    654.30KB
```

### GET

Имитируем запрос *get* от пользователя. [Скрипт](./scripts/get.lua).

`wrk2 -t 1 -c 1 -d 180s -R 10000 http://localhost:3000 -s get.lua`

Вывод программы:

```
Running 3m test @ http://localhost:3000
  1 threads and 1 connections

  Thread calibration: mean lat.: 1.843ms, rate sampling interval: 10ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     1.37ms    6.88ms 131.33ms   98.76%
    Req/Sec    10.57k     0.96k   29.00k    83.74%
  1799988 requests in 3.00m, 127.03MB read
Requests/sec:   9999.92
Transfer/sec:    722.65KB

```

## Вывод

Видно, что чтение работает медленнее, чем запись. Это связано с тем что при сохранении мы не должны искать по всей памяти нужную нам запись, а просто кладем ее, а после флашим пакетом.
Если смотреть на исполнение запросов, видно что большая часть времени уходит не на работу с сетью(работу с сетью мы не с оптимизируем), а на обработку запросов, конвертация строк, аллокации массивов. Мы можем вынести эту часть в воркеру который может это делать параллельно. У нас также есть проблемы с бд, так при последовательных запросах id она работает на плохо, но при случайных запросах, мы часто обращаемся к памяти и тратим на этом много времени, это не исправить изменениями в сервере.
alloc.html слишком тяжелый, поэтому не клал в репозиторий.
