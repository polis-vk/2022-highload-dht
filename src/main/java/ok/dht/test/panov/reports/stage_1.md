# Stage 1

## Нагрузочное тестирование

### PUT

Тестирование производится при помощи следующего скрипта, который генерирует случайный ключ и кладет значение по нему.

```
request = function()
    id = math.random(1, 10000)
    path = "/v0/entity?id=" .. id
    body = "id" .. id
    return wrk.format("PUT", path)
end
```

Попробуем протестировать с `rate = 1000`:

`wrk2 -d 1m -t 1 -c 1 -R 1000 -s PutStableLoad.lua "http://localhost:19234"`

Результат работы:

```
Running 1m test @ http://localhost:19234
  1 threads and 1 connections
  Thread calibration: mean lat.: 1.019ms, rate sampling interval: 10ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     1.05ms  474.07us  13.54ms   74.69%
    Req/Sec     1.05k    74.26     2.22k    92.62%
  59999 requests in 1.00m, 3.83MB read
Requests/sec:    999.99
Transfer/sec:     65.43KB
```

Видим, что сервер справляется с такой нагрузкой без каких либо проблем.

Увеличим рейт и протеструем `rate = 10000`:

`wrk2 -d 1m -t 1 -c 1 -R 10000 -s PutStableLoad.lua "http://localhost:19234"`


Результат работы:

```
Running 1m test @ http://localhost:19234
  1 threads and 1 connections
  Thread calibration: mean lat.: 29.025ms, rate sampling interval: 182ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency    37.31ms   56.73ms 322.56ms   84.55%
    Req/Sec    10.03k     1.35k   16.71k    87.59%
  599985 requests in 1.00m, 38.34MB read
Requests/sec:   9999.86
Transfer/sec:    654.29KB
```

Сервер справляется с такой нагрузкой, но уже видно, что latency выросло до 37 ms.

Увеличим рейт еще и протеструем `rate = 100000`:

`wrk2 -d 1m -t 1 -c 1 -R 100000 -s PutStableLoad.lua "http://localhost:19234"`

Результат работы:

```
Running 1m test @ http://localhost:19234
  1 threads and 1 connections
  Thread calibration: mean lat.: 4306.856ms, rate sampling interval: 16424ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency    31.16s    12.76s    0.89m    58.18%
    Req/Sec    11.11k   135.76    11.20k    66.67%
  657619 requests in 1.00m, 42.02MB read
Requests/sec:  10960.43
Transfer/sec:    717.14KB
```

С такой нагрузкой видно, что сервер не справляется. Latency достигло 31s, а количество запросов в 10 раз меньше чем должно быть.

### GET

Предворительно я заполнил базу данными примерно на 1.3GB.

Тестирование производилось скриптом, который достает значение по случайному ключу.

```
request = function()
    id = math.random(1, 10000)
    path = "/v0/entity?id=" .. id
    return wrk.format("GET", path)
end
```

Протестируем с `rate = 1000`:

`wrk2 -d 1m -t 1 -c 1 -R 1000 -s GetStableLoad.lua "http://localhost:19234"`

Резутьтат работы:

```
Running 1m test @ http://localhost:19234
  1 threads and 1 connections
  Thread calibration: mean lat.: 1.404ms, rate sampling interval: 10ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     1.18ms  476.91us   5.11ms   68.47%
    Req/Sec     1.06k    85.57     1.44k    81.28%
  59999 requests in 1.00m, 690.41MB read
Requests/sec:    999.99
Transfer/sec:     11.51MB
```

Сервер успешно справляется с такой нагрузкой.

Увеличим нагрузку до `rate = 5000`:

`wrk2 -d 1m -t 1 -c 1 -R 5000 -s GetStableLoad.lua "http://localhost:19234"`


```
Running 1m test @ http://localhost:19234
  1 threads and 1 connections
  Thread calibration: mean lat.: 1.849ms, rate sampling interval: 10ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     1.80ms    2.93ms  22.90ms   89.24%
    Req/Sec     5.28k   513.09     7.78k    71.76%
  299997 requests in 1.00m, 3.37GB read
Requests/sec:   4999.96
Transfer/sec:     57.53MB
```

Резутьтат работы:

С такой нагрузкой сервер также справляется, но немного увеличивается latency.

Увеличим нагрузку до `rate = 10000`:

`wrk2 -d 1m -t 1 -c 1 -R 10000 -s GetStableLoad.lua "http://localhost:19234"`

Резутьтат работы:

```
Running 1m test @ http://localhost:19234
  1 threads and 1 connections
  Thread calibration: mean lat.: 1213.667ms, rate sampling interval: 4755ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     9.76s     4.13s   17.15s    59.66%
    Req/Sec     7.09k   394.52     7.52k    70.00%
  428300 requests in 1.00m, 4.81GB read
Requests/sec:   7138.37
Transfer/sec:     82.14MB
```

Серер захлебывается, и не справляется с нагрузкой. Latency возрастает практически до 10 секунд, а количество запросов в секунду выполнющихся значительно меньше производимых.

## Профилирование

Во время профилирования база уже была заполнена тетовыми данными.

### GET

#### CPU

Примерно 22% времени тратится на запись ответа, 19% на чтение запроса их сокета, 37% процентов на селект и около 10% на поход в базу данных.

## POST

#### CPU

Примерно 24% времени тратится на запись ответа, 18% на чтение запроса их сокета, 37% процентов на селект и около 9% на поход в базу данных.

