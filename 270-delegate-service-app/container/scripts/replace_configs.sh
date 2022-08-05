#!/usr/bin/env bash
# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

CONFIG_FILE=/opt/harness/delegate-service-config.yml
REDISSON_CACHE_FILE=/opt/harness/redisson-jcache.yaml

replace_key_value () {
  CONFIG_KEY="$1";
  CONFIG_VALUE="$2";
  if [[ "" != "$CONFIG_VALUE" ]]; then
    yq -i '.$CONFIG_KEY="$CONFIG_VALUE"' $CONFIG_FILE
  fi
}

yq 'del(.server.applicationConnectors[0])' $CONFIG_FILE
yq 'del(.grpcServerConfig.connectors[0])' $CONFIG_FILE
yq 'del(.grpcServerClassicConfig.connectors[0])' $CONFIG_FILE


yq -i '.server.adminConnectors="[]"' $CONFIG_FILE

if [[ "" != "$LOGGING_LEVEL" ]]; then
    yq -i '.logging.level="$LOGGING_LEVEL"' $CONFIG_FILE
fi

if [[ "" != "$LOGGERS" ]]; then
  IFS=',' read -ra LOGGER_ITEMS <<< "$LOGGERS"
  for ITEM in "${LOGGER_ITEMS[@]}"; do
    LOGGER=`echo $ITEM | awk -F= '{print $1}'`
    LOGGER_LEVEL=`echo $ITEM | awk -F= '{print $2}'`
    yq -i '.logging.loggers.[$LOGGER]="${LOGGER_LEVEL}"' $CONFIG_FILE
  done
fi

if [[ "" != "$SERVER_PORT" ]]; then
  yq -i '.server.applicationConnectors[0].port="$SERVER_PORT"' $CONFIG_FILE
else
  yq -i '.server.applicationConnectors[0].port="9080"' $CONFIG_FILE
fi

if [[ "" != "$GRPC_SERVER_PORT" ]]; then
  yq -i '.grpcServerConfig.connectors[0].port="$GRPC_SERVER_PORT"' $CONFIG_FILE
fi

if [[ "" != "$GRPC_SERVER_CLASSIC_PORT" ]]; then
  yq -i '.grpcServerClassicConfig.connectors[0].port="$GRPC_SERVER_CLASSIC_PORT"' $CONFIG_FILE
fi

if [[ "" != "$SERVER_MAX_THREADS" ]]; then
  yq -i '.server.maxThreads="$SERVER_MAX_THREADS"' $CONFIG_FILE
fi

if [[ "" != "$UI_SERVER_URL" ]]; then
  yq -i '.portal.url="$UI_SERVER_URL"' $CONFIG_FILE
fi

if [[ "" != "$AUTHTOKENEXPIRYINMILLIS" ]]; then
  yq -i '.portal.authTokenExpiryInMillis="$AUTHTOKENEXPIRYINMILLIS"' $CONFIG_FILE
fi

if [[ "" != "$EXTERNAL_GRAPHQL_RATE_LIMIT" ]]; then
  yq -i '.portal.externalGraphQLRateLimitPerMinute="$EXTERNAL_GRAPHQL_RATE_LIMIT"' $CONFIG_FILE
fi

if [[ "" != "$CUSTOM_DASH_GRAPHQL_RATE_LIMIT" ]]; then
  yq -i '.portal.customDashGraphQLRateLimitPerMinute="$CUSTOM_DASH_GRAPHQL_RATE_LIMIT"' $CONFIG_FILE
fi

if [[ "" != "$ALLOWED_ORIGINS" ]]; then
  yq -i '.portal.allowedOrigins="$ALLOWED_ORIGINS"' $CONFIG_FILE
fi

if [[ "" != "$STORE_REQUEST_PAYLOAD" ]]; then
  yq -i '.auditConfig.storeRequestPayload="$STORE_REQUEST_PAYLOAD"' $CONFIG_FILE
fi

if [[ "" != "$STORE_RESPONSE_PAYLOAD" ]]; then
  yq -i '.auditConfig.storeResponsePayload="$STORE_RESPONSE_PAYLOAD"' $CONFIG_FILE
fi

