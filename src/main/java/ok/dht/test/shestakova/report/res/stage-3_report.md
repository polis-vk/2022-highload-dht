### **STAGE 3**

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

| Количество нод | Optimal rate | Latency avg | Latency per 90% | Max latency |
|:--------------:|:------------:|:-----------:|:---------------:|:-----------:|
|       1        |    70000     |  759.96 us  |     1.17 ms     |  13.46 ms   |
|       2        |    20000     |  744.21 us  |     1.13 ms     |  22.70 ms   |
|       3        |    30000     |   1.52 ms   |     1.31 ms     |  162.94 ms  |
|       4        |    30000     |   1.15 ms   |     1.25 ms     |  125.95 ms  |
|       5        |    30000     |   2.12 ms   |     2.34 ms     |  223.87 ms  |
|       10       |    30000     |   1.03 ms   |     1.26 ms     |  203.26 ms  |

Ниже представлен вывод для варианта с количеством нод 3.  

`wrk -t 64 -c 64 -d 30 -R 30000 -s put.lua -L http://localhost:35319`  
Running 30s test @ http://localhost:35319  
64 threads and 64 connections  
Thread calibration: mean lat.: 0.726ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.757ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.788ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.804ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.740ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.766ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.774ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.774ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.752ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.729ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.773ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.823ms, rate sampling interval: 10ms   
Thread calibration: mean lat.: 0.777ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.775ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.787ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.769ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.731ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.806ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.801ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.754ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.748ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.812ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.793ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.771ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.789ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.814ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.772ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.795ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.759ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.751ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.752ms, rate sampling interval: 10ms   
Thread calibration: mean lat.: 0.739ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.764ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.741ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.788ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.772ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.745ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.788ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.769ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.779ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.812ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.785ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.724ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.736ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.769ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.772ms, rate sampling interval: 10ms   
Thread calibration: mean lat.: 0.733ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.731ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.774ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.747ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.773ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.787ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.811ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.779ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.793ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.790ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.773ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.754ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.759ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.759ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.748ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.769ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.736ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.787ms, rate sampling interval: 10ms  
Thread Stats   Avg      Stdev     Max   +/- Stdev  
Latency     1.52ms    5.98ms 162.82ms   98.07%  
Req/Sec   490.92     64.87     1.78k    83.10%  
Latency Distribution (HdrHistogram - Recorded Latency)  
50.000%    0.85ms  
75.000%    1.11ms  
90.000%    1.31ms  
99.000%   23.28ms  
99.900%   88.89ms  
99.990%  150.91ms  
99.999%  160.77ms  
100.000%  162.94ms  

