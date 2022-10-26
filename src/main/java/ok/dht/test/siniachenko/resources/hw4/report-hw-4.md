# wrk тестирование 1 реплики

Вначале решил пострелять в кластер из 1 инстанса (так же, как в прошлой домашке), чтобы потом сравнить результаты
с шардированием. Но и к тому же я начал запускать на другом ноутбуке, поэтому нужно было определить начальную нагрузку.
PUT запросами на 2 минуты сервис выдерживал целых 160000 rps:
```
Running 1m test @ http://localhost:12345
6 threads and 64 connections
Thread calibration: mean lat.: 1.427ms, rate sampling interval: 10ms
Thread calibration: mean lat.: 1.395ms, rate sampling interval: 10ms
Thread calibration: mean lat.: 1.396ms, rate sampling interval: 10ms
Thread calibration: mean lat.: 1.407ms, rate sampling interval: 10ms
Thread calibration: mean lat.: 1.419ms, rate sampling interval: 10ms
Thread calibration: mean lat.: 1.392ms, rate sampling interval: 10ms
Thread Stats   Avg      Stdev     Max   +/- Stdev
Latency     5.48ms   28.72ms 414.72ms   97.52%
Req/Sec    28.13k     4.23k   58.22k    81.77%
9595975 requests in 1.00m, 613.15MB read
Requests/sec: 159936.98
Transfer/sec:     10.22MB
```

GET запросами удалось выжать 140000, тоже вполне неплохой результат:
```
Running 1m test @ http://localhost:12345
  6 threads and 64 connections
  Thread calibration: mean lat.: 874.876ms, rate sampling interval: 3209ms
  Thread calibration: mean lat.: 640.717ms, rate sampling interval: 3559ms
  Thread calibration: mean lat.: 713.206ms, rate sampling interval: 3327ms
  Thread calibration: mean lat.: 577.992ms, rate sampling interval: 2320ms
  Thread calibration: mean lat.: 826.235ms, rate sampling interval: 3893ms
  Thread calibration: mean lat.: 630.966ms, rate sampling interval: 2703ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency    75.79ms  348.35ms   3.27s    95.16%
    Req/Sec    23.88k     1.60k   30.89k    89.47%
  8396482 requests in 1.00m, 598.71MB read
  Non-2xx or 3xx responses: 170934
Requests/sec: 139945.01
Transfer/sec:      9.98MB
```

# профилирование 1 реплики

Профилирование за 10 секунд с rps таким же, как в прошлом пункте. На профиле значительную долю занимает send, а так же
работа базы данных. Малую долю уже занимает преобразование байтов в UTF8 строку и обратно и парсинг параметров from, ack.
И немного GC. Тут всё вполне отлично, почти всё полезная работу, которую сложно как-то улучшить. На профиле аллокаций
видны только аллокации в базе, тут уже всё упирается в базу, ничего не изменить. Видна лишь малая доля сервисных аллокаций.
Блокировки тоже только в базе.
Конечно, намного интереснее потестить кластер.

# wrk тестирование 3 реплик

Тестируем 3 реплики, в скриптах установил параметры from и ack равные 3, чтобы учитывать и ожидание ответов от всех реплик.
PUT запросами выдерживается уже только от 30000 rps:
```
Running 1m test @ http://localhost:12345
  6 threads and 64 connections
  Thread calibration: mean lat.: 7.334ms, rate sampling interval: 44ms
  Thread calibration: mean lat.: 5.991ms, rate sampling interval: 31ms
  Thread calibration: mean lat.: 6.386ms, rate sampling interval: 34ms
  Thread calibration: mean lat.: 6.521ms, rate sampling interval: 37ms
  Thread calibration: mean lat.: 6.498ms, rate sampling interval: 37ms
  Thread calibration: mean lat.: 6.359ms, rate sampling interval: 36ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency   205.25ms  339.90ms   1.75s    86.66%
    Req/Sec     5.03k   751.06     7.20k    74.06%
  1785693 requests in 1.00m, 114.10MB read
Requests/sec:  29761.39
Transfer/sec:      1.90MB
```

