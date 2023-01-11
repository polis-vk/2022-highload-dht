# Отчет

Профилирование проводилось на ноде, входящей в кластер из 4 четырех нод.
По ходу профилирование сделал модификацию, которая несколько улучшило RPS.

Изначальная реализация содержала один executor, на котором исполнялись
как сами запросы, так и код, ожидающий ответа от других нод кластера.

Профиль для запроса метода PUT ack=1 from=1:
```
Running 1m test @ http://localhost:1337
  4 threads and 64 connections
  Thread calibration: mean lat.: 2508.538ms, rate sampling interval: 9109ms
  Thread calibration: mean lat.: 2511.383ms, rate sampling interval: 9117ms
  Thread calibration: mean lat.: 2509.819ms, rate sampling interval: 9117ms
  Thread calibration: mean lat.: 2506.905ms, rate sampling interval: 9109ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency    21.96s     9.33s   39.09s    59.11%
    Req/Sec     4.14k   356.13     4.76k    60.00%
----------------------------------------------------------
  1051203 requests in 1.00m, 67.17MB read
  Non-2xx or 3xx responses: 119
Requests/sec:  17520.26
Transfer/sec:      1.12MB
```

[cpu heatmap & flame graph](profiles/2022-11-01-23-24-14_ack1_from1_put_t4_c64_R50000_d1m/cpu.html)
![image](profiles/2022-11-01-23-24-14_ack1_from1_put_t4_c64_R50000_d1m/cpu.png)

[alloc heatmap & flame graph](profiles/2022-11-01-23-24-14_ack1_from1_put_t4_c64_R50000_d1m/alloc.html)
![image](profiles/2022-11-01-23-24-14_ack1_from1_put_t4_c64_R50000_d1m/alloc.png)

[lock flame graph](profiles/2022-11-01-23-24-14_ack1_from1_put_t4_c64_R50000_d1m/lock.html)
![image](profiles/2022-11-01-23-24-14_ack1_from1_put_t4_c64_R50000_d1m/lock.png)


Далее разделил один executor на два, один для исполнения запросов, другой для ожидания.

Профиль для запроса метода PUT ack=1 from=1 в этом случае:
```
Running 1m test @ http://localhost:1337
  4 threads and 64 connections
  Thread calibration: mean lat.: 1229.371ms, rate sampling interval: 4993ms
  Thread calibration: mean lat.: 1560.326ms, rate sampling interval: 5656ms
  Thread calibration: mean lat.: 1562.673ms, rate sampling interval: 5681ms
  Thread calibration: mean lat.: 1559.912ms, rate sampling interval: 5689ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     2.17s     1.72s    4.82s    44.96%
    Req/Sec     5.35k   673.30     6.28k    72.73%
----------------------------------------------------------
  1192855 requests in 1.00m, 76.23MB read
  Non-2xx or 3xx responses: 375
Requests/sec:  19881.11
Transfer/sec:      1.27MB
```

[cpu heatmap & flame graph](profiles/2022-11-02-01-09-20_ack1_from1_connectionTimeout500ms_anotherOneExecutor_put_t4_c64_R20000_d1m/cpu.html)
![image](profiles/2022-11-02-01-09-20_ack1_from1_connectionTimeout500ms_anotherOneExecutor_put_t4_c64_R20000_d1m/cpu.png)

[alloc heatmap & flame graph](profiles/2022-11-02-01-09-20_ack1_from1_connectionTimeout500ms_anotherOneExecutor_put_t4_c64_R20000_d1m/alloc.html)
![image](profiles/2022-11-02-01-09-20_ack1_from1_connectionTimeout500ms_anotherOneExecutor_put_t4_c64_R20000_d1m/alloc.png)

[lock flame graph](profiles/2022-11-02-01-09-20_ack1_from1_connectionTimeout500ms_anotherOneExecutor_put_t4_c64_R20000_d1m/lock.html)
![image](profiles/2022-11-02-01-09-20_ack1_from1_connectionTimeout500ms_anotherOneExecutor_put_t4_c64_R20000_d1m/lock.png)

Как мы видим RPS вырос с 17.5 тысяч до почти 20.

Далее следуют другие профили для этой же реализации.

Профиль для запроса метода PUT ack=2 from=3:
```
Running 1m test @ http://localhost:1337
  4 threads and 64 connections
  Thread calibration: mean lat.: 4400.750ms, rate sampling interval: 14098ms
  Thread calibration: mean lat.: 4346.192ms, rate sampling interval: 14065ms
  Thread calibration: mean lat.: 4376.419ms, rate sampling interval: 14090ms
  Thread calibration: mean lat.: 4342.130ms, rate sampling interval: 14057ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency    24.73s     9.97s   41.35s    55.59%
    Req/Sec     1.52k   117.96     1.68k    41.67%
----------------------------------------------------------
  351277 requests in 1.00m, 22.45MB read
  Socket errors: connect 0, read 0, write 0, timeout 97
  Non-2xx or 3xx responses: 98
Requests/sec:   5854.59
Transfer/sec:    383.10KB
```

[cpu heatmap & flame graph](profiles/2022-11-02-01-15-34_ack2_from3_connectionTimeout500ms_anotherOneExecutor_put_ack2_from3_t4_c64_R20000_d1m/cpu.html)
![image](profiles/2022-11-02-01-15-34_ack2_from3_connectionTimeout500ms_anotherOneExecutor_put_ack2_from3_t4_c64_R20000_d1m/cpu.png)

