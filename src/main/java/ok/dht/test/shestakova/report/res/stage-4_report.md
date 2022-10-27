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


##### GET-запросы на заполненной (1.4 Гб) БД

Как и для PUT-запросов, были проведены запуски сервера с количеством нод от 1 до 10. В приведённой таблице видно, что с увеличением количества нод (>1) 
оптимальное значение rate не меняется, только увеличивается latency для 90% запросов и max latency, что связано с необходимостью сервера дополнительно использовать передачу запросов по HTTP другим нодам.  
В среднем при размере кластера >1 достигается значение rate = 1000 при среднем значении latency ~20 ms, для 90% запросов latency = ~30 ms.  

В отличие от варианта с размером кластера = 1, в других вариантах значительно увеличивается значение max latency (в 2-3 раза, а в варианте с количеством нод = 10 - в 13.5 раз).
Предполагаю, что, как и с put-запросами, на это влияет передача запросов и получение ответов от других нод. 
Если говорить про значение latency при нескольких нодах, небольшой разброс значений, вероятно, связан 
с тем, в какую ноду мы стреляли, и в какую ноду далее распределялся ключ, исходя из чего какая-то нода могла становиться более востребованной, 
что связано с использованием не очень хорошей хеш-функции. 
А такое большое значение max latency, особенно в многонодовых вариантах, вероятно, связано с работой сборщика мусора G1, 
а на put-запросах еще добавляется фоновый flush.


| Количество нод | Optimal rate | Latency avg | Latency per 90% | Max latency |
|:--------------:|:------------:|:-----------:|:---------------:|:-----------:|
|       1        |     1000     |  14.68 ms   |    22.78 ms     |  58.56 ms   |
|       2        |     1000     |  19.68 ms   |    32.37 ms     |  120.13 ms  |
|       3        |     1000     |  26.05 ms   |    39.97 ms     |  166.27 ms  |
|       4        |     1000     |  16.44 ms   |    27.57 ms     |  109.76 ms  |
|       5        |     1000     |  25.17 ms   |    42.72 ms     |  130.56 ms  |
|       10       |     1000     |  18.12 ms   |    28.75 ms     |  787.97 ms  |

UPD: в таблице выше приведены результаты с использованием плохой хеш-функции, впоследствии замененной на MurMur3,
ниже в таблице приведены обновленные результаты.  

| Количество нод | Optimal rate | Latency avg | Latency per 90% | Max latency |
|:--------------:|:------------:|:-----------:|:---------------:|:-----------:|
|       2        |     1000     |  23.87 ms   |    38.88 ms     |  141.82 ms  |
|       3        |     1000     |  18.31 ms   |    30.99 ms     |  141.18 ms  |
|       4        |     1000     |  14.66 ms   |    23.52 ms     |  178.69 ms  |
|       5        |     1000     |  29.12 ms   |    46.81 ms     |  213.25 ms  |

Ниже представлен вывод для варианта с количеством нод 3.   

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


