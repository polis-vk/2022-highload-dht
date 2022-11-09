Сервер, wrk и async-profiler запускались через wsl
------------------------------------------------------------------------------------------------------------------------
**Параметр flushThresholdBytes в dao равен 1 МБ.
Размер очереди в Executor для обработки запросов равен 100.
Алгоритмов распределения данных между узлами - consistent hashing (virtual nodes number = 10).
Число узлов в кластере = 5**

Общие параметры для нагрузочного тестирования:
    - Длительность 60 секунд;
    - Число потоков 64;
    - Число соединений 64;

Стоит учитывать, что предыдущая реализация была частично асинхронной: ожидание проксируемых запросов происходило 
с помощью вызова get() после sendAsync в отдельном executor, с количеством потоков большим чем в основном executor.
Схема работы с executor-ми во всей цепочке CompletableFuture следующая: после httpClient.sendAsync делается
thenApplyAsync с proxyPool, где proxyPool - ForkJoinPool с параметром parallelism равным 8. Таким образом вся цепочка
после sendAsync будет выполняться в proxyPool (я убедился в этом определив фабрику в нем, то есть именовал треды и 
сделал лог в методе handle из ReplicaResponsesHandler, который является завершающим, в логе показывается имя треда и
эти имена были именами тредов из proxyPool). Только если метод proceed из ProxyHandler успеет выполниться быстрее чем 
сконструируется цепочка в методе handle из ReplicaResponsesHandler, то handle будет выполняться в RequestExecutor, но
на практике такой ситуации не было, но даже если такое произойдет, то это не страшно, так как в handle происходит все 
быстро + он тогда будет выполняться в RequestExecutor, а не потоке SelectorThread-а. Поэтому отложенный старт делать
особого смысла нет, так как описанная ситуация очень маловероятна и даже если она произойдет, то почти ни на что 
не повлияет. Таким образом, решение с thenApplyAsync с proxyPool после sendAsync является оптимальным так как 
смена контекста происходит только 1 раз, меньше невозможно, так как HttpClient в любом случае делегирует всю цепочку
вызовов после sendAsync в commonPool, но это плохо, ведь commonPool может использовать кто угодно. Менять контекст более
1 раза не имеет смысла, потому что методы, идущие после того как уже получен ответ от проксируемого узла выполняются 
быстро и менять executor для них нецелесообразно и оптимальнее их выполнять в том же proxyPool.
Для начала определим максимальный выдерживаемый rate при текущей реализации. Запустим сервис на 5 узлах
с from=5 и ack=5. Для GET запросов не будем сильно заполнять базу данных, чтобы принимаемый сервис больше времени 
обрабатывал запросы к репликам, именно которых и касается блок кода с асинхронностью и результаты были именно для 
нового асинхронного кода более "чистыми". Да, при заполненности базы ожидания ответов от реплик будут дольше,
но судя по профилям они и так занимают более 60-70% времени, чего вполне достаточно. При PUT запросах максимально
возможный rate составил 1800 request/sec, при GET запросах 1553 request/sec. Как мы видим максимально возможный rate
поднялся с 1150 и 882 request/sec по сравнению с предыдущей версией сервиса, благодаря выполнению асинхронной работы
через CompletableFuture, что сделало ожидание ответов по сети, которые занимают весомую часть времени работы сервиса 
более оптимальным. При from и ack равными 1 максимально выдерживаемый rate составил 4710 и 4300 request/sec 
соответственно, что сравнимо на уровне погрешности с предыдущей версией.
Теперь измерим временные показатели для текущей версии сервиса для PUT запросов при rate 1000 и GET запросов 
при rate 500 и 300_000 сохраненными записями в трех случаях:
1) from=1, ack=1;
При PUT запросах Avg time =  2.09ms, Max time = 14.76ms, 99.000% = 5.11ms, 99.900% = 9.08ms
При GET запросах Avg time =  2.24ms, Max time = 53.41ms, 99.000% = 4.30ms, 99.900% = 29.68ms
2) from=3, ack=2;
При PUT запросах Avg time = 3.57ms, Max time = 31.02ms, 99.000% = 11.73ms, 99.900% = 17.73ms
При GET запросах Avg time = 3.08ms, Max time =  23.73ms, 99.000% = 7.55ms, 99.900% = 22.91ms
3) from=5, ack=3;
При PUT запросах Avg time = 8.72ms, Max time = 86.21ms, 99.000% = 24.75ms, 99.900% = 76.74ms
При GET запросах Avg time = 6.39ms, Max time = 49.28ms, 99.000% = 18.8ms, 99.900% = 36.31ms
 
(Показатели для GET запросов могут быть лучше, чем для PUT, так как rate при них в 2 раза ниже).
Как видно из результатов, при from=1, ack=1 отличий во временных показателях c предыдущей версией практически нет.
При from=3, ack=2 среднее и максимальное время подросли примерно в 1.5 раза, но время по процентилям 99.000% и 99.900%
отличается от предыдущей версии на уровне погрешности измерений. Зато при from=5, ack=3 показатели уже лучше 
приблизительно в 1.5 раза, что говорит о том, что при небольшом количестве реплик текущая асинхронная реализация
работает на том же уровне, но с увеличением числа реплик дает выигрыш в производительности. В первую очередь это 
связано с тем, что метод вызов метода get() для ожидания запросов был заменен на thenAccept, что также позволило 
переиспользовать потоки ForkJoinPool-а CompletableFuture для всей логики связанной с отправкой/получением ответов
от реплик. Также было рассмотрено два варианта обработки запросов к dao, с помощью supplyAsync и без него, просто 
возвращая Response, supplyAsync не дал выигрыша в производительности, так как данный метод все равно выполняется в 
requestExecutor, и еще раз выносить его асинхронно смысла нет, поэтому было принято решение не делать CompletableFuture
для proceed() из dao, а потом просто обернуть его через CompletableFuture.completedFuture() для общего использование 
с логикой проксируемых запросов.

**Результаты профилирования.**
Будем профилировать случай когда from=3, ack=2.

CPU:
    PUT запросы:
      24.15% - работа jdk.internal.net.http.Http1AsyncReceiver, который ставит в очередь входящие данные в HttpClient.
      19.86% - проверка на то, следует ли запрашивать дополнительные данные из Http1TubeSubscriber
        (jdk.internal.net.http.SocketTube$InternalWriteSubscriber.requestMore)
      15.25% - отправка ответов на проксируемые запросы от других узлов jdk.internal.net.http.Exchange.responseAsync
      3.24% - метод proceed из ProxyHandler, из них 1.53% - отправка проксируемого запроса,
        остальное - формирование запроса для HttpClient и итогового ответа Response.
      0.26% - upsert в dao
      0.21% - Высчитывание позиций реплик и формирование списка CompletableFuture для запросов/ответов них
        (ok.dht.test.lutsenko.service.DemoService.createReplicaResponsesFutures)
      0.21% - Инициализация обработки ответов от каждого CompletableFuture от реплик
        (ok.dht.test.lutsenko.service.DemoService$1.lambda$handleRequest)
      3.07% - java.util.concurrent.ThreadPoolExecutor.getTask, синхронизация взятия задач из очереди в executor
      5.28% - java.util.concurrent.ForkJoinPool.awaitWork
      2.21% - запись в сокет (one.nio.net.NativeSocket.write)
      1.36% - java.util.concurrent.CompletableFuture$UniApply.tryFire в proceed() из proxyHandler после sendAsync
      7.75% - jdk.internal.net.http.HttpClientImpl$SelectorManager.handleEvent в SelectorManager HttpClient
      1.36% - чтение из сокета (one.nio.net.NativeSocket.read)
      3.5% -  работа а SelectorManager.run в HttpClient
      0.6% - one.nio.net.NativeSelector.epollWait
      Остальное - запуск потоков в Executor-ах и JIT
    GET запросы:
      18.95% - работа jdk.internal.net.http.Http1AsyncReceiver, который ставит в очередь входящие данные в HttpClient.
      7.48% - проверка на то, следует ли запрашивать дополнительные данные из Http1TubeSubscriber
        (jdk.internal.net.http.SocketTube$InternalWriteSubscriber.requestMore)
      12.1% - отправка ответов на проксируемые запросы от других узлов jdk.internal.net.http.Exchange.responseAsync
      4.97% - метод proceed из ProxyHandler, из них 2% - отправка проксируемого запроса, остальное - формирование
        запроса для HttpClient и итогового ответа Response.
      8% - get в dao
      0.31% - Высчитывание позиций реплик и формирование списка CompletableFuture для запросов/ответов них
        (ok.dht.test.lutsenko.service.DemoService.createReplicaResponsesFutures)
      0.25% - Инициализация обработки ответов от каждого CompletableFuture от реплик
        (ok.dht.test.lutsenko.service.DemoService$1.lambda$handleRequest$)
      17% - java.util.concurrent.ThreadPoolExecutor.getTask, синхронизация взятия задач из очереди в executor
      4.26% - java.util.concurrent.ForkJoinPool.awaitWork
      2.31% - запись в сокет (one.nio.net.NativeSocket.write)
      0.97% - java.util.concurrent.CompletableFuture$UniApply.tryFire в proceed() из proxyHandler после sendAsync
      7.19% - jdk.internal.net.http.HttpClientImpl$SelectorManager.handleEvent в SelectorManager HttpClient
      0.4% - чтение из сокета (one.nio.net.NativeSocket.read)
      4.5% -  работа а SelectorManager.run в HttpClient
      1.94% - one.nio.net.NativeSelector.epollWait
    Остальное - запуск потоков в Executor-ах и JIT
Alloc:
    PUT запросы:
      33.4% - аллокации в jdk.internal.net.http.MultiExchange
      11.54% - аллокации при jdk.internal.net.http.Http1Response$Receiver.accept
      5.96% - аллокации внутри jdk.internal.net.http.HttpClientImpl.sendAsync
      3% - аллокации в jdk.internal.net.http.HttpRequestBuilder для создания запроса через HttpClient
      3% - создание и инициализация CompletableFuture для запросов к репликам 
      14% - перевод HttpResponse в one.nio Response (ok.dht.test.lutsenko.service.ServiceUtils.toResponse)
      3.76% - создание итогового Response
      2% - jdk.internal.net.http.SocketTube$InternalReadPublisher$InternalReadSubscription.read
      15.58% - one.nio.http.HttpSession.processRead
      Остальное - аллокации в Executor-ах и SelectorManager
    GET запросы:
      29.11% - аллокации в jdk.internal.net.http.MultiExchange
      12.21% - аллокации при jdk.internal.net.http.Http1Response$Receiver.accept
      1.29% - аллокации внутри jdk.internal.net.http.HttpClientImpl.sendAsync
      11% -  get в dao
      0.6% - аллокации в jdk.internal.net.http.HttpRequestBuilder для создания запроса через HttpClient
      0.35% - создание и инициализация CompletableFuture для запросов к репликам
      13.55% - перевод HttpResponse в one.nio Response (ok.dht.test.lutsenko.service.ServiceUtils.toResponse)
      4.75% - создание итогового Response
      1% - jdk.internal.net.http.SocketTube$InternalReadPublisher$InternalReadSubscription.read
      16.22% - one.nio.http.HttpSession.processRead
      Остальное - аллокации в Executor-ах и SelectorManager
Lock:
    PUT запросы:
      55.6% - лок во время получения запроса в проксируемом узле
        (jdk.internal.net.http.Http1AsyncReceiver.checkRequestMore и 
        jdk.internal.net.http.Http1AsyncReceiver.handlePendingDelegate)
      32.3% - лок в ComletableFuture для jdk.internal.net.http.Exchange.responseAsync
      2.57% - java.util.concurrent.LinkedBlockingQueue.take из очереди в requestExecutor
      0.62% - java.util.concurrent.CompletableFuture.orTimeout в proceed из proxyHandler
      Остальное - локи для jdk.internal.net.http.HttpClientImpl$SelectorManager.
  GET запросы:
      38.06% - лок во время получения запроса в проксируемом узле
      (jdk.internal.net.http.Http1AsyncReceiver.checkRequestMore и
      jdk.internal.net.http.Http1Response$Receiver.accept)
      24.3% - лок в ComletableFuture для jdk.internal.net.http.Exchange.responseAsync
      3.4% - java.util.concurrent.LinkedBlockingQueue.take из очереди в requestExecutor
      1% - java.util.concurrent.CompletableFuture.orTimeout в proceed из proxyHandler
      Остальное - локи для jdk.internal.net.http.HttpClientImpl$SelectorManager.

