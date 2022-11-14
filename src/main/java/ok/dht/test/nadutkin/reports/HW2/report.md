# Отчёт

Все тестирования проводятся при помощи скриптов [get](scripts/get.lua) и [put](scripts/put.lua).

**Get:**

```lua
id = 0
wrk.method = "GET"
request = function()
    wrk.path = "/v0/entity?id=" .. math.random(0, 1000000)
    id = id + 1
    return wrk.format(nil)
end
```

**Put:**

```lua
id = 0
wrk.method = "PUT"
request = function()
    wrk.path = "/v0/entity?id=" .. math.random(0, 1000000)
    wrk.body = "№ " .. id
    return wrk.format(nil)
end
```

А также `wrk` команд:

1. `wrk2 -t 8 -c 64 -d 30s -R 100000 -L http://localhost:19234 -s reports/HW2/scripts/put.lua`
2. `wrk2 -t 8 -c 64 -d 30s -R 100000 -L http://localhost:19234 -s reports/HW2/scripts/get.lua`

## Без вспомогательных `workers`

### PUT

```
Running 30s test @ http://localhost:19234
  8 threads and 64 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     1.27s   514.64ms   2.25s    58.89%
    Req/Sec    11.35k   322.29    12.02k    61.84%
  Latency Distribution (HdrHistogram - Recorded Latency)
 50.000%    1.27s 
 75.000%    1.74s 
 90.000%    1.94s 
 99.000%    2.14s 
 99.900%    2.21s 
 99.990%    2.24s 
 99.999%    2.25s 
100.000%    2.25s 

#[Mean    =     1266.760, StdDeviation   =      514.641]
#[Max     =     2252.800, Total count    =      1809353]
#[Buckets =           27, SubBuckets     =         2048]
----------------------------------------------------------
  2781797 requests in 30.00s, 177.75MB read
Requests/sec:  92730.05
Transfer/sec:      5.93MB
```

![image](histograms/no_workers/put_no_default.png)

### GET

```
Running 30s test @ http://localhost:19234
  8 threads and 64 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency   777.11ms  376.38ms   1.64s    59.00%
    Req/Sec    11.60k   487.60    12.54k    82.81%
  Latency Distribution (HdrHistogram - Recorded Latency)
 50.000%  763.39ms
 75.000%    1.10s 
 90.000%    1.30s 
 99.000%    1.56s 
 99.900%    1.61s 
 99.990%    1.63s 
 99.999%    1.64s 
100.000%    1.64s 

#[Mean    =      777.106, StdDeviation   =      376.380]
#[Max     =     1638.400, Total count    =      1845209]
#[Buckets =           27, SubBuckets     =         2048]
----------------------------------------------------------
  2837573 requests in 30.00s, 197.45MB read
  Non-2xx or 3xx responses: 38289
Requests/sec:  94589.57
Transfer/sec:      6.58MB
```

![image](histograms/no_workers/get_no_default.png)

## Обычный `ExecutorService`

### PUT

```
Running 30s test @ http://localhost:19234
  8 threads and 64 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     3.48s     1.18s    5.64s    57.24%
    Req/Sec     9.98k   173.04    10.32k    71.43%
  Latency Distribution (HdrHistogram - Recorded Latency)
 50.000%    3.45s 
 75.000%    4.50s 
 90.000%    5.14s 
 99.000%    5.52s 
 99.900%    5.58s 
 99.990%    5.61s 
 99.999%    5.64s 
100.000%    5.64s 

#[Mean    =     3476.062, StdDeviation   =     1176.200]
#[Max     =     5640.192, Total count    =      1589904]
#[Buckets =           27, SubBuckets     =         2048]
----------------------------------------------------------
  2441256 requests in 30.00s, 155.99MB read
Requests/sec:  81377.95
Transfer/sec:      5.20MB
```

![image](histograms/default_workers/put_default_default.png)

### GET

