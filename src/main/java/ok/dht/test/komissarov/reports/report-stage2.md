

# *Stage 2. Асинхронный сервер*

## *Нагрузочное тестирование с помощью wrk* 

Для того, чтобы понять являются ли внесенные изменения эффективными, было проведено нагрузочное тестирование `GET`  и`PUT`запросами до модификации и после. Для обоих типов запросов был выбран `RATE` равный `15000`, при котором сервер стабильно захлёбывался.  

<hr>

### *PUT*

Результаты до модификации обработки запросов. 

```
       0.032     0.000000            4         1.00
       0.285     0.100000        30021         1.11
       0.517     0.200000        60028         1.25
       0.744     0.300000        90028         1.43
       0.928     0.400000       120110         1.67
       1.046     0.500000       150213         2.00
       1.108     0.550000       165028         2.22
       1.211     0.600000       180077         2.50
       1.321     0.650000       194992         2.86
       1.432     0.700000       210031         3.33
       1.545     0.750000       225114         4.00
       1.600     0.775000       232549         4.44
       1.658     0.800000       240083         5.00
       1.714     0.825000       247539         5.71
       1.769     0.850000       254983         6.67
       1.826     0.875000       262595         8.00
       1.854     0.887500       266303         8.89
       1.883     0.900000       270007        10.00
       1.913     0.912500       273796        11.43
       1.953     0.925000       277521        13.33
       2.105     0.937500       281251        16.00
       2.281     0.943750       283106        17.78
       2.523     0.950000       284975        20.00
       2.835     0.956250       286856        22.86
       3.241     0.962500       288724        26.67
       3.755     0.968750       290599        32.00
       4.059     0.971875       291539        35.56
       4.399     0.975000       292475        40.00
       4.875     0.978125       293416        45.71
       5.459     0.981250       294349        53.33
       6.119     0.984375       295291        64.00
       6.351     0.985938       295764        71.11
       6.619     0.987500       296224        80.00
       7.039     0.989062       296694        91.43
       7.635     0.990625       297161       106.67
       8.311     0.992188       297631       128.00
       8.679     0.992969       297872       142.22
       9.079     0.993750       298103       160.00
       9.631     0.994531       298332       182.86
      10.263     0.995313       298566       213.33
      11.199     0.996094       298801       256.00
      11.879     0.996484       298919       284.44
      12.535     0.996875       299036       320.00
      13.135     0.997266       299154       365.71
      13.847     0.997656       299269       426.67
      14.791     0.998047       299387       512.00
      15.391     0.998242       299445       568.89
      15.943     0.998437       299504       640.00
      16.463     0.998633       299563       731.43
      17.023     0.998828       299621       853.33
      17.535     0.999023       299680      1024.00
      17.855     0.999121       299709      1137.78
      18.191     0.999219       299738      1280.00
      18.511     0.999316       299767      1462.86
      18.959     0.999414       299799      1706.67
      19.295     0.999512       299826      2048.00
      19.487     0.999561       299842      2275.56
      19.823     0.999609       299855      2560.00
      20.239     0.999658       299870      2925.71
      20.639     0.999707       299885      3413.33
      20.927     0.999756       299899      4096.00
      21.055     0.999780       299907      4551.11
      21.167     0.999805       299914      5120.00
      21.327     0.999829       299921      5851.43
      21.567     0.999854       299930      6826.67
      21.823     0.999878       299936      8192.00
      21.919     0.999890       299942      9102.22
      21.935     0.999902       299943     10240.00
      22.031     0.999915       299947     11702.86
      22.111     0.999927       299951     13653.33
      22.287     0.999939       299954     16384.00
      22.511     0.999945       299956     18204.44
      22.911     0.999951       299958     20480.00
      23.327     0.999957       299960     23405.71
      23.695     0.999963       299962     27306.67
      23.887     0.999969       299963     32768.00
      24.063     0.999973       299964     36408.89
      24.271     0.999976       299965     40960.00
      24.463     0.999979       299966     46811.43
      24.623     0.999982       299967     54613.33
      24.815     0.999985       299968     65536.00
      24.815     0.999986       299968     72817.78
      24.911     0.999988       299969     81920.00
      24.911     0.999989       299969     93622.86
      24.991     0.999991       299970    109226.67
      24.991     0.999992       299970    131072.00
      24.991     0.999993       299970    145635.56
      25.023     0.999994       299971    163840.00
      25.023     0.999995       299971    187245.71
      25.023     0.999995       299971    218453.33
      25.023     0.999996       299971    262144.00
      25.023     0.999997       299971    291271.11
      25.087     0.999997       299972    327680.00
      25.087     1.000000       299972          inf

```

`90%` запросов обрабатываются быстрее, чем за `2ms`, оставшиеся `10%` же имеют неприемлемое время ответа. Сервер однозначно не справляется.