Как видно из результатов профилирования, большая часть времени работы CPU уходит на проксирование запросов по http
на другой узел и обратно, также при этом происходят более 90% локов и больше половины аллокаций.
Таким образом, как и в предыдущем stage, в первую очередь следует оптимизировать передачу данных между узлами кластера
заменив http протокол на другой, более эффективный, например, RPC, только в случае с репликацией такая оптимизация будет
еще полезней, так как число запросов в другие узлы вырастает. Также около 3% для PUT запросов и 17% для GET CPU уходит 
на метод getTask(take) для взятия задачи из очереди в requestExecutor, это связано с тем, что присутствует конкуренция
между потоками, так как каждый HttpClient содержит в себе executor, то есть 5 потоков минимум, при 1 потоке в 
таком executor-е плюс потоки в requestExecutor и replicasResponsesExecutor, что много больше ядер на моем устройстве, 
которых только 4. Но так как каждый кластер в реальности будет на отдельной машине, то данная проблема исчезнет.
Также при GET запросах очередь бывает пустой так как rate всего 500, что в 3 меньше максимально возможного,
чем объясняется такое высокое значение для take(), но такое значение нужно, чтобы сравнить с предыдущей версией, 
которая больший rate адекватно не выдержит. При асинхронной работе через CompletableFuture в целом картина профиля такая
же как и в прошлой версии сервиса, но в профиле по CPU видно, что метод proceed стал занимать в 2.5-3 раза меньше
процессорного времени, при этом локов не добавилось, так как использовался исключительно lock-free алгоритм для 
обработки и ответов репликами. Дальнейших оптимизаций на данный момент мне не предвидится, так как все упирается либо в 
sys call-ы, либо в работу сетевого протокола между узлами по http, а обработка ответов реплик теперь и так асинхронная.

Результаты wrk:

from = 5, ack = 5, rate 10000, PUT
```
./wrk -d 60 -t 64 -c 64 -R 10000 -L -s ./wrk-scripts/stage4_PUT_from5_ack5.lua http://localhost:20001
Running 1m test @ http://localhost:20001
  64 threads and 64 connections
  Thread calibration: mean lat.: 3468.213ms, rate sampling interval: 13680ms
  Thread calibration: mean lat.: 3483.973ms, rate sampling interval: 13623ms
  Thread calibration: mean lat.: 3461.840ms, rate sampling interval: 13615ms
  Thread calibration: mean lat.: 3387.481ms, rate sampling interval: 13459ms
  Thread calibration: mean lat.: 3415.751ms, rate sampling interval: 13443ms
  Thread calibration: mean lat.: 3493.929ms, rate sampling interval: 13647ms
  Thread calibration: mean lat.: 3382.661ms, rate sampling interval: 13467ms
  Thread calibration: mean lat.: 3394.787ms, rate sampling interval: 13426ms
  Thread calibration: mean lat.: 3386.283ms, rate sampling interval: 13352ms
  Thread calibration: mean lat.: 3495.571ms, rate sampling interval: 13606ms
  Thread calibration: mean lat.: 3464.577ms, rate sampling interval: 13557ms
  Thread calibration: mean lat.: 3469.776ms, rate sampling interval: 13557ms
  Thread calibration: mean lat.: 3472.144ms, rate sampling interval: 13492ms
  Thread calibration: mean lat.: 3503.985ms, rate sampling interval: 13795ms
  Thread calibration: mean lat.: 3456.483ms, rate sampling interval: 13582ms
  Thread calibration: mean lat.: 3506.344ms, rate sampling interval: 13631ms
  Thread calibration: mean lat.: 3465.546ms, rate sampling interval: 13492ms
  Thread calibration: mean lat.: 3453.560ms, rate sampling interval: 13590ms
  Thread calibration: mean lat.: 3461.579ms, rate sampling interval: 13647ms
  Thread calibration: mean lat.: 3386.829ms, rate sampling interval: 13467ms
  Thread calibration: mean lat.: 3426.415ms, rate sampling interval: 13606ms
  Thread calibration: mean lat.: 3431.395ms, rate sampling interval: 13524ms
  Thread calibration: mean lat.: 3476.690ms, rate sampling interval: 13647ms
  Thread calibration: mean lat.: 3436.497ms, rate sampling interval: 13524ms
  Thread calibration: mean lat.: 3438.382ms, rate sampling interval: 13426ms
  Thread calibration: mean lat.: 3361.015ms, rate sampling interval: 13352ms
  Thread calibration: mean lat.: 3431.964ms, rate sampling interval: 13492ms
  Thread calibration: mean lat.: 3433.821ms, rate sampling interval: 13574ms
  Thread calibration: mean lat.: 3389.030ms, rate sampling interval: 13459ms
  Thread calibration: mean lat.: 3480.249ms, rate sampling interval: 13467ms
  Thread calibration: mean lat.: 3479.887ms, rate sampling interval: 13680ms
  Thread calibration: mean lat.: 3481.987ms, rate sampling interval: 13500ms
  Thread calibration: mean lat.: 3408.263ms, rate sampling interval: 13352ms
  Thread calibration: mean lat.: 3462.656ms, rate sampling interval: 13582ms
  Thread calibration: mean lat.: 3448.170ms, rate sampling interval: 13746ms
  Thread calibration: mean lat.: 3464.247ms, rate sampling interval: 13688ms
  Thread calibration: mean lat.: 3480.275ms, rate sampling interval: 13615ms
  Thread calibration: mean lat.: 3429.460ms, rate sampling interval: 13443ms
  Thread calibration: mean lat.: 3438.917ms, rate sampling interval: 13664ms
  Thread calibration: mean lat.: 3441.660ms, rate sampling interval: 13664ms
  Thread calibration: mean lat.: 3399.904ms, rate sampling interval: 13484ms
  Thread calibration: mean lat.: 3413.432ms, rate sampling interval: 13524ms
  Thread calibration: mean lat.: 3488.486ms, rate sampling interval: 13664ms
  Thread calibration: mean lat.: 3501.262ms, rate sampling interval: 13631ms
  Thread calibration: mean lat.: 3392.508ms, rate sampling interval: 13549ms
  Thread calibration: mean lat.: 3413.626ms, rate sampling interval: 13656ms
  Thread calibration: mean lat.: 3390.364ms, rate sampling interval: 13672ms
  Thread calibration: mean lat.: 3471.253ms, rate sampling interval: 13615ms
  Thread calibration: mean lat.: 3416.858ms, rate sampling interval: 13598ms
  Thread calibration: mean lat.: 3503.887ms, rate sampling interval: 13582ms
  Thread calibration: mean lat.: 3476.930ms, rate sampling interval: 13451ms
  Thread calibration: mean lat.: 3487.275ms, rate sampling interval: 13729ms
  Thread calibration: mean lat.: 3435.012ms, rate sampling interval: 13492ms
  Thread calibration: mean lat.: 3440.649ms, rate sampling interval: 13516ms
  Thread calibration: mean lat.: 3429.906ms, rate sampling interval: 13541ms
  Thread calibration: mean lat.: 3411.032ms, rate sampling interval: 13459ms
  Thread calibration: mean lat.: 3444.779ms, rate sampling interval: 13533ms
  Thread calibration: mean lat.: 3426.140ms, rate sampling interval: 13344ms
  Thread calibration: mean lat.: 3446.396ms, rate sampling interval: 13443ms
  Thread calibration: mean lat.: 3454.497ms, rate sampling interval: 13590ms
  Thread calibration: mean lat.: 3471.496ms, rate sampling interval: 13697ms
  Thread calibration: mean lat.: 3494.491ms, rate sampling interval: 13787ms
  Thread calibration: mean lat.: 3450.637ms, rate sampling interval: 13492ms
  Thread calibration: mean lat.: 3406.314ms, rate sampling interval: 13582ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency    28.49s    11.92s   49.28s    57.76%
    Req/Sec    26.41      0.50    27.00    100.00%
  Latency Distribution (HdrHistogram - Recorded Latency)
 50.000%   28.48s
 75.000%   38.83s
 90.000%   44.99s
 99.000%   48.73s
 99.900%   49.15s
 99.990%   49.25s
 99.999%   49.32s
100.000%   49.32s

  Detailed Percentile spectrum:
       Value   Percentile   TotalCount 1/(1-Percentile)

    7786.495     0.000000            1         1.00
   12009.471     0.100000         8622         1.11
   16138.239     0.200000        17245         1.25
   20201.471     0.300000        25881         1.43
   24281.087     0.400000        34512         1.67
   28475.391     0.500000        43120         2.00
   30572.543     0.550000        47428         2.22
   32718.847     0.600000        51735         2.50
   34766.847     0.650000        56101         2.86
   36765.695     0.700000        60364         3.33
   38830.079     0.750000        64673         4.00
   39878.655     0.775000        66857         4.44
   40927.231     0.800000        68979         5.00
   42008.575     0.825000        71177         5.71
   42991.615     0.850000        73325         6.67
   43974.655     0.875000        75487         8.00
   44466.175     0.887500        76523         8.89
   44990.463     0.900000        77637        10.00
   45481.983     0.912500        78689        11.43
   46071.807     0.925000        79816        13.33
   46563.327     0.937500        80890        16.00
   46792.703     0.943750        81376        17.78
   47054.847     0.950000        81954        20.00
   47316.991     0.956250        82483        22.86
   47579.135     0.962500        83026        26.67
   47808.511     0.968750        83519        32.00
   47939.583     0.971875        83794        35.56
   48103.423     0.975000        84117        40.00
   48234.495     0.978125        84383        45.71
   48365.567     0.981250        84656        53.33
   48463.871     0.984375        84862        64.00
   48562.175     0.985938        85057        71.11
   48627.711     0.987500        85175        80.00
   48693.247     0.989062        85294        91.43
   48758.783     0.990625        85421       106.67
   48824.319     0.992188        85535       128.00
   48889.855     0.992969        85662       142.22
   48922.623     0.993750        85725       160.00
   48955.391     0.994531        85784       182.86
   48988.159     0.995313        85849       213.33
   49020.927     0.996094        85909       256.00
   49020.927     0.996484        85909       284.44
   49053.695     0.996875        85969       320.00
   49086.463     0.997266        86029       365.71
   49086.463     0.997656        86029       426.67
   49119.231     0.998047        86080       512.00
   49119.231     0.998242        86080       568.89
   49119.231     0.998437        86080       640.00
   49151.999     0.998633        86130       731.43
   49151.999     0.998828        86130       853.33
   49151.999     0.999023        86130      1024.00
   49184.767     0.999121        86161      1137.78
   49184.767     0.999219        86161      1280.00
   49184.767     0.999316        86161      1462.86
   49184.767     0.999414        86161      1706.67
   49217.535     0.999512        86183      2048.00
   49217.535     0.999561        86183      2275.56
   49217.535     0.999609        86183      2560.00
   49217.535     0.999658        86183      2925.71
   49217.535     0.999707        86183      3413.33
   49250.303     0.999756        86199      4096.00
   49250.303     0.999780        86199      4551.11
   49250.303     0.999805        86199      5120.00
   49250.303     0.999829        86199      5851.43
   49250.303     0.999854        86199      6826.67
   49250.303     0.999878        86199      8192.00
   49250.303     0.999890        86199      9102.22
   49283.071     0.999902        86206     10240.00
   49283.071     0.999915        86206     11702.86
   49283.071     0.999927        86206     13653.33
   49283.071     0.999939        86206     16384.00
   49283.071     0.999945        86206     18204.44
   49283.071     0.999951        86206     20480.00
   49283.071     0.999957        86206     23405.71
   49283.071     0.999963        86206     27306.67
   49283.071     0.999969        86206     32768.00
   49283.071     0.999973        86206     36408.89
   49283.071     0.999976        86206     40960.00
   49315.839     0.999979        86208     46811.43
   49315.839     1.000000        86208          inf
#[Mean    =    28491.335, StdDeviation   =    11921.254]
#[Max     =    49283.072, Total count    =        86208]
#[Buckets =           27, SubBuckets     =         2048]
----------------------------------------------------------
  107991 requests in 1.00m, 6.90MB read
Requests/sec:   1800.18
Transfer/sec:    117.79KB
```