```
Running 30s test @ http://localhost:19234
  8 threads and 64 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     3.06s     1.19s    5.20s    57.20%
    Req/Sec     9.90k   342.19    10.41k    63.46%
  Latency Distribution (HdrHistogram - Recorded Latency)
 50.000%    3.01s 
 75.000%    4.13s 
 90.000%    4.70s 
 99.000%    5.12s 
 99.900%    5.18s 
 99.990%    5.19s 
 99.999%    5.20s 
100.000%    5.21s 

#[Mean    =     3064.426, StdDeviation   =     1193.119]
#[Max     =     5201.920, Total count    =      1579627]
#[Buckets =           27, SubBuckets     =         2048]
----------------------------------------------------------
  2479613 requests in 30.00s, 172.15MB read
  Non-2xx or 3xx responses: 27568
Requests/sec:  82656.83
Transfer/sec:      5.74MB
```

![image](histograms/default_workers/get_default_default.png)

## `LIFO`

### PUT

```
Running 30s test @ http://localhost:19234
  8 threads and 64 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     3.80s     1.40s    6.41s    58.32%
    Req/Sec     9.48k   294.97     9.98k    71.43%
  Latency Distribution (HdrHistogram - Recorded Latency)
 50.000%    3.72s 
 75.000%    4.97s 
 90.000%    5.81s 
 99.000%    6.31s 
 99.900%    6.38s 
 99.990%    6.40s 
 99.999%    6.41s 
100.000%    6.42s 

#[Mean    =     3797.264, StdDeviation   =     1402.948]
#[Max     =     6414.336, Total count    =      1508887]
#[Buckets =           27, SubBuckets     =         2048]
----------------------------------------------------------
  2361346 requests in 30.00s, 150.88MB read
Requests/sec:  78714.86
Transfer/sec:      5.03MB
```

![image](histograms/lifo_workers/put_lifo_default.png)

### GET

```
Running 30s test @ http://localhost:19234
  8 threads and 64 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     3.29s     1.27s    5.67s    58.51%
    Req/Sec     9.69k   321.36    10.12k    61.05%
  Latency Distribution (HdrHistogram - Recorded Latency)
 50.000%    3.19s 
 75.000%    4.43s 
 90.000%    5.04s 
 99.000%    5.53s 
 99.900%    5.62s 
 99.990%    5.65s 
 99.999%    5.67s 
100.000%    5.67s 

#[Mean    =     3291.421, StdDeviation   =     1265.312]
#[Max     =     5668.864, Total count    =      1541662]
#[Buckets =           27, SubBuckets     =         2048]
----------------------------------------------------------
  2425386 requests in 30.00s, 169.03MB read
  Non-2xx or 3xx responses: 49738
Requests/sec:  80849.41
Transfer/sec:      5.63MB
```

![image](histograms/lifo_workers/get_lifo_default.png)

## Выводы

Как можно видеть передача работы от селекторов к `executors` только ухудшила результат, попробуем разобраться в причинах.

### PUT

**Requests/sec:**

1. Без `executors`: 92730
2. Дефолтные `executors`: 81377 (Просадка на 12 %)
3. `executors` с `lifo`: 78714 (Просадка на 15 %)

Рассматривая графики cpu [put_no](analysis/default%20size/no_workers/put_no_default_cpu.html),
[put_default](analysis/default%20size/default_workers/put_default_default_cpu.html),
[put_lifo](analysis/default%20size/lifo_workers/put_lifo_default_cpu.html)

Можно увидеть, что работа с базой данных (`ServiceImpl.put`), никогда не занимала больше 11% от CPU,
основное время тратилось на работу с сетью (посылка ответов, приём запросов, работа с селекторами),
то есть put - не было bottle neck.

