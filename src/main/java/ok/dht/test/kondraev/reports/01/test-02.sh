URL=http://localhost:19234
WRK="wrk $URL --connections 1\
    --duration 30s\
    --threads 1\
    --latency\
    --rate 15k\
    --timeout 1s"
PRF="async-profiler --chunktime 500ms -i 5ms -e cpu,alloc"
PRF_E='async-profiler stop Service'
$PRF -f put-02.jfr start Service
sleep 0.5
$WRK --script put.lua
sleep 0.5
$WRK --script put.lua
sleep 0.5
$WRK --script put.lua
sleep 0.5
$PRF_E
$PRF -f get-02.jfr start Service
sleep 0.5
$WRK --script get.lua
sleep 0.5
$WRK --script get.lua
sleep 0.5
$PRF_E