### Нагрузчное тестирование

## PUT
Я использовал следующий скрипт для тестирования PUT запроса, ключ и значение - это случайные числа.
```
function request()
  path = "/v0/entity?id=" .. tostring(math.random(1, 100000000))
  body = tostring(math.random(1, 100000000))
  return wrk.format("PUT", path, wrk.headers, body)
end
```
750 RPS - это максимальный RPS на моем железе, при котором сервис справляется с нагрузкой  с приемлимым latency.
``` 
wrk -d 30 -t 1 -c 1 -R 750 -s ../put.lua http://localhost:19234
Running 30s test @ http://localhost:19234
  1 threads and 1 connections
  Thread calibration: mean lat.: 1.579ms, rate sampling interval: 10ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     1.53ms    1.86ms  37.34ms   98.10%
    Req/Sec   793.56    106.94     2.00k    93.54%
  22500 requests in 30.00s, 1.44MB read
Requests/sec:    749.98
Transfer/sec:     49.07KB
```
При увеличении RPS сервис начинал захлебываться и latency сильно увеличивалась.

```
wrk -d 30 -t 1 -c 1 -R 1000 -s ../put.lua http://localhost:19234
Running 30s test @ http://localhost:19234
  1 threads and 1 connections
  Thread calibration: mean lat.: 321.973ms, rate sampling interval: 1607ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency   663.14ms  615.02ms   1.70s    41.44%
    Req/Sec     1.05k   143.29     1.21k    58.33%
  29999 requests in 30.00s, 1.92MB read
Requests/sec:    999.99
Transfer/sec:     65.43KB
```

## GET
Я наполнил базу до 1.3 гб, чтобы тестирование проводилось более честно. Флашил memtable на диск при пороге в 4 МБ, чтобы больше данных хранилось в RAM.

для GET запроса использовался следующий скрипт:
```
function request()
  path = "/v0/entity?id=" .. tostring(math.random(1, 100000000))
  return wrk.format("GET", path, wrk.headers, wrk.body)
end
```

500 RPS - это максимальный RPS на моем железе, при котором сервис справляется с нагрузкой с приемлимым latency. (Non-2xx or 3xx responses - это ненайденные ключи)

```
wrk -d 30 -t 1 -c 1 -R 500 -s ../get.lua http://localhost:19234
Running 30s test @ http://localhost:19234
  1 threads and 1 connections
  Thread calibration: mean lat.: 1.561ms, rate sampling interval: 10ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     1.59ms    2.05ms  36.58ms   98.54%
    Req/Sec   528.87     76.15     1.44k    84.14%
  15001 requests in 30.00s, 0.99MB read
  Non-2xx or 3xx responses: 7417
Requests/sec:    500.02
Transfer/sec:     33.91KB
```

При увеличении RPS сервис начинал захлебываться и latency сильно увеличивалась.
```
wrk -d 30 -t 1 -c 1 -R 750 -s ../get.lua http://localhost:19234
Running 30s test @ http://localhost:19234
  1 threads and 1 connections
  Thread calibration: mean lat.: 28.114ms, rate sampling interval: 251ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     3.35s     1.26s    4.51s    80.18%
    Req/Sec   593.62    160.95     0.90k    64.56%
  19118 requests in 30.00s, 1.27MB read
  Non-2xx or 3xx responses: 9452
Requests/sec:    637.27
Transfer/sec:     43.22KB
```

Ожидаемо PUT запрос справился с большим RPS, чем GET: LSM дерево, которое лежит в основе моего DAO, оптимизировано под вставки в ущерб скорости чтения. 

### Профилирование

## CPU
Запустил async-profiler с интервалом сэмплирования 1ms и дал нагрузку, с которой справляется сервис, с помощью wrk, сначала PUT, затем GET (два отдельных прямоугольника получилось)

Получил следующие результаты:
# PUT
- ~45% сэмплов - селектор ждет на селекте
- ~40% сэмплов - пишем response в сеть
- ~7% сэмплов - логгирование
- ~4% cэмплов - вставляем entry в memtable

# GET
- ~38% сэмплов - ищем entry в sstables
- ~26% сэмплов - пишем response в сеть
- ~15% сэмплов - селектор ждет на селекте
- ~10% сэмплов - логгирование
- ~2% сэмплов - ищем entry в memtable


Результаты связаны опять же с тем, что PUT гораздо более дешевая операция, нам нужно всего лишь добавить в memtable, которая помещается в кэш, а не искать бинпоиском по всем sstables, хранящимся на диске, как в случае с GET

## ALLOC
Опять запустил async-profiler в режиме профилирования аллокаций и дал нагрузку с помощью wrk на минуту

# PUT
- ~73% сэмплов - логгирование
- ~15% сэмплов - обработка распаршенного запроса
- ~10% сэмплов - парсинг запроса


# GET
~81% сэмплов - аллокации, когда мы бинпоиском ищем по sstable ключ и берем view на mid элемент,
мы делаем аллокацию буквально на каждую итерацию бинпоиска.
~15 сэмплов - логгирование

Также отмечу, что у GET аллокаций более чем в 2 раза больше, чем у PUT, при меньшем количестве запросов
Получается, что PUT выгоднее не только в палне CPU, но также он делает гораздо меньше аллокаций.

Заоптимизировать GET запрос можно, добавив in-memory фильтр Блума и sparse index, но мы пожертвуем памятью и простотой решения. Также можно избавиться от этой аллокации в GET и не использовать view, но мы пожертвуем читабельностью и простотой.
Еще PUT и GET можно заоптимизировать убрав логи или уменьшив их количество, правда без логов будет грустно, можно попробовать поискать более производительный и менее аллоцирующий фреймворк для логгирования.