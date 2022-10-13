#!/bin/bash

~/y2019/highload/async-profiler/profiler.sh -e cpu,alloc -f tycoon-server.jfr --chunktime 1s start Server