from = 1, ack = 1, rate 10000, PUT
```
./wrk -d 60 -t 64 -c 64 -R 10000 -L -s ./wrk-scripts/stage4_PUT_from1_ack1.lua http://localhost:20001
Running 1m test @ http://localhost:20001
  64 threads and 64 connections
  Thread calibration: mean lat.: 2153.686ms, rate sampling interval: 8806ms
  Thread calibration: mean lat.: 2110.565ms, rate sampling interval: 8863ms
  Thread calibration: mean lat.: 2140.757ms, rate sampling interval: 8830ms
  Thread calibration: mean lat.: 2124.158ms, rate sampling interval: 8871ms
  Thread calibration: mean lat.: 2140.321ms, rate sampling interval: 8880ms
  Thread calibration: mean lat.: 2128.461ms, rate sampling interval: 8904ms
  Thread calibration: mean lat.: 2141.112ms, rate sampling interval: 8830ms
  Thread calibration: mean lat.: 2125.116ms, rate sampling interval: 8806ms
  Thread calibration: mean lat.: 2115.677ms, rate sampling interval: 8732ms
  Thread calibration: mean lat.: 2097.898ms, rate sampling interval: 8830ms
  Thread calibration: mean lat.: 2137.788ms, rate sampling interval: 8814ms
  Thread calibration: mean lat.: 2172.640ms, rate sampling interval: 8970ms
  Thread calibration: mean lat.: 2170.819ms, rate sampling interval: 8896ms
  Thread calibration: mean lat.: 2152.835ms, rate sampling interval: 8798ms
  Thread calibration: mean lat.: 2155.081ms, rate sampling interval: 8871ms
  Thread calibration: mean lat.: 2108.487ms, rate sampling interval: 8781ms
  Thread calibration: mean lat.: 2147.375ms, rate sampling interval: 8830ms
  Thread calibration: mean lat.: 2140.561ms, rate sampling interval: 8929ms
  Thread calibration: mean lat.: 2072.601ms, rate sampling interval: 8568ms
  Thread calibration: mean lat.: 2125.803ms, rate sampling interval: 8806ms
  Thread calibration: mean lat.: 2130.873ms, rate sampling interval: 8921ms
  Thread calibration: mean lat.: 2157.112ms, rate sampling interval: 8945ms
  Thread calibration: mean lat.: 2138.187ms, rate sampling interval: 8953ms
  Thread calibration: mean lat.: 2154.211ms, rate sampling interval: 8855ms
  Thread calibration: mean lat.: 2137.291ms, rate sampling interval: 8888ms
  Thread calibration: mean lat.: 2135.693ms, rate sampling interval: 8798ms
  Thread calibration: mean lat.: 2185.852ms, rate sampling interval: 8912ms
  Thread calibration: mean lat.: 2193.603ms, rate sampling interval: 9035ms
  Thread calibration: mean lat.: 2143.934ms, rate sampling interval: 8814ms
  Thread calibration: mean lat.: 2180.579ms, rate sampling interval: 8871ms
  Thread calibration: mean lat.: 2180.836ms, rate sampling interval: 8912ms
  Thread calibration: mean lat.: 2139.702ms, rate sampling interval: 8847ms
  Thread calibration: mean lat.: 2128.745ms, rate sampling interval: 8724ms
  Thread calibration: mean lat.: 2163.534ms, rate sampling interval: 8855ms
  Thread calibration: mean lat.: 2139.126ms, rate sampling interval: 8863ms
  Thread calibration: mean lat.: 2184.243ms, rate sampling interval: 8929ms
  Thread calibration: mean lat.: 2131.883ms, rate sampling interval: 8839ms
  Thread calibration: mean lat.: 2191.455ms, rate sampling interval: 8912ms
  Thread calibration: mean lat.: 2170.408ms, rate sampling interval: 8822ms
  Thread calibration: mean lat.: 2105.735ms, rate sampling interval: 8634ms
  Thread calibration: mean lat.: 2147.197ms, rate sampling interval: 8724ms
  Thread calibration: mean lat.: 2139.892ms, rate sampling interval: 8773ms
  Thread calibration: mean lat.: 2157.169ms, rate sampling interval: 8757ms
  Thread calibration: mean lat.: 2230.225ms, rate sampling interval: 8986ms
  Thread calibration: mean lat.: 2183.383ms, rate sampling interval: 8904ms
  Thread calibration: mean lat.: 2131.269ms, rate sampling interval: 8921ms
  Thread calibration: mean lat.: 2158.624ms, rate sampling interval: 8945ms
  Thread calibration: mean lat.: 2151.412ms, rate sampling interval: 8798ms
  Thread calibration: mean lat.: 2182.583ms, rate sampling interval: 8953ms
  Thread calibration: mean lat.: 2134.514ms, rate sampling interval: 8683ms
  Thread calibration: mean lat.: 2154.048ms, rate sampling interval: 8847ms
  Thread calibration: mean lat.: 2190.255ms, rate sampling interval: 8880ms
  Thread calibration: mean lat.: 2148.459ms, rate sampling interval: 8847ms
  Thread calibration: mean lat.: 2172.800ms, rate sampling interval: 8863ms
  Thread calibration: mean lat.: 2123.250ms, rate sampling interval: 8830ms
  Thread calibration: mean lat.: 2136.382ms, rate sampling interval: 8749ms
  Thread calibration: mean lat.: 2203.665ms, rate sampling interval: 8994ms
  Thread calibration: mean lat.: 2201.378ms, rate sampling interval: 8904ms
  Thread calibration: mean lat.: 2177.018ms, rate sampling interval: 8937ms
  Thread calibration: mean lat.: 2210.412ms, rate sampling interval: 8888ms
  Thread calibration: mean lat.: 2191.393ms, rate sampling interval: 8855ms
  Thread calibration: mean lat.: 2203.962ms, rate sampling interval: 9003ms
  Thread calibration: mean lat.: 2122.785ms, rate sampling interval: 8699ms
  Thread calibration: mean lat.: 2135.175ms, rate sampling interval: 8855ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency    18.38s     7.64s   31.97s    58.12%
    Req/Sec    72.36      1.14    75.00     95.00%
  Latency Distribution (HdrHistogram - Recorded Latency)
 50.000%   18.30s
 75.000%   24.95s
 90.000%   28.95s
 99.000%   31.46s
 99.900%   31.75s
 99.990%   31.90s
 99.999%   31.97s
100.000%   31.98s

  Detailed Percentile spectrum:
       Value   Percentile   TotalCount 1/(1-Percentile)

    4878.335     0.000000            1         1.00
    7716.863     0.100000        23229         1.11
   10477.567     0.200000        46475         1.25
   13221.887     0.300000        69724         1.43
   15802.367     0.400000        92922         1.67
   18300.927     0.500000       116119         2.00
   19726.335     0.550000       127838         2.22
   21069.823     0.600000       139384         2.50
   22429.695     0.650000       151017         2.86
   23773.183     0.700000       162663         3.33
   24952.831     0.750000       174185         4.00
   25591.807     0.775000       179987         4.44
   26230.783     0.800000       185752         5.00
   26886.143     0.825000       191645         5.71
   27607.039     0.850000       197351         6.67
   28295.167     0.875000       203290         8.00
   28622.847     0.887500       206177         8.89
   28950.527     0.900000       209076        10.00
   29294.591     0.912500       211924        11.43
   29655.039     0.925000       214840        13.33
   29982.719     0.937500       217691        16.00
   30146.559     0.943750       219189        17.78
   30310.399     0.950000       220618        20.00
   30490.623     0.956250       222068        22.86
   30670.847     0.962500       223528        26.67
   30851.071     0.968750       225056        32.00
   30932.991     0.971875       225710        35.56
   31031.295     0.975000       226444        40.00
   31129.599     0.978125       227177        45.71
   31227.903     0.981250       227947        53.33
   31309.823     0.984375       228623        64.00
   31358.975     0.985938       229043        71.11
   31391.743     0.987500       229343        80.00
   31440.895     0.989062       229757        91.43
   31490.047     0.990625       230148       106.67
   31522.815     0.992188       230393       128.00
   31555.583     0.992969       230651       142.22
   31571.967     0.993750       230781       160.00
   31588.351     0.994531       230926       182.86
   31621.119     0.995313       231182       213.33
   31637.503     0.996094       231320       256.00
   31653.887     0.996484       231446       284.44
   31670.271     0.996875       231550       320.00
   31670.271     0.997266       231550       365.71
   31686.655     0.997656       231644       426.67
   31703.039     0.998047       231742       512.00
   31719.423     0.998242       231822       568.89
   31719.423     0.998437       231822       640.00
   31735.807     0.998633       231913       731.43
   31735.807     0.998828       231913       853.33
   31752.191     0.999023       231973      1024.00
   31752.191     0.999121       231973      1137.78
   31768.575     0.999219       232024      1280.00
   31768.575     0.999316       232024      1462.86
   31784.959     0.999414       232065      1706.67
   31784.959     0.999512       232065      2048.00
   31801.343     0.999561       232089      2275.56
   31801.343     0.999609       232089      2560.00
   31817.727     0.999658       232115      2925.71
   31817.727     0.999707       232115      3413.33
   31834.111     0.999756       232132      4096.00
   31834.111     0.999780       232132      4551.11
   31834.111     0.999805       232132      5120.00
   31850.495     0.999829       232144      5851.43
   31850.495     0.999854       232144      6826.67
   31866.879     0.999878       232149      8192.00
   31883.263     0.999890       232152      9102.22
   31899.647     0.999902       232159     10240.00
   31899.647     0.999915       232159     11702.86
   31916.031     0.999927       232162     13653.33
   31932.415     0.999939       232167     16384.00
   31932.415     0.999945       232167     18204.44
   31932.415     0.999951       232167     20480.00
   31948.799     0.999957       232171     23405.71
   31948.799     0.999963       232171     27306.67
   31948.799     0.999969       232171     32768.00
   31948.799     0.999973       232171     36408.89
   31965.183     0.999976       232176     40960.00
   31965.183     0.999979       232176     46811.43
   31965.183     0.999982       232176     54613.33
   31965.183     0.999985       232176     65536.00
   31965.183     0.999986       232176     72817.78
   31965.183     0.999988       232176     81920.00
   31965.183     0.999989       232176     93622.86
   31965.183     0.999991       232176    109226.67
   31965.183     0.999992       232176    131072.00
   31965.183     0.999993       232176    145635.56
   31965.183     0.999994       232176    163840.00
   31965.183     0.999995       232176    187245.71
   31965.183     0.999995       232176    218453.33
   31981.567     0.999996       232177    262144.00
   31981.567     1.000000       232177          inf
#[Mean    =    18384.579, StdDeviation   =     7644.119]
#[Max     =    31965.184, Total count    =       232177]
#[Buckets =           27, SubBuckets     =         2048]
----------------------------------------------------------
  282282 requests in 1.00m, 18.04MB read
Requests/sec:   4710.81
Transfer/sec:    308.23KB
```

