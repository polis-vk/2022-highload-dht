#!/bin/bash

~/y2019/highload/wrk2/wrk -d $1 -t 6 -c 64 -R $2 -s put-request.lua http://localhost:12345
