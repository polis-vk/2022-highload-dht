# Отчет  

## Введение

Для нагрузочного тестирования использовался wrk2.

Для формирования Get - запроса использовался [get_http_query](./lua/get_http_query.lua), 
который обращается в рандомную ячейку таблицы. Это лучшая имитация пользовательских запросов.

Для формирования Post - запроса использовался [put_http_query](./lua/put_http_query.lua). 
В отличие от Get запись происходит последовательно. 

Команда заполнения базы и результаты:

    wrk2 -d 6m -c 1 -t 1 -s put_http_query.lua -R 10000 -L http://localhost:4242

    1 threads and 1 connections
    Thread calibration: mean lat.: 1.563ms, rate sampling interval: 10ms
    Thread Stats   Avg      Stdev     Max   +/- Stdev
      Latency     0.99ms    1.70ms  58.50ms   99.02%
      Req/Sec    10.56k     1.17k   26.78k    79.84%
     Latency Distribution (HdrHistogram - Recorded Latency)
    50.000%  843.00us
    75.000%    1.16ms
    90.000%    1.68ms
    99.000%    2.59ms
    99.900%   28.85ms
    99.990%   50.62ms
    99.999%   58.01ms
    100.000%   58.53ms

Команда и результаты беспорядочного получения данных:
    
    wrk2 -d 3m -c 1 -t 1 -s get_http_query.lua -R 10000 -L http://localhost:4242
    
    Running 3m test @ http://localhost:4242
      1 threads and 1 connections
      Thread calibration: mean lat.: 0.855ms, rate sampling interval: 10ms
      Thread Stats   Avg      Stdev     Max   +/- Stdev
        Latency     3.47ms   19.27ms 243.20ms   97.59%
        Req/Sec    10.57k     1.48k   26.00k    89.52%
      Latency Distribution (HdrHistogram - Recorded Latency)
    50.000%  832.00us
    75.000%    1.16ms
    90.000%    1.74ms
    99.000%   99.33ms
    99.900%  235.65ms
    99.990%  242.56ms
    99.999%  243.20ms
    100.000%  243.33ms


      1799986 requests in 3.00m, 1.29GB read
    Requests/sec:   9999.93
    Transfer/sec:      7.32MB

Приблизительно в базе 1,5 GB.

Как и было обещано запись работает на порядок быстрее чтения!

## Выводы

1. Про выделения памяти можно заменить, 
что большие выделения памяти происходят при добавлении таблицу, 
что вызвано  особенностью нашей бд.

2. При прочтении тела запроса тратиться много аллокаций. 
Utf8 утильный класс создает 10 процентов аллокций.

3. Процессорное время также сжирает конвертация строки в байты. 
   Возможное ускорение оперировать байтами на прямую. 

