#!/usr/bin/env bash
# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

CONFIG_FILE=/opt/harness/config.yml

if [[ "$STACK_DRIVER_LOGGING_ENABLED" == "true" ]]; then
  yq -i 'del(.logging.appenders | select(.type == "console"))' $CONFIG_FILE
  yq -i '(.logging.appenders | select(.type == "gke-console") | .stackdriverLogEnabled) = "true"' $CONFIG_FILE
else
  yq -i 'del(.logging.appenders | select(.type == "gke-console"))' $CONFIG_FILE
fi

# Remove the TLS connector (as ingress terminates TLS)
yq -i 'del(.connectors[0])' $CONFIG_FILE


if [[ "" != "$SERVER_PORT" ]]; then
  yq -i '.server.applicationConnectors[0].port="$SERVER_PORT"' $CONFIG_FILE
else
  yq -i '.server.applicationConnectors[0].port="7100"' $CONFIG_FILE
fi

# The config for communication with the other services
if [[ "" != "$CD_CLIENT_BASEURL" ]]; then
  yq -i '.cdServiceClientConfig.baseUrl="$CD_CLIENT_BASEURL"' $CONFIG_FILE
fi

if [[ "" != "$CI_CLIENT_BASEURL" ]]; then
  yq -i '.ciServiceClientConfig.baseUrl="$CI_CLIENT_BASEURL"' $CONFIG_FILE
fi

if [[ "" != "$NG_MANAGER_CLIENT_BASEURL" ]]; then
  yq -i '.ngManagerClientConfig.baseUrl="$NG_MANAGER_CLIENT_BASEURL"' $CONFIG_FILE
fi


# Secrets
if [[ "" != "$NEXT_GEN_MANAGER_SECRET" ]]; then
  yq -i '.secrets.ngManagerServiceSecret="$NEXT_GEN_MANAGER_SECRET"' $CONFIG_FILE
fi

if [[ "" != "$PIPELINE_SERVICE_SECRET" ]]; then
  yq -i '.secrets.pipelineServiceSecret="$PIPELINE_SERVICE_SECRET"' $CONFIG_FILE
fi

if [[ "" != "$JWT_AUTH_SECRET" ]]; then
  yq -i '.secrets.jwtAuthSecret="$JWT_AUTH_SECRET"' $CONFIG_FILE
fi

if [[ "" != "$JWT_IDENTITY_SERVICE_SECRET" ]]; then
  yq -i '.secrets.jwtIdentityServiceSecret="$JWT_IDENTITY_SERVICE_SECRET"' $CONFIG_FILE
fi

if [[ "" != "$ALLOWED_ORIGINS" ]]; then
  yq -i 'del(.allowedOrigins)' $CONFIG_FILE
  yq -i '.allowedOrigins="$ALLOWED_ORIGINS"' $CONFIG_FILE
fi
