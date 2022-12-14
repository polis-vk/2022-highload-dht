Все результаты получены при 3 запущенных в разных процессах нодах на одном компьютере.

# Нагрузчное тестирование

Я стрелял только в одну ноду, она посылала запросы остальным. Необходимо ожидать, что
результаты будут хуже, чем в предыдущем этапе, так как мы все еще делим ресурсы одного компьютера, но теперь мы ждем ответа от from нод (в прошлом этапе можно считать что было from = 1)
, это повышает нашу отказоустойчивость и снижает задержку на латенси в случае реплик, расположенных географически в разных частях света.
В анализе я испытывал параметры ack = 2, from = 3 (такие будут если их не указывать в запросе), так как это
сбалансированное соотношение между отказоустойчивостью и производительностью.

## PUT
```
math.randomseed(os.time())

function request()
  path = "/v0/entity?id=" .. tostring(math.random(1, 10000000))
  body = tostring(math.random(1, 10000000))
  return wrk.format("PUT", path, wrk.headers, body)
end
```
Методом дихотомии была найдена точка разладки на 3100 RPS, сервис не смог справиться с заданным RPS.
Сервис справился с точкой разладки относительно хуже, чем в предыдущем этапе, график латенси более крутой, особенно это заметно ближе к нулевым перцентилям,
где на предыдущем этапе сервис героически сражался.


```
wrk -d 120 -t 4 -c 64 -R 3100 -L -s ../put.lua http://localhost:19234
Running 2m test @ http://localhost:19234
  4 threads and 64 connections
  Thread calibration: mean lat.: 110.589ms, rate sampling interval: 416ms
  Thread calibration: mean lat.: 110.257ms, rate sampling interval: 414ms
  Thread calibration: mean lat.: 121.114ms, rate sampling interval: 418ms
  Thread calibration: mean lat.: 110.424ms, rate sampling interval: 414ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     2.22s     1.24s    4.74s    60.62%
    Req/Sec   743.71     14.50   772.00     75.09%
  Latency Distribution (HdrHistogram - Recorded Latency)
 50.000%    2.12s
 75.000%    3.22s
 90.000%    4.06s
 99.000%    4.67s
 99.900%    4.72s
 99.990%    4.73s
 99.999%    4.74s
100.000%    4.74s

  Detailed Percentile spectrum:
       Value   Percentile   TotalCount 1/(1-Percentile)

     226.815     0.000000            1         1.00
     581.119     0.100000        32712         1.11
    1001.471     0.200000        65392         1.25
    1368.063     0.300000        98059         1.43
    1734.655     0.400000       130730         1.67
    2117.631     0.500000       163443         2.00
    2295.807     0.550000       179823         2.22
    2494.463     0.600000       196172         2.50
    2701.311     0.650000       212550         2.86
    2902.015     0.700000       228860         3.33
    3219.455     0.750000       245198         4.00
    3354.623     0.775000       253379         4.44
    3467.263     0.800000       261470         5.00
    3596.287     0.825000       269709         5.71
    3756.031     0.850000       277819         6.67
    3905.535     0.875000       286053         8.00
    3979.263     0.887500       290108         8.89
    4055.039     0.900000       294146        10.00
    4149.247     0.912500       298316        11.43
    4218.879     0.925000       302314        13.33
    4296.703     0.937500       306432        16.00
    4337.663     0.943750       308432        17.78
    4378.623     0.950000       310517        20.00
    4419.583     0.956250       312538        22.86
    4472.831     0.962500       314693        26.67
    4501.503     0.968750       316651        32.00
    4521.983     0.971875       317646        35.56
    4538.367     0.975000       318653        40.00
    4558.847     0.978125       319855        45.71
    4575.231     0.981250       320871        53.33
    4620.287     0.984375       321811        64.00
    4632.575     0.985938       322381        71.11
    4644.863     0.987500       322825        80.00
    4657.151     0.989062       323270        91.43
    4669.439     0.990625       323962       106.67
    4677.631     0.992188       324476       128.00
    4681.727     0.992969       324644       142.22
    4689.919     0.993750       324786       160.00
    4706.303     0.994531       325247       182.86
    4710.399     0.995313       325601       213.33
    4710.399     0.996094       325601       256.00
    4714.495     0.996484       325975       284.44
    4714.495     0.996875       325975       320.00
    4714.495     0.997266       325975       365.71
    4718.591     0.997656       326278       426.67
    4718.591     0.998047       326278       512.00
    4718.591     0.998242       326278       568.89
    4722.687     0.998437       326509       640.00
    4722.687     0.998633       326509       731.43
    4722.687     0.998828       326509       853.33
    4722.687     0.999023       326509      1024.00
    4726.783     0.999121       326663      1137.78
    4726.783     0.999219       326663      1280.00
    4726.783     0.999316       326663      1462.86
    4726.783     0.999414       326663      1706.67
    4726.783     0.999512       326663      2048.00
    4730.879     0.999561       326758      2275.56
    4730.879     0.999609       326758      2560.00
    4730.879     0.999658       326758      2925.71
    4730.879     0.999707       326758      3413.33
    4730.879     0.999756       326758      4096.00
    4730.879     0.999780       326758      4551.11
    4730.879     0.999805       326758      5120.00
    4730.879     0.999829       326758      5851.43
    4734.975     0.999854       326789      6826.67
    4734.975     0.999878       326789      8192.00
    4734.975     0.999890       326789      9102.22
    4734.975     0.999902       326789     10240.00
    4734.975     0.999915       326789     11702.86
    4734.975     0.999927       326789     13653.33
    4739.071     0.999939       326806     16384.00
    4739.071     0.999945       326806     18204.44
    4739.071     0.999951       326806     20480.00
    4739.071     0.999957       326806     23405.71
    4739.071     0.999963       326806     27306.67
    4739.071     0.999969       326806     32768.00
    4739.071     0.999973       326806     36408.89
    4739.071     0.999976       326806     40960.00
    4739.071     0.999979       326806     46811.43
    4743.167     0.999982       326812     54613.33
    4743.167     1.000000       326812          inf
#[Mean    =     2215.899, StdDeviation   =     1244.231]
#[Max     =     4739.072, Total count    =       326812]
#[Buckets =           27, SubBuckets     =         2048]
----------------------------------------------------------
  356517 requests in 2.00m, 22.78MB read
Requests/sec:   2970.96
Transfer/sec:    194.39KB
```