до 34000:
```
Running 1m test @ http://localhost:12345
  6 threads and 64 connections
  Thread calibration: mean lat.: 10.011ms, rate sampling interval: 66ms
  Thread calibration: mean lat.: 8.646ms, rate sampling interval: 46ms
  Thread calibration: mean lat.: 7.462ms, rate sampling interval: 42ms
  Thread calibration: mean lat.: 9.002ms, rate sampling interval: 53ms
  Thread calibration: mean lat.: 11.511ms, rate sampling interval: 63ms
  Thread calibration: mean lat.: 9.101ms, rate sampling interval: 53ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency   788.17ms  619.84ms   3.14s    67.89%
    Req/Sec     5.75k   685.64     7.52k    76.03%
  2056254 requests in 1.00m, 131.39MB read
Requests/sec:  34271.75
Transfer/sec:      2.19MB
```
И то, здесь с большим даже средним Latency.
Очень много времени занимает теперь ожидание ответов и агрегация результатов вместе.

GET запросами достигаем 23000 rps:
```
Running 1m test @ http://localhost:12345
  6 threads and 64 connections
  Thread calibration: mean lat.: 3.266ms, rate sampling interval: 15ms
  Thread calibration: mean lat.: 3.318ms, rate sampling interval: 16ms
  Thread calibration: mean lat.: 3.942ms, rate sampling interval: 22ms
  Thread calibration: mean lat.: 3.538ms, rate sampling interval: 17ms
  Thread calibration: mean lat.: 3.844ms, rate sampling interval: 20ms
  Thread calibration: mean lat.: 3.289ms, rate sampling interval: 15ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency    51.00ms  178.48ms   1.52s    92.61%
    Req/Sec     3.96k   453.07     5.91k    78.13%
  1379473 requests in 1.00m, 95.77MB read
  Non-2xx or 3xx responses: 489444
Requests/sec:  22991.23
Transfer/sec:      1.60MB
```

# профилирование 3 реплик

Вcе профили выглядят довольно хорошо. 60% cpu занимает работа базы, что является "основной необходимой" работой.
13,5% send, что тоже необходимая работа.
...Потом я понял, что это был профиль не той реплики, по которой я стрелял из wrk, а другой, потому что у них были
одинаковые Jps имена :) Профили с профилем этой реплики лежат в файлах *...-3-replics-....html*.

Теперь всё-таки интересный профиль мастера. Сразу выделяется область 20% с компилятором. Код мастера, где производится
подсчёт количества пришедших запросов, агрегация разельтата и прочая работа - менее прямолинейный, чем на репликах - видимо,
поэтому такую заметную область компилятор занял. Так и с PUT, и с GET профилями.
А так большую долю снова заняло http взаимодействие с репликами. Там виден 0,5% __read и 5% __writev. Понятно, что у http
заметные накладные расходы. Вероятно, отказавшись от ненужных хедеров, можно было бы уменьшить занимаемое им время.
5,6% Занял ForkJoinThread (треды, создаваемые в sendAsync), в котором только 1,7% __send.
GET и PUT профили особо не отличаются.

Аллокации большая часть тоже за httpClient-ом. В 86,5% ThreadPoolExecutor$Worker есть только 9,45% выполнение запроса сервисом.
Но в нём 7,7% на proxyRequest, который аллоцирует нужные объекты для проксирования запроса реплике. Всего 0,7% отдан базе,
и около процента на объекты для агрегации результатов запросов репликам. А в GET запросах целых 8,2% за базой.

78% локов на http клиенте. Но 20% за levelDB. А в GET запросах больше половины за блокировками в базе.

# Выводы

Видно, что снова очень всё упирается в отправку и обработку запросов через http client.
Теперь запрос может проксироваться не только на одну другую ноду, а на любое количетсво. Но в данном кластере это не сильно влияло,
потому что запросы отправляются более менее параллельно и могут обрабатываться тоже параллельно. А агрегация запросов на
малом кластере съедает крошечную долю ресурсов, всё упирается в сетевое взаимодействие.

Очень хочется переделать хэширование рандеву на консистентное из-за очень неприятного кода, полученного при попытке переделать
алгоритм на возвращение не одной ноды, а нескольких. В рандеву приходится аллоцировать массив размером с количество нод
и сортировать его. На больших кластерах в это можно теоретически упереться. Думал, сделать один массив, и его переиспользовать,
но тогда нельзя будет обращаться из разных процессов к NodeMapper. Пришла в голову идея предаллоцирорвать каждому треду
свой массив... Пока оставил аллокацию массива каждый раз.
