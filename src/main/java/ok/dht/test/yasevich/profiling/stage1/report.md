К сожалению к моменту дедлайна на M1 не получилось привести программу wrk2 в рабочее состояние, 
поэтому для тестирования использовалась программа wrk с характерным для неё способом измерения задержек

wrk запустим следующими командами:

`wrk -c 1 -t 1 -d 2m -s get.lua --latency http://localhost:19234 > wrk-get-report`   
`wrk -c 1 -t 1 -d 2m -s put.lua --latency http://localhost:19234 > wrk-put-report`
* 1 соединение
* 1 поток
* 2 минуты
* Имеем lua скрипты [GET](../scripts/get.lua) и [PUT](../scripts/put.lua), генерирующие соответсвующее запросы со случайными ключами и значениями вида key n value m 

Профилировщик запустим в двух режимах: cpu и alloc

`./profiler.sh -f put.html -e cpu,alloc --chunktime 1s start Server`

`./profiler.sh -f get.html -e cpu,alloc --chunktime 1s start Server`

## СPU
Прогрев сервер программой wrk за 30 сек get запросов, запускаем профилирование put запросов, потом сразу же запускаем профилирование get
### Тест 1
* В БД изначально 130 файлов суммарным размером 1,3 Гб (с записями вплоть до key 50кк, value 50kk)
* `4 Мб` flushThreshold

[wrk-put-report](reports/wrk/wrk-put-report), [put.html](reports/html/put.html)

Получили:
* В среднем `45.93k` запросов в секунду с задержкой `29.38us`
* Примерно поровну делятся ресурсы cpu на работу с http сессией и на работу с MemorySegmentDao (memory.put())

[wrk-get-report](reports/wrk/wrk-get-report), [get.html](html/get.html)

* Get запросов получилось всего `10.46k` в секунду со средней задержкой `98.33us` 
* Имеем 235 сэплов по работе с Memory (CurrentSkipListMap) и 10770 по работе со Storage (2% vs 96.5% относительно метода dao.get())
* Можно заметить, что 65% от storage.get() уходят на бинарный поиск индекса интересующего нас entry, и только 18.5% уходит непосредственно на чтение по индексу  
Что объяснимо, так как нам требуется log(n) произвольных доступов на поиск (где n - число записей), и потом одним читаем
### Тест 2
Уменьшим flushThreshold до `512 кб`

Можно заметить, что количество put запросов осталось таким же, количество get запросов сильно упало, а задержка для get сильно выросла, 
что объяснимо, так как put запросы происходят в memory, а для get запросов мы имеем, что на диске стало в несколько раз больше файлов, а данных в памяти храним меньше

[wrk-put-report](wrk/wrk-put-report2), [put.html](reports/html/put2.html)
* В среднем `47.91k` запросов в секунду с задержкой `18.92us`
* В данном случае 339 сэплов (6.91% of all) заняли автоматические флаши, при этом производительность осталась прежней

[wrk-get-report](reports/wrk/wrk-get-report2), [get.html](reports/html/get2.html)

* Get запросов получилось всего `1.72к` в секунду со средней задержкой `584.84us` (было 10.46k и 98.33us)
* Можно заметить, что для get запросов уменьшилось количество сэпмлов по работе с ConcurrentSkipListMap(с 235 до 50)
* 99.41% времени от dao.get() уходит на storage.get() (Было 96.53%)
### Тест 3
[wrk-put-report](wrk/wrk-put-report2), [put.html](reports/html/put2.html)
* Уменьшив flushThreshold до `128 кб`, можно заметить снижение производительности put запросов
* Получили, что для этого случая столь частые автоматические флаши с соответствующими взятиями write лока (с целью изменить state) сказались на производительности
* cpu профилирование показывает, что GC работал активнее прежнего, запустив полную сборку мусора (часто менялся state и в памяти стало много ConcurrentSkipList мап без ссылок на них)
### Итого
Увидели на практике, что наш storage имеет бОльшую производительность на put запросы и при этом flushThreshold на них не так сильно влияет, как на get запросы
## Аллокации
[get-alloc.html](reports/html/get-alloc.html)

Для get запросов 96.5% всех аллокаций приходится на MemorySegmentImpl

[put-alloc.html](reports/html/put-alloc.html)

* 28.65% от handlePost() и 7.47% от всех методов приходится на метод fromString(), который преобразует строки в MemorySegment.
В пределах этого метода примерно по ровну приходится на аллокации MemorySegmentImpl и массива char[], и последнего хотелось бы избежать
* Оставшиеся аллокации строк и массивов байт приходятся на работу one.nio


