# Изменения 

Добавил сервису `ExecutorService` с фиксированным числом потоком, равным `Runtime.getRuntime().availableProcessors()`. 
На моём устройстве это было число 12 (Как я понимаю, 6 физических ядер, 12 виртуальных).
Вначале решил не менять число selector-ов у сервера, оставить 1, и потестировать в той же конфигурации.
Потом уже проставил число селекторов равным тому же числу.

# wrk тестирование с 1 селектором.

Изменил скрипты, запускающие wrk, добавил `-t 6 -c 64` (в 6 потоков и 64 соединения). Вначале ставил PUT-ам и GET-ам
такие же rate, до которого дошел в первой домашке. Вывод `put.sh 120 20000`:
```
Running 2m test @ http://localhost:12345
  6 threads and 64 connections
  Thread calibration: mean lat.: 1.080ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.208ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.278ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.226ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.206ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.229ms, rate sampling interval: 10ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     1.46ms    4.37ms 214.66ms   98.56%
    Req/Sec     3.52k   422.71    22.70k    93.55%
  2399548 requests in 2.00m, 153.32MB read
Requests/sec:  19996.22
Transfer/sec:      1.28MB
```

Сервер вполне ожидаемо выдержал. Latency примерно такое же, как и с однопоточным сервером. Пробуем увеличить до
`put.sh 120 40000`:
```
Running 2m test @ http://localhost:12345
  6 threads and 64 connections
  Thread calibration: mean lat.: 1.369ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.365ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.370ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.370ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.381ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.367ms, rate sampling interval: 10ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     1.51ms    1.92ms  52.93ms   98.29%
    Req/Sec     7.03k   606.28    12.44k    76.65%
  4799031 requests in 2.00m, 306.64MB read
Requests/sec:  39992.16
Transfer/sec:      2.56MB
```

Снова сервер всё выдержал, Latency не увеличилось. Снова пробуем прибавить 20000 к rpc, `put.sh 120 60000`:
```
Running 2m test @ http://localhost:12345
  6 threads and 64 connections
  Thread calibration: mean lat.: 489.818ms, rate sampling interval: 1960ms
  Thread calibration: mean lat.: 547.366ms, rate sampling interval: 2109ms
  Thread calibration: mean lat.: 471.310ms, rate sampling interval: 1860ms
  Thread calibration: mean lat.: 451.931ms, rate sampling interval: 1787ms
  Thread calibration: mean lat.: 573.448ms, rate sampling interval: 2224ms
  Thread calibration: mean lat.: 523.504ms, rate sampling interval: 2064ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     8.57s     4.59s   22.51s    63.31%
    Req/Sec     8.75k   547.41     9.89k    70.91%
  6313440 requests in 2.00m, 403.40MB read
Requests/sec:  52612.52
Transfer/sec:      3.36MB
```

Сервер начал захлёбываться. 8000 запросов не успевало обработаться, Latency поднялось в 8000 раз... В целом неплохо,
в 2,5 раза увеличилась "производительность".



Попробуем с GET-ами. Вначале я случайно запустил wrk снова в 1 потоке с 1 коннекцией, `get.sh 120 15000`:

```
Running 2m test @ http://localhost:12345
  1 threads and 1 connections
  Thread calibration: mean lat.: 1352.400ms, rate sampling interval: 4661ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency    12.12s     5.71s   21.23s    56.50%
    Req/Sec    12.43k   408.11    12.98k    69.57%
  1481384 requests in 2.00m, 104.37MB read
  Non-2xx or 3xx responses: 254511
Requests/sec:  12344.89
Transfer/sec:      0.87MB
```

Интересно, что производительность упала, по сравнению с однопоточным сервером. Всё-таки есть накладные расходы от
использования экзекьютора.

Дальше уже поднял в скрипте треды до 6, соединения до 64. Вывод `get.sh 120 15000`:
```
Running 2m test @ http://localhost:12345
  6 threads and 64 connections
  Thread calibration: mean lat.: 1.075ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.060ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.101ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 0.990ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.002ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.016ms, rate sampling interval: 10ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     1.05ms  497.09us  14.35ms   65.53%
    Req/Sec     2.63k   175.29     4.40k    73.18%
  1799671 requests in 2.00m, 126.79MB read
  Non-2xx or 3xx responses: 310632
Requests/sec:  14997.28
Transfer/sec:      1.06MB
```

Пока история, как с PUT-ами. Теперь `get.sh 120 30000`:
```
Running 2m test @ http://localhost:12345
  6 threads and 64 connections
  Thread calibration: mean lat.: 1.280ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.190ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.281ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.255ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.166ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.171ms, rate sampling interval: 10ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     1.46ms    2.95ms 140.80ms   99.01%
    Req/Sec     5.27k   429.83    10.89k    81.80%
  3594409 requests in 2.00m, 253.23MB read
  Non-2xx or 3xx responses: 619023
Requests/sec:  29953.42
Transfer/sec:      2.11MB
```