Отступив на 20% от точки разладки (на 2500 RPS) видим, что сервис начал справляться. 

```
wrk -d 120 -t 4 -c 64 -R 2500 -L -s ../put.lua http://localhost:19234
Running 2m test @ http://localhost:19234
  4 threads and 64 connections
  Thread calibration: mean lat.: 2.610ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.616ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.596ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 2.572ms, rate sampling interval: 10ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     2.54ms    1.19ms  24.62ms   73.15%
    Req/Sec   658.49    102.42     1.11k    62.17%
  Latency Distribution (HdrHistogram - Recorded Latency)
 50.000%    2.42ms
 75.000%    3.17ms
 90.000%    3.92ms
 99.000%    6.18ms
 99.900%   11.17ms
 99.990%   16.53ms
 99.999%   20.96ms
100.000%   24.64ms

  Detailed Percentile spectrum:
       Value   Percentile   TotalCount 1/(1-Percentile)

       0.609     0.000000            1         1.00
       1.191     0.100000        27548         1.11
       1.520     0.200000        55004         1.25
       1.817     0.300000        82479         1.43
       2.125     0.400000       110047         1.67
       2.423     0.500000       137404         2.00
       2.569     0.550000       151160         2.22
       2.715     0.600000       165013         2.50
       2.861     0.650000       178655         2.86
       3.013     0.700000       192470         3.33
       3.173     0.750000       206132         4.00
       3.261     0.775000       213000         4.44
       3.357     0.800000       219881         5.00
       3.463     0.825000       226749         5.71
       3.589     0.850000       233659         6.67
       3.735     0.875000       240459         8.00
       3.821     0.887500       243896         8.89
       3.917     0.900000       247353        10.00
       4.027     0.912500       250774        11.43
       4.155     0.925000       254274        13.33
       4.295     0.937500       257656        16.00
       4.375     0.943750       259369        17.78
       4.463     0.950000       261102        20.00
       4.563     0.956250       262819        22.86
       4.675     0.962500       264528        26.67
       4.823     0.968750       266237        32.00
       4.907     0.971875       267075        35.56
       5.015     0.975000       267957        40.00
       5.143     0.978125       268789        45.71
       5.303     0.981250       269657        53.33
       5.515     0.984375       270518        64.00
       5.655     0.985938       270939        71.11
       5.831     0.987500       271376        80.00
       6.031     0.989062       271802        91.43
       6.311     0.990625       272228       106.67
       6.631     0.992188       272658       128.00
       6.815     0.992969       272868       142.22
       7.007     0.993750       273084       160.00
       7.251     0.994531       273298       182.86
       7.531     0.995313       273517       213.33
       7.911     0.996094       273727       256.00
       8.131     0.996484       273835       284.44
       8.415     0.996875       273944       320.00
       8.719     0.997266       274049       365.71
       9.023     0.997656       274158       426.67
       9.479     0.998047       274266       512.00
       9.711     0.998242       274317       568.89
      10.015     0.998437       274371       640.00
      10.335     0.998633       274426       731.43
      10.719     0.998828       274478       853.33
      11.247     0.999023       274532      1024.00
      11.511     0.999121       274560      1137.78
      11.943     0.999219       274586      1280.00
      12.367     0.999316       274613      1462.86
      12.999     0.999414       274641      1706.67
      13.511     0.999512       274666      2048.00
      13.823     0.999561       274681      2275.56
      14.327     0.999609       274693      2560.00
      14.663     0.999658       274707      2925.71
      15.031     0.999707       274720      3413.33
      15.391     0.999756       274733      4096.00
      15.487     0.999780       274740      4551.11
      15.639     0.999805       274747      5120.00
      15.903     0.999829       274754      5851.43
      16.071     0.999854       274760      6826.67
      16.343     0.999878       274767      8192.00
      16.463     0.999890       274772      9102.22
      16.575     0.999902       274774     10240.00
      16.751     0.999915       274777     11702.86
      16.943     0.999927       274780     13653.33
      17.327     0.999939       274784     16384.00
      17.439     0.999945       274785     18204.44
      17.567     0.999951       274787     20480.00
      17.711     0.999957       274789     23405.71
      17.935     0.999963       274790     27306.67
      18.239     0.999969       274792     32768.00
      18.367     0.999973       274793     36408.89
      18.399     0.999976       274794     40960.00
      18.431     0.999979       274795     46811.43
      18.431     0.999982       274795     54613.33
      19.423     0.999985       274796     65536.00
      20.959     0.999986       274797     72817.78
      20.959     0.999988       274797     81920.00
      23.503     0.999989       274798     93622.86
      23.503     0.999991       274798    109226.67
      23.503     0.999992       274798    131072.00
      23.871     0.999993       274799    145635.56
      23.871     0.999994       274799    163840.00
      23.871     0.999995       274799    187245.71
      23.871     0.999995       274799    218453.33
      23.871     0.999996       274799    262144.00
      24.639     0.999997       274800    291271.11
      24.639     1.000000       274800          inf
#[Mean    =        2.537, StdDeviation   =        1.194]
#[Max     =       24.624, Total count    =       274800]
#[Buckets =           27, SubBuckets     =         2048]
----------------------------------------------------------
  299936 requests in 2.00m, 19.16MB read
Requests/sec:   2499.46
Transfer/sec:    163.54KB
```