from = 5, ack = 5, rate 10000, GET
```
 ./wrk -d 60 -t 64 -c 64 -R 10000 -L -s ./wrk-scripts/stage4_GET_from5_ack5.lua http://localhost:20001
Running 1m test @ http://localhost:20001
  64 threads and 64 connections
  Thread calibration: mean lat.: 3784.606ms, rate sampling interval: 14852ms
  Thread calibration: mean lat.: 3696.537ms, rate sampling interval: 14614ms
  Thread calibration: mean lat.: 3744.930ms, rate sampling interval: 14557ms
  Thread calibration: mean lat.: 3725.977ms, rate sampling interval: 14729ms
  Thread calibration: mean lat.: 3801.949ms, rate sampling interval: 14942ms
  Thread calibration: mean lat.: 3867.030ms, rate sampling interval: 14901ms
  Thread calibration: mean lat.: 3771.647ms, rate sampling interval: 14737ms
  Thread calibration: mean lat.: 3829.034ms, rate sampling interval: 14884ms
  Thread calibration: mean lat.: 3743.225ms, rate sampling interval: 14761ms
  Thread calibration: mean lat.: 3830.152ms, rate sampling interval: 14827ms
  Thread calibration: mean lat.: 3807.794ms, rate sampling interval: 14925ms
  Thread calibration: mean lat.: 3728.687ms, rate sampling interval: 14680ms
  Thread calibration: mean lat.: 3785.720ms, rate sampling interval: 14761ms
  Thread calibration: mean lat.: 3788.371ms, rate sampling interval: 14532ms
  Thread calibration: mean lat.: 3874.006ms, rate sampling interval: 14868ms
  Thread calibration: mean lat.: 3796.082ms, rate sampling interval: 14622ms
  Thread calibration: mean lat.: 3826.066ms, rate sampling interval: 14868ms
  Thread calibration: mean lat.: 3898.103ms, rate sampling interval: 14868ms
  Thread calibration: mean lat.: 3865.906ms, rate sampling interval: 14835ms
  Thread calibration: mean lat.: 3848.468ms, rate sampling interval: 14745ms
  Thread calibration: mean lat.: 3870.331ms, rate sampling interval: 14942ms
  Thread calibration: mean lat.: 3987.947ms, rate sampling interval: 15015ms
  Thread calibration: mean lat.: 3788.792ms, rate sampling interval: 14745ms
  Thread calibration: mean lat.: 3852.245ms, rate sampling interval: 14770ms
  Thread calibration: mean lat.: 3823.542ms, rate sampling interval: 14761ms
  Thread calibration: mean lat.: 3881.324ms, rate sampling interval: 15081ms
  Thread calibration: mean lat.: 3788.790ms, rate sampling interval: 14753ms
  Thread calibration: mean lat.: 3821.701ms, rate sampling interval: 14786ms
  Thread calibration: mean lat.: 3835.034ms, rate sampling interval: 15032ms
  Thread calibration: mean lat.: 3774.546ms, rate sampling interval: 14622ms
  Thread calibration: mean lat.: 3926.677ms, rate sampling interval: 14917ms
  Thread calibration: mean lat.: 3862.632ms, rate sampling interval: 14917ms
  Thread calibration: mean lat.: 3902.848ms, rate sampling interval: 14860ms
  Thread calibration: mean lat.: 3852.644ms, rate sampling interval: 14639ms
  Thread calibration: mean lat.: 3820.975ms, rate sampling interval: 14761ms
  Thread calibration: mean lat.: 3824.258ms, rate sampling interval: 14630ms
  Thread calibration: mean lat.: 3793.471ms, rate sampling interval: 14802ms
  Thread calibration: mean lat.: 3941.814ms, rate sampling interval: 14843ms
  Thread calibration: mean lat.: 3840.681ms, rate sampling interval: 14843ms
  Thread calibration: mean lat.: 3865.641ms, rate sampling interval: 14827ms
  Thread calibration: mean lat.: 3776.973ms, rate sampling interval: 14794ms
  Thread calibration: mean lat.: 3805.207ms, rate sampling interval: 14606ms
  Thread calibration: mean lat.: 3810.796ms, rate sampling interval: 14934ms
  Thread calibration: mean lat.: 3959.515ms, rate sampling interval: 14835ms
  Thread calibration: mean lat.: 3886.553ms, rate sampling interval: 14753ms
  Thread calibration: mean lat.: 3811.745ms, rate sampling interval: 14573ms
  Thread calibration: mean lat.: 3885.801ms, rate sampling interval: 14893ms
  Thread calibration: mean lat.: 3877.622ms, rate sampling interval: 14712ms
  Thread calibration: mean lat.: 3847.929ms, rate sampling interval: 14680ms
  Thread calibration: mean lat.: 3871.820ms, rate sampling interval: 14819ms
  Thread calibration: mean lat.: 3884.125ms, rate sampling interval: 14802ms
  Thread calibration: mean lat.: 3884.074ms, rate sampling interval: 14835ms
  Thread calibration: mean lat.: 3836.492ms, rate sampling interval: 14778ms
  Thread calibration: mean lat.: 3841.846ms, rate sampling interval: 14778ms
  Thread calibration: mean lat.: 3783.849ms, rate sampling interval: 14573ms
  Thread calibration: mean lat.: 3869.258ms, rate sampling interval: 14696ms
  Thread calibration: mean lat.: 3847.193ms, rate sampling interval: 14745ms
  Thread calibration: mean lat.: 3903.796ms, rate sampling interval: 15032ms
  Thread calibration: mean lat.: 3817.596ms, rate sampling interval: 14835ms
  Thread calibration: mean lat.: 3919.807ms, rate sampling interval: 14917ms
  Thread calibration: mean lat.: 3815.730ms, rate sampling interval: 14737ms
  Thread calibration: mean lat.: 3876.374ms, rate sampling interval: 15032ms
  Thread calibration: mean lat.: 3899.480ms, rate sampling interval: 14852ms
  Thread calibration: mean lat.: 3839.291ms, rate sampling interval: 14819ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency    29.68s    12.07s   50.76s    58.04%
    Req/Sec    23.51      0.52    24.00    100.00%
  Latency Distribution (HdrHistogram - Recorded Latency)
 50.000%   29.72s
 75.000%   40.11s
 90.000%   46.40s
 99.000%   50.23s
 99.900%   50.63s
 99.990%   50.72s
 99.999%   50.76s
100.000%   50.79s

  Detailed Percentile spectrum:
       Value   Percentile   TotalCount 1/(1-Percentile)

    8314.879     0.000000            1         1.00
   12992.511     0.100000         7653         1.11
   17203.199     0.200000        15306         1.25
   21348.351     0.300000        22963         1.43
   25559.039     0.400000        30633         1.67
   29720.575     0.500000        38282         2.00
   31834.111     0.550000        42097         2.22
   33882.111     0.600000        45913         2.50
   35946.495     0.650000        49775         2.86
   38010.879     0.700000        53570         3.33
   40108.031     0.750000        57396         4.00
   41156.607     0.775000        59336         4.44
   42172.415     0.800000        61230         5.00
   43220.991     0.825000        63182         5.71
   44269.567     0.850000        65067         6.67
   45318.143     0.875000        66974         8.00
   45842.431     0.887500        67908         8.89
   46399.487     0.900000        68909        10.00
   46923.775     0.912500        69826        11.43
   47448.063     0.925000        70779        13.33
   48005.119     0.937500        71775        16.00
   48267.263     0.943750        72226        17.78
   48529.407     0.950000        72699        20.00
   48824.319     0.956250        73214        22.86
   49086.463     0.962500        73682        26.67
   49348.607     0.968750        74175        32.00
   49446.911     0.971875        74370        35.56
   49577.983     0.975000        74608        40.00
   49709.055     0.978125        74843        45.71
   49872.895     0.981250        75136        53.33
   50003.967     0.984375        75379        64.00
   50069.503     0.985938        75496        71.11
   50135.039     0.987500        75615        80.00
   50200.575     0.989062        75719        91.43
   50266.111     0.990625        75837       106.67
   50331.647     0.992188        75954       128.00
   50364.415     0.992969        76004       142.22
   50397.183     0.993750        76066       160.00
   50429.951     0.994531        76128       182.86
   50462.719     0.995313        76184       213.33
   50495.487     0.996094        76243       256.00
   50528.255     0.996484        76304       284.44
   50528.255     0.996875        76304       320.00
   50561.023     0.997266        76357       365.71
   50561.023     0.997656        76357       426.67
   50593.791     0.998047        76408       512.00
   50593.791     0.998242        76408       568.89
   50593.791     0.998437        76408       640.00
   50626.559     0.998633        76454       731.43
   50626.559     0.998828        76454       853.33
   50626.559     0.999023        76454      1024.00
   50626.559     0.999121        76454      1137.78
   50659.327     0.999219        76478      1280.00
   50659.327     0.999316        76478      1462.86
   50659.327     0.999414        76478      1706.67
   50692.095     0.999512        76502      2048.00
   50692.095     0.999561        76502      2275.56
   50692.095     0.999609        76502      2560.00
   50692.095     0.999658        76502      2925.71
   50692.095     0.999707        76502      3413.33
   50692.095     0.999756        76502      4096.00
   50692.095     0.999780        76502      4551.11
   50692.095     0.999805        76502      5120.00
   50724.863     0.999829        76510      5851.43
   50724.863     0.999854        76510      6826.67
   50724.863     0.999878        76510      8192.00
   50724.863     0.999890        76510      9102.22
   50724.863     0.999902        76510     10240.00
   50724.863     0.999915        76510     11702.86
   50757.631     0.999927        76515     13653.33
   50757.631     0.999939        76515     16384.00
   50757.631     0.999945        76515     18204.44
   50757.631     0.999951        76515     20480.00
   50757.631     0.999957        76515     23405.71
   50757.631     0.999963        76515     27306.67
   50757.631     0.999969        76515     32768.00
   50757.631     0.999973        76515     36408.89
   50757.631     0.999976        76515     40960.00
   50757.631     0.999979        76515     46811.43
   50757.631     0.999982        76515     54613.33
   50757.631     0.999985        76515     65536.00
   50757.631     0.999986        76515     72817.78
   50790.399     0.999988        76516     81920.00
   50790.399     1.000000        76516          inf
#[Mean    =    29678.419, StdDeviation   =    12073.584]
#[Max     =    50757.632, Total count    =        76516]
#[Buckets =           27, SubBuckets     =         2048]
----------------------------------------------------------
  93132 requests in 1.00m, 8.46MB read
Requests/sec:   1553.62
Transfer/sec:    144.49KB
```

