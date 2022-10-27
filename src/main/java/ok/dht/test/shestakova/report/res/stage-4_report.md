### **STAGE 4**

#### **Нагрузочное тестирование**
##### PUT-запросы с кол-вом соединений 64

При размере кластера 1 на прошлых этапах максимальное значение rate, получаемое с приемлемым latency, было равно 70000. 
На предыдущем этапе при количестве нод >1 оптимальный rate был равен 30000.  
Были проведены запуски сервера с количеством нод от 1 до 10, параметры ack и from по умолчанию равны кворуму от количества нод и количеству нод соответственно. 
В приведённой таблице видно, что с увеличением размера кластера оптимальное значение rate постепенно снижается. 
В сравнении с прошлым этапом ключевым изменением, влияющим на снижение оптимального rate является добавление репликации, 
в связи с чем теперь (при дефолтных ack, from) нода всегда должна общаться с другими нодами, отчего возрастают затраты времени 
как на отправку запросов и получение ответов, так и на сами операции обработки запросов put/get, которых теперь становится больше в ack раз.  
Можно заметить, что для размеров кластера 2 и 3, 4 и 5 значения ack равны (2 и 3 соответственно), что заметно по получаемым результатам: 
для количества нод 2 и 3 оптимальное значение rate = 20000, значение latency avg около 0.76-0.86 ms, latency для 90% запросов - 1.16-1.26 ms, 
latency max - 10.94-11.91 ms. Значения довольно близкие, тк в обоих случаях нужно доставить запрос одинаковому количеству нод - двум (ack) нодам
(следовательно, затраты на общение по сети и обработку запросов в обоих случаях увеличиваются одинаково - в 2 раза).  
При увеличении значения from значения latency относительно немного возрастают, что связано с уменьшением вероятности того, что ключ запрос будет обработан локально 
на ноде, в которую мы стреляем. Т.е. при ack=from=2 мы понимаем, что запрос обработается локально + отправится на вторую ноду. 
А при ack=2, from=3 мы знаем только то, что запрос точно должен отправиться на одну другую ноду, а произойдет ли вторая его обработка 
локально (т.е. без доп. затрат на общение по протоколу между нодами) или же будет отправлен другой ноде, мы можем только предположить, посчитав вероятность. 
Как раз в связи с этим заметны небольшие увеличения latency с увеличением параметра from - тк увеличивается количество передач запросов по сети.  
Аналогично для размеров кластера 4 и 5, имеющих ack=3.

| Количество нод |  ack/from  | Optimal rate | Latency avg | Latency per 90% | Max latency |
|:--------------:|:----------:|:------------:|:-----------:|:---------------:|:-----------:|
|       2        |    2/2     |    20000     |  764.45 us  |     1.16 ms     |  10.94 ms   |
|       3        |    2/3     |    20000     |   0.86 ms   |     1.26 ms     |  11.91 ms   |
|       4        |    3/4     |    10000     |   0.93 ms   |     1.32 ms     |  17.31 ms   |
|       5        |    3/5     |    10000     |   1.18 ms   |     1.66 ms     |  17.33 ms   |
|       10       |    6/10    |     5000     |   2.54 ms   |     4.15 ms     |  24.09 ms   |

Ниже представлен вывод для варианта с количеством нод 3.  

