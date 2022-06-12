#!/usr/bin/env bash
# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

CONFIG_FILE=/opt/harness/verification-config.yml

replace_key_value () {
  CONFIG_KEY="$1";
  CONFIG_VALUE="$2";
  if [[ "" != "$CONFIG_VALUE" ]]; then
    yq write -i "$CONFIG_FILE" "$CONFIG_KEY" "$CONFIG_VALUE"
  fi
}

yq delete -i /opt/harness/verification-config.yml server.adminConnectors
yq delete -i /opt/harness/verification-config.yml server.applicationConnectors[0]

if [[ "" != "$LOGGING_LEVEL" ]]; then
  yq write -i /opt/harness/verification-config.yml logging.level "$LOGGING_LEVEL"
fi

if [[ "" != "$VERIFICATION_PORT" ]]; then
  yq write -i /opt/harness/verification-config.yml server.applicationConnectors[0].port "$VERIFICATION_PORT"
else
  yq write -i /opt/harness/verification-config.yml server.applicationConnectors[0].port "7070"
fi

if [[ "" != "$MONGO_URI" ]]; then
  yq write -i /opt/harness/verification-config.yml mongo.uri "${MONGO_URI//\\&/&}"
fi

if [[ "" != "$MONGO_SSL_CONFIG" ]]; then
  yq write -i /opt/harness/verification-config.yml mongo.mongoSSLConfig.mongoSSLEnabled "$MONGO_SSL_CONFIG"
fi

if [[ "" != "$MONGO_SSL_CA_TRUST_STORE_PATH" ]]; then
  yq write -i /opt/harness/verification-config.yml mongo.mongoSSLConfig.mongoTrustStorePath "$MONGO_SSL_CA_TRUST_STORE_PATH"
fi

if [[ "" != "$MONGO_SSL_CA_TRUST_STORE_PASSWORD" ]]; then
  yq write -i /opt/harness/verification-config.yml mongo.mongoSSLConfig.mongoTrustStorePassword "$MONGO_SSL_CA_TRUST_STORE_PASSWORD"
fi

if [[ "" != "$MANAGER_URL" ]]; then
  yq write -i /opt/harness/verification-config.yml managerUrl "$MANAGER_URL"
fi

  yq write -i /opt/harness/verification-config.yml server.requestLog.appenders[0].type "console"
  yq write -i /opt/harness/verification-config.yml server.requestLog.appenders[0].threshold "TRACE"
  yq write -i /opt/harness/verification-config.yml server.requestLog.appenders[0].target "STDOUT"

if [[ "$STACK_DRIVER_LOGGING_ENABLED" == "true" ]]; then
  yq delete -i /opt/harness/verification-config.yml logging.appenders[2]
  yq delete -i /opt/harness/verification-config.yml logging.appenders[0]
  yq write -i /opt/harness/verification-config.yml logging.appenders[0].stackdriverLogEnabled "true"
else
  if [[ "$ROLLING_FILE_LOGGING_ENABLED" == "true" ]]; then
    yq delete -i /opt/harness/verification-config.yml logging.appenders[1]
    yq write -i /opt/harness/verification-config.yml logging.appenders[1].currentLogFilename "/opt/harness/logs/verification.log"
    yq write -i /opt/harness/verification-config.yml logging.appenders[1].archivedLogFilenamePattern "/opt/harness/logs/verification.%d.%i.log"
  else
    yq delete -i /opt/harness/verification-config.yml logging.appenders[2]
    yq delete -i /opt/harness/verification-config.yml logging.appenders[1]
  fi
fi

if [[ "" != "$DATA_STORE" ]]; then
  yq write -i /opt/harness/verification-config.yml dataStorageMode "$DATA_STORE"
fi

replace_key_value cfClientConfig.apiKey "$CF_CLIENT_API_KEY"
replace_key_value cfClientConfig.configUrl "$CF_CLIENT_CONFIG_URL"
replace_key_value cfClientConfig.eventUrl "$CF_CLIENT_EVENT_URL"
replace_key_value cfClientConfig.analyticsEnabled "$CF_CLIENT_ANALYTICS_ENABLED"
replace_key_value cfClientConfig.connectionTimeout "$CF_CLIENT_CONNECTION_TIMEOUT"
replace_key_value cfClientConfig.readTimeout "$CF_CLIENT_READ_TIMEOUT"
replace_key_value cfClientConfig.bufferSize "$CF_CLIENT_BUFFER_SIZE"
replace_key_value featureFlagConfig.featureFlagSystem "$FEATURE_FLAG_SYSTEM"
replace_key_value featureFlagConfig.syncFeaturesToCF "$SYNC_FEATURES_TO_CF"