from = 1, ack = 1, rate 10000, GET
```
./wrk -d 60 -t 64 -c 64 -R 10000 -L -s ./wrk-scripts/stage4_GET_from1_ack1.lua http://localhost:200
01
Running 1m test @ http://localhost:20001
  64 threads and 64 connections
  Thread calibration: mean lat.: 2353.320ms, rate sampling interval: 9363ms
  Thread calibration: mean lat.: 2296.886ms, rate sampling interval: 9379ms
  Thread calibration: mean lat.: 2267.560ms, rate sampling interval: 9207ms
  Thread calibration: mean lat.: 2333.401ms, rate sampling interval: 9412ms
  Thread calibration: mean lat.: 2299.280ms, rate sampling interval: 9232ms
  Thread calibration: mean lat.: 2504.088ms, rate sampling interval: 9740ms
  Thread calibration: mean lat.: 2303.806ms, rate sampling interval: 9347ms
  Thread calibration: mean lat.: 2426.927ms, rate sampling interval: 9510ms
  Thread calibration: mean lat.: 2483.568ms, rate sampling interval: 9756ms
  Thread calibration: mean lat.: 2364.854ms, rate sampling interval: 9347ms
  Thread calibration: mean lat.: 2461.788ms, rate sampling interval: 9592ms
  Thread calibration: mean lat.: 2532.351ms, rate sampling interval: 9797ms
  Thread calibration: mean lat.: 2419.063ms, rate sampling interval: 9355ms
  Thread calibration: mean lat.: 2406.980ms, rate sampling interval: 9502ms
  Thread calibration: mean lat.: 2502.125ms, rate sampling interval: 9715ms
  Thread calibration: mean lat.: 2271.195ms, rate sampling interval: 9199ms
  Thread calibration: mean lat.: 2435.593ms, rate sampling interval: 9543ms
  Thread calibration: mean lat.: 2442.399ms, rate sampling interval: 9486ms
  Thread calibration: mean lat.: 2572.998ms, rate sampling interval: 9928ms
  Thread calibration: mean lat.: 2454.058ms, rate sampling interval: 9633ms
  Thread calibration: mean lat.: 2439.629ms, rate sampling interval: 9666ms
  Thread calibration: mean lat.: 2458.548ms, rate sampling interval: 9428ms
  Thread calibration: mean lat.: 2502.633ms, rate sampling interval: 9773ms
  Thread calibration: mean lat.: 2483.306ms, rate sampling interval: 9732ms
  Thread calibration: mean lat.: 2538.347ms, rate sampling interval: 9838ms
  Thread calibration: mean lat.: 2505.319ms, rate sampling interval: 9715ms
  Thread calibration: mean lat.: 2489.963ms, rate sampling interval: 9584ms
  Thread calibration: mean lat.: 2409.288ms, rate sampling interval: 9469ms
  Thread calibration: mean lat.: 2438.863ms, rate sampling interval: 9617ms
  Thread calibration: mean lat.: 2438.073ms, rate sampling interval: 9396ms
  Thread calibration: mean lat.: 2384.871ms, rate sampling interval: 9388ms
  Thread calibration: mean lat.: 2475.001ms, rate sampling interval: 9650ms
  Thread calibration: mean lat.: 2412.522ms, rate sampling interval: 9428ms
  Thread calibration: mean lat.: 2493.438ms, rate sampling interval: 9641ms
  Thread calibration: mean lat.: 2410.365ms, rate sampling interval: 9445ms
  Thread calibration: mean lat.: 2498.382ms, rate sampling interval: 9756ms
  Thread calibration: mean lat.: 2513.488ms, rate sampling interval: 9854ms
  Thread calibration: mean lat.: 2436.345ms, rate sampling interval: 9543ms
  Thread calibration: mean lat.: 2417.654ms, rate sampling interval: 9682ms
  Thread calibration: mean lat.: 2506.665ms, rate sampling interval: 9773ms
  Thread calibration: mean lat.: 2446.253ms, rate sampling interval: 9625ms
  Thread calibration: mean lat.: 2544.702ms, rate sampling interval: 9846ms
  Thread calibration: mean lat.: 2499.183ms, rate sampling interval: 9699ms
  Thread calibration: mean lat.: 2443.247ms, rate sampling interval: 9535ms
  Thread calibration: mean lat.: 2489.989ms, rate sampling interval: 9682ms
  Thread calibration: mean lat.: 2445.643ms, rate sampling interval: 9510ms
  Thread calibration: mean lat.: 2470.976ms, rate sampling interval: 9732ms
  Thread calibration: mean lat.: 2520.028ms, rate sampling interval: 9789ms
  Thread calibration: mean lat.: 2373.498ms, rate sampling interval: 9306ms
  Thread calibration: mean lat.: 2486.316ms, rate sampling interval: 9814ms
  Thread calibration: mean lat.: 2486.559ms, rate sampling interval: 9732ms
  Thread calibration: mean lat.: 2517.716ms, rate sampling interval: 9781ms
  Thread calibration: mean lat.: 2475.138ms, rate sampling interval: 9666ms
  Thread calibration: mean lat.: 2477.609ms, rate sampling interval: 9592ms
  Thread calibration: mean lat.: 2409.356ms, rate sampling interval: 9527ms
  Thread calibration: mean lat.: 2430.221ms, rate sampling interval: 9633ms
  Thread calibration: mean lat.: 2474.963ms, rate sampling interval: 9691ms
  Thread calibration: mean lat.: 2444.189ms, rate sampling interval: 9617ms
  Thread calibration: mean lat.: 2479.463ms, rate sampling interval: 9732ms
  Thread calibration: mean lat.: 2486.663ms, rate sampling interval: 9666ms
  Thread calibration: mean lat.: 2490.026ms, rate sampling interval: 9748ms
  Thread calibration: mean lat.: 2465.033ms, rate sampling interval: 9699ms
  Thread calibration: mean lat.: 2468.771ms, rate sampling interval: 9764ms
  Thread calibration: mean lat.: 2495.434ms, rate sampling interval: 9650ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency    19.92s     8.24s   34.34s    57.93%
    Req/Sec    66.38      1.53    70.00     90.31%
  Latency Distribution (HdrHistogram - Recorded Latency)
 50.000%   19.99s
 75.000%   27.12s
 90.000%   31.20s
 99.000%   33.82s
 99.900%   34.21s
 99.990%   34.31s
 99.999%   34.37s
100.000%   34.37s

  Detailed Percentile spectrum:
       Value   Percentile   TotalCount 1/(1-Percentile)

    5255.167     0.000000            1         1.00
    8364.031     0.100000        21373         1.11
   11337.727     0.200000        42749         1.25
   14295.039     0.300000        64114         1.43
   17268.735     0.400000        85594         1.67
   19988.479     0.500000       106864         2.00
   21381.119     0.550000       117577         2.22
   22855.679     0.600000       128262         2.50
   24281.087     0.650000       139001         2.86
   25690.111     0.700000       149590         3.33
   27115.519     0.750000       160354         4.00
   27820.031     0.775000       165647         4.44
   28475.391     0.800000       170994         5.00
   29130.751     0.825000       176293         5.71
   29818.879     0.850000       181696         6.67
   30523.391     0.875000       187076         8.00
   30851.071     0.887500       189668         8.89
   31195.135     0.900000       192379        10.00
   31539.199     0.912500       195030        11.43
   31883.263     0.925000       197658        13.33
   32260.095     0.937500       200407        16.00
   32440.319     0.943750       201662        17.78
   32636.927     0.950000       203092        20.00
   32817.151     0.956250       204424        22.86
   32997.375     0.962500       205733        26.67
   33193.983     0.968750       207113        32.00
   33275.903     0.971875       207699        35.56
   33374.207     0.975000       208414        40.00
   33456.127     0.978125       209036        45.71
   33554.431     0.981250       209793        53.33
   33652.735     0.984375       210495        64.00
   33685.503     0.985938       210757        71.11
   33751.039     0.987500       211196        80.00
   33783.807     0.989062       211414        91.43
   33849.343     0.990625       211814       106.67
   33882.111     0.992188       212028       128.00
   33914.879     0.992969       212217       142.22
   33947.647     0.993750       212421       160.00
   33980.415     0.994531       212614       182.86
   34013.183     0.995313       212799       213.33
   34045.951     0.996094       212950       256.00
   34045.951     0.996484       212950       284.44
   34078.719     0.996875       213084       320.00
   34111.487     0.997266       213223       365.71
   34111.487     0.997656       213223       426.67
   34144.255     0.998047       213316       512.00
   34144.255     0.998242       213316       568.89
   34177.023     0.998437       213411       640.00
   34177.023     0.998633       213411       731.43
   34209.791     0.998828       213503       853.33
   34209.791     0.999023       213503      1024.00
   34209.791     0.999121       213503      1137.78
   34242.559     0.999219       213582      1280.00
   34242.559     0.999316       213582      1462.86
   34242.559     0.999414       213582      1706.67
   34242.559     0.999512       213582      2048.00
   34275.327     0.999561       213631      2275.56
   34275.327     0.999609       213631      2560.00
   34275.327     0.999658       213631      2925.71
   34275.327     0.999707       213631      3413.33
   34275.327     0.999756       213631      4096.00
   34308.095     0.999780       213666      4551.11
   34308.095     0.999805       213666      5120.00
   34308.095     0.999829       213666      5851.43
   34308.095     0.999854       213666      6826.67
   34308.095     0.999878       213666      8192.00
   34308.095     0.999890       213666      9102.22
   34308.095     0.999902       213666     10240.00
   34308.095     0.999915       213666     11702.86
   34308.095     0.999927       213666     13653.33
   34340.863     0.999939       213677     16384.00
   34340.863     0.999945       213677     18204.44
   34340.863     0.999951       213677     20480.00
   34340.863     0.999957       213677     23405.71
   34340.863     0.999963       213677     27306.67
   34340.863     0.999969       213677     32768.00
   34340.863     0.999973       213677     36408.89
   34340.863     0.999976       213677     40960.00
   34340.863     0.999979       213677     46811.43
   34373.631     0.999982       213681     54613.33
   34373.631     1.000000       213681          inf
#[Mean    =    19919.450, StdDeviation   =     8240.544]
#[Max     =    34340.864, Total count    =       213681]
#[Buckets =           27, SubBuckets     =         2048]
----------------------------------------------------------
  258885 requests in 1.00m, 23.63MB read
Requests/sec:   4319.43
Transfer/sec:    403.79KB
```

