#!/bin/bash
cd $(dirname "$0")
HOST=brief-glade.iad02.hubspot-networks.net
rm -v ./out/*
cd ../
rsync -avz $HOST:~/SeatSolver/out/ ./SeatSolver/out/ &>/dev/null