if [[ "" != "$MONGO_URI" ]]; then
  yq -i '.mongo.uri="${MONGO_URI//\\&/&}"' $CONFIG_FILE
fi

if [[ "" != "$MONGO_TRACE_MODE" ]]; then
  yq -i '.mongo.traceMode="$MONGO_TRACE_MODE"' $CONFIG_FILE
fi

if [[ "" != "$MONGO_SSL_CONFIG" ]]; then
  yq -i '.mongo.mongoSSLConfig.mongoSSLEnabled="$MONGO_SSL_CONFIG"' $CONFIG_FILE
fi

if [[ "" != "$MONGO_SSL_CA_TRUST_STORE_PATH" ]]; then
  yq -i '.mongo.mongoSSLConfig.mongoTrustStorePath="$MONGO_SSL_CA_TRUST_STORE_PATH"' $CONFIG_FILE
fi

if [[ "" != "$MONGO_SSL_CA_TRUST_STORE_PASSWORD" ]]; then
  yq -i '.mongo.mongoSSLConfig.mongoTrustStorePassword="$MONGO_SSL_CA_TRUST_STORE_PASSWORD"' $CONFIG_FILE
fi

if [[ "" != "$MONGO_CONNECT_TIMEOUT" ]]; then
  yq -i '.mongo.connectTimeout="$MONGO_CONNECT_TIMEOUT"' $CONFIG_FILE
fi

if [[ "" != "$MONGO_SERVER_SELECTION_TIMEOUT" ]]; then
  yq -i '.mongo.serverSelectionTimeout="$MONGO_SERVER_SELECTION_TIMEOUT"' $CONFIG_FILE
fi

if [[ "" != "$MAX_CONNECTION_IDLE_TIME" ]]; then
  yq -i '.mongo.maxConnectionIdleTime="$MAX_CONNECTION_IDLE_TIME"' $CONFIG_FILE
fi

if [[ "" != "$MONGO_CONNECTIONS_PER_HOST" ]]; then
  yq -i '.mongo.connectionsPerHost="$MONGO_CONNECTIONS_PER_HOST"' $CONFIG_FILE
fi

if [[ "" != "$MONGO_INDEX_MANAGER_MODE" ]]; then
  yq -i '.mongo.indexManagerMode="$MONGO_INDEX_MANAGER_MODE"' $CONFIG_FILE
fi

if [[ "" != "$EVEMTS_MONGO_INDEX_MANAGER_MODE" ]]; then
  yq -i '.events-mongo.indexManagerMode="$EVEMTS_MONGO_INDEX_MANAGER_MODE"' $CONFIG_FILE
fi

if [[ "" != "$EVENTS_MONGO_URI" ]]; then
  yq -i '.events-mongo.uri="$EVENTS_MONGO_URI"' $CONFIG_FILE
else
  yq 'del(.events-mongo)' $CONFIG_FILE
fi

if [[ "" != "$CF_CLIENT_API_KEY" ]]; then
  yq -i '.cfClientConfig.apiKey="$CF_CLIENT_API_KEY"' $CONFIG_FILE
fi

if [[ "" != "$CF_CLIENT_CONFIG_URL" ]]; then
  yq -i '.cfClientConfig.configUrl="$CF_CLIENT_CONFIG_URL"' $CONFIG_FILE
fi

if [[ "" != "$CF_CLIENT_EVENT_URL" ]]; then
  yq -i '.cfClientConfig.eventUrl="$CF_CLIENT_EVENT_URL"' $CONFIG_FILE
fi

if [[ "" != "$CF_CLIENT_ANALYTICS_ENABLED" ]]; then
  yq -i '.cfClientConfig.analyticsEnabled="$CF_CLIENT_ANALYTICS_ENABLED"' $CONFIG_FILE
fi

if [[ "" != "$CF_CLIENT_CONNECTION_TIMEOUT" ]]; then
  yq -i '.cfClientConfig.connectionTimeout="$CF_CLIENT_CONNECTION_TIMEOUT"' $CONFIG_FILE
fi

if [[ "" != "$CF_CLIENT_READ_TIMEOUT" ]]; then
  yq -i '.cfClientConfig.readTimeout="$CF_CLIENT_READ_TIMEOUT"' $CONFIG_FILE
