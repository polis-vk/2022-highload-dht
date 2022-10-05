URL=http://localhost:19234
WRK="wrk $URL --connections 1\
    --duration 5s\
    --threads 1\
    --latency\
    --timeout 1s"
PRF="async-profiler --chunktime 1s -e cpu,alloc"
PRF_E='async-profiler stop Service'
$PRF -f put-01.jfr start Service
sleep 0.5
$WRK --rate 30k --script put.lua
sleep 0.5
$WRK --rate 15k --script put.lua
sleep 0.5
$WRK --rate 14k --script put.lua
sleep 0.5
$PRF_E
$PRF -f get-01.jfr start Service
sleep 0.5
$WRK --rate 100k --script get.lua
sleep 0.5
$WRK --rate 50k --script get.lua
sleep 0.5
$WRK --rate 25k --script get.lua
sleep 0.5
$WRK --rate 20k --script get.lua
sleep 0.5
$WRK --rate 17k --script get.lua
sleep 0.5
$WRK --rate 15k --script get.lua
sleep 0.5
$WRK --rate 14k --script get.lua
sleep 0.5
$PRF_E