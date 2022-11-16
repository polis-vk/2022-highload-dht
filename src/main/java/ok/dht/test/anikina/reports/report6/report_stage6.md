# Нагрузочное тестирование

Предварительно база нагружена на 1ГБ последовательными ключами.
Для начала запустим запросы с range = 20:

```
veronika@MacBook-Pro-Veronika anikina % wrk2 -t 8 -c 64 -d 2m -R 10000 -s lua/get-range.lua http://localhost:8080
Running 2m test @ http://localhost:8080
  8 threads and 64 connections
  Thread calibration: mean lat.: 0.944ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 0.979ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 0.989ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 0.977ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 0.987ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 0.990ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 0.968ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 0.950ms, rate sampling interval: 10ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     0.96ms  429.81us   3.93ms   63.12%
    Req/Sec     1.31k   125.94     1.78k    71.57%
  1199842 requests in 2.00m, 86.96MB read
Requests/sec:   9998.72
Transfer/sec:    742.09KB
```

Сервис выдерживает R = 10000, latency составляет 0.96ms, на уровне обычных get запросов.

Попробуем увеличить range до 50:

```
veronika@MacBook-Pro-Veronika anikina % wrk2 -t 8 -c 64 -d 2m -R 10000 -s lua/get-range.lua http://localhost:8080
Running 2m test @ http://localhost:8080
  8 threads and 64 connections
  Thread calibration: mean lat.: 362.928ms, rate sampling interval: 787ms
  Thread calibration: mean lat.: 363.457ms, rate sampling interval: 787ms
  Thread calibration: mean lat.: 363.155ms, rate sampling interval: 788ms
  Thread calibration: mean lat.: 361.458ms, rate sampling interval: 784ms
  Thread calibration: mean lat.: 362.681ms, rate sampling interval: 786ms
  Thread calibration: mean lat.: 362.029ms, rate sampling interval: 785ms
  Thread calibration: mean lat.: 362.809ms, rate sampling interval: 788ms
  Thread calibration: mean lat.: 362.406ms, rate sampling interval: 787ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency   342.32ms  309.65ms   1.15s    35.04%
    Req/Sec     1.24k    97.87     2.20k    89.50%
  1189177 requests in 2.00m, 12.75GB read
  Socket errors: connect 0, read 0, write 0, timeout 32
Requests/sec:   9909.83
Transfer/sec:    108.83MB
```

Теперь уже сервис не справляется с нагрузкой R = 10000, попробуем уменьшить rate:

```
veronika@MacBook-Pro-Veronika anikina % wrk2 -t 8 -c 64 -d 2m -R 5000 -s lua/get-range.lua http://localhost:8080
Running 2m test @ http://localhost:8080
  8 threads and 64 connections
  Thread calibration: mean lat.: 1.151ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.122ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.142ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.199ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.151ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.150ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.171ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.116ms, rate sampling interval: 10ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     1.17ms    2.16ms 308.74ms   99.91%
    Req/Sec   657.07     87.63     2.22k    66.92%
  599943 requests in 2.00m, 10.60GB read
Requests/sec:   4999.49
Transfer/sec:     90.48MB
```

Теперь latency составляет 1.17ms.

Увеличивать range еще больше нет особого смысла, тк R = 5000 уже достаточно мало, а с увеличением 
range пропускная способность сервиса падает.

# Профилирование

Профилирование проводилось для запросов с range = 20 и с range = 50.

## CPU
### Range = 20:
Теперь 66% cpu тратится на работу с dao, а именно на получение итератора по MemorySegment. 
Еще 8% уходит на запись response в сокет через StreamingQueueItem.
### Range = 50:
Здесь соотношение немного другое, 56% - получение итератора, и 18% - запись response в сокет, 
ведь теперь данных для записи больше.

## ALLOC
### Range = 20:
24% аллокаций уходит на получение итератора, 6% на запись в сокет, 50% на чтение и парсинг запроса из буфера, и
15% на селекторы.
### Range = 50:
Здесь же 24% уходит на запись в сокет, 19% на получение итератора, и 55% на селекторы.

## LOCK
Новых локов не добавилось, все локи занимает извлечение из очереди тасок и вставка новых внутри threadPoolExecutor.

# Выводы

1. При значениях range около 20 потребление ресурсов такое же, как при обычных get запросах.
2. При этом при увеличении range сервис выдерживает почти пропорционально меньший rate.
3. В качестве оптимизации имеет смысл так же собирать кворум на значение ключа, 
чтобы обеспечить хороший уровень consistency.
