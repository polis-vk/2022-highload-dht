# Stage 3

В этом задание мы шардировали наше приложение на несколько нод. Запуски нод производились на разных JVM.
Была выбрана ведущая нода(одна из трех), которая распределяла запросы.

Нижи приведены результаты работы wrk на разных запросах(нормальная/избыточная нагрузка).

put(too much)
```alex
Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     5.54s     2.68s   13.65s    66.45%
    Req/Sec   359.58     69.99   461.00     77.13%
  1407969 requests in 1.00m, 84.19MB read
Requests/sec:  23471.68
Transfer/sec:      1.40MB
```

put
```alex
Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     1.47ms    3.44ms  86.14ms   98.75%
    Req/Sec   164.52     53.53   800.00     56.02%
  599963 requests in 1.00m, 35.88MB read
Requests/sec:  10001.72
Transfer/sec:    612.50KB
```

get(too much)

```alex
Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency    14.24s     6.75s   27.03s    59.05%
    Req/Sec   400.68     46.06   494.00     72.07%
  4619549 requests in 3.00m, 303.70MB read
Requests/sec:  25667.76
Transfer/sec:      1.69MB
```

get
```alex
Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     1.27ms    1.61ms  67.65ms   98.93%
    Req/Sec   164.30     52.29     0.89k    40.98%
  1799992 requests in 3.00m, 118.36MB read
Requests/sec:  10000.58
Transfer/sec:    673.38KB
```

[Здесь можно найти графики и html](./flames/three).

Показатели несколько сбитые, тк мы запускаем все на одной машине и ведем работу через лидера(далее раскрою этот момент).

Для распределения по нодам было использовано консистентное хеширование. Для избежания коллизий при не сильно различных данных было использовано `Hash.murmur3`.
Количество промежутков(виртуальных нод) на каждую ноду для тестов было выбрано в 5, хотя на практике лучше себя показало большее(почемуто на 10 тесты не всегда проходились полностью, понять причину не успел).

HTML с CPU не прикреплял, тк были очень тяжелыми.
Исходя из этих графиков у нас сильно выросло время взаимодействия нод друг с другом.
Клиент на ноде плохо сказался на производительности.


# Bывод

* Консистентное хэширование хорошо себя показало, в частности разгрузило работу на бд, при этом хорошо улучшило работу с гет запросом.
* Очеень важна хеш функция, тк без оптимального выбора мы подаем на коллизиях и не равномерно грузим наши ноды
* Клиент на сервере очень плохо сказался на производительности. Скорее всего надо уходить от http клиента, переходить на более легковесные протоколы. Нужно настроить отдельный апи для работы нод друг с другом. Плюс скорее всего следует отделить работу клиента и сервера в различных воркерах, а не смешанных, тк ожидание пересылки сообщение , забивает их.


