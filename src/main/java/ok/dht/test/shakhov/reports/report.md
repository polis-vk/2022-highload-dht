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
3500 RPS - это максимальный RPS на моем железе, при котором сервис справляется с нагрузкой  с приемлимым latency.
``` 
wrk -d 10 -t 1 -c 1 -R 3500 -s put.lua http://localhost:19234
Running 10s test @ http://localhost:19234
  1 threads and 1 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     1.15ms    1.47ms  16.78ms   96.84%
    Req/Sec       -nan      -nan   0.00      0.00%
  34997 requests in 10.00s, 2.24MB read
Requests/sec:   3500.03
Transfer/sec:    229.01KB
```
При увеличении RPS сервис начинал захлебываться и latency сильно увеличивалась.

```
 wrk -d 10 -t 1 -c 1 -R 4000 -s put.lua http://localhost:19234
Running 10s test @ http://localhost:19234
  1 threads and 1 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency   193.19ms  105.12ms 386.30ms   59.59%
    Req/Sec       -nan      -nan   0.00      0.00%
  38454 requests in 10.00s, 2.46MB read
Requests/sec:   3845.78
Transfer/sec:    251.63KB
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

2250 RPS - это максимальный RPS на моем железе, при котором сервис справляется с нагрузкой с приемлимым latency. (Non-2xx or 3xx responses - это ненайденные ключи)

```
wrk -d 10 -t 1 -c 1 -R 2250 -s get.lua http://localhost:19234
Running 10s test @ http://localhost:19234
  1 threads and 1 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     1.50ms    1.45ms  17.15ms   96.36%
    Req/Sec       -nan      -nan   0.00      0.00%
  22498 requests in 10.00s, 1.49MB read
  Non-2xx or 3xx responses: 11119
Requests/sec:   2250.14
Transfer/sec:    152.61KB
```

При увеличении RPS сервис начинал захлебываться и latency сильно увеличивалась.
```
wrk -d 10 -t 1 -c 1 -R 2750 -s get.lua http://localhost:19234
Running 10s test @ http://localhost:19234
  1 threads and 1 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency   426.50ms  227.70ms 804.35ms   60.71%
    Req/Sec       -nan      -nan   0.00      0.00%
  25288 requests in 10.00s, 1.67MB read
  Non-2xx or 3xx responses: 12495
Requests/sec:   2529.01
Transfer/sec:    171.52KB
```

Ожидаемо PUT запрос справился с большим RPS, чем GET: LSM дерево, которое лежит в основе моего DAO, оптимизировано под вставки в ущерб скорости чтения. 

### Профилирование

## CPU
Запустил async-profiler с интервалом сэмплирования 500us и дал нагрузку с помощью wrk, сначала PUT, затем GET (два отдельных прямоугольника получилось)

Получил следующие результаты:
# PUT
- ~75% сэмплов - селектор ждет на селекте
- ~21% сэмплов - пишем response в сеть
- ~1% cэмплов - вставляем entry в memtable

# GET
- ~40% сэмплов - селектор ждет на селекте
- ~30% сэмплов - ищем entry в sstables
- ~26% сэмплов - пишем response в сеть


Результаты связаны опять же с тем, что PUT гораздо более дешевая операция, нам нужно всего лишь добавить в memtable, которая помещается в кэш, а не искать бинпоиском по всем sstables, хранящимся на диске, как в случае с GET

## ALLOC
Опять запустил async-profiler в режиме профилирования аллокаций и дал нагрузку с помощью wrk

# PUT
- ~50% сэмплов - во время парсинга запроса
- ~30% сэмплов - во время работы handler метода (вставка в memtable + создание Response)
- ~10% сэмплов - передача параметров запроса в нужный handler метод
- ~10% сэмплов - отправка Response по сети

# GET
~92% сэмплов - аллокация, когда мы бинпоиском ищем по sstable ключ и берем view на mid элемент,
мы делаем аллокацию буквально на каждую итерацию бинпоиска.
остальные аллокации незначительны

Также отмечу, что у GET аллокаций в 6 раз больше, чем у PUT, при меньшем количестве запросов
Получается, что PUT выгоднее не только в палне CPU, но также он делает гораздо меньше аллокаций.

Заоптимизировать GET запрос можно, добавив in-memory фильтр Блума и sparse index, но мы пожертвуем памятью и простотой решения. Также можно избавиться от этой аллокации в GET и не использовать view, но мы пожертвуем читабельностью и простотой.
