#!/usr/bin/env bash
# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

CONFIG_FILE=/opt/harness/command-library-server-config.yml

replace_key_value () {
  CONFIG_KEY="$1";
  CONFIG_VALUE="$2";
  if [[ "" != "$CONFIG_VALUE" ]]; then
    export CONFIG_VALUE; export CONFIG_KEY; export CONFIG_KEY=.$CONFIG_KEY; yq -i 'eval(strenv(CONFIG_KEY))=env(CONFIG_VALUE)' $CONFIG_FILE
  fi
}

addTags(){
	path=$1
	tags=$2
	IFS=',' read -ra str_array <<< "$tags"
	for tag in "${str_array[@]}"
		do
yq -i '.	yq=write' 	
		done
}

yq -i 'del(.server.adminConnectors)' $CONFIG_FILE
yq -i 'del(.server.applicationConnectors.[] | select(.type == "h2"))' $CONFIG_FILE

if [[ "" != "$LOGGING_LEVEL" ]]; then
  export LOGGING_LEVEL; yq -i '.logging.level=env(LOGGING_LEVEL)' $CONFIG_FILE
fi

if [[ "" != "$COMMAND_LIBRARY_SERVER_PORT" ]]; then
  export COMMAND_LIBRARY_SERVER_PORT; yq -i '.server.applicationConnectors[0].port=env(COMMAND_LIBRARY_SERVER_PORT)' $CONFIG_FILE
else
  yq -i '.server.applicationConnectors[0].port=7070' $CONFIG_FILE
fi

if [[ "" != "$MONGO_URI" ]]; then
  export MONGO_URI=${MONGO_URI//\\&/&}; yq -i '.mongo.uri=env(MONGO_URI)' $CONFIG_FILE
fi

if [[ "" != "$MONGO_MAX_DOCUMENT_LIMIT" ]]; then
  export MONGO_MAX_DOCUMENT_LIMIT; yq -i '.mongo.maxDocumentsToBeFetched=env(MONGO_MAX_DOCUMENT_LIMIT)' $CONFIG_FILE
fi

yq -i '.server.requestLog.appenders[0].type="console"' $CONFIG_FILE
yq -i '.server.requestLog.appenders[0].threshold="TRACE"' $CONFIG_FILE
yq -i '.server.requestLog.appenders[0].target="STDOUT"' $CONFIG_FILE

if [[ "$STACK_DRIVER_LOGGING_ENABLED" == "true" ]]; then
  yq -i 'del(.logging.appenders.[] | select(.type == "console"))' $CONFIG_FILE
  yq -i '(.logging.appenders.[] | select(.type == "gke-console") | .stackdriverLogEnabled) = true' $CONFIG_FILE
else
  yq -i 'del(.logging.appenders.[] | select(.type == "gke-console"))' $CONFIG_FILE
fi

if [[ "" != "$MANAGER_TO_COMMAND_LIBRARY_SERVICE_SECRET" ]]; then
  export MANAGER_TO_COMMAND_LIBRARY_SERVICE_SECRET; yq -i '.serviceSecret.managerToCommandLibraryServiceSecret=env(MANAGER_TO_COMMAND_LIBRARY_SERVICE_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$ALLOWED_TAGS_TO_ADD" ]]; then
  addTags "tag.allowedTags" "$ALLOWED_TAGS_TO_ADD"
fi

if [[ "" != "$IMPORTANT_TAGS_TO_ADD" ]]; then
  addTags "tag.importantTags" "$IMPORTANT_TAGS_TO_ADD"
fi

replace_key_value cfClientConfig.apiKey "$CF_CLIENT_API_KEY"
replace_key_value cfClientConfig.configUrl "$CF_CLIENT_CONFIG_URL"
replace_key_value cfClientConfig.bufferSize "$CF_CLIENT_BUFFER_SIZE"
replace_key_value cfClientConfig.eventUrl "$CF_CLIENT_EVENT_URL"
replace_key_value cfClientConfig.analyticsEnabled "$CF_CLIENT_ANALYTICS_ENABLED"
replace_key_value cfClientConfig.connectionTimeout "$CF_CLIENT_CONNECTION_TIMEOUT"
replace_key_value cfClientConfig.readTimeout "$CF_CLIENT_READ_TIMEOUT"
replace_key_value featureFlagConfig.featureFlagSystem "$FEATURE_FLAG_SYSTEM"
replace_key_value featureFlagConfig.syncFeaturesToCF "$SYNC_FEATURES_TO_CF"
