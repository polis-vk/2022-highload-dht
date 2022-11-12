rate=$1
type=$2
wrk2 -d 60 -t 1 -c 1 -R "$rate" -s "$type" http://localhost:25565