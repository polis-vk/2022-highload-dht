### **STAGE 5**

#### **Нагрузочное тестирование**
##### PUT-запросы с кол-вом соединений 64

При размере кластера 1 на прошлых этапах максимальное значение rate, получаемое с приемлемым latency, было равно 70000. 
На предыдущем этапе с увеличением количества нод оптимальный rate постепенно снижался с 20000(2-3 ноды) до 5000(10 нод).  
Были проведены запуски сервера с количеством нод от 1 до 10, параметры ack и from по умолчанию равны кворуму от количества нод и количеству нод соответственно. 
В приведённой таблице видно, что с увеличением размера кластера оптимальное значение rate снижается. 
В сравнении с прошлым этапом ключевым изменением, влияющим на изменение оптимального rate является добавление асинхронного получения ответов от сервера. 
Теперь мы не ждем получения ответа от другой ноды, а сразу отправляем всем искомым нодам запросы, 
при получении ответа добавляем его в лист с ответами, и если набралось ack ответов - возвращаем этот лист в виде CompletableFuture, а затем обрабатываем.  
Ожидалось, что теперь, когда мы получаем ответы не последовательно, а параллельно, производительность значительно вырастет, но для put-запросов такого не произошло.  
При количестве нод 2 мы вроде как и повысили производительность с rate=20000 до rate=30000 при относительно небольшом увеличении latency 
(avg - в 1.28 раз, 90% - в 1.14 раз, max - в 1,07 раз), но далее при размере кластера 3 оптимальное значение rate упало с 20000 до 15000, 
при размерах кластера 4 и 5 оптимальное значение rate осталось тем же, что и в прошлом stage (10000, значения latency относительно близки), 
а при размере кластера 10 при одинаковом rate наблюдается увеличение среднего latency и latency для 90% запросов больше, чем в 10 раз, значение 
max latency вырасло в ~3.5 раза.  
Причиной такого не совсем ожидаемого поведения системы могла, конечно же, послужить неидельная реализация искомой идеи, но кроме этого есть 
и другие причины, как, например, появление затрат на перемещение задач из потока в поток (с нашего пула воркеров в пул обработчиков клиента - ForkJoinPool). 
Вероятно, время выполнения и отправки ответов на put-запросы несоразмерно с тем временем, которое уходит на перемещение задачи между потоками, 
что особенно заметно при увеличении количества нод в кластере (следовательно, и значений параметра ack, от которого зависит 
количество необходимых ответов и, следовательно, количество перемещений задач в пул клиента).  

STAGE 5:

| Количество нод |  ack/from  | Optimal rate | Latency avg | Latency per 90% | Max latency |
|:--------------:|:----------:|:------------:|:-----------:|:---------------:|:-----------:|
|       2        |    2/2     |    30000     |   0.92 ms   |     1.33 ms     |  11.80 ms   |
|       3        |    2/3     |    15000     |   1.23 ms   |     1.98 ms     |   9.34 ms   |
|       4        |    3/4     |    10000     |   1.26 ms   |     1.98 ms     |  18.08 ms   |
|       5        |    3/5     |    10000     |   1.53 ms   |     2.45 ms     |  17.38 ms   |
|       10       |    6/10    |     5000     |  33.60 ms   |    53.53 ms     |  81.92 ms   |

STAGE 4:

| Количество нод |  ack/from  | Optimal rate | Latency avg | Latency per 90% | Max latency |
|:--------------:|:----------:|:------------:|:-----------:|:---------------:|:-----------:|
|       2        |    2/2     |    20000     |  764.45 us  |     1.16 ms     |  10.94 ms   |
|       3        |    2/3     |    20000     |   0.86 ms   |     1.26 ms     |  11.91 ms   |
|       4        |    3/4     |    10000     |   0.93 ms   |     1.32 ms     |  17.31 ms   |
|       5        |    3/5     |    10000     |   1.18 ms   |     1.66 ms     |  17.33 ms   |
|       10       |    6/10    |     5000     |   2.54 ms   |     4.15 ms     |  24.09 ms   |

Ниже представлен вывод для варианта с количеством нод 3.  

