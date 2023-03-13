#!/usr/bin/env bash
# Copyright 2023 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Shield 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.

CONFIG_FILE=/opt/harness/config.yml

replace_key_value () {
  CONFIG_KEY="$1";
  CONFIG_VALUE="$2";
  if [[ "" != "$CONFIG_VALUE" ]]; then
    export CONFIG_VALUE; export CONFIG_KEY; export CONFIG_KEY=.$CONFIG_KEY; yq -i 'eval(strenv(CONFIG_KEY))=env(CONFIG_VALUE)' $CONFIG_FILE
  fi
}

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

yq -i 'del(.server.applicationConnectors.[] | select(.type == "https"))' $CONFIG_FILE
yq -i '.server.adminConnectors=[]' $CONFIG_FILE

if [[ "" != "$LOGGING_LEVEL" ]]; then
    export LOGGING_LEVEL; yq -i '.logging.level=env(LOGGING_LEVEL)' $CONFIG_FILE
fi

if [[ "" != "$LOGGERS" ]]; then
  IFS=',' read -ra LOGGER_ITEMS <<< "$LOGGERS"
  for ITEM in "${LOGGER_ITEMS[@]}"; do
    LOGGER=`echo $ITEM | awk -F= '{print $1}'`
    LOGGER_LEVEL=`echo $ITEM | awk -F= '{print $2}'`
    export LOGGER_LEVEL; export LOGGER; yq -i '.logging.loggers.[env(LOGGER)]=env(LOGGER_LEVEL)' $CONFIG_FILE
  done
fi

if [[ "" != "$SERVER_MAX_THREADS" ]]; then
  export SERVER_MAX_THREADS; yq -i '.server.maxThreads=env(SERVER_MAX_THREADS)' $CONFIG_FILE
fi