Detailed Percentile spectrum:  
Value   Percentile   TotalCount 1/(1-Percentile)  

       0.102     0.000000            1         1.00
       0.406     0.100000        60140         1.11
       0.525     0.200000       120090         1.25
       0.635     0.300000       180066         1.43
       0.743     0.400000       239869         1.67
       0.851     0.500000       300035         2.00
       0.904     0.550000       329876         2.22
       0.957     0.600000       359878         2.50
       1.010     0.650000       390074         2.86
       1.062     0.700000       419899         3.33
       1.114     0.750000       449686         4.00
       1.140     0.775000       464611         4.44
       1.166     0.800000       479690         5.00
       1.193     0.825000       494982         5.71
       1.221     0.850000       509623         6.67
       1.257     0.875000       524593         8.00
       1.280     0.887500       532091         8.89
       1.309     0.900000       539599        10.00
       1.349     0.912500       547105        11.43
       1.406     0.925000       554520        13.33
       1.506     0.937500       562029        16.00
       1.592     0.943750       565747        17.78
       1.729     0.950000       569489        20.00
       1.950     0.956250       573233        22.86
       2.309     0.962500       576989        26.67
       2.981     0.968750       580722        32.00
       3.551     0.971875       582590        35.56
       4.467     0.975000       584468        40.00
       5.839     0.978125       586342        45.71
       7.951     0.981250       588213        53.33
      10.999     0.984375       590083        64.00
      13.247     0.985938       591021        71.11
      16.375     0.987500       591956        80.00
      20.463     0.989062       592894        91.43
      25.071     0.990625       593836       106.67
      29.983     0.992188       594768       128.00
      32.383     0.992969       595235       142.22
      35.167     0.993750       595707       160.00
      38.783     0.994531       596176       182.86
      43.487     0.995313       596641       213.33
      49.855     0.996094       597109       256.00
      53.311     0.996484       597344       284.44
      56.799     0.996875       597577       320.00
      60.287     0.997266       597811       365.71
      64.223     0.997656       598047       426.67
      69.759     0.998047       598282       512.00
      73.407     0.998242       598398       568.89
      76.735     0.998437       598514       640.00
      80.639     0.998633       598630       731.43
      84.927     0.998828       598747       853.33
      89.535     0.999023       598867      1024.00
      92.415     0.999121       598923      1137.78
      97.599     0.999219       598981      1280.00
     106.303     0.999316       599040      1462.86
     114.431     0.999414       599098      1706.67
     122.239     0.999512       599157      2048.00
     125.887     0.999561       599186      2275.56
     129.279     0.999609       599215      2560.00
     132.735     0.999658       599245      2925.71
     136.447     0.999707       599274      3413.33
     140.287     0.999756       599303      4096.00
     142.463     0.999780       599319      4551.11
     144.127     0.999805       599332      5120.00
     146.431     0.999829       599347      5851.43
     148.095     0.999854       599362      6826.67
     149.503     0.999878       599376      8192.00
     150.271     0.999890       599385      9102.22
     151.295     0.999902       599393     10240.00
     152.063     0.999915       599399     11702.86
     152.959     0.999927       599406     13653.33
     153.727     0.999939       599413     16384.00
     154.495     0.999945       599417     18204.44
     154.879     0.999951       599420     20480.00
     155.903     0.999957       599424     23405.71
     157.183     0.999963       599428     27306.67
     157.567     0.999969       599431     32768.00
     158.335     0.999973       599433     36408.89
     158.719     0.999976       599435     40960.00
     159.231     0.999979       599437     46811.43
     159.871     0.999982       599439     54613.33
     159.999     0.999985       599441     65536.00
     159.999     0.999986       599441     72817.78
     160.383     0.999988       599442     81920.00
     160.767     0.999989       599444     93622.86
     160.767     0.999991       599444    109226.67
     161.023     0.999992       599445    131072.00
     161.023     0.999993       599445    145635.56
     161.919     0.999994       599447    163840.00
     161.919     0.999995       599447    187245.71
     161.919     0.999995       599447    218453.33
     161.919     0.999996       599447    262144.00
     161.919     0.999997       599447    291271.11
     162.943     0.999997       599449    327680.00
     162.943     1.000000       599449          inf

[Mean    =        1.522, StdDeviation   =        5.985]  
[Max     =      162.816, Total count    =       599449]  
[Buckets =           27, SubBuckets     =         2048]    
----------------------------------------------------------  
899643 requests in 29.98s, 50.62MB read  
Requests/sec:  30006.97  
Transfer/sec:      1.69MB  


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


| Количество нод | Optimal rate | Latency avg | Latency per 90% | Max latency |
|:--------------:|:------------:|:-----------:|:---------------:|:-----------:|
|       1        |     1000     |  14.68 ms   |    22.78 ms     |  58.56 ms   |
|       2        |     1000     |  19.68 ms   |    32.37 ms     |  120.13 ms  |
|       3        |     1000     |  26.05 ms   |    39.97 ms     |  166.27 ms  |
|       4        |     1000     |  16.44 ms   |    27.57 ms     |  109.76 ms  |
|       5        |     1000     |  25.17 ms   |    42.72 ms     |  130.56 ms  |
|       10       |     1000     |  18.12 ms   |    28.75 ms     |  787.97 ms  |