fi

if [[ "" != "$CF_CLIENT_BUFFER_SIZE" ]]; then
  yq -i '.cfClientConfig.bufferSize="$CF_CLIENT_BUFFER_SIZE"' $CONFIG_FILE
fi

if [[ "" != "$CF_MIGRATION_ENABLED" ]]; then
  yq -i '.cfMigrationConfig.enabled="$CF_MIGRATION_ENABLED"' $CONFIG_FILE
fi

if [[ "" != "$CF_MIGRATION_ADMIN_URL" ]]; then
  yq -i '.cfMigrationConfig.adminUrl="$CF_MIGRATION_ADMIN_URL"' $CONFIG_FILE
fi

if [[ "" != "$CF_MIGRATION_API_KEY" ]]; then
  yq -i '.cfMigrationConfig.apiKey="$CF_MIGRATION_API_KEY"' $CONFIG_FILE
fi

if [[ "" != "$CF_MIGRATION_ACCOUNT" ]]; then
  yq -i '.cfMigrationConfig.account="$CF_MIGRATION_ACCOUNT"' $CONFIG_FILE
fi

if [[ "" != "$CF_MIGRATION_ORG" ]]; then
  yq -i '.cfMigrationConfig.org="$CF_MIGRATION_ORG"' $CONFIG_FILE
fi

if [[ "" != "$CF_MIGRATION_PROJECT" ]]; then
  yq -i '.cfMigrationConfig.project="$CF_MIGRATION_PROJECT"' $CONFIG_FILE
fi

if [[ "" != "$CF_MIGRATION_ENVIRONMENT" ]]; then
  yq -i '.cfMigrationConfig.environment="$CF_MIGRATION_ENVIRONMENT"' $CONFIG_FILE
fi

replace_key_value featureFlagConfig.featureFlagSystem "$FEATURE_FLAG_SYSTEM"
replace_key_value featureFlagConfig.syncFeaturesToCF "$SYNC_FEATURES_TO_CF"


if [[ "" != "$MONGO_LOCK_URI" ]]; then
  yq -i '.mongo.locksUri="${MONGO_LOCK_URI//\\&/&}"' $CONFIG_FILE
fi

yq -i '.server.requestLog.appenders[0].threshold="TRACE"' $CONFIG_FILE

if [[ "$STACK_DRIVER_LOGGING_ENABLED" == "true" ]]; then
  yq 'del(.logging.appenders[2])' $CONFIG_FILE
  yq 'del(.logging.appenders[0])' $CONFIG_FILE
  yq -i '.logging.appenders[0].stackdriverLogEnabled="true"' $CONFIG_FILE
else
  if [[ "$ROLLING_FILE_LOGGING_ENABLED" == "true" ]]; then
    yq 'del(.logging.appenders[1])' $CONFIG_FILE
    yq -i '.logging.appenders[1].currentLogFilename="/opt/harness/logs/delegate-service.log"' $CONFIG_FILE
    yq -i '.logging.appenders[1].archivedLogFilenamePattern="/opt/harness/logs/delegate-service.%d.%i.log"' $CONFIG_FILE
  else
    yq 'del(.logging.appenders[2])' $CONFIG_FILE
    yq 'del(.logging.appenders[1])' $CONFIG_FILE
  fi
fi

if [[ "" != "$WATCHER_METADATA_URL" ]]; then
  yq -i '.watcherMetadataUrl="$WATCHER_METADATA_URL"' $CONFIG_FILE
fi

if [[ "" != "$DELEGATE_METADATA_URL" ]]; then
  yq -i '.delegateMetadataUrl="$DELEGATE_METADATA_URL"' $CONFIG_FILE
fi

if [[ "" != "$API_URL" ]]; then
  yq -i '.apiUrl="$API_URL"' $CONFIG_FILE
fi

if [[ "" != "$ENV_PATH" ]]; then
  yq -i '.envPath="$ENV_PATH"' $CONFIG_FILE
fi

if [[ "" != "$DEPLOY_MODE" ]]; then
  yq -i '.deployMode="$DEPLOY_MODE"' $CONFIG_FILE
fi

