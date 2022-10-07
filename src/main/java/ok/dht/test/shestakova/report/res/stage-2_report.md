### **STAGE 2**

#### **Нагрузочное тестирование**
##### PUT-запросы с кол-вом соединений 64

Для хранения задач ExecutorService была использована очередь (FIFO) ArrayBlockingQueue, а также на основе LinkedBlockedDeque был реализован стек (LIFO).  

ArrayBlockingQueue  
(блокирующая очередь на основе массива, а не связного списка ( LinkedBlockingQueue) была выбрана для того, чтобы не хранить дополнительную информацию, как указатель на следующий узел, и не аллоцировать каждый раз новые узлы при добавлении в очередь. 
Как написано в документации, "Связанные очереди обычно имеют более высокую пропускную способность, чем очереди на основе массива, но менее предсказуемую производительность в большинстве параллельных приложений". 
С другой стороны очередь на основе связного списка использует два отдельных лока для добавления узла в конец и взятия узла из начала, в связи с чем производительность может расти благодаря возможности параллельного исполнения перечисленных операций.
Но хоть в этом аспекте ArrayBlockingQueue проигрывает, всё же она гарантирует отсутствие перезаписи записей)   

Размер очереди варьировался следующим образом: 32, 64, 128, 256, 512. В приведённой таблице видно, что при размере очереди, меньшем, чем количество потоков/соединений, производительность сервиса сильно падает, что связано с тем, что потокам приходится бороться за места в очереди, которых в два раза меньше, чем потоков. Начиная с размера очереди, равного 64, ситуация стабилизируется: достигается бОльшая производительность за счет того, что теперь каждому потоку проще отдать свою задачу в очередь, тк теоретически на каждый поток приходится 1/2/4/8 мест в очереди.  
При размере очереди 128 и 256 достигается наивысшее значение rate = 70000 при среднем значении latency < 1 ms, для 90% запросов latency = ~1.17 ms.  
При увеличении размера очереди до 512 наблюдается снижение производительности: при том же rate, что и при размерах очереди 128 и 256 вылетают ошибки таймаута, в связи с чем оптимальный для этого размера очереди rate равен 60000.  
В сравнении с синхронным вариантом сервиса, в котором достигалось значение rate = 40000 при avg latency 0.86 ms вынос пула воркеров с освобождением селекторов дают хороший прирост производительности.

| Queue capacity | Кол-во connections = threads | Optimal rate |  Latency avg  | Latency per 90% | Max latency  |
|:--------------:|:----------------------------:|:------------:|:-------------:|:---------------:|:------------:|
|       32       |              64              |     4000     |    1.34 ms    |     2.27 ms     |   10.16 ms   |
|       64       |              64              |    60000     |   618.33 us   |     1.05 ms     |   6.61 ms    |
|      128       |              64              |    70000     |   757.90 us   |     1.18 ms     |   7.55 ms    |
|    **256**     |            **64**            |  **70000**   | **759.96 us** |   **1.17 ms**   | **13.46 ms** |
|      512       |              64              |    60000     |   617.78 us   |     1.04 ms     |   21.02 ms   |

Стек на LinkedBlockedDeque (переопределены конструктор и методы offer, put, add для соответствия LIFO) 
Для стека были проведены аналогичные замеры. Как можно заметить, если сравнить вышеприведенную таблицу для FIFO и нижеприведенную таблицу для LIFO, 
можно заметить, что пропускная способность очереди выше, чем у стека, что, вероятно, связано с используемыми коллекциями. Кроме того верхняя граница latency для стека значительно (до 6-7 раз) больше верхней границы latency для очереди.  

| Queue capacity | Кол-во connections = threads | Optimal rate | Latency avg | Latency per 90% | Max latency |
|:--------------:|:----------------------------:|:------------:|:-----------:|:---------------:|:-----------:|
|       32       |              64              |     2000     |   1.00 ms   |     1.46 ms     |   7.49 ms   |
|       64       |              64              |    60000     |  632.56 ms  |     1.06 ms     |  13.26 ms   |
|      128       |              64              |    50000     |  620.60 ms  |     1.02 ms     |  15.77 ms   |
|      256       |              64              |    60000     |  721.34 us  |     1.07 ms     |  82.37 ms   |

Ниже представлен вывод для оптимального варианта - очереди размером 256.  

