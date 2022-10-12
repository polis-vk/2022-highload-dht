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

# wrk тестирование 3 шардов

PUT запросами вышло всего 22000 rps:
```
Running 2m test @ http://localhost:12345
  6 threads and 64 connections
  Thread calibration: mean lat.: 2.013ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.018ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.012ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.025ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.035ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.020ms, rate sampling interval: 10ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency    61.61ms   66.56ms 254.34ms   78.42%
    Req/Sec     3.86k   366.89     5.33k    72.83%
  2639315 requests in 2.00m, 168.64MB read
Requests/sec:  21994.53
Transfer/sec:      1.41MB
```

GET запросами 24000:
```
Running 2m test @ http://localhost:12345
  6 threads and 64 connections
  Thread calibration: mean lat.: 1.446ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.464ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.482ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.490ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.444ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.455ms, rate sampling interval: 10ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency   117.96ms  101.02ms 396.54ms   58.65%
    Req/Sec     4.22k   383.88     6.67k    70.73%
  2879411 requests in 2.00m, 200.43MB read
  Non-2xx or 3xx responses: 928889
Requests/sec:  23995.22
Transfer/sec:      1.67MB
```

Максимальная нагрузка уменьшилась примерно в 2.5-3 раза. Это из-за того, что теперь большую часть запросов инстанс
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
