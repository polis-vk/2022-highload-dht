#!/usr/bin/env bash

LGREEN='\033[1;32m'
YELLOW='\033[1;33m'
LCYAN='\033[1;36m'
NC='\033[0m'

rs=(10000 15000 20000 25000 27500 30000)

for r in "${rs[@]}"; do
  echo -e "${LCYAN}starting -R$r${NC}";
  ~/packages/wrk2/wrk -t1 -c1 -d1m -R"$r" -s puts.lua -L http://localhost:2022 > data/puts-"$r".txt
  echo -e "${LGREEN}finished -R$r${NC}";
  echo -e "${YELLOW}sleeping...${NC}";
  sleep 15;
  echo "";
done
