# wrk тестирование

Локальное тестирование асинхронного варианта по сравнению с синхронным довольно удивило. Я попытался дать такую же
нагрузку в 30000, какая и была выявлена в прошлой домашке. Вышло, что GET и PUT нагрузки выдерживались только около
22500 и 21000 rps соответственно:
```
./put.sh 60 30000

Running 1m test @ http://localhost:12345
  6 threads and 64 connections
  Thread calibration: mean lat.: 897.591ms, rate sampling interval: 3205ms
  Thread calibration: mean lat.: 881.533ms, rate sampling interval: 3205ms
  Thread calibration: mean lat.: 851.417ms, rate sampling interval: 3149ms
  Thread calibration: mean lat.: 907.829ms, rate sampling interval: 3237ms
  Thread calibration: mean lat.: 864.236ms, rate sampling interval: 3153ms
  Thread calibration: mean lat.: 908.413ms, rate sampling interval: 3239ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     6.57ms   23.44ms 209.41ms   95.39%
    Req/Sec     3.75k   481.35     4.59k    66.67%
  1351274 requests in 1.00m, 86.34MB read
  Socket errors: connect 0, read 0, write 0, timeout 420
Requests/sec:  22521.34
Transfer/sec:      1.44MB
```

И результаты GET:

```
./get.sh 60 30000
Running 1m test @ http://localhost:12345
  6 threads and 64 connections
  Thread calibration: mean lat.: 254.142ms, rate sampling interval: 1860ms
  Thread calibration: mean lat.: 283.534ms, rate sampling interval: 1910ms
  Thread calibration: mean lat.: 346.901ms, rate sampling interval: 2074ms
  Thread calibration: mean lat.: 246.354ms, rate sampling interval: 1883ms
  Thread calibration: mean lat.: 330.842ms, rate sampling interval: 2001ms
  Thread calibration: mean lat.: 355.821ms, rate sampling interval: 2097ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     1.64ms    3.13ms  85.12ms   97.97%
    Req/Sec     3.47k   570.51     4.50k    72.48%
  1263607 requests in 1.00m, 87.53MB read
  Socket errors: connect 0, read 0, write 0, timeout 500
  Non-2xx or 3xx responses: 482855
Requests/sec:  21060.55
Transfer/sec:      1.46MB
```

Поскольку могла очень сильно влиять текущая загруженность ноутбука, то я зачекаутился на ветку с hw 4 и пустил такие же тесты
на синхронный вариант сервера. Вот результаты:
```
./put.sh 60 30000
Running 1m test @ http://localhost:12345
  6 threads and 64 connections
  Thread calibration: mean lat.: 1731.841ms, rate sampling interval: 5275ms
  Thread calibration: mean lat.: 1744.225ms, rate sampling interval: 6680ms
  Thread calibration: mean lat.: 1764.943ms, rate sampling interval: 5570ms
  Thread calibration: mean lat.: 2220.241ms, rate sampling interval: 6389ms
  Thread calibration: mean lat.: 2458.734ms, rate sampling interval: 6959ms
  Thread calibration: mean lat.: 1793.239ms, rate sampling interval: 7028ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     1.07s     1.75s    9.01s    82.68%
    Req/Sec     5.23k   308.12     5.85k    71.11%
  1799270 requests in 1.00m, 114.97MB read
Requests/sec:  29988.27
Transfer/sec:      1.92MB
```
И
```
Running 1m test @ http://localhost:12345
  6 threads and 64 connections
  Thread calibration: mean lat.: 1159.239ms, rate sampling interval: 4546ms
  Thread calibration: mean lat.: 1350.373ms, rate sampling interval: 4607ms
  Thread calibration: mean lat.: 995.252ms, rate sampling interval: 5021ms
  Thread calibration: mean lat.: 1054.119ms, rate sampling interval: 3100ms
  Thread calibration: mean lat.: 1016.727ms, rate sampling interval: 3579ms
  Thread calibration: mean lat.: 1058.209ms, rate sampling interval: 3719ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency   124.05ms  521.92ms   4.92s    94.39%
    Req/Sec     5.09k   194.78     5.88k    80.28%
  1799054 requests in 1.00m, 124.74MB read
  Non-2xx or 3xx responses: 667165
Requests/sec:  29985.05
Transfer/sec:      2.08MB
```

Синхронный вариант выдержал нагрузку в 30000, но с большей Latency. С одной стороны, это потому что там больше запросов
конкурентно висело, и из-за ограниченных аппаратных ресурсов дольше выполнялось. С другой стороны это в принципе из-за того,
что селектор треды ожидают результата. А в асинхронном варианте явного ожидания результатов нет, есть только ожидания
ответов от реплик, которые происходят в тред пуле httpClient-a.

Ещё в асинхронном варианте небольшая часть запросов затаймаутилась. Иногда в логах проскакивали ошибки, что не удалось
закинуть задачу в тред пулл. Это так потому, что теперь мы потенциально сколько угодно запросов принять,
не ограничиваясь числом селекторов, как в прошлом варианте.

# Профилирование координатора

На cpu профиле увеличилась доля компилятора, и в PUT и в GET тестах, по сравнению с синхронным вариантом.
Теперь это 26% и 8%. Остались те же большие зоны от http клиента. Видно, что теперь работа нашего сервиса и хождение в базу
происходят в нашем тред пуле, а не в селектор тредах. Но занимают довольно небольшую долю.

Аллокации в основном в http клиенте (а в GET запросах снова немалая доля за level DB), но видна доля аллокаций строчек и байтов
в сервисе (для параметров и тел запросов) в сетектор треде. Но больше половины селектор треда заняли handlePut и handleGet,
в которых создаются объекты для репликации запросов и аггрегации результатов. Так же там лямбды и таски.
B PUT это 6.2%, а в GET 9.9%.

Больше полвины блокировок снова за клиентов. Но 22.6% в PUT и 36.25% в GET за базой. Езё малую долю занимают блокировки
на сессии в one nio.

# Мысли

Почему же асинхронный сервер в этом тестировании выдержал меньший rps? Моё предположение, что тут сказывается
условия тестирования - локально запущены 3 реплики, а сетевого взаиможействия в полном понимании нет - мы ходим на
виртуальный localhost интерфейс, и скорость этих запросов относительно мала. А вот накладные расходы на асинхронность
взаимодействия, CompletableFuture, возможное переключение потоков становятся высоки. В докладе Сергея Куксенко были
интересные цифры увеличения затрат на асинхронные выполнения в зависимости, конечно же, от сложности выполняемой работы.
Для 100 наносекунд был оверхед в 8-9 тысяч процентов... Конечно, у нас задачи дольше, но оверхед всё равно чувствуется.
Уверен, в случае тестирования на разных машинах картина была бы позитивнее.
