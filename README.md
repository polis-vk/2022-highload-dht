# 2022-highload-dht
Курсовой проект 2022 года [курса "Проектирование высоконагруженных систем"](https://polis.vk.company/curriculum/program/discipline/1444/) [VK Образования](https://polis.vk.company/).

## Этап 1. HTTP + storage (deadline 2022-09-28 23:59:59 MSK)
### Fork
[Форкните проект](https://help.github.com/articles/fork-a-repo/), склонируйте и добавьте `upstream`:
```
$ git clone git@github.com:<username>/2022-highload-dht.git
Cloning into '2022-highload-dht'...
...
$ git remote add upstream git@github.com:polis-vk/2022-highload-dht.git
$ git fetch upstream
From github.com:polis-vk/2022-highload-dht
 * [new branch]      master     -> upstream/master
```

### Make
Так можно запустить тесты:
```
$ ./gradlew test
```

А вот так -- сервер:
```
$ ./gradlew run
```

### Develop
Откройте в IDE -- [IntelliJ IDEA Community Edition](https://www.jetbrains.com/idea/) нам будет достаточно.

**ВНИМАНИЕ!** При запуске тестов или сервера в IDE необходимо передавать Java опцию `-Xmx128m`.

В своём Java package `ok.dht.test.<username>` реализуйте интерфейсы [`Service`](src/main/java/ok/dht/Service.java) и [`ServiceFactory.Factory`](src/main/java/ok/dht/test/ServiceFactory.java) и поддержите следующий HTTP REST API протокол:
* HTTP `GET /v0/entity?id=<ID>` -- получить данные по ключу `<ID>`. Возвращает `200 OK` и данные или `404 Not Found`.
* HTTP `PUT /v0/entity?id=<ID>` -- создать/перезаписать (upsert) данные по ключу `<ID>`. Возвращает `201 Created`.
* HTTP `DELETE /v0/entity?id=<ID>` -- удалить данные по ключу `<ID>`. Возвращает `202 Accepted`.

Используем свою реализацию `DAO` из весеннего курса `2022-nosql-lsm`, либо берём референсную реализацию, если курс БД не был завершён.

Проведите нагрузочное тестирование с помощью [wrk2](https://github.com/giltene/wrk2) в **одно соединение**:
* `PUT` запросами на **стабильной** нагрузке (`wrk2` должен обеспечивать заданный с помощью `-R` rate запросов)
* `GET` запросами на **стабильной** нагрузке по **наполненной** БД

Почему не `curl`/F5, можно узнать [здесь](http://highscalability.com/blog/2015/10/5/your-load-generator-is-probably-lying-to-you-take-the-red-pi.html) и [здесь](https://www.youtube.com/watch?v=lJ8ydIuPFeU).

Приложите полученный консольный вывод `wrk2` для обоих видов нагрузки.

Отпрофилируйте приложение (CPU и alloc) под `PUT` и `GET` нагрузкой с помощью [async-profiler](https://github.com/Artyomcool/async-profiler).
Приложите SVG-файлы FlameGraph `cpu`/`alloc` для `PUT`/`GET` нагрузки.

**Объясните** результаты нагрузочного тестирования и профилирования и приложите **текстовый отчёт** (в Markdown).

Продолжайте запускать тесты и исправлять ошибки, не забывая [подтягивать новые тесты и фиксы из `upstream`](https://help.github.com/articles/syncing-a-fork/).
Если заметите ошибку в `upstream`, заводите баг и присылайте pull request ;)

### Report
Когда всё будет готово, присылайте pull request со своей реализацией и оптимизациями на review.
Не забывайте **отвечать на комментарии в PR** (в том числе автоматизированные) и **исправлять замечания**!

## Этап 2. Асинхронный сервер (deadline 2022-10-05 23:59:59 MSK)

Вынесите **обработку** запросов в отдельный `ExecutorService` с ограниченной очередью, чтобы разгрузить `SelectorThread`ы HTTP сервера.

Проведите нагрузочное тестирование с помощью [wrk2](https://github.com/giltene/wrk2) с **большим количеством соединений** (не меньше 64) `PUT` и `GET` запросами.

Отпрофилируйте приложение (CPU, alloc и lock) под `PUT` и `GET` нагрузкой с помощью [async-profiler](https://github.com/Artyomcool/async-profiler).

### Report
Когда всё будет готово, присылайте pull request с изменениями, результатами нагрузочного тестирования и профилирования, а также анализом результатов **по сравнению с предыдущей** (синхронной) версией.