`wrk -t 64 -c 64 -d 30 -R 15000 -s put.lua -L http://localhost:35351`  
Running 30s test @ http://localhost:35351  
64 threads and 64 connections 
Thread calibration: mean lat.: 1.062ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 1.132ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 1.223ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 1.212ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 1.114ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 1.103ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 1.157ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 1.206ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 1.094ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 1.246ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 1.249ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 1.266ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 1.291ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 1.268ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 1.287ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 1.282ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 1.282ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 1.271ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 1.233ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 1.292ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 1.272ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 1.260ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 1.258ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 1.290ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 1.265ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 1.265ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 1.286ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 1.270ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 1.143ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 1.053ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 1.236ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 1.250ms, rate sampling interval: 10ms
Thread calibration: mean lat.: 1.083ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 1.169ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 1.215ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 1.234ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 1.171ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 1.056ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 1.178ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 1.125ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 1.151ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 1.101ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 1.071ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 1.059ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 1.053ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 1.082ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 1.288ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 1.197ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 1.300ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 1.246ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 1.245ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 1.241ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 1.280ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 1.274ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 1.259ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 1.285ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 1.280ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 1.269ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 1.262ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 1.282ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 1.265ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 1.253ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 1.095ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 1.083ms, rate sampling interval: 10ms  
Thread Stats   Avg      Stdev     Max   +/- Stdev  
Latency     1.23ms  719.70us   9.34ms   83.01%  
Req/Sec   247.42     52.52   444.00     84.99%  
Latency Distribution (HdrHistogram - Recorded Latency)  
50.000%    1.10ms  
75.000%    1.40ms  
90.000%    1.98ms  
99.000%    4.25ms  
99.900%    6.07ms  
99.990%    7.75ms  
99.999%    8.77ms  
100.000%    9.34ms  

Detailed Percentile spectrum:  
Value   Percentile   TotalCount 1/(1-Percentile)  

       0.187     0.000000            1         1.00
       0.571     0.100000        30110         1.11
       0.725     0.200000        60003         1.25
       0.860     0.300000        90075         1.43
       0.984     0.400000       120015         1.67
       1.101     0.500000       150167         2.00
       1.157     0.550000       165141         2.22
       1.211     0.600000       180099         2.50
       1.266     0.650000       195092         2.86
       1.327     0.700000       210045         3.33
       1.402     0.750000       224932         4.00
       1.449     0.775000       232417         4.44
       1.507     0.800000       239944         5.00
       1.580     0.825000       247482         5.71
       1.678     0.850000       254965         6.67
       1.810     0.875000       262452         8.00
       1.889     0.887500       266179         8.89
       1.981     0.900000       269913        10.00
       2.087     0.912500       273667        11.43
       2.207     0.925000       277457        13.33
       2.359     0.937500       281158        16.00
       2.449     0.943750       283035        17.78
       2.553     0.950000       284918        20.00
       2.671     0.956250       286791        22.86
       2.819     0.962500       288662        26.67
       2.997     0.968750       290525        32.00
       3.109     0.971875       291466        35.56
       3.231     0.975000       292399        40.00
       3.375     0.978125       293329        45.71
       3.547     0.981250       294273        53.33
       3.763     0.984375       295209        64.00
       3.883     0.985938       295671        71.11
       4.003     0.987500       296143        80.00
       4.143     0.989062       296619        91.43
       4.323     0.990625       297081       106.67
       4.519     0.992188       297547       128.00
       4.635     0.992969       297786       142.22
       4.751     0.993750       298014       160.00
       4.875     0.994531       298257       182.86
       5.007     0.995313       298488       213.33
       5.139     0.996094       298726       256.00
       5.219     0.996484       298833       284.44
       5.307     0.996875       298952       320.00
       5.399     0.997266       299067       365.71
       5.507     0.997656       299188       426.67
       5.639     0.998047       299304       512.00
       5.699     0.998242       299362       568.89
       5.771     0.998437       299420       640.00
       5.863     0.998633       299480       731.43
       5.971     0.998828       299537       853.33
       6.087     0.999023       299596      1024.00
       6.139     0.999121       299625      1137.78
       6.223     0.999219       299653      1280.00
       6.323     0.999316       299682      1462.86
       6.427     0.999414       299712      1706.67
       6.547     0.999512       299741      2048.00
       6.627     0.999561       299756      2275.56
       6.695     0.999609       299770      2560.00
       6.839     0.999658       299785      2925.71
       6.979     0.999707       299800      3413.33
       7.111     0.999756       299814      4096.00
       7.203     0.999780       299823      4551.11
       7.283     0.999805       299829      5120.00
       7.387     0.999829       299836      5851.43
       7.523     0.999854       299844      6826.67
       7.631     0.999878       299851      8192.00
       7.679     0.999890       299855      9102.22
       7.811     0.999902       299858     10240.00
       8.023     0.999915       299862     11702.86
       8.103     0.999927       299866     13653.33
       8.143     0.999939       299869     16384.00
       8.263     0.999945       299871     18204.44
       8.295     0.999951       299873     20480.00
       8.367     0.999957       299876     23405.71
       8.407     0.999963       299877     27306.67
       8.471     0.999969       299878     32768.00
       8.503     0.999973       299879     36408.89
       8.511     0.999976       299881     40960.00
       8.511     0.999979       299881     46811.43
       8.583     0.999982       299882     54613.33
       8.711     0.999985       299883     65536.00
       8.711     0.999986       299883     72817.78
       8.767     0.999988       299884     81920.00
       8.767     0.999989       299884     93622.86
       8.831     0.999991       299885    109226.67
       8.831     0.999992       299885    131072.00
       8.831     0.999993       299885    145635.56
       9.287     0.999994       299886    163840.00
       9.287     0.999995       299886    187245.71
       9.287     0.999995       299886    218453.33
       9.287     0.999996       299886    262144.00
       9.287     0.999997       299886    291271.11
       9.343     0.999997       299887    327680.00
       9.343     1.000000       299887          inf