`wrk -t 64 -c 64 -d 30 -R 20000 -s put.lua -L http://localhost:38081`  
Running 30s test @ http://localhost:38081  
64 threads and 64 connections  
Thread calibration: mean lat.: 0.958ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.997ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.997ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.955ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.934ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.991ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.993ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.981ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.947ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.949ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.982ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 1.011ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.888ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.963ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.921ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.945ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.976ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 1.003ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.986ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.934ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.967ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.971ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.949ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.966ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.911ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.960ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.947ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.903ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.893ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.912ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.923ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.930ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.926ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.924ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.922ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.896ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.942ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.896ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.864ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.862ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.864ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.898ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.890ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.891ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.899ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.839ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.893ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.874ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.872ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.861ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.859ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.840ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.885ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.884ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.891ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.881ms, rate sampling interval: 10ms   
Thread calibration: mean lat.: 0.867ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.836ms, rate sampling interval: 10ms   
Thread calibration: mean lat.: 0.890ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.872ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.864ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.871ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.856ms, rate sampling interval: 10ms  
Thread calibration: mean lat.: 0.870ms, rate sampling interval: 10ms  
Thread Stats   Avg      Stdev     Max   +/- Stdev  
Latency     0.86ms  426.81us  11.90ms   78.73%  
Req/Sec   331.84     39.01   666.00     83.94%  
Latency Distribution (HdrHistogram - Recorded Latency)  
50.000%  842.00us  
75.000%    1.10ms  
90.000%    1.26ms  
99.000%    2.33ms  
99.900%    4.66ms  
99.990%    5.87ms  
99.999%    9.94ms  
100.000%   11.91ms  

Detailed Percentile spectrum:  
Value   Percentile   TotalCount 1/(1-Percentile)  

       0.133     0.000000            1         1.00
       0.398     0.100000        40033         1.11
       0.517     0.200000        79993         1.25
       0.628     0.300000       119899         1.43
       0.737     0.400000       160153         1.67
       0.842     0.500000       200037         2.00
       0.893     0.550000       219800         2.22
       0.944     0.600000       239934         2.50
       0.994     0.650000       259723         2.86
       1.045     0.700000       279692         3.33
       1.095     0.750000       299808         4.00
       1.120     0.775000       309873         4.44
       1.145     0.800000       319700         5.00
       1.170     0.825000       329838         5.71
       1.195     0.850000       339703         6.67
       1.223     0.875000       349482         8.00
       1.241     0.887500       354694         8.89
       1.259     0.900000       359541        10.00
       1.280     0.912500       364462        11.43
       1.306     0.925000       369540        13.33
       1.337     0.937500       374447        16.00
       1.358     0.943750       377039        17.78
       1.382     0.950000       379522        20.00
       1.413     0.956250       381984        22.86
       1.454     0.962500       384437        26.67
       1.514     0.968750       386952        32.00
       1.555     0.971875       388192        35.56
       1.607     0.975000       389440        40.00
       1.672     0.978125       390675        45.71
       1.763     0.981250       391920        53.33
       1.890     0.984375       393173        64.00
       1.974     0.985938       393793        71.11
       2.081     0.987500       394424        80.00
       2.221     0.989062       395042        91.43
       2.403     0.990625       395672       106.67
       2.625     0.992188       396292       128.00
       2.747     0.992969       396601       142.22
       2.875     0.993750       396917       160.00
       3.007     0.994531       397227       182.86
       3.183     0.995313       397536       213.33
       3.365     0.996094       397849       256.00
       3.459     0.996484       398004       284.44
       3.573     0.996875       398161       320.00
       3.713     0.997266       398316       365.71
       3.845     0.997656       398472       426.67
       3.999     0.998047       398630       512.00
       4.111     0.998242       398708       568.89
       4.243     0.998437       398787       640.00
       4.379     0.998633       398867       731.43
       4.511     0.998828       398941       853.33
       4.675     0.999023       399018      1024.00
       4.751     0.999121       399058      1137.78
       4.843     0.999219       399096      1280.00
       4.919     0.999316       399138      1462.86
       5.035     0.999414       399175      1706.67
       5.111     0.999512       399213      2048.00
       5.163     0.999561       399235      2275.56
       5.215     0.999609       399254      2560.00
       5.279     0.999658       399272      2925.71
       5.355     0.999707       399291      3413.33
       5.455     0.999756       399311      4096.00
       5.543     0.999780       399321      4551.11
       5.571     0.999805       399331      5120.00
       5.647     0.999829       399340      5851.43
       5.755     0.999854       399351      6826.67
       5.795     0.999878       399360      8192.00
       5.831     0.999890       399365      9102.22
       5.895     0.999902       399369     10240.00
       5.999     0.999915       399374     11702.86
       6.151     0.999927       399379     13653.33
       6.295     0.999939       399384     16384.00
       6.403     0.999945       399387     18204.44
       6.495     0.999951       399389     20480.00
       6.767     0.999957       399391     23405.71
       7.051     0.999963       399394     27306.67
       7.435     0.999969       399396     32768.00
       7.623     0.999973       399398     36408.89
       7.651     0.999976       399399     40960.00
       7.655     0.999979       399400     46811.43
       7.995     0.999982       399401     54613.33
       8.951     0.999985       399402     65536.00
       9.127     0.999986       399403     72817.78
       9.935     0.999988       399404     81920.00
       9.935     0.999989       399404     93622.86
      10.007     0.999991       399405    109226.67
      10.007     0.999992       399405    131072.00
      10.295     0.999993       399406    145635.56
      10.295     0.999994       399406    163840.00
      10.295     0.999995       399406    187245.71
      10.567     0.999995       399407    218453.33
      10.567     0.999996       399407    262144.00
      10.567     0.999997       399407    291271.11
      10.567     0.999997       399407    327680.00
      10.567     0.999997       399407    374491.43
      11.911     0.999998       399408    436906.67
      11.911     1.000000       399408          inf
