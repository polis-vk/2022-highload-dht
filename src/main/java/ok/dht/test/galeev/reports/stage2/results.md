# Stage 2

## Количество потоков воркеров
Для начало нужно определиться с количеством воркеров. Определять будем с помощью get
### 10, как в selector thread
```
└─$ wrk -t 1 -c 1 -d 60s -s /media/coradead/Windows1/Users/CORADEAD/IdeaProjects/2022-highload-dht/src/main/java/ok/dht/test/galeev/reports/scritps/get.lua -L http://localhost:19234 -R 500 
Running 1m test @ http://localhost:19234
  1 threads and 1 connections
  Thread calibration: mean lat.: 1.490ms, rate sampling interval: 10ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     1.21ms  429.15us  13.10ms   73.04%
    Req/Sec   527.76     68.15     1.11k    64.77%

```

### 32
```
└─$ wrk -t 1 -c 1 -d 60s -s /media/coradead/Windows1/Users/CORADEAD/IdeaProjects/2022-highload-dht/src/main/java/ok/dht/test/galeev/reports/scritps/get.lua -L http://localhost:19234 -R 500
Running 1m test @ http://localhost:19234
  1 threads and 1 connections
  Thread calibration: mean lat.: 1.826ms, rate sampling interval: 10ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     1.37ms  392.57us  10.66ms   65.66%
    Req/Sec   527.61     45.25     1.00k    87.24%
```
Видим хоть и незначительное, но ухудшение в среднем времени, но улучшение максимального времени ожидания.
Идем дальше

### 64
```
└─$ wrk -t 1 -c 1 -d 60s -s /media/coradead/Windows1/Users/CORADEAD/IdeaProjects/2022-highload-dht/src/main/java/ok/dht/test/galeev/reports/scritps/get.lua -L http://localhost:19234 -R 500
Running 1m test @ http://localhost:19234
  1 threads and 1 connections
  Thread calibration: mean lat.: 1.910ms, rate sampling interval: 10ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     1.29ms  367.81us   8.74ms   68.35%
    Req/Sec   527.89     59.58   777.00     70.52%
```

Среднее время не изменилось, но вот максимальное опять уменьшилось.
### 100
```
└─$ wrk -t 1 -c 1 -d 60s -s /media/coradead/Windows1/Users/CORADEAD/IdeaProjects/2022-highload-dht/src/main/java/ok/dht/test/galeev/reports/scritps/get.lua -L http://localhost:19234 -R 500
Running 1m test @ http://localhost:19234
  1 threads and 1 connections
  Thread calibration: mean lat.: 1.811ms, rate sampling interval: 10ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     1.40ms  412.36us  11.30ms   66.63%
    Req/Sec   527.05     63.13   777.00     58.63%
```
Уже видим регрессию поэтому остановимся на 64, как более менее оптимальное количество воркеров,
обращающихся к DAO.


## Предположения
В первую очередь обратим внимание, что мы освободили selector threads,
а значит теоретически, если раньше мы упирались в эти 10 selector thread,
то сейчас ситуация должна улучшиться. Давайте сравним. Например, недавний тест с 64 воркерами

Было
```
└─$ wrk -t 1 -c 1 -d 60s -s /media/coradead/Windows1/Users/CORADEAD/IdeaProjects/2022-highload-dht/src/main/java/ok/dht/test/galeev/reports/scritps/get.lua -L http://localhost:19234 -R 500 
Running 1m test @ http://localhost:19234
  1 threads and 1 connections
  Thread calibration: mean lat.: 2.217ms, rate sampling interval: 10ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     1.29ms  361.43us   6.76ms   67.06%
    Req/Sec   527.50     65.35     0.89k    68.90%
```

Стало
```
└─$ wrk -t 1 -c 1 -d 60s -s /media/coradead/Windows1/Users/CORADEAD/IdeaProjects/2022-highload-dht/src/main/java/ok/dht/test/galeev/reports/scritps/get.lua -L http://localhost:19234 -R 500
Running 1m test @ http://localhost:19234
  1 threads and 1 connections
  Thread calibration: mean lat.: 1.910ms, rate sampling interval: 10ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     1.29ms  367.81us   8.74ms   68.35%
    Req/Sec   527.89     59.58   777.00     70.52%
```
Стало даже хуже. Предположим, что преимущество новой версии появится при повышенной
нагрузке. Проверим увеличив количество потоков и каналов.

Было
```
└─$ wrk -t 6 -c 64 -d 60s -s /media/coradead/Windows1/Users/CORADEAD/IdeaProjects/2022-highload-dht/src/main/java/ok/dht/test/galeev/reports/scritps/get.lua -L http://localhost:19234 -R 40000
Running 1m test @ http://localhost:19234
  6 threads and 64 connections
  Thread calibration: mean lat.: 13.070ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 16.370ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 14.119ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 15.216ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 14.272ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 15.935ms, rate sampling interval: 10ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     1.22ms  609.66us  14.98ms   67.94%
    Req/Sec     7.03k   515.23    13.22k    67.14%
```