from = 5, ack = 3, rate 1000, PUT
```
./wrk -d 60 -t 64 -c 64 -R 1000 -L -s ./wrk-scripts/stage4_PUT_from5_ack3.lua http://localhost:2000
1
Running 1m test @ http://localhost:20001
  64 threads and 64 connections
  Thread calibration: mean lat.: 5.099ms, rate sampling interval: 15ms
  Thread calibration: mean lat.: 5.440ms, rate sampling interval: 15ms
  Thread calibration: mean lat.: 5.655ms, rate sampling interval: 17ms
  Thread calibration: mean lat.: 5.504ms, rate sampling interval: 17ms
  Thread calibration: mean lat.: 5.169ms, rate sampling interval: 19ms
  Thread calibration: mean lat.: 5.183ms, rate sampling interval: 18ms
  Thread calibration: mean lat.: 6.759ms, rate sampling interval: 22ms
  Thread calibration: mean lat.: 6.114ms, rate sampling interval: 20ms
  Thread calibration: mean lat.: 6.672ms, rate sampling interval: 21ms
  Thread calibration: mean lat.: 6.425ms, rate sampling interval: 20ms
  Thread calibration: mean lat.: 7.268ms, rate sampling interval: 21ms
  Thread calibration: mean lat.: 8.147ms, rate sampling interval: 24ms
  Thread calibration: mean lat.: 8.342ms, rate sampling interval: 26ms
  Thread calibration: mean lat.: 8.285ms, rate sampling interval: 25ms
  Thread calibration: mean lat.: 7.783ms, rate sampling interval: 22ms
  Thread calibration: mean lat.: 7.952ms, rate sampling interval: 24ms
  Thread calibration: mean lat.: 7.444ms, rate sampling interval: 22ms
  Thread calibration: mean lat.: 8.327ms, rate sampling interval: 26ms
  Thread calibration: mean lat.: 7.671ms, rate sampling interval: 23ms
  Thread calibration: mean lat.: 7.315ms, rate sampling interval: 23ms
  Thread calibration: mean lat.: 8.125ms, rate sampling interval: 25ms
  Thread calibration: mean lat.: 8.114ms, rate sampling interval: 25ms
  Thread calibration: mean lat.: 4.379ms, rate sampling interval: 16ms
  Thread calibration: mean lat.: 4.691ms, rate sampling interval: 17ms
  Thread calibration: mean lat.: 4.693ms, rate sampling interval: 16ms
  Thread calibration: mean lat.: 4.673ms, rate sampling interval: 16ms
  Thread calibration: mean lat.: 4.668ms, rate sampling interval: 16ms
  Thread calibration: mean lat.: 4.539ms, rate sampling interval: 15ms
  Thread calibration: mean lat.: 4.631ms, rate sampling interval: 15ms
  Thread calibration: mean lat.: 4.576ms, rate sampling interval: 15ms
  Thread calibration: mean lat.: 4.080ms, rate sampling interval: 14ms
  Thread calibration: mean lat.: 4.346ms, rate sampling interval: 15ms
  Thread calibration: mean lat.: 4.298ms, rate sampling interval: 14ms
  Thread calibration: mean lat.: 4.751ms, rate sampling interval: 15ms
  Thread calibration: mean lat.: 4.955ms, rate sampling interval: 16ms
  Thread calibration: mean lat.: 4.686ms, rate sampling interval: 13ms
  Thread calibration: mean lat.: 4.665ms, rate sampling interval: 16ms
  Thread calibration: mean lat.: 4.935ms, rate sampling interval: 13ms
  Thread calibration: mean lat.: 4.306ms, rate sampling interval: 15ms
  Thread calibration: mean lat.: 4.140ms, rate sampling interval: 14ms
  Thread calibration: mean lat.: 4.361ms, rate sampling interval: 15ms
  Thread calibration: mean lat.: 5.031ms, rate sampling interval: 15ms
  Thread calibration: mean lat.: 5.038ms, rate sampling interval: 16ms
  Thread calibration: mean lat.: 4.599ms, rate sampling interval: 15ms
  Thread calibration: mean lat.: 5.051ms, rate sampling interval: 16ms
  Thread calibration: mean lat.: 4.977ms, rate sampling interval: 15ms
  Thread calibration: mean lat.: 4.389ms, rate sampling interval: 14ms
  Thread calibration: mean lat.: 4.900ms, rate sampling interval: 15ms
  Thread calibration: mean lat.: 4.719ms, rate sampling interval: 16ms
  Thread calibration: mean lat.: 4.882ms, rate sampling interval: 15ms
  Thread calibration: mean lat.: 4.751ms, rate sampling interval: 14ms
  Thread calibration: mean lat.: 4.924ms, rate sampling interval: 15ms
  Thread calibration: mean lat.: 4.884ms, rate sampling interval: 15ms
  Thread calibration: mean lat.: 5.018ms, rate sampling interval: 15ms
  Thread calibration: mean lat.: 5.622ms, rate sampling interval: 16ms
  Thread calibration: mean lat.: 5.372ms, rate sampling interval: 17ms
  Thread calibration: mean lat.: 4.547ms, rate sampling interval: 16ms
  Thread calibration: mean lat.: 4.310ms, rate sampling interval: 15ms
  Thread calibration: mean lat.: 4.869ms, rate sampling interval: 15ms
  Thread calibration: mean lat.: 4.379ms, rate sampling interval: 15ms
  Thread calibration: mean lat.: 4.540ms, rate sampling interval: 15ms
  Thread calibration: mean lat.: 4.825ms, rate sampling interval: 15ms
  Thread calibration: mean lat.: 4.960ms, rate sampling interval: 14ms
  Thread calibration: mean lat.: 5.409ms, rate sampling interval: 15ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     8.72ms    5.43ms  86.21ms   90.33%
    Req/Sec    16.00     27.41    83.00     77.16%
  Latency Distribution (HdrHistogram - Recorded Latency)
 50.000%    7.65ms
 75.000%   10.43ms
 90.000%   13.39ms
 99.000%   24.75ms
 99.900%   69.38ms
 99.990%   76.74ms
 99.999%   86.27ms
100.000%   86.27ms

  Detailed Percentile spectrum:
       Value   Percentile   TotalCount 1/(1-Percentile)

       1.373     0.000000            1         1.00
       4.539     0.100000         5002         1.11
       5.351     0.200000         9999         1.25
       6.083     0.300000        15023         1.43
       6.819     0.400000        19999         1.67
       7.651     0.500000        25001         2.00
       8.123     0.550000        27512         2.22
       8.615     0.600000        30015         2.50
       9.159     0.650000        32495         2.86
       9.767     0.700000        35013         3.33
      10.431     0.750000        37516         4.00
      10.815     0.775000        38776         4.44
      11.215     0.800000        40004         5.00
      11.647     0.825000        41252         5.71
      12.119     0.850000        42502         6.67
      12.695     0.875000        43744         8.00
      13.015     0.887500        44369         8.89
      13.391     0.900000        45002        10.00
      13.823     0.912500        45632        11.43
      14.287     0.925000        46244        13.33
      14.895     0.937500        46870        16.00
      15.239     0.943750        47185        17.78
      15.671     0.950000        47498        20.00
      16.135     0.956250        47805        22.86
      16.719     0.962500        48120        26.67
      17.487     0.968750        48430        32.00
      17.903     0.971875        48586        35.56
      18.415     0.975000        48745        40.00
      19.087     0.978125        48901        45.71
      19.759     0.981250        49055        53.33
      20.815     0.984375        49212        64.00
      21.663     0.985938        49291        71.11
      22.559     0.987500        49369        80.00
      23.855     0.989062        49448        91.43
      25.551     0.990625        49526       106.67
      28.623     0.992188        49602       128.00
      31.087     0.992969        49641       142.22
      33.535     0.993750        49680       160.00
      37.183     0.994531        49719       182.86
      42.623     0.995313        49758       213.33
      55.679     0.996094        49797       256.00
      60.255     0.996484        49817       284.44
      61.535     0.996875        49836       320.00
      62.847     0.997266        49856       365.71
      64.063     0.997656        49875       426.67
      65.855     0.998047        49896       512.00
      66.303     0.998242        49905       568.89
      66.815     0.998437        49914       640.00
      67.775     0.998633        49924       731.43
      68.799     0.998828        49934       853.33
      69.439     0.999023        49944      1024.00
      69.631     0.999121        49949      1137.78
      70.079     0.999219        49953      1280.00
      70.591     0.999316        49958      1462.86
      71.103     0.999414        49964      1706.67
      71.999     0.999512        49968      2048.00
      73.087     0.999561        49971      2275.56
      73.471     0.999609        49973      2560.00
      73.535     0.999658        49975      2925.71
      73.919     0.999707        49978      3413.33
      74.111     0.999756        49980      4096.00
      74.623     0.999780        49983      4551.11
      74.623     0.999805        49983      5120.00
      75.775     0.999829        49984      5851.43
      76.223     0.999854        49985      6826.67
      76.479     0.999878        49986      8192.00
      76.735     0.999890        49987      9102.22
      76.799     0.999902        49988     10240.00
      76.799     0.999915        49988     11702.86
      76.863     0.999927        49989     13653.33
      76.863     0.999939        49989     16384.00
      82.815     0.999945        49990     18204.44
      82.815     0.999951        49990     20480.00
      82.815     0.999957        49990     23405.71
      82.879     0.999963        49991     27306.67
      82.879     0.999969        49991     32768.00
      82.879     0.999973        49991     36408.89
      82.879     0.999976        49991     40960.00
      82.879     0.999979        49991     46811.43
      86.271     0.999982        49992     54613.33
      86.271     1.000000        49992          inf
#[Mean    =        8.719, StdDeviation   =        5.433]
#[Max     =       86.208, Total count    =        49992]
#[Buckets =           27, SubBuckets     =         2048]
----------------------------------------------------------
  60032 requests in 1.00m, 3.84MB read
Requests/sec:   1000.94
Transfer/sec:     65.49KB
```

from = 3, ack = 2, rate 1000, PUT
```
./wrk -d 60 -t 64 -c 64 -R 1000 -L -s ./wrk-scripts/stage4_PUT_from3_ack2.lua http://localhost:2000
1
Running 1m test @ http://localhost:20001
  64 threads and 64 connections
  Thread calibration: mean lat.: 2.072ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.205ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.024ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.970ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.064ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.083ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.066ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.069ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.064ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.266ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.193ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.344ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.755ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.665ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.614ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.427ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.696ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.036ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.614ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.717ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.399ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.723ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.701ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.238ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.196ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.205ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.181ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.277ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.335ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.268ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.252ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.513ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.504ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.541ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.403ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.205ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.194ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.134ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.487ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.500ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.442ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.402ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.819ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.981ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.974ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 3.179ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 3.299ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.956ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 3.054ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.874ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.924ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 3.042ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.867ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 3.283ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 3.029ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.918ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.867ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.919ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.800ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.805ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 3.097ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.892ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.987ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 3.206ms, rate sampling interval: 10ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     3.57ms    2.07ms  31.02ms   85.50%
    Req/Sec    16.45     38.04   111.00     84.19%
  Latency Distribution (HdrHistogram - Recorded Latency)
 50.000%    2.99ms
 75.000%    4.05ms
 90.000%    5.97ms
 99.000%   11.73ms
 99.900%   17.73ms
 99.990%   28.05ms
 99.999%   31.04ms
100.000%   31.04ms

  Detailed Percentile spectrum:
       Value   Percentile   TotalCount 1/(1-Percentile)

       0.832     0.000000            1         1.00
       1.867     0.100000         5001         1.11
       2.197     0.200000        10005         1.25
       2.473     0.300000        15008         1.43
       2.727     0.400000        20001         1.67
       2.993     0.500000        25006         2.00
       3.143     0.550000        27483         2.22
       3.311     0.600000        30005         2.50
       3.507     0.650000        32486         2.86
       3.737     0.700000        34973         3.33
       4.045     0.750000        37479         4.00
       4.227     0.775000        38726         4.44
       4.447     0.800000        39969         5.00
       4.727     0.825000        41228         5.71
       5.047     0.850000        42478         6.67
       5.443     0.875000        43717         8.00
       5.683     0.887500        44340         8.89
       5.975     0.900000        44969        10.00
       6.271     0.912500        45593        11.43
       6.639     0.925000        46214        13.33
       7.063     0.937500        46835        16.00
       7.323     0.943750        47148        17.78
       7.591     0.950000        47461        20.00
       7.911     0.956250        47777        22.86
       8.295     0.962500        48085        26.67
       8.719     0.968750        48399        32.00
       8.983     0.971875        48553        35.56
       9.263     0.975000        48712        40.00
       9.591     0.978125        48867        45.71
      10.007     0.981250        49025        53.33
      10.535     0.984375        49177        64.00
      10.799     0.985938        49255        71.11
      11.103     0.987500        49333        80.00
      11.471     0.989062        49411        91.43
      11.943     0.990625        49489       106.67
      12.383     0.992188        49567       128.00
      12.607     0.992969        49606       142.22
      12.951     0.993750        49645       160.00
      13.407     0.994531        49685       182.86
      13.847     0.995313        49723       213.33
      14.279     0.996094        49762       256.00
      14.535     0.996484        49783       284.44
      14.735     0.996875        49801       320.00
      15.023     0.997266        49821       365.71
      15.391     0.997656        49840       426.67
      15.967     0.998047        49860       512.00
      16.383     0.998242        49870       568.89
      16.719     0.998437        49879       640.00
      17.151     0.998633        49889       731.43
      17.455     0.998828        49899       853.33
      17.839     0.999023        49909      1024.00
      18.063     0.999121        49914      1137.78
      18.543     0.999219        49918      1280.00
      18.831     0.999316        49924      1462.86
      19.135     0.999414        49928      1706.67
      20.287     0.999512        49933      2048.00
      20.991     0.999561        49936      2275.56
      21.791     0.999609        49938      2560.00
      21.807     0.999658        49940      2925.71
      22.447     0.999707        49944      3413.33
      22.495     0.999756        49945      4096.00
      25.343     0.999780        49947      4551.11
      25.631     0.999805        49948      5120.00
      25.871     0.999829        49949      5851.43
      26.431     0.999854        49950      6826.67
      27.631     0.999878        49951      8192.00
      28.047     0.999890        49952      9102.22
      29.967     0.999902        49953     10240.00
      29.967     0.999915        49953     11702.86
      30.079     0.999927        49954     13653.33
      30.079     0.999939        49954     16384.00
      30.271     0.999945        49955     18204.44
      30.271     0.999951        49955     20480.00
      30.271     0.999957        49955     23405.71
      30.863     0.999963        49956     27306.67
      30.863     0.999969        49956     32768.00
      30.863     0.999973        49956     36408.89
      30.863     0.999976        49956     40960.00
      30.863     0.999979        49956     46811.43
      31.039     0.999982        49957     54613.33
      31.039     1.000000        49957          inf
#[Mean    =        3.566, StdDeviation   =        2.066]
#[Max     =       31.024, Total count    =        49957]
#[Buckets =           27, SubBuckets     =         2048]
----------------------------------------------------------
  60032 requests in 1.00m, 3.84MB read
Requests/sec:   1000.62
Transfer/sec:     65.47KB
```

