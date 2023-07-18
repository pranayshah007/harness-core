#!/bin/bash -e
# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

if [[ $PRECHECK_CONN == "true" || $PRECHECK_CONN == "" ]]; then
  set +e
  command="curl --connect-timeout 10 --write-out %{http_code} $MANAGER_PROXY_CURL -s $MANAGER_HOST_AND_PORT/api/account/$ACCOUNT_ID/status  --output /dev/null"
  echo "Checking connection to Harness manager..."
  echo $command
  status=$(eval "$command")
  echo "HTTP Status Code: $status, Curl exit code  $?"
  [[ $status == 000 ]] && echo "Connectivity test failed. Please check network connection from your delegate to Harness manager." && exit 1
  [[ $status == 401 ]] && echo "Account ID: $ACCOUNT_ID not found. Stop launching delegate." && exit 2
  # other errors
  [[ $status -ge 400 ]] && echo "Connection check to Harness manager failed. Stop launching delegate." && exit 3
fi
