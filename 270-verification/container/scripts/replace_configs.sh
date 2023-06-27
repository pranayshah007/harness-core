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
    export CONFIG_VALUE; export CONFIG_KEY; export CONFIG_KEY=.$CONFIG_KEY; yq -i 'eval(strenv(CONFIG_KEY))=env(CONFIG_VALUE)' $CONFIG_FILE
  fi
}

yq -i 'del(.server.adminConnectors)' $CONFIG_FILE
yq -i 'del(.server.applicationConnectors[0])' $CONFIG_FILE

if [[ "" != "$LOGGING_LEVEL" ]]; then
  export LOGGING_LEVEL; yq -i '.logging.level=env(LOGGING_LEVEL)' $CONFIG_FILE
fi

if [[ "" != "$VERIFICATION_PORT" ]]; then
  export VERIFICATION_PORT; yq -i '.server.applicationConnectors[0].port=env(VERIFICATION_PORT)' $CONFIG_FILE
else
  yq -i '.server.applicationConnectors[0].port=7070' $CONFIG_FILE
fi

if [[ "" != "$MONGO_URI" ]]; then
  export MONGO_URI=${MONGO_URI//\\&/&}; yq -i '.mongo.uri=env(MONGO_URI)' $CONFIG_FILE
fi

if [[ "" != "$DMS_MONGO_URI" ]]; then
  export DMS_MONGO_URI=${DMS_MONGO_URI//\\&/&}; yq -i '.dms-mongo.uri=env(DMS_MONGO_URI)' $CONFIG_FILE
fi

if [[ "" != "$MONGO_SSL_CONFIG" ]]; then
  export MONGO_SSL_CONFIG; yq -i '.mongo.mongoSSLConfig.mongoSSLEnabled=env(MONGO_SSL_CONFIG)' $CONFIG_FILE
fi

if [[ "" != "$MONGO_SSL_CA_TRUST_STORE_PATH" ]]; then
  export MONGO_SSL_CA_TRUST_STORE_PATH; yq -i '.mongo.mongoSSLConfig.mongoTrustStorePath=env(MONGO_SSL_CA_TRUST_STORE_PATH)' $CONFIG_FILE
fi

if [[ "" != "$MONGO_SSL_CA_TRUST_STORE_PASSWORD" ]]; then
  export MONGO_SSL_CA_TRUST_STORE_PASSWORD; yq -i '.mongo.mongoSSLConfig.mongoTrustStorePassword=env(MONGO_SSL_CA_TRUST_STORE_PASSWORD)' $CONFIG_FILE
fi

if [[ "" != "$MANAGER_URL" ]]; then
  export MANAGER_URL; yq -i '.managerUrl=env(MANAGER_URL)' $CONFIG_FILE
fi

if [[ "" != "$SG_ITERATOR_ENABLED" ]]; then
  export SG_ITERATOR_ENABLED; yq -i '.serviceGuardIteratorConfig.enabled=env(SG_ITERATOR_ENABLED)' $CONFIG_FILE
fi

if [[ "" != "$SG_ITERATOR_THREAD_COUNT" ]]; then
  export SG_ITERATOR_THREAD_COUNT; yq -i '.serviceGuardIteratorConfig.threadPoolCount=env(SG_ITERATOR_THREAD_COUNT)' $CONFIG_FILE
fi

if [[ "" != "$SG_ITERATOR_INTERVAL_SECS" ]]; then
  export SG_ITERATOR_INTERVAL_SECS; yq -i '.serviceGuardIteratorConfig.targetIntervalInSeconds=env(SG_ITERATOR_INTERVAL_SECS)' $CONFIG_FILE
fi

if [[ "" != "$DISTRIBUTED_LOCK_IMPLEMENTATION" ]]; then
  export DISTRIBUTED_LOCK_IMPLEMENTATION; yq -i '.distributedLockImplementation=env(DISTRIBUTED_LOCK_IMPLEMENTATION)' $CONFIG_FILE
fi

  yq -i '.server.requestLog.appenders[0].type="console"' $CONFIG_FILE
  yq -i '.server.requestLog.appenders[0].threshold="TRACE"' $CONFIG_FILE
  yq -i '.server.requestLog.appenders[0].target="STDOUT"' $CONFIG_FILE

if [[ "$STACK_DRIVER_LOGGING_ENABLED" == "true" ]]; then
  yq -i 'del(.logging.appenders[2])' $CONFIG_FILE
  yq -i 'del(.logging.appenders[0])' $CONFIG_FILE
  yq -i '.logging.appenders[0].stackdriverLogEnabled=true' $CONFIG_FILE
else
  if [[ "$ROLLING_FILE_LOGGING_ENABLED" == "true" ]]; then
    yq -i 'del(.logging.appenders[1])' $CONFIG_FILE
    yq -i '.logging.appenders[1].currentLogFilename="/opt/harness/logs/verification.log"' $CONFIG_FILE
    yq -i '.logging.appenders[1].archivedLogFilenamePattern="/opt/harness/logs/verification.%d.%i.log"' $CONFIG_FILE
  else
    yq -i 'del(.logging.appenders[2])' $CONFIG_FILE
    yq -i 'del(.logging.appenders[1])' $CONFIG_FILE
  fi
fi

if [[ "" != "$DATA_STORE" ]]; then
  export DATA_STORE; yq -i '.dataStorageMode=env(DATA_STORE)' $CONFIG_FILE
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
