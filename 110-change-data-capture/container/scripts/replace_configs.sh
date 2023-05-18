#!/usr/bin/env bash
# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

CONFIG_FILE=/opt/harness/config.yml

replace_key_value () {
  CONFIG_KEY="$1";
  CONFIG_VALUE="$2";
  if [[ "" != "$CONFIG_VALUE" ]]; then
    export CONFIG_VALUE; export CONFIG_KEY; export CONFIG_KEY=.$CONFIG_KEY; yq -i 'eval(strenv(CONFIG_KEY))=env(CONFIG_VALUE)' $CONFIG_FILE
  fi
}

if [[ "$STACK_DRIVER_LOGGING_ENABLED" == "true" ]]; then
  yq -i 'del(.logging.appenders[0])' $CONFIG_FILE
  yq -i '.logging.appenders[0].stackdriverLogEnabled=true' $CONFIG_FILE
else
  yq -i 'del(.logging.appenders[1])' $CONFIG_FILE
fi

# Remove the TLS connector (as ingress terminates TLS)
yq -i 'del(.connectors[0])' $CONFIG_FILE

if [[ "" != "$MONGO_URI" ]]; then
  export MONGO_URI; yq -i '.harness-mongo.uri=env(MONGO_URI)' $CONFIG_FILE
fi

if [[ "" != "$MONGO_TAG_NAME" ]]; then
  export MONGO_TAG_NAME; yq -i '.mongotags.tagKey=env(MONGO_TAG_NAME)' $CONFIG_FILE
fi

if [[ "" != "$MONGO_TAG_VALUE" ]]; then
  export MONGO_TAG_VALUE; yq -i '.mongotags.tagValue=env(MONGO_TAG_VALUE)' $CONFIG_FILE
fi

if [[ "" != "$MONGO_INDEX_MANAGER_MODE" ]]; then
  export MONGO_INDEX_MANAGER_MODE; yq -i '.harness-mongo.indexManagerMode=env(MONGO_INDEX_MANAGER_MODE)' $CONFIG_FILE
fi

if [[ "" != "$EVEMTS_MONGO_INDEX_MANAGER_MODE" ]]; then
  export EVEMTS_MONGO_INDEX_MANAGER_MODE; yq -i '.events-mongo.indexManagerMode=env(EVEMTS_MONGO_INDEX_MANAGER_MODE)' $CONFIG_FILE
fi

if [[ "" != "$EVENTS_MONGO_URI" ]]; then
  export EVENTS_MONGO_URI; yq -i '.events-mongo.uri=env(EVENTS_MONGO_URI)' $CONFIG_FILE
fi

if [[ "" != "$PMS_MONGO_URI" ]]; then
  export PMS_MONGO_URI; yq -i '.pms-harness.uri=env(PMS_MONGO_URI)' $CONFIG_FILE
fi

if [[ "" != "$CDC_MONGO_URI" ]]; then
  export CDC_MONGO_URI; yq -i '.cdc-mongo.uri=env(CDC_MONGO_URI)' $CONFIG_FILE
fi

if [[ "" != "$MONGO_CHANGE_STREAM_BATCH_SIZE" ]]; then
  export MONGO_CHANGE_STREAM_BATCH_SIZE; yq -i '.changeStreamBatchSize=env(MONGO_CHANGE_STREAM_BATCH_SIZE)' $CONFIG_FILE
fi

if [[ "" != "$TIMESCALEDB_URI" ]]; then
  export TIMESCALEDB_URI; yq -i '.timescaledb.timescaledbUrl=env(TIMESCALEDB_URI)' $CONFIG_FILE
fi

if [[ "" != "$TIMESCALEDB_USERNAME" ]]; then
  export TIMESCALEDB_USERNAME; yq -i '.timescaledb.timescaledbUsername=env(TIMESCALEDB_USERNAME)' $CONFIG_FILE
fi

if [[ "" != "$TIMESCALEDB_PASSWORD" ]]; then
  export TIMESCALEDB_PASSWORD; yq -i '.timescaledb.timescaledbPassword=env(TIMESCALEDB_PASSWORD)' $CONFIG_FILE
fi

if [[ "" != "$TIMESCALEDB_SSL_MODE" ]]; then
  export TIMESCALEDB_SSL_MODE; yq -i '.timescaledb.sslMode=env(TIMESCALEDB_SSL_MODE)' $CONFIG_FILE
fi

if [[ "" != "$TIMESCALEDB_SSL_ROOT_CERT" ]]; then
  export TIMESCALEDB_SSL_ROOT_CERT; yq -i '.timescaledb.sslRootCert=env(TIMESCALEDB_SSL_ROOT_CERT)' $CONFIG_FILE
fi

if [[ "" != "$GCP_PROJECT_ID" ]]; then
  export GCP_PROJECT_ID; yq -i '.gcp-project-id=env(GCP_PROJECT_ID)' $CONFIG_FILE
fi

if [[ "" != "$NG_HARNESS_MONGO_URI" ]]; then
  yq -i '.ng-harness.uri=' $CONFIG_FILE
fi

if [[ "" != "$CVNG_MONGO_URI" ]]; then
  export $CVNG_MONGO_URI; yq -i '.cvng.uri=env($CVNG_MONGO_URI)' $CONFIG_FILE
fi

replace_key_value cfClientConfig.apiKey "$CF_CLIENT_API_KEY"
replace_key_value cfClientConfig.configUrl "$CF_CLIENT_CONFIG_URL"
replace_key_value cfClientConfig.eventUrl "$CF_CLIENT_EVENT_URL"
replace_key_value cfClientConfig.analyticsEnabled "$CF_CLIENT_ANALYTICS_ENABLED"
replace_key_value cfClientConfig.connectionTimeout "$CF_CLIENT_CONNECTION_TIMEOUT"
replace_key_value cfClientConfig.readTimeout "$CF_CLIENT_READ_TIMEOUT"
replace_key_value cfClientConfig.bufferSize "$CF_CLIENT_BUFFER_SIZE"

replace_key_value debeziumEnabled "$DEBEZIUM_ENABLED"

replace_key_value featureFlagConfig.featureFlagSystem "$FEATURE_FLAG_SYSTEM"
replace_key_value featureFlagConfig.syncFeaturesToCF "$SYNC_FEATURES_TO_CF"
