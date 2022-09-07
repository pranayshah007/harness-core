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
    yq -i '.$CONFIG_KEY="$CONFIG_VALUE"' $CONFIG_FILE
  fi
}

yq -i 'del(.server.adminConnectors)' /opt/harness/verification-config.yml
yq -i 'del(.server.applicationConnectors | select(.type == "h2"))' $CONFIG_FILE

if [[ "" != "$LOGGING_LEVEL" ]]; then
  yq -i '.logging.level="$LOGGING_LEVEL"' /opt/harness/verification-config.yml
fi

if [[ "" != "$VERIFICATION_PORT" ]]; then
  yq -i '.server.applicationConnectors[0].port="$VERIFICATION_PORT"' /opt/harness/verification-config.yml
else
  yq -i '.server.applicationConnectors[0].port="7070"' /opt/harness/verification-config.yml
fi

if [[ "" != "$MONGO_URI" ]]; then
  yq -i '.mongo.uri="${MONGO_URI//\\&/&}"' /opt/harness/verification-config.yml
fi

if [[ "" != "$MONGO_SSL_CONFIG" ]]; then
  yq -i '.mongo.mongoSSLConfig.mongoSSLEnabled="$MONGO_SSL_CONFIG"' /opt/harness/verification-config.yml
fi

if [[ "" != "$MONGO_SSL_CA_TRUST_STORE_PATH" ]]; then
  yq -i '.mongo.mongoSSLConfig.mongoTrustStorePath="$MONGO_SSL_CA_TRUST_STORE_PATH"' /opt/harness/verification-config.yml
fi

if [[ "" != "$MONGO_SSL_CA_TRUST_STORE_PASSWORD" ]]; then
  yq -i '.mongo.mongoSSLConfig.mongoTrustStorePassword="$MONGO_SSL_CA_TRUST_STORE_PASSWORD"' /opt/harness/verification-config.yml
fi

if [[ "" != "$MANAGER_URL" ]]; then
  yq -i '.managerUrl="$MANAGER_URL"' /opt/harness/verification-config.yml
fi

  yq -i '.server.requestLog.appenders[0].type="console"' /opt/harness/verification-config.yml
  yq -i '.server.requestLog.appenders[0].threshold="TRACE"' /opt/harness/verification-config.yml
  yq -i '.server.requestLog.appenders[0].target="STDOUT"' /opt/harness/verification-config.yml

if [[ "$STACK_DRIVER_LOGGING_ENABLED" == "true" ]]; then
  yq -i 'del(.logging.appenders | select(.type == "file"))' $CONFIG_FILE
  yq -i 'del(.logging.appenders | select(.type == "console"))' $CONFIG_FILE
  yq -i '(.logging.appenders | select(.type == "gke-console") | .stackdriverLogEnabled) = "true"' $CONFIG_FILE
else
  if [[ "$ROLLING_FILE_LOGGING_ENABLED" == "true" ]]; then
    yq -i 'del(.logging.appenders | select(.type == "gke-console"))' $CONFIG_FILE
    yq -i '(.logging.appenders | select(.type == "file") | .currentLogFilename) = "/opt/harness/logs/verification.log"' $CONFIG_FILE
    yq -i '(.logging.appenders | select(.type == "file") | .archivedLogFilenamePattern) = "/opt/harness/logs/verification.%d.%i.log"' $CONFIG_FILE
  else
    yq -i 'del(.logging.appenders | select(.type == "file"))' $CONFIG_FILE
    yq -i 'del(.logging.appenders | select(.type == "gke-console"))' $CONFIG_FILE
  fi
fi

if [[ "" != "$DATA_STORE" ]]; then
  yq -i '.dataStorageMode="$DATA_STORE"' /opt/harness/verification-config.yml
fi

replace_key_value cfClientConfig.apiKey "$CF_CLIENT_API_KEY"
replace_key_value cfClientConfig.configUrl "$CF_CLIENT_CONFIG_URL"
replace_key_value cfClientConfig.eventUrl "$CF_CLIENT_EVENT_URL"
replace_key_value cfClientConfig.bufferSize "$CF_CLIENT_BUFFER_SIZE"
replace_key_value cfClientConfig.analyticsEnabled "$CF_CLIENT_ANALYTICS_ENABLED"
replace_key_value cfClientConfig.connectionTimeout "$CF_CLIENT_CONNECTION_TIMEOUT"
replace_key_value cfClientConfig.readTimeout "$CF_CLIENT_READ_TIMEOUT"
replace_key_value featureFlagConfig.featureFlagSystem "$FEATURE_FLAG_SYSTEM"
replace_key_value featureFlagConfig.syncFeaturesToCF "$SYNC_FEATURES_TO_CF"