График latency растет сильно медленее, как и в предыдущих этапах максимум latency сильно отличается от среднего,
присутствуют скачки в latency у крайне правых перцентилей, но в отличии от предыдушего этапа скачки в латенси тут более ярко выражены.
Посмотрев в логи GC, которые собирались во время нагрузочного тестирования я увидел:
```
[929.741s][info][gc] GC(844) Pause Young (Concurrent Start) (G1 Evacuation Pause) 110M->57M(128M) 2.880ms
[929.741s][info][gc] GC(845) Concurrent Mark Cycle
[929.768s][info][gc] GC(845) Pause Remark 60M->36M(128M) 1.799ms
[929.785s][info][gc] GC(845) Pause Cleanup 38M->38M(128M) 0.157ms
[929.785s][info][gc] GC(845) Concurrent Mark Cycle 44.479ms
[930.264s][info][gc] GC(846) Pause Young (Prepare Mixed) (G1 Evacuation Pause) 86M->33M(128M) 2.863ms
[930.300s][info][gc] GC(847) Pause Young (Mixed) (G1 Evacuation Pause) 37M->32M(128M) 2.411ms
```

Это означает, что помимо пауз, GC параллельно работал с моим приложением продолжительное время, забирая итак дефицитные ресурсы ядер.
По сравнению с предыдущим этапом PUT сделал в 3.2 раз меньше RPS.



