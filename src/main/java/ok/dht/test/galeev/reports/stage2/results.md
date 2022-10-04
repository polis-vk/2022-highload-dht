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