Если считать, что 'Без `executors`', проделывает задачу за время t
на 12 % производительнее, чем 'Дефолтные `executors`' (который проделывает задачу за время 1.136 * t); 
и на 15 % производительнее, чем '`executors` с `lifo`', проделывает задачу за время 1.176 * t.
То на `HttpServer.handleRequest` в
1. 'Без `executors`' тратится 0.3987 * t.
2. 'Дефолтные `executors`' тратится 0.2742 * 1.136 * t = 0.3115 * t
3. '`executors` с `lifo`' тратится 0.2663 * 1.176 * t = 0.3137 * t

Таким образом можно сказать, что добавление `executors` **ускорило** обработку запросов. 

Однако остаётся вопрос: Почему же просадка? 

Ответ лежит рядом, добавление `executors` заставило систему дополнительно работать над оркестрацией thread pool.
Вследствие чего системе приходится тратить ресурсы на `park` и `unpark`.
По итогу:
- 'Дефолтные `executors`' тратят ~19 % всего времени на обслуживание thread pool.
- '`executors` с `lifo`' тратят более 19 %.

Что в обоих случаях больше времени затраченного `put` в базу данных.

### GET

Аналогично, смотря на цифры и на
[get_no](analysis/default%20size/no_workers/get_no_default_cpu.html),
[get_default](analysis/default%20size/default_workers/get_default_default_cpu.html),
[get_lifo](analysis/default%20size/lifo_workers/get_lifo_default_cpu.html)

Видна просадка 'Дефолтные `executors`' на 12.6 % и '`executors` с `lifo`' на 14.5 % по сравнению с 'Без `executors`'.

При этом смотря на время работы обработки самого запроса (`HttpServer.handleRequest`) видно, что в
1. 'Без `executors`' на него тратится 0.3237 * t.
2. 'Дефолтные `executors`' тратится 0.2234 * 1.144* t = 0.256 * t
3. '`executors` с `lifo`' тратится 0.2122 * 1.17 * t = 0.248 * t

То есть `executors` дают выигрыш в обработке, однако оркестрация thread pool нивелирует это достижение.

Получается, что добавление `ExecutorService` бесполезно и даже вредно?
Не совсем. 

1. Как мы увидели они, сократили время обработки запроса,
то есть, если `HttpServer.handleRequest` увеличит свой вес в CPU, то мы можем получить выигрыш. 
Как мы знаем из предыдущего дз при уменьшении flushThresholdBytes
поход в базу данных становится более весомым по производительности,
так как база данных чаще сбрасывает данные на диск,
а после обходит эти файлы, тратит время на аллокацию загружая их к себе и ища в них данные.

2. Метод `park` вызывается в том случае, если очередь запросов пуста.
Cам `park` мы ускорить мы не можем. 
Так как selector не справляется с большим количеством запросов 
(что видно по тесту без `executors`, то увеличение нагрузки не приведёт к тому,
что потоки не успеют разобрать задачи)
Возможным решением могло быть переопределение сценария,
когда поток уходит в сон, сидя некоторое время в активном ожидании (что выглядит достаточно заморочено).

Попробуем увеличить `HttpServer.handleRequest` уменьшив количество информации, которое можно держать в памяти
(сделать это можно и при помощи простого `Thread.sleep`, но не хочется специально портить нашу модель,
а так получается возможный сценарий).


# Меньший `flushThresholdBytes = 1 << 18`

## Без вспомогательных `workers`

### PUT

```
Running 30s test @ http://localhost:19234
  8 threads and 64 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     1.62s   878.13ms   3.24s    59.84%
    Req/Sec    10.61k   834.06    11.58k    82.14%
  2684493 requests in 30.00s, 171.53MB read
Requests/sec:  89487.31
Transfer/sec:      5.72MB
```

### GET

```
Running 30s test @ http://localhost:19234
  8 threads and 64 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     5.96s     3.48s   15.06s    64.56%
    Req/Sec     9.11k   525.95    10.07k    62.50%
  2100862 requests in 30.00s, 145.02MB read
Requests/sec:  70030.84
Transfer/sec:      4.83MB

```

## Обычный `ExecutorService`

### PUT