[Mean    =        1.229, StdDeviation   =        0.720]  
[Max     =        9.336, Total count    =       299887]  
[Buckets =           27, SubBuckets     =         2048]  
----------------------------------------------------------  
450011 requests in 30.00s, 28.75MB read  
Requests/sec:  15002.08  
Transfer/sec:      0.96MB  


##### GET-запросы на заполненной (1.2 Гб на каждой ноде) БД
 
Как и для put-запросов, были проведены запуски сервера с количеством нод от 2 до 5. В приведённой таблице видно, что с увеличением количества нод  
оптимальное значение rate не меняется, только увеличиваются все значения latency: и среднее, и для 90% запросов, и для max latency.  
Но в сравнении с предыдущим этапом все рассматриваемые значения latency уменьшились в 2.5-7 раз.  
Как и для put-запросов, становится заметрым влияние количества дубляжей запросов (равное ack) на производительность, увеличивающее
количество передач задач между потоками нашего пула воркеров и пула воркеров клента (ForkJoinPool).  
В отличие от ситуации с put-запросами, которые сами по себе относительно быстрые, добавление асинхронного получения ответов дало хорошие результаты. 
Да, теперь приходится передавать обработку запроса с одного потока на другой, на что тратится некоторое время, но get-запросы, как и раньше, 
очень тяжеловесны из-за необходимости итерироваться по диску, и распараллеливание их выполнения и отправки их результатов-ответов позволяет 
теперь ждать не `ack*Ti`(*) времени для одного запроса, где Ti-время обработки запроса i-й нодой и время на общение по сети с этой нодой (если не локально), а
`max(Ti)+Tfj` - время обработки запроса самой "долгой" нодой плюс время на передачу задачи в поток ForkJoinPool-а (Tfj).

А такое большое значение max latency, особенно в вариантах с бОльшим количеством нод, вероятно, связано с работой сборщика мусора G1, 
а на put-запросах еще добавляется фоновый flush.

STAGE 5:

| Количество нод | ack/from | Optimal rate | Latency avg | Latency per 90% | Max latency |
|:--------------:|:--------:|:------------:|:-----------:|:---------------:|:-----------:|
|       2        |   2/2    |     100      |   5.48 ms   |    10.11 ms     |  15.30 ms   |
|       3        |   2/3    |     100      |  16.86 ms   |    35.04 ms     |  86.53 ms   |
|       4        |   3/4    |     100      |  31.69 ms   |    71.87 ms     |  116.22 ms  |
|       5        |   3/5    |     100      |  48.00 ms   |    72.19 ms     |  150.14 ms  |

STAGE 4:

| Количество нод | ack/from | Optimal rate | Latency avg | Latency per 90% | Max latency |
|:--------------:|:--------:|:------------:|:-----------:|:---------------:|:-----------:|
|       2        |   2/2    |     100      |  35.03 ms   |    65.47 ms     |  132.35 ms  |
|       3        |   2/3    |     100      |  58.30 ms   |    88.64 ms     |  180.86 ms  |
|       4        |   3/4    |     100      |  120.83 ms  |    198.53 ms    |  243.97 ms  |
|       5        |   3/5    |     100      |  146.67 ms  |    213.25 ms    |  234.24 ms  |

