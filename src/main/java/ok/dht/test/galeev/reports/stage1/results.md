# PUT

####Приведу текст скрипта на lua:

[Lua put script](java/ok/dht/test/galeev/reports/scritps/put.lua)
```
cnt = 0
request = function()
    uri = "/v0/entity?id=K:" .. cnt
    wrk.body = "V:" .. cnt
    cnt = cnt + 1
    return wrk.format("PUT", uri)
end
```

## 13.5K
После перебора кучи вариантов параметра `-R` - пришел к выводу, что это наиболее оптимальный

```
└─$ wrk -t 1 -c 1 -d 60s -s /media/coradead/Windows1/Users/CORADEAD/IdeaProjects/2022-highload-dht/src/main/java/ok/dht/test/galeev/reports/scritps/put.lua -L http://localhost:19234 -R 13500
Running 1m test @ http://localhost:19234
  1 threads and 1 connections
  Thread calibration: mean lat.: 32.014ms, rate sampling interval: 342ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     2.18ms    7.12ms  86.53ms   95.52%
    Req/Sec    13.52k   232.48    15.20k    91.78%
  Latency Distribution (HdrHistogram - Recorded Latency)
 50.000%  745.00us
 75.000%    1.07ms
 90.000%    2.22ms
 99.000%   46.17ms
 99.900%   70.78ms
 99.990%   85.06ms
 99.999%   86.59ms
100.000%   86.59ms
----------------------------------------------------------
  809970 requests in 1.00m, 51.75MB read
Requests/sec:  13499.82
Transfer/sec:      0.86MB
```

## 14K

```
└─$ wrk -t 1 -c 1 -d 60s -s /media/coradead/Windows1/Users/CORADEAD/IdeaProjects/2022-highload-dht/src/main/java/ok/dht/test/galeev/reports/scritps/put.lua -L http://localhost:19234 -R 14000 
Running 1m test @ http://localhost:19234
  1 threads and 1 connections
  Thread calibration: mean lat.: 93.228ms, rate sampling interval: 645ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     6.25ms   12.63ms  67.20ms   90.21%
    Req/Sec    14.01k   245.50    15.35k    87.01%
  Latency Distribution (HdrHistogram - Recorded Latency)
 50.000%    0.92ms
 75.000%    4.93ms
 90.000%   18.64ms
 99.000%   58.21ms
 99.900%   65.60ms
 99.990%   66.82ms
 99.999%   67.20ms
100.000%   67.26ms
----------------------------------------------------------
  839968 requests in 1.00m, 53.67MB read
Requests/sec:  13999.63
Transfer/sec:      0.89MB
```

## Заполненная БД
```
└─$ wrk -t 1 -c 1 -d 60s -s /media/coradead/Windows1/Users/CORADEAD/IdeaProjects/2022-highload-dht/src/main/java/ok/dht/test/galeev/reports/scritps/put.lua -L http://localhost:19234 -R 13500  
Running 1m test @ http://localhost:19234
  1 threads and 1 connections
  Thread calibration: mean lat.: 124.930ms, rate sampling interval: 764ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     2.74ms    6.68ms  52.99ms   92.75%
    Req/Sec    13.51k   115.23    14.05k    90.77%
  Latency Distribution (HdrHistogram - Recorded Latency)
 50.000%  774.00us
 75.000%    1.10ms
 90.000%    5.04ms
 99.000%   29.77ms
 99.900%   51.39ms
 99.990%   52.83ms
 99.999%   52.99ms
100.000%   53.02ms
----------------------------------------------------------
  809972 requests in 1.00m, 51.75MB read
Requests/sec:  13499.77
Transfer/sec:      0.86MB

```


## Графики:
### 1. Plotted latency graph для `put` на рейте в 13.5K
![put graph](./PNGs/PutHistogram.png)

### 2. Plotted latency graph для `put` на рейте в 14K
![put graph](./PNGs/Put14KHistogram.png)

### 3. Plotted latency graph для `put` при заполненной БД
![put graph](./PNGs/PutFullBDHistogram.png)

### 4. CPU async profiler 13.5K
![put cpu](./PNGs/cpu_put.png)

### 5. CPU async profiler 14K
![put cpu](./PNGs/cpu_put14k.png)

### 6. CPU async profiler 13.5K c заполненной БД
![put cpu](./PNGs/cpu_put_full_bd.png)

### 7. Alloc async profiler 13.5K
![put alloc](./PNGs/alloc_put.png)

### 8. Alloc async profiler 14K
![put alloc](./PNGs/alloc_put14k.png)

### 9. Alloc async profiler 13.5K c заполненной БД
![put alloc](./PNGs/alloc_put_full_bd.png)

# GET

Для тестирования `get` была создана БД на 3Гб(примерно 70млн `entry`), размер файла 8Мб

####Так же код на lua:
[Lua get script](java/ok/dht/test/galeev/reports/scritps/get.lua)
```
cnt = 0
request = function()
    uri = string.format("/v0/entity?id=k%010d", cnt)
    cnt = cnt + 1
    return wrk.format("GET", uri, {})
end
```

## 500
Пожалуй сразу попали в приемлемый результат
```
└─$ wrk -t 1 -c 1 -d 60s -s /media/coradead/Windows1/Users/CORADEAD/IdeaProjects/2022-highload-dht/src/main/java/ok/dht/test/galeev/reports/scritps/get.lua -L http://localhost:19234 -R 500  
Running 1m test @ http://localhost:19234
  1 threads and 1 connections
  Thread calibration: mean lat.: 2.104ms, rate sampling interval: 10ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     1.92ms  484.60us  12.91ms   71.75%
    Req/Sec   526.14     66.87   800.00     63.94%
  Latency Distribution (HdrHistogram - Recorded Latency)
 50.000%    1.92ms
 75.000%    2.22ms
 90.000%    2.48ms
 99.000%    2.81ms
 99.900%    5.14ms
 99.990%   11.50ms
 99.999%   12.92ms
100.000%   12.92ms
----------------------------------------------------------
  30000 requests in 1.00m, 2.12MB read
Requests/sec:    500.00
Transfer/sec:     36.13KB
```

###Графики:
#### 1. Plotted latency graph для `get`
![get graph](./PNGs/GetHistogram.png)

### 2. CPU async profiler
![get cpu](./PNGs/cpu_get.png)

### 3. Alloc async profiler
![get alloc](./PNGs/alloc_get.png)

#Вывод

В первую очередь хочется заметить, что на данном железе нагрузка в 13.5К является наиболее оптимальной, потому что при увеличении даже до 14к - задержка увеличивается с 2мс до 6мс.

Ограничения по памяти мы не можем изменять, поэтому объективно оценить влияние объема ОЗУ мы не можем, но можем предположить, что это позволит операционной системе не сразу загружать файлы на жесткий диск, а только при заполнении ОЗУ.

При заполненной бд задержки никак не изменяются, потому что это особенность реализации реализованной нами бд. Новые записи просто дописываются в конец новым файлом, а не перезаписывают старые, что мы и видим в результатах(погрешность и все такое).