Ниже представлен вывод для оптимального варианта - очереди размером 256.  

`wrk -t 64 -c 64 -d 30 -R 1000 -s get.lua -L http://localhost:41575`  
Running 30s test @ http://localhost:41575  
64 threads and 64 connections  
Thread calibration: mean lat.: 10.742ms, rate sampling interval: 31ms  
Thread calibration: mean lat.: 8.155ms, rate sampling interval: 18ms  
Thread calibration: mean lat.: 9.270ms, rate sampling interval: 19ms  
Thread calibration: mean lat.: 8.144ms, rate sampling interval: 18ms  
Thread calibration: mean lat.: 8.869ms, rate sampling interval: 19ms  
Thread calibration: mean lat.: 17.686ms, rate sampling interval: 44ms  
Thread calibration: mean lat.: 10.877ms, rate sampling interval: 29ms  
Thread calibration: mean lat.: 15.483ms, rate sampling interval: 45ms  
Thread calibration: mean lat.: 12.992ms, rate sampling interval: 31ms  
Thread calibration: mean lat.: 17.830ms, rate sampling interval: 52ms  
Thread calibration: mean lat.: 15.505ms, rate sampling interval: 41ms  
Thread calibration: mean lat.: 19.074ms, rate sampling interval: 45ms  
Thread calibration: mean lat.: 15.682ms, rate sampling interval: 41ms  
Thread calibration: mean lat.: 14.142ms, rate sampling interval: 45ms  
Thread calibration: mean lat.: 18.993ms, rate sampling interval: 64ms  
Thread calibration: mean lat.: 18.927ms, rate sampling interval: 44ms  
Thread calibration: mean lat.: 14.534ms, rate sampling interval: 39ms  
Thread calibration: mean lat.: 14.599ms, rate sampling interval: 43ms  
Thread calibration: mean lat.: 29.878ms, rate sampling interval: 72ms  
Thread calibration: mean lat.: 26.602ms, rate sampling interval: 63ms  
Thread calibration: mean lat.: 28.887ms, rate sampling interval: 69ms  
Thread calibration: mean lat.: 23.351ms, rate sampling interval: 71ms  
Thread calibration: mean lat.: 13.654ms, rate sampling interval: 52ms  
Thread calibration: mean lat.: 13.364ms, rate sampling interval: 39ms  
Thread calibration: mean lat.: 18.854ms, rate sampling interval: 45ms  
Thread calibration: mean lat.: 26.803ms, rate sampling interval: 72ms  
Thread calibration: mean lat.: 28.557ms, rate sampling interval: 75ms  
Thread calibration: mean lat.: 23.488ms, rate sampling interval: 66ms  
Thread calibration: mean lat.: 29.615ms, rate sampling interval: 81ms  
Thread calibration: mean lat.: 19.154ms, rate sampling interval: 50ms  
Thread calibration: mean lat.: 30.716ms, rate sampling interval: 79ms  
Thread calibration: mean lat.: 31.493ms, rate sampling interval: 72ms  
Thread calibration: mean lat.: 36.710ms, rate sampling interval: 90ms  
Thread calibration: mean lat.: 37.954ms, rate sampling interval: 91ms  
Thread calibration: mean lat.: 36.436ms, rate sampling interval: 101ms  
Thread calibration: mean lat.: 24.756ms, rate sampling interval: 61ms  
Thread calibration: mean lat.: 25.025ms, rate sampling interval: 57ms  
Thread calibration: mean lat.: 37.024ms, rate sampling interval: 84ms  
Thread calibration: mean lat.: 38.561ms, rate sampling interval: 87ms  
Thread calibration: mean lat.: 31.265ms, rate sampling interval: 71ms  
Thread calibration: mean lat.: 30.042ms, rate sampling interval: 71ms  
Thread calibration: mean lat.: 26.189ms, rate sampling interval: 65ms  
Thread calibration: mean lat.: 39.719ms, rate sampling interval: 102ms  
Thread calibration: mean lat.: 41.830ms, rate sampling interval: 95ms  
Thread calibration: mean lat.: 29.654ms, rate sampling interval: 76ms  
Thread calibration: mean lat.: 34.487ms, rate sampling interval: 86ms  
Thread calibration: mean lat.: 19.812ms, rate sampling interval: 53ms  
Thread calibration: mean lat.: 35.436ms, rate sampling interval: 82ms  
Thread calibration: mean lat.: 37.698ms, rate sampling interval: 91ms  
Thread calibration: mean lat.: 43.286ms, rate sampling interval: 109ms  
Thread calibration: mean lat.: 42.561ms, rate sampling interval: 104ms  
Thread calibration: mean lat.: 27.060ms, rate sampling interval: 69ms  
Thread calibration: mean lat.: 37.144ms, rate sampling interval: 94ms  
Thread calibration: mean lat.: 27.979ms, rate sampling interval: 67ms  
Thread calibration: mean lat.: 35.502ms, rate sampling interval: 92ms  
Thread calibration: mean lat.: 37.820ms, rate sampling interval: 84ms  
Thread calibration: mean lat.: 35.361ms, rate sampling interval: 84ms  
Thread calibration: mean lat.: 36.334ms, rate sampling interval: 91ms  
Thread calibration: mean lat.: 28.431ms, rate sampling interval: 71ms  
Thread calibration: mean lat.: 35.773ms, rate sampling interval: 86ms  
Thread calibration: mean lat.: 30.731ms, rate sampling interval: 80ms  
Thread calibration: mean lat.: 42.100ms, rate sampling interval: 106ms  
Thread calibration: mean lat.: 27.637ms, rate sampling interval: 69ms  
Thread calibration: mean lat.: 34.434ms, rate sampling interval: 92ms  
Thread Stats   Avg      Stdev     Max   +/- Stdev  
Latency    26.05ms   14.10ms 166.14ms   74.36%  
Req/Sec    15.48     13.48    64.00     62.93%  
Latency Distribution (HdrHistogram - Recorded Latency)  
50.000%   25.36ms  
75.000%   33.41ms  
90.000%   39.97ms  
99.000%   74.05ms  
99.900%  108.80ms  
99.990%  136.57ms  
99.999%  166.27ms  
100.000%  166.27ms  

