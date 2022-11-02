#!/bin/bash

NODE1=19234
NODE2=19235

PID1=$(lsof -i :$NODE1 | grep :$NODE1 | awk '{print $2}')

PID2=$(lsof -i :$NODE2 | grep :$NODE2 | awk '{print $2}')

echo "PID1 = $PID1"
echo "PID2 = $PID2"

t=1
c=1
R=10000
m=get

PROFILER=~/Documents/uni-data/highload/async-profiler
STAGE=~/Documents/uni-data/highload/2022-highload-dht/src/main/java/ok/dht/test/vihnin/resources/report/stage5

while getopts ":t:c:R:m:" opt; do
  case $opt in
    t) t="$OPTARG"
    ;;
    c) c="$OPTARG"
    ;;
    R) R="$OPTARG"
    ;;
    m) m="$OPTARG"
    ;;
    \?) echo "Invalid option -$OPTARG" >&2
    exit 1
    ;;
  esac

  case $OPTARG in
    -*) echo "Option $opt needs a valid argument"
    exit 1
    ;;
  esac
done

echo "Input: t=$t, c=$c, R=$R, method=$m"
echo ""

name=t${t}_c${c}_m${m}_R${R}

JFR1=$STAGE/jfrs/results_"$name"_$NODE1.jfr
JFR2=$STAGE/jfrs/results_"$name"_$NODE2.jfr

echo "Start profiler for $NODE1"

$PROFILER/profiler.sh -f "$JFR1" -e cpu,alloc,lock start "$PID1"

echo "Start profiler for $NODE2"

$PROFILER/profiler.sh -f "$JFR2" -e cpu,alloc,lock start "$PID2"

package=profile_results_$name

{
  mkdir $STAGE/"$package"
} || {
  package=$package"["$(date +"%T")"]"
  mkdir $STAGE/"$package"
}

package=$STAGE/$package

wrk -L -d 60 -t "$t" -c "$c" -R "$R" http://localhost:$NODE1 -s $STAGE/"$m".lua > "$package"/wrk_res.txt

echo ""

echo "Wrk finished after $t seconds"
echo ""


echo "Stop profiler for $NODE1"

$PROFILER/profiler.sh -f "$JFR1" stop "$PID1"

echo "Stop profiler for $NODE2"

$PROFILER/profiler.sh -f "$JFR2" stop "$PID2"

java -cp $PROFILER/build/converter.jar jfr2heat "$JFR1" --lock  "$package"/lock_$NODE1.html > /dev/null
java -cp $PROFILER/build/converter.jar jfr2heat "$JFR1" --cpu "$package"/cpu_$NODE1.html > /dev/null
java -cp $PROFILER/build/converter.jar jfr2heat "$JFR1" --alloc  "$package"/alloc_$NODE1.html > /dev/null

java -cp $PROFILER/build/converter.jar jfr2heat "$JFR2" --lock  "$package"/lock_$NODE2.html > /dev/null
java -cp $PROFILER/build/converter.jar jfr2heat "$JFR2" --cpu "$package"/cpu_$NODE2.html > /dev/null
java -cp $PROFILER/build/converter.jar jfr2heat "$JFR2" --alloc  "$package"/alloc_$NODE2.html > /dev/null


cp "$JFR1" "$package"/results_"$name"_$NODE1.jfr
cp "$JFR2" "$package"/results_"$name"_$NODE2.jfr


echo "All htmls was stored to:"
echo "$package"


