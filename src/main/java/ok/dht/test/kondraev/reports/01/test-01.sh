URL=$1
WRK="wrk $URL --connections 1\
    --duration 15s\
    --threads 1\
    --latency\
    --timeout 1s"
PRF="async-profiler --chunktime 1s -e cpu,alloc"
PRF_E='async-profiler stop Service'

$PRF -f put-01.jfr start Service
for RATE in 30k 15k 10k 8500
do
  sleep 1
  $WRK --rate $RATE --script put.lua -- 10 32 | tee "put-01-$RATE.out"
done
sleep 0.5
$PRF_E

$PRF -f get-01.jfr start Service
for RATE in 50k 25k 20k 17k 15k 14k
do
  sleep 1
  $WRK --rate $RATE --script get.lua -- 10 | tee "get-01-$RATE.out"
done
sleep 0.5
$PRF_E

CONV="java -cp $HOME/dev/async-profiler/build/converter.jar jfr2heat"
$CONV --alloc put-01.jfr put-01-alloc.html
$CONV         put-01.jfr put-01-cpu.html
$CONV --alloc get-01.jfr get-01-alloc.html
$CONV         get-01.jfr get-01-cpu.html
$CONV --alloc get-02.jfr get-02-alloc.html
$CONV         get-02.jfr get-02-cpu.html