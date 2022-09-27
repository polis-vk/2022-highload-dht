# Отчёт

## PUT

Для `PUT` запросов был написан [скрипт](scripts/put.lua)

```lua
id = 0
wrk.method = "PUT"
request = function()
    wrk.path = "/v0/entity?id=" .. id
    wrk.body = "№ " .. id
    id = id + 1
    return wrk.format(nil)
end
```

### 10000 запросов

Запускаем команду `wrk2 -t 1 -c 1 -d 10s -R 10000 -L http://localhost:19234 -s reports/HW1/scripts/put.lua`

Получаем следующий результат

```
  1 threads and 1 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     1.52ms    5.75ms  51.84ms   97.40%
    Req/Sec        nan       nan   0.00      0.00%
  Latency Distribution (HdrHistogram - Recorded Latency)
 50.000%  674.00us
 75.000%    0.97ms
 90.000%    1.14ms
 99.000%   42.94ms
 99.900%   51.55ms
 99.990%   51.78ms
 99.999%   51.84ms
100.000%   51.87ms      
----------------------------------------------------------
  99986 requests in 10.00s, 6.39MB read
Requests/sec:   9998.55
Transfer/sec:    654.20KB
```

![image](histograms/put_10000.png)

**Итог:**
1. Сервер справляется с нагрузкой
2. Среднее latency = 1.52 ms. 
3. Количество запросов в секнду 9998.55 из 10000

### 20000 запросов

Запрос: `wrk2 -t 1 -c 1 -d 10s -R 20000 -L http://localhost:19234 -s reports/HW1/scripts/put.lua`

Результат работы:

```
  1 threads and 1 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency   111.02ms   27.14ms 160.38ms   58.08%
    Req/Sec        nan       nan   0.00      0.00%
  Latency Distribution (HdrHistogram - Recorded Latency)
 50.000%  103.42ms
 75.000%  141.57ms
 90.000%  150.01ms
 99.000%  158.72ms
 99.900%  160.38ms
 99.990%  160.51ms
 99.999%  160.51ms
100.000%  160.51ms

----------------------------------------------------------
  197065 requests in 10.00s, 12.59MB read
Requests/sec:  19706.83
Transfer/sec:      1.26MB
```

![image](histograms/put_20000.png)

**Итог:**
1. Сервер перестаёт справляться со своей работой, начинает пропускать много запросов
2. Среднее latency = 111 ms
3. Количество запросов в секунду 19706 из 20000

