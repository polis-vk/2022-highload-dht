Для начала выберем размер очереди запросов для threadPoolExecutor = 128 и дефолтную политику обработки запросов, 
не влезающих в очередь - AbortPolicy.

# PUT
Нагружаем базу данных put запросами в 8 потоков, 64 соединения в течение 5 минут с rate = 10000 запросов/сек.

```bash
veronika@MacBook-Pro-Veronika anikina % wrk2 -t 8 -c 64 -d 5m -R 10000 -s lua/put.lua http://localhost:8080
Running 5m test @ http://localhost:8080
  8 threads and 64 connections
  Thread calibration: mean lat.: 0.880ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 0.878ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 0.887ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 0.884ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 0.885ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 0.883ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 0.886ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 0.895ms, rate sampling interval: 10ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     0.88ms  425.34us  16.43ms   67.56%
    Req/Sec     1.31k   116.29     2.70k    73.15%
  2999845 requests in 5.00m, 191.68MB read
Requests/sec:   9999.50
Transfer/sec:    654.26KB
```
Средняя latency составляет 0.88ms, что немного больше, чем в синхронной версии, тогда latency составляло 0.724ms.
Это скорее всего связано с тем, что теперь потоки должны синхронизироваться как для работы с очередью задач,
так и для добавления в dao, поэтому среднее время на ответ немного выше, но и пропускная способность больше.

## Профилирование

### CPU
22% времени тратится потоком на синхронизацию в очереди задач.
13% времени уходит на обработку запроса, из них 10% на запись response и 3% на реальную логику.
33% времени занимают селекты и 25% уходит на чтение запроса.

### ALLOC
handleRequest занял 33% аллокаций, из них 13% происходит при записи в dao, 20% при парсинге request.
10% аллокаций использует one.nio для записи response и 2% занимает flush на диск.
43% уходит на селектор треды, из них 40% занимает one.nio на чтение запроса.

### LOCK
78% уходит на синхронизацию потоков для получения задачи из очереди, 13% при синхронизации 
worker тредов для записи в dao и 8% приходится на селектор треды.

# GET

```bash
veronika@MacBook-Pro-Veronika anikina % wrk2 -t 8 -c 64 -d 5m -R 10000 -s lua/get.lua http://localhost:8080
Running 5m test @ http://localhost:8080
  8 threads and 64 connections
  Thread calibration: mean lat.: 0.893ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 0.907ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 0.869ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 0.899ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 0.844ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 0.922ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 0.903ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 0.879ms, rate sampling interval: 10ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     0.90ms  595.20us  33.60ms   85.72%
    Req/Sec     1.31k   124.11     5.22k    77.58%
  2999847 requests in 5.00m, 1.18GB read
Requests/sec:   9999.50
Transfer/sec:      4.04MB
```

Средняя latency 1.63ms, что намного быстрее чем в синхронной версии - 2.49m.

## Профилирование

### CPU
Аналогично с put тратим 18% времени на синхронизацию в очереди.
19% времени занимает чтение из dao, 10% уходит на запись response, 28% занимают select треды, 
18% уходит на чтение запроса.

### ALLOC
45% аллокаций занимает чтение из dao, 47% занимают селектор треды, из них 33% уходит на чтение запроса.

### LOCK
Аналогично с put запросами, 85% происходит при синхронизации потоков в очереди задач. 
Также 3% при синхронизации в dao и 11% приходится на селектор треды.

Теперь попробуем увеличить размер очереди до 1024.  

# PUT

```bash
veronika@MacBook-Pro-Veronika anikina % wrk2 -t 8 -c 64 -d 2m -R 15000 -s lua/put.lua http://localhost:8080
Running 2m test @ http://localhost:8080
8 threads and 64 connections
Thread calibration: mean lat.: 1.121ms, rate sampling interval: 10ms
Thread calibration: mean lat.: 1.086ms, rate sampling interval: 10ms
Thread calibration: mean lat.: 1.131ms, rate sampling interval: 10ms
Thread calibration: mean lat.: 1.128ms, rate sampling interval: 10ms
Thread calibration: mean lat.: 1.117ms, rate sampling interval: 10ms
Thread calibration: mean lat.: 1.151ms, rate sampling interval: 10ms
Thread calibration: mean lat.: 1.118ms, rate sampling interval: 10ms
Thread calibration: mean lat.: 1.139ms, rate sampling interval: 10ms
Thread Stats   Avg      Stdev     Max   +/- Stdev
Latency     1.17ms    1.15ms  39.74ms   95.63%
Req/Sec     1.96k   272.44     7.89k    89.72%
1799757 requests in 2.00m, 115.00MB read
Requests/sec:  14997.96
Transfer/sec:      0.96MB
```
Сервис стал выдерживать больший rate, до этого максимальный был 10000, при большем сервис не справлялся с нагрузкой.

# GET

