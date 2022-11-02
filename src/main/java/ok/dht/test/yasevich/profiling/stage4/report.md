* Имеем lua скрипты [GET](../scripts/get.lua) и [PUT](../scripts/put.lua), генерирующие соответствующие запросы со
  случайными ключами и значениями вида key n value m
* `5 Мб` flushThreshold
* Размер очереди `1000`
* Размер кластера `3`

Прогрев сервер, запускаем профилирование put запросов, потом сразу же запускаем профилирование get.
Профилировщик запускаем в трёх режимах: cpu, alloc и lock

`./profiler.sh -f put.html -e cpu,alloc,lock --chunktime 1s start Server`

wrk2 запускаем в

* 64 соединений
* 8 потоков
* 1 минута

Для чистоты эксперимента (и так как это не лучший способ обрабатывать случай с болеющей нодой) код в
Node.managePossibleIllness() и Node.managePossibleRecovery() закомментирован :)

## Тест 1

Попробуем нагрузить сервис предельной нагрузкой из прошлого stage

* `60к` rate на put
* `50к` rate на get
* добавим скрипты [get-replicating](../scripts/get-replicating.lua), [put-replicating](../scripts/put-replicating.lua)
* которые используют параметр `replicas=1/1` (чтобы сервис работал как в прошлом stage)

Пробуя сервис нагрузкой из прошлого stage обнаружил, что он с ней не справляется и точка разладки теперь
около `20k-25k` rate.
На flame графе получилось 4 момента: put с rate `22500` (не справился), put c rate `21250`, put c rate
`21500` ([wrk-report](wrk/wrk-put-report1)), get c rate `19000` ([wrk-report](wrk/wrk-get-report1))

[сpu](html/cpu.html), [alloc](html/alloc.html), [lock](html/lock.html)

Похожие цифры были в stage3 (тест2 в отчёте) для `40k` put rate и `30k` get rate

Получили для put:

* Средняя задержка в `2.80ms` (`2.30ms` для теста2 из stage3)
* `90.0%` перцентиль в `6.15ms` (vs `3.85ms`)
* `99.0%` - `17.77ms` (vs `7.85ms`)
* `99.9%` - `29.60ms` (vs `12.44ms`)

Для get:

* Средняя задержка `1.43ms` (`1.5ms` для теста2 из stage3)
* `90.0%` перцентиль `2.37ms` (vs `2.68ms`)
* `99.0%` - `6.41ms` (vs `4.04ms`)
* `99.9%` - `11.99ms` (vs `7.12ms`)

Очевидно, производительность сервиса упала

При профилировании `CPU` наблюдаем:

* `6%` работа сборщика мусора
* `13%` + `5%` работа селекторов
* около `10%` работает ForkJoinPool.managedBlock() и около `3%` ForkJoinPool.awaitWork()
* `60%` работа воркеров:
    * ~`13%` парков - queue.take() из рабочей очереди
    * `24%` парков - ThreadPool берёт таск из SynchronousQueue (выглядит не очень нормально)
    * .offer в эту очередь делает HttpClientImplSelectorManager (~`4.3%` сэмплов) и CF.completeAsync (~`1%`)
    * около `4%` handleReplicatingRequest()
    * оставшееся связано с обработкой готовой CompletableFuture, отправкой ответов по сети

`Alloc`:

* `80%` аллокаций приходится на работу воркеров
    * около `2.5%` уходит на работу TimeStampingDao.get()
    * около `3.5%` уходит на sendResponse
    * остальные сэмплы с префиксом java.net.http
* оставшиеся `20%` - селекторы

`Lock`:

* `2%` ForkJoinPoolWorkerThread.run
* `12%` + `5%` работа селекторов
* `11.52%`, `181923 сэмплов` ReentrantLockов для Queue.take()
* моих локов, используемых, чтобы обновить текущий ответ на get, если только что
  полученный имеет более свежий timestamp, всего `2173 сэмла` для get (`0` для put) [img](imgs/locks.png)
* `68%` в ThreadPoolExecutor.run сэмплов с префиксом java.net.http

## Тесты 2,3

Возьмём rate из прошлого stage, но на этот раз воспользуемся скриптами без параметра replicas, что значит, что будут
использоваться параметры по умолчанию для кластера из `трёх нод`: `ack = 2`, `from = 3`

`wrk2 -c 64 -t 8 -d 1m -R 21500 -s put.lua --latency http://localhost:12353 > wrk-put-report2`

`wrk2 -c 64 -t 8 -d 1m -R 19000 -s get.lua --latency http://localhost:12353 > wrk-get-report2`

По итогу сервис не справился с цифрами из прошлого теста в реплицирующем режиме [wrk-report](wrk/wrk-put-report2)
, [wrk-report](wrk/wrk-get-report2)

[сpu](html/cpu2.html), [alloc](html/alloc2.html), [lock](html/lock2.html) Выглядят примерно так же, разве что сразу
бросается в глаза, что на сборщик мусора приходится уже `10%` времени, а не `6%`

Производительность по сравнению с тестом, где `ack, from = 1` упала: сервис справился только с rate `9000` для put и
get: [wrk-put-report](wrk/wrk-put-report3), [wrk-get-report](wrk/wrk-get-report3)

## Тесты 4,5 - после исправлений

Помимо исправлений в классе ReplicasManager, поменял некоторый цифры:

* `1 Мб` flushThreshold
* Размер очереди `100`
* Таймаут проксированных запросов `100ms`

Очередь в ThreadPoolExecutor решил заменить на LinkedBlockingQueue вместо LinkedBlockingDeque, c которой работал в
режиме LIFO. С LIFO у нас может быть случай, когда собрали все ответы кроме одного, получаем чуть позже
последний, но из-за занятости воркеров в очередь перед таской по обработке этого ответа могли встать несколько более
новых, и в итоге мы обрабатываем ответ позже и имеем бOльшую задержку.

Кажется, что получили чуть лучшую производительность: сервис справился с rate `14000` для put и `12000` для
get: [wrk-put-report](wrk/wrk-put-report4), [wrk-get-report](wrk/wrk-get-report4)

Rate `10к` для put и get. На [cpu](html/cpu5.html) профилировании картина примерно та же за тем исключением, что на сборщик мусора теперь
приходится `1.5%` времени, что скорее всего и повлияло на результаты. 
При этом при запущенном профилировщике сервис не справился c Rate 10к: [wrk-put-report](wrk/wrk-put-report5), [wrk-get-report](wrk/wrk-get-report5)

### Итого

Есть что исправлять :)

* уменьшить число своих аллокаций для TimestampingDao
* что-то сделать с числом парков












