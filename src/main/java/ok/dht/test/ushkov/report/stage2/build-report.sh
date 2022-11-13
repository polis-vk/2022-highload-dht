#!/bin/bash

cat <<EOF > "report.md"
# Отчет

$(cat tpl/intro.md)

# Профилирование

Результаты профилирования на старой реализации.
Заголовок в формате \`<Метод> t=<количество потоков wrk2> с=<количество соединений wrk2> R=<RPS для wrk2> d=<время профилирования>\`.

## PUT t=4 c=64 R=70000 d=1m
wrk2 output:
\`\`\`
$(cat "profiles/2022-10-04-10-27-04_old_put_t4_c64_R70000_d1m/wrk2.txt")
\`\`\`

[cpu heatmap & flame graph](profiles/2022-10-04-10-27-04_old_put_t4_c64_R70000_d1m/cpu.html)
![image](profiles/2022-10-04-10-27-04_old_put_t4_c64_R70000_d1m/cpu.png)

[alloc heatmap & flame graph](profiles/2022-10-04-10-27-04_old_put_t4_c64_R70000_d1m/alloc.html)
![image](profiles/2022-10-04-10-27-04_old_put_t4_c64_R70000_d1m/alloc.png)

[lock flame graph](profiles/2022-10-04-10-27-04_old_put_t4_c64_R70000_d1m/lock.html)
![image](profiles/2022-10-04-10-27-04_old_put_t4_c64_R70000_d1m/lock.png)

## GET t=4 c=64 R=70000 d=1m
wrk2 output:
\`\`\`
$(cat "profiles/2022-10-04-10-42-45_old_get_t4_c64_R70000_d1m/wrk2.txt")
\`\`\`

[cpu heatmap & flame graph](profiles/2022-10-04-10-42-45_old_get_t4_c64_R70000_d1m/cpu.html)
![image](profiles/2022-10-04-10-42-45_old_get_t4_c64_R70000_d1m/cpu.png)

[alloc heatmap & flame graph](profiles/2022-10-04-10-42-45_old_get_t4_c64_R70000_d1m/alloc.html)
![image](profiles/2022-10-04-10-42-45_old_get_t4_c64_R70000_d1m/alloc.png)

[lock flame graph](profiles/2022-10-04-10-42-45_old_get_t4_c64_R70000_d1m/lock.html)
![image](profiles/2022-10-04-10-42-45_old_get_t4_c64_R70000_d1m/lock.png)

Далее следуют результаты профилорвания.
Заголовок в формате \`<Очередь> <Метод> t=<количество потоков wrk2> с=<количество соединений wrk2> R=<RPS для wrk2> d=<время профилирования>\`.

## Queue PUT t=4 c=64 R=70000 d=1m
wrk2 output:
\`\`\`
$(cat "profiles/2022-10-04-10-24-46_new_queue_put_t4_c64_R70000_d1m/wrk2.txt")
\`\`\`

[cpu heatmap & flame graph](profiles/2022-10-04-10-24-46_new_queue_put_t4_c64_R70000_d1m/cpu.html)
![image](profiles/2022-10-04-10-24-46_new_queue_put_t4_c64_R70000_d1m/cpu.png)

[alloc heatmap & flame graph](profiles/2022-10-04-10-24-46_new_queue_put_t4_c64_R70000_d1m/alloc.html)
![image](profiles/2022-10-04-10-24-46_new_queue_put_t4_c64_R70000_d1m/alloc.png)

[lock flame graph](profiles/2022-10-04-10-24-46_new_queue_put_t4_c64_R70000_d1m/lock.html)
![image](profiles/2022-10-04-10-24-46_new_queue_put_t4_c64_R70000_d1m/lock.png)

## Stack PUT t=4 c=64 R=70000 d=1m
wrk2 output:
\`\`\`
$(cat "profiles/2022-10-04-10-29-41_new_stack_put_t4_c64_R70000_d1m/wrk2.txt")
\`\`\`

[cpu heatmap & flame graph](profiles/2022-10-04-10-29-41_new_stack_put_t4_c64_R70000_d1m/cpu.html)
![image](profiles/2022-10-04-10-29-41_new_stack_put_t4_c64_R70000_d1m/cpu.png)

[alloc heatmap & flame graph](profiles/2022-10-04-10-29-41_new_stack_put_t4_c64_R70000_d1m/alloc.html)
![image](profiles/2022-10-04-10-29-41_new_stack_put_t4_c64_R70000_d1m/alloc.png)

[lock flame graph](profiles/2022-10-04-10-29-41_new_stack_put_t4_c64_R70000_d1m/lock.html)
![image](profiles/2022-10-04-10-29-41_new_stack_put_t4_c64_R70000_d1m/lock.png)

## Queue GET t=4 c=64 R=70000 d=1m
wrk2 output:
\`\`\`
$(cat "profiles/2022-10-04-10-46-51_new_queue_get_t4_c64_R70000_d1m/wrk2.txt")
\`\`\`

[cpu heatmap & flame graph](profiles/2022-10-04-10-46-51_new_queue_get_t4_c64_R70000_d1m/cpu.html)
![image](profiles/2022-10-04-10-46-51_new_queue_get_t4_c64_R70000_d1m/cpu.png)

[alloc heatmap & flame graph](profiles/2022-10-04-10-46-51_new_queue_get_t4_c64_R70000_d1m/alloc.html)
![image](profiles/2022-10-04-10-46-51_new_queue_get_t4_c64_R70000_d1m/alloc.png)

[lock flame graph](profiles/2022-10-04-10-46-51_new_queue_get_t4_c64_R70000_d1m/lock.html)
![image](profiles/2022-10-04-10-46-51_new_queue_get_t4_c64_R70000_d1m/lock.png)


## Stack GET t=4 c=64 R=70000 d=1m
wrk2 output:
\`\`\`
$(cat "profiles/2022-10-04-10-39-28_new_stack_get_t4_c64_R70000_d1m/wrk2.txt")
\`\`\`

[cpu heatmap & flame graph](profiles/2022-10-04-10-39-28_new_stack_get_t4_c64_R70000_d1m/cpu.html)
![image](profiles/2022-10-04-10-39-28_new_stack_get_t4_c64_R70000_d1m/cpu.png)

[alloc heatmap & flame graph](profiles/2022-10-04-10-39-28_new_stack_get_t4_c64_R70000_d1m/alloc.html)
![image](profiles/2022-10-04-10-39-28_new_stack_get_t4_c64_R70000_d1m/alloc.png)

[lock flame graph](profiles/2022-10-04-10-39-28_new_stack_get_t4_c64_R70000_d1m/lock.html)
![image](profiles/2022-10-04-10-39-28_new_stack_get_t4_c64_R70000_d1m/lock.png)

$(cat "tpl/conclusion.md")
EOF