```bash
veronika@MacBook-Pro-Veronika anikina % wrk2 -t 8 -c 64 -d 2m -R 15000 -s lua/get.lua http://localhost:8080
Running 2m test @ http://localhost:8080
  8 threads and 64 connections
  Thread calibration: mean lat.: 0.997ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 0.995ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.003ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 0.957ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 0.999ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 0.997ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 1.014ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 0.996ms, rate sampling interval: 10ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     1.10ms  829.68us  15.22ms   89.26%
    Req/Sec     1.96k   228.80     4.11k    77.21%
  1799761 requests in 2.00m, 727.75MB read
Requests/sec:  14997.96
Transfer/sec:      6.06MB
```
 
Latency на чтение уменьшилась в 1.5 раза, но при этом и rate мы увеличили. Отсюда можно сделать вывод, 
что от размера очереди зависит пропускная способность сервиса.  

   
Теперь возьмем другую политику обработки запросов - discardOldestPolicy.  

# PUT

```bash
veronika@MacBook-Pro-Veronika anikina % wrk2 -t 8 -c 64 -d 3m -R 10000 -s lua/put.lua http://localhost:8080
Running 3m test @ http://localhost:8080
  8 threads and 64 connections
  Thread calibration: mean lat.: 0.940ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 0.957ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 0.946ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 0.945ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 0.944ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 0.950ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 0.948ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 0.942ms, rate sampling interval: 10ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     0.89ms  506.59us  15.72ms   75.44%
    Req/Sec     1.31k   133.93     2.78k    80.17%
  1799848 requests in 3.00m, 115.00MB read
Requests/sec:   9999.18
Transfer/sec:    654.24KB
```

Latency не изменилась по сравнению с предыдущим запуском.

## Профилирование

### CPU

22% времени тратится на синхронизацию потоков в очереди запросов, 12% времени уходит на обработку запроса, 
из них 2% на реальную логику и 10% на запись ответа.
32% занимают селекты, 26% времени занимает one.nio чтением запроса, что не отличается от abortPolicy.

### ALLOC

13% аллокаций занимает запись в dao, 8% уходит на формирование response, 11% занимает one.nio на парсинг запроса, 
11% уходит на запись ответа и всего 2% занимают flush на диск. Так же можно заметить, что около 3% аллокаций занимает 
извлечение таски из очереди threadPoolExecutor. Все остальное - 44% - уходит на селекты, из них 40% - чтение запроса.

### LOCK

82% времени занимает синхронизация потоков в очереди задач для threadPoolExecutor, что на 5% больше чем для abortPolicy. 
Около 7% приходится на селектор треды и 10% на синхронизацию worker тредов для записи в dao.

# GET

```bash
veronika@MacBook-Pro-Veronika anikina % wrk2 -t 8 -c 64 -d 5m -R 10000 -s lua/get.lua http://localhost:8080
Running 5m test @ http://localhost:8080
  8 threads and 64 connections
  Thread calibration: mean lat.: 0.949ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 0.958ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 0.965ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 0.950ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 0.938ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 0.962ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 0.927ms, rate sampling interval: 10ms
  Thread calibration: mean lat.: 0.934ms, rate sampling interval: 10ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     0.96ms  563.15us  20.37ms   78.82%
    Req/Sec     1.31k   123.30     3.11k    76.43%
  2999851 requests in 5.00m, 1.18GB read
Requests/sec:   9999.46
Transfer/sec:      4.04MB
```

## Профилирование

### CPU
24% времени занимает чтение данных с диска, и так же около 10% занимает one.nio на запись response. 
25% времени уходит на синхронизацию в очереди, и 24% на селекты. 12% занимает чтение запроса.

### ALLOC
44% аллокаций происходит при чтении из dao, 2% занимает конвертация из строки в байты и 2% занимает запись ответа.
34% уходит на чтение и парсинг запроса селектор тредами.

### LOCK
Аналогично put запросам, 87% уходит на синхронизацию потоков в очереди задач, 8% на селектор треды и 
только 4% на синхронизацию потоков в рамках dao.

# Выводы
Можно сделать вывод, что от размера очереди задач на executor сервиса зависит пропускная способность сервиса.
Увеличив со 128 до 1024, мы получили заметное увеличение rate с 10000 до 15000 запросов/сек.

Разницы в производительности между abortPolicy и discardOldestPolicy особо нет, но в первой меньше времени уходит 
на синхронизацию потоков в очереди.

Большое количество cpu занимает синхронизация потоков в очереди задач, что, вероятно, происходит из-за использования
реализации очереди с блокировками. В качестве оптимизации можно попробовать использовать lock-free очередь, 
например, очередь Майкла-Скотта. Так же парсинг запросов и преобразование из строк в байты занимают как cpu время, так 
и значительное число аллокаций. Так как это делает one.nio, один из возможных вариантов для нас использовать более 
эффективный фреймворк или написать свой :)