## GET
Скрипт:
```
math.randomseed(os.time())

function request()
  path = "/v0/entity?id=" .. tostring(math.random(1, 1000000))
  return wrk.format("GET", path, wrk.headers, wrk.body)
end
```
Методом дихотомии была найдена точка разладки на 3100 RPS, сервис не смог справиться с заданным RPS.
Как и в случае с PUT график latency очень быстро растет, но максимум латенси здесь меньше по сравнению с точкой разладки на предыдущем этапе.

```
wrk -d 120 -t 4 -c 64 -R 3100 -L -s ../get.lua http://localhost:19234
Running 2m test @ http://localhost:19234
  4 threads and 64 connections
  Thread calibration: mean lat.: 385.612ms, rate sampling interval: 1333ms
  Thread calibration: mean lat.: 385.556ms, rate sampling interval: 1333ms
  Thread calibration: mean lat.: 386.631ms, rate sampling interval: 1335ms
  Thread calibration: mean lat.: 385.322ms, rate sampling interval: 1332ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     4.48s     2.14s    8.39s    58.12%
    Req/Sec   720.78     12.33   732.00     95.43%
  Latency Distribution (HdrHistogram - Recorded Latency)
 50.000%    4.49s
 75.000%    6.34s
 90.000%    7.40s
 99.000%    8.31s
 99.900%    8.37s
 99.990%    8.38s
 99.999%    8.39s
100.000%    8.40s

  Detailed Percentile spectrum:
       Value   Percentile   TotalCount 1/(1-Percentile)

     726.015     0.000000            1         1.00
    1580.031     0.100000        31744         1.11
    2285.567     0.200000        63439         1.25
    3002.367     0.300000        95165         1.43
    3729.407     0.400000       126859         1.67
    4493.311     0.500000       158514         2.00
    4857.855     0.550000       174451         2.22
    5242.879     0.600000       190219         2.50
    5619.711     0.650000       206290         2.86
    5955.583     0.700000       222074         3.33
    6336.511     0.750000       237887         4.00
    6504.447     0.775000       245694         4.44
    6709.247     0.800000       253669         5.00
    6881.279     0.825000       261674         5.71
    7061.503     0.850000       269508         6.67
    7233.535     0.875000       277440         8.00
    7323.647     0.887500       281431         8.89
    7401.471     0.900000       285398        10.00
    7491.583     0.912500       289434        11.43
    7585.791     0.925000       293328        13.33
    7688.191     0.937500       297371        16.00
    7745.535     0.943750       299331        17.78
    7794.687     0.950000       301313        20.00
    7827.455     0.956250       303179        22.86
    7876.607     0.962500       305300        26.67
    7921.663     0.968750       307280        32.00
    7979.007     0.971875       308132        35.56
    8081.407     0.975000       309113        40.00
    8151.039     0.978125       310239        45.71
    8212.479     0.981250       311123        53.33
    8265.727     0.984375       312112        64.00
    8278.015     0.985938       312583        71.11
    8290.303     0.987500       313124        80.00
    8302.591     0.989062       313758        91.43
    8310.783     0.990625       314070       106.67
    8323.071     0.992188       314574       128.00
    8331.263     0.992969       314923       142.22
    8335.359     0.993750       315133       160.00
    8339.455     0.994531       315354       182.86
    8343.551     0.995313       315552       213.33
    8351.743     0.996094       315842       256.00
    8355.839     0.996484       316023       284.44
    8359.935     0.996875       316257       320.00
    8359.935     0.997266       316257       365.71
    8364.031     0.997656       316501       426.67
    8364.031     0.998047       316501       512.00
    8364.031     0.998242       316501       568.89
    8368.127     0.998437       316664       640.00
    8368.127     0.998633       316664       731.43
    8368.127     0.998828       316664       853.33
    8372.223     0.999023       316794      1024.00
    8372.223     0.999121       316794      1137.78
    8372.223     0.999219       316794      1280.00
    8376.319     0.999316       316893      1462.86
    8376.319     0.999414       316893      1706.67
    8376.319     0.999512       316893      2048.00
    8376.319     0.999561       316893      2275.56
    8380.415     0.999609       316971      2560.00
    8380.415     0.999658       316971      2925.71
    8380.415     0.999707       316971      3413.33
    8380.415     0.999756       316971      4096.00
    8380.415     0.999780       316971      4551.11
    8380.415     0.999805       316971      5120.00
    8380.415     0.999829       316971      5851.43
    8384.511     0.999854       317011      6826.67
    8384.511     0.999878       317011      8192.00
    8384.511     0.999890       317011      9102.22
    8384.511     0.999902       317011     10240.00
    8384.511     0.999915       317011     11702.86
    8384.511     0.999927       317011     13653.33
    8384.511     0.999939       317011     16384.00
    8384.511     0.999945       317011     18204.44
    8384.511     0.999951       317011     20480.00
    8384.511     0.999957       317011     23405.71
    8388.607     0.999963       317022     27306.67
    8388.607     0.999969       317022     32768.00
    8388.607     0.999973       317022     36408.89
    8388.607     0.999976       317022     40960.00
    8388.607     0.999979       317022     46811.43
    8388.607     0.999982       317022     54613.33
    8388.607     0.999985       317022     65536.00
    8388.607     0.999986       317022     72817.78
    8388.607     0.999988       317022     81920.00
    8388.607     0.999989       317022     93622.86
    8388.607     0.999991       317022    109226.67
    8388.607     0.999992       317022    131072.00
    8388.607     0.999993       317022    145635.56
    8396.799     0.999994       317024    163840.00
    8396.799     1.000000       317024          inf
#[Mean    =     4484.533, StdDeviation   =     2140.419]
#[Max     =     8388.608, Total count    =       317024]
#[Buckets =           27, SubBuckets     =         2048]
----------------------------------------------------------
  345935 requests in 2.00m, 22.76MB read
  Non-2xx or 3xx responses: 4312
Requests/sec:   2882.77
Transfer/sec:    194.24KB
```