if [[ "" != "$jwtPasswordSecret" ]]; then
  yq -i '.portal.jwtPasswordSecret="$jwtPasswordSecret"' $CONFIG_FILE
fi

if [[ "" != "$jwtExternalServiceSecret" ]]; then
  yq -i '.portal.jwtExternalServiceSecret="$jwtExternalServiceSecret"' $CONFIG_FILE
fi

if [[ "" != "$jwtZendeskSecret" ]]; then
  yq -i '.portal.jwtZendeskSecret="$jwtZendeskSecret"' $CONFIG_FILE
fi

if [[ "" != "$jwtMultiAuthSecret" ]]; then
  yq -i '.portal.jwtMultiAuthSecret="$jwtMultiAuthSecret"' $CONFIG_FILE
fi

if [[ "" != "$jwtSsoRedirectSecret" ]]; then
  yq -i '.portal.jwtSsoRedirectSecret="$jwtSsoRedirectSecret"' $CONFIG_FILE
fi

if [[ "" != "$jwtAuthSecret" ]]; then
  yq -i '.portal.jwtAuthSecret="$jwtAuthSecret"' $CONFIG_FILE
fi

if [[ "" != "$jwtMarketPlaceSecret" ]]; then
  yq -i '.portal.jwtMarketPlaceSecret="$jwtMarketPlaceSecret"' $CONFIG_FILE
fi

if [[ "" != "$jwtIdentityServiceSecret" ]]; then
  yq -i '.portal.jwtIdentityServiceSecret="$jwtIdentityServiceSecret"' $CONFIG_FILE
fi

if [[ "" != "$jwtDataHandlerSecret" ]]; then
  yq -i '.portal.jwtDataHandlerSecret="$jwtDataHandlerSecret"' $CONFIG_FILE
fi

if [[ "" != "$jwtNextGenManagerSecret" ]]; then
  yq -i '.portal.jwtNextGenManagerSecret="$jwtNextGenManagerSecret"' $CONFIG_FILE
fi

if [[ "" != "$DELEGATE_DOCKER_IMAGE" ]]; then
  yq -i '.portal.delegateDockerImage="$DELEGATE_DOCKER_IMAGE"' $CONFIG_FILE
fi

if [[ "" != "$OPTIONAL_DELEGATE_TASK_REJECT_AT_LIMIT" ]]; then
  yq -i '.portal.optionalDelegateTaskRejectAtLimit="$OPTIONAL_DELEGATE_TASK_REJECT_AT_LIMIT"' $CONFIG_FILE
fi

if [[ "" != "$BACKGROUND_SCHEDULER_CLUSTERED" ]]; then
  yq -i '.backgroundScheduler.clustered="$BACKGROUND_SCHEDULER_CLUSTERED"' $CONFIG_FILE
fi

if [[ "" != "$ENABLE_CRONS" ]]; then
  yq -i '.enableIterators="$ENABLE_CRONS"' $CONFIG_FILE
  yq -i '.backgroundScheduler.enabled="$ENABLE_CRONS"' $CONFIG_FILE
  yq -i '.serviceScheduler.enabled="$ENABLE_CRONS"' $CONFIG_FILE
fi

if [[ "" != "$WORKERS" ]]; then
  IFS=',' read -ra WORKER_ITEMS <<< "$WORKERS"
  for ITEM in "${WORKER_ITEMS[@]}"; do
    WORKER=`echo $ITEM | awk -F= '{print $1}'`
    WORKER_FLAG=`echo $ITEM | awk -F= '{print $2}'`
    yq -i '.workers.active.[$WORKER]="${WORKER_FLAG}"' $CONFIG_FILE
  done
fi

if [[ "" != "$PUBLISHERS" ]]; then
  IFS=',' read -ra PUBLISHER_ITEMS <<< "$PUBLISHERS"
  for ITEM in "${PUBLISHER_ITEMS[@]}"; do
    PUBLISHER=`echo $ITEM | awk -F= '{print $1}'`
    PUBLISHER_FLAG=`echo $ITEM | awk -F= '{print $2}'`
    yq -i '.publishers.active.[$PUBLISHER]="${PUBLISHER_FLAG}"' $CONFIG_FILE
  done