То же самое. И наконец `get.sh 120 60000`:
```
Running 2m test @ http://localhost:12345
  6 threads and 64 connections
  Thread calibration: mean lat.: 935.858ms, rate sampling interval: 3813ms
  Thread calibration: mean lat.: 856.142ms, rate sampling interval: 3473ms
  Thread calibration: mean lat.: 998.509ms, rate sampling interval: 4005ms
  Thread calibration: mean lat.: 905.109ms, rate sampling interval: 3588ms
  Thread calibration: mean lat.: 945.861ms, rate sampling interval: 4046ms
  Thread calibration: mean lat.: 881.965ms, rate sampling interval: 3614ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency    15.22s     8.01s   36.08s    60.22%
    Req/Sec     7.57k   209.36     8.02k    68.79%
  5462639 requests in 2.00m, 384.87MB read
  Non-2xx or 3xx responses: 938621
Requests/sec:  45522.36
Transfer/sec:      3.21MB
```

И вот тут сервер захлебнулся. Latency выросло в 15000 раз, 15000 запросов в секунду не успевали обрабатываться. В целом,
то же самое, что и с PUT запросами, только порог чуть раньше, как и с однопоточным сервисом (там тоже у GET меньше порог
был). Думаю, объяснение здесь такое же, GET запросы более накладные из-за записи непустого ответа. 

# Профили cpu, alloc и lock для сервера с 1 селектором

html и jfr файлы для всех тестов слишком много весили, поэтому я решил запустить отдельно wrk на 10 секунд для 2х видов.
Профили лежат в файлах tycoon-server-1-selector-alloc.html, tycoon-server-1-selector-cpu.html
и tycoon-server-1-selector-lock.html.

## cpu

Сразу выделяется, что весь профиль +- равномерно красный, постоянно работают потоки. Это оттого, что потоки часто
вызывают метод ожидания. Как и с однопоточными тестами в 1й домашке, видны большие красные полоски с `write`, `read` и
`kevent`. Но они меньше, и появились красные `__psynch_cvwait` и маленькие другие. Это, в целом, неудивительно,
теперь у нас много блокировок из-за тред пул экзекьютора и из-за lock-ов в базе.

## alloc

Вновь на PUT намного меньше аллокаций. Преимущественно, аллокации все те же, что и в 1 домашке - преобразование строк
в байты и наоборот, new Response[] в сервисе, Slice в базе, Object[] в ArrayList-е в базе. В GET запросах всё то же и
красный byte[] в конструкторе Slice в базе. Интересно, что в PUT-ах интенсивность аллокаций возрастает к концу теста,
а в GET-ах, наоборот, вначале нагрузка больше, а под конец уменьшается. Предположу, что в случае с PUT база постепенно
увеличивается, и новые рандомные значения нужно запихивать глубже в структуру дерева, больше делать Slice-ов и т.д.
А с GET мог бы сделать наивное предположение про какие-нибудь кэши, которые создаются при взятии кусков с диска,
например, но все запросы идут случайно, непоследовательно, к тому же я не знаю внутреннее устройство базы, так что нет.

## lock

В основном блокировки были на ReentrantLock#NonsairSync, потому что writeInternal в базе полностью
блокируется mutex-ом... И часть поменьше занята блокировками в тред пуле сервиса. C GET запросами то же самое. Тут явно
хотелось бы подумать в сторону уменьшения характера блокировок, не лочить всю структуру, а сделать какие-то нибудь локи
на части дерева.

# wrk тестирование с несколькими селектором.

Дальше решил попробовать поднять количество selector потоков до такого же числа, как и размер рабочего пула. Попробовал
вначале такой же рейт, на котором остановились с сервером с одним селектором. Вывод `put.sh 120 53000`:
```
Running 2m test @ http://localhost:12345
  6 threads and 64 connections
  Thread calibration: mean lat.: 6.073ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 5.489ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 6.551ms, rate sampling interval: 11ms
  Thread calibration: mean lat.: 5.849ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 5.661ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 5.545ms, rate sampling interval: 10ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     2.77s     1.89s    7.03s    57.10%
    Req/Sec     9.05k     1.57k   22.11k    74.85%
  6175351 requests in 2.00m, 394.58MB read
Requests/sec:  51461.61
Transfer/sec:      3.29MB
```

Кажется, что картина, к сожалению, не изменилась по сравнению с предыдущим тестом. Latency уже целых 2 секунды, что даёт
понять, что дальше точно не стоит повышать рейт. Пробуем GET запросы, `get.sh 120 46000`:
```
Running 2m test @ http://localhost:12345
  6 threads and 64 connections
  Thread calibration: mean lat.: 1.695ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.686ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.792ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.785ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.635ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.623ms, rate sampling interval: 10ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency    41.29ms  152.23ms   1.17s    92.86%
    Req/Sec     8.10k     0.89k   12.89k    82.69%
  5518847 requests in 2.00m, 388.63MB read
  Non-2xx or 3xx responses: 983287
Requests/sec:  45990.78
Transfer/sec:      3.24MB
```

