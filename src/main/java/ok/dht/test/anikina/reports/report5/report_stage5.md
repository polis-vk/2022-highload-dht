Так же как в прошлой версии, запускались 3 ноды, и использовался кворум 2 / 3.

## Нагрузочное тестирование

### PUT

Ассинхронное взаимодействие между нодами заметно улучшило пропускную способность:

```
veronika@MBP-Veronika anikina % wrk2 -t 8 -c 64 -d 3m -R 6000 -s lua/put.lua http://localhost:8080
Running 3m test @ http://localhost:8080
  8 threads and 64 connections
  Thread calibration: mean lat.: 1.410ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.412ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.412ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.379ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.591ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.578ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.549ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.589ms, rate sampling interval: 10ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     1.54ms    1.08ms  30.03ms   89.43%
    Req/Sec   790.17     91.65     1.33k    64.20%
  1079918 requests in 3.00m, 69.00MB read
Requests/sec:   5999.52
Transfer/sec:    392.55KB
```

Теперь сервис выдерживает R = 6000, в то время как при синхронном взаимодействии максимальный был R = 1000.

### GET

То же самое и для get запросов, теперь сервис выдерживает R = 6000:

```
veronika@MBP-Veronika anikina % wrk2 -t 8 -c 64 -d 3m -R 6000 -s lua/get.lua http://localhost:8080
Running 3m test @ http://localhost:8080
  8 threads and 64 connections
  Thread calibration: mean lat.: 1.504ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.458ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.477ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.481ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.474ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.554ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.591ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.580ms, rate sampling interval: 10ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     1.81ms    5.57ms 132.61ms   99.26%
    Req/Sec   790.07     88.91     1.44k    50.22%
  1079911 requests in 3.00m, 436.67MB read
Requests/sec:   5999.50
Transfer/sec:      2.43MB
```

## Профилирование

### PUT

Достаточно много cpu все еще тратится на репликацию запросов - 26%, 
однако по сравнению с синхронной версией меньше времени уходит на сетевое взаимодействие 
между нодами для репликации запросов - 2.3%, в синхронной версии 10.8%.
Так же меньше cpu времени уходит на httpClient, 10% vs 17%.

Меньше аллокаций стало уходить на репликацию - 7.3%, в синхронной версии 15%.
Связано с тем, что sendAsync потребляет меньшее количество аллокаций, чем синхронный send, 4.3% vs 11.1%.
Но теперь 28% аллокаций занимает асинхронная обработка, а именно работа с CompletableFuture.

Добавились новые локи - 36% - которые берутся при асинхронной обработке пулом потоков в httpClient.

### GET

Результаты профилирования get запросов такие же, что и put.

## Выводы

Асинхронное взаимодейсвие нод внутри кластера увеличило пропускную способность в 6 раз
(было R = 1000, стало R = 6000).

Вместе с тем, появились расходы на асинхронную обработку в httpClient, это видно по результатам профилирования:
дополнительные 28% аллокаций и 36% блокировок.

В качестве оптимизаций можно все еще использовать более оптимальный сетевой протокол.
