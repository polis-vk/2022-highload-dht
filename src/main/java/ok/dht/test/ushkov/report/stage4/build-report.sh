#!/bin/bash

cat <<EOF > "report.md"
# Отчет

В данном стейдже мной был взят алгоритм
с разбора дз про шардирования и дополнен таким образом, чтобы уметь
работать с несколькими репликами.

# Профилирование

Профилирование без использование репликаций показывает, что реализация сильно замедлилась.
Связано это с тем, что по сравнению с предыдущей реализацией появилась логика для:

* Поддержки репликаций
* Решения проблемы с болеющей нодой, которая тормозит всех остальных

Наблюдается также значительное количество не 2xx и 3xx запросов, что связано
с ограниченностью очередей.

## PUT t=4 c=64 R=10000 d=1m
Из-за
wrk2 output:
\`\`\`
$(cat "profiles/2022-10-26-18-11-11_notag_put_t4_c64_R10000_d1m/wrk2.txt")
\`\`\`

[cpu heatmap & flame graph](profiles/2022-10-26-18-11-11_notag_put_t4_c64_R10000_d1m/cpu.html)
![image](profiles/2022-10-26-18-11-11_notag_put_t4_c64_R10000_d1m/cpu.png)

[alloc heatmap & flame graph](profiles/2022-10-26-18-11-11_notag_put_t4_c64_R10000_d1m/alloc.html)
![image](profiles/2022-10-26-18-11-11_notag_put_t4_c64_R10000_d1m/alloc.png)

[lock flame graph](profiles/2022-10-26-18-11-11_notag_put_t4_c64_R10000_d1m/lock.html)
![image](profiles/2022-10-26-18-11-11_notag_put_t4_c64_R10000_d1m/lock.png)


Теперь проведем тот же опыт, только для с параметрами `ack=2,from=3`, чтоьы
посмотреть, насколько быстро наша реализация справляется с записью сразу в несколько
реплик.

wrk2 output:
\`\`\`
$(cat "profiles/2022-10-26-19-10-07_ack2from3_put_t4_c64_R10000_d1m/wrk2.txt")
\`\`\`

[cpu heatmap & flame graph](profiles/2022-10-26-19-10-07_ack2from3_put_t4_c64_R10000_d1m/cpu.html)
![image](profiles/2022-10-26-19-10-07_ack2from3_put_t4_c64_R10000_d1m/cpu.png)

[alloc heatmap & flame graph](profiles/2022-10-26-19-10-07_ack2from3_put_t4_c64_R10000_d1m/alloc.html)
![image](profiles/2022-10-26-19-10-07_ack2from3_put_t4_c64_R10000_d1m/alloc.png)

[lock flame graph](profiles/2022-10-26-19-10-07_ack2from3_put_t4_c64_R10000_d1m/lock.html)
![image](profiles/2022-10-26-19-10-07_ack2from3_put_t4_c64_R10000_d1m/lock.png)

Как мы видим, благодаря тому, что все три ноды обрабатываются параллельно нет
резкого уменьшения RPS.

Аналогичную картину можно наблюдать на GET

Для обычного GET:
wrk2 output:
\`\`\`
$(cat "profiles/2022-10-26-19-09-01_notag_get_t4_c64_R10000_d1m/wrk2.txt")
\`\`\`

[cpu heatmap & flame graph](profiles/2022-10-26-19-09-01_notag_get_t4_c64_R10000_d1m/cpu.html)
![image](profiles/2022-10-26-19-09-01_notag_get_t4_c64_R10000_d1m/cpu.png)

[alloc heatmap & flame graph](profiles/2022-10-26-19-09-01_notag_get_t4_c64_R10000_d1m/alloc.html)
![image](profiles/2022-10-26-19-09-01_notag_get_t4_c64_R10000_d1m/alloc.png)

[lock flame graph](profiles/2022-10-26-19-09-01_notag_get_t4_c64_R10000_d1m/lock.html)
![image](profiles/2022-10-26-19-09-01_notag_get_t4_c64_R10000_d1m/lock.png)

Для GET с репликами:
wrk2 output:
\`\`\`
$(cat "profiles/2022-10-26-19-14-12_ack2from3_get_t4_c64_R10000_d1m/wrk2.txt")
\`\`\`

[cpu heatmap & flame graph](profiles/2022-10-26-19-14-12_ack2from3_get_t4_c64_R10000_d1m/cpu.html)
![image](profiles/2022-10-26-19-14-12_ack2from3_get_t4_c64_R10000_d1m/cpu.png)

[alloc heatmap & flame graph](profiles/2022-10-26-19-14-12_ack2from3_get_t4_c64_R10000_d1m/alloc.html)
![image](profiles/2022-10-26-19-14-12_ack2from3_get_t4_c64_R10000_d1m/alloc.png)

[lock flame graph](profiles/2022-10-26-19-14-12_ack2from3_get_t4_c64_R10000_d1m/lock.html)
![image](profiles/2022-10-26-19-14-12_ack2from3_get_t4_c64_R10000_d1m/lock.png)

# Выводы
* Благодаря асинхронной обработке реплик, мы несильно теряем в производительности при работе с ними.
* Большая часть времени (70%) уходит на взаимодействие нод. Чтобы его уменьшить необходимо использовать другой
протокол для общения между нодами, HTTP слишком долго.
* Бонус неочевидный из профилирования -- повышение отказоустойчивости.
* На alloc и lock сильно заметны логи, однако на мой взгляд нет смысла
пускать в прод базу данных без логов, поэтому отключать я их не стал.
EOF