[Mean    =        0.864, StdDeviation   =        0.427]  
[Max     =       11.904, Total count    =       399408]  
[Buckets =           27, SubBuckets     =         2048]  
----------------------------------------------------------  
599594 requests in 29.96s, 38.31MB read  
Requests/sec:  20013.94  
Transfer/sec:      1.28MB  


##### GET-запросы на заполненной (1.2 Гб на каждой ноде) БД

Как и для PUT-запросов, были проведены запуски сервера с количеством нод от 2 до 5. В приведённой таблице видно, что с увеличением количества нод (>1) 
оптимальное значение rate не меняется, только увеличиваются все значения latency: и среднее, и для 90% запросов, и для max latency, 
что связано с необходимостью сервера в ack раз больше, чем в прошлом этапе, использовать передачу запросов по HTTP другим нодам.  
Как и для put-запросов, становится заметрым влияние количества дубляжей запросов (равное ack) на производительность. Так, 
в прошлых этапах при размере кластера >1 достигается значение rate = 1000 при среднем значении latency ~20 ms, для 90% запросов latency = ~30 ms. 
Теперь же максимально достигаемый rate = 100, причем теперь значения всех latency в несколько раз больше. 
Можно заметить, что при одинаковом значении ack и разных значениях from (при размерах клстера 2 и 3) хначения latency растут менее, чем в два раза, тк 
в обоих случаях приходится искать ключ на другой ноде, но при этом в первом варианте второе подтверждение ключа мы ищем точно локально 
(т.е. не ходим по сети в другую ноду), а во втором варианте мы либо ищем локально, либо обращаемя к другой ноде, в связи с чем увеличивается время на 
хождение по сети.  
А вот если сравнить варианты с разными значениями ack (например, вариант с количеством нод 3 и ack=2, а также вариант с количеством нод 4 и ack=3), 
можно заметить увеличение latency более, чем в два раза, и здесь ключевое влияние оказывает необходимость искать ключ на еще одной ноде (во втором варианте), 
а итерирование по диску у нас и так самая тяжеловесная операция.  

А такое большое значение max latency, особенно в вариантах с бОльшим количеством нод, вероятно, связано с работой сборщика мусора G1, 
а на put-запросах еще добавляется фоновый flush.

| Количество нод | ack/from | Optimal rate | Latency avg | Latency per 90% | Max latency |
|:--------------:|:--------:|:------------:|:-----------:|:---------------:|:-----------:|
|       2        |   2/2    |     100      |  35.03 ms   |    65.47 ms     |  132.35 ms  |
|       3        |   2/3    |     100      |  58.30 ms   |    88.64 ms     |  180.86 ms  |
|       4        |   3/4    |     100      |  120.83 ms  |    198.53 ms    |  243.97 ms  |
|       5        |   3/5    |     100      |  146.67 ms  |    213.25 ms    |  234.24 ms  |

