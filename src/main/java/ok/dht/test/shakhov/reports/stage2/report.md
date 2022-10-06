### Нагрузчное тестирование

Со стадии 1 я немного порефакторил код и ускорил его, данные со стадии 1 теперь не актуальны,
я провёл новое нагрузочное тестирование и выяснил, что сервис держит 14K RPS PUT и 6K RPS GET

## PUT
```
math.randomseed(os.time())

function request()
  path = "/v0/entity?id=" .. tostring(math.random(1, 10000000))
  body = tostring(math.random(1, 10000000))
  return wrk.format("PUT", path, wrk.headers, body)
end
```

сервис ускорился на 221%, теперь он способен выдержать 45K RPS PUT
```
 wrk -d 120 -t 4 -c 64 -R 45000 -s ../put.lua http://localhost:19234
Running 2m test @ http://localhost:19234
  4 threads and 64 connections
  Thread calibration: mean lat.: 1.348ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.348ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.349ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.348ms, rate sampling interval: 10ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     1.51ms    1.86ms  61.15ms   97.95%
    Req/Sec    11.87k     1.42k   36.44k    82.34%
  5398258 requests in 2.00m, 344.93MB read
Requests/sec:  44985.58
Transfer/sec:      2.87MB
```

## GET
Скрипт:
```
math.randomseed(os.time())

function request()
  path = "/v0/entity?id=" .. tostring(math.random(1, 1000000))
  return wrk.format("GET", path, wrk.headers, wrk.body)
end
```
Сервис ускорился на GET запросы тоже на 33%, теперь он способен выдержать 8K RPS
```
wrk -d 120 -t 4 -c 64 -R 8000 -s ../get.lua http://localhost:19234
Running 2m test @ http://localhost:19234
  4 threads and 64 connections
  Thread calibration: mean lat.: 1.661ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.636ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.571ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.592ms, rate sampling interval: 10ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     1.45ms    2.00ms  57.98ms   98.56%
    Req/Sec     2.11k   179.97     4.67k    71.04%
  959725 requests in 2.00m, 62.98MB read
  Non-2xx or 3xx responses: 5900
Requests/sec:   7997.69
Transfer/sec:    537.43KB
```

Разница объясняется тем, что в PUT запросе мы упирались в то, что Selector выполнял весь запрос, включая syscall send.

### Профилирование

## CPU

# PUT

По сравнению с предыдущим результатом, мы теперь обрабатывам больше запросов благодаря тому, что селекторы передают запросы на пулл воркеров,
воркеры берут на себя работу по отправлению ответа в сеть. 
1. Теперь 5% сэмплов - это селекторы посылают сигнал, чтобы поток проснулся (pthread_cond_signal)
когда кладут таску в пустую очередь, в 2 раза больше сэмплов, чем где они читают из сети.
2. Также 9% сэмплов это воркеры, которые засыпают
на ожидании того, когда в очередь попадет таска.
3. Еще 9% ушло на то, чтобы воркер при unlock, после того как забрал таску, разбудил следующий поток

Значит очередь недостаточно заполняется, при этом много CPU time тратится на contention за 
эту недостаточно заполненную очередь. Первое решается повышением нагрузки, второе уменьшением числа воркеров.

# GET

GET мы тоже разгрузили переходом на пул, но это не позволило нам сильно увеличить производительность, тут
мы упираемся в длинный поиск по sstables, можно попробовать добавить автоматический compaction, чтобы уменьшить кол-во sstables,
он не реализован в референсной реализации. Тут нет такого контешена за очередь, воркеры заняты поиском
по sstables. Также тут лучше ситуация по загруженности очереди, воркеры медленнее разбирают задачи.

## ALLOC

# GET

Стало больше аллокаций во время получения и парсинга запроса, это связано с тем, что мы
теперь больше забираем запросов из сети, так как добавили воркер-пул.
Стало больше аллокаций итератора при select() на селекторе, связано с тем, что селекторы, разгрузившись могут больше селектать.

# PUT

Как и в гете стало больше аллокаций итератора при select() на селекторе.
В остальном картина выглядит похоже.

## LOCK

# PUT

- ~46% - cэмплов на то, чтобы получить лок после await когда очередь станет непустой.
- ~16% - cэмплов на то, чтобы получить лок на очередь, когда его кто-то держит
- ~35% - cэмплов лок на HttpSession во время отправки ответа на запрос, похоже что в одну сессию пытаются
писать несколько потоков

# GET

- ~56% - cэмплов на то, чтобы получить лок на очередь, когда его кто-то держит
- ~18% - cэмплов на то, чтобы получить лок после await когда очередь станет непустой
- ~13% - cэмплов лок на HttpSession во время отправки ответа на запрос
- ~5% - cэмплов лок на очередь, когда вставляем таску

GET Меньше ждут на локах по сравнению с PUT, т.к. меньше сэмплов,
PUT будет лучше, если уменьшить кол-во потоков, понизив contention за пустую очередь,
а GET наоборот, т.к. его очередь реже бывает пустой, и потоки будут работать.