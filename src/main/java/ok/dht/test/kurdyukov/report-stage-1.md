# Отчет  

## Введение

Работа выполнена c LevelDB, для нагрузочного тестирования использовался wrk2.

Референсная реализация вызывала ряд вопросов, к примеру flush происходил лишь единожды.
Поэтому у меня была высокая летенси (в предыдущих коммитах), так как все копилось в диске.  

Для формирования Get - запроса использовался [get_http_query](./lua/get_http_query.lua), 
который обращается в рандомную ячейку таблицы. Это лучшая имитация пользовательских запросов.

Для формирования Post - запроса использовался [put_http_query](./lua/put_http_query.lua). 
В отличие от Get запись происходит последовательно. 

## Ход работы 


На 10000 rate база начала захлебываться, выдавая 300мс средний тайминг. 
Но value была строка размера 2100 символов.
На такое большое сообщение хватает 4000 rate.

Команда заполнения базы и результаты:
   
    kurdyukov-kir@i109817004 lua % wrk2 -d 5s -c 1 -t 1 -s put_http_query.lua -R 10000  http://localhost:4242
    Running 5s test @ http://localhost:4242
       1 threads and 1 connections
       Thread Stats   Avg      Stdev     Max   +/- Stdev
       Latency   307.37ms  477.36ms   1.14s    72.24%
       Req/Sec        nan       nan   0.00      0.00%
       39871 requests in 5.00s, 2.55MB read
      Requests/sec:   7974.57
      Transfer/sec:    521.77KB


На 10000 rate с value 700 символов, вполне можно выжимать 10000 rate. 

Заполняем базу три минуты, все равно видно, что 52 мс не то что мы хотели увидеть.

    kurdyukov-kir@i109817004 lua % wrk2 -d 3m -c 1 -t 1 -s put_http_query.lua -R 10000  http://localhost:4242
    Running 3m test @ http://localhost:4242
       1 threads and 1 connections
       Thread calibration: mean lat.: 26.477ms, rate sampling interval: 168ms
       Thread Stats   Avg      Stdev     Max   +/- Stdev
          Latency    52.53ms   82.51ms 418.05ms   85.06%
          Req/Sec    10.01k     1.36k   13.60k    70.92%
        1796576 requests in 3.00m, 114.79MB read
      Requests/sec:   9980.98
      Transfer/sec:    653.05KB

При 6000 rate вполне себе 1.5мс.

```
kurdyukov-kir@i109817004 lua % wrk2 -d 1m -c 1 -t 1 -s put_http_query.lua -R 6000  http://localhost:4242
Running 1m test @ http://localhost:4242
  1 threads and 1 connections
  Thread calibration: mean lat.: 1.240ms, rate sampling interval: 10ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     1.54ms    2.85ms  43.10ms   96.14%
    Req/Sec     6.33k     1.31k   17.78k    90.99%
  359995 requests in 1.00m, 23.00MB read
Requests/sec:   5999.93
Transfer/sec:    392.57KB
```

Итого размер:

    kurdyukov-kir@i109817004 ~ % du -sh data
    1,2G	data

Стрельба на гет запросы на 10000 rate привела к ожидаемым плачевным результатам:

    kurdyukov-kir@i109817004 lua % wrk2 -d 30s -c 1 -t 1 -s get_http_query.lua -R 10000  http://localhost:4242
    Running 30s test @ http://localhost:4242
      1 threads and 1 connections
      Thread calibration: mean lat.: 2719.007ms, rate sampling interval: 8298ms
      Thread Stats   Avg      Stdev     Max   +/- Stdev
        Latency     8.35s     2.26s   12.20s    56.33%
        Req/Sec     6.13k    56.00     6.18k    50.00%
      177992 requests in 30.00s, 130.35MB read
    Requests/sec:   5933.09
    Transfer/sec:      4.34MB

Это объясняется особенностью нашей бд. Наша бд - это лсм дерево, 
структура которой решает в первую очередь задачу на быструю запись. Это, например, хранилище логов.

Запросы на получение, здесь несильно оптимальны. 

Можно увидеть на этой картиночки сколько отъедает процессорного времени [get](./profiler/png/get_cpu.png).
Это еще обусловленно тем, что у нас безпорядочное чтение.

С аллокацией еще хуже [get](./profiler/png/get_alloc.png).

3000 rate дают вполне подходящие тайминги. 

```
kurdyukov-kir@i109817004 lua % wrk2 -d 30s -c 1 -t 1 -s get_http_query.lua -R 3000  http://localhost:4242
Running 30s test @ http://localhost:4242
  1 threads and 1 connections
  Thread calibration: mean lat.: 1.972ms, rate sampling interval: 10ms
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     1.26ms    1.83ms  37.57ms   99.14%
    Req/Sec     3.18k   461.22    12.90k    91.85%
  89994 requests in 30.00s, 65.90MB read
Requests/sec:   2999.79
Transfer/sec:      2.20MB
```

## Выводы

`PUT` - видно, что большенство аллокаций происходят на запись в базу, также в внедрах one-nio и на формирование response.
   Распределени сpu [здесь](./profiler/png/put_cpu.png), в целом видно, что база ест большую часть cpu.
   Возможная оптимизация LevelDB имеет возможность писать батчами, НО в дальнейшем сервис должен стать многопоточным,
   что очень вероятно приведет к дополнительным рассходам даже с лок - фри структурами на формирование батча.
   Есть гипотеза что это будет дороже, чем писать одиночно. Также можно за ранее создать пустые ответы. 

   6000 rate - сервер отвечал стабильно не захлебывался, в среднем время ответа 1.54ms, max - 43.10ms. 

`GET` - Много аллокаций даже больше чем в [PUT](./profiler/png/put_alloc.png), тратяться на бд. 
   Вызвано тем, что на случайные запросы по ключу
   мы часто ходим в диск (в разные батчи). Опять же такая база не предназначена для большого чтения, здесь нужен
   SQL или другой подход. Здесь все - таки парадигма хранилища и хороший летенси на запись.
   Единственная оптимизация увеличить размер memery table, но это мало чем поможет.

   3000 rate - сервер отвечал стабильно не захлебывался, в среднем время ответа 1.26ms, max - 37.57ms. 

В целом на стороне сервиса никаких задержек - следующая разумная оптимизация асинхронщина.

Также возможное улучшение уйти от аннотаций и перейти на явный handle ручек. (Избавиться от Spring - style)
 

