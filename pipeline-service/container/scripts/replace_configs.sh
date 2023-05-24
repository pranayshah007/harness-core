#!/usr/bin/env bash
# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Shield 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.

CONFIG_FILE=/opt/harness/config.yml
REDISSON_CACHE_FILE=/opt/harness/redisson-jcache.yaml
ENTERPRISE_REDISSON_CACHE_FILE=/opt/harness/enterprise-redisson-jcache.yaml

replace_key_value () {
  CONFIG_KEY="$1";
  CONFIG_VALUE="$2";
  if [[ "" != "$CONFIG_VALUE" ]]; then
    export CONFIG_VALUE; export CONFIG_KEY; export CONFIG_KEY=.$CONFIG_KEY; yq -i 'eval(strenv(CONFIG_KEY))=env(CONFIG_VALUE)' $CONFIG_FILE
  fi
}

if [[ "" != "$SERVER_MAX_THREADS" ]]; then
  export SERVER_MAX_THREADS; yq -i '.server.maxThreads=env(SERVER_MAX_THREADS)' $CONFIG_FILE
fi

yq -i '.server.adminConnectors=[]' $CONFIG_FILE

yq -i 'del(.grpcServerConfig.connectors.[] | select(.secure == true))' $CONFIG_FILE
yq -i 'del(.gitSdkConfiguration.gitSdkGrpcServerConfig.connectors.[] | select(.secure == true))' $CONFIG_FILE

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

