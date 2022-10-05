# Отчет Stage 2 #

## Нагрузочное тестирование с помощью wrk2 ##

Тестирование производится на `Ubuntu 22.04.1 LTS` ядро `Linux 5.15.0-48-generic`
процессор `Intel(R) Xeon(R) CPU E5-2620 v3 @ 2.40GHz`
кеш L3 `15 Mb` диск nvme

### PUT ###

#### wrk2 ####

Для 64 соединений удалось достичь rate в 100000

`wrk -d 60 -t 64 -c 64 -R 100000 -s ./put.lua -L http://localhost:8084` - Avg Latency 0.96ms

```sh
nikita@nikita-X99:~/wrk2$ wrk -d 120 -t 64 -c 64 -R 100000 -s ./put.lua -L http://localhost:8084
Running 2m test @ http://localhost:8084
  64 threads and 64 connections
  Thread calibration: mean lat.: 5.947ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 6.603ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 4.104ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 7.327ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 4.811ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 6.576ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 7.079ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 7.179ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 6.278ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 4.518ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 6.656ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 4.506ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 7.197ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 6.667ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 4.029ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 6.445ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 6.350ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 7.324ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 6.492ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 7.188ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 4.579ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 5.834ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 7.377ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 5.711ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 7.327ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 6.115ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 4.385ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 4.520ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 6.341ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 3.958ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 6.941ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 7.337ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 6.675ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 6.102ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 7.006ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 6.274ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 6.559ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 4.296ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 6.314ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 6.610ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 6.942ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 7.210ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 6.866ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 7.021ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 5.624ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 7.154ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 6.415ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 6.904ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 7.136ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 6.103ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 3.114ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 6.399ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 3.662ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 6.105ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 6.404ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 5.409ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 5.647ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 4.263ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 6.693ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 6.307ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 6.101ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 6.284ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 5.713ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 6.052ms, rate sampling interval: 10ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     0.96ms    0.93ms  70.66ms   99.06%
    Req/Sec     1.63k   115.73     4.89k    65.74%
  Latency Distribution (HdrHistogram - Recorded Latency)
 50.000%    0.93ms
 75.000%    1.23ms
 90.000%    1.53ms
 99.000%    1.88ms
 99.900%    9.65ms
 99.990%   39.94ms
 99.999%   61.82ms
100.000%   70.72ms

  Detailed Percentile spectrum:
       Value   Percentile   TotalCount 1/(1-Percentile)

       0.038     0.000000            3         1.00
       0.331     0.100000      1101561         1.11
       0.548     0.200000      2202173         1.25
       0.711     0.300000      3300829         1.43
       0.822     0.400000      4400317         1.67
       0.926     0.500000      5510022         2.00
       0.977     0.550000      6049831         2.22
       1.032     0.600000      6609326         2.50
       1.088     0.650000      7149494         2.86
       1.151     0.700000      7706396         3.33
       1.233     0.750000      8251766         4.00
       1.280     0.775000      8525357         4.44
       1.329     0.800000      8804034         5.00
       1.377     0.825000      9074281         5.71
       1.426     0.850000      9349615         6.67
       1.476     0.875000      9625191         8.00
       1.502     0.887500      9765897         8.89
       1.528     0.900000      9903155        10.00
       1.554     0.912500     10037231        11.43
       1.581     0.925000     10174639        13.33
       1.609     0.937500     10316175        16.00
       1.623     0.943750     10384488        17.78
       1.637     0.950000     10449397        20.00
       1.653     0.956250     10518017        22.86
       1.672     0.962500     10589352        26.67
       1.693     0.968750     10656822        32.00
       1.705     0.971875     10690006        35.56
       1.720     0.975000     10725874        40.00
       1.737     0.978125     10760120        45.71
       1.757     0.981250     10793019        53.33
       1.784     0.984375     10827210        64.00
       1.802     0.985938     10844650        71.11
       1.824     0.987500     10861650        80.00
       1.854     0.989062     10879067        91.43
       1.896     0.990625     10895974       106.67
       1.967     0.992188     10913225       128.00
       2.023     0.992969     10921822       142.22
       2.101     0.993750     10930477       160.00
       2.209     0.994531     10938933       182.86
       2.365     0.995313     10947513       213.33
       2.579     0.996094     10956114       256.00
       2.705     0.996484     10960432       284.44
       2.851     0.996875     10964730       320.00
       3.027     0.997266     10969023       365.71
       3.259     0.997656     10973298       426.67
       3.651     0.998047     10977582       512.00
       4.071     0.998242     10979730       568.89
       4.819     0.998437     10981891       640.00
       5.859     0.998633     10984032       731.43
       7.359     0.998828     10986175       853.33
      10.015     0.999023     10988328      1024.00
      11.535     0.999121     10989396      1137.78
      13.327     0.999219     10990472      1280.00
      15.455     0.999316     10991546      1462.86
      18.143     0.999414     10992623      1706.67
      20.975     0.999512     10993693      2048.00
      22.335     0.999561     10994231      2275.56
      23.775     0.999609     10994770      2560.00
      25.615     0.999658     10995305      2925.71
      27.871     0.999707     10995841      3413.33
      30.751     0.999756     10996379      4096.00
      32.031     0.999780     10996652      4551.11
      33.375     0.999805     10996915      5120.00
      34.911     0.999829     10997185      5851.43
      36.287     0.999854     10997454      6826.67
      37.887     0.999878     10997721      8192.00
      38.847     0.999890     10997856      9102.22
      40.127     0.999902     10997989     10240.00
      41.567     0.999915     10998124     11702.86
      43.327     0.999927     10998261     13653.33
      45.535     0.999939     10998396     16384.00
      46.719     0.999945     10998460     18204.44
      48.255     0.999951     10998527     20480.00
      49.759     0.999957     10998594     23405.71
      51.295     0.999963     10998661     27306.67
      53.759     0.999969     10998728     32768.00
      54.687     0.999973     10998761     36408.89
      55.359     0.999976     10998796     40960.00
      56.031     0.999979     10998829     46811.43
      56.831     0.999982     10998863     54613.33
      58.111     0.999985     10998896     65536.00
      58.655     0.999986     10998912     72817.78
      60.351     0.999988     10998929     81920.00
      61.599     0.999989     10998946     93622.86
      62.367     0.999991     10998963    109226.67
      65.855     0.999992     10998981    131072.00
      65.983     0.999993     10998988    145635.56
      66.303     0.999994     10998996    163840.00
      66.815     0.999995     10999005    187245.71
      67.135     0.999995     10999015    218453.33
      67.327     0.999996     10999022    262144.00
      67.455     0.999997     10999028    291271.11
      67.583     0.999997     10999030    327680.00
      68.159     0.999997     10999034    374491.43
      68.735     0.999998     10999038    436906.67
      69.119     0.999998     10999043    524288.00
      69.311     0.999998     10999045    582542.22
      69.503     0.999998     10999048    655360.00
      69.631     0.999999     10999049    748982.86
      69.823     0.999999     10999054    873813.33
      69.823     0.999999     10999054   1048576.00
      69.823     0.999999     10999054   1165084.44
      69.951     0.999999     10999056   1310720.00
      69.951     0.999999     10999056   1497965.71
      70.015     0.999999     10999058   1747626.67
      70.015     1.000000     10999058   2097152.00
      70.079     1.000000     10999059   2330168.89
      70.079     1.000000     10999059   2621440.00
      70.143     1.000000     10999060   2995931.43
      70.143     1.000000     10999060   3495253.33
      70.271     1.000000     10999061   4194304.00
      70.271     1.000000     10999061   4660337.78
      70.271     1.000000     10999061   5242880.00
      70.527     1.000000     10999062   5991862.86
      70.527     1.000000     10999062   6990506.67
      70.527     1.000000     10999062   8388608.00
      70.527     1.000000     10999062   9320675.55
      70.527     1.000000     10999062  10485760.00
      70.719     1.000000     10999063  11983725.71
      70.719     1.000000     10999063          inf
#[Mean    =        0.962, StdDeviation   =        0.932]
#[Max     =       70.656, Total count    =     10999063]
#[Buckets =           27, SubBuckets     =         2048]
----------------------------------------------------------
  11999520 requests in 2.00m, 766.72MB read
Requests/sec: 100003.83
Transfer/sec:      6.39MB

```