Ниже представлен вывод для варианта с количеством нод 3.   

`wrk -t 64 -c 64 -d 30 -R 100 -s get.lua -L http://localhost:33069`  
Running 30s test @ http://localhost:33069  
64 threads and 64 connections  
Thread calibration: mean lat.: 19.711ms, rate sampling interval: 44ms  
Thread calibration: mean lat.: 18.741ms, rate sampling interval: 44ms  
Thread calibration: mean lat.: 16.785ms, rate sampling interval: 35ms  
Thread calibration: mean lat.: 18.390ms, rate sampling interval: 42ms  
Thread calibration: mean lat.: 18.072ms, rate sampling interval: 43ms  
Thread calibration: mean lat.: 18.867ms, rate sampling interval: 40ms  
Thread calibration: mean lat.: 26.939ms, rate sampling interval: 59ms  
Thread calibration: mean lat.: 41.944ms, rate sampling interval: 103ms  
Thread calibration: mean lat.: 25.314ms, rate sampling interval: 59ms  
Thread calibration: mean lat.: 41.554ms, rate sampling interval: 89ms  
Thread calibration: mean lat.: 22.540ms, rate sampling interval: 60ms  
Thread calibration: mean lat.: 74.118ms, rate sampling interval: 175ms  
Thread calibration: mean lat.: 38.180ms, rate sampling interval: 107ms  
Thread calibration: mean lat.: 81.320ms, rate sampling interval: 182ms  
Thread calibration: mean lat.: 65.334ms, rate sampling interval: 157ms  
Thread calibration: mean lat.: 52.057ms, rate sampling interval: 113ms  
Thread calibration: mean lat.: 47.077ms, rate sampling interval: 126ms  
Thread calibration: mean lat.: 38.809ms, rate sampling interval: 92ms  
Thread calibration: mean lat.: 26.893ms, rate sampling interval: 66ms  
Thread calibration: mean lat.: 50.964ms, rate sampling interval: 113ms  
Thread calibration: mean lat.: 52.544ms, rate sampling interval: 113ms  
Thread calibration: mean lat.: 67.109ms, rate sampling interval: 166ms  
Thread calibration: mean lat.: 45.404ms, rate sampling interval: 120ms  
Thread calibration: mean lat.: 41.152ms, rate sampling interval: 96ms  
Thread calibration: mean lat.: 64.919ms, rate sampling interval: 156ms  
Thread calibration: mean lat.: 74.916ms, rate sampling interval: 179ms  
Thread calibration: mean lat.: 60.486ms, rate sampling interval: 149ms  
Thread calibration: mean lat.: 36.180ms, rate sampling interval: 83ms  
Thread calibration: mean lat.: 58.162ms, rate sampling interval: 153ms  
Thread calibration: mean lat.: 70.484ms, rate sampling interval: 157ms  
Thread calibration: mean lat.: 43.138ms, rate sampling interval: 104ms  
Thread calibration: mean lat.: 77.221ms, rate sampling interval: 196ms  
Thread calibration: mean lat.: 73.047ms, rate sampling interval: 174ms  
Thread calibration: mean lat.: 66.866ms, rate sampling interval: 173ms   
Thread calibration: mean lat.: 36.815ms, rate sampling interval: 90ms  
Thread calibration: mean lat.: 76.569ms, rate sampling interval: 178ms  
Thread calibration: mean lat.: 28.974ms, rate sampling interval: 82ms  
Thread calibration: mean lat.: 54.218ms, rate sampling interval: 138ms  
Thread calibration: mean lat.: 71.552ms, rate sampling interval: 170ms  
Thread calibration: mean lat.: 76.282ms, rate sampling interval: 182ms  
Thread calibration: mean lat.: 59.386ms, rate sampling interval: 153ms  
Thread calibration: mean lat.: 60.793ms, rate sampling interval: 143ms  
Thread calibration: mean lat.: 54.634ms, rate sampling interval: 134ms   
Thread calibration: mean lat.: 78.640ms, rate sampling interval: 175ms  
Thread calibration: mean lat.: 30.993ms, rate sampling interval: 66ms  
Thread calibration: mean lat.: 68.026ms, rate sampling interval: 168ms  
Thread calibration: mean lat.: 65.906ms, rate sampling interval: 152ms  
Thread calibration: mean lat.: 49.492ms, rate sampling interval: 116ms  
Thread calibration: mean lat.: 74.472ms, rate sampling interval: 170ms  
Thread calibration: mean lat.: 61.513ms, rate sampling interval: 149ms  
Thread calibration: mean lat.: 70.032ms, rate sampling interval: 179ms  
Thread calibration: mean lat.: 60.659ms, rate sampling interval: 146ms  
Thread calibration: mean lat.: 78.674ms, rate sampling interval: 176ms  
Thread calibration: mean lat.: 84.288ms, rate sampling interval: 185ms  
Thread calibration: mean lat.: 82.993ms, rate sampling interval: 183ms  
Thread calibration: mean lat.: 82.507ms, rate sampling interval: 193ms  
Thread calibration: mean lat.: 87.072ms, rate sampling interval: 195ms  
Thread calibration: mean lat.: 87.188ms, rate sampling interval: 194ms  
Thread calibration: mean lat.: 85.356ms, rate sampling interval: 184ms  
Thread calibration: mean lat.: 87.704ms, rate sampling interval: 195ms   
Thread calibration: mean lat.: 85.679ms, rate sampling interval: 195ms  
Thread calibration: mean lat.: 84.448ms, rate sampling interval: 189ms  
Thread calibration: mean lat.: 84.076ms, rate sampling interval: 179ms  
Thread calibration: mean lat.: 88.028ms, rate sampling interval: 191ms  
Thread Stats   Avg      Stdev     Max   +/- Stdev  
Latency    58.30ms   28.99ms 180.74ms   67.54%  
Req/Sec     1.48      4.07    29.00     92.62%  
Latency Distribution (HdrHistogram - Recorded Latency)  
50.000%   59.23ms  
75.000%   79.68ms   
90.000%   88.64ms  
99.000%  166.78ms  
99.900%  180.22ms  
99.990%  180.86ms  
99.999%  180.86ms  
100.000%  180.86ms  

