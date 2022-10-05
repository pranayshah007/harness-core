#!/bin/bash
source ./remote-debug.sh
echo  "Service name:" "$1"
echo "Config file path:" "$2"

main "$1" "$2"