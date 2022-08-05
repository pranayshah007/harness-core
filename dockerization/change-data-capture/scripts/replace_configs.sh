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
    yq -i '.$CONFIG_KEY="$CONFIG_VALUE"' $CONFIG_FILE
  fi
}

if [[ "$STACK_DRIVER_LOGGING_ENABLED" == "true" ]]; then
  yq 'del(.'logging.appenders.(type==console)')' $CONFIG_FILE
  yq -i '.'logging.appenders.(type==gke-console).stackdriverLogEnabled'="true"' $CONFIG_FILE
else
  yq 'del(.'logging.appenders.(type==gke-console)')' $CONFIG_FILE
fi

# Remove the TLS connector (as ingress terminates TLS)
yq 'del(.connectors[0])' $CONFIG_FILE

if [[ "" != "$MONGO_URI" ]]; then
  yq -i '.harness-mongo.uri="$MONGO_URI"' $CONFIG_FILE
fi

if [[ "" != "$MONGO_SERVER_SELECTION_TIMEOUT" ]]; then
  yq -i '.harness-mongo.serverSelectionTimeout="$MONGO_SERVER_SELECTION_TIMEOUT"' $CONFIG_FILE
fi

if [[ "" != "$MONGO_TAG_NAME" ]]; then
  yq -i '.mongotags.tagKey="$MONGO_TAG_NAME"' $CONFIG_FILE
fi

if [[ "" != "$MONGO_TAG_VALUE" ]]; then
  yq -i '.mongotags.tagValue="$MONGO_TAG_VALUE"' $CONFIG_FILE
fi

if [[ "" != "$MONGO_INDEX_MANAGER_MODE" ]]; then
  yq -i '.harness-mongo.indexManagerMode="$MONGO_INDEX_MANAGER_MODE"' $CONFIG_FILE
fi

if [[ "" != "$EVEMTS_MONGO_INDEX_MANAGER_MODE" ]]; then
  yq -i '.events-mongo.indexManagerMode="$EVEMTS_MONGO_INDEX_MANAGER_MODE"' $CONFIG_FILE
fi

if [[ "" != "$EVENTS_MONGO_URI" ]]; then
  yq -i '.events-mongo.uri="$EVENTS_MONGO_URI"' $CONFIG_FILE
fi

if [[ "" != "$PMS_MONGO_URI" ]]; then
  yq -i '.pms-harness.uri="$PMS_MONGO_URI"' $CONFIG_FILE
fi

if [[ "" != "$CDC_MONGO_URI" ]]; then
  yq -i '.cdc-mongo.uri="$CDC_MONGO_URI"' $CONFIG_FILE
fi

if [[ "" != "$TIMESCALEDB_URI" ]]; then
  yq -i '.timescaledb.timescaledbUrl="$TIMESCALEDB_URI"' $CONFIG_FILE
fi

if [[ "" != "$TIMESCALEDB_USERNAME" ]]; then
  yq -i '.timescaledb.timescaledbUsername="$TIMESCALEDB_USERNAME"' $CONFIG_FILE
fi

if [[ "" != "$TIMESCALEDB_PASSWORD" ]]; then
  yq -i '.timescaledb.timescaledbPassword="$TIMESCALEDB_PASSWORD"' $CONFIG_FILE
fi

if [[ "" != "$GCP_PROJECT_ID" ]]; then
  yq -i '.gcp-project-id="$GCP_PROJECT_ID"' $CONFIG_FILE
fi

if [[ "" != "$NG_HARNESS_MONGO_URI" ]]; then
  yq -i '.ng-harness.uri=""' $CONFIG_FILE
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