Ниже представлен вывод для варианта с количеством нод 3.   

`wrk -t 64 -c 64 -d 30 -R 100 -s get.lua -L http://localhost:35351`  
Running 30s test @ http://localhost:35351  
64 threads and 64 connections  
Thread calibration: mean lat.: 12.695ms, rate sampling interval: 38ms  
Thread calibration: mean lat.: 11.867ms, rate sampling interval: 35ms  
Thread calibration: mean lat.: 11.610ms, rate sampling interval: 33ms  
Thread calibration: mean lat.: 13.795ms, rate sampling interval: 40ms  
Thread calibration: mean lat.: 13.504ms, rate sampling interval: 36ms  
Thread calibration: mean lat.: 13.095ms, rate sampling interval: 32ms  
Thread calibration: mean lat.: 12.831ms, rate sampling interval: 32ms  
Thread calibration: mean lat.: 16.605ms, rate sampling interval: 39ms  
Thread calibration: mean lat.: 18.096ms, rate sampling interval: 45ms  
Thread calibration: mean lat.: 15.562ms, rate sampling interval: 46ms  
Thread calibration: mean lat.: 16.294ms, rate sampling interval: 39ms  
Thread calibration: mean lat.: 15.586ms, rate sampling interval: 40ms  
Thread calibration: mean lat.: 15.321ms, rate sampling interval: 36ms  
Thread calibration: mean lat.: 15.845ms, rate sampling interval: 41ms  
Thread calibration: mean lat.: 13.388ms, rate sampling interval: 30ms  
Thread calibration: mean lat.: 14.481ms, rate sampling interval: 36ms  
Thread calibration: mean lat.: 13.644ms, rate sampling interval: 34ms  
Thread calibration: mean lat.: 13.754ms, rate sampling interval: 30ms  
Thread calibration: mean lat.: 13.330ms, rate sampling interval: 46ms  
Thread calibration: mean lat.: 13.241ms, rate sampling interval: 36ms  
Thread calibration: mean lat.: 13.708ms, rate sampling interval: 35ms  
Thread calibration: mean lat.: 14.621ms, rate sampling interval: 43ms  
Thread calibration: mean lat.: 13.597ms, rate sampling interval: 32ms  
Thread calibration: mean lat.: 13.795ms, rate sampling interval: 35ms  
Thread calibration: mean lat.: 13.560ms, rate sampling interval: 34ms  
Thread calibration: mean lat.: 13.263ms, rate sampling interval: 33ms  
Thread calibration: mean lat.: 12.431ms, rate sampling interval: 34ms  
Thread calibration: mean lat.: 12.870ms, rate sampling interval: 33ms  
Thread calibration: mean lat.: 8.124ms, rate sampling interval: 21ms  
Thread calibration: mean lat.: 8.784ms, rate sampling interval: 26ms  
Thread calibration: mean lat.: 8.577ms, rate sampling interval: 21ms  
Thread calibration: mean lat.: 8.969ms, rate sampling interval: 25ms  
Thread calibration: mean lat.: 6.544ms, rate sampling interval: 16ms  
Thread calibration: mean lat.: 5.432ms, rate sampling interval: 15ms  
Thread calibration: mean lat.: 5.212ms, rate sampling interval: 11ms  
Thread calibration: mean lat.: 4.766ms, rate sampling interval: 12ms  
Thread calibration: mean lat.: 5.235ms, rate sampling interval: 12ms  
Thread calibration: mean lat.: 4.622ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 4.846ms, rate sampling interval: 11ms  
Thread calibration: mean lat.: 4.662ms, rate sampling interval: 11ms  
Thread calibration: mean lat.: 4.599ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 5.549ms, rate sampling interval: 12ms  
Thread calibration: mean lat.: 5.909ms, rate sampling interval: 12ms  
Thread calibration: mean lat.: 5.387ms, rate sampling interval: 13ms  
Thread calibration: mean lat.: 6.378ms, rate sampling interval: 15ms  
Thread calibration: mean lat.: 6.029ms, rate sampling interval: 14ms  
Thread calibration: mean lat.: 6.890ms, rate sampling interval: 15ms  
Thread calibration: mean lat.: 7.254ms, rate sampling interval: 13ms  
Thread calibration: mean lat.: 7.110ms, rate sampling interval: 18ms  
Thread calibration: mean lat.: 5.078ms, rate sampling interval: 11ms  
Thread calibration: mean lat.: 4.848ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 5.403ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 8.355ms, rate sampling interval: 19ms  
Thread calibration: mean lat.: 9.706ms, rate sampling interval: 22ms  
Thread calibration: mean lat.: 9.183ms, rate sampling interval: 20ms  
Thread calibration: mean lat.: 9.325ms, rate sampling interval: 21ms  
Thread calibration: mean lat.: 7.493ms, rate sampling interval: 18ms  
Thread calibration: mean lat.: 9.274ms, rate sampling interval: 21ms  
Thread calibration: mean lat.: 9.949ms, rate sampling interval: 21ms  
Thread calibration: mean lat.: 9.099ms, rate sampling interval: 21ms  
Thread calibration: mean lat.: 9.433ms, rate sampling interval: 20ms  
Thread calibration: mean lat.: 8.151ms, rate sampling interval: 20ms  
Thread calibration: mean lat.: 8.971ms, rate sampling interval: 24ms  
Thread calibration: mean lat.: 8.066ms, rate sampling interval: 14ms  
Thread Stats   Avg      Stdev     Max   +/- Stdev  
Latency    16.86ms   13.14ms  86.46ms   77.72%  
Req/Sec     1.59     10.00   111.00     96.87%  
Latency Distribution (HdrHistogram - Recorded Latency)  
50.000%   13.00ms  
75.000%   22.96ms  
90.000%   35.04ms  
99.000%   64.19ms  
99.900%   76.22ms  
99.990%   86.53ms  
99.999%   86.53ms  
100.000%   86.53ms  

