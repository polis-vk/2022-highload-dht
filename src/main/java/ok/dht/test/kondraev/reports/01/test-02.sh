URL=$1
WRK="wrk $URL --connections 1\
    --duration 30s\
    --threads 1\
    --latency\
    --timeout 1s"
PRF="async-profiler --chunktime 1s -i 5ms -e cpu,alloc"
PRF_E='async-profiler stop Service'

$PRF -f put-02.jfr start Service
for i in 1 2 3 4
do
  sleep 1
  $WRK --rate 10k --script put.lua -- 10 32 > "put-02-$i.out"
done
sleep 0.5
$PRF_E

$PRF -f get-03.jfr start Service
for i in 1 2 3 4 5
do
  sleep 1
  $WRK --rate 4300 --script get.lua -- 10 > "get-03-4.3k-$i.out"
done
sleep 0.5
$PRF_E

CONV="java -cp $HOME/dev/async-profiler/build/converter.jar jfr2heat"
$CONV --alloc put-02.jfr put-02-alloc.html
$CONV         put-02.jfr put-02-cpu.html
$CONV --alloc get-03.jfr get-03-alloc.html
$CONV         get-03.jfr get-03-cpu.html
