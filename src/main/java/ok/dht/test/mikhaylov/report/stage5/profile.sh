#!/usr/bin/env bash

set -e

if [ $# -ne 3 ]; then
    echo "Usage: profile.sh <put|get> <RPS> <sync|async>"
    exit 1
fi

PORT1=19234
PORT2=19235

METHOD=$1
RPS=$2
CLIENTMODE=$3

# start servers
cd ~/repos/2022-highload-dht/
#./gradlew :run --args="$PORT1" &
PID1=$(lsof -i :$PORT1 | grep LISTEN | awk '{print $2}')
#./gradlew :run --args="$PORT2" &
PID2=$(lsof -i :$PORT2 | grep LISTEN | awk '{print $2}')

# start profiler
cd ~/repos/async-profiler-artyom/
./profiler.sh -f ${PORT1}_"${CLIENTMODE}"_"${METHOD}".jfr -e cpu,alloc,lock start "$PID1" &
./profiler.sh -f ${PORT2}_"${CLIENTMODE}"_"${METHOD}".jfr -e cpu,alloc,lock start "$PID2" &
sleep 5

# start wrk
cd ~/repos/2022-highload-dht/src/main/java/ok/dht/test/mikhaylov/report/stage4/scripts/
wrk2 -t 6 -c 128 -d 60 -R"$RPS" -L -s "$METHOD".lua http://localhost:"$PORT1"

# stop profiler
cd ~/repos/async-profiler-artyom/
./profiler.sh -f ${PORT1}_"${CLIENTMODE}"_"${METHOD}".jfr stop "$PID1"
./profiler.sh -f ${PORT2}_"${CLIENTMODE}"_"${METHOD}".jfr stop "$PID2"

# stop servers
#kill "$PID1"
#kill "$PID2"

# convert jfr to flamegraph
cd ~/repos/async-profiler-artyom/
for port in $PORT1 $PORT2; do
  java -cp build/converter.jar jfr2heat ${port}_"${CLIENTMODE}"_"${METHOD}".jfr -o ${port}_cpu_"${CLIENTMODE}"_"${METHOD}".html &> /dev/null
  java -cp build/converter.jar jfr2heat ${port}_"${CLIENTMODE}"_"${METHOD}".jfr -o ${port}_alloc_"${CLIENTMODE}"_"${METHOD}".html --alloc &> /dev/null
  java -cp build/converter.jar jfr2heat ${port}_"${CLIENTMODE}"_"${METHOD}".jfr -o ${port}_lock_"${CLIENTMODE}"_"${METHOD}".html --lock &> /dev/null
done