#### CPU ####

* Обработка put запроса занимает 50 % времени процессора
* SelectorThread one.nio 15 % времени
* Работа с пулом потоков 18 % времени
* upsert в базу данных 6.63 % времени
  ![img.png](put_cpu.png)

#### ALLOC ####

* 62 % алолокаций идет на чтение запроса
* 33 % аллокаций идет на обработку put
* upsert в базу данных около 0.72 %
  ![img.png](put_alloc.png)

#### LOCK ####

* 33 % локов идет на чтение запроса
* 50 % локов идет на работу с очередью запросов
  ![img.png](put_lock.png)

### GET ###

#### wrk2 ####

Для 64 соединений удалось достичь rate в 100000
`wrk -d 120 -t 64 -c 64 -R 100000 -s ./get.lua -L http://localhost:8084` - Avg Latency - 0.94ms

```sh
nikita@nikita-X99:~/wrk2$ wrk -d 120 -t 64 -c 64 -R 100000 -s ./get.lua -L http://localhost:8084
Running 2m test @ http://localhost:8084
  64 threads and 64 connections
  Thread calibration: mean lat.: 12.630ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 13.247ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 12.770ms, rate sampling interval: 11ms
  Thread calibration: mean lat.: 14.626ms, rate sampling interval: 55ms
  Thread calibration: mean lat.: 11.979ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 11.713ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 11.960ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 12.776ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 13.254ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 12.833ms, rate sampling interval: 11ms
  Thread calibration: mean lat.: 12.336ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 13.747ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 12.835ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 13.465ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 13.851ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 13.557ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 12.588ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 12.778ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 12.165ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 12.302ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 12.179ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 13.129ms, rate sampling interval: 17ms
  Thread calibration: mean lat.: 12.988ms, rate sampling interval: 12ms
  Thread calibration: mean lat.: 13.218ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 12.669ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 12.427ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 15.655ms, rate sampling interval: 74ms
  Thread calibration: mean lat.: 13.172ms, rate sampling interval: 15ms
  Thread calibration: mean lat.: 15.543ms, rate sampling interval: 78ms
  Thread calibration: mean lat.: 13.437ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 13.711ms, rate sampling interval: 15ms
  Thread calibration: mean lat.: 12.611ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 12.827ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 13.115ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 15.469ms, rate sampling interval: 54ms
  Thread calibration: mean lat.: 13.774ms, rate sampling interval: 22ms
  Thread calibration: mean lat.: 15.526ms, rate sampling interval: 42ms
  Thread calibration: mean lat.: 14.463ms, rate sampling interval: 46ms
  Thread calibration: mean lat.: 14.032ms, rate sampling interval: 13ms
  Thread calibration: mean lat.: 13.767ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 13.304ms, rate sampling interval: 16ms
  Thread calibration: mean lat.: 14.343ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 14.255ms, rate sampling interval: 48ms
  Thread calibration: mean lat.: 15.608ms, rate sampling interval: 47ms
  Thread calibration: mean lat.: 14.061ms, rate sampling interval: 39ms
  Thread calibration: mean lat.: 15.969ms, rate sampling interval: 58ms
  Thread calibration: mean lat.: 13.534ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 12.837ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 13.710ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 16.031ms, rate sampling interval: 49ms
  Thread calibration: mean lat.: 12.107ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 13.608ms, rate sampling interval: 11ms
  Thread calibration: mean lat.: 16.715ms, rate sampling interval: 83ms
  Thread calibration: mean lat.: 17.069ms, rate sampling interval: 59ms
  Thread calibration: mean lat.: 15.492ms, rate sampling interval: 42ms
  Thread calibration: mean lat.: 14.476ms, rate sampling interval: 59ms
  Thread calibration: mean lat.: 14.519ms, rate sampling interval: 35ms
  Thread calibration: mean lat.: 14.228ms, rate sampling interval: 51ms
  Thread calibration: mean lat.: 12.518ms, rate sampling interval: 11ms
  Thread calibration: mean lat.: 12.484ms, rate sampling interval: 13ms
  Thread calibration: mean lat.: 11.900ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 15.132ms, rate sampling interval: 53ms
  Thread calibration: mean lat.: 13.218ms, rate sampling interval: 33ms
  Thread calibration: mean lat.: 11.126ms, rate sampling interval: 10ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     0.96ms  456.21us  15.34ms   65.93%
    Req/Sec     1.63k   108.52     4.11k    71.07%
  Latency Distribution (HdrHistogram - Recorded Latency)
 50.000%    0.94ms
 75.000%    1.26ms
 90.000%    1.55ms
 99.000%    1.90ms
 99.900%    3.26ms
 99.990%    7.48ms
 99.999%   12.65ms
100.000%   15.35ms

  Detailed Percentile spectrum:
       Value   Percentile   TotalCount 1/(1-Percentile)

       0.040     0.000000            1         1.00
       0.346     0.100000      1101099         1.11
       0.562     0.200000      2199715         1.25
       0.726     0.300000      3306246         1.43
       0.839     0.400000      4399924         1.67
       0.944     0.500000      5506836         2.00
       0.995     0.550000      6050598         2.22
       1.048     0.600000      6598995         2.50
       1.104     0.650000      7148852         2.86
       1.170     0.700000      7703877         3.33
       1.257     0.750000      8253286         4.00
       1.304     0.775000      8524095         4.44
       1.353     0.800000      8802356         5.00
       1.401     0.825000      9075451         5.71
       1.450     0.850000      9352551         6.67
       1.499     0.875000      9627343         8.00
       1.524     0.887500      9765511         8.89
       1.549     0.900000      9901853        10.00
       1.574     0.912500     10036735        11.43
       1.600     0.925000     10174066        13.33
       1.627     0.937500     10311531        16.00
       1.642     0.943750     10383010        17.78
       1.657     0.950000     10449054        20.00
       1.674     0.956250     10517632        22.86
       1.694     0.962500     10588091        26.67
       1.717     0.968750     10655762        32.00
       1.731     0.971875     10690484        35.56
       1.746     0.975000     10723063        40.00
       1.765     0.978125     10758000        45.71
       1.788     0.981250     10792382        53.33
       1.817     0.984375     10826600        64.00
       1.835     0.985938     10843326        71.11
       1.857     0.987500     10860600        80.00
       1.884     0.989062     10877682        91.43
       1.920     0.990625     10895118       106.67
       1.965     0.992188     10911930       128.00
       1.994     0.992969     10920500       142.22
       2.028     0.993750     10929251       160.00
       2.069     0.994531     10937766       182.86
       2.119     0.995313     10946331       213.33
       2.181     0.996094     10954840       256.00
       2.221     0.996484     10959264       284.44
       2.267     0.996875     10963502       320.00
       2.325     0.997266     10967778       365.71
       2.401     0.997656     10972121       426.67
       2.511     0.998047     10976334       512.00
       2.589     0.998242     10978464       568.89
       2.695     0.998437     10980636       640.00
       2.841     0.998633     10982767       731.43
       3.031     0.998828     10984913       853.33
       3.295     0.999023     10987056      1024.00
       3.459     0.999121     10988136      1137.78
       3.643     0.999219     10989212      1280.00
       3.865     0.999316     10990277      1462.86
       4.115     0.999414     10991358      1706.67
       4.427     0.999512     10992430      2048.00
       4.595     0.999561     10992967      2275.56
       4.787     0.999609     10993498      2560.00
       5.007     0.999658     10994035      2925.71
       5.307     0.999707     10994571      3413.33
       5.639     0.999756     10995108      4096.00
       5.859     0.999780     10995378      4551.11
       6.091     0.999805     10995643      5120.00
       6.363     0.999829     10995913      5851.43
       6.703     0.999854     10996184      6826.67
       7.067     0.999878     10996451      8192.00
       7.295     0.999890     10996583      9102.22
       7.539     0.999902     10996717     10240.00
       7.843     0.999915     10996852     11702.86
       8.175     0.999927     10996986     13653.33
       8.599     0.999939     10997121     16384.00
       8.839     0.999945     10997189     18204.44
       9.127     0.999951     10997256     20480.00
       9.479     0.999957     10997325     23405.71
       9.879     0.999963     10997389     27306.67
      10.247     0.999969     10997456     32768.00
      10.551     0.999973     10997489     36408.89
      10.863     0.999976     10997523     40960.00
      11.183     0.999979     10997557     46811.43
      11.527     0.999982     10997590     54613.33
      11.983     0.999985     10997625     65536.00
      12.095     0.999986     10997642     72817.78
      12.343     0.999988     10997657     81920.00
      12.575     0.999989     10997674     93622.86
      12.807     0.999991     10997691    109226.67
      13.071     0.999992     10997708    131072.00
      13.207     0.999993     10997716    145635.56
      13.271     0.999994     10997725    163840.00
      13.415     0.999995     10997733    187245.71
      13.535     0.999995     10997741    218453.33
      13.615     0.999996     10997750    262144.00
      13.695     0.999997     10997754    291271.11
      13.767     0.999997     10997758    327680.00
      13.839     0.999997     10997762    374491.43
      13.967     0.999998     10997766    436906.67
      14.039     0.999998     10997771    524288.00
      14.063     0.999998     10997773    582542.22
      14.103     0.999998     10997775    655360.00
      14.143     0.999999     10997777    748982.86
      14.175     0.999999     10997779    873813.33
      14.207     0.999999     10997781   1048576.00
      14.223     0.999999     10997782   1165084.44
      14.263     0.999999     10997783   1310720.00
      14.295     0.999999     10997784   1497965.71
      14.319     0.999999     10997785   1747626.67
      14.359     1.000000     10997786   2097152.00
      14.367     1.000000     10997787   2330168.89
      14.367     1.000000     10997787   2621440.00
      14.479     1.000000     10997788   2995931.43
      14.479     1.000000     10997788   3495253.33
      14.695     1.000000     10997789   4194304.00
      14.695     1.000000     10997789   4660337.78
      14.695     1.000000     10997789   5242880.00
      14.823     1.000000     10997790   5991862.86
      14.823     1.000000     10997790   6990506.67
      14.823     1.000000     10997790   8388608.00
      14.823     1.000000     10997790   9320675.55
      14.823     1.000000     10997790  10485760.00
      15.351     1.000000     10997791  11983725.71
      15.351     1.000000     10997791          inf
#[Mean    =        0.956, StdDeviation   =        0.456]
#[Max     =       15.344, Total count    =     10997791]
#[Buckets =           27, SubBuckets     =         2048]
----------------------------------------------------------
  11998239 requests in 2.00m, 839.35MB read
Requests/sec: 100010.63
Transfer/sec:      7.00MB
```

#### CPU ####

* Обработка get проса занимает 57 % времени процессора
* SelectorThread one.nio 16 % времени
* Работа с пулом потоков 15 % времени
* upsert в базу данных 8.5 % времени
  Аналогичные результаты с put
  ![img.png](get_cpu.png)
#### ALLOC ####

* 62 % алолокаций идет на чтение запроса
* 33 % аллокаций идет на обработку get
* get в базу данных около 15 %
  Аналогичные результаты с put
  ![img.png](get_alloc.png)

#### LOCK ####

* 32 % локов идет на чтение запроса
* 50 % локов идет на работу с очередью запросов
  ![img.png](get_lock.png)

### Сравнение с прошлыми результатами ###

Сравнивая Heatmaps данной и прошлой реализации легко заметить, что в нынешней ресурсы используются
более полно и равномерно (нет белых квадратов), так же можно заметить что мы снизили нагрузку на
SelectorThread из-за чего сервис стал больше времени тратить именно на обработку запроса. Так же ужалось
увеличить rate до 100000 (почти в 10 раз), работа с методами базы данных осталась на том же уровне,
что и была

