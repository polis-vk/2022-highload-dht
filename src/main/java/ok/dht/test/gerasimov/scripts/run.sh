#!/bin/bash
#paths to scripts
lua_put_path="put.lua"
lua_get_path="get.lua"
path_to_async_profiler="/Users/michael/Desktop/async-profiler/async-profiler"

#server configuration
server_host="localhost"
server_port=25565
server_name="Main"

#wrk2 configuration
wrk2_duration=30
wrk2_threads=32
wrk2_connections=64

function wrk2_start() {
  local rate=$1
  local type=$2
  local type_request=$3
  local file_name="wrk2_${type_request}_rate_${rate}.txt"
  local file_path="$PWD/$file_name"
  wrk2 -d $wrk2_duration -t $wrk2_threads -c $wrk2_connections -R "$rate" -s "$type" -L "http://${server_host}:${server_port}" > "$file_path"
}

function async_profiler_start() {
  local file_name=$1
  local file_path="$PWD/$file_name"
  local path=$PWD
  cd $path_to_async_profiler || exit
  source "./profiler.sh" -e cpu,alloc,lock --alloc 512 -f "$file_path" start $server_name
  echo "async-profiler started"
  cd "$path" || exit
}

function async_profiler_stop() {
  local file_name=$1
  local file_path="$PWD/$file_name"
  local path=$PWD
  cd $path_to_async_profiler || exit
  source "./profiler.sh" -f "$file_path" stop "$server_name"
  echo "async-profiler stopped"
  cd "$path" || exit
}

echo "      ___                       ___           ___                  "
echo "     /  /\          ___        /  /\         /  /\          ___    "
echo "    /  /:/_        /  /\      /  /::\       /  /::\        /  /\   "
echo "   /  /:/ /\      /  /:/     /  /:/\:\     /  /:/\:\      /  /:/   "
echo "  /  /:/ /::\    /  /:/     /  /:/~/::\   /  /:/~/:/     /  /:/    "
echo " /__/:/ /:/\:\  /  /::\    /__/:/ /:/\:\ /__/:/ /:/___  /  /::\    "
echo " \  \:\/:/~/:/ /__/:/\:\   \  \:\/:/__\/ \  \:\/:::::/ /__/:/\:\   "
echo "  \  \::/ /:/  \__\/  \:\   \  \::/       \  \::/~~~~  \__\/  \:\  "
echo "   \__\/ /:/        \  \:\   \  \:\        \  \:\           \  \:\ "
echo "     /__/:/          \__\/    \  \:\        \  \:\           \__\/ "
echo "     \__\/                     \__\/         \__\/                 "
echo "                                                                   "

#rates=(500 1000 2000)
#rates=(5000)
rates=(10000)

for rate in "${rates[@]}"; do
  type_request="put"
  file_name="async-profiler_${type_request}_rate_${rate}.jfr"
  async_profiler_start "$file_name"
  wrk2_start "$rate" $lua_put_path $type_request
  async_profiler_stop "$file_name"
done

for rate in "${rates[@]}"; do
  type_request="get"
  file_name="async-profiler_${type_request}_rate_${rate}.jfr"
  async_profiler_start "$file_name"
  wrk2_start "$rate" $lua_get_path $type_request
  async_profiler_stop "$file_name"
done