fi

if [[ "" != "$DISTRIBUTED_LOCK_IMPLEMENTATION" ]]; then
  yq -i '.distributedLockImplementation="$DISTRIBUTED_LOCK_IMPLEMENTATION"' $CONFIG_FILE
fi

if [[ "" != "$ATMOSPHERE_BACKEND" ]]; then
  yq -i '.atmosphereBroadcaster="$ATMOSPHERE_BACKEND"' $CONFIG_FILE
fi

yq 'del(.codec)' $REDISSON_CACHE_FILE

if [[ "" != "$REDIS_URL" ]]; then
  yq -i '.redisLockConfig.redisUrl="$REDIS_URL"' $CONFIG_FILE
  yq -i '.redisAtmosphereConfig.redisUrl="$REDIS_URL"' $CONFIG_FILE
  yq -i '.singleServerConfig.address="$REDIS_URL"' $REDISSON_CACHE_FILE
fi

if [[ "$REDIS_SENTINEL" == "true" ]]; then
  yq -i '.redisLockConfig.sentinel="true"' $CONFIG_FILE
  yq -i '.redisAtmosphereConfig.sentinel="true"' $CONFIG_FILE
  yq 'del(.singleServerConfig)' $REDISSON_CACHE_FILE
fi

if [[ "" != "$REDIS_MASTER_NAME" ]]; then
  yq -i '.redisLockConfig.masterName="$REDIS_MASTER_NAME"' $CONFIG_FILE
  yq -i '.redisAtmosphereConfig.masterName="$REDIS_MASTER_NAME"' $CONFIG_FILE
  yq -i '.sentinelServersConfig.masterName="$REDIS_MASTER_NAME"' $REDISSON_CACHE_FILE
fi

if [[ "" != "$REDIS_SENTINELS" ]]; then
  IFS=',' read -ra REDIS_SENTINEL_URLS <<< "$REDIS_SENTINELS"
  INDEX=0
  for REDIS_SENTINEL_URL in "${REDIS_SENTINEL_URLS[@]}"; do
    yq -i '.redisLockConfig.sentinelUrls.[$INDEX]="${REDIS_SENTINEL_URL}"' $CONFIG_FILE
    yq -i '.redisAtmosphereConfig.sentinelUrls.[$INDEX]="${REDIS_SENTINEL_URL}"' $CONFIG_FILE
    yq -i '.sentinelServersConfig.sentinelAddresses.[+]="${REDIS_SENTINEL_URL}"' $REDISSON_CACHE_FILE
    INDEX=$(expr $INDEX + 1)
  done
fi

if [[ "" != "$REDIS_ENV_NAMESPACE" ]]; then
    yq -i '.redisLockConfig.envNamespace="$REDIS_ENV_NAMESPACE"' $CONFIG_FILE
    yq -i '.redisAtmosphereConfig.envNamespace="$REDIS_ENV_NAMESPACE"' $CONFIG_FILE
fi

if [[ "" != "$REDIS_NETTY_THREADS" ]]; then
  yq -i '.redisLockConfig.nettyThreads="$REDIS_NETTY_THREADS"' $CONFIG_FILE
  yq -i '.redisAtmosphereConfig.nettyThreads="$REDIS_NETTY_THREADS"' $CONFIG_FILE
  yq -i '.nettyThreads="$REDIS_NETTY_THREADS"' $REDISSON_CACHE_FILE
fi

if [[ "$REDIS_SCRIPT_CACHE" == "false" ]]; then
  yq -i '.redisLockConfig.useScriptCache="false"' $CONFIG_FILE
  yq -i '.redisAtmosphereConfig.useScriptCache="false"' $CONFIG_FILE
  yq -i '.useScriptCache="false"' $REDISSON_CACHE_FILE
fi

if [[ "" != "$CACHE_NAMESPACE" ]]; then
    yq -i '.cacheConfig.cacheNamespace="$CACHE_NAMESPACE"' $CONFIG_FILE
fi

if [[ "" != "$CACHE_BACKEND" ]]; then
    yq -i '.cacheConfig.cacheBackend="$CACHE_BACKEND"' $CONFIG_FILE
fi

