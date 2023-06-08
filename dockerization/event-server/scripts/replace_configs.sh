#!/usr/bin/env bash
# Copyright 2022 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

CONFIG_FILE=/opt/harness/event-service-config.yml

write_mongo_hosts_and_ports() {
  IFS=',' read -ra HOST_AND_PORT <<< "$2"
  for INDEX in "${!HOST_AND_PORT[@]}"; do
    HOST=$(cut -d: -f 1 <<< "${HOST_AND_PORT[$INDEX]}")
    PORT=$(cut -d: -f 2 -s <<< "${HOST_AND_PORT[$INDEX]}")

    export HOST; export ARG1=$1; export INDEX; yq -i '.env(ARG1).[env(INDEX)].host=env(HOST)' $CONFIG_FILE
    if [[ "" != "$PORT" ]]; then
      export PORT; export ARG1=$1; export INDEX; yq -i '.env(ARG1).[env(INDEX)].port=env(PORT)' $CONFIG_FILE
    fi
  done
}

write_mongo_params() {
  IFS='&' read -ra PARAMS <<< "$2"
  for PARAM_PAIR in "${PARAMS[@]}"; do
    NAME=$(cut -d= -f 1 <<< "$PARAM_PAIR")
    VALUE=$(cut -d= -f 2 <<< "$PARAM_PAIR")
    export VALUE; export ARG1=$1; export NAME; yq -i '.env(ARG1).params.env(NAME)=env(VALUE)' $CONFIG_FILE
  done
}

# Remove the TLS connector (as ingress terminates TLS)
yq -i 'del(.connectors.[] | select(.secure == true))' $CONFIG_FILE

if [[ "" != "$MONGO_URI" ]]; then
  export MONGO_URI; yq -i '.harness-mongo.uri=env(MONGO_URI)' $CONFIG_FILE
fi

if [[ "" != "$MONGO_HOSTS_AND_PORTS" ]]; then
  yq -i 'del(.harness-mongo.uri)' $CONFIG_FILE
  export MONGO_USERNAME; yq -i '.harness-mongo.username=env(MONGO_USERNAME)' $CONFIG_FILE
  export MONGO_PASSWORD; yq -i '.harness-mongo.password=env(MONGO_PASSWORD)' $CONFIG_FILE
  export MONGO_DATABASE; yq -i '.harness-mongo.database=env(MONGO_DATABASE)' $CONFIG_FILE
  export MONGO_SCHEMA; yq -i '.harness-mongo.schema=env(MONGO_SCHEMA)' $CONFIG_FILE
  write_mongo_hosts_and_ports harness-mongo "$MONGO_HOSTS_AND_PORTS"
  write_mongo_params harness-mongo "$MONGO_PARAMS"
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

if [[ "" != "$EVENTS_MONGO_HOSTS_AND_PORTS" ]]; then
  yq -i 'del(.events-mongo.uri)' $CONFIG_FILE
  export EVENTS_MONGO_USERNAME; yq -i '.events-mongo.username=env(EVENTS_MONGO_USERNAME)' $CONFIG_FILE
  export EVENTS_MONGO_PASSWORD; yq -i '.events-mongo.password=env(EVENTS_MONGO_PASSWORD)' $CONFIG_FILE
  export EVENTS_MONGO_DATABASE; yq -i '.events-mongo.database=env(EVENTS_MONGO_DATABASE)' $CONFIG_FILE
  export EVENTS_MONGO_SCHEMA; yq -i '.events-mongo.schema=env(EVENTS_MONGO_SCHEMA)' $CONFIG_FILE
  write_mongo_hosts_and_ports events-mongo "$EVENTS_MONGO_HOSTS_AND_PORTS"
  write_mongo_params events-mongo "$EVENTS_MONGO_PARAMS"
fi

if [[ "" != "$DMS_MONGO_INDEX_MANAGER_MODE" ]]; then
  export DMS_MONGO_INDEX_MANAGER_MODE; yq -i '.dms-mongo.indexManagerMode=env(DMS_MONGO_INDEX_MANAGER_MODE)' $CONFIG_FILE
fi

if [[ "" != "$DMS_MONGO_URI" ]]; then
  export DMS_MONGO_URI; yq -i '.dms-mongo.uri=env(DMS_MONGO_URI)' $CONFIG_FILE
fi

if [[ "" != "$DMS_MONGO_HOSTS_AND_PORTS" ]]; then
  yq -i 'del(.dms-mongo.uri)' $CONFIG_FILE
  export DMS_MONGO_USERNAME; yq -i '.dms-mongo.username=env(DMS_MONGO_USERNAME)' $CONFIG_FILE
  export DMS_MONGO_PASSWORD; yq -i '.dms-mongo.password=env(DMS_MONGO_PASSWORD)' $CONFIG_FILE
  export DMS_MONGO_DATABASE; yq -i '.dms-mongo.database=env(DMS_MONGO_DATABASE)' $CONFIG_FILE
  export DMS_MONGO_SCHEMA; yq -i '.dms-mongo.schema=env(DMS_MONGO_SCHEMA)' $CONFIG_FILE
  write_mongo_hosts_and_ports dms-mongo "$DMS_MONGO_HOSTS_AND_PORTS"
  write_mongo_params dms-mongo "$DMS_MONGO_PARAMS"
fi

if [[ "" != "$GCP_SECRET_MANAGER_PROJECT" ]]; then
  export GCP_SECRET_MANAGER_PROJECT; yq -i '.secretsConfiguration.gcpSecretManagerProject=env(GCP_SECRET_MANAGER_PROJECT)' $CONFIG_FILE
fi

if [[ "" != "$RESOLVE_SECRETS" ]]; then
  export RESOLVE_SECRETS; yq -i '.secretsConfiguration.secretResolutionEnabled=env(RESOLVE_SECRETS)' $CONFIG_FILE
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
  yq -i 'del(.logging.appenders.[] | select(.type == "console"))' $CONFIG_FILE
  yq -i '(.logging.appenders.[] | select(.type == "gke-console") | .stackdriverLogEnabled) = true' $CONFIG_FILE
else
  yq -i 'del(.logging.appenders.[] | select(.type == "gke-console"))' $CONFIG_FILE
fi