if [[ "" != "$MONGO_URI" ]]; then
  export MONGO_URI=${MONGO_URI//\\&/&}; yq -i '.mongo.uri=env(MONGO_URI)' $CONFIG_FILE
fi

if [[ "" != "$MONGO_HOSTS_AND_PORTS" ]]; then
  yq -i 'del(.mongo.uri)' $CONFIG_FILE
  export MONGO_USERNAME; yq -i '.mongo.username=env(MONGO_USERNAME)' $CONFIG_FILE
  export MONGO_PASSWORD; yq -i '.mongo.password=env(MONGO_PASSWORD)' $CONFIG_FILE
  export MONGO_DATABASE; yq -i '.mongo.database=env(MONGO_DATABASE)' $CONFIG_FILE
  export MONGO_SCHEMA; yq -i '.mongo.schema=env(MONGO_SCHEMA)' $CONFIG_FILE
  write_mongo_hosts_and_ports mongo "$MONGO_HOSTS_AND_PORTS"
  write_mongo_params mongo "$MONGO_PARAMS"
fi

if [[ "" != "$MONGO_TRACE_MODE" ]]; then
  export MONGO_TRACE_MODE; yq -i '.mongo.traceMode=env(MONGO_TRACE_MODE)' $CONFIG_FILE
fi

if [[ "" != "$MONGO_MAX_OPERATION_TIME_IN_MILLIS" ]]; then
  export MONGO_MAX_OPERATION_TIME_IN_MILLIS; yq -i '.mongo.maxOperationTimeInMillis=env(MONGO_MAX_OPERATION_TIME_IN_MILLIS)' $CONFIG_FILE
fi

if [[ "" != "$MONGO_CONNECT_TIMEOUT" ]]; then
  export MONGO_CONNECT_TIMEOUT; yq -i '.mongo.connectTimeout=env(MONGO_CONNECT_TIMEOUT)' $CONFIG_FILE
fi

if [[ "" != "$MONGO_SERVER_SELECTION_TIMEOUT" ]]; then
  export MONGO_SERVER_SELECTION_TIMEOUT; yq -i '.mongo.serverSelectionTimeout=env(MONGO_SERVER_SELECTION_TIMEOUT)' $CONFIG_FILE
fi

if [[ "" != "$MONGO_SOCKET_TIMEOUT" ]]; then
  export MONGO_SOCKET_TIMEOUT; yq -i '.mongo.socketTimeout=env(MONGO_SOCKET_TIMEOUT)' $CONFIG_FILE
fi

if [[ "" != "$MAX_CONNECTION_IDLE_TIME" ]]; then
  export MAX_CONNECTION_IDLE_TIME; yq -i '.mongo.maxConnectionIdleTime=env(MAX_CONNECTION_IDLE_TIME)' $CONFIG_FILE
fi

if [[ "" != "$MONGO_CONNECTIONS_PER_HOST" ]]; then
  export MONGO_CONNECTIONS_PER_HOST; yq -i '.mongo.connectionsPerHost=env(MONGO_CONNECTIONS_PER_HOST)' $CONFIG_FILE
fi

if [[ "" != "$MONGO_INDEX_MANAGER_MODE" ]]; then
  export MONGO_INDEX_MANAGER_MODE; yq -i '.mongo.indexManagerMode=env(MONGO_INDEX_MANAGER_MODE)' $CONFIG_FILE
fi

if [[ "" != "$MONGO_TRANSACTIONS_ALLOWED" ]]; then
  export MONGO_TRANSACTIONS_ALLOWED; yq -i '.mongo.transactionsEnabled=env(MONGO_TRANSACTIONS_ALLOWED)' $CONFIG_FILE
fi

if [[ "" != "$EVENTS_FRAMEWORK_REDIS_SENTINELS" ]]; then
  IFS=',' read -ra SENTINEL_URLS <<< "$EVENTS_FRAMEWORK_REDIS_SENTINELS"
  INDEX=0
  for REDIS_SENTINEL_URL in "${SENTINEL_URLS[@]}"; do
    export REDIS_SENTINEL_URL; export INDEX; yq -i '.eventsFramework.redis.sentinelUrls.[env(INDEX)]=env(REDIS_SENTINEL_URL)' $CONFIG_FILE
    INDEX=$(expr $INDEX + 1)
  done
fi

if [[ "" != "$LOG_STREAMING_SERVICE_BASEURL" ]]; then
  export LOG_STREAMING_SERVICE_BASEURL; yq -i '.logStreamingServiceConfig.baseUrl=env(LOG_STREAMING_SERVICE_BASEURL)' $CONFIG_FILE
fi

if [[ "" != "$LOG_STREAMING_SERVICE_TOKEN" ]]; then
  export LOG_STREAMING_SERVICE_TOKEN; yq -i '.logStreamingServiceConfig.serviceToken=env(LOG_STREAMING_SERVICE_TOKEN)' $CONFIG_FILE
fi

if [[ "$STACK_DRIVER_LOGGING_ENABLED" == "true" ]]; then
  yq -i 'del(.logging.appenders.[] | select(.type == "console"))' $CONFIG_FILE
  yq -i '(.logging.appenders.[] | select(.type == "gke-console") | .stackdriverLogEnabled) = true' $CONFIG_FILE
else
  yq -i 'del(.logging.appenders.[] | select(.type == "gke-console"))' $CONFIG_FILE
fi

if [[ "" != "$NG_MANAGER_SERVICE_SECRET" ]]; then
  export NG_MANAGER_SERVICE_SECRET; yq -i '.ngManagerServiceSecret=env(NG_MANAGER_SERVICE_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$NG_MANAGER_BASE_URL" ]]; then
  export NG_MANAGER_BASE_URL; yq -i '.ngManagerServiceHttpClientConfig.baseUrl=env(NG_MANAGER_BASE_URL)' $CONFIG_FILE
fi

if [[ "" != "$ACCESS_CONTROL_BASE_URL" ]]; then
  export ACCESS_CONTROL_BASE_URL; yq -i '.accessControlClient.accessControlServiceConfig.baseUrl=env(ACCESS_CONTROL_BASE_URL)' $CONFIG_FILE
fi

if [[ "" != "$ACCESS_CONTROL_SECRET" ]]; then
  export ACCESS_CONTROL_SECRET; yq -i '.accessControlClient.accessControlServiceSecret=env(ACCESS_CONTROL_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$ACCESS_CONTROL_ENABLED" ]]; then
  export ACCESS_CONTROL_ENABLED; yq -i '.accessControlClient.enableAccessControl=env(ACCESS_CONTROL_ENABLED)' $CONFIG_FILE
fi

if [[ "" != "$BACKSTAGE_BASE_URL" ]]; then
  export BACKSTAGE_BASE_URL; yq -i '.backstageHttpClientConfig.baseUrl=env(BACKSTAGE_BASE_URL)' $CONFIG_FILE
fi

if [[ "" != "$BACKSTAGE_SERVICE_SECRET" ]]; then
  export BACKSTAGE_SERVICE_SECRET; yq -i '.backstageServiceSecret=env(BACKSTAGE_SERVICE_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$IDP_SERVICE_SECRET" ]]; then
  export IDP_SERVICE_SECRET; yq -i '.idpServiceSecret=env(IDP_SERVICE_SECRET)' $CONFIG_FILE
fi

replace_key_value eventsFramework.redis.sentinel $EVENTS_FRAMEWORK_USE_SENTINEL
replace_key_value eventsFramework.redis.envNamespace $EVENTS_FRAMEWORK_ENV_NAMESPACE
replace_key_value eventsFramework.redis.redisUrl $EVENTS_FRAMEWORK_REDIS_URL
replace_key_value eventsFramework.redis.masterName $EVENTS_FRAMEWORK_SENTINEL_MASTER_NAME
replace_key_value eventsFramework.redis.userName $EVENTS_FRAMEWORK_REDIS_USERNAME
replace_key_value eventsFramework.redis.password $EVENTS_FRAMEWORK_REDIS_PASSWORD
replace_key_value eventsFramework.redis.nettyThreads $EVENTS_FRAMEWORK_NETTY_THREADS
replace_key_value eventsFramework.redis.sslConfig.enabled $EVENTS_FRAMEWORK_REDIS_SSL_ENABLED
replace_key_value eventsFramework.redis.sslConfig.CATrustStorePath $EVENTS_FRAMEWORK_REDIS_SSL_CA_TRUST_STORE_PATH
replace_key_value eventsFramework.redis.sslConfig.CATrustStorePassword $EVENTS_FRAMEWORK_REDIS_SSL_CA_TRUST_STORE_PASSWORD
replace_key_value backstageSaToken "$BACKSTAGE_SA_TOKEN"
replace_key_value backstageSaCaCrt "$BACKSTAGE_SA_CA_CRT"
replace_key_value backstageMasterUrl "$BACKSTAGE_MASTER_URL"
replace_key_value idpServiceSecret "$IDP_SERVICE_SECRET"
replace_key_value jwtAuthSecret "$JWT_AUTH_SECRET"
replace_key_value jwtIdentityServiceSecret "$JWT_IDENTITY_SERVICE_SECRET"
replace_key_value provisionModuleConfig.triggerPipelineUrl "$TRIGGER_PIPELINE_URL"
replace_key_value accessControlClient.enableAccessControl $ACCESS_CONTROL_ENABLED
replace_key_value accessControlClient.accessControlServiceConfig.baseUrl "$ACCESS_CONTROL_BASE_URL"
replace_key_value accessControlClient.accessControlServiceSecret "$ACCESS_CONTROL_SECRET"
replace_key_value backstageHttpClientConfig.baseUrl "$BACKSTAGE_BASE_URL"
replace_key_value backstageServiceSecret "$BACKSTAGE_SERVICE_SECRET"