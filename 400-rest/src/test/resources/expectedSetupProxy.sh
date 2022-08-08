#!/bin/bash
# Copyright 2020 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

set -e
set -o pipefail

echo
echo "** This script will generate a proxy configuration for the Harness delegate **"
echo

if [[ -e proxy.config ]]; then
  echo "A proxy.config file already exists. Overwrite? (Y/n):"
  read -n1 overwrite
  if [[ $overwrite == "n" || $overwrite == "N" ]]; then
    echo
    echo Exiting
    echo
    exit 0
  fi
fi

echo "Enter proxy (https://example.company.com:9091):"
read proxy

if [[ $proxy != "http://"* && $proxy != "https://"* ]]; then
  echo
  echo "ERROR: Invalid proxy URL. Must be of the form http[s]://<host>[:port]"
  echo
  exit 1
fi

scheme=$(echo $proxy | cut -d ':' -f 1)
host_and_port=$(echo $proxy | cut -d '/' -f 3)
host=$(echo $host_and_port | cut -d ':' -f 1)
if [[ $host_and_port == *:* ]]; then
  port=$(echo $host_and_port | cut -d ':' -f 2)
elif [[	$scheme == https ]]; then
  port=443
else
  port=80
fi

echo "Enter username if required (optional):"
read user

echo "Enter password (optional):"
read -s password
echo

if [[ $user != "" && $password != "" ]]; then
  password_enc=$(echo $password | openssl enc -a -des-ecb -K 4143434f554e)
elif [[ $user != "" && $password == "" ]]; then
  echo ERROR: Username entered without password
  echo
  exit 1
elif [[ $user == "" && $password != "" ]]; then
  echo ERROR: Password entered without username
  echo
  exit 1
fi

echo "Enter a comma separated list of suffixes for which proxy is not required. Do not use leading wildcards. (.company.com,specifichost) (optional):"
read no_proxy

echo "Bypass proxy settings to reach Harness manager? (y/N):"
read -n1 bypass
if [[ $bypass == "y" || $bypass == "Y" ]]; then
  proxy_manager=false
else
  proxy_manager=true
fi

echo
echo "PROXY_HOST='$host'" > proxy.config
echo "PROXY_PORT='$port'" >> proxy.config
echo "PROXY_SCHEME='$scheme'" >> proxy.config
echo "PROXY_USER='$user'" >> proxy.config
echo "PROXY_PASSWORD_ENC='$password_enc'" >> proxy.config
echo "NO_PROXY='$no_proxy'" >> proxy.config
echo "PROXY_MANAGER='$proxy_manager'" >> proxy.config

echo
echo "Proxy configured - proxy.config generated:"
echo

cat proxy.config

echo