Detailed Percentile spectrum:  
Value   Percentile   TotalCount 1/(1-Percentile)  

       1.762     0.000000            1         1.00
       4.455     0.100000          199         1.11
       6.163     0.200000          398         1.25
       7.955     0.300000          596         1.43
      10.447     0.400000          794         1.67
      12.999     0.500000          992         2.00
      14.215     0.550000         1092         2.22
      15.783     0.600000         1191         2.50
      17.839     0.650000         1291         2.86
      20.175     0.700000         1389         3.33
      22.959     0.750000         1488         4.00
      24.607     0.775000         1538         4.44
      26.751     0.800000         1588         5.00
      28.639     0.825000         1637         5.71
      30.831     0.850000         1687         6.67
      33.119     0.875000         1736         8.00
      34.079     0.887500         1761         8.89
      35.039     0.900000         1786        10.00
      36.479     0.912500         1811        11.43
      37.695     0.925000         1836        13.33
      39.103     0.937500         1860        16.00
      39.647     0.943750         1873        17.78
      40.383     0.950000         1885        20.00
      41.919     0.956250         1898        22.86
      43.775     0.962500         1910        26.67
      47.167     0.968750         1922        32.00
      49.183     0.971875         1929        35.56
      52.511     0.975000         1935        40.00
      56.191     0.978125         1941        45.71
      58.335     0.981250         1947        53.33
      61.119     0.984375         1953        64.00
      62.175     0.985938         1957        71.11
      62.847     0.987500         1960        80.00
      64.095     0.989062         1963        91.43
      64.543     0.990625         1966       106.67
      65.855     0.992188         1969       128.00
      67.391     0.992969         1971       142.22
      67.519     0.993750         1972       160.00
      68.159     0.994531         1974       182.86
      68.351     0.995313         1975       213.33
      69.055     0.996094         1977       256.00
      69.311     0.996484         1978       284.44
      69.311     0.996875         1978       320.00
      71.551     0.997266         1979       365.71
      72.511     0.997656         1980       426.67
      73.919     0.998047         1981       512.00
      73.919     0.998242         1981       568.89
      73.919     0.998437         1981       640.00
      76.223     0.998633         1982       731.43
      76.223     0.998828         1982       853.33
      77.247     0.999023         1983      1024.00
      77.247     0.999121         1983      1137.78
      77.247     0.999219         1983      1280.00
      77.247     0.999316         1983      1462.86
      77.247     0.999414         1983      1706.67
      86.527     0.999512         1984      2048.00
      86.527     1.000000         1984          inf
[Mean    =       16.864, StdDeviation   =       13.136]  
[Max     =       86.464, Total count    =         1984]  
[Buckets =           27, SubBuckets     =         2048]  
----------------------------------------------------------  
3008 requests in 30.02s, 240.25KB read  
Requests/sec:    100.20  
Transfer/sec:      8.00KB  