Detailed Percentile spectrum:  
Value   Percentile   TotalCount 1/(1-Percentile)  

       4.411     0.000000            1         1.00
       8.743     0.100000         2000         1.11
      13.199     0.200000         4000         1.25
      18.223     0.300000         5999         1.43
      22.303     0.400000         8004         1.67
      25.359     0.500000        10000         2.00
      27.407     0.550000        10998         2.22
      28.671     0.600000        12006         2.50
      30.079     0.650000        13005         2.86
      31.647     0.700000        14006         3.33
      33.407     0.750000        15013         4.00
      34.143     0.775000        15501         4.44
      35.039     0.800000        16002         5.00
      35.871     0.825000        16496         5.71
      37.087     0.850000        17006         6.67
      38.367     0.875000        17512         8.00
      39.103     0.887500        17749         8.89
      39.967     0.900000        18002        10.00
      40.927     0.912500        18248        11.43
      42.271     0.925000        18498        13.33
      44.287     0.937500        18746        16.00
      46.143     0.943750        18871        17.78
      48.351     0.950000        18996        20.00
      51.231     0.956250        19122        22.86
      53.887     0.962500        19246        26.67
      57.695     0.968750        19371        32.00
      59.359     0.971875        19433        35.56
      60.895     0.975000        19497        40.00
      62.751     0.978125        19562        45.71
      64.831     0.981250        19622        53.33
      67.071     0.984375        19683        64.00
      68.287     0.985938        19714        71.11
      70.143     0.987500        19746        80.00
      72.511     0.989062        19778        91.43
      74.943     0.990625        19808       106.67
      79.743     0.992188        19839       128.00
      82.879     0.992969        19855       142.22
      86.015     0.993750        19871       160.00
      89.983     0.994531        19886       182.86
      93.887     0.995313        19902       213.33
      97.151     0.996094        19917       256.00
      97.791     0.996484        19925       284.44
      99.583     0.996875        19933       320.00
     101.247     0.997266        19941       365.71
     102.271     0.997656        19949       426.67
     102.847     0.998047        19956       512.00
     103.615     0.998242        19960       568.89
     105.343     0.998437        19964       640.00
     105.983     0.998633        19969       731.43
     107.007     0.998828        19972       853.33
     108.991     0.999023        19976      1024.00
     110.335     0.999121        19979      1137.78
     111.167     0.999219        19980      1280.00
     113.407     0.999316        19982      1462.86
     117.567     0.999414        19984      1706.67
     121.727     0.999512        19986      2048.00
     123.263     0.999561        19987      2275.56
     124.991     0.999609        19988      2560.00
     130.559     0.999658        19989      2925.71
     132.607     0.999707        19990      3413.33
     133.247     0.999756        19991      4096.00
     133.247     0.999780        19991      4551.11
     134.655     0.999805        19992      5120.00
     134.655     0.999829        19992      5851.43
     136.575     0.999854        19993      6826.67
     136.575     0.999878        19993      8192.00
     136.575     0.999890        19993      9102.22
     150.911     0.999902        19994     10240.00
     150.911     0.999915        19994     11702.86
     150.911     0.999927        19994     13653.33
     150.911     0.999939        19994     16384.00
     150.911     0.999945        19994     18204.44
     166.271     0.999951        19995     20480.00
     166.271     1.000000        19995          inf