if [[ "" != "$MONGO_URI" ]]; then
  export MONGO_URI=${MONGO_URI//\\&/&}; yq -i '.mongo.uri=env(MONGO_URI)' $CONFIG_FILE
fi

if [[ "" != "$MONGO_TRACE_MODE" ]]; then
  export MONGO_TRACE_MODE; yq -i '.mongo.traceMode=env(MONGO_TRACE_MODE)' $CONFIG_FILE
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

if [[ "" != "$DISTRIBUTED_LOCK_IMPLEMENTATION" ]]; then
  export DISTRIBUTED_LOCK_IMPLEMENTATION; yq -i '.distributedLockImplementation=env(DISTRIBUTED_LOCK_IMPLEMENTATION)' $CONFIG_FILE
fi

if [[ "" != "$GRPC_SERVER_PORT" ]]; then
  export GRPC_SERVER_PORT; yq -i '.grpcServerConfig.connectors[0].port=env(GRPC_SERVER_PORT)' $CONFIG_FILE
fi

if [[ "" != "$MANAGER_TARGET" ]]; then
  export MANAGER_TARGET; yq -i '.managerTarget=env(MANAGER_TARGET)' $CONFIG_FILE
fi

if [[ "" != "$MANAGER_AUTHORITY" ]]; then
  export MANAGER_AUTHORITY; yq -i '.managerAuthority=env(MANAGER_AUTHORITY)' $CONFIG_FILE
fi

if [[ "" != "$MANAGER_BASE_URL" ]]; then
  export MANAGER_BASE_URL; yq -i '.managerClientConfig.baseUrl=env(MANAGER_BASE_URL)' $CONFIG_FILE
fi

if [[ "" != "$MANAGER_SERVICE_SECRET" ]]; then
  export MANAGER_SERVICE_SECRET; yq -i '.managerServiceSecret=env(MANAGER_SERVICE_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$NG_MANAGER_BASE_URL" ]]; then
  export NG_MANAGER_BASE_URL; yq -i '.ngManagerServiceHttpClientConfig.baseUrl=env(NG_MANAGER_BASE_URL)' $CONFIG_FILE
fi

if [[ "" != "$NG_MANAGER_SERVICE_SECRET" ]]; then
  export NG_MANAGER_SERVICE_SECRET; yq -i '.ngManagerServiceSecret=env(NG_MANAGER_SERVICE_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$PIPELINE_SERVICE_ENDPOINT" ]]; then
  export PIPELINE_SERVICE_ENDPOINT; yq -i '.pipelineServiceClientConfig.baseUrl=env(PIPELINE_SERVICE_ENDPOINT)' $CONFIG_FILE
fi

if [[ "" != "$PIPELINE_SERVICE_SECRET" ]]; then
  export PIPELINE_SERVICE_SECRET; yq -i '.pipelineServiceSecret=env(PIPELINE_SERVICE_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$TEMPLATE_SERVICE_ENDPOINT" ]]; then
  export TEMPLATE_SERVICE_ENDPOINT; yq -i '.templateServiceClientConfig.baseUrl=env(TEMPLATE_SERVICE_ENDPOINT)' $CONFIG_FILE
fi

if [[ "" != "$TEMPLATE_SERVICE_SECRET" ]]; then
  export TEMPLATE_SERVICE_SECRET; yq -i '.templateServiceSecret=env(TEMPLATE_SERVICE_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$CI_MANAGER_BASE_URL" ]]; then
  export CI_MANAGER_BASE_URL;
  yq -i '.yamlSchemaClientConfig.yamlSchemaHttpClientMap.ci.serviceHttpClientConfig.baseUrl=env(CI_MANAGER_BASE_URL)' $CONFIG_FILE
  yq -i '.ciServiceClientConfig.baseUrl=env(CI_MANAGER_BASE_URL)' $CONFIG_FILE
fi

if [[ "" != "$CI_MANAGER_SERVICE_CONNECT_TIMEOUT_IN_SECONDS" ]]; then
  export CI_MANAGER_SERVICE_CONNECT_TIMEOUT_IN_SECONDS;
  yq -i '.yamlSchemaClientConfig.yamlSchemaHttpClientMap.ci.serviceHttpClientConfig.connectTimeOutSeconds=env(CI_MANAGER_SERVICE_CONNECT_TIMEOUT_IN_SECONDS)' $CONFIG_FILE
  yq -i '.ciServiceClientConfig.connectTimeOutSeconds=env(CI_MANAGER_SERVICE_CONNECT_TIMEOUT_IN_SECONDS)' $CONFIG_FILE
fi

if [[ "" != "$CI_MANAGER_SERVICE_READ_TIMEOUT_IN_SECONDS" ]]; then
  export CI_MANAGER_SERVICE_READ_TIMEOUT_IN_SECONDS;
  yq -i '.yamlSchemaClientConfig.yamlSchemaHttpClientMap.ci.serviceHttpClientConfig.readTimeOutSeconds=env(CI_MANAGER_SERVICE_READ_TIMEOUT_IN_SECONDS)' $CONFIG_FILE
  yq -i '.ciServiceClientConfig.readTimeOutSeconds=env(CI_MANAGER_SERVICE_READ_TIMEOUT_IN_SECONDS)' $CONFIG_FILE
fi

if [[ "" != "$CI_MANAGER_SERVICE_SECRET" ]]; then
  export CI_MANAGER_SERVICE_SECRET;
  yq -i '.yamlSchemaClientConfig.yamlSchemaHttpClientMap.ci.secret=env(CI_MANAGER_SERVICE_SECRET)' $CONFIG_FILE
  yq -i '.ciServiceSecret=env(CI_MANAGER_SERVICE_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$NG_MANAGER_BASE_URL" ]]; then
  export NG_MANAGER_BASE_URL; yq -i '.yamlSchemaClientConfig.yamlSchemaHttpClientMap.cd.serviceHttpClientConfig.baseUrl=env(NG_MANAGER_BASE_URL)' $CONFIG_FILE
fi

if [[ "" != "$NG_MANAGER_SERVICE_CONNECT_TIMEOUT_IN_SECONDS" ]]; then
  export NG_MANAGER_SERVICE_CONNECT_TIMEOUT_IN_SECONDS; yq -i '.yamlSchemaClientConfig.yamlSchemaHttpClientMap.cd.serviceHttpClientConfig.connectTimeOutSeconds=env(NG_MANAGER_SERVICE_CONNECT_TIMEOUT_IN_SECONDS)' $CONFIG_FILE
fi

if [[ "" != "$NG_MANAGER_SERVICE_READ_TIMEOUT_IN_SECONDS" ]]; then
  export NG_MANAGER_SERVICE_READ_TIMEOUT_IN_SECONDS; yq -i '.yamlSchemaClientConfig.yamlSchemaHttpClientMap.cd.serviceHttpClientConfig.readTimeOutSeconds=env(NG_MANAGER_SERVICE_READ_TIMEOUT_IN_SECONDS)' $CONFIG_FILE
fi

if [[ "" != "$NG_MANAGER_SERVICE_SECRET" ]]; then
  export NG_MANAGER_SERVICE_SECRET; yq -i '.yamlSchemaClientConfig.yamlSchemaHttpClientMap.cd.secret=env(NG_MANAGER_SERVICE_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$CV_MANAGER_BASE_URL" ]]; then
  export CV_MANAGER_BASE_URL; yq -i '.yamlSchemaClientConfig.yamlSchemaHttpClientMap.cv.serviceHttpClientConfig.baseUrl=env(CV_MANAGER_BASE_URL)' $CONFIG_FILE
fi

if [[ "" != "$CV_MANAGER_SERVICE_CONNECT_TIMEOUT_IN_SECONDS" ]]; then
  export CV_MANAGER_SERVICE_CONNECT_TIMEOUT_IN_SECONDS; yq -i '.yamlSchemaClientConfig.yamlSchemaHttpClientMap.cv.serviceHttpClientConfig.connectTimeOutSeconds=env(CV_MANAGER_SERVICE_CONNECT_TIMEOUT_IN_SECONDS)' $CONFIG_FILE
fi

if [[ "" != "$CV_MANAGER_SERVICE_READ_TIMEOUT_IN_SECONDS" ]]; then
  export CV_MANAGER_SERVICE_READ_TIMEOUT_IN_SECONDS; yq -i '.yamlSchemaClientConfig.yamlSchemaHttpClientMap.cv.serviceHttpClientConfig.readTimeOutSeconds=env(CV_MANAGER_SERVICE_READ_TIMEOUT_IN_SECONDS)' $CONFIG_FILE
fi

if [[ "" != "$CV_MANAGER_SERVICE_SECRET" ]]; then
  export CV_MANAGER_SERVICE_SECRET; yq -i '.yamlSchemaClientConfig.yamlSchemaHttpClientMap.cv.secret=env(CV_MANAGER_SERVICE_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$CV_MANAGER_BASE_URL" ]]; then
  export CV_MANAGER_BASE_URL; yq -i '.yamlSchemaClientConfig.yamlSchemaHttpClientMap.srm.serviceHttpClientConfig.baseUrl=env(CV_MANAGER_BASE_URL)' $CONFIG_FILE
fi

if [[ "" != "$CV_MANAGER_SERVICE_CONNECT_TIMEOUT_IN_SECONDS" ]]; then
  export CV_MANAGER_SERVICE_CONNECT_TIMEOUT_IN_SECONDS; yq -i '.yamlSchemaClientConfig.yamlSchemaHttpClientMap.srm.serviceHttpClientConfig.connectTimeOutSeconds=env(CV_MANAGER_SERVICE_CONNECT_TIMEOUT_IN_SECONDS)' $CONFIG_FILE
fi

if [[ "" != "$CV_MANAGER_SERVICE_READ_TIMEOUT_IN_SECONDS" ]]; then
  export CV_MANAGER_SERVICE_READ_TIMEOUT_IN_SECONDS; yq -i '.yamlSchemaClientConfig.yamlSchemaHttpClientMap.srm.serviceHttpClientConfig.readTimeOutSeconds=env(CV_MANAGER_SERVICE_READ_TIMEOUT_IN_SECONDS)' $CONFIG_FILE
fi

if [[ "" != "$CV_MANAGER_SERVICE_SECRET" ]]; then
  export CV_MANAGER_SERVICE_SECRET; yq -i '.yamlSchemaClientConfig.yamlSchemaHttpClientMap.srm.secret=env(CV_MANAGER_SERVICE_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$NG_MANAGER_TARGET" ]]; then
  export NG_MANAGER_TARGET; yq -i '.grpcClientConfigs.cd.target=env(NG_MANAGER_TARGET)' $CONFIG_FILE
fi

if [[ "" != "$NG_MANAGER_AUTHORITY" ]]; then
  export NG_MANAGER_AUTHORITY; yq -i '.grpcClientConfigs.cd.authority=env(NG_MANAGER_AUTHORITY)' $CONFIG_FILE
fi

if [[ "" != "$CVNG_MANAGER_TARGET" ]]; then
  export CVNG_MANAGER_TARGET; yq -i '.grpcClientConfigs.cv.target=env(CVNG_MANAGER_TARGET)' $CONFIG_FILE
fi

if [[ "" != "$CVNG_MANAGER_AUTHORITY" ]]; then
  export CVNG_MANAGER_AUTHORITY; yq -i '.grpcClientConfigs.cv.authority=env(CVNG_MANAGER_AUTHORITY)' $CONFIG_FILE
fi

if [[ "" != "$CVNG_MANAGER_TARGET" ]]; then
  export CVNG_MANAGER_TARGET; yq -i '.grpcClientConfigs.srm.target=env(CVNG_MANAGER_TARGET)' $CONFIG_FILE
fi

if [[ "" != "$CVNG_MANAGER_AUTHORITY" ]]; then
  export CVNG_MANAGER_AUTHORITY; yq -i '.grpcClientConfigs.srm.authority=env(CVNG_MANAGER_AUTHORITY)' $CONFIG_FILE
fi

if [[ "" != "$CI_MANAGER_TARGET" ]]; then
  export CI_MANAGER_TARGET; yq -i '.grpcClientConfigs.ci.target=env(CI_MANAGER_TARGET)' $CONFIG_FILE
fi

if [[ "" != "$CI_MANAGER_AUTHORITY" ]]; then
  export CI_MANAGER_AUTHORITY; yq -i '.grpcClientConfigs.ci.authority=env(CI_MANAGER_AUTHORITY)' $CONFIG_FILE
fi

if [[ "" != "$STO_MANAGER_TARGET" ]]; then
  export STO_MANAGER_TARGET; yq -i '.grpcClientConfigs.sto.target=env(STO_MANAGER_TARGET)' $CONFIG_FILE
fi

if [[ "" != "$STO_MANAGER_AUTHORITY" ]]; then
  export STO_MANAGER_AUTHORITY; yq -i '.grpcClientConfigs.sto.authority=env(STO_MANAGER_AUTHORITY)' $CONFIG_FILE
fi

if [[ "" != "$NG_MANAGER_GITSYNC_TARGET" ]]; then
  export NG_MANAGER_GITSYNC_TARGET; yq -i '.gitSdkConfiguration.gitManagerGrpcClientConfig.target=env(NG_MANAGER_GITSYNC_TARGET)' $CONFIG_FILE
fi

if [[ "" != "$NG_MANAGER_GITSYNC_AUTHORITY" ]]; then
  export NG_MANAGER_GITSYNC_AUTHORITY; yq -i '.gitSdkConfiguration.gitManagerGrpcClientConfig.authority=env(NG_MANAGER_GITSYNC_AUTHORITY)' $CONFIG_FILE
fi

if [[ "" != "$SCM_SERVICE_URI" ]]; then
  export SCM_SERVICE_URI; yq -i '.gitSdkConfiguration.scmConnectionConfig.url=env(SCM_SERVICE_URI)' $CONFIG_FILE
fi

if [[ "" != "$PIPELINE_SERVICE_BASE_URL" ]]; then
  export PIPELINE_SERVICE_BASE_URL; yq -i '.pipelineServiceBaseUrl=env(PIPELINE_SERVICE_BASE_URL)' $CONFIG_FILE
fi

if [[ "" != "$PMS_API_BASE_URL" ]]; then
  export PMS_API_BASE_URL; yq -i '.pmsApiBaseUrl=env(PMS_API_BASE_URL)' $CONFIG_FILE
fi

if [[ "" != "$SSCA_SERVICE_ENDPOINT" ]]; then
  export SSCA_SERVICE_ENDPOINT; yq -i '.sscaServiceConfig.httpClientConfig.baseUrl=env(SSCA_SERVICE_ENDPOINT)' $CONFIG_FILE
fi

if [[ "" != "$SSCA_SERVICE_GLOBAL_TOKEN" ]]; then
  export SSCA_SERVICE_GLOBAL_TOKEN; yq -i '.sscaServiceConfig.globalToken=env(SSCA_SERVICE_GLOBAL_TOKEN)' $CONFIG_FILE
fi

if [[ "$STACK_DRIVER_LOGGING_ENABLED" == "true" ]]; then
  yq -i 'del(.logging.appenders.[] | select(.type == "console"))' $CONFIG_FILE
  yq -i '(.logging.appenders.[] | select(.type == "gke-console") | .stackdriverLogEnabled) = true' $CONFIG_FILE
else
  yq -i 'del(.logging.appenders.[] | select(.type == "gke-console"))' $CONFIG_FILE
fi

if [[ "" != "$JWT_AUTH_SECRET" ]]; then
  export JWT_AUTH_SECRET; yq -i '.jwtAuthSecret=env(JWT_AUTH_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$JWT_IDENTITY_SERVICE_SECRET" ]]; then
  export JWT_IDENTITY_SERVICE_SECRET; yq -i '.jwtIdentityServiceSecret=env(JWT_IDENTITY_SERVICE_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$EVENTS_FRAMEWORK_REDIS_SENTINELS" ]]; then
  IFS=',' read -ra SENTINEL_URLS <<< "$EVENTS_FRAMEWORK_REDIS_SENTINELS"
  INDEX=0
  for REDIS_SENTINEL_URL in "${SENTINEL_URLS[@]}"; do
    export REDIS_SENTINEL_URL; export INDEX; yq -i '.eventsFramework.redis.sentinelUrls.[env(INDEX)]=env(REDIS_SENTINEL_URL)' $CONFIG_FILE
    INDEX=$(expr $INDEX + 1)
  done
fi

if [[ "" != "$EVENTS_FRAMEWORK_SNAPSHOT_REDIS_SENTINELS" ]]; then
  IFS=',' read -ra SENTINEL_URLS <<< "$EVENTS_FRAMEWORK_SNAPSHOT_REDIS_SENTINELS"
  INDEX=0
  for REDIS_SENTINEL_URL in "${SENTINEL_URLS[@]}"; do
    export REDIS_SENTINEL_URL; export INDEX; yq -i '.eventsFrameworkSnapshotDebezium.redis.sentinelUrls.[env(INDEX)]=env(REDIS_SENTINEL_URL)' $CONFIG_FILE
    INDEX=$(expr $INDEX + 1)
  done
fi

if [[ "" != "$NOTIFICATION_BASE_URL" ]]; then
  export NOTIFICATION_BASE_URL; yq -i '.notificationClient.httpClient.baseUrl=env(NOTIFICATION_BASE_URL)' $CONFIG_FILE
fi

if [[ "" != "$NOTIFICATION_MONGO_URI" ]]; then
  export NOTIFICATION_MONGO_URI=${NOTIFICATION_MONGO_URI//\\&/&}; yq -i '.notificationClient.messageBroker.uri=env(NOTIFICATION_MONGO_URI)' $CONFIG_FILE
fi

if [[ "" != "$MANAGER_CLIENT_BASEURL" ]]; then
  export MANAGER_CLIENT_BASEURL; yq -i '.managerClientConfig.baseUrl=env(MANAGER_CLIENT_BASEURL)' $CONFIG_FILE
fi

if [[ "" != "$TIMESCALE_PASSWORD" ]]; then
  export TIMESCALE_PASSWORD; yq -i '.timescaledb.timescaledbPassword=env(TIMESCALE_PASSWORD)' $CONFIG_FILE
fi

if [[ "" != "$TIMESCALE_URI" ]]; then
  export TIMESCALE_URI; yq -i '.timescaledb.timescaledbUrl=env(TIMESCALE_URI)' $CONFIG_FILE
fi

if [[ "" != "$TIMESCALEDB_USERNAME" ]]; then
  export TIMESCALEDB_USERNAME; yq -i '.timescaledb.timescaledbUsername=env(TIMESCALEDB_USERNAME)' $CONFIG_FILE
fi

if [[ "" != "$TIMESCALEDB_SSL_MODE" ]]; then
  export TIMESCALEDB_SSL_MODE; yq -i '.timescaledb.sslMode=env(TIMESCALEDB_SSL_MODE)' $CONFIG_FILE
fi

if [[ "" != "$TIMESCALEDB_SSL_ROOT_CERT" ]]; then
  export TIMESCALEDB_SSL_ROOT_CERT; yq -i '.timescaledb.sslRootCert=env(TIMESCALEDB_SSL_ROOT_CERT)' $CONFIG_FILE
fi

if [[ "" != "$ENABLE_DASHBOARD_TIMESCALE" ]]; then
  export ENABLE_DASHBOARD_TIMESCALE; yq -i '.enableDashboardTimescale=env(ENABLE_DASHBOARD_TIMESCALE)' $CONFIG_FILE
fi

yq -i 'del(.codec)' $REDISSON_CACHE_FILE

if [[ "$REDIS_SCRIPT_CACHE" == "false" ]]; then
  yq -i '.useScriptCache=false' $REDISSON_CACHE_FILE
fi


if [[ "" != "$CACHE_CONFIG_REDIS_URL" ]]; then
  export CACHE_CONFIG_REDIS_URL; yq -i '.singleServerConfig.address=env(CACHE_CONFIG_REDIS_URL)' $REDISSON_CACHE_FILE
fi

if [[ "$CACHE_CONFIG_USE_SENTINEL" == "true" ]]; then
  yq -i 'del(.singleServerConfig)' $REDISSON_CACHE_FILE
fi

if [[ "" != "$CACHE_CONFIG_SENTINEL_MASTER_NAME" ]]; then
  export CACHE_CONFIG_SENTINEL_MASTER_NAME; yq -i '.sentinelServersConfig.masterName=env(CACHE_CONFIG_SENTINEL_MASTER_NAME)' $REDISSON_CACHE_FILE
fi

if [[ "" != "$CACHE_CONFIG_REDIS_SENTINELS" ]]; then
  IFS=',' read -ra SENTINEL_URLS <<< "$CACHE_CONFIG_REDIS_SENTINELS"
  INDEX=0
  for REDIS_SENTINEL_URL in "${SENTINEL_URLS[@]}"; do
    export REDIS_SENTINEL_URL; export INDEX; yq -i '.sentinelServersConfig.sentinelAddresses.[env(INDEX)]=env(REDIS_SENTINEL_URL)' $REDISSON_CACHE_FILE
    INDEX=$(expr $INDEX + 1)
  done
fi

if [[ "" != "$REDIS_NETTY_THREADS" ]]; then
  export REDIS_NETTY_THREADS; yq -i '.nettyThreads=env(REDIS_NETTY_THREADS)' $REDISSON_CACHE_FILE
fi

if [[ "" != "$REDIS_CONNECTION_POOL_SIZE" ]]; then
  export REDIS_CONNECTION_POOL_SIZE; yq -i '.singleServerConfig.connectionPoolSize=env(REDIS_CONNECTION_POOL_SIZE)' $REDISSON_CACHE_FILE
fi

if [[ "" != "$REDIS_RETRY_INTERVAL" ]]; then
  export REDIS_RETRY_INTERVAL; yq -i '.singleServerConfig.retryInterval=env(REDIS_RETRY_INTERVAL)' $REDISSON_CACHE_FILE
fi

if [[ "" != "$REDIS_RETRY_ATTEMPTS" ]]; then
  export REDIS_RETRY_ATTEMPTS; yq -i '.singleServerConfig.retryAttempts=env(REDIS_RETRY_ATTEMPTS)' $REDISSON_CACHE_FILE
fi

if [[ "" != "$REDIS_TIMEOUT" ]]; then
  export REDIS_TIMEOUT; yq -i '.singleServerConfig.timeout=env(REDIS_TIMEOUT)' $REDISSON_CACHE_FILE
fi

yq -i 'del(.codec)' $ENTERPRISE_REDISSON_CACHE_FILE

if [[ "$REDIS_SCRIPT_CACHE" == "false" ]]; then
  yq -i '.useScriptCache=false' $ENTERPRISE_REDISSON_CACHE_FILE
fi

if [[ "" != "$EVENTS_FRAMEWORK_NETTY_THREADS" ]]; then
  export EVENTS_FRAMEWORK_NETTY_THREADS; yq -i '.nettyThreads=env(EVENTS_FRAMEWORK_NETTY_THREADS)' $ENTERPRISE_REDISSON_CACHE_FILE
fi

if [[ "" != "$EVENTS_FRAMEWORK_REDIS_URL" ]]; then
  export EVENTS_FRAMEWORK_REDIS_URL; yq -i '.singleServerConfig.address=env(EVENTS_FRAMEWORK_REDIS_URL)' $ENTERPRISE_REDISSON_CACHE_FILE
fi

if [[ "" != "$EVENTS_FRAMEWORK_REDIS_USERNAME" ]]; then
  export EVENTS_FRAMEWORK_REDIS_USERNAME; yq -i '.singleServerConfig.username=env(EVENTS_FRAMEWORK_REDIS_USERNAME)' $ENTERPRISE_REDISSON_CACHE_FILE
fi

if [[ "" != "$EVENTS_FRAMEWORK_REDIS_PASSWORD" ]]; then
  export EVENTS_FRAMEWORK_REDIS_PASSWORD; yq -i '.singleServerConfig.password=env(EVENTS_FRAMEWORK_REDIS_PASSWORD)' $ENTERPRISE_REDISSON_CACHE_FILE
fi

if [[ "" != "$EVENTS_FRAMEWORK_REDIS_SSL_CA_TRUST_STORE_PATH" ]]; then
  export FILE_VAR="file:$EVENTS_FRAMEWORK_REDIS_SSL_CA_TRUST_STORE_PATH"; yq -i '.singleServerConfig.sslTruststore=env(FILE_VAR)' $ENTERPRISE_REDISSON_CACHE_FILE
fi

if [[ "" != "$EVENTS_FRAMEWORK_REDIS_SSL_CA_TRUST_STORE_PASSWORD" ]]; then
  export EVENTS_FRAMEWORK_REDIS_SSL_CA_TRUST_STORE_PASSWORD; yq -i '.singleServerConfig.sslTruststorePassword=env(EVENTS_FRAMEWORK_REDIS_SSL_CA_TRUST_STORE_PASSWORD)' $ENTERPRISE_REDISSON_CACHE_FILE
fi

if [[ "$EVENTS_FRAMEWORK_USE_SENTINEL" == "true" ]]; then
  yq -i 'del(.singleServerConfig)' $ENTERPRISE_REDISSON_CACHE_FILE

  if [[ "" != "$EVENTS_FRAMEWORK_SENTINEL_MASTER_NAME" ]]; then
    export EVENTS_FRAMEWORK_SENTINEL_MASTER_NAME; yq -i '.sentinelServersConfig.masterName=env(EVENTS_FRAMEWORK_SENTINEL_MASTER_NAME)' $ENTERPRISE_REDISSON_CACHE_FILE
  fi

  if [[ "" != "$EVENTS_FRAMEWORK_REDIS_SENTINELS" ]]; then
    IFS=',' read -ra SENTINEL_URLS <<< "$EVENTS_FRAMEWORK_REDIS_SENTINELS"
    INDEX=0
    for REDIS_SENTINEL_URL in "${SENTINEL_URLS[@]}"; do
      export REDIS_SENTINEL_URL; export INDEX; yq -i '.sentinelServersConfig.sentinelAddresses.[env(INDEX)]=env(REDIS_SENTINEL_URL)' $ENTERPRISE_REDISSON_CACHE_FILE
      INDEX=$(expr $INDEX + 1)
    done
  fi
fi

if [[ "" != "$ALLOWED_ORIGINS" ]]; then
  yq -i 'del(.allowedOrigins)' $CONFIG_FILE
  export ALLOWED_ORIGINS; yq -i '.allowedOrigins=(env(ALLOWED_ORIGINS) | split(",") | map(trim))' $CONFIG_FILE
fi

replace_key_value cacheConfig.cacheNamespace $CACHE_NAMESPACE
replace_key_value cacheConfig.cacheBackend $CACHE_BACKEND
replace_key_value cacheConfig.enterpriseCacheEnabled $ENTERPRISE_CACHE_ENABLED

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
replace_key_value eventsFramework.redis.retryAttempts $REDIS_RETRY_ATTEMPTS
replace_key_value eventsFramework.redis.retryInterval $REDIS_RETRY_INTERVAL

replace_key_value eventsFrameworkSnapshotDebezium.redis.sentinel $EVENTS_FRAMEWORK_SNAPSHOT_USE_SENTINEL
replace_key_value eventsFrameworkSnapshotDebezium.redis.envNamespace $EVENTS_FRAMEWORK_SNAPSHOT_ENV_NAMESPACE
replace_key_value eventsFrameworkSnapshotDebezium.redis.redisUrl $EVENTS_FRAMEWORK_SNAPSHOT_REDIS_URL
replace_key_value eventsFrameworkSnapshotDebezium.redis.masterName $EVENTS_FRAMEWORK_SNAPSHOT_SENTINEL_MASTER_NAME
replace_key_value eventsFrameworkSnapshotDebezium.redis.userName $EVENTS_FRAMEWORK_SNAPSHOT_REDIS_USERNAME
replace_key_value eventsFrameworkSnapshotDebezium.redis.password $EVENTS_FRAMEWORK_SNAPSHOT_REDIS_PASSWORD
replace_key_value eventsFrameworkSnapshotDebezium.redis.nettyThreads $EVENTS_FRAMEWORK_SNAPSHOT_NETTY_THREADS
replace_key_value eventsFrameworkSnapshotDebezium.redis.sslConfig.enabled $EVENTS_FRAMEWORK_SNAPSHOT_REDIS_SSL_ENABLED
replace_key_value eventsFrameworkSnapshotDebezium.redis.sslConfig.CATrustStorePath $EVENTS_FRAMEWORK_SNAPSHOT_REDIS_SSL_CA_TRUST_STORE_PATH
replace_key_value eventsFrameworkSnapshotDebezium.redis.sslConfig.CATrustStorePassword $EVENTS_FRAMEWORK_SNAPSHOT_REDIS_SSL_CA_TRUST_STORE_PASSWORD

if [[ "" != "$LOCK_CONFIG_REDIS_SENTINELS" ]]; then
  IFS=',' read -ra SENTINEL_URLS <<< "$LOCK_CONFIG_REDIS_SENTINELS"
  INDEX=0
  for REDIS_SENTINEL_URL in "${SENTINEL_URLS[@]}"; do
    export REDIS_SENTINEL_URL; export INDEX; yq -i '.redisLockConfig.sentinelUrls.[env(INDEX)]=env(REDIS_SENTINEL_URL)' $CONFIG_FILE
    INDEX=$(expr $INDEX + 1)
  done
fi

replace_key_value redisLockConfig.redisUrl "$LOCK_CONFIG_REDIS_URL"
replace_key_value redisLockConfig.envNamespace "$LOCK_CONFIG_ENV_NAMESPACE"
replace_key_value redisLockConfig.sentinel "$LOCK_CONFIG_USE_SENTINEL"
replace_key_value redisLockConfig.masterName "$LOCK_CONFIG_SENTINEL_MASTER_NAME"
replace_key_value redisLockConfig.userName "$LOCK_CONFIG_REDIS_USERNAME"
replace_key_value redisLockConfig.password "$LOCK_CONFIG_REDIS_PASSWORD"
replace_key_value redisLockConfig.nettyThreads "$REDIS_NETTY_THREADS"
replace_key_value redisLockConfig.connectionPoolSize $REDIS_CONNECTION_POOL_SIZE
replace_key_value redisLockConfig.retryInterval $REDIS_RETRY_INTERVAL
replace_key_value redisLockConfig.retryAttempts $REDIS_RETRY_ATTEMPTS
replace_key_value redisLockConfig.timeout $REDIS_TIMEOUT

replace_key_value accessControlClient.enableAccessControl "$ACCESS_CONTROL_ENABLED"

replace_key_value accessControlClient.accessControlServiceConfig.baseUrl "$ACCESS_CONTROL_BASE_URL"

replace_key_value accessControlClient.accessControlServiceSecret "$ACCESS_CONTROL_SECRET"

replace_key_value logStreamingServiceConfig.baseUrl "$LOG_STREAMING_SERVICE_BASEURL"

replace_key_value logStreamingServiceConfig.serviceToken "$LOG_STREAMING_SERVICE_TOKEN"

replace_key_value iteratorsConfig.approvalInstanceIteratorConfig.enabled "$APPROVAL_INSTANCE_ITERATOR_ENABLED"
replace_key_value iteratorsConfig.approvalInstanceIteratorConfig.targetIntervalInSeconds "$APPROVAL_INSTANCE_ITERATOR_INTERVAL_SEC"
replace_key_value orchestrationStepConfig.ffServerBaseUrl "$FF_SERVER_BASE_URL"
replace_key_value orchestrationStepConfig.ffServerApiKey "$FF_SERVER_API_KEY"

replace_key_value shouldDeployWithGitSync "$ENABLE_GIT_SYNC"

replace_key_value enableAudit "$ENABLE_AUDIT"
replace_key_value auditClientConfig.baseUrl "$AUDIT_SERVICE_BASE_URL"
replace_key_value notificationClient.secrets.notificationClientSecret "$NOTIFICATION_CLIENT_SECRET"

replace_key_value triggerConfig.webhookBaseUrl "$WEBHOOK_TRIGGER_BASEURL"
replace_key_value triggerConfig.customBaseUrl "$CUSTOM_TRIGGER_BASEURL"

replace_key_value opaServerConfig.baseUrl "$OPA_SERVER_BASEURL"
replace_key_value opaServerConfig.secret "$OPA_SERVER_SECRET"

replace_key_value delegatePollingConfig.syncDelay "$POLLING_SYNC_DELAY"
replace_key_value delegatePollingConfig.asyncDelay "$POLLING_ASYNC_DELAY"
replace_key_value delegatePollingConfig.progressDelay "$POLLING_PROGRESS_DELAY"

replace_key_value segmentConfiguration.enabled "$SEGMENT_ENABLED"
replace_key_value segmentConfiguration.apiKey "$SEGMENT_APIKEY"

#Iterators Configuration
replace_key_value iteratorsConfig.approvalInstance.enabled "$APPROVAL_INSTANCE_ITERATOR_ENABLED"
replace_key_value iteratorsConfig.approvalInstance.threadPoolCount "$BARRIER_ITERATOR_THREAD_POOL_SIZE"
replace_key_value iteratorsConfig.approvalInstance.targetIntervalInSeconds "$APPROVAL_INSTANCE_ITERATOR_INTERVAL_SEC"

replace_key_value iteratorsConfig.webhook.enabled "$WEBHOOK_ITERATOR_ENABLED"
replace_key_value iteratorsConfig.webhook.threadPoolCount "$WEBHOOK_ITERATOR_THREAD_POOL_SIZE"
replace_key_value iteratorsConfig.webhook.targetIntervalInSeconds "$WEBHOOK_ITERATOR_INTERVAL_SEC"

replace_key_value iteratorsConfig.scheduledTrigger.enabled "$SCHEDULED_TRIGGER_ITERATOR_ENABLED"
replace_key_value iteratorsConfig.scheduledTrigger.threadPoolCount "$SCHEDULED_TRIGGER_ITERATOR_THREAD_POOL_SIZE"
replace_key_value iteratorsConfig.scheduledTrigger.targetIntervalInSeconds "$SCHEDULED_TRIGGER_ITERATOR_INTERVAL_SEC"

replace_key_value iteratorsConfig.timeoutEngine.enabled "$TIME_OUT_ENGINE_ITERATOR_ENABLED"
replace_key_value iteratorsConfig.timeoutEngine.threadPoolCount "$TIMEOUT_ENGINE_ITERATOR_THREAD_POOL_SIZE"
replace_key_value iteratorsConfig.timeoutEngine.targetIntervalInSeconds "$TIME_OUT_ENGINE_ITERATOR_INTERVAL_SEC"

replace_key_value iteratorsConfig.barrier.enabled "$BARRIER_ITERATOR_ENABLED"
replace_key_value iteratorsConfig.barrier.threadPoolCount "$BARRIER_ITERATOR_THREAD_POOL_SIZE"
replace_key_value iteratorsConfig.barrier.targetIntervalInSeconds "$BARRIER_ITERATOR_INTERVAL_SEC"

replace_key_value iteratorsConfig.resourceRestraint.enabled "$RESOURCE_RESTRAINT_ITERATOR_ENABLED"
replace_key_value iteratorsConfig.resourceRestraint.threadPoolCount "$RESOURCE_RESTRAINT_ITERATOR_THREAD_POOL_SIZE"
replace_key_value iteratorsConfig.resourceRestraint.targetIntervalInSeconds "$RESOURCE_RESTRAINT_ITERATOR_INTERVAL_SEC"

replace_key_value iteratorsConfig.interruptMonitor.enabled "$INTERRUPT_MONITOR_ITERATOR_ENABLED"
replace_key_value iteratorsConfig.interruptMonitor.threadPoolCount "$INTERRUPT_MONITOR_ITERATOR_THREAD_POOL_SIZE"
replace_key_value iteratorsConfig.interruptMonitor.targetIntervalInSeconds "$INTERRUPT_MONITOR_ITERATOR_INTERVAL_SEC"

#consumers configuration
replace_key_value pipelineEventConsumersConfig.interrupt.threads "$INTERRUPT_CONSUMER_THREAD_COUNT"
replace_key_value pipelineEventConsumersConfig.orchestrationEvent.threads "$ORCHESTRATION_EVENT_CONSUMER_THREAD_COUNT"
replace_key_value pipelineEventConsumersConfig.facilitatorEvent.threads "$FACILITATE_EVENT_CONSUMER_THREAD_COUNT"
replace_key_value pipelineEventConsumersConfig.nodeStart.threads "$NODE_START_EVENT_CONSUMER_THREAD_COUNT"
replace_key_value pipelineEventConsumersConfig.progress.threads "$PROGRESS_EVENT_CONSUMER_THREAD_COUNT"
replace_key_value pipelineEventConsumersConfig.advise.threads "$ADVISE_EVENT_CONSUMER_THREAD_COUNT"
replace_key_value pipelineEventConsumersConfig.resume.threads "$RESUME_EVENT_CONSUMER_THREAD_COUNT"
replace_key_value pipelineEventConsumersConfig.sdkResponse.threads "$SDK_RESPONSE_EVENT_CONSUMER_THREAD_COUNT"
replace_key_value pipelineEventConsumersConfig.graphUpdate.threads "$GRAPH_UPDATE_EVENT_CONSUMER_THREAD_COUNT"
replace_key_value pipelineEventConsumersConfig.pipelineExecutionEvent.threads "$PIPELINE_EXECUTION_EVENT_CONSUMER_THREAD_COUNT"
replace_key_value pipelineEventConsumersConfig.partialPlanResponse.threads "$PARTIAL_PLAN_RESPONSE_EVENT_CONSUMER_THREAD_COUNT"
replace_key_value pipelineEventConsumersConfig.createPlan.threads "$CREATE_PLAN_EVENT_CONSUMER_THREAD_COUNT"
replace_key_value pipelineEventConsumersConfig.planNotify.threads "$PLAN_NOTIFY_EVENT_CONSUMER_THREAD_COUNT"
replace_key_value pipelineEventConsumersConfig.pmsNotify.threads "$PMS_NOTIFY_EVENT_CONSUMER_THREAD_COUNT"
replace_key_value pipelineEventConsumersConfig.webhookEvent.threads "$PMS_WEBHOOK_EVENT_CONSUMER_THREAD_COUNT"
replace_key_value pipelineEventConsumersConfig.initiateNode.threads "$INITIATE_NODE_EVENT_CONSUMER_THREAD_COUNT"

replace_key_value enforcementClientConfiguration.enforcementCheckEnabled "$ENFORCEMENT_CHECK_ENABLED"
replace_key_value segmentConfiguration.url "$SEGMENT_URL"

replace_key_value cfClientConfig.apiKey "$CF_CLIENT_API_KEY"
replace_key_value cfClientConfig.configUrl "$CF_CLIENT_CONFIG_URL"
replace_key_value cfClientConfig.eventUrl "$CF_CLIENT_EVENT_URL"
replace_key_value cfClientConfig.analyticsEnabled "$CF_CLIENT_ANALYTICS_ENABLED"
replace_key_value cfClientConfig.connectionTimeout "$CF_CLIENT_CONNECTION_TIMEOUT"
replace_key_value cfClientConfig.readTimeout "$CF_CLIENT_READ_TIMEOUT"
replace_key_value cfClientConfig.bufferSize "$CF_CLIENT_BUFFER_SIZE"
replace_key_value cfClientConfig.retries "$CF_RETRIES"
replace_key_value cfClientConfig.sleepInterval "$CF_SLEEP_INTERVAL"

replace_key_value featureFlagConfig.featureFlagSystem "$FEATURE_FLAG_SYSTEM"
replace_key_value featureFlagConfig.syncFeaturesToCF "$SYNC_FEATURES_TO_CF"
