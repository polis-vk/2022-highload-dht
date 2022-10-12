#!/bin/bash
path_to_async_profiler="/Users/michael/Desktop/async-profiler/async-profiler"

function run_jfr2flame_alloc() {
  local src_name=$1
  local dst_name=$2
  local src_path="$PWD/$src_name"
  local dst_path="$PWD/$dst_name"
  local path=$PWD
  cd $path_to_async_profiler || exit
  java -cp "$path_to_async_profiler/build/converter.jar" jfr2heat --alloc "$src_path" "$dst_path"
  cd "$path" || exit
}

function run_jfr2flame_cpu() {
  local src_name=$1
  local dst_name=$2
  local src_path="$PWD/$src_name"
  local dst_path="$PWD/$dst_name"
  local path=$PWD
  cd $path_to_async_profiler || exit
  java -cp "$path_to_async_profiler/build/converter.jar" jfr2heat "$src_path" "$dst_path"
  cd "$path" || exit
}

function run_jfr2flame_lock() {
  local src_name=$1
  local dst_name=$2
  local src_path="$PWD/$src_name"
  local dst_path="$PWD/$dst_name"
  local path=$PWD
  cd $path_to_async_profiler || exit
  java -cp "$path_to_async_profiler/build/converter.jar" jfr2heat --lock "$src_path" "$dst_path"
  cd "$path" || exit
}

rates=(3000)

for rate in "${rates[@]}"; do
  type_request="put"
  file_name="async-profiler_${type_request}_rate_${rate}.jfr"
  run_jfr2flame_alloc "$file_name" "async-profiler_${type_request}_alloc_rate_${rate}.html"
  run_jfr2flame_cpu "$file_name" "async-profiler_${type_request}_cpu_rate_${rate}.html"
  run_jfr2flame_lock "$file_name" "async-profiler_${type_request}_lock_rate_${rate}.html"
done

for rate in "${rates[@]}"; do
  type_request="get"
  file_name="async-profiler_${type_request}_rate_${rate}.jfr"
  run_jfr2flame_alloc "$file_name" "async-profiler_${type_request}_alloc_rate_${rate}.html"
  run_jfr2flame_cpu "$file_name" "async-profiler_${type_request}_cpu_rate_${rate}.html"
  run_jfr2flame_lock "$file_name" "async-profiler_${type_request}_lock_rate_${rate}.html"
done