[Mean    =       26.052, StdDeviation   =       14.104]  
[Max     =      166.144, Total count    =        19995]  
[Buckets =           27, SubBuckets     =         2048]  
----------------------------------------------------------  
30016 requests in 30.02s, 2.28MB read  
Requests/sec:    999.83  
Transfer/sec:     77.85KB  
 


#### **Профилирование (CPU, alloc, lock) с помощью async-profiler**
##### PUT-запросы
**CPU**


|                                                                    cluster size = 3                                                                     |                                                               cluster size = 1 (stage 2)                                                                |
|:-------------------------------------------------------------------------------------------------------------------------------------------------------:|:-------------------------------------------------------------------------------------------------------------------------------------------------------:|
| ![Иллюстрация к проекту](https://github.com/Anilochka/2022-highload-dht/blob/stage3/src/main/java/ok/dht/test/shestakova/report/jpg/stage3/put_cpu.jpg) | ![Иллюстрация к проекту](https://github.com/Anilochka/2022-highload-dht/blob/stage2/src/main/java/ok/dht/test/shestakova/report/jpg/stage2/put_cpu.jpg) |



Относительно синхронной версии помимо появившегося ThreadPoolExecutor-а, занимающего 67% (включая работу с блокирующей очередью, в которой взятие задачи занимает 12%), также значительно увеличилось количество нативного кода и syscall-ов из-за локов, появившихся в связи с увеличением потоков, желающих работать с сервисом (присутствуют локи после вызова метода offer, вставляющего задачу в очередь, после метода take, берущего из очереди задачу. Виден механизм работы блокирующей очереди, он забирает около 20%, но увеличивает пропускную способность сервера за счет освобождения селекторов, в связи с чем можно считать это не сильно критическим).  
Менее 2% времени процессора занимают локи, присутствующие в реализации upsert-метода dao, что не критично.


**alloc**

|                                                                     cluster size = 3                                                                      |                                                                          cluster size = 1 (stage 2)                                                                           |
|:---------------------------------------------------------------------------------------------------------------------------------------------------------:|:-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------:|
| ![Иллюстрация к проекту](https://github.com/Anilochka/2022-highload-dht/blob/stage3/src/main/java/ok/dht/test/shestakova/report/jpg/stage3/put_alloc.jpg) |           ![Иллюстрация к проекту](https://github.com/Anilochka/2022-highload-dht/blob/stage2/src/main/java/ok/dht/test/shestakova/report/jpg/stage2/put_alloc.jpg)           |

По аллокациям сильных изменений помимо появления ThreadPoolExecutor не наблюдается: добавились аллокации на локи (менее 1% на лок в upsert).

**lock**

|                                                                       cluster size = 3                                                                        |                                                                cluster size = 1 (stage 2)                                                                |
|:-------------------------------------------------------------------------------------------------------------------------------------------------------------:|:--------------------------------------------------------------------------------------------------------------------------------------------------------:|
| ![Иллюстрация к проекту](https://github.com/Anilochka/2022-highload-dht/blob/stage3/src/main/java/ok/dht/test/shestakova/report/jpg/stage3/put_lock_sync.jpg) | ![Иллюстрация к проекту](https://github.com/Anilochka/2022-highload-dht/blob/stage2/src/main/java/ok/dht/test/shestakova/report/jpg/stage2/put_lock.jpg) |

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

|                                                                    cluster size = 3                                                                     |                                                               cluster size = 1 (stage 2)                                                                |
|:-------------------------------------------------------------------------------------------------------------------------------------------------------:|:-------------------------------------------------------------------------------------------------------------------------------------------------------:|
| ![Иллюстрация к проекту](https://github.com/Anilochka/2022-highload-dht/blob/stage3/src/main/java/ok/dht/test/shestakova/report/jpg/stage3/get_cpu.jpg) | ![Иллюстрация к проекту](https://github.com/Anilochka/2022-highload-dht/blob/stage2/src/main/java/ok/dht/test/shestakova/report/jpg/stage2/get_cpu.jpg) |


Как и в put-запросах заметно, что ThreadPoolExecutor забрал у SelectorThread работу с БД, чем освободил его.  
Заметно меньше стало видно на графике syscall-ов, они перешли на селектор, а 98% - работа с БД - висят уже на нашем пуле воркеров, где все еще более 80% занимаеет поиск ключа по диску.

**alloc**

|                                                                     cluster size = 3                                                                      |                                                                cluster size = 1 (stage 2)                                                                 |
|:---------------------------------------------------------------------------------------------------------------------------------------------------------:|:---------------------------------------------------------------------------------------------------------------------------------------------------------:|
| ![Иллюстрация к проекту](https://github.com/Anilochka/2022-highload-dht/blob/stage3/src/main/java/ok/dht/test/shestakova/report/jpg/stage3/get_alloc.jpg) | ![Иллюстрация к проекту](https://github.com/Anilochka/2022-highload-dht/blob/stage2/src/main/java/ok/dht/test/shestakova/report/jpg/stage2/get_alloc.jpg) |

В контексте аллокаций графики синхронного и асинхронного вариантов практически не различаются.


**lock**

|                                                                       cluster size = 3                                                                        |                                                                cluster size = 1 (stage 2)                                                                |
|:-------------------------------------------------------------------------------------------------------------------------------------------------------------:|:--------------------------------------------------------------------------------------------------------------------------------------------------------:|
| ![Иллюстрация к проекту](https://github.com/Anilochka/2022-highload-dht/blob/stage3/src/main/java/ok/dht/test/shestakova/report/jpg/stage3/get_lock_sync.jpg) | ![Иллюстрация к проекту](https://github.com/Anilochka/2022-highload-dht/blob/stage2/src/main/java/ok/dht/test/shestakova/report/jpg/stage2/get_lock.jpg) |

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