if [[ "" != "$DELEGATE_SERVICE_TARGET" ]]; then
  yq -i '.grpcDelegateServiceClientConfig.target="$DELEGATE_SERVICE_TARGET"' $CONFIG_FILE
fi

if [[ "" != "$DELEGATE_SERVICE_AUTHORITY" ]]; then
  yq -i '.grpcDelegateServiceClientConfig.authority="$DELEGATE_SERVICE_AUTHORITY"' $CONFIG_FILE
fi

if [[ "" != "$LOG_STREAMING_SERVICE_BASEURL" ]]; then
  yq -i '.logStreamingServiceConfig.baseUrl="$LOG_STREAMING_SERVICE_BASEURL"' $CONFIG_FILE
fi

if [[ "" != "$LOG_STREAMING_SERVICE_TOKEN" ]]; then
  yq -i '.logStreamingServiceConfig.serviceToken="$LOG_STREAMING_SERVICE_TOKEN"' $CONFIG_FILE
fi

if [[ "" != "$ACCESS_CONTROL_ENABLED" ]]; then
  yq -i '.accessControlClient.enableAccessControl="$ACCESS_CONTROL_ENABLED"' $CONFIG_FILE
fi

if [[ "" != "$ACCESS_CONTROL_BASE_URL" ]]; then
  yq -i '.accessControlClient.accessControlServiceConfig.baseUrl="$ACCESS_CONTROL_BASE_URL"' $CONFIG_FILE
fi

if [[ "" != "$ACCESS_CONTROL_SECRET" ]]; then
  yq -i '.accessControlClient.accessControlServiceSecret="$ACCESS_CONTROL_SECRET"' $CONFIG_FILE
fi

if [[ "" != "$ENABLE_AUDIT" ]]; then
  yq -i '.enableAudit="$ENABLE_AUDIT"' $CONFIG_FILE
fi

if [[ "" != "$EVENTS_FRAMEWORK_REDIS_SENTINELS" ]]; then
  IFS=',' read -ra SENTINEL_URLS <<< "$EVENTS_FRAMEWORK_REDIS_SENTINELS"
  INDEX=0
  for REDIS_SENTINEL_URL in "${SENTINEL_URLS[@]}"; do
    yq -i '.eventsFramework.redis.sentinelUrls.[$INDEX]="${REDIS_SENTINEL_URL}"' $CONFIG_FILE
    INDEX=$(expr $INDEX + 1)
  done
fi

replace_key_value eventsFramework.redis.sentinel $EVENTS_FRAMEWORK_USE_SENTINEL
replace_key_value eventsFramework.redis.envNamespace $EVENTS_FRAMEWORK_ENV_NAMESPACE
replace_key_value eventsFramework.redis.redisUrl $EVENTS_FRAMEWORK_REDIS_URL
replace_key_value eventsFramework.redis.masterName $EVENTS_FRAMEWORK_SENTINEL_MASTER_NAME
replace_key_value eventsFramework.redis.userName $EVENTS_FRAMEWORK_REDIS_USERNAME
replace_key_value eventsFramework.redis.password $EVENTS_FRAMEWORK_REDIS_PASSWORD
replace_key_value eventsFramework.redis.sslConfig.enabled $EVENTS_FRAMEWORK_REDIS_SSL_ENABLED
replace_key_value eventsFramework.redis.sslConfig.CATrustStorePath $EVENTS_FRAMEWORK_REDIS_SSL_CA_TRUST_STORE_PATH
replace_key_value eventsFramework.redis.sslConfig.CATrustStorePassword $EVENTS_FRAMEWORK_REDIS_SSL_CA_TRUST_STORE_PASSWORD

if [[ "" != "$NG_MANAGER_BASE_URL" ]]; then
  yq -i '.ngManagerServiceHttpClientConfig.baseUrl="$NG_MANAGER_BASE_URL"' $CONFIG_FILE
fi

if [[ "" != "$ENABLE_USER_CHANGESTREAM" ]]; then
  yq -i '.userChangeStreamEnabled="$ENABLE_USER_CHANGESTREAM"' $CONFIG_FILE
fi

