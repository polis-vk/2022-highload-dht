Range запрос выполнялся через wrk со следующей конфигурацией:
    - Длительность 60 секунд;
    - Число потоков 64;
    - Число соединений 64;
    - Число entry 10000;
    - Rate 200;

Среднее время запроса составило 222.21ms, максимальное 477.95ms
90.000% процентиль - 262.65ms;
99.000% процентиль- 430.85ms;
Разброс между процентилями и средним с максимальным временем связан с работой GC, работа которого видна на профиле.
Сравнивать показатели с одиночными запросами некорректно так как, очевидно что range запрос быстрее, и быстрее 
с ростом числа entries в запросе, ведь будет только одно обращение по сети при range запросе против числа запросов
равному числу entries при одиночных.

На профилях CPU видно, что происходит только 2 основных действия: 
    - запись в сокет (47%);
    - чтение данных из dao через итератор (20%)
Также 20% - перевод строк для ключа и значения в байты для формирования чанков.
Session.listen - 1.26%
Session.processRead - 1.5%
Чтение из dao было только из памяти, но это не сильно влияет в данном случае так как если оно будет с диска, то 
процентная доля на него просто увеличится.

Около 40% аллокаций происходят при чтении из dao внутри MergeIterator;
Около 60% для перевода полученных из него строковых значений key и value и данных для чанков в массивы байт.
Если бы dao было бы сразу на ByteBuffer-х, то большего число аллокаций из этих 60% можно было бы не делать, так как при
формировании чанков можно было бы сразу использовать эти буфера, также не пришлось бы использовать CPU для 
этих трансформаций.
Единственный лок - one.nio.net.Session.write, от которого никак не избавиться.

Как видно из профилей большая часть работы CPU и аллокаций уходит на запись в сокет и чтение из dao, что оптимизациям 
не подлежит.


