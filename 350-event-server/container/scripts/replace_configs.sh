#!/usr/bin/env bash
# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

CONFIG_FILE=/opt/harness/event-service-config.yml

# Remove the TLS connector (as ingress terminates TLS)
yq -i 'del(.connectors[0])' $CONFIG_FILE

if [[ "" != "$MONGO_URI" ]]; then
  export MONGO_URI; yq -i '.harness-mongo.uri=env(MONGO_URI)' $CONFIG_FILE
fi

if [[ "" != "$MONGO_READ_PREF_NAME" ]]; then
  export MONGO_READ_PREF_NAME; yq -i '.harness-mongo.readPref.name=env(MONGO_READ_PREF_NAME)' $CONFIG_FILE
fi

if [[ "" != "$MONGO_READ_PREF_TAGS" ]]; then
  IFS=',' read -ra TAG_ITEMS <<< "$MONGO_READ_PREF_TAGS"
  for ITEM in "${TAG_ITEMS[@]}"; do
    TAG_NAME=$(echo $ITEM | awk -F= '{print $1}')
    TAG_VALUE=$(echo $ITEM | awk -F= '{print $2}')
    export TAG_VALUE; export TAG_NAME; yq -i '.harness-mongo.readPref.tagSet.[env(TAG_NAME)]=env(TAG_VALUE)' $CONFIG_FILE
  done
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

if [[ "" != "$DMS_MONGO_INDEX_MANAGER_MODE" ]]; then
  export DMS_MONGO_INDEX_MANAGER_MODE; yq -i '.dms-mongo.indexManagerMode=env(DMS_MONGO_INDEX_MANAGER_MODE)' $CONFIG_FILE
fi

if [[ "" != "$DMS_MONGO_URI" ]]; then
  export DMS_MONGO_URI; yq -i '.dms-mongo.uri=env(DMS_MONGO_URI)' $CONFIG_FILE
fi

if [[ "" != "$LOGGING_LEVEL" ]]; then
  export LOGGING_LEVEL; yq -i '.logging.level=env(LOGGING_LEVEL)' $CONFIG_FILE
fi

if [[ "" != "$ENABLE_BATCH_WRITE" ]]; then
  export ENABLE_BATCH_WRITE; yq -i '.eventDataBatchQueryConfig.enableBatchWrite=env(ENABLE_BATCH_WRITE)' $CONFIG_FILE
fi

if [[ "" != "QUERY_BATCH_SIZE" ]]; then
  export QUERY_BATCH_SIZE; yq -i '.eventDataBatchQueryConfig.queryBatchSize=env(QUERY_BATCH_SIZE)' $CONFIG_FILE
fi

if [[ "$STACK_DRIVER_LOGGING_ENABLED" == "true" ]]; then
  yq -i 'del(.logging.appenders[0])' $CONFIG_FILE
  yq -i '.logging.appenders[0].stackdriverLogEnabled=true' $CONFIG_FILE
else
  yq -i 'del(.logging.appenders[1])' $CONFIG_FILE
fi