#!/bin/bash

cat <<EOF > "report.md"
# Отчет

## GET t=5 c=64 R=5000 d=1m
wrk2 output:
\`\`\`
$(cat "profiles/2022-10-03-10-54-31_get_t5_c64_R5000_d1m/wrk2.txt")
\`\`\`

[cpu heatmap & flame graph](profiles/2022-10-03-10-54-31_get_t5_c64_R5000_d1m/cpu.html)
![image](profiles/2022-10-03-10-54-31_get_t5_c64_R5000_d1m/cpu.png)

[alloc heatmap & flame graph](profiles/2022-10-03-10-54-31_get_t5_c64_R5000_d1m/alloc.html)
![image](profiles/2022-10-03-10-54-31_get_t5_c64_R5000_d1m/alloc.png)

## PUT t=5 c=64 R=5000 d=1m
TODO

$(cat "tpl/conclusion.md")
EOF