```
./wrk -d 60 -t 64 -c 64 -R 200 -L -s ./wrk-scripts/stage6_GET.lua http://localhost:12353
Running 1m test @ http://localhost:12353
64 threads and 64 connections
Thread calibration: mean lat.: 72.245ms, rate sampling interval: 625ms
Thread calibration: mean lat.: 235.720ms, rate sampling interval: 868ms
Thread calibration: mean lat.: 122.725ms, rate sampling interval: 393ms
Thread calibration: mean lat.: 235.963ms, rate sampling interval: 867ms
Thread calibration: mean lat.: 142.924ms, rate sampling interval: 412ms
Thread calibration: mean lat.: 147.377ms, rate sampling interval: 422ms
Thread calibration: mean lat.: 139.837ms, rate sampling interval: 792ms
Thread calibration: mean lat.: 177.077ms, rate sampling interval: 487ms
Thread calibration: mean lat.: 108.834ms, rate sampling interval: 624ms
Thread calibration: mean lat.: 193.005ms, rate sampling interval: 785ms
Thread calibration: mean lat.: 266.812ms, rate sampling interval: 860ms
Thread calibration: mean lat.: 266.702ms, rate sampling interval: 855ms
Thread calibration: mean lat.: 232.506ms, rate sampling interval: 807ms
Thread calibration: mean lat.: 232.744ms, rate sampling interval: 806ms
Thread calibration: mean lat.: 195.689ms, rate sampling interval: 518ms
Thread calibration: mean lat.: 196.617ms, rate sampling interval: 519ms
Thread calibration: mean lat.: 236.596ms, rate sampling interval: 815ms
Thread calibration: mean lat.: 236.072ms, rate sampling interval: 817ms
Thread calibration: mean lat.: 172.856ms, rate sampling interval: 631ms
Thread calibration: mean lat.: 238.095ms, rate sampling interval: 823ms
Thread calibration: mean lat.: 173.259ms, rate sampling interval: 634ms
Thread calibration: mean lat.: 198.039ms, rate sampling interval: 517ms
Thread calibration: mean lat.: 210.611ms, rate sampling interval: 662ms
Thread calibration: mean lat.: 215.191ms, rate sampling interval: 671ms
Thread calibration: mean lat.: 206.691ms, rate sampling interval: 529ms
Thread calibration: mean lat.: 215.266ms, rate sampling interval: 667ms
Thread calibration: mean lat.: 206.709ms, rate sampling interval: 529ms
Thread calibration: mean lat.: 272.842ms, rate sampling interval: 819ms
Thread calibration: mean lat.: 238.594ms, rate sampling interval: 824ms
Thread calibration: mean lat.: 274.650ms, rate sampling interval: 819ms
Thread calibration: mean lat.: 238.453ms, rate sampling interval: 825ms
Thread calibration: mean lat.: 272.908ms, rate sampling interval: 813ms
Thread calibration: mean lat.: 219.819ms, rate sampling interval: 687ms
Thread calibration: mean lat.: 272.862ms, rate sampling interval: 814ms
Thread calibration: mean lat.: 276.244ms, rate sampling interval: 829ms
Thread calibration: mean lat.: 221.006ms, rate sampling interval: 701ms
Thread calibration: mean lat.: 235.996ms, rate sampling interval: 816ms
Thread calibration: mean lat.: 275.873ms, rate sampling interval: 827ms
Thread calibration: mean lat.: 220.533ms, rate sampling interval: 700ms
Thread calibration: mean lat.: 202.588ms, rate sampling interval: 518ms
Thread calibration: mean lat.: 219.740ms, rate sampling interval: 702ms
Thread calibration: mean lat.: 203.586ms, rate sampling interval: 520ms
Thread calibration: mean lat.: 205.584ms, rate sampling interval: 526ms
Thread calibration: mean lat.: 233.970ms, rate sampling interval: 818ms
Thread calibration: mean lat.: 228.295ms, rate sampling interval: 823ms
Thread calibration: mean lat.: 198.804ms, rate sampling interval: 508ms
Thread calibration: mean lat.: 274.968ms, rate sampling interval: 834ms
Thread calibration: mean lat.: 216.131ms, rate sampling interval: 706ms
Thread calibration: mean lat.: 274.471ms, rate sampling interval: 833ms
Thread calibration: mean lat.: 215.606ms, rate sampling interval: 707ms
Thread calibration: mean lat.: 231.941ms, rate sampling interval: 818ms
Thread calibration: mean lat.: 213.722ms, rate sampling interval: 715ms
Thread calibration: mean lat.: 235.665ms, rate sampling interval: 838ms
Thread calibration: mean lat.: 192.960ms, rate sampling interval: 496ms
Thread calibration: mean lat.: 213.391ms, rate sampling interval: 724ms
Thread calibration: mean lat.: 215.292ms, rate sampling interval: 671ms
Thread calibration: mean lat.: 276.106ms, rate sampling interval: 841ms
Thread calibration: mean lat.: 220.645ms, rate sampling interval: 817ms
Thread calibration: mean lat.: 272.208ms, rate sampling interval: 845ms
Thread calibration: mean lat.: 190.656ms, rate sampling interval: 492ms
Thread calibration: mean lat.: 270.839ms, rate sampling interval: 848ms
Thread calibration: mean lat.: 272.400ms, rate sampling interval: 852ms
Thread calibration: mean lat.: 216.449ms, rate sampling interval: 823ms
Thread calibration: mean lat.: 182.616ms, rate sampling interval: 475ms

Thread Stats Avg Stdev Max +/- Stdev
Latency 222.21ms 61.28ms 477.95ms 84.47%
Req/Sec 2.57 0.78 5.00 99.30%

Latency Distribution (HdrHistogram - Recorded Latency)
50.000% 229.89ms
75.000% 245.76ms
90.000% 262.65ms
99.000% 430.85ms
99.900% 467.97ms
99.990% 476.16ms
99.999% 478.21ms
100.000% 478.21ms

Detailed Percentile spectrum:
Value Percentile TotalCount 1/(1-Percentile)

10.295 0.000000 1 1.00
159.487 0.100000 1000 1.11
202.111 0.200000 2001 1.25
215.551 0.300000 3012 1.43
223.487 0.400000 4016 1.67
229.887 0.500000 5021 2.00
232.575 0.550000 5503 2.22
235.903 0.600000 6008 2.50
238.847 0.650000 6499 2.86
242.175 0.700000 7005 3.33
245.759 0.750000 7509 4.00
247.679 0.775000 7752 4.44
249.983 0.800000 7996 5.00
252.287 0.825000 8246 5.71
254.847 0.850000 8499 6.67
258.047 0.875000 8748 8.00
260.223 0.887500 8876 8.89
262.655 0.900000 8998 10.00
265.471 0.912500 9130 11.43
269.823 0.925000 9251 13.33
275.455 0.937500 9373 16.00
279.807 0.943750 9433 17.78
288.511 0.950000 9499 20.00
297.983 0.956250 9558 22.86
314.623 0.962500 9622 26.67
329.727 0.968750 9683 32.00
342.527 0.971875 9714 35.56
359.167 0.975000 9746 40.00
377.343 0.978125 9777 45.71
393.471 0.981250 9808 53.33
408.319 0.984375 9840 64.00
417.535 0.985938 9856 71.11
422.143 0.987500 9871 80.00
428.543 0.989062 9886 91.43
433.919 0.990625 9902 106.67
439.551 0.992188 9917 128.00
442.879 0.992969 9925 142.22
445.439 0.993750 9933 160.00
447.999 0.994531 9941 182.86
452.095 0.995313 9949 213.33
456.447 0.996094 9956 256.00
459.007 0.996484 9960 284.44
461.567 0.996875 9965 320.00
462.335 0.997266 9968 365.71
463.359 0.997656 9972 426.67
464.383 0.998047 9976 512.00
465.663 0.998242 9978 568.89
466.943 0.998437 9981 640.00
467.455 0.998633 9982 731.43
467.967 0.998828 9985 853.33
468.223 0.999023 9986 1024.00
469.247 0.999121 9987 1137.78
469.759 0.999219 9988 1280.00
470.783 0.999316 9989 1462.86
471.295 0.999414 9990 1706.67
472.063 0.999512 9991 2048.00
472.063 0.999561 9991 2275.56
472.319 0.999609 9992 2560.00
472.319 0.999658 9992 2925.71
474.367 0.999707 9993 3413.33
474.367 0.999756 9993 4096.00
474.367 0.999780 9993 4551.11
476.159 0.999805 9994 5120.00
476.159 0.999829 9994 5851.43
476.159 0.999854 9994 6826.67
476.159 0.999878 9994 8192.00
476.159 0.999890 9994 9102.22
478.207 0.999902 9995 10240.00
478.207 1.000000 9995 inf
#[Mean = 222.207, StdDeviation = 61.276]
#[Max = 477.952, Total count = 9995]
#[Buckets = 27, SubBuckets = 2048]
--------------------------------------------------------—
11978 requests in 1.00m, 1.77GB read
Requests/sec: 199.60
Transfer/sec: 30.16MB
```