```
Running 30s test @ http://localhost:19234
  8 threads and 64 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     3.18s     1.34s    5.73s    57.46%
    Req/Sec     9.71k   510.05    10.42k    56.94%
  2415670 requests in 30.00s, 154.35MB read
Requests/sec:  80525.34
Transfer/sec:      5.15MB
```

### GET

```
Running 30s test @ http://localhost:19234
  8 threads and 64 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     8.36s     1.77s   11.18s    59.57%
    Req/Sec     8.47k   589.97     9.08k    68.75%
  1897837 requests in 30.00s, 130.82MB read
Requests/sec:  63263.79
Transfer/sec:      4.36MB
```

## `LIFO`

### PUT

```
Running 30s test @ http://localhost:19234
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     3.33s     1.31s    5.71s    58.21%
    Req/Sec     9.67k   291.26    10.35k    80.00%
  2429251 requests in 30.00s, 155.22MB read
Requests/sec:  80980.55
Transfer/sec:      5.17MB

```

### GET

```
Running 30s test @ http://localhost:19234
  8 threads and 64 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     5.28s     1.56s    8.19s    57.72%
    Req/Sec     9.16k   406.19     9.90k    68.75%
  2233604 requests in 30.00s, 154.23MB read
  Requests/sec:  74456.07
  Transfer/sec:      5.14MB
```

## Выводы

### PUT

**Requests/sec:**
1. 'Без `executors`': 89487
2. 'Дефолтные `executors`': 80525 (Просадка на 10 %)
3. '`executors` с `lifo`': 80980 (Просадка на 9.5 %)

В целом результат повторяет тот,
что был достигнут при `flushThresholdBytes = 1 << 26`,
с незначительными погрешностями.

Что достаточно логично, так как 'Сбрасывание на диск выполняется в отдельном потоке',
а потому не сильно влияет на результат работы.

### GET

Отдельного внимания заслуживает `get` хоть и не сильно (74456 на 70030),
но '`executors` с `lifo`' смог обогнать 'Без `executors`'.

Возникает 2 вопроса:

1. Почему так получилось?
2. Почему 'Дефолтные `executors`' так сильно проигрывает,
когда '`executors` с `lifo`' получил выигрыш?

   
1. Ответом на 1 вопрос может послужить реализация метода `get`.
Не находя значения в базе, метод обходит все файлы и пытается найти в них,
очевидно, что при этом происходит большое количество аллокаций, 
что можно увидеть в [get_no_alloc](analysis/small%20size/no_workers/get_no_small_alloc.html),
где `MemorySegmentDao.get` занимает 95,28% всех аллокаций.
В следствии подобной реализации `HttpServer.handleRequest`,
непосредственно работающий с базой, начинает съедать всё больше CPU (пример [get_no_cpu](analysis/small%20size/no_workers/get_no_small_cpu.html)).
Как мы убедились выше `executors` ускоряет обработку запроса, а в связи с тем, что теперь это bottle neck нашей реализации,
то передача работы на `ExecutorService` даёт оптимизацию в скорости.

2. Причиной такого проигрыша можно назвать 2 вещи:
    - newFixedThreadPool. Если потоки '`executors` с `lifo`' не справлялись со своей задачей,
то `ThreadPool` увеличивал количество потоков в 2 раза.
В 'Дефолтные `executors`' количество потоков всегда оставалось постоянным,
поэтому `MemorySegmentDao.get` там занимает настолько больше места. (Мой косяк :-))
      - К моменту когда очередь доходила до старых запросов, они уже становились не нужны.
В связи с тем, что обработка теперь стала гораздо дольше, запросы, добавленные в конец, успевали протухнуть,
поэтому отправка ответов становилась невозможной.

# ИТОГИ:

Технологию стоит применять только в тех случаях, когда `handleRequest` становится слабым местом вашего сервиса,
в противном случае оркестрация ThreadPool будет съедать ресурс нивелирующий всю оптимизацию.

