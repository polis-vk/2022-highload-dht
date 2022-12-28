#!/bin/sh
PROFILE_DIR=/Users/mk17ru/async-profiler/profiler.sh

echo "$1"

mkdir -m 777 ~/stage3/$1

echo "Start"

$PROFILE_DIR -f ~/stage3/$1/$1.jfr start -e cpu,alloc,lock ServerImpl

wrk2 -t 6 -c 64 -d 60s -R 50000 -s requests-wrk/put-requests.lua --latency http://localhost:42342 > ~/stage3/$1/$1.txt

$PROFILE_DIR stop ServerImpl

java -cp build/converter.jar jfr2heat ~/stage3/$1/$1.jfr ~/stage3/$1/$1-cpu.html

java -cp build/converter.jar jfr2heat --alloc ~/stage3/$1/$1.jfr ~/stage3/$1/$1-alloc.html

java -cp build/converter.jar jfr2heat --lock ~/stage3/$1/$1.jfr ~/stage3/$1/$1-lock.html