Отступив на 20% от точки разладки (на 3100 RPS) видим, что сервис начал справляться.
Как и в случае с PUT график latency растет сильно медленее, чем график с точкой разладки,
максимум latency сильно отличается от среднего, присутствуют скачки в latency у крайне правых перцентилей.
Значения максимального latency меньше, чем у PUT в 2 раза (такого не было на предыдущих этапах), также скачки в latency не такие резкие, я связываю это с тем, что
я провёл compaction базы, тем самым уменьшив кол-во sstables, где нужно искать, также в логах GC я не нашел таких записей, что я приводил выше для PUT,
также заметно, что среднее время паузы GC примерно в 2-3 раза меньше на GET, чем на PUT.

По сравнению с предыдущим этапом максимальное latency уменьшилось в 2 раза, я думаю, что дело в compaction, RPS уменьшился более чем в 2.3 раза

```
 wrk -d 120 -t 4 -c 64 -R 2400 -L -s ../get.lua http://localhost:19234
Running 2m test @ http://localhost:19234
  4 threads and 64 connections
  Thread calibration: mean lat.: 2.840ms, rate sampling interval: 11ms
  Thread calibration: mean lat.: 2.848ms, rate sampling interval: 11ms
  Thread calibration: mean lat.: 4.238ms, rate sampling interval: 13ms
  Thread calibration: mean lat.: 2.863ms, rate sampling interval: 11ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     3.30ms    1.88ms  13.08ms   63.18%
    Req/Sec   628.29    303.16     1.33k    77.13%
  Latency Distribution (HdrHistogram - Recorded Latency)
 50.000%    2.73ms
 75.000%    4.85ms
 90.000%    6.13ms
 99.000%    7.47ms
 99.900%    8.71ms
 99.990%   11.21ms
 99.999%   12.38ms
100.000%   13.09ms

  Detailed Percentile spectrum:
       Value   Percentile   TotalCount 1/(1-Percentile)

       0.624     0.000000            1         1.00
       1.234     0.100000        26443         1.11
       1.541     0.200000        52822         1.25
       1.816     0.300000        79233         1.43
       2.179     0.400000       105524         1.67
       2.729     0.500000       131925         2.00
       3.153     0.550000       145116         2.22
       3.601     0.600000       158306         2.50
       4.029     0.650000       171501         2.86
       4.443     0.700000       184801         3.33
       4.847     0.750000       197866         4.00
       5.063     0.775000       204524         4.44
       5.279     0.800000       211105         5.00
       5.499     0.825000       217739         5.71
       5.707     0.850000       224280         6.67
       5.915     0.875000       230904         8.00
       6.023     0.887500       234253         8.89
       6.131     0.900000       237489        10.00
       6.247     0.912500       240842        11.43
       6.359     0.925000       244032        13.33
       6.495     0.937500       247378        16.00
       6.563     0.943750       248971        17.78
       6.643     0.950000       250688        20.00
       6.727     0.956250       252266        22.86
       6.823     0.962500       253928        26.67
       6.927     0.968750       255597        32.00
       6.983     0.971875       256437        35.56
       7.039     0.975000       257218        40.00
       7.111     0.978125       258069        45.71
       7.183     0.981250       258864        53.33
       7.271     0.984375       259703        64.00
       7.319     0.985938       260111        71.11
       7.367     0.987500       260509        80.00
       7.427     0.989062       260926        91.43
       7.495     0.990625       261332       106.67
       7.583     0.992188       261762       128.00
       7.631     0.992969       261962       142.22
       7.679     0.993750       262158       160.00
       7.739     0.994531       262369       182.86
       7.807     0.995313       262568       213.33
       7.903     0.996094       262784       256.00
       7.951     0.996484       262880       284.44
       8.007     0.996875       262985       320.00
       8.083     0.997266       263087       365.71
       8.163     0.997656       263192       426.67
       8.287     0.998047       263291       512.00
       8.367     0.998242       263341       568.89
       8.439     0.998437       263394       640.00
       8.511     0.998633       263445       731.43
       8.623     0.998828       263495       853.33
       8.743     0.999023       263547      1024.00
       8.823     0.999121       263576      1137.78
       8.887     0.999219       263601      1280.00
       8.967     0.999316       263626      1462.86
       9.071     0.999414       263650      1706.67
       9.215     0.999512       263676      2048.00
       9.295     0.999561       263689      2275.56
       9.559     0.999609       263701      2560.00
       9.647     0.999658       263714      2925.71
       9.751     0.999707       263727      3413.33
       9.919     0.999756       263740      4096.00
      10.111     0.999780       263747      4551.11
      10.287     0.999805       263753      5120.00
      10.455     0.999829       263759      5851.43
      10.735     0.999854       263766      6826.67
      10.879     0.999878       263772      8192.00
      11.159     0.999890       263776      9102.22
      11.247     0.999902       263779     10240.00
      11.391     0.999915       263782     11702.86
      11.551     0.999927       263785     13653.33
      11.663     0.999939       263788     16384.00
      11.751     0.999945       263790     18204.44
      11.839     0.999951       263792     20480.00
      11.895     0.999957       263793     23405.71
      12.047     0.999963       263795     27306.67
      12.127     0.999969       263796     32768.00
      12.175     0.999973       263797     36408.89
      12.287     0.999976       263798     40960.00
      12.311     0.999979       263799     46811.43
      12.359     0.999982       263800     54613.33
      12.359     0.999985       263800     65536.00
      12.383     0.999986       263801     72817.78
      12.383     0.999988       263801     81920.00
      12.439     0.999989       263802     93622.86
      12.439     0.999991       263802    109226.67
      12.439     0.999992       263802    131072.00
      13.023     0.999993       263803    145635.56
      13.023     0.999994       263803    163840.00
      13.023     0.999995       263803    187245.71
      13.023     0.999995       263803    218453.33
      13.023     0.999996       263803    262144.00
      13.087     0.999997       263804    291271.11
      13.087     1.000000       263804          inf
#[Mean    =        3.302, StdDeviation   =        1.881]
#[Max     =       13.080, Total count    =       263804]
#[Buckets =           27, SubBuckets     =         2048]
----------------------------------------------------------
  287359 requests in 2.00m, 18.91MB read
  Non-2xx or 3xx responses: 5370
Requests/sec:   2394.38
Transfer/sec:    161.33KB
```