Detailed Percentile spectrum:  
Value   Percentile   TotalCount 1/(1-Percentile)  

      13.791     0.000000            1         1.00  
      18.319     0.100000          199         1.11  
      28.271     0.200000          397         1.25  
      38.783     0.300000          596         1.43  
      48.863     0.400000          794         1.67  
      59.231     0.500000          992         2.00  
      64.319     0.550000         1092         2.22  
      69.247     0.600000         1191         2.50  
      73.087     0.650000         1293         2.86  
      76.479     0.700000         1389         3.33  
      79.679     0.750000         1489         4.00  
      81.215     0.775000         1539         4.44  
      82.431     0.800000         1588         5.00  
      83.903     0.825000         1639         5.71  
      85.247     0.850000         1687         6.67  
      86.271     0.875000         1736         8.00  
      87.359     0.887500         1765         8.89  
      88.639     0.900000         1786        10.00  
      89.599     0.912500         1811        11.43  
      91.071     0.925000         1837        13.33  
      92.479     0.937500         1860        16.00  
      93.631     0.943750         1875        17.78  
      93.951     0.950000         1885        20.00  
      94.975     0.956250         1898        22.86  
      95.807     0.962500         1910        26.67  
      98.047     0.968750         1922        32.00  
      99.967     0.971875         1929        35.56  
     102.463     0.975000         1935        40.00  
     103.999     0.978125         1942        45.71  
     135.423     0.981250         1948        53.33  
     153.087     0.984375         1953        64.00  
     154.111     0.985938         1957        71.11  
     160.255     0.987500         1960        80.00  
     166.527     0.989062         1963        91.43  
     167.423     0.990625         1966       106.67  
     168.063     0.992188         1970       128.00  
     175.231     0.992969         1971       142.22  
     176.767     0.993750         1973       160.00  
     176.895     0.994531         1975       182.86  
     176.895     0.995313         1975       213.33  
     177.023     0.996094         1978       256.00  
     177.023     0.996484         1978       284.44  
     177.023     0.996875         1978       320.00  
     177.279     0.997266         1979       365.71   
     180.095     0.997656         1981       426.67  
     180.095     0.998047         1981       512.00  
     180.095     0.998242         1981       568.89  
     180.095     0.998437         1981       640.00  
     180.223     0.998633         1982       731.43  
     180.223     0.998828         1982       853.33  
     180.479     0.999023         1983      1024.00   
     180.479     0.999121         1983      1137.78  
     180.479     0.999219         1983      1280.00  
     180.479     0.999316         1983      1462.86  
     180.479     0.999414         1983      1706.67  
     180.863     0.999512         1984      2048.00  
     180.863     1.000000         1984          inf  
