#!/bin/bash
set -euo pipefail

ASYNC_PROFILER_DIR="/Users/udav318/ITMO/7-term/hl/async-profiler"
VANILLA_CONVERTER_DIR="/Users/udav318/ITMO/7-term/hl/converters/converter.jar"

DATE=$(date '+%Y-%m-%d-%H-%M-%S')
DIR="profiles/${DATE}_${SCRIPT%.lua}_t${t}_c${c}_R${R}_d${d}"
mkdir -p "$DIR"

function async-profiler-start() {
    "$ASYNC_PROFILER_DIR/profiler.sh" -f "$DIR/profile.jfr" -e "cpu,alloc,lock" start Main
}

function async-profiler-stop() {
    "$ASYNC_PROFILER_DIR/profiler.sh" stop Main > /dev/null
}

function converter() {
    java -cp "$ASYNC_PROFILER_DIR/build/converter.jar" "$@" > /dev/null
}

# stop profiler if it wasn't finalized
async-profiler-stop || :

echo "=== Start async-profiler"
async-profiler-start

echo "=== Start wrk2"
wrk2 -t "$t" -c "$c" -R "$R" -d "$d" -s "scripts/$SCRIPT" http://localhost:8000 > "$DIR/wrk2.txt"

echo "=== Stop async-profiler"
async-profiler-stop

echo "=== Convert to html"
converter "jfr2heat" "$DIR/profile.jfr" "$DIR/cpu.html"
converter "jfr2heat" --alloc "$DIR/profile.jfr" "$DIR/alloc.html"
# converter "jfr2flame" --lock "$DIR/profile.jfr" "$DIR/lock.html"

# use vanilla converter instead of fork, as it has bug
java -cp "$VANILLA_CONVERTER_DIR" "jfr2flame" --lock "$DIR/profile.jfr" "$DIR/lock.html" > /dev/null