# Профилирование

## CPU

### PUT

1. ~81% сэмплов - сетевое взаимодействие между нодами, из которых ~15% сэмплов - это наш воркер, который блокируется пока не получит ответ от нод,
остальные сэмплы из-за того, что на самом деле это реализовано асинхронно
2. ~5% сэмплов - воркер пишет ответ в сеть 
3. ~5% сэмплов - селекторы читают из сокета, процессят данные и кладут в очередь воркеров
4. ~1% сэмплов - работа по вставке в memtable

По сравнению с предыдущим этапом воркеры стали меньше писать ответ на клиентский запрос, но больше времени проводить на внутреннем сетевом взаимодействии.
Можно разгрузить воркеров (те 15% сэмплов), послав всем нодам запросы сразу, а потом ждать сразу всю пачку, а не по одному, но асинхронное взаимодействие, я так понимаю,
заготовлено для следующих этапов.

Про белые квадратики на профиле ничего нового к дополнению предыдущего отчета не выяснил.

### GET

1. ~78% сэмплов - сетевое взаимодействие между нодами, из которых ~16% сэмплов - это наш воркер, который блокируется пока не получит ответ от нод,
   остальные сэмплы из-за того, что на самом деле это реализовано асинхронно
2. ~4% сэмплов - воркер пишет ответ в сеть
3. ~3% сэмплов - воркер ищет данные по своим sstables
4. ~2% сэмплов - селекторы читают из сокета, процессят данные и кладут в очередь воркеров