`wrk -t 64 -c 64 -d 30 -R 70000 -s put.lua -L http://localhost:19234`  
Running 30s test @ http://localhost:19234  
64 threads and 64 connections  
Thread calibration: mean lat.: 0.750ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.753ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.751ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.752ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.757ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.754ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.756ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.753ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.753ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.752ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.757ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.756ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.755ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.755ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.754ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.751ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.748ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.753ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.755ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.754ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.754ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.757ms, rate sampling interval: 10ms   
Thread calibration: mean lat.: 0.750ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.752ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.751ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.757ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.755ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.764ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.756ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.750ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.750ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.751ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.751ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.756ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.756ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.754ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.753ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.751ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.758ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.771ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.754ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.757ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.757ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.750ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.751ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.752ms, rate sampling interval: 10ms   
Thread calibration: mean lat.: 0.756ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.755ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.751ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.750ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.754ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.758ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.751ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.758ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.757ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.754ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.757ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.754ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.758ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.754ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.751ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.751ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.753ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.752ms, rate sampling interval: 10ms  
Thread Stats   Avg      Stdev     Max   +/- Stdev  
Latency   759.96us  386.43us  13.46ms   73.04%  
Req/Sec     1.18k    71.51     2.33k    75.16%  
Latency Distribution (HdrHistogram - Recorded Latency)  
50.000%  748.00us  
75.000%    1.01ms  
90.000%    1.17ms  
99.000%    1.30ms  
99.900%    4.87ms  
99.990%    9.46ms  
99.999%   10.82ms  
100.000%   13.46ms

Detailed Percentile spectrum:  
Value   Percentile   TotalCount 1/(1-Percentile)

       0.037     0.000000            2         1.00
       0.325     0.100000       140604         1.11
       0.430     0.200000       280520         1.25
       0.536     0.300000       420414         1.43
       0.642     0.400000       560633         1.67
       0.748     0.500000       699954         2.00
       0.801     0.550000       769787         2.22
       0.854     0.600000       839661         2.50
       0.908     0.650000       910320         2.86
       0.961     0.700000       980222         3.33
       1.014     0.750000      1049969         4.00
       1.041     0.775000      1085641         4.44
       1.067     0.800000      1119870         5.00
       1.094     0.825000      1155268         5.71
       1.120     0.850000      1189536         6.67
       1.147     0.875000      1224932         8.00
       1.160     0.887500      1242180         8.89
       1.174     0.900000      1260563        10.00
       1.187     0.912500      1277788        11.43
       1.200     0.925000      1294853        13.33
       1.213     0.937500      1312024        16.00
       1.220     0.943750      1321138        17.78
       1.227     0.950000      1329969        20.00
       1.234     0.956250      1338578        22.86
       1.241     0.962500      1347085        26.67
       1.249     0.968750      1356300        32.00
       1.253     0.971875      1360641        35.56
       1.257     0.975000      1364810        40.00
       1.262     0.978125      1369444        45.71
       1.267     0.981250      1373281        53.33
       1.274     0.984375      1377564        64.00
       1.279     0.985938      1379965        71.11
       1.285     0.987500      1382120        80.00
       1.292     0.989062      1384275        91.43
       1.302     0.990625      1386419       106.67
       1.319     0.992188      1388520       128.00
       1.333     0.992969      1389579       142.22
       1.361     0.993750      1390630       160.00
       1.449     0.994531      1391717       182.86
       1.678     0.995313      1392807       213.33
       2.007     0.996094      1393900       256.00
       2.183     0.996484      1394447       284.44
       2.381     0.996875      1394997       320.00
       2.617     0.997266      1395543       365.71
       2.895     0.997656      1396087       426.67
       3.277     0.998047      1396634       512.00
       3.511     0.998242      1396908       568.89
       3.803     0.998437      1397180       640.00
       4.147     0.998633      1397455       731.43
       4.499     0.998828      1397727       853.33
       4.915     0.999023      1398003      1024.00
       5.091     0.999121      1398141      1137.78
       5.311     0.999219      1398273      1280.00
       5.527     0.999316      1398410      1462.86
       5.731     0.999414      1398547      1706.67
       6.083     0.999512      1398683      2048.00
       6.287     0.999561      1398753      2275.56
       6.595     0.999609      1398820      2560.00
       6.887     0.999658      1398888      2925.71
       7.331     0.999707      1398957      3413.33
       7.839     0.999756      1399025      4096.00
       8.103     0.999780      1399059      4551.11
       8.359     0.999805      1399093      5120.00
       8.663     0.999829      1399127      5851.43
       8.943     0.999854      1399163      6826.67
       9.223     0.999878      1399196      8192.00
       9.343     0.999890      1399213      9102.22
       9.503     0.999902      1399230     10240.00
       9.631     0.999915      1399247     11702.86
       9.735     0.999927      1399265     13653.33
       9.839     0.999939      1399284     16384.00
       9.879     0.999945      1399290     18204.44
       9.983     0.999951      1399298     20480.00
      10.071     0.999957      1399307     23405.71
      10.143     0.999963      1399315     27306.67
      10.271     0.999969      1399324     32768.00
      10.335     0.999973      1399328     36408.89
      10.375     0.999976      1399332     40960.00
      10.447     0.999979      1399338     46811.43
      10.511     0.999982      1399341     54613.33
      10.575     0.999985      1399346     65536.00
      10.671     0.999986      1399349     72817.78
      10.671     0.999988      1399349     81920.00
      10.823     0.999989      1399352     93622.86
      10.903     0.999991      1399354    109226.67
      11.103     0.999992      1399356    131072.00
      11.223     0.999993      1399357    145635.56
      11.327     0.999994      1399358    163840.00
      11.575     0.999995      1399359    187245.71
      11.679     0.999995      1399360    218453.33
      11.903     0.999996      1399361    262144.00
      12.335     0.999997      1399362    291271.11
      12.335     0.999997      1399362    327680.00
      12.575     0.999997      1399363    374491.43
      12.575     0.999998      1399363    436906.67
      12.767     0.999998      1399364    524288.00
      12.767     0.999998      1399364    582542.22
      12.767     0.999998      1399364    655360.00
      13.271     0.999999      1399365    748982.86
      13.271     0.999999      1399365    873813.33
      13.271     0.999999      1399365   1048576.00
      13.271     0.999999      1399365   1165084.44
      13.271     0.999999      1399365   1310720.00
      13.463     0.999999      1399366   1497965.71
      13.463     1.000000      1399366          inf