[Mean    =       58.299, StdDeviation   =       28.994]  
[Max     =      180.736, Total count    =         1984]  
[Buckets =           27, SubBuckets     =         2048]  
----------------------------------------------------------  
3008 requests in 30.01s, 266.69KB read  
Requests/sec:    100.22  
Transfer/sec:      8.89KB  
 


#### **Профилирование (CPU, alloc, lock) с помощью async-profiler**
##### PUT-запросы
**CPU**


|                                                                     cluster size = 3                                                                     |                                                                      replication 2/3                                                                      |
|:--------------------------------------------------------------------------------------------------------------------------------------------------------:|:---------------------------------------------------------------------------------------------------------------------------------------------------------:|
| ![Иллюстрация к проекту](https://github.com/Anilochka/2022-highload-dht-1/blob/stage3/src/main/java/ok/dht/test/shestakova/report/jpg/stage3/put_cpu.jpg) | ![Иллюстрация к проекту](https://github.com/Anilochka/2022-highload-dht-1/blob/stage4/src/main/java/ok/dht/test/shestakova/report/jpg/stage44/put_cpu.jpg) |

Сильных отличий между новой версией и stage 3 в контексте cpu-профилирования put запросов практически нет. Единственно, в новой реализации 
пул воркеров теперь не только обрабатывает локальный запрос, но еще и занимается отправкой запросов другим нодам и получением ответов от них, 
чем раньше занимались селекторы, а в связи с этими изменениями теперь SelectorThread занимает на 10% меньше процессорного времени.  
Далее стоит оставить пулу воркеров только обработку локальных запросов, как это было в предыдущей версии, а для отправки запросов и получения ответов от других нод 
стоит добавить еще один ExecutorService, чтобы мы могли проводить общение с другими нодами параллельно, отчего время получения ответов от всех нод 
теоретически должно сократиться и быть не больше, чем время получения ответа от ноды, коммуникация с которой самая медленная.  
Также в новой реализации увеличилось время для получения нод c использованием Randezvous Hashing (c 0.22% до 1.73%), тк 
теперь используется TreeMap, stream() и toList()(0.75%). Далее стоит, вероятно, попытаться убрать стримы (еще не успела).  

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

|                                                                      cluster size = 3                                                                      |                                                                      replication 2/3                                                                       |
|:----------------------------------------------------------------------------------------------------------------------------------------------------------:|:----------------------------------------------------------------------------------------------------------------------------------------------------------:|
| ![Иллюстрация к проекту](https://github.com/Anilochka/2022-highload-dht-1/blob/stage3/src/main/java/ok/dht/test/shestakova/report/jpg/stage3/put_alloc.jpg) | ![Иллюстрация к проекту](https://github.com/Anilochka/2022-highload-dht-1/blob/stage4/src/main/java/ok/dht/test/shestakova/report/jpg/stage4/put_alloc.jpg) |

Как и при cpu-профилировании, сильных изменении нет. Селекторы производят столько же аллокаций, только теперь аллокации на 
отправку запроса и получение ответа от других нод перешли на пул воркеров, вместо них теперь добавилось 16% аллокаций из-за использования TreeMap, 
как уже писалось выше.  
Теперь 25% аллокаций пула воркеров происходят при появившемся общении с другими нодами (раньше это делали селекторы).

Сравнение однонодовым вариантом (stage 2):  
В отличие от сервиса со 2 стейджа появились аллокации для SelctorManager клиента (2.6%), включающие аллокации на взятиях задач с очереди, 
чтение из сокета, итерацию по задачам клиента, итерацию по пулу соединений, аллокации DirectMethodHandler-а для вызова методов.   

В SelectorThread-е добавилась работа с клиентами: отправление запросов с проксированием, получение ответов, обеспечение HTTP-общения клиентов, 
создание соединения, добавились аллокации на работу планировщика SequentalScheduler (12%), обработку CompletableFuture. 

**lock**

|                                                                     cluster size = 3                                                                      |                                                                     replication 2/3)                                                                      |
|:---------------------------------------------------------------------------------------------------------------------------------------------------------:|:---------------------------------------------------------------------------------------------------------------------------------------------------------:|
| ![Иллюстрация к проекту](https://github.com/Anilochka/2022-highload-dht-1/blob/stage3/src/main/java/ok/dht/test/shestakova/report/jpg/stage3/put_lock.jpg) | ![Иллюстрация к проекту](https://github.com/Anilochka/2022-highload-dht-1/blob/stage4/src/main/java/ok/dht/test/shestakova/report/jpg/stage4/put_lock.jpg) |

По сравнению с предыдущим вариантом увеличилось процентное соотвношение локов при взятии задач из очереди пула воркеров (на 3%), что связано 
с увеличением количества задач, приходящих воркерам (из-за необходимости дублировать запросы других нодам).  

Сравнение с однонодовым вариантом:  
В отличие от сервиса со 2 стейджа на SelectorThread-е теперь практически нет лока при вызове метода Session.process.  
Добавились локи на клиенте - SelectorManager (Selector.select, работа с пулом соединений, обеспечение асинхронного обмена запросами и ответами).  
На воркерах также появились локи, связанных с появившимся общением нод по сети (регистрация нового event SelectorManager-ом).  
Таким образом теперь сама обработка запросов занимает менее 3% от общего числа локов (на прошлом стейдже было ~82%).

##### GET-запросы
**CPU**

|                                                                     cluster size = 3                                                                     |                                                                     replication 2/3                                                                      |
|:--------------------------------------------------------------------------------------------------------------------------------------------------------:|:--------------------------------------------------------------------------------------------------------------------------------------------------------:|
| ![Иллюстрация к проекту](https://github.com/Anilochka/2022-highload-dht-1/blob/stage3/src/main/java/ok/dht/test/shestakova/report/jpg/stage3/get_cpu.jpg) | ![Иллюстрация к проекту](https://github.com/Anilochka/2022-highload-dht-1/blob/stage4/src/main/java/ok/dht/test/shestakova/report/jpg/stage4/get_cpu.jpg) |


Как и в put-запросах, сильных изменений нет. Использование процессорного времени SelectorThread-ом сократилось с 1.28% дл 0.19% 
за счет переноса общения с другими нодами (передачи запросов и получения ответов) на пул воркеров.  
Все так же около 93% процессорного времени занимает хождение по диску в поисках ключа, только теперь на пул воркеров добавилось немного (около 1%) 
затрат на общение с другими нодами (которое раньшебыло на селекторах).

**alloc**

|                                                                      cluster size = 3                                                                      |                                                                      replication 2/3                                                                       |
|:----------------------------------------------------------------------------------------------------------------------------------------------------------:|:----------------------------------------------------------------------------------------------------------------------------------------------------------:|
| ![Иллюстрация к проекту](https://github.com/Anilochka/2022-highload-dht-1/blob/stage3/src/main/java/ok/dht/test/shestakova/report/jpg/stage3/get_alloc.jpg) | ![Иллюстрация к проекту](https://github.com/Anilochka/2022-highload-dht-1/blob/stage4/src/main/java/ok/dht/test/shestakova/report/jpg/stage4/get_alloc.jpg) |


Значительно (с 16% до 5%) снизилось количество аллокаций на селекторах, связвнное с переносом общения с другими нодами на пул воркеров. 
Теперь селекторы выполняют select и работу с сессиями.
В следствие этого добавились аллокации на пуле воркеров по общению с другими нодами ().  
С 30% до 69% увеличилось количество аллокаций, затрачиваемых при итерировании по диску в поисках ключа (тк теперь мы это делаем в два раза больше, 
по крайней мере в рассматриваемом примере с ack=2).


**lock**

|                                                                     cluster size = 3                                                                      |                                                                      replication 2/3                                                                      |
|:---------------------------------------------------------------------------------------------------------------------------------------------------------:|:---------------------------------------------------------------------------------------------------------------------------------------------------------:|
| ![Иллюстрация к проекту](https://github.com/Anilochka/2022-highload-dht-1/blob/stage3/src/main/java/ok/dht/test/shestakova/report/jpg/stage3/get_lock.jpg) | ![Иллюстрация к проекту](https://github.com/Anilochka/2022-highload-dht-1/blob/stage4/src/main/java/ok/dht/test/shestakova/report/jpg/stage4/get_lock.jpg) |

ThreadPoolExecutor (75% локов):  
При работе пула воркеров появились локи, связанные с общением нод (отправка запроса клиентом), тк раньше эту работу выполняли селекторы.  
24% занимают локи на обработке CompletableFuture, регистрацию событий SelectorManager-ом клиента.  
На взятие задачи из очереди воркеров уходит 6% от общего числа локов.  
Добавилось 4% локов для при работе клиента (асинхронная отправка запросов нескольким нодам, а раньше отправляли только одной).  
40% локов используется для планировщика, асинхронно принимающего запросы/ответы, для регистрации нового event SelectorManager-ом. 

SelectorThread:  
И около 2% забирает synchronized метод process из пакета one.nio.net, вызывающий методы чтения и записи в сокет.  

SelectorManager (22% локов)
Работает с пулом соединений.
 
Если сравнивать результаты с результатами предыдущего этапа, можно заметить, что производительность снизилась, 
что связано с необходимостью сервера увеличивать в ack раз количество работы по обработке запросов и по передаче запросов по HTTP другим нодам.  
В основном на изменение результатов профилирования повлияло то, что в новой реализации пул воркеров теперь не только обрабатывает локальный запрос, н
о еще и занимается отправкой запросов другим нодам и получением ответов от них, чем раньше занимались селекторы.  
Также в новой реализации увеличилось процессорное время и количество аллокаций для получения нод c использованием Randezvous Hashing, тк
теперь используется TreeMap, stream() и toList(), теперь надо попытаться убрать стримы (еще не успела).  
Кроме того, стоит добавить еще один экзекьютор для проксирования запросов на другие ноды, чтобы локальая обработка запросов и работа с запросами к другим нодам 
осуществлялись параллельно.  

Как и на прошлых этапах:
Для оптимизации однозначно стоит рассмотреть поиск ключа на диске: подумать о фоновом компакшене, а также о других способах, которые позволили бы меньше "ходить" по диску.  
Для ускорения работы пула воркеров можно поискать более оптимальную структуру данных для очереди задач воркеров, которая бы затрачивала меньше времени на блокировки, что особенно актуально для get-запросов, в которых появление очереди задач не дало особого выигрыша в производительности из-за появившихся блокировок взятия/размещения в очереди задач.  
Кроме того можно попробовать другие коллекции для очереди задач клиента; также можно посмотреть другие протоколы для общения между клиентами.   
