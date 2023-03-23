#!/usr/bin/env bash
# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

set -x

if [[ -z "$PMS_SDK_SERVER_CONFIG_PORT" ]]; then
   export PMS_SDK_SERVER_CONFIG_PORT=14302
fi

if [[ -z "$PMS_SDK_SERVER_CONFIG_SECURE_PORT" ]]; then
   export PMS_SDK_SERVER_CONFIG_SECURE_PORT=14301
fi

if [[ -z "$SERVER_ADMIN_HTTPS_PORT" ]]; then
   export SERVER_ADMIN_HTTPS_PORT=7072
fi

if [[ -z "$SERVER_APP_HTTPS_PORT" ]]; then
   export SERVER_APP_HTTPS_PORT=7172
fi

if [[ -z "$SERVER_APP_HTTP_PORT" ]]; then
   export SERVER_APP_HTTP_PORT=14057
fi

if [[ -z "$STO_STEP_CONFIG_DEFAULT_TAG" ]]; then
   export STO_STEP_CONFIG_DEFAULT_TAG="latest"
fi