if [[ "" != "$DELEGATE_SERVICE_SECRET" ]]; then
  yq -i '.dmsSecret="$DELEGATE_SERVICE_SECRET"' $CONFIG_FILE
fi

if [[ "" != "$CDN_URL" ]]; then
  yq -i '.cdnConfig.url="$CDN_URL"' $CONFIG_FILE
fi

if [[ "" != "$CDN_KEY" ]]; then
  yq -i '.cdnConfig.keyName="$CDN_KEY"' $CONFIG_FILE
fi

if [[ "" != "$CDN_KEY_SECRET" ]]; then
  yq -i '.cdnConfig.keySecret="$CDN_KEY_SECRET"' $CONFIG_FILE
fi

if [[ "" != "$CDN_DELEGATE_JAR_PATH" ]]; then
  yq -i '.cdnConfig.delegateJarPath="$CDN_DELEGATE_JAR_PATH"' $CONFIG_FILE
fi

if [[ "" != "$CDN_WATCHER_JAR_BASE_PATH" ]]; then
  yq -i '.cdnConfig.watcherJarBasePath="$CDN_WATCHER_JAR_BASE_PATH"' $CONFIG_FILE
fi

if [[ "" != "$CDN_WATCHER_JAR_PATH" ]]; then
  yq -i '.cdnConfig.watcherJarPath="$CDN_WATCHER_JAR_PATH"' $CONFIG_FILE
fi

if [[ "" != "$CDN_WATCHER_METADATA_FILE_PATH" ]]; then
  yq -i '.cdnConfig.watcherMetaDataFilePath="$CDN_WATCHER_METADATA_FILE_PATH"' $CONFIG_FILE
fi

if [[ "" != "$CDN_ORACLE_JRE_TAR_PATH" ]]; then
  yq -i '.cdnConfig.cdnJreTarPaths.oracle8u191="$CDN_ORACLE_JRE_TAR_PATH"' $CONFIG_FILE
fi

if [[ "" != "$CDN_OPENJDK_JRE_TAR_PATH" ]]; then
  yq -i '.cdnConfig.cdnJreTarPaths.openjdk8u242="$CDN_OPENJDK_JRE_TAR_PATH"' $CONFIG_FILE
fi

if [[ "" != "$CURRENT_JRE" ]]; then
  yq -i '.currentJre="$CURRENT_JRE"' $CONFIG_FILE
fi

if [[ "" != "$MIGRATE_TO_JRE" ]]; then
  yq -i '.migrateToJre="$MIGRATE_TO_JRE"' $CONFIG_FILE
fi

if [[ "" != "$ORACLE_JRE_TAR_PATH" ]]; then
  yq -i '.jreConfigs.oracle8u191.jreTarPath="$ORACLE_JRE_TAR_PATH"' $CONFIG_FILE
fi

if [[ "" != "$OPENJDK_JRE_TAR_PATH" ]]; then
  yq -i '.jreConfigs.openjdk8u242.jreTarPath="$OPENJDK_JRE_TAR_PATH"' $CONFIG_FILE
fi

if [[ "" != "$SEGMENT_ENABLED" ]]; then
  yq -i '.segmentConfig.enabled="$SEGMENT_ENABLED"' $CONFIG_FILE
fi

if [[ "" != "$SEGMENT_URL" ]]; then
  yq -i '.segmentConfig.url="$SEGMENT_URL"' $CONFIG_FILE
fi

if [[ "" != "$SEGMENT_APIKEY" ]]; then
  yq -i '.segmentConfig.apiKey="$SEGMENT_APIKEY"' $CONFIG_FILE
fi

#segmentConfiguration is for telemetry framework
if [[ "" != "$SEGMENT_ENABLED_NG" ]]; then
  yq -i '.segmentConfiguration.enabled="$SEGMENT_ENABLED_NG"' $CONFIG_FILE
fi

if [[ "" != "$SEGMENT_URL_NG" ]]; then
  yq -i '.segmentConfiguration.url="$SEGMENT_URL_NG"' $CONFIG_FILE
fi

if [[ "" != "$SEGMENT_APIKEY_NG" ]]; then
  yq -i '.segmentConfiguration.apiKey="$SEGMENT_APIKEY_NG"' $CONFIG_FILE
fi