После внесения модификаций:

```
	   0.028     0.000000            1         1.00
       0.264     0.100000        30046         1.11
       0.480     0.200000        60055         1.25
       0.693     0.300000        90051         1.43
       0.889     0.400000       120118         1.67
       0.999     0.500000       150183         2.00
       1.053     0.550000       165124         2.22
       1.116     0.600000       180033         2.50
       1.218     0.650000       195056         2.86
       1.324     0.700000       210026         3.33
       1.429     0.750000       225028         4.00
       1.483     0.775000       232489         4.44
       1.536     0.800000       239955         5.00
       1.590     0.825000       247534         5.71
       1.642     0.850000       254937         6.67
       1.696     0.875000       262469         8.00
       1.723     0.887500       266217         8.89
       1.750     0.900000       270071        10.00
       1.776     0.912500       273673        11.43
       1.803     0.925000       277416        13.33
       1.831     0.937500       281276        16.00
       1.844     0.943750       283085        17.78
       1.857     0.950000       285020        20.00
       1.870     0.956250       286868        22.86
       1.884     0.962500       288775        26.67
       1.897     0.968750       290530        32.00
       1.905     0.971875       291598        35.56
       1.912     0.975000       292468        40.00
       1.919     0.978125       293375        45.71
       1.929     0.981250       294327        53.33
       1.944     0.984375       295217        64.00
       1.958     0.985938       295686        71.11
       1.986     0.987500       296157        80.00
       2.029     0.989062       296629        91.43
       2.101     0.990625       297095       106.67
       2.273     0.992188       297559       128.00
       2.425     0.992969       297791       142.22
       2.595     0.993750       298026       160.00
       2.809     0.994531       298263       182.86
       3.049     0.995313       298495       213.33
       3.341     0.996094       298728       256.00
       3.509     0.996484       298846       284.44
       3.677     0.996875       298963       320.00
       3.879     0.997266       299082       365.71
       4.095     0.997656       299200       426.67
       4.311     0.998047       299318       512.00
       4.419     0.998242       299373       568.89
       4.531     0.998437       299432       640.00
       4.675     0.998633       299489       731.43
       4.843     0.998828       299550       853.33
       4.999     0.999023       299610      1024.00
       5.079     0.999121       299637      1137.78
       5.171     0.999219       299665      1280.00
       5.271     0.999316       299696      1462.86
       5.371     0.999414       299725      1706.67
       5.463     0.999512       299753      2048.00
       5.535     0.999561       299768      2275.56
       5.579     0.999609       299783      2560.00
       5.643     0.999658       299798      2925.71
       5.755     0.999707       299813      3413.33
       5.839     0.999756       299826      4096.00
       5.943     0.999780       299834      4551.11
       5.987     0.999805       299841      5120.00
       6.047     0.999829       299848      5851.43
       6.127     0.999854       299857      6826.67
       6.215     0.999878       299863      8192.00
       6.267     0.999890       299867      9102.22
       6.311     0.999902       299870     10240.00
       6.399     0.999915       299874     11702.86
       6.523     0.999927       299878     13653.33
       6.579     0.999939       299881     16384.00
       6.691     0.999945       299883     18204.44
       6.755     0.999951       299885     20480.00
       6.795     0.999957       299887     23405.71
       6.879     0.999963       299889     27306.67
       6.951     0.999969       299890     32768.00
       6.979     0.999973       299891     36408.89
       7.003     0.999976       299892     40960.00
       7.067     0.999979       299893     46811.43
       7.195     0.999982       299894     54613.33
       7.211     0.999985       299895     65536.00
       7.211     0.999986       299895     72817.78
       7.387     0.999988       299896     81920.00
       7.387     0.999989       299896     93622.86
       7.407     0.999991       299897    109226.67
       7.407     0.999992       299897    131072.00
       7.407     0.999993       299897    145635.56
       7.423     0.999994       299898    163840.00
       7.423     0.999995       299898    187245.71
       7.423     0.999995       299898    218453.33
       7.423     0.999996       299898    262144.00
       7.423     0.999997       299898    291271.11
       7.635     0.999997       299899    327680.00
       7.635     1.000000       299899          inf
```

Сервер справляется с `98%` запросов, что на `6%` больше, чем до внедрения модификаций. Это говорит о том, что добавленная асинхронность действительно весомо влияет на работу сервера, хоть он и продолжает захлёбываться при `2%` запросов, всё же прирост `6%` более, чем ощутим.

<hr>

## *GET*

До модификации сервер так же не полноценно справлялся с `GET` запросами: 