[Mean    =        0.760, StdDeviation   =        0.386]

[Max     =       13.456, Total count    =      1399366]

[Buckets =           27, SubBuckets     =         2048]

----------------------------------------------------------
2099713 requests in 29.99s, 134.16MB read  
Requests/sec:  70006.43  
Transfer/sec:      4.47MB  

##### GET-запросы на заполненной (1.4 Гб) БД

Размер очереди варьировался аналогично тестированию на put-запросах: 32, 64, 128, 256, 512. 
Как и для put-запросов, в приведённой ниже таблице видно, что при размере очереди, меньшем, чем количество потоков/соединений, производительность сервиса сильно падает, а с учётом того, что get-запросы сами по себе отрабатывают гораздо медленнее put-запросов, что связано с необходимостью искать ключи на диске, открывая и итерируясь по большому количеству файлов, то в нашей ситуации с размером очереди 32 сервис вообще перестаёт справляться с нагрузкой: даже при rate = 100 вылетают ошибки таймаута.  
При увеличении размера очереди сервис справляется с rate = 800.  
При размере очереди 128, 256 и 512 достигается наивысшее значение rate = 1000 при среднем значении latency ~ 14-19 ms, для 90% запросов latency = ~22-28 ms.  
В сравнении с синхронным вариантом сервиса, в котором достигалось значение rate = 100 при avg latency ~6 ms, а значение rate = 1000 при avg latency ~18 ms, вынос пула воркеров с освобождением селекторов не дают сильного прироста производительности, что, вероятно связано с тяжеловесностью обработки get-запросов, улучшать которую следует методами, связанными с непосредственно с нашим Storage (например, если бы мы клали и хотели получить случайный ключи, а не последовательно генерирующиеся, то помог бы фильтр Блума. Кроме того, возможно, стоит запускать фоновый компакшн, что, опять же, позволит не "бегать" с бинарным поиском по большому количеству маленьких файлов). Кроме того причина отсутствия значительного улучшения производительности кроется в локах (см. результаты профилирования).

