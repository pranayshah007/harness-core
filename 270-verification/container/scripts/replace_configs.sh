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
    export CONFIG_VALUE; export CONFIG_KEY; yq -i '.env(CONFIG_KEY)"=env(CONFIG_VALUE)' "$CONFIG_FILE"
  fi
}

yq -i 'del(.server.adminConnectors)' /opt/harness/verification-config.yml
yq -i 'del(.server.applicationConnectors[0])' /opt/harness/verification-config.yml

if [[ "" != "$LOGGING_LEVEL" ]]; then
  export LOGGING_LEVEL; yq -i '.logging.level=env(LOGGING_LEVEL)' /opt/harness/verification-config.yml
fi

if [[ "" != "$VERIFICATION_PORT" ]]; then
  export VERIFICATION_PORT; yq -i '.server.applicationConnectors[0].port=env(VERIFICATION_PORT)' /opt/harness/verification-config.yml
else
  yq -i '.server.applicationConnectors[0].port=7070' /opt/harness/verification-config.yml
fi

if [[ "" != "$MONGO_URI" ]]; then
  export MONGO_URI; yq -i '.mongo.uri=env(MONGO_URI)' /opt/harness/verification-config.yml
fi

if [[ "" != "$MONGO_SSL_CONFIG" ]]; then
  export MONGO_SSL_CONFIG; yq -i '.mongo.mongoSSLConfig.mongoSSLEnabled=env(MONGO_SSL_CONFIG)' /opt/harness/verification-config.yml
fi

if [[ "" != "$MONGO_SSL_CA_TRUST_STORE_PATH" ]]; then
  export MONGO_SSL_CA_TRUST_STORE_PATH; yq -i '.mongo.mongoSSLConfig.mongoTrustStorePath=env(MONGO_SSL_CA_TRUST_STORE_PATH)' /opt/harness/verification-config.yml
fi

if [[ "" != "$MONGO_SSL_CA_TRUST_STORE_PASSWORD" ]]; then
  export MONGO_SSL_CA_TRUST_STORE_PASSWORD; yq -i '.mongo.mongoSSLConfig.mongoTrustStorePassword=env(MONGO_SSL_CA_TRUST_STORE_PASSWORD)' /opt/harness/verification-config.yml
fi

if [[ "" != "$MANAGER_URL" ]]; then
  export MANAGER_URL; yq -i '.managerUrl=env(MANAGER_URL)' /opt/harness/verification-config.yml
fi

  yq -i '.server.requestLog.appenders[0].type="console"' /opt/harness/verification-config.yml
  yq -i '.server.requestLog.appenders[0].threshold="TRACE"' /opt/harness/verification-config.yml
  yq -i '.server.requestLog.appenders[0].target="STDOUT"' /opt/harness/verification-config.yml

if [[ "$STACK_DRIVER_LOGGING_ENABLED" == "true" ]]; then
  yq -i 'del(.logging.appenders[2])' /opt/harness/verification-config.yml
  yq -i 'del(.logging.appenders[0])' /opt/harness/verification-config.yml
  yq -i '.logging.appenders[0].stackdriverLogEnabled=true' /opt/harness/verification-config.yml
else
  if [[ "$ROLLING_FILE_LOGGING_ENABLED" == "true" ]]; then
    yq -i 'del(.logging.appenders[1])' /opt/harness/verification-config.yml
    yq -i '.logging.appenders[1].currentLogFilename="/opt/harness/logs/verification.log"' /opt/harness/verification-config.yml
    yq -i '.logging.appenders[1].archivedLogFilenamePattern="/opt/harness/logs/verification.%d.%i.log"' /opt/harness/verification-config.yml
  else
    yq -i 'del(.logging.appenders[2])' /opt/harness/verification-config.yml
    yq -i 'del(.logging.appenders[1])' /opt/harness/verification-config.yml
  fi
fi

if [[ "" != "$DATA_STORE" ]]; then
  export DATA_STORE; yq -i '.dataStorageMode=env(DATA_STORE)' /opt/harness/verification-config.yml
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
