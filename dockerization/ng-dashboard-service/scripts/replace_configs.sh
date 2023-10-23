#!/usr/bin/env bash
# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

CONFIG_FILE=/opt/harness/config.yml

if [[ "$STACK_DRIVER_LOGGING_ENABLED" == "true" ]]; then
  yq -i 'del(.logging.appenders.[] | select(.type == "console"))' $CONFIG_FILE
  yq -i '(.logging.appenders.[] | select(.type == "gke-console") | .stackdriverLogEnabled) = true' $CONFIG_FILE
else
  yq -i 'del(.logging.appenders.[] | select(.type == "gke-console"))' $CONFIG_FILE
fi

# Remove the TLS ConnectorDisconnectHandler (as ingress terminates TLS)
yq -i 'del(.connectors[0])' $CONFIG_FILE


if [[ "" != "$SERVER_PORT" ]]; then
  export SERVER_PORT; yq -i '.server.applicationConnectors[0].port=env(SERVER_PORT)' $CONFIG_FILE
else
  yq -i '.server.applicationConnectors[0].port=7100' $CONFIG_FILE
fi

# The config for communication with the other services
if [[ "" != "$CD_CLIENT_BASEURL" ]]; then
  export CD_CLIENT_BASEURL; yq -i '.cdServiceClientConfig.baseUrl=env(CD_CLIENT_BASEURL)' $CONFIG_FILE
fi

if [[ "" != "$CI_CLIENT_BASEURL" ]]; then
  export CI_CLIENT_BASEURL; yq -i '.ciServiceClientConfig.baseUrl=env(CI_CLIENT_BASEURL)' $CONFIG_FILE
fi

if [[ "" != "$MANAGER_CLIENT_BASEURL" ]]; then
  export MANAGER_CLIENT_BASEURL; yq -i '.managerClientConfig.baseUrl=env(MANAGER_CLIENT_BASEURL)' $CONFIG_FILE
fi

if [[ "" != "$NG_MANAGER_CLIENT_BASEURL" ]]; then
  export NG_MANAGER_CLIENT_BASEURL; yq -i '.ngManagerClientConfig.baseUrl=env(NG_MANAGER_CLIENT_BASEURL)' $CONFIG_FILE
fi


# Secrets
if [[ "" != "$MANAGER_SECRET" ]]; then
  export MANAGER_SECRET; yq -i '.secrets.managerServiceSecret=env(MANAGER_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$NEXT_GEN_MANAGER_SECRET" ]]; then
  export NEXT_GEN_MANAGER_SECRET; yq -i '.secrets.ngManagerServiceSecret=env(NEXT_GEN_MANAGER_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$PIPELINE_SERVICE_SECRET" ]]; then
  export PIPELINE_SERVICE_SECRET; yq -i '.secrets.pipelineServiceSecret=env(PIPELINE_SERVICE_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$JWT_AUTH_SECRET" ]]; then
  export JWT_AUTH_SECRET; yq -i '.secrets.jwtAuthSecret=env(JWT_AUTH_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$JWT_IDENTITY_SERVICE_SECRET" ]]; then
  export JWT_IDENTITY_SERVICE_SECRET; yq -i '.secrets.jwtIdentityServiceSecret=env(JWT_IDENTITY_SERVICE_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$ALLOWED_ORIGINS" ]]; then
  yq -i 'del(.allowedOrigins)' $CONFIG_FILE
  export ALLOWED_ORIGINS; yq -i '.allowedOrigins=env(ALLOWED_ORIGINS)' $CONFIG_FILE
fi