from = 1, ack = 1, rate 1000, PUT
```
./wrk -d 60 -t 64 -c 64 -R 1000 -L -s ./wrk-scripts/stage4_PUT_from1_ack1.lua http://localhost:2000
1
Running 1m test @ http://localhost:20001
  64 threads and 64 connections
  Thread calibration: mean lat.: 2.447ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.180ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.969ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.719ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.594ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.589ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.575ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.074ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.892ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.790ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.776ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.713ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.801ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.079ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.985ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.145ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.785ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.775ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.690ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.246ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.497ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.846ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.918ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.787ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.830ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.847ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.678ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.623ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.024ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.846ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.978ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.976ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.795ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.672ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.497ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.460ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.003ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.939ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.973ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.916ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.974ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.978ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.989ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.928ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.787ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.947ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.864ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.766ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.695ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.770ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.745ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.762ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.737ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.711ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.764ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.735ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.788ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.802ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.826ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.719ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.748ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.834ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.830ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.630ms, rate sampling interval: 10ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     2.09ms    0.88ms  14.76ms   78.57%
    Req/Sec    16.56     37.93   111.00     83.94%
  Latency Distribution (HdrHistogram - Recorded Latency)
 50.000%    2.01ms
 75.000%    2.47ms
 90.000%    3.00ms
 99.000%    5.11ms
 99.900%    9.08ms
 99.990%   13.47ms
 99.999%   14.77ms
100.000%   14.77ms

  Detailed Percentile spectrum:
       Value   Percentile   TotalCount 1/(1-Percentile)

       0.176     0.000000            1         1.00
       1.170     0.100000         5009         1.11
       1.457     0.200000        10004         1.25
       1.668     0.300000        14997         1.43
       1.845     0.400000        20022         1.67
       2.007     0.500000        24996         2.00
       2.089     0.550000        27523         2.22
       2.169     0.600000        29987         2.50
       2.261     0.650000        32526         2.86
       2.363     0.700000        35012         3.33
       2.475     0.750000        37488         4.00
       2.539     0.775000        38742         4.44
       2.609     0.800000        39988         5.00
       2.683     0.825000        41232         5.71
       2.769     0.850000        42481         6.67
       2.869     0.875000        43731         8.00
       2.931     0.887500        44372         8.89
       2.997     0.900000        44979        10.00
       3.073     0.912500        45597        11.43
       3.159     0.925000        46227        13.33
       3.263     0.937500        46850        16.00
       3.327     0.943750        47160        17.78
       3.401     0.950000        47472        20.00
       3.483     0.956250        47783        22.86
       3.583     0.962500        48098        26.67
       3.705     0.968750        48409        32.00
       3.793     0.971875        48565        35.56
       3.887     0.975000        48719        40.00
       4.003     0.978125        48877        45.71
       4.143     0.981250        49033        53.33
       4.351     0.984375        49188        64.00
       4.495     0.985938        49267        71.11
       4.675     0.987500        49345        80.00
       4.927     0.989062        49422        91.43
       5.231     0.990625        49501       106.67
       5.535     0.992188        49578       128.00
       5.699     0.992969        49617       142.22
       5.903     0.993750        49657       160.00
       6.139     0.994531        49695       182.86
       6.335     0.995313        49734       213.33
       6.587     0.996094        49773       256.00
       6.783     0.996484        49793       284.44
       6.883     0.996875        49812       320.00
       7.091     0.997266        49832       365.71
       7.355     0.997656        49852       426.67
       7.615     0.998047        49871       512.00
       7.935     0.998242        49881       568.89
       8.143     0.998437        49890       640.00
       8.359     0.998633        49900       731.43
       8.719     0.998828        49910       853.33
       9.111     0.999023        49920      1024.00
       9.255     0.999121        49925      1137.78
       9.599     0.999219        49929      1280.00
       9.983     0.999316        49934      1462.86
      10.543     0.999414        49939      1706.67
      11.639     0.999512        49944      2048.00
      11.759     0.999561        49947      2275.56
      12.375     0.999609        49950      2560.00
      12.479     0.999658        49952      2925.71
      12.591     0.999707        49954      3413.33
      12.695     0.999756        49956      4096.00
      12.759     0.999780        49958      4551.11
      12.839     0.999805        49959      5120.00
      13.055     0.999829        49960      5851.43
      13.271     0.999854        49961      6826.67
      13.359     0.999878        49962      8192.00
      13.471     0.999890        49963      9102.22
      14.095     0.999902        49964     10240.00
      14.095     0.999915        49964     11702.86
      14.343     0.999927        49965     13653.33
      14.343     0.999939        49965     16384.00
      14.423     0.999945        49966     18204.44
      14.423     0.999951        49966     20480.00
      14.423     0.999957        49966     23405.71
      14.495     0.999963        49967     27306.67
      14.495     0.999969        49967     32768.00
      14.495     0.999973        49967     36408.89
      14.495     0.999976        49967     40960.00
      14.495     0.999979        49967     46811.43
      14.767     0.999982        49968     54613.33
      14.767     1.000000        49968          inf
#[Mean    =        2.086, StdDeviation   =        0.881]
#[Max     =       14.760, Total count    =        49968]
#[Buckets =           27, SubBuckets     =         2048]
----------------------------------------------------------
  60032 requests in 1.00m, 3.84MB read
Requests/sec:   1000.37
Transfer/sec:     65.45KB
```

from = 5, ack = 3, rate 500, GET
```
./wrk -d 60 -t 64 -c 64 -R 500 -L -s ./wrk-scripts/stage4_GET_from5_ack3.lua http://localhost:20001
Running 1m test @ http://localhost:20001
  64 threads and 64 connections
  Thread calibration: mean lat.: 3.635ms, rate sampling interval: 11ms
  Thread calibration: mean lat.: 3.785ms, rate sampling interval: 11ms
  Thread calibration: mean lat.: 4.410ms, rate sampling interval: 13ms
  Thread calibration: mean lat.: 4.077ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 4.414ms, rate sampling interval: 12ms
  Thread calibration: mean lat.: 4.624ms, rate sampling interval: 13ms
  Thread calibration: mean lat.: 4.669ms, rate sampling interval: 12ms
  Thread calibration: mean lat.: 4.508ms, rate sampling interval: 12ms
  Thread calibration: mean lat.: 4.350ms, rate sampling interval: 11ms
  Thread calibration: mean lat.: 4.869ms, rate sampling interval: 14ms
  Thread calibration: mean lat.: 4.670ms, rate sampling interval: 12ms
  Thread calibration: mean lat.: 4.779ms, rate sampling interval: 12ms
  Thread calibration: mean lat.: 3.790ms, rate sampling interval: 11ms
  Thread calibration: mean lat.: 3.645ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 3.584ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 4.062ms, rate sampling interval: 11ms
  Thread calibration: mean lat.: 4.258ms, rate sampling interval: 12ms
  Thread calibration: mean lat.: 4.362ms, rate sampling interval: 12ms
  Thread calibration: mean lat.: 4.487ms, rate sampling interval: 12ms
  Thread calibration: mean lat.: 5.111ms, rate sampling interval: 15ms
  Thread calibration: mean lat.: 5.055ms, rate sampling interval: 14ms
  Thread calibration: mean lat.: 4.913ms, rate sampling interval: 13ms
  Thread calibration: mean lat.: 4.946ms, rate sampling interval: 14ms
  Thread calibration: mean lat.: 5.181ms, rate sampling interval: 14ms
  Thread calibration: mean lat.: 5.014ms, rate sampling interval: 14ms
  Thread calibration: mean lat.: 5.623ms, rate sampling interval: 16ms
  Thread calibration: mean lat.: 5.089ms, rate sampling interval: 16ms
  Thread calibration: mean lat.: 4.833ms, rate sampling interval: 15ms
  Thread calibration: mean lat.: 5.715ms, rate sampling interval: 18ms
  Thread calibration: mean lat.: 6.701ms, rate sampling interval: 20ms
  Thread calibration: mean lat.: 5.888ms, rate sampling interval: 17ms
  Thread calibration: mean lat.: 6.940ms, rate sampling interval: 20ms
  Thread calibration: mean lat.: 7.221ms, rate sampling interval: 19ms
  Thread calibration: mean lat.: 7.144ms, rate sampling interval: 19ms
  Thread calibration: mean lat.: 7.060ms, rate sampling interval: 19ms
  Thread calibration: mean lat.: 5.505ms, rate sampling interval: 18ms
  Thread calibration: mean lat.: 7.141ms, rate sampling interval: 23ms
  Thread calibration: mean lat.: 7.675ms, rate sampling interval: 22ms
  Thread calibration: mean lat.: 7.663ms, rate sampling interval: 25ms
  Thread calibration: mean lat.: 7.697ms, rate sampling interval: 23ms
  Thread calibration: mean lat.: 9.018ms, rate sampling interval: 28ms
  Thread calibration: mean lat.: 9.047ms, rate sampling interval: 28ms
  Thread calibration: mean lat.: 8.834ms, rate sampling interval: 28ms
  Thread calibration: mean lat.: 8.261ms, rate sampling interval: 23ms
  Thread calibration: mean lat.: 9.965ms, rate sampling interval: 31ms
  Thread calibration: mean lat.: 9.765ms, rate sampling interval: 29ms
  Thread calibration: mean lat.: 9.887ms, rate sampling interval: 30ms
  Thread calibration: mean lat.: 10.063ms, rate sampling interval: 33ms
  Thread calibration: mean lat.: 9.890ms, rate sampling interval: 31ms
  Thread calibration: mean lat.: 8.376ms, rate sampling interval: 26ms
  Thread calibration: mean lat.: 9.541ms, rate sampling interval: 29ms
  Thread calibration: mean lat.: 8.590ms, rate sampling interval: 26ms
  Thread calibration: mean lat.: 8.236ms, rate sampling interval: 25ms
  Thread calibration: mean lat.: 3.323ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 3.884ms, rate sampling interval: 12ms
  Thread calibration: mean lat.: 4.023ms, rate sampling interval: 11ms
  Thread calibration: mean lat.: 4.042ms, rate sampling interval: 11ms
  Thread calibration: mean lat.: 3.064ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 3.268ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 3.329ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 4.037ms, rate sampling interval: 11ms
  Thread calibration: mean lat.: 3.723ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 3.831ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 3.833ms, rate sampling interval: 10ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     6.39ms    3.73ms  49.28ms   82.78%
    Req/Sec     8.06     23.91   111.00     88.90%
  Latency Distribution (HdrHistogram - Recorded Latency)
 50.000%    5.21ms
 75.000%    7.81ms
 90.000%   11.24ms
 99.000%   18.80ms
 99.900%   36.61ms
 99.990%   48.06ms
 99.999%   49.31ms
100.000%   49.31ms

  Detailed Percentile spectrum:
       Value   Percentile   TotalCount 1/(1-Percentile)

       1.486     0.000000            1         1.00
       3.181     0.100000         2498         1.11
       3.739     0.200000         5008         1.25
       4.215     0.300000         7512         1.43
       4.679     0.400000         9992         1.67
       5.211     0.500000        12507         2.00
       5.519     0.550000        13741         2.22
       5.907     0.600000        14989         2.50
       6.415     0.650000        16239         2.86
       7.051     0.700000        17490         3.33
       7.811     0.750000        18736         4.00
       8.271     0.775000        19359         4.44
       8.759     0.800000        19983         5.00
       9.287     0.825000        20612         5.71
       9.855     0.850000        21235         6.67
      10.463     0.875000        21853         8.00
      10.831     0.887500        22168         8.89
      11.239     0.900000        22481        10.00
      11.631     0.912500        22792        11.43
      12.111     0.925000        23101        13.33
      12.623     0.937500        23416        16.00
      12.927     0.943750        23574        17.78
      13.223     0.950000        23728        20.00
      13.583     0.956250        23885        22.86
      13.991     0.962500        24038        26.67
      14.527     0.968750        24195        32.00
      14.863     0.971875        24273        35.56
      15.279     0.975000        24352        40.00
      15.623     0.978125        24429        45.71
      16.119     0.981250        24506        53.33
      16.783     0.984375        24585        64.00
      17.279     0.985938        24623        71.11
      17.775     0.987500        24662        80.00
      18.335     0.989062        24701        91.43
      19.071     0.990625        24740       106.67
      19.871     0.992188        24779       128.00
      20.479     0.992969        24800       142.22
      21.039     0.993750        24820       160.00
      22.271     0.994531        24838       182.86
      24.143     0.995313        24857       213.33
      25.423     0.996094        24878       256.00
      25.903     0.996484        24887       284.44
      26.623     0.996875        24896       320.00
      27.327     0.997266        24906       365.71
      27.775     0.997656        24916       426.67
      29.119     0.998047        24926       512.00
      30.671     0.998242        24931       568.89
      31.391     0.998437        24935       640.00
      32.191     0.998633        24940       731.43
      33.823     0.998828        24945       853.33
      37.663     0.999023        24950      1024.00
      38.527     0.999121        24953      1137.78
      39.423     0.999219        24955      1280.00
      40.255     0.999316        24957      1462.86
      43.999     0.999414        24960      1706.67
      45.215     0.999512        24962      2048.00
      45.951     0.999561        24964      2275.56
      46.463     0.999609        24965      2560.00
      46.879     0.999658        24967      2925.71
      46.879     0.999707        24967      3413.33
      47.135     0.999756        24968      4096.00
      47.231     0.999780        24969      4551.11
      47.551     0.999805        24970      5120.00
      47.551     0.999829        24970      5851.43
      47.999     0.999854        24971      6826.67
      47.999     0.999878        24971      8192.00
      48.063     0.999890        24972      9102.22
      48.063     0.999902        24972     10240.00
      48.063     0.999915        24972     11702.86
      48.447     0.999927        24973     13653.33
      48.447     0.999939        24973     16384.00
      48.447     0.999945        24973     18204.44
      48.447     0.999951        24973     20480.00
      48.447     0.999957        24973     23405.71
      49.311     0.999963        24974     27306.67
      49.311     1.000000        24974          inf
#[Mean    =        6.393, StdDeviation   =        3.730]
#[Max     =       49.280, Total count    =        24974]
#[Buckets =           27, SubBuckets     =         2048]
----------------------------------------------------------
  30016 requests in 1.00m, 2.71MB read
Requests/sec:    500.34
Transfer/sec:     46.30KB
```

