# Шардирование

В качестве алгоритма хэширования для детерминированного определения ноды для ключа выбрал rendezvous hashing.

# wrk тестирование 1 шарда

Начал тестировать на другом компьютере, поэтому результаты не сравнимы с прошлыми этапами.
Вначале решил пострелять в кластер из 1 инстанса, чтобы потом сравнить результаты профилирования с кластером из 3 шардов.
wrk запускал в 6 потоков и 64 соединения. При выставлении нагрузки на 20 секунд PUT запросами удалось выжать 200000 rps
и при выставлении для wrk параметра rps в 250000:
```
./put.sh 20 250000

Running 20s test @ http://localhost:12345
  6 threads and 64 connections
  Thread calibration: mean lat.: 792.780ms, rate sampling interval: 3266ms
  Thread calibration: mean lat.: 779.567ms, rate sampling interval: 3213ms
  Thread calibration: mean lat.: 787.263ms, rate sampling interval: 3244ms
  Thread calibration: mean lat.: 781.223ms, rate sampling interval: 3229ms
  Thread calibration: mean lat.: 785.332ms, rate sampling interval: 3235ms
  Thread calibration: mean lat.: 785.451ms, rate sampling interval: 3244ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     2.97s   659.51ms   4.09s    53.17%
    Req/Sec    32.38k   411.44    32.89k    61.11%
  3995568 requests in 20.00s, 255.30MB read
Requests/sec: 199795.86
Transfer/sec:     12.77MB
```

И при выставлении в 200000:
```
./put.sh 20 200000

Running 20s test @ http://localhost:12345
  6 threads and 64 connections
  Thread calibration: mean lat.: 2.673ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.668ms, rate sampling interval: 11ms
  Thread calibration: mean lat.: 2.650ms, rate sampling interval: 11ms
  Thread calibration: mean lat.: 2.579ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.459ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.676ms, rate sampling interval: 10ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     1.98ms    1.87ms  20.78ms   86.89%
    Req/Sec    35.10k     5.60k   55.11k    73.75%
  3995092 requests in 20.00s, 255.27MB read
Requests/sec: 199762.56
Transfer/sec:     12.76MB
```

Видимо, это близко к точке разлада (всё-таки сервер ответил почти на все запросы),
но уже чуть дальше неё. А за 1 минуту удалось выжать 190000 rps:

```
./put.sh 60 190000

Running 1m test @ http://localhost:12345
  6 threads and 64 connections
  Thread calibration: mean lat.: 1.676ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.702ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.662ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.711ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.693ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.687ms, rate sampling interval: 10ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     2.50ms    7.95ms 113.98ms   98.62%
    Req/Sec    33.41k     5.58k   64.22k    75.79%
  11395398 requests in 1.00m, 728.12MB read
Requests/sec: 189928.69
Transfer/sec:     12.14MB
```

С GET запросами получилось нагрузить сервер почти на 150000 rps:
```
./get.sh 20 150000

Running 20s test @ http://localhost:12345
  6 threads and 64 connections
  Thread calibration: mean lat.: 4.215ms, rate sampling interval: 17ms
  Thread calibration: mean lat.: 4.298ms, rate sampling interval: 17ms
  Thread calibration: mean lat.: 4.384ms, rate sampling interval: 18ms
  Thread calibration: mean lat.: 4.324ms, rate sampling interval: 17ms
  Thread calibration: mean lat.: 4.317ms, rate sampling interval: 17ms
  Thread calibration: mean lat.: 4.225ms, rate sampling interval: 17ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency   112.16ms   86.78ms 339.20ms   56.87%
    Req/Sec    25.04k     4.47k   36.44k    63.60%
  2953677 requests in 20.00s, 209.70MB read
  Non-2xx or 3xx responses: 222342
Requests/sec: 147695.30
Transfer/sec:     10.49MB
```

Latency 112 ms, то есть около точки разлада.

# wrk тестирование 3 шардов