[alloc heatmap & flame graph](profiles/2022-11-02-01-15-34_ack2_from3_connectionTimeout500ms_anotherOneExecutor_put_ack2_from3_t4_c64_R20000_d1m/alloc.html)
![image](profiles/2022-11-02-01-15-34_ack2_from3_connectionTimeout500ms_anotherOneExecutor_put_ack2_from3_t4_c64_R20000_d1m/alloc.png)

[lock flame graph](profiles/2022-11-02-01-15-34_ack2_from3_connectionTimeout500ms_anotherOneExecutor_put_ack2_from3_t4_c64_R20000_d1m/lock.html)
![image](profiles/2022-11-02-01-15-34_ack2_from3_connectionTimeout500ms_anotherOneExecutor_put_ack2_from3_t4_c64_R20000_d1m/lock.png)

Профиль для запроса метода GET ack=1 from=1:
```
Running 1m test @ http://localhost:1337
  4 threads and 64 connections
  Thread calibration: mean lat.: 1747.846ms, rate sampling interval: 4714ms
  Thread calibration: mean lat.: 1748.185ms, rate sampling interval: 4718ms
  Thread calibration: mean lat.: 1747.105ms, rate sampling interval: 4698ms
  Thread calibration: mean lat.: 1747.382ms, rate sampling interval: 4702ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency   396.60ms  715.42ms   2.77s    84.92%
    Req/Sec     5.28k   471.82     6.35k    80.00%
----------------------------------------------------------
  1199246 requests in 1.00m, 187.56MB read
  Non-2xx or 3xx responses: 128
Requests/sec:  19987.54
Transfer/sec:      3.13MB
```

[cpu heatmap & flame graph](profiles/2022-11-02-19-56-15_ack1_from1_connectionTimeout500ms_anotherOneExecutor_get_t4_c64_R20000_d1m/cpu.html)
![image](profiles/2022-11-02-19-56-15_ack1_from1_connectionTimeout500ms_anotherOneExecutor_get_t4_c64_R20000_d1m/cpu.png)

[alloc heatmap & flame graph](profiles/2022-11-02-19-56-15_ack1_from1_connectionTimeout500ms_anotherOneExecutor_get_t4_c64_R20000_d1m/alloc.html)
![image](profiles/2022-11-02-19-56-15_ack1_from1_connectionTimeout500ms_anotherOneExecutor_get_t4_c64_R20000_d1m/alloc.png)

[lock flame graph](profiles/2022-11-02-19-56-15_ack1_from1_connectionTimeout500ms_anotherOneExecutor_get_t4_c64_R20000_d1m/lock.html)
![image](profiles/2022-11-02-19-56-15_ack1_from1_connectionTimeout500ms_anotherOneExecutor_get_t4_c64_R20000_d1m/lock.png)


Профиль для запроса метода GET ack=2 from=3:
```
Running 1m test @ http://localhost:1337
  4 threads and 64 connections
  Thread calibration: mean lat.: 4449.356ms, rate sampling interval: 16359ms
  Thread calibration: mean lat.: 4449.267ms, rate sampling interval: 16359ms
  Thread calibration: mean lat.: 4448.174ms, rate sampling interval: 16359ms
  Thread calibration: mean lat.: 4449.022ms, rate sampling interval: 16359ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency    30.72s    12.51s    0.89m    58.79%
    Req/Sec     1.41k    99.57     1.53k    33.33%
----------------------------------------------------------
  335391 requests in 1.00m, 52.45MB read
  Socket errors: connect 0, read 0, write 0, timeout 5
  Non-2xx or 3xx responses: 60
Requests/sec:   5589.85
Transfer/sec:      0.87MB
```

[cpu heatmap & flame graph](profiles/2022-11-02-20-12-00_ack2_from3_connectionTimeout500ms_anotherOneExecutor_get_ack2_from3_t4_c64_R50000_d1m/cpu.html)
![image](profiles/2022-11-02-20-12-00_ack2_from3_connectionTimeout500ms_anotherOneExecutor_get_ack2_from3_t4_c64_R50000_d1m/cpu.png)

[alloc heatmap & flame graph](profiles/2022-11-02-20-12-00_ack2_from3_connectionTimeout500ms_anotherOneExecutor_get_ack2_from3_t4_c64_R50000_d1m/alloc.html)
![image](profiles/2022-11-02-20-12-00_ack2_from3_connectionTimeout500ms_anotherOneExecutor_get_ack2_from3_t4_c64_R50000_d1m/alloc.png)

[lock flame graph](profiles/2022-11-02-20-12-00_ack2_from3_connectionTimeout500ms_anotherOneExecutor_get_ack2_from3_t4_c64_R50000_d1m/lock.html)
![image](profiles/2022-11-02-20-12-00_ack2_from3_connectionTimeout500ms_anotherOneExecutor_get_ack2_from3_t4_c64_R50000_d1m/lock.png)


# Выводы
* Запросы, которые предполагают обращение к нескольким репликам 
исполняются сильно медленнее, что по-видимому связно с синхронизацией.
На профилях cpu видно, что количество sample-ов в executor-е увеличилось.
Возможно также executor, ожидающий ответов от других не успевает их разгребать.
Не будем также забывать, что 4 ноды запускаются на одном железном хосте.
* Заметим, что много времени также тратится на работу HttpClient (см картинку нижу). Возможно
другой, бинарный протокол будет работать быстрее.
![](img.png)
