#!/bin/bash

~/y2019/highload/wrk2/wrk -d $1 -t 1 -c 1 -R $2 -s get-request.lua http://localhost:12345