```
	   0.031     0.000000            1         1.00
       0.273     0.100000        30085         1.11
       0.490     0.200000        60029         1.25
       0.704     0.300000        90020         1.43
       0.898     0.400000       119974         1.67
       1.011     0.500000       150164         2.00
       1.065     0.550000       164934         2.22
       1.136     0.600000       180045         2.50
       1.241     0.650000       195051         2.86
       1.347     0.700000       209946         3.33
       1.456     0.750000       224966         4.00
       1.512     0.775000       232491         4.44
       1.565     0.800000       239934         5.00
       1.620     0.825000       247403         5.71
       1.675     0.850000       254902         6.67
       1.731     0.875000       262414         8.00
       1.757     0.887500       266159         8.89
       1.784     0.900000       269897        10.00
       1.812     0.912500       273686        11.43
       1.840     0.925000       277395        13.33
       1.868     0.937500       281216        16.00
       1.882     0.943750       283093        17.78
       1.896     0.950000       284967        20.00
       1.910     0.956250       286823        22.86
       1.925     0.962500       288715        26.67
       1.948     0.968750       290506        32.00
       1.976     0.971875       291463        35.56
       2.043     0.975000       292388        40.00
       2.185     0.978125       293318        45.71
       2.509     0.981250       294254        53.33
       3.163     0.984375       295192        64.00
       3.653     0.985938       295659        71.11
       4.239     0.987500       296132        80.00
       4.919     0.989062       296597        91.43
       5.751     0.990625       297065       106.67
       6.447     0.992188       297536       128.00
       6.791     0.992969       297770       142.22
       7.087     0.993750       298002       160.00
       7.359     0.994531       298237       182.86
       7.667     0.995313       298473       213.33
       8.043     0.996094       298709       256.00
       8.247     0.996484       298823       284.44
       8.607     0.996875       298940       320.00
       9.079     0.997266       299057       365.71
       9.655     0.997656       299174       426.67
      10.151     0.998047       299292       512.00
      10.407     0.998242       299350       568.89
      10.959     0.998437       299409       640.00
      11.495     0.998633       299467       731.43
      12.359     0.998828       299526       853.33
      13.103     0.999023       299585      1024.00
      13.503     0.999121       299617      1137.78
      13.871     0.999219       299643      1280.00
      14.239     0.999316       299672      1462.86
      14.543     0.999414       299701      1706.67
      14.935     0.999512       299730      2048.00
      15.167     0.999561       299745      2275.56
      15.391     0.999609       299759      2560.00
      15.663     0.999658       299774      2925.71
      16.023     0.999707       299789      3413.33
      16.495     0.999756       299804      4096.00
      16.719     0.999780       299811      4551.11
      16.959     0.999805       299818      5120.00
      17.199     0.999829       299825      5851.43
      17.519     0.999854       299833      6826.67
      17.775     0.999878       299841      8192.00
      17.951     0.999890       299844      9102.22
      18.127     0.999902       299847     10240.00
      18.319     0.999915       299851     11702.86
      18.527     0.999927       299855     13653.33
      18.735     0.999939       299858     16384.00
      18.815     0.999945       299860     18204.44
      18.959     0.999951       299862     20480.00
      19.167     0.999957       299864     23405.71
      19.375     0.999963       299866     27306.67
      19.455     0.999969       299867     32768.00
      19.583     0.999973       299868     36408.89
      19.663     0.999976       299869     40960.00
      19.791     0.999979       299870     46811.43
      19.823     0.999982       299871     54613.33
      19.999     0.999985       299872     65536.00
      19.999     0.999986       299872     72817.78
      20.207     0.999988       299873     81920.00
      20.207     0.999989       299873     93622.86
      20.431     0.999991       299874    109226.67
      20.431     0.999992       299874    131072.00
      20.431     0.999993       299874    145635.56
      20.575     0.999994       299875    163840.00
      20.575     0.999995       299875    187245.71
      20.575     0.999995       299875    218453.33
      20.575     0.999996       299875    262144.00
      20.575     0.999997       299875    291271.11
      20.767     0.999997       299876    327680.00
      20.767     1.000000       299876          inf
```

Примерно `97%` запросов обрабатываются быстрее, чем за `2ms`, потом сервер перестаёт справляться и перфоманс резко начинает ухудшаться.

После добавления асинхронности сервер стал справляться с большим числом запросов за адекватное время:

```
	   0.030     0.000000            2         1.00
       0.259     0.100000        30075         1.11
       0.472     0.200000        60003         1.25
       0.685     0.300000        90069         1.43
       0.885     0.400000       120196         1.67
       0.993     0.500000       150200         2.00
       1.047     0.550000       165039         2.22
       1.103     0.600000       179998         2.50
       1.205     0.650000       194993         2.86
       1.311     0.700000       209970         3.33
       1.416     0.750000       225009         4.00
       1.469     0.775000       232448         4.44
       1.522     0.800000       239984         5.00
       1.573     0.825000       247457         5.71
       1.627     0.850000       255040         6.67
       1.680     0.875000       262464         8.00
       1.706     0.887500       266163         8.89
       1.733     0.900000       270041        10.00
       1.758     0.912500       273687        11.43
       1.784     0.925000       277460        13.33
       1.811     0.937500       281225        16.00
       1.825     0.943750       283146        17.78
       1.838     0.950000       284958        20.00
       1.852     0.956250       286863        22.86
       1.865     0.962500       288778        26.67
       1.878     0.968750       290581        32.00
       1.885     0.971875       291549        35.56
       1.892     0.975000       292553        40.00
       1.898     0.978125       293350        45.71
       1.906     0.981250       294387        53.33
       1.912     0.984375       295223        64.00
       1.916     0.985938       295776        71.11
       1.919     0.987500       296153        80.00
       1.924     0.989062       296705        91.43
       1.929     0.990625       297136       106.67
       1.936     0.992188       297621       128.00
       1.939     0.992969       297802       142.22
       1.944     0.993750       298031       160.00
       1.952     0.994531       298273       182.86
       1.962     0.995313       298516       213.33
       1.977     0.996094       298735       256.00
       1.987     0.996484       298848       284.44
       2.003     0.996875       298964       320.00
       2.020     0.997266       299082       365.71
       2.043     0.997656       299202       426.67
       2.073     0.998047       299317       512.00
       2.089     0.998242       299374       568.89
       2.109     0.998437       299434       640.00
       2.131     0.998633       299491       731.43
       2.191     0.998828       299549       853.33
       2.335     0.999023       299608      1024.00
       2.451     0.999121       299637      1137.78
       2.581     0.999219       299666      1280.00
       2.771     0.999316       299695      1462.86
       2.953     0.999414       299725      1706.67
       3.153     0.999512       299754      2048.00
       3.239     0.999561       299769      2275.56
       3.329     0.999609       299783      2560.00
       3.419     0.999658       299799      2925.71
       3.567     0.999707       299813      3413.33
       3.723     0.999756       299827      4096.00
       3.827     0.999780       299835      4551.11
       3.889     0.999805       299842      5120.00
       4.009     0.999829       299849      5851.43
       4.085     0.999854       299857      6826.67
       4.255     0.999878       299864      8192.00
       4.387     0.999890       299868      9102.22
       4.471     0.999902       299871     10240.00
       4.535     0.999915       299875     11702.86
       4.747     0.999927       299879     13653.33
       5.127     0.999939       299882     16384.00
       5.403     0.999945       299884     18204.44
       5.839     0.999951       299886     20480.00
       6.283     0.999957       299888     23405.71
       6.719     0.999963       299890     27306.67
       6.935     0.999969       299891     32768.00
       7.155     0.999973       299892     36408.89
       7.363     0.999976       299893     40960.00
       7.575     0.999979       299894     46811.43
       7.783     0.999982       299895     54613.33
       7.991     0.999985       299896     65536.00
       7.991     0.999986       299896     72817.78
       8.199     0.999988       299897     81920.00
       8.199     0.999989       299897     93622.86
       8.407     0.999991       299898    109226.67
       8.407     0.999992       299898    131072.00
       8.407     0.999993       299898    145635.56
       8.591     0.999994       299899    163840.00
       8.591     0.999995       299899    187245.71
       8.591     0.999995       299899    218453.33
       8.591     0.999996       299899    262144.00
       8.591     0.999997       299899    291271.11
       8.775     0.999997       299900    327680.00
       8.775     1.000000       299900          inf
```

Как видно, время ответа сервера линейно растёт при обработке `99%` запросов и составляет не более `2ms`, это связано с тем, что с каждым следующим запросом необходимо проверить больше таблиц на диске (ключи были добавлены последовательно, `get` так же бьёт последовательно), но потом сервер начинает захлёбываться и ответы приходят медленнее вплоть до `4` раз.


<hr>
Данные результаты были получены при использовании четырёх потоков и четырёх подключений, если же увеличить количество потоков и соединений, например, до значений `-t 4 -c 64`, то перфоманс будет идентичный, что до внедрения асинхронности, что после.

## *Профилирование с помощью async-profiler*

При профилировании было выявлено, что плюсы, которые привносит асинхронность, а именно параллельная обработка запросов и разгрузка селекторов, нивелируется блокировками очереди. Примерно `10% CPU` тратится на обработку метода `take()` у реализации блокирующей очереди. За счет асинхронности аллокации стали распределены равномерно, но блокирующая очередь так же требует дополнительных аллокаций. В целом, добавление ассинхронности дает заметное улучшение перфоманса при низкой нагрузке на сервер, но, если нагрузка возрастает, то возрастает и дополнительные расходы, алгоритм явно нуждается в доработке.