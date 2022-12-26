#Отчет по Stage 1

##Нагрузочное тестирование

### Method PUT
<ul>
  <li><b>Connections</b>: 1</li>
  <li><b>Threads</b>: 1</li>
  <li><b>Rate</b>: 19000</li>
  <li><b>Duration</b>: 60s</li>
  <li><b>Размер body</b>: 10 bytes</li>
</ul>

####Результат:

```
Thread Stats   Avg      Stdev     Max       +/- Stdev
Latency        0.97ms   1.30ms    17.41ms   95.51%
Req/Sec        19.50k   830.75    21.74k    77.71%
Latency Distribution (HdrHistogram - Recorded Latency)
50.000%  782.00us
75.000%    1.12ms
90.000%    1.39ms
99.000%    7.55ms
99.900%   16.12ms
99.990%   17.36ms
99.999%   17.41ms
100.000%  17.42ms



1139978 requests in 1.00m, 72.84MB read
Requests/sec:  18999.76
Transfer/sec:      1.21MB
```
Задержка приемлемая - в среднем не превышает 1ms. По процентилям тоже все хорошо: если верить результатам, 
то максимальная задержка укладывается в 20ms, что является довольно хорошим результатом. 

При увеличении параметра Rate - задержка начинает сильно расти. Если задать Rate 20000 - средняя задержка выйдет за 100ms. 
Соотношение RPS/Rate так же падает:
```
Latency   161.06ms  137.18ms 510.98ms   65.07%
Req/Sec    19.89k     0.85k   20.57k    89.22%

1189748 requests in 1.00m, 76.02MB read
Requests/sec:  19829.45
Transfer/sec:      1.27MB
```

### Method GET


##Профилирование