PUT запросами вышло всего 39000 rps:
```
./put.sh 60 39000

Running 1m test @ http://localhost:12345
  6 threads and 64 connections
  Thread calibration: mean lat.: 5.284ms, rate sampling interval: 27ms
  Thread calibration: mean lat.: 5.096ms, rate sampling interval: 25ms
  Thread calibration: mean lat.: 5.192ms, rate sampling interval: 27ms
  Thread calibration: mean lat.: 5.631ms, rate sampling interval: 30ms
  Thread calibration: mean lat.: 5.133ms, rate sampling interval: 27ms
  Thread calibration: mean lat.: 5.390ms, rate sampling interval: 27ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency    16.36ms   50.48ms 400.38ms   92.32%
    Req/Sec     6.63k   462.85     9.72k    81.20%
  2339046 requests in 1.00m, 149.46MB read
Requests/sec:  38985.03
Transfer/sec:      2.49MB
```

GET запросами 30000:
```
./get.sh 60 30000

Running 1m test @ http://localhost:12345
  6 threads and 64 connections
  Thread calibration: mean lat.: 16.818ms, rate sampling interval: 167ms
  Thread calibration: mean lat.: 18.125ms, rate sampling interval: 177ms
  Thread calibration: mean lat.: 16.183ms, rate sampling interval: 155ms
  Thread calibration: mean lat.: 18.595ms, rate sampling interval: 185ms
  Thread calibration: mean lat.: 15.827ms, rate sampling interval: 153ms
  Thread calibration: mean lat.: 17.343ms, rate sampling interval: 166ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     1.24ms  774.86us  21.55ms   80.61%
    Req/Sec     5.02k    26.25     5.43k    87.26%
  1799291 requests in 1.00m, 125.20MB read
  Non-2xx or 3xx responses: 587436
Requests/sec:  29988.44
Transfer/sec:      2.09MB
```

Максимальная нагрузка уменьшилась примерно в 5 раз. Это из-за того, что теперь большую часть запросов инстанс
проксирует на целевую ноду, то есть к запросу добавляется пересылка двух сообщений - запроса и ответа - а также
нахождение целевой ноды через rendezvous hashing - в каждом запросе мы проходимся циклом по всем урлам в кластере
и вычисляем хэш.

# профилирование 1 шарда

Эти профили особо не отличаются от профилей из 2 домашки. Только добавилось немного сэмплов с getNodeByUrl, но всё
осталось таким же. Аллокаций или блокировок в случае 1 шарда не добавилось.

# профилирование 3 шардов

Тут уже профиль cpu довольно разношёрстный. Бросается в глаза, что красные областей с write, read запросов и ответов
клиенту мало, так же появились writev и read запросов взаимодействия с другими шардами. Из-за этого понятно, почему
так сильно уменьшилась максимальная нагрузка на инстанс. Довольно немалую часть профиля занимает HttpClientImpl::send,
а в нём есть блокировки, accept, select и прочее. Как мне кажется, важную роль играет то, что я делаю send, а мог бы
делать sendAsync, и в CompletableFuture после получения ответа от другого шарда уже посылать наш ответ клиенту.
(Но чтобы поисследовать это, нужно порефакторить текущую мою реализацию, и, кажется, успеваю это сделать
уже только после дедлайна)
На профиле аллокаций слишком много места заняли аллокации джавового HTTP клиента. Возможно, в one nio клиенте это лучше
оптимизировано, но в тестах использовался джавовый + как я понял, one nio клиент умеет подключаться только к одному эндпоинту.
На профиле блокировок почти всё место занял synchronized SelectorManager::register в джавовом клиенте.
(Очень интересно поисследовать профили с one nio клиентом, но это тоже уже после дедлайна)

В итоге "производительность" сервиса резко упала из-за взаимодействия с другими инстансами. Но теперь из-за шардирования
данные начали распределяться по разным нодам и теоретически мы можем большое количество данных хранить, используя
много машин.