from = 3, ack = 2, rate 500, GET
```
./wrk -d 60 -t 64 -c 64 -R 500 -L -s ./wrk-scripts/stage4_GET_from3_ack2.lua http://localhost:20001
Running 1m test @ http://localhost:20001
  64 threads and 64 connections
  Thread calibration: mean lat.: 3.291ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.514ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.513ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.833ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.299ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.969ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.954ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.532ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.393ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.229ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.075ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.239ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 3.335ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 3.123ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.901ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.888ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.571ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.483ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.407ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.395ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.612ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.636ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.848ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.668ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.781ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.617ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.862ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.876ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.701ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.868ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 3.011ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.699ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.767ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 3.421ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.978ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.962ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.973ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.746ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.848ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 3.032ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.925ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 3.035ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 3.165ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 3.034ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.656ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.720ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 3.126ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.955ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 3.211ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 3.189ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.991ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.981ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 3.156ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 3.313ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 3.339ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 3.378ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 3.170ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 3.308ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 3.213ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 3.092ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.777ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.802ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.923ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.901ms, rate sampling interval: 10ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     3.08ms    1.21ms  23.73ms   81.63%
    Req/Sec     8.28     28.20   111.00     92.03%
  Latency Distribution (HdrHistogram - Recorded Latency)
 50.000%    2.84ms
 75.000%    3.51ms
 90.000%    4.36ms
 99.000%    7.55ms
 99.900%   11.95ms
 99.990%   22.91ms
 99.999%   23.74ms
100.000%   23.74ms

  Detailed Percentile spectrum:
       Value   Percentile   TotalCount 1/(1-Percentile)

       0.965     0.000000            1         1.00
       1.964     0.100000         2507         1.11
       2.233     0.200000         5001         1.25
       2.441     0.300000         7505         1.43
       2.639     0.400000         9987         1.67
       2.841     0.500000        12492         2.00
       2.947     0.550000        13732         2.22
       3.067     0.600000        14996         2.50
       3.195     0.650000        16246         2.86
       3.333     0.700000        17475         3.33
       3.505     0.750000        18740         4.00
       3.599     0.775000        19351         4.44
       3.713     0.800000        19976         5.00
       3.839     0.825000        20600         5.71
       3.987     0.850000        21230         6.67
       4.159     0.875000        21853         8.00
       4.263     0.887500        22165         8.89
       4.363     0.900000        22471        10.00
       4.515     0.912500        22783        11.43
       4.687     0.925000        23093        13.33
       4.883     0.937500        23407        16.00
       5.015     0.943750        23563        17.78
       5.155     0.950000        23717        20.00
       5.343     0.956250        23872        22.86
       5.507     0.962500        24028        26.67
       5.775     0.968750        24184        32.00
       5.947     0.971875        24264        35.56
       6.103     0.975000        24342        40.00
       6.283     0.978125        24420        45.71
       6.495     0.981250        24497        53.33
       6.775     0.984375        24575        64.00
       6.947     0.985938        24613        71.11
       7.143     0.987500        24652        80.00
       7.371     0.989062        24691        91.43
       7.651     0.990625        24730       106.67
       7.939     0.992188        24769       128.00
       8.103     0.992969        24789       142.22
       8.295     0.993750        24809       160.00
       8.639     0.994531        24829       182.86
       8.911     0.995313        24847       213.33
       9.207     0.996094        24867       256.00
       9.391     0.996484        24877       284.44
       9.559     0.996875        24886       320.00
       9.815     0.997266        24896       365.71
      10.175     0.997656        24906       426.67
      10.599     0.998047        24916       512.00
      10.871     0.998242        24921       568.89
      11.063     0.998437        24925       640.00
      11.319     0.998633        24930       731.43
      11.647     0.998828        24935       853.33
      12.015     0.999023        24940      1024.00
      12.583     0.999121        24943      1137.78
      12.783     0.999219        24945      1280.00
      12.807     0.999316        24947      1462.86
      13.127     0.999414        24950      1706.67
      13.279     0.999512        24952      2048.00
      13.767     0.999561        24954      2275.56
      14.727     0.999609        24955      2560.00
      14.855     0.999658        24956      2925.71
      15.319     0.999707        24957      3413.33
      16.431     0.999756        24958      4096.00
      18.079     0.999780        24959      4551.11
      18.751     0.999805        24960      5120.00
      18.751     0.999829        24960      5851.43
      21.487     0.999854        24961      6826.67
      21.487     0.999878        24961      8192.00
      22.911     0.999890        24962      9102.22
      22.911     0.999902        24962     10240.00
      22.911     0.999915        24962     11702.86
      23.119     0.999927        24963     13653.33
      23.119     0.999939        24963     16384.00
      23.119     0.999945        24963     18204.44
      23.119     0.999951        24963     20480.00
      23.119     0.999957        24963     23405.71
      23.743     0.999963        24964     27306.67
      23.743     1.000000        24964          inf
#[Mean    =        3.080, StdDeviation   =        1.207]
#[Max     =       23.728, Total count    =        24964]
#[Buckets =           27, SubBuckets     =         2048]
----------------------------------------------------------
  30016 requests in 1.00m, 2.71MB read
Requests/sec:    500.17
Transfer/sec:     46.29KB
```

from = 1, ack = 1, rate 500, GET
```
./wrk -d 60 -t 64 -c 64 -R 500 -L -s ./wrk-scripts/stage4_GET_from1_ack1.lua http://localhost:20001
Running 1m test @ http://localhost:20001
  64 threads and 64 connections
  Thread calibration: mean lat.: 2.644ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.981ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.858ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.912ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.140ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.224ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.912ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.020ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.264ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.959ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.842ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.005ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.754ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.097ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.148ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.994ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.099ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.204ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.192ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.633ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.791ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.875ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.306ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.966ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.896ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.196ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.140ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.950ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.345ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.939ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.925ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.293ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.339ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.916ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.521ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.982ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.876ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.420ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.851ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.391ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.367ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.980ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.161ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.900ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.023ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.120ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.110ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.894ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.189ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.980ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.246ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.136ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.153ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.916ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.923ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.956ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.971ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.865ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.944ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.802ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.956ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.978ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.019ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.437ms, rate sampling interval: 10ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     2.24ms    1.53ms  53.41ms   95.71%
    Req/Sec     8.33     28.18   111.00     91.93%
  Latency Distribution (HdrHistogram - Recorded Latency)
 50.000%    2.08ms
 75.000%    2.62ms
 90.000%    3.27ms
 99.000%    4.30ms
 99.900%   29.68ms
 99.990%   48.42ms
 99.999%   53.44ms
100.000%   53.44ms

  Detailed Percentile spectrum:
       Value   Percentile   TotalCount 1/(1-Percentile)

       0.303     0.000000            1         1.00
       1.323     0.100000         2501         1.11
       1.565     0.200000         5002         1.25
       1.749     0.300000         7510         1.43
       1.919     0.400000        10001         1.67
       2.081     0.500000        12502         2.00
       2.169     0.550000        13763         2.22
       2.261     0.600000        15021         2.50
       2.359     0.650000        16253         2.86
       2.477     0.700000        17515         3.33
       2.615     0.750000        18750         4.00
       2.699     0.775000        19382         4.44
       2.783     0.800000        20002         5.00
       2.891     0.825000        20632         5.71
       3.003     0.850000        21257         6.67
       3.133     0.875000        21873         8.00
       3.209     0.887500        22194         8.89
       3.273     0.900000        22506        10.00
       3.345     0.912500        22810        11.43
       3.419     0.925000        23124        13.33
       3.507     0.937500        23436        16.00
       3.551     0.943750        23596        17.78
       3.597     0.950000        23750        20.00
       3.657     0.956250        23914        22.86
       3.711     0.962500        24063        26.67
       3.775     0.968750        24216        32.00
       3.829     0.971875        24295        35.56
       3.873     0.975000        24376        40.00
       3.927     0.978125        24451        45.71
       3.999     0.981250        24530        53.33
       4.077     0.984375        24608        64.00
       4.123     0.985938        24649        71.11
       4.159     0.987500        24687        80.00
       4.227     0.989062        24724        91.43
       4.327     0.990625        24765       106.67
       4.455     0.992188        24802       128.00
       4.523     0.992969        24823       142.22
       4.583     0.993750        24841       160.00
       4.707     0.994531        24861       182.86
       4.843     0.995313        24880       213.33
       5.131     0.996094        24900       256.00
       5.459     0.996484        24910       284.44
       5.695     0.996875        24919       320.00
       6.059     0.997266        24929       365.71
       6.359     0.997656        24939       426.67
       7.127     0.998047        24949       512.00
       7.871     0.998242        24954       568.89
       8.703     0.998437        24958       640.00
      25.055     0.998633        24963       731.43
      27.743     0.998828        24968       853.33
      29.807     0.999023        24973      1024.00
      32.511     0.999121        24976      1137.78
      33.375     0.999219        24978      1280.00
      35.615     0.999316        24980      1462.86
      38.303     0.999414        24983      1706.67
      38.591     0.999512        24985      2048.00
      40.831     0.999561        24987      2275.56
      41.183     0.999609        24988      2560.00
      41.791     0.999658        24989      2925.71
      42.623     0.999707        24990      3413.33
      44.831     0.999756        24991      4096.00
      44.863     0.999780        24992      4551.11
      47.103     0.999805        24993      5120.00
      47.103     0.999829        24993      5851.43
      47.935     0.999854        24994      6826.67
      47.935     0.999878        24994      8192.00
      48.415     0.999890        24995      9102.22
      48.415     0.999902        24995     10240.00
      48.415     0.999915        24995     11702.86
      49.151     0.999927        24996     13653.33
      49.151     0.999939        24996     16384.00
      49.151     0.999945        24996     18204.44
      49.151     0.999951        24996     20480.00
      49.151     0.999957        24996     23405.71
      53.439     0.999963        24997     27306.67
      53.439     1.000000        24997          inf
#[Mean    =        2.237, StdDeviation   =        1.529]
#[Max     =       53.408, Total count    =        24997]
#[Buckets =           27, SubBuckets     =         2048]
----------------------------------------------------------
  30016 requests in 1.00m, 2.71MB read
Requests/sec:    500.13
Transfer/sec:     46.28KB
```