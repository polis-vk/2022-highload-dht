Запускались 3 процесса, в качестве параметров репликации использовались дефолтные значения, 2 / 3.

## Нагрузочное тестирование

### PUT

В начале возьмем те же параметры, что и в версии без репликации, R = 15000: 

```
veronika@MBP-Veronika anikina % wrk2 -t 8 -c 64 -d 3m -R 15000 -s lua/put.lua http://localhost:8080
Running 3m test @ http://localhost:8080
  8 threads and 64 connections
  Thread calibration: mean lat.: 5095.602ms, rate sampling interval: 15327ms
  Thread calibration: mean lat.: 5092.974ms, rate sampling interval: 15319ms
  Thread calibration: mean lat.: 5096.108ms, rate sampling interval: 15327ms
  Thread calibration: mean lat.: 5092.943ms, rate sampling interval: 15327ms
  Thread calibration: mean lat.: 5095.257ms, rate sampling interval: 15327ms
  Thread calibration: mean lat.: 5093.470ms, rate sampling interval: 15319ms
  Thread calibration: mean lat.: 5093.918ms, rate sampling interval: 15319ms
  Thread calibration: mean lat.: 5094.248ms, rate sampling interval: 15327ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     0.98m    30.10s    2.18m    60.56%
    Req/Sec   519.65    150.48   626.00     90.91%
  728883 requests in 3.00m, 46.57MB read
Requests/sec:   4047.73
Transfer/sec:    264.84KB
```

Видим, что latency возрасло значительно, теперь оно составляет почти минуту, в то время как в 
прошлой версии оно было около 1ms.

```
veronika@MBP-Veronika anikina % wrk2 -t 8 -c 64 -d 1m -R 1000 -s lua/put.lua http://localhost:8080
Running 1m test @ http://localhost:8080
  8 threads and 64 connections
  Thread calibration: mean lat.: 21.655ms, rate sampling interval: 179ms
  Thread calibration: mean lat.: 21.437ms, rate sampling interval: 167ms
  Thread calibration: mean lat.: 21.879ms, rate sampling interval: 183ms
  Thread calibration: mean lat.: 22.149ms, rate sampling interval: 185ms
  Thread calibration: mean lat.: 21.911ms, rate sampling interval: 184ms
  Thread calibration: mean lat.: 22.005ms, rate sampling interval: 183ms
  Thread calibration: mean lat.: 21.925ms, rate sampling interval: 171ms
  Thread calibration: mean lat.: 22.090ms, rate sampling interval: 188ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     1.65ms    1.41ms  37.09ms   97.91%
    Req/Sec   124.86      6.31   151.00     77.29%
  60028 requests in 1.00m, 3.84MB read
Requests/sec:   1000.37
Transfer/sec:     65.45KB
```

При R = 1000 latency стало 1.6ms.

### GET

Аналогичная ситуация, при R = 10000 latency составило 17s:

```
veronika@MBP-Veronika anikina % wrk2 -t 8 -c 64 -d 1m -R 10000 -s lua/get.lua http://localhost:8080
Running 1m test @ http://localhost:8080
  8 threads and 64 connections
  Thread calibration: mean lat.: 2418.015ms, rate sampling interval: 8544ms
  Thread calibration: mean lat.: 2419.569ms, rate sampling interval: 8544ms
  Thread calibration: mean lat.: 2419.665ms, rate sampling interval: 8544ms
  Thread calibration: mean lat.: 2418.910ms, rate sampling interval: 8544ms
  Thread calibration: mean lat.: 2419.246ms, rate sampling interval: 8544ms
  Thread calibration: mean lat.: 2420.094ms, rate sampling interval: 8544ms
  Thread calibration: mean lat.: 2418.450ms, rate sampling interval: 8544ms
  Thread calibration: mean lat.: 2419.038ms, rate sampling interval: 8544ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency    17.63s     7.53s   30.61s    57.69%
    Req/Sec   603.62     16.30   630.00     60.00%
  293837 requests in 1.00m, 186.07MB read
Requests/sec:   4896.99
Transfer/sec:      3.10MB
```

В то время как при R = 1000 latency уже 1.5ms:

```
veronika@MBP-Veronika anikina % wrk2 -t 8 -c 64 -d 1m -R 1000 -s lua/get.lua http://localhost:8080
Running 1m test @ http://localhost:8080
  8 threads and 64 connections
  Thread calibration: mean lat.: 1.970ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.046ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.062ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.052ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.046ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.061ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.026ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.252ms, rate sampling interval: 10ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     1.54ms  746.59us  20.96ms   79.08%
    Req/Sec   134.58    134.04     0.89k    92.86%
  59899 requests in 1.00m, 37.93MB read
Requests/sec:    998.16
Transfer/sec:    647.25KB
```

## Профилирование

По результатам профилирования:

### PUT

16% CPU тратится на репликацию запросов и ожидания ответа на них; 
15% аллокаций так же уходит на репликацию, 33% на сетевое взаимодействие;
37% локов берет httpClient при сетевом взаимодействии внутри кластера.

### GET

Аналогично, 16% cpu уходит на репликацию запросов и ожидание ответа от реплик;
13% аллокаций так же занимает репликация, 30% - сетевое взаимодействие;
и так же 37% локов уходит на httpClient при сетевом взаимодействии внутри кластера.

## Выводы

Понизилась пропускная способность кластера, это связано с затратами на репликацию и ожидание ответа 
от реплик, на это уходит 16% CPU. В качестве оптимизации можно реплицировать запросы 
асинхронно, это будет сделано в следующих заданиях.

Но вместе с этим мы повысили durability, так как теперь данные лежат на разных репликах, так же 
пользователь имеет возможность контролировать уровень consistency, задав нужные параметры репликации.

Большой процент и CPU, и аллокаций, и блокировок занимает сетевое взаимодейсвие в кластере через 
httpClient, как и в прошлой версии. В качестве оптимизации можно использовать более оптимальный сетевой протокол.