Стало
```
└─$ wrk -t 6 -c 64 -d 60s -s /media/coradead/Windows1/Users/CORADEAD/IdeaProjects/2022-highload-dht/src/main/java/ok/dht/test/galeev/reports/scritps/get.lua -L http://localhost:19234 -R 40000
Running 1m test @ http://localhost:19234
  6 threads and 64 connections
  Thread calibration: mean lat.: 7.850ms, rate sampling interval: 46ms
  Thread calibration: mean lat.: 8.971ms, rate sampling interval: 50ms
  Thread calibration: mean lat.: 9.183ms, rate sampling interval: 53ms
  Thread calibration: mean lat.: 8.682ms, rate sampling interval: 50ms
  Thread calibration: mean lat.: 7.753ms, rate sampling interval: 44ms
  Thread calibration: mean lat.: 8.959ms, rate sampling interval: 50ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     2.63ms    3.24ms  57.86ms   91.21%
    Req/Sec     6.74k   313.08     8.64k    76.40%
```
Хуже во всем. Теперь же проведем повторные испытания, но в 1 поток.

Было
```
└─$ wrk -t 1 -c 64 -d 60s -s /media/coradead/Windows1/Users/CORADEAD/IdeaProjects/2022-highload-dht/src/main/java/ok/dht/test/galeev/reports/scritps/get.lua -L http://localhost:19234 -R 40000
Running 1m test @ http://localhost:19234
  1 threads and 64 connections
  Thread calibration: mean lat.: 776.579ms, rate sampling interval: 2312ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency    88.66ms  263.64ms   1.58s    90.25%
    Req/Sec    40.95k     2.34k   47.97k    85.71%
```
Стало
```
└─$ wrk -t 1 -c 64 -d 60s -s /media/coradead/Windows1/Users/CORADEAD/IdeaProjects/2022-highload-dht/src/main/java/ok/dht/test/galeev/reports/scritps/get.lua -L http://localhost:19234 -R 40000 
Running 1m test @ http://localhost:19234
  1 threads and 64 connections
  Thread calibration: mean lat.: 1075.483ms, rate sampling interval: 3289ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency   380.18ms  566.67ms   2.07s    79.78%
    Req/Sec    41.27k     1.35k   43.61k    60.00%
```
Все так же плохо.
Попробуем 1 поток, но количество соединений увеличим до 256

Было
```
└─$ wrk -t 1 -c 256 -d 60s -s /media/coradead/Windows1/Users/CORADEAD/IdeaProjects/2022-highload-dht/src/main/java/ok/dht/test/galeev/reports/scritps/get.lua -L http://localhost:19234 -R 40000
Running 1m test @ http://localhost:19234
  1 threads and 256 connections
  Thread calibration: mean lat.: 1152.713ms, rate sampling interval: 3266ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency   287.07ms  489.16ms   2.18s    81.44%
    Req/Sec    41.46k     2.01k   45.15k    71.43%

```

Стало
```
└─$ wrk -t 1 -c 256 -d 60s -s /media/coradead/Windows1/Users/CORADEAD/IdeaProjects/2022-highload-dht/src/main/java/ok/dht/test/galeev/reports/scritps/get.lua -L http://localhost:19234 -R 40000                                                                         1 ⨯
Running 1m test @ http://localhost:19234
  1 threads and 256 connections
  Thread calibration: mean lat.: 95.370ms, rate sampling interval: 927ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     3.83ms    1.55ms  16.45ms   66.69%
    Req/Sec    20.17k    35.78    20.24k    65.38%
```

Неужто чудо? А нет. Если пролистать ниже, то видно, что сервер просто захлебнулся
```
  1350971 requests in 1.00m, 95.34MB read
  Socket errors: connect 0, read 0, write 0, timeout 3122
Requests/sec:  22515.92
Transfer/sec:      1.59MB
```

(уменьшение количества работников только ухудшает ситуацию)

Посмотрим на флейм графы у `get`.

![get cpu](./PNGs/cpu_get.png)
![get alloc](./PNGs/alloc_get.png)

Теперь selector thread занимает всего 8%, хотя раньше занимал 80%.
А значит основную задачу пул воркеров выполнил, да и flamegraph стал
равномернее. По памяти ничего особо не изменилось(кроме того факта,
что теперь DAO выделяет память в воркерах). Мы все так же упираемся
в системные вызовы, но теперь у нас еще есть куча локов, на которых
мы тоже теряем время.

Так же если мы посмотрим на время выполнения потоков и локи. Мы заметим, что
наша очередь для воркеров много спит в ожидании, чтобы взять runnable. 
А значит если использовать не блокирующую очередь, то это если не ускорит,
то хотя бы приравняет к задержкам stage1

![get lock](./PNGs/lock_get.png)
![get wall](./PNGs/wall_get.png)