Попробовала увеличить размер файлов с 1 мб до 20, 100 мб, тк думала, что это должно помочь с решением проблемы долгих get-запросов 
(тк не пришлось бы ходить бинарным поиском по куче маленьких файлов), но, попробовав, увидела, что latency от этого, наоборот, растет (для 20 мб: 
avg = 19.11 ms, 90% = 33.25 ms, max = 76.29 ms). Рост максимального значения ~ в 1.5 раза обусловлен тем, что теперь сервису сложнее искать ключи на диске в больших файлах 
из-за значительного увеличения времени бинарного поиска в одном файле. Получается, сервису легче и быстрее найти ключ в небольшом файле, даже если он переберёт много таких файлов.  
(а кроме того для put-запросов возрасла длительность фонового flush -> max latency также значитально увеличилась).


| Queue capacity | Кол-во connections = threads | Optimal rate | Latency avg  | Latency per 90% | Max latency  |
|:--------------:|:----------------------------:|:------------:|:------------:|:---------------:|:------------:|
|       32       |              64              |    100(*)    |   23.93 ms   |    40.26 ms     |   87.74 ms   |
|       64       |              64              |     800      |   12.67 ms   |    20.59 ms     |   83.33 ms   |
|      128       |              64              |     1000     |   18.96 ms   |    28.59 ms     |  222.72 ms   |
|    **256**     |            **64**            |   **1000**   | **14.68 ms** |  **22.78 ms**   | **58.56 ms** |
|      512       |              64              |     1000     |   17.16 ms   |     27.5 ms     |   76.74 ms   |

(*) При этом вылетают ошибки таймаута, а при уменьшении/увеличении rate latency растет, ошибки таймаута остаются.

Стек на LinkedBlockedDeque  
Для стека были проведены аналогичные замеры. Как можно заметить, если сравнить вышеприведенную таблицу для FIFO и нижеприведенную таблицу для LIFO, 
можно заметить, что при схожей пропускной способности и схожих значениях параметра avg latency, верхняя граница latency, а также latency для 90% запросов для стека заметно больше верхней границы latency для очереди.  

| Queue capacity | Кол-во connections = threads | Optimal rate | Latency avg | Latency per 90% | Max latency |
|:--------------:|:----------------------------:|:------------:|:-----------:|:---------------:|:-----------:|
|       32       |              64              |    100(*)    |  28.25 ms   |    59.36 ms     |  106.05 ms  |
|       64       |              64              |     1000     |  16.63 ms   |    35.97 ms     |  187.39 ms  |
|      128       |              64              |     1000     |  21.79 ms   |    48.67 ms     |  194.30 ms  |
|      256       |              64              |     1000     |  12.27 ms   |    24.75 ms     |  159.36 ms  |

(*) При этом вылетают ошибки таймаута, а при уменьшении/увеличении rate latency растет, ошибки таймаута остаются

Ниже представлен вывод для оптимального варианта - очереди размером 256.  

