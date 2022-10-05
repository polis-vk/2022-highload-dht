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
они берут на себя работу по отправлению ответа в сеть. Теперь селекторы тратят 5% времени на то, чтобы положить таску в очередь, это в 2 раза больше, чем они читают из сети.
Можно попробовать поиграться с очередью.

# GET

GET мы тоже разгрузили переходом на асинхронность, но это не позволило нам сильно увеличить производительность, тут
мы упираемся в длинный поиск по sstables, можно попробовать добавить автоматический compaction, чтобы уменьшить кол-во sstables,
он не реализован в референсной реализации

## ALLOC

# GET

Стало больше аллокаций во время получения и парсинга запроса, это связано с тем, что мы
теперь больше забираем запросов из сети, так как добавили воркер-пул

# PUT

С PUT ситуация такая же, как и с GET

## LOCK

# PUT

Большинство локов берется на забирание таски из очереди (можно попробовать перейти на lock-free),
и на HttpSession во время отправки ответа на запрос

# GET

Берется гораздо меньше локов по сравнению с PUT, но на теже потребности.