|                                                                     cluster size = 3                                                                     |                                                                      replication 2/3                                                                      |
|:--------------------------------------------------------------------------------------------------------------------------------------------------------:|:---------------------------------------------------------------------------------------------------------------------------------------------------------:|
| ![Иллюстрация к проекту](https://github.com/Anilochka/2022-highload-dht1/blob/stage3/src/main/java/ok/dht/test/shestakova/report/jpg/stage3/put_cpu.jpg) | ![Иллюстрация к проекту](https://github.com/Anilochka/2022-highload-dht1/blob/stage4/src/main/java/ok/dht/test/shestakova/report/jpg/stage44/put_cpu.jpg) |

Сильных отличий между новой версией и stage 3 в контексте cpu-профилирования put запросов практически нет. Единственно, в новой реализации 
пул воркеров теперь не только обрабатывает локальный запрос, но еще и занимается отправкой запросов другим нодам и получением ответов от них, 
чем рантше занимались селекторы, а в связи с этими изменениями теперь SelectorThread занимает на 10% меньше процессорного времени.  
Далее стоит оставить пулу воркеров только обработку локальных запросов, как это было в предыдущей версии, а для отправки запросов и получения ответов от других нод 
стоит добавить еще один ExecutorService, чтобы мы могли проводить общение с другими нодами параллельно, отчего время получения ответов от всех нод 
теоретически должно сократиться и быть не больше, чем время получения ответа от ноды, коммуникация с которой самая медленная.  
Также в новой реализации увеличилось время для получения нод c bcgjkmpjdfybtv Randezvous Hashing (c 0.22% до 1.73%), тк 
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
| ![Иллюстрация к проекту](https://github.com/Anilochka/2022-highload-dht1/blob/stage3/src/main/java/ok/dht/test/shestakova/report/jpg/stage3/put_alloc.jpg) | ![Иллюстрация к проекту](https://github.com/Anilochka/2022-highload-dht1/blob/stage4/src/main/java/ok/dht/test/shestakova/report/jpg/stage4/put_alloc.jpg) |

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
| ![Иллюстрация к проекту](https://github.com/Anilochka/2022-highload-dht1/blob/stage3/src/main/java/ok/dht/test/shestakova/report/jpg/stage3/put_lock.jpg) | ![Иллюстрация к проекту](https://github.com/Anilochka/2022-highload-dht1/blob/stage4/src/main/java/ok/dht/test/shestakova/report/jpg/stage4/put_lock.jpg) |

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
| ![Иллюстрация к проекту](https://github.com/Anilochka/2022-highload-dht1/blob/stage3/src/main/java/ok/dht/test/shestakova/report/jpg/stage3/get_cpu.jpg) | ![Иллюстрация к проекту](https://github.com/Anilochka/2022-highload-dht1/blob/stage4/src/main/java/ok/dht/test/shestakova/report/jpg/stage4/get_cpu.jpg) |


Как и в put-запросах, появился SelectorManager, работающий с очередью задач клиента (занимает всего 0.5% процессорного времени). 
Воркеры так же занимают большую часть процессорного времени (97%), в основном итерируясь по диску в поиске ключа, 
а также занимаясь отправкой запросов (не более 2% времени).  

**alloc**

|                                                                      cluster size = 3                                                                      |                                                                      replication 2/3                                                                       |
|:----------------------------------------------------------------------------------------------------------------------------------------------------------:|:----------------------------------------------------------------------------------------------------------------------------------------------------------:|
| ![Иллюстрация к проекту](https://github.com/Anilochka/2022-highload-dht1/blob/stage3/src/main/java/ok/dht/test/shestakova/report/jpg/stage3/get_alloc.jpg) | ![Иллюстрация к проекту](https://github.com/Anilochka/2022-highload-dht1/blob/stage4/src/main/java/ok/dht/test/shestakova/report/jpg/stage4/get_alloc.jpg) |

В контексте аллокаций также видно добавление аллокаций для работы с клиетами (отправка/получение запросов/ответов, 
аллокации на создании URI, синхронизация планировщика задач, парсинг заголовков, работа с CompletableFuture).
Из 80% аллокаций, создаваемых на ThreadPoolExecutore, только 30% идут на итерацию по диску, остальные же - 
результат добавления общения нод по http.


**lock**

|                                                                     cluster size = 3                                                                      |                                                                      replication 2/3                                                                      |
|:---------------------------------------------------------------------------------------------------------------------------------------------------------:|:---------------------------------------------------------------------------------------------------------------------------------------------------------:|
| ![Иллюстрация к проекту](https://github.com/Anilochka/2022-highload-dht1/blob/stage3/src/main/java/ok/dht/test/shestakova/report/jpg/stage3/get_lock.jpg) | ![Иллюстрация к проекту](https://github.com/Anilochka/2022-highload-dht1/blob/stage4/src/main/java/ok/dht/test/shestakova/report/jpg/stage4/get_lock.jpg) |

ThreadPoolExecutor (80% локов):  
22% занимают локи на обработке CompletableFuture, регистрацию событий SelectorManager-ом клиента.  
Теперь вместо 74% на взятие задачи из очереди воркеров уходит 14% от общего числа локов.
45% локов используется для планировщика, асинхронно принимающего запросы/ответы, для регистрации нового event SelectorManager-ом. 

SelectorThread:  
И около 2% забирает synchronized метод process из пакета one.nio.net, вызывающий методы чтения и записи в сокет.  

SelectorManager (18% локов)
Работает с пулом соединений.
 
Если сравнивать результаты с результатами предыдущего этапа, можно заметить, что производительность снизилась, 
что связано с необходимостью сервера дополнительно использовать передачу запросов по HTTP другим нодам.  
В связи с этими изменениями главным образом увеличилось количество аллокаций и локов, 
а процессорное время в основном увеличилось для put-запросов, тк get-запросы все так же остаются тяжеловесными сами по себе (по работе с диском).     

Как и на прошлых этапах:
Для оптимизации однозначно стоит рассмотреть поиск ключа на диске: подумать о фоновом компакшене, а также о других способах, которые позволили бы меньше "ходить" по диску.  
Для ускорения работы пула воркеров можно поискать более оптимальную структуру данных для очереди задач воркеров, которая бы затрачивала меньше времени на блокировки, что особенно актуально для get-запросов, в которых появление очереди задач не дало особого выигрыша в производительности из-за появившихся блокировок взятия/размещения в очереди задач.  
Кроме того можно попробовать другие коллекции для очереди задач клиента; также можно посмотреть другие протоколы для общения между клиентами.  

Еще было бы интересно одновременно нагрузить несколько нод (или все) с помощью wrk и посмотреть на их взаимодействие. (это пока не успела)  