`wrk -t 64 -c 64 -d 30 -R 1000 -s get.lua -L http://localhost:19234`  
Running 30s test @ http://localhost:19234  
64 threads and 64 connections  
Thread calibration: mean lat.: 10.118ms, rate sampling interval: 34ms  
Thread calibration: mean lat.: 10.022ms, rate sampling interval: 35ms  
Thread calibration: mean lat.: 12.096ms, rate sampling interval: 43ms    
Thread calibration: mean lat.: 11.012ms, rate sampling interval: 39ms  
Thread calibration: mean lat.: 10.396ms, rate sampling interval: 38ms  
Thread calibration: mean lat.: 10.097ms, rate sampling interval: 34ms  
Thread calibration: mean lat.: 10.055ms, rate sampling interval: 34ms  
Thread calibration: mean lat.: 10.005ms, rate sampling interval: 34ms  
Thread calibration: mean lat.: 9.982ms, rate sampling interval: 34ms  
Thread calibration: mean lat.: 20.159ms, rate sampling interval: 70ms  
Thread calibration: mean lat.: 19.372ms, rate sampling interval: 70ms  
Thread calibration: mean lat.: 16.992ms, rate sampling interval: 63ms  
Thread calibration: mean lat.: 18.796ms, rate sampling interval: 71ms  
Thread calibration: mean lat.: 15.227ms, rate sampling interval: 58ms  
Thread calibration: mean lat.: 15.297ms, rate sampling interval: 58ms  
Thread calibration: mean lat.: 15.401ms, rate sampling interval: 58ms  
Thread calibration: mean lat.: 16.741ms, rate sampling interval: 60ms  
Thread calibration: mean lat.: 18.963ms, rate sampling interval: 59ms  
Thread calibration: mean lat.: 15.604ms, rate sampling interval: 58ms  
Thread calibration: mean lat.: 24.832ms, rate sampling interval: 82ms  
Thread calibration: mean lat.: 23.425ms, rate sampling interval: 81ms  
Thread calibration: mean lat.: 22.438ms, rate sampling interval: 77ms  
Thread calibration: mean lat.: 22.100ms, rate sampling interval: 76ms  
Thread calibration: mean lat.: 20.086ms, rate sampling interval: 70ms  
Thread calibration: mean lat.: 21.495ms, rate sampling interval: 70ms  
Thread calibration: mean lat.: 19.794ms, rate sampling interval: 64ms  
Thread calibration: mean lat.: 22.803ms, rate sampling interval: 82ms  
Thread calibration: mean lat.: 15.606ms, rate sampling interval: 68ms  
Thread calibration: mean lat.: 15.823ms, rate sampling interval: 69ms  
Thread calibration: mean lat.: 17.350ms, rate sampling interval: 68ms  
Thread calibration: mean lat.: 15.583ms, rate sampling interval: 66ms  
Thread calibration: mean lat.: 18.643ms, rate sampling interval: 70ms  
Thread calibration: mean lat.: 18.652ms, rate sampling interval: 70ms  
Thread calibration: mean lat.: 16.000ms, rate sampling interval: 68ms  
Thread calibration: mean lat.: 18.941ms, rate sampling interval: 70ms  
Thread calibration: mean lat.: 17.514ms, rate sampling interval: 72ms  
Thread calibration: mean lat.: 14.169ms, rate sampling interval: 64ms  
Thread calibration: mean lat.: 16.075ms, rate sampling interval: 64ms  
Thread calibration: mean lat.: 15.355ms, rate sampling interval: 68ms  
Thread calibration: mean lat.: 14.839ms, rate sampling interval: 64ms  
Thread calibration: mean lat.: 15.903ms, rate sampling interval: 74ms  
Thread calibration: mean lat.: 15.177ms, rate sampling interval: 68ms  
Thread calibration: mean lat.: 17.135ms, rate sampling interval: 69ms  
Thread calibration: mean lat.: 17.995ms, rate sampling interval: 68ms  
Thread calibration: mean lat.: 13.208ms, rate sampling interval: 53ms  
Thread calibration: mean lat.: 15.140ms, rate sampling interval: 57ms  
Thread calibration: mean lat.: 14.881ms, rate sampling interval: 57ms  
Thread calibration: mean lat.: 18.561ms, rate sampling interval: 71ms  
Thread calibration: mean lat.: 18.903ms, rate sampling interval: 68ms  
Thread calibration: mean lat.: 17.221ms, rate sampling interval: 70ms  
Thread calibration: mean lat.: 20.438ms, rate sampling interval: 72ms  
Thread calibration: mean lat.: 21.344ms, rate sampling interval: 73ms  
Thread calibration: mean lat.: 19.464ms, rate sampling interval: 64ms  
Thread calibration: mean lat.: 20.952ms, rate sampling interval: 74ms  
Thread calibration: mean lat.: 15.468ms, rate sampling interval: 58ms  
Thread calibration: mean lat.: 14.028ms, rate sampling interval: 58ms  
Thread calibration: mean lat.: 17.552ms, rate sampling interval: 61ms  
Thread calibration: mean lat.: 14.267ms, rate sampling interval: 56ms  
Thread calibration: mean lat.: 14.850ms, rate sampling interval: 60ms  
Thread calibration: mean lat.: 13.150ms, rate sampling interval: 50ms  
Thread calibration: mean lat.: 13.756ms, rate sampling interval: 58ms  
Thread calibration: mean lat.: 13.981ms, rate sampling interval: 53ms  
Thread calibration: mean lat.: 13.943ms, rate sampling interval: 62ms  
Thread calibration: mean lat.: 18.823ms, rate sampling interval: 68ms  
Thread Stats   Avg      Stdev     Max   +/- Stdev  
Latency    14.68ms    8.30ms  58.53ms   86.27%  
Req/Sec    15.34      8.14    38.00     69.31%  
Latency Distribution (HdrHistogram - Recorded Latency)  
50.000%   12.67ms  
75.000%   17.50ms  
90.000%   22.78ms  
99.000%   47.46ms  
99.900%   57.12ms  
99.990%   58.21ms  
99.999%   58.56ms  
100.000%   58.56ms  