#### **Профилирование (CPU, alloc, lock) с помощью async-profiler**
##### PUT-запросы
**CPU**


|                                                                    using CF (stage 5)                                                                     |                                                                      replication 2/3                                                                      |
|:---------------------------------------------------------------------------------------------------------------------------------------------------------:|:---------------------------------------------------------------------------------------------------------------------------------------------------------:|
| ![Иллюстрация к проекту](https://github.com/Anilochka/2022-highload-dht-1/blob/stage3/src/main/java/ok/dht/test/shestakova/report/jpg/stage3/put_cpu.jpg) | ![Иллюстрация к проекту](https://github.com/Anilochka/2022-highload-dht-1/blob/stage4/src/main/java/ok/dht/test/shestakova/report/jpg/stage4/put_cpu.jpg) |

Сильных отличий между новой версией и stage 4 в контексте cpu-профилирования put запросов практически нет. Единственно, в новой реализации 
ForkJoinWorkerThread забрал на себя 3% процессорного времени от пула воркеров (теперь он забирает 7.5% процессорного времени). Главным образом 
уведичилось время на выполнение задачи из очереди (WorkQueue.topLevelExec - добавилась отпрака ответа и запись в сокет).  

Заметно стали выделяться на флеймграфе прямоугольники SharedRuntime::complete_monitor_locking_C при работе с ConnectionPool:  
- в SelectorManager-е httpClient-а - при вызове purgeExpiredConnectionsAndReturnNextDeadline (очистка просроченного соединения и возврат 
количества миллисекунд до истечения срока действия следующего соединения), где SharedRuntime::complete_monitor_locking_C - ;  
- в SequentalScheduler-е потоков из пула воркеров при вызове returnToPool (возвращение соединения в пул), где SharedRuntime::complete_monitor_locking_C - 2%, 
плюс при регистрации события SelectorManager-ом - 1%;
- при обработке ответа из CF - при регистрации события SelectorManager-ом SharedRuntime::complete_monitor_locking_C забирает 0.15% процессорного времени, плюс
при ConnectionPool.getConnection - 1%.  
Вышеперечисленные вызовы методов, приводящие к вызову SharedRuntime::complete_monitor_locking_C, являются либо synchronize-методами, либо значительная часть их 
содержимого помещена в блок synchronized. В предыдущей версии работы такие вызовы тоже присутствовали, но на флеймграфе они незаметны. В новой же реализации 
добавилась асинхронная обработка ответов -> больше работы с CF, плюс больше соединений при передаче задач между потоками из разных пулов.  

Как и в прошлой реализации время для получения нод c использованием Randezvous Hashing можно уменьшить (1.73%), тк 
используется TreeMap, stream(), limit(), и toList()(0.75%). Далее стоит, вероятно, попытаться убрать стримы (еще не успела).  

Сравнение многонодового варианта с однонодовым:  
В отличие от варианта (stage 2), где кластер состоит из одного узла, в кластере с тремя узлами помимо взятия задачи из очереди (11% процессорного времени) и обработки запроса (6% процессорного времени) для воркеров меняется следующее:  
*  добавляется обработка CompletableFuture (11%), включающая отправку запроса, подтверждение запроса перед отправкой его тела, 
установку HTTP-соединения, формирование запроса и ответа;  
* появляется InternalWriteSubscriber (13% процессорного времени воркеров), нужный для объявления того, безопасен ли встроенный вызов getBody() или 
его необходимо вызывать асинхронно в потоке исполнителя. Включает вызов SocketDispatcher.writev для записи в сокет (9.5% времени, 
большая часть которого уходит на сисколы для записи), отправку запроса;  
* также появляется SequentalScheduler (12% времени), использующийся в качестве вспомогательного средства синхронизации, 
помогающего нескольким сторонам (клиентам) выполнять задачу взаимоисключающим образом. Включает чтение из сокета, получение response.

Кроме Thread-а воркеров и SelectorThread-а появляются HttpClientImpl.SelectorManager, регистрирующий и доставляющий асинхронные события, 
приходящие на клиент. Достает задачи из очереди экзекьютора, читает из сокета, задействует SelectorImpl.select с таймаутом и блокировкой.


**alloc**

|                                                                     using CF (stage 5)                                                                      |                                                                      replication 2/3                                                                       |
|:-----------------------------------------------------------------------------------------------------------------------------------------------------------:|:----------------------------------------------------------------------------------------------------------------------------------------------------------:|
| ![Иллюстрация к проекту](https://github.com/Anilochka/2022-highload-dht-1/blob/stage5/src/main/java/ok/dht/test/shestakova/report/jpg/stage5/put_alloc.jpg) | ![Иллюстрация к проекту](https://github.com/Anilochka/2022-highload-dht-1/blob/stage4/src/main/java/ok/dht/test/shestakova/report/jpg/stage4/put_alloc.jpg) |

Как и при cpu-профилировании, сильных изменении нет. Главное и самое заметное изменение - появление аллокаций на ForkJoinWorkerThread-е - 11.31% 
(метод getResponses, включающий добавление в CopyOnWriteArraylist(1.3%), создание ответа - 5%, запись ответа в сессию - 3.5%).

Сравнение однонодовым вариантом (stage 2):  
В отличие от сервиса со 2 стейджа появились аллокации для SelectorManager клиента (2.6%), включающие аллокации на взятиях задач с очереди, 
чтение из сокета, итерацию по задачам клиента, итерацию по пулу соединений, аллокации DirectMethodHandler-а для вызова методов.   

В SelectorThread-е добавилась работа с клиентами: отправление запросов с проксированием, получение ответов, обеспечение HTTP-общения клиентов, 
создание соединения, добавились аллокации на работу планировщика SequentalScheduler (12%), обработку CompletableFuture. 

**lock**

|                                                                     using CF (stage 5)                                                                     |                                                                     replication 2/3)                                                                      |
|:----------------------------------------------------------------------------------------------------------------------------------------------------------:|:---------------------------------------------------------------------------------------------------------------------------------------------------------:|
| ![Иллюстрация к проекту](https://github.com/Anilochka/2022-highload-dht-1/blob/stage5/src/main/java/ok/dht/test/shestakova/report/jpg/stage5/put_lock.jpg) | ![Иллюстрация к проекту](https://github.com/Anilochka/2022-highload-dht-1/blob/stage4/src/main/java/ok/dht/test/shestakova/report/jpg/stage4/put_lock.jpg) |

По сравнению с предыдущим вариантом с 0.5% до 14% увеличилось процентное соотношение локов при работе с CF (при получении соединения в ConnectionPool) 
к общему количеству локов.

Сравнение с однонодовым вариантом:  
В отличие от сервиса со 2 стейджа на SelectorThread-е теперь практически нет лока при вызове метода Session.process.  
Добавились локи на клиенте - SelectorManager (Selector.select, работа с пулом соединений, обеспечение асинхронного обмена запросами и ответами).  
На воркерах также появились локи, связанных с появившимся общением нод по сети (регистрация нового event SelectorManager-ом).  
Таким образом теперь сама обработка запросов занимает менее 3% от общего числа локов (на прошлом стейдже было ~82%).

##### GET-запросы
**CPU**

|                                                                    using CF (stage 5)                                                                     |                                                                     replication 2/3                                                                      |
|:---------------------------------------------------------------------------------------------------------------------------------------------------------:|:--------------------------------------------------------------------------------------------------------------------------------------------------------:|
| ![Иллюстрация к проекту](https://github.com/Anilochka/2022-highload-dht-1/blob/stage5/src/main/java/ok/dht/test/shestakova/report/jpg/stage5/get_cpu.jpg) | ![Иллюстрация к проекту](https://github.com/Anilochka/2022-highload-dht-1/blob/stage4/src/main/java/ok/dht/test/shestakova/report/jpg/stage4/get_cpu.jpg) |

Как и в put-запросах, сильных изменений нет.
С 1.5% до 4% увеличилось количество процессорного времени, затрачиваемого на CF, взятие задач из пула.  
Все так же практически любые изменения слабо заметны на флеймграфе из-за того, что все еще самая тяжеловесная операция - итерирование по диску - 
занимает большую часть флеймграфа.

**alloc**

|                                                                     using CF (stage 5)                                                                      |                                                                      replication 2/3                                                                       |
|:-----------------------------------------------------------------------------------------------------------------------------------------------------------:|:----------------------------------------------------------------------------------------------------------------------------------------------------------:|
| ![Иллюстрация к проекту](https://github.com/Anilochka/2022-highload-dht-1/blob/stage5/src/main/java/ok/dht/test/shestakova/report/jpg/stage5/get_alloc.jpg) | ![Иллюстрация к проекту](https://github.com/Anilochka/2022-highload-dht-1/blob/stage4/src/main/java/ok/dht/test/shestakova/report/jpg/stage4/get_alloc.jpg) |

Главное изменение - появление ForkJoinWorkerThread-а (2% аллокаций), используемого для асинхронной обработки ответов. 
Значительно (с 5% до 13%) увеличилось количество аллокаций на селекторах, связвнное с увеличением аллокаций при вызове
getNodesSortedByRendezvousHashing, использующего TreeMap, stream, limit (был добавлен в этом стейдже), toList.

**lock**

|                                                                     using CF (stage 5)                                                                     |                                                                      replication 2/3                                                                      |
|:----------------------------------------------------------------------------------------------------------------------------------------------------------:|:---------------------------------------------------------------------------------------------------------------------------------------------------------:|
| ![Иллюстрация к проекту](https://github.com/Anilochka/2022-highload-dht-1/blob/stage5/src/main/java/ok/dht/test/shestakova/report/jpg/stage5/get_lock.jpg) | ![Иллюстрация к проекту](https://github.com/Anilochka/2022-highload-dht-1/blob/stage4/src/main/java/ok/dht/test/shestakova/report/jpg/stage4/get_lock.jpg) |

Главные изменения в контексте блокировок связаны с раюотой с пулом соединений: getConnection(16% блокировок), returnToPool(20% блокировок),
в SelectorManager-е httpClient-а - вызов purgeExpiredConnectionsAndReturnNextDeadline(7% блокировок).
ThreadPoolExecutor (75% локов):  
При работе пула воркеров появились локи, связанные с общением нод (отправка запроса клиентом), тк раньше эту работу выполняли селекторы.  
20% занимают локи на обработке CompletableFuture, регистрацию событий SelectorManager-ом клиента.  
На взятие задачи из очереди воркеров уходит 6% от общего числа локов.  
Добавилось 4% локов для при работе клиента (асинхронная отправка запросов нескольким нодам, а раньше отправляли только одной).  
30% локов используется для планировщика для регистрации нового event SelectorManager-ом (плюс описанный выше returnToPool). 

SelectorThread:  
И около 2% забирает synchronized метод process из пакета one.nio.net, вызывающий методы чтения и записи в сокет.  

SelectorManager (23% локов)
Работает с пулом соединений (включая вышеописанный purgeExpiredConnectionsAndReturnNextDeadline).  


Если сравнивать результаты с результатами предыдущего этапа, можно заметить, что производительность на put-запросах снизилась,
появлением затрат на перемещение задач из потока в поток (с нашего пула воркеров в пул обработчиков клиента - ForkJoinPool).
Вероятно, время выполнения и отправки ответов на put-запросы несоразмерно с тем временем, которое уходит на перемещение задачи между потоками,
что особенно заметно при увеличении количества нод в кластере (следовательно, и значений параметра ack, от которого зависит
количество необходимых ответов и, следовательно, количество перемещений задач в пул клиента).  
В отличие от ситуации с put-запросами, которые сами по себе относительно быстрые, добавление асинхронного получения ответов дало хорошие результаты.
Несмотря на необходимость передавать обработку запроса с одного потока на другой, на что тратится некоторое время, get-запросы, как и раньше,
очень тяжеловесны из-за необходимости итерироваться по диску, и распараллеливание их выполнения и отправки их результатов-ответов позволяет 
значительно сократить время ожидания полбзователем ответа.

Также в новой реализации увеличилось процессорное время и количество аллокаций для получения нод c использованием Randezvous Hashing, тк
используется TreeMap, stream(), limit() и toList(), теперь надо попытаться убрать стримы (еще не успела).

Как и на прошлых этапах:
Для оптимизации однозначно стоит рассмотреть поиск ключа на диске: подумать о фоновом компакшене, а также о других способах, которые позволили бы меньше "ходить" по диску.  
Для ускорения работы пула воркеров можно поискать более оптимальную структуру данных для очереди задач воркеров, которая бы затрачивала меньше времени на блокировки, что особенно актуально для get-запросов, в которых появление очереди задач не дало особого выигрыша в производительности из-за появившихся блокировок взятия/размещения в очереди задач.  
Кроме того можно попробовать другие коллекции для очереди задач клиента; также можно посмотреть другие протоколы для общения между клиентами.   