Тут явно ситуация улучшилась, Latency 41 ms, можно повышать. `get.sh 120 50000`:
```
Running 2m test @ http://localhost:12345
  6 threads and 64 connections
  Thread calibration: mean lat.: 1.351ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.339ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.347ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.348ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.349ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.349ms, rate sampling interval: 10ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency    63.08ms  204.85ms   1.26s    91.20%
    Req/Sec     8.81k     0.97k   16.10k    83.01%
  5998808 requests in 2.00m, 422.43MB read
  Non-2xx or 3xx responses: 1068841
Requests/sec:  49990.50
Transfer/sec:      3.52MB
```

Сервер выдерживает! Тут я подумал, что get запросы лучше работают с пулом селекторов, потому что в них происходит запись
данных в буферы сокетов, а в PUT пустой ответ. Но зато в PUT запросах есть в реквесте точно такие же данные, которые
надо читать селеткорам, и не очевидно, почему GET-ы лучше распараллелились. Дальше на `get.sh 120 60000` сервер уже
захлебнулся:
```
Running 2m test @ http://localhost:12345
  6 threads and 64 connections
  Thread calibration: mean lat.: 72.254ms, rate sampling interval: 447ms
  Thread calibration: mean lat.: 55.651ms, rate sampling interval: 357ms
  Thread calibration: mean lat.: 68.499ms, rate sampling interval: 435ms
  Thread calibration: mean lat.: 67.895ms, rate sampling interval: 433ms
  Thread calibration: mean lat.: 57.486ms, rate sampling interval: 371ms
  Thread calibration: mean lat.: 54.475ms, rate sampling interval: 365ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     4.79s     2.73s   12.57s    66.38%
    Req/Sec     9.14k   418.78     9.82k    75.76%
  6598722 requests in 2.00m, 464.67MB read
  Non-2xx or 3xx responses: 1175816
Requests/sec:  54989.91
Transfer/sec:      3.87MB
```

Возникло предположение, что ноутбук или java машина могли "прогреться", и я решил снова попробовать запустить put.
`put.sh 120 55000`:
```
Running 2m test @ http://localhost:12345
  6 threads and 64 connections
  Thread calibration: mean lat.: 1.708ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.727ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.700ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.717ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.694ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.702ms, rate sampling interval: 10ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency    10.93ms   24.12ms 190.08ms   89.24%
    Req/Sec     9.67k     1.14k   14.11k    73.22%
  6598653 requests in 2.00m, 421.63MB read
Requests/sec:  54989.25
Transfer/sec:      3.51MB
```

И правда, эти запросы тоже стали вывозить rate 55000. Далее повышение до `put.sh 120 60000` тоже вывезлось:
```
Running 2m test @ http://localhost:12345
  6 threads and 64 connections
  Thread calibration: mean lat.: 1.710ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.745ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.773ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.704ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.736ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.870ms, rate sampling interval: 10ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency   955.92ms  855.58ms   2.35s    39.99%
    Req/Sec    10.45k     1.47k   14.33k    80.89%
  7108666 requests in 2.00m, 454.22MB read
Requests/sec:  59239.61
Transfer/sec:      3.79MB
```

Но тут уже Latency выросло до почти секунды и было ясно, что это предел. Вполне ожидаемо дальше сервер
уже не вывез `put.sh 120 65000`:
```
Running 2m test @ http://localhost:12345
  6 threads and 64 connections
  Thread calibration: mean lat.: 11.363ms, rate sampling interval: 77ms
  Thread calibration: mean lat.: 11.085ms, rate sampling interval: 76ms
  Thread calibration: mean lat.: 11.128ms, rate sampling interval: 76ms
  Thread calibration: mean lat.: 10.823ms, rate sampling interval: 75ms
  Thread calibration: mean lat.: 11.863ms, rate sampling interval: 80ms
  Thread calibration: mean lat.: 10.552ms, rate sampling interval: 71ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     4.44s     2.88s   10.36s    56.41%
    Req/Sec     9.97k     1.02k   11.44k    88.17%
  7180917 requests in 2.00m, 458.83MB read
Requests/sec:  59841.46
Transfer/sec:      3.82MB
```

Из-за этой особенности сложнее сказать, как лучше, с пулом селекторов, или с одним. Но можно порассуждать, что в случае
записи и чтения больших данных, вероятно, лучше иметь пул селекторов.

# Профили cpu, alloc и lock для сервера с 1 селектором

Тут снова отдельно запустил нагрузку на меньшее время, чтобы html файлы не были огромного размера.

Профили лежат в файлах tycoon-server-many-selectors-alloc.html, tycoon-server-many-selectors-cpu.html
и tycoon-server-many-selectors-lock.html.

Профили выглядят так же, как и в случае с 1 селектором, поэтому применимы те же рассуждения.