Detailed Percentile spectrum:  
Value   Percentile   TotalCount 1/(1-Percentile)  

       3.807     0.000000            1         1.00
       6.795     0.100000         1999         1.11
       7.619     0.200000         4000         1.25
      10.487     0.300000         5995         1.43
      11.727     0.400000         8010         1.67
      12.671     0.500000         9997         2.00
      13.239     0.550000        10998         2.22
      14.719     0.600000        11991         2.50
      16.447     0.650000        13008         2.86
      17.039     0.700000        14004         3.33
      17.503     0.750000        15011         4.00
      17.759     0.775000        15506         4.44
      18.031     0.800000        15988         5.00
      18.415     0.825000        16492         5.71
      18.927     0.850000        16986         6.67
      19.871     0.875000        17492         8.00
      21.327     0.887500        17737         8.89
      22.783     0.900000        17986        10.00
      23.823     0.912500        18239        11.43
      25.919     0.925000        18486        13.33
      28.767     0.937500        18736        16.00
      30.559     0.943750        18859        17.78
      33.183     0.950000        18984        20.00
      35.359     0.956250        19110        22.86
      37.983     0.962500        19234        26.67
      40.063     0.968750        19360        32.00
      40.767     0.971875        19421        35.56
      41.375     0.975000        19484        40.00
      42.271     0.978125        19547        45.71
      42.943     0.981250        19609        53.33
      44.287     0.984375        19672        64.00
      45.439     0.985938        19703        71.11
      46.527     0.987500        19737        80.00
      47.103     0.989062        19766        91.43
      47.775     0.990625        19796       106.67
      49.087     0.992188        19827       128.00
      49.631     0.992969        19844       142.22
      50.175     0.993750        19859       160.00
      50.495     0.994531        19874       182.86
      51.071     0.995313        19890       213.33
      52.095     0.996094        19906       256.00
      52.767     0.996484        19913       284.44
      53.343     0.996875        19921       320.00
      53.471     0.997266        19931       365.71
      53.695     0.997656        19937       426.67
      54.335     0.998047        19944       512.00
      54.495     0.998242        19948       568.89
      54.879     0.998437        19952       640.00
      55.871     0.998633        19956       731.43
      56.863     0.998828        19960       853.33
      57.183     0.999023        19964      1024.00
      57.631     0.999121        19967      1137.78
      57.663     0.999219        19968      1280.00
      57.759     0.999316        19970      1462.86
      57.855     0.999414        19973      1706.67
      57.919     0.999512        19974      2048.00
      57.951     0.999561        19975      2275.56
      58.015     0.999609        19977      2560.00
      58.015     0.999658        19977      2925.71
      58.079     0.999707        19978      3413.33
      58.111     0.999756        19979      4096.00
      58.111     0.999780        19979      4551.11
      58.175     0.999805        19980      5120.00
      58.175     0.999829        19980      5851.43
      58.207     0.999854        19981      6826.67
      58.207     0.999878        19981      8192.00
      58.207     0.999890        19981      9102.22
      58.495     0.999902        19982     10240.00
      58.495     0.999915        19982     11702.86
      58.495     0.999927        19982     13653.33
      58.495     0.999939        19982     16384.00
      58.495     0.999945        19982     18204.44
      58.559     0.999951        19983     20480.00
      58.559     1.000000        19983          inf

[Mean    =       14.683, StdDeviation   =        8.299]

[Max     =       58.528, Total count    =        19983]

[Buckets =           27, SubBuckets     =         2048]

----------------------------------------------------------  
30016 requests in 30.03s, 2.37MB read  
Requests/sec:    999.39  
Transfer/sec:     80.74KB  


#### **Профилирование (CPU, alloc, lock) с помощью async-profiler**
##### PUT-запросы
**CPU**


