#!/bin/bash

java -cp ~/y2019/highload/async-profiler/build/converter.jar jfr2heat tycoon-server.jfr tycoon-server-cpu.html
java -cp ~/y2019/highload/async-profiler/build/converter.jar jfr2heat --alloc tycoon-server.jfr tycoon-server-alloc.html
