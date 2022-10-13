$PRF -f put-01.jfr start Service
sleep 1
$WRK --script put.lua
sleep 1
$WRK --script put.lua
sleep 1
$PRF_E
$PRF -f get-01.jfr start Service
sleep 1
$WRK --script get.lua
sleep 1
$WRK --script get.lua
sleep 1
$WRK --script get.lua
sleep 1
$PRF_E


URL=$1
WRK="wrk $URL --connections 64\
    --duration 30s\
    --threads 8\
    --latency\
    --rate 30k\
    --timeout 1s"
PRF="async-profiler --chunktime 1s -i 5ms -e cpu,alloc"
PRF_E='async-profiler stop Service'

$PRF -f put-02.jfr start Service
for i in 1 2 3
do
  sleep 1
  $WRK --script put.lua -- 10 32 > "put-01-$i.out"
done
sleep 0.5
$PRF_E

$PRF -f get-03.jfr start Service
for i in 1 2 3
do
  sleep 1
  $WRK --script get.lua -- 10 > "get-01-$i.out"
done
sleep 0.5
$PRF_E

CONV="java -cp $HOME/dev/async-profiler/build/converter.jar jfr2heat"
$CONV --alloc put-01.jfr put-01-alloc.html
$CONV         put-01.jfr put-01-cpu.html
$CONV --alloc get-01.jfr get-01-alloc.html
$CONV         get-01.jfr get-01-cpu.html