|                                                                      Sync                                                                      |                                                                          Async                                                                          |
|:----------------------------------------------------------------------------------------------------------------------------------------------:|:-------------------------------------------------------------------------------------------------------------------------------------------------------:|
| ![Иллюстрация к проекту](https://github.com/Anilochka/2022-highload-dht/blob/main/src/main/java/ok/dht/test/shestakova/report/jpg/put_cpu.jpg) | ![Иллюстрация к проекту](https://github.com/Anilochka/2022-highload-dht/blob/stage2/src/main/java/ok/dht/test/shestakova/report/jpg/stage2/put_cpu.jpg) |



Относительно синхронной версии помимо появившегося ThreadPoolExecutor-а, занимающего 67% (включая работу с блокирующей очередью, в которой взятие задачи занимает 12%), также значительно увеличилось количество нативного кода и syscall-ов из-за локов, появившихся в связи с увеличением потоков, желающих работать с сервисом (присутствуют локи после вызова метода offer, вставляющего задачу в очередь, после метода take, берущего из очереди задачу. Виден механизм работы блокирующей очереди, он забирает около 20%, но увеличивает пропускную способность сервера за счет освобождения селекторов, в связи с чем можно считать это не сильно критическим).  
Менее 2% времени процессора занимают локи, присутствующие в реализации upsert-метода dao, что не критично.


**alloc**

|                                                                             Sync                                                                             |                                                                                       Async                                                                                        |
|:------------------------------------------------------------------------------------------------------------------------------------------------------------:|:----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------:|
|       ![Иллюстрация к проекту](https://github.com/Anilochka/2022-highload-dht/blob/main/src/main/java/ok/dht/test/shestakova/report/jpg/put_alloc.jpg)       |             ![Иллюстрация к проекту](https://github.com/Anilochka/2022-highload-dht/blob/stage2/src/main/java/ok/dht/test/shestakova/report/jpg/stage2/put_alloc.jpg)              |

По аллокациям сильных изменений помимо появления ThreadPoolExecutor не наблюдается: добавились аллокации на локи (менее 1% на лок в upsert).

**lock**

|                                                                             Sync                                                                              |                                                                          Async                                                                           |
|:-------------------------------------------------------------------------------------------------------------------------------------------------------------:|:--------------------------------------------------------------------------------------------------------------------------------------------------------:|
| ![Иллюстрация к проекту](https://github.com/Anilochka/2022-highload-dht/blob/stage2/src/main/java/ok/dht/test/shestakova/report/jpg/stage2/put_lock_sync.jpg) | ![Иллюстрация к проекту](https://github.com/Anilochka/2022-highload-dht/blob/stage2/src/main/java/ok/dht/test/shestakova/report/jpg/stage2/put_lock.jpg) |

В синхронном варианте сервера присутствует единственный лок - при вызове метода upsert, когда мы получаем состояние (State) под write-локом.  

В асинхронном варианте:
ThreadPoolExecutor:  
В асинхронном варианте вышеупомянутый лок занимает ~83%, остальное время занимают локи, необходимые для обеспечения асинхронности с использованием блокирующей очереди:
7.5% - блокировка на взятие задач из очереди.

SelectorThread:  
И около 10% забирает synchronized метод process из пакета one.nio.net, вызывающий методы чтения и записи в сокет,
причём 3.5% идёт на блокировку очереди при добавлении в неё задач.  
Таким образом, видно, что добавление пула воркеров разгрузило селекторы, забрав на себя локи, ставящиеся на блокирующую очередь для асинхронного взятия задач, а также локи, ставящиеся для выполнения этих задач (при работе методов БД).

Теперь на селекторах остались только локи для работы с сокетами и локи для добавления задач в блокирующую очередь пула воркеров. Таким образом, мы сняли с селектора тяжеловесный лок для метода upsert,
из чего становится ясно, что за счёт этого производительность сервера для put-запросов значительно увеличилась в сравнении с синхронным вариантом. 
 

##### GET-запросы
**CPU**

|                                                                      Sync                                                                      |                                                                          Async                                                                          |
|:----------------------------------------------------------------------------------------------------------------------------------------------:|:-------------------------------------------------------------------------------------------------------------------------------------------------------:|
| ![Иллюстрация к проекту](https://github.com/Anilochka/2022-highload-dht/blob/main/src/main/java/ok/dht/test/shestakova/report/jpg/get_cpu.jpg) | ![Иллюстрация к проекту](https://github.com/Anilochka/2022-highload-dht/blob/stage2/src/main/java/ok/dht/test/shestakova/report/jpg/stage2/get_cpu.jpg) |


Как и в put-запросах заметно, что ThreadPoolExecutor забрал у SelectorThread работу с БД, чем освободил его.  
Заметно меньше стало видно на графике syscall-ов, они перешли на селектор, а 98% - работа с БД - висят уже на нашем пуле воркеров, где все еще более 80% занимаеет поиск ключа по диску.

**alloc**

|                                                                       Sync                                                                       |                                                                           Async                                                                           |
|:------------------------------------------------------------------------------------------------------------------------------------------------:|:---------------------------------------------------------------------------------------------------------------------------------------------------------:|
| ![Иллюстрация к проекту](https://github.com/Anilochka/2022-highload-dht/blob/main/src/main/java/ok/dht/test/shestakova/report/jpg/get_alloc.jpg) | ![Иллюстрация к проекту](https://github.com/Anilochka/2022-highload-dht/blob/stage2/src/main/java/ok/dht/test/shestakova/report/jpg/stage2/get_alloc.jpg) |

В контексте аллокаций графики синхронного и асинхронного вариантов практически не различаются.


**lock**

|                                                                             Sync                                                                              |                                                                          Async                                                                           |
|:-------------------------------------------------------------------------------------------------------------------------------------------------------------:|:--------------------------------------------------------------------------------------------------------------------------------------------------------:|
| ![Иллюстрация к проекту](https://github.com/Anilochka/2022-highload-dht/blob/stage2/src/main/java/ok/dht/test/shestakova/report/jpg/stage2/get_lock_sync.jpg) | ![Иллюстрация к проекту](https://github.com/Anilochka/2022-highload-dht/blob/stage2/src/main/java/ok/dht/test/shestakova/report/jpg/stage2/get_lock.jpg) |

В синхронном варианте сервера флеймграф локов пустой, что связано с тем, что при вызове профайлера был выставлен параметр 20 наносекунд, в связи с чем на графике отображаются только локи, 
длящиеся дольше этого промежутка времени (хотя при запуске с параметром 1 нс флеймнраф все равно пустой -> для синхронного варианта сервера все же на гет-запросах не присутствует локов длиннее 1 нс).

В асинхронном варианте:
ThreadPoolExecutor:  
74% времени уходит на блокировку очереди для взятия из неё задач.  
Менее 1% уходит на локи, ставящиеся в методе get нашего Dao. 

SelectorThread:  
И около 25% забирает synchronized метод process из пакета one.nio.net, вызывающий методы чтения и записи в сокет, 
причём 2.2% идёт на блокировку очереди при добавлении в неё задач.  
Как и для put-запросов, здесь видно, что добавление пула воркеров разгрузило селекторы, забрав на себя локи, ставящиеся на блокирующую очередь для асинхронного взятия задач, а также локи, ставящиеся для выполнения этих задач (при работе методов БД).
Теперь на селекторах остались только локи для работы с сокетами и локи для добавления задач в блокирующую очередь пула воркеров.
 
Если сравнивать результаты с результатами синхронной реализации, можно заметить, что особого прироста производительности нет, 
и теперь становится яснее, почему: освободив селекторы от быстрого лока метода get (менее 1%), мы добавили работу с блокирующей очередью (2.5%), 
тем самым загрузив селектор чуть больше, чем в синхронном варианте.  
Кроме того низкую производительность обработки get-запросов теперь можно объяснить еще и со стороны локов, тк 74% времени локов уходит у воркеров только на взятие задачи из очереди, помимо чего get-запросы и так тяжеловесны в нашей реализации.  


Для оптимизации однозначно стоит рассмотреть поиск ключа на диске: подумать о фоновом компакшене, а также о других способах, которые позволили бы меньше "ходить" по диску.  
Также неплохо было бы самим отправлять для передачи в сокет ответы на запросы в виде массива байт, а не в виде Response, который далее будет превращен обратно в массив байт. Также было бы здорово убрать работу со строками, в виде которых мы получаем id, а затем через массив байт превращаем в MemorySegment для работы с БД.  
Для ускорения работы пула воркеров можно поискать более оптимальную структуру данных для очереди задач, которая бы затрачивала меньше времени на блокировки, что особенно актуально для get-запросов, в которых появление очереди задач не дало особого выигрыша в производительности из-за появившихся блокировок взятия/размещения в очереди задач.  
