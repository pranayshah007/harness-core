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
    yq write -i "$CONFIG_FILE" "$CONFIG_KEY" "$CONFIG_VALUE"
  fi
}

addTags(){
	path=$1
	tags=$2
	IFS=',' read -ra str_array <<< "$tags"
	for tag in "${str_array[@]}"
		do
	   	 	yq write -i /opt/harness/command-library-server-config.yml "$path[+]" "$tag"
		done
}

yq delete -i /opt/harness/command-library-server-config.yml server.adminConnectors
yq delete -i $CONFIG_FILE 'server.applicationConnectors.(type==h2)'

if [[ "" != "$LOGGING_LEVEL" ]]; then
  yq write -i /opt/harness/command-library-server-config.yml logging.level "$LOGGING_LEVEL"
fi

if [[ "" != "$COMMAND_LIBRARY_SERVER_PORT" ]]; then
  yq write -i /opt/harness/command-library-server-config.yml server.applicationConnectors[0].port "$COMMAND_LIBRARY_SERVER_PORT"
else
  yq write -i /opt/harness/command-library-server-config.yml server.applicationConnectors[0].port "7070"
fi

if [[ "" != "$MONGO_URI" ]]; then
  yq write -i /opt/harness/command-library-server-config.yml mongo.uri "${MONGO_URI//\\&/&}"
fi

yq write -i /opt/harness/command-library-server-config.yml server.requestLog.appenders[0].type "console"
yq write -i /opt/harness/command-library-server-config.yml server.requestLog.appenders[0].threshold "TRACE"
yq write -i /opt/harness/command-library-server-config.yml server.requestLog.appenders[0].target "STDOUT"

if [[ "$STACK_DRIVER_LOGGING_ENABLED" == "true" ]]; then
  yq delete -i $CONFIG_FILE 'logging.appenders.(type==console)'
  yq write -i $CONFIG_FILE 'logging.appenders.(type==gke-console).stackdriverLogEnabled' "true"
else
  yq delete -i $CONFIG_FILE 'logging.appenders.(type==gke-console)'
fi

if [[ "" != "$MANAGER_TO_COMMAND_LIBRARY_SERVICE_SECRET" ]]; then
  yq write -i /opt/harness/command-library-server-config.yml serviceSecret.managerToCommandLibraryServiceSecret "$MANAGER_TO_COMMAND_LIBRARY_SERVICE_SECRET"
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