По сравнению с предыдущим этапом воркеры стали меньше писать ответ на клиентский запрос, меньше тратить время на поиск по sstables (из-за compaction),
но стало больше процентов сэмплов во внутреннем сетевом взаимодействии. Как и в случае с PUT можно разгрузить воркеров (те 16% сэмплов), отправив все запросы пачкой,
а затем дожидаться всех сразу.

Про белые квадратики на профиле ничего нового к дополнению предыдущего отчета не выяснил.

## ALLOC

### PUT

1. ~65% сэмплов - аллокации от HTTP Client
2. ~25% сэмплов - аллокации селектора при приема запроса и вставки в очередь


По сравнению с прошлым этапом стало меньше аллокаций в селекторах при приеме запроса и вставки в очередь и больше аллокаций 
от HTTP Client, это связано с увеличением кол-ва сетевого взаимодействия между нодами, и уменьшением кол-ва RPS

### GET

1. ~57% сэмплов - аллокации от HTTP Client
2. ~35% сэмплов - аллокации селектора при приема запроса и вставки в очередь
3. ~3% сэмплов - аллокации во время поиска по sstable

По сравнению с прошлым этапом стало меньше процентов аллокаций во время поиска по sstabels из-за compaction,
на первый план выходят аллокации селектора при приема запроса и вставки в очередь

## LOCK

Как в PUT, так и в GET:

1. ~36% сэмплов - лок на SelectorManager, когда происходит регистрация события, которого селектор ждет
2. ~22% сэмплов - лок флажка прерывания в реализации Selector, когда происходит сброс флажка прерывания
3. ~21% сэмплов - лок на HttpClientImpl при работе с таймаутами
4. ~18% сэмплов - лок на SelectorManager, когда в цикле обрабатывает зарегистрированные события

По сравнению с предыдущим этапом появился lock contention на HttpClientImpl при работе с таймаутами, т.к.
я добавил таймауты на реквест и на коннект. От всего этого lock contention на внутренней коммуникации можно избавиться, если выдать каждому треду-воркеру
свой ThreadLocal HttpClient, но надо чтобы HttpClient был синхронный, т.к. асинхронный клиент даже ThreadLocal использует разные треды,
сейчас я не могу перейти на такое решение, т.к. мне понадобится асинхронный клиент в будущем.