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
    yq write -i $CONFIG_FILE $CONFIG_KEY $CONFIG_VALUE
  fi
}

yq delete -i $CONFIG_FILE 'server.applicationConnectors.(type==h2)'
yq delete -i $CONFIG_FILE 'grpcServerConfig.connectors.(secure==true)'
yq delete -i $CONFIG_FILE 'grpcServerClassicConfig.connectors.(secure==true)'


yq write -i $CONFIG_FILE server.adminConnectors "[]"

if [[ "" != "$LOGGING_LEVEL" ]]; then
    yq write -i $CONFIG_FILE logging.level "$LOGGING_LEVEL"
fi

if [[ "" != "$LOGGERS" ]]; then
  IFS=',' read -ra LOGGER_ITEMS <<< "$LOGGERS"
  for ITEM in "${LOGGER_ITEMS[@]}"; do
    LOGGER=`echo $ITEM | awk -F= '{print $1}'`
    LOGGER_LEVEL=`echo $ITEM | awk -F= '{print $2}'`
    yq write -i $CONFIG_FILE logging.loggers.[$LOGGER] "${LOGGER_LEVEL}"
  done
fi

if [[ "" != "$SERVER_PORT" ]]; then
  yq write -i $CONFIG_FILE server.applicationConnectors[0].port "$SERVER_PORT"
else
  yq write -i $CONFIG_FILE server.applicationConnectors[0].port "9080"
fi

if [[ "" != "$GRPC_SERVER_PORT" ]]; then
  yq write -i $CONFIG_FILE grpcServerConfig.connectors[0].port "$GRPC_SERVER_PORT"
fi

if [[ "" != "$GRPC_SERVER_CLASSIC_PORT" ]]; then
  yq write -i $CONFIG_FILE grpcServerClassicConfig.connectors[0].port "$GRPC_SERVER_CLASSIC_PORT"
fi

if [[ "" != "$SERVER_MAX_THREADS" ]]; then
  yq write -i $CONFIG_FILE server.maxThreads "$SERVER_MAX_THREADS"
fi

if [[ "" != "$UI_SERVER_URL" ]]; then
  yq write -i $CONFIG_FILE portal.url "$UI_SERVER_URL"
fi

if [[ "" != "$AUTHTOKENEXPIRYINMILLIS" ]]; then
  yq write -i $CONFIG_FILE portal.authTokenExpiryInMillis "$AUTHTOKENEXPIRYINMILLIS"
fi

if [[ "" != "$EXTERNAL_GRAPHQL_RATE_LIMIT" ]]; then
  yq write -i $CONFIG_FILE portal.externalGraphQLRateLimitPerMinute "$EXTERNAL_GRAPHQL_RATE_LIMIT"
fi

if [[ "" != "$CUSTOM_DASH_GRAPHQL_RATE_LIMIT" ]]; then
  yq write -i $CONFIG_FILE portal.customDashGraphQLRateLimitPerMinute "$CUSTOM_DASH_GRAPHQL_RATE_LIMIT"
fi

if [[ "" != "$ALLOWED_ORIGINS" ]]; then
  yq write -i $CONFIG_FILE portal.allowedOrigins "$ALLOWED_ORIGINS"
fi

if [[ "" != "$STORE_REQUEST_PAYLOAD" ]]; then
  yq write -i $CONFIG_FILE auditConfig.storeRequestPayload "$STORE_REQUEST_PAYLOAD"
fi

if [[ "" != "$STORE_RESPONSE_PAYLOAD" ]]; then
  yq write -i $CONFIG_FILE auditConfig.storeResponsePayload "$STORE_RESPONSE_PAYLOAD"
fi

if [[ "" != "$MONGO_URI" ]]; then
  yq write -i $CONFIG_FILE mongo.uri "${MONGO_URI//\\&/&}"
fi

if [[ "" != "$MONGO_TRACE_MODE" ]]; then
  yq write -i $CONFIG_FILE mongo.traceMode $MONGO_TRACE_MODE
fi

if [[ "" != "$MONGO_SSL_CONFIG" ]]; then
  yq write -i $CONFIG_FILE mongo.mongoSSLConfig.mongoSSLEnabled "$MONGO_SSL_CONFIG"
fi

if [[ "" != "$MONGO_SSL_CA_TRUST_STORE_PATH" ]]; then
  yq write -i $CONFIG_FILE mongo.mongoSSLConfig.mongoTrustStorePath "$MONGO_SSL_CA_TRUST_STORE_PATH"
fi

if [[ "" != "$MONGO_SSL_CA_TRUST_STORE_PASSWORD" ]]; then
  yq write -i $CONFIG_FILE mongo.mongoSSLConfig.mongoTrustStorePassword "$MONGO_SSL_CA_TRUST_STORE_PASSWORD"
fi

if [[ "" != "$MONGO_CONNECT_TIMEOUT" ]]; then
  yq write -i $CONFIG_FILE mongo.connectTimeout $MONGO_CONNECT_TIMEOUT
fi

if [[ "" != "$MONGO_SERVER_SELECTION_TIMEOUT" ]]; then
  yq write -i $CONFIG_FILE mongo.serverSelectionTimeout $MONGO_SERVER_SELECTION_TIMEOUT
fi

if [[ "" != "$MAX_CONNECTION_IDLE_TIME" ]]; then
  yq write -i $CONFIG_FILE mongo.maxConnectionIdleTime $MAX_CONNECTION_IDLE_TIME
fi

if [[ "" != "$MONGO_CONNECTIONS_PER_HOST" ]]; then
  yq write -i $CONFIG_FILE mongo.connectionsPerHost $MONGO_CONNECTIONS_PER_HOST
fi

if [[ "" != "$MONGO_INDEX_MANAGER_MODE" ]]; then
  yq write -i $CONFIG_FILE mongo.indexManagerMode $MONGO_INDEX_MANAGER_MODE
fi

if [[ "" != "$EVEMTS_MONGO_INDEX_MANAGER_MODE" ]]; then
  yq write -i $CONFIG_FILE events-mongo.indexManagerMode $EVEMTS_MONGO_INDEX_MANAGER_MODE
fi

if [[ "" != "$EVENTS_MONGO_URI" ]]; then
  yq write -i $CONFIG_FILE events-mongo.uri "$EVENTS_MONGO_URI"
else
  yq delete -i $CONFIG_FILE events-mongo
fi

if [[ "" != "$CF_CLIENT_API_KEY" ]]; then
  yq write -i $CONFIG_FILE cfClientConfig.apiKey "$CF_CLIENT_API_KEY"
fi

if [[ "" != "$CF_CLIENT_CONFIG_URL" ]]; then
  yq write -i $CONFIG_FILE cfClientConfig.configUrl "$CF_CLIENT_CONFIG_URL"
fi

if [[ "" != "$CF_CLIENT_BUFFER_SIZE" ]]; then
  yq write -i $CONFIG_FILE cfClientConfig.bufferSize "$CF_CLIENT_BUFFER_SIZE"
fi

if [[ "" != "$CF_CLIENT_EVENT_URL" ]]; then
  yq write -i $CONFIG_FILE cfClientConfig.eventUrl "$CF_CLIENT_EVENT_URL"
fi

if [[ "" != "$CF_CLIENT_ANALYTICS_ENABLED" ]]; then
  yq write -i $CONFIG_FILE cfClientConfig.analyticsEnabled "$CF_CLIENT_ANALYTICS_ENABLED"
fi

if [[ "" != "$CF_CLIENT_CONNECTION_TIMEOUT" ]]; then
  yq write -i $CONFIG_FILE cfClientConfig.connectionTimeout "$CF_CLIENT_CONNECTION_TIMEOUT"
fi

if [[ "" != "$CF_CLIENT_READ_TIMEOUT" ]]; then
  yq write -i $CONFIG_FILE cfClientConfig.readTimeout "$CF_CLIENT_READ_TIMEOUT"
fi

if [[ "" != "$CF_MIGRATION_ENABLED" ]]; then
  yq write -i $CONFIG_FILE cfMigrationConfig.enabled "$CF_MIGRATION_ENABLED"
fi

if [[ "" != "$CF_MIGRATION_ADMIN_URL" ]]; then
  yq write -i $CONFIG_FILE cfMigrationConfig.adminUrl "$CF_MIGRATION_ADMIN_URL"
fi

if [[ "" != "$CF_MIGRATION_API_KEY" ]]; then
  yq write -i $CONFIG_FILE cfMigrationConfig.apiKey "$CF_MIGRATION_API_KEY"
fi

if [[ "" != "$CF_MIGRATION_ACCOUNT" ]]; then
  yq write -i $CONFIG_FILE cfMigrationConfig.account "$CF_MIGRATION_ACCOUNT"
fi

if [[ "" != "$CF_MIGRATION_ORG" ]]; then
  yq write -i $CONFIG_FILE cfMigrationConfig.org "$CF_MIGRATION_ORG"
fi

if [[ "" != "$CF_MIGRATION_PROJECT" ]]; then
  yq write -i $CONFIG_FILE cfMigrationConfig.project "$CF_MIGRATION_PROJECT"
fi

if [[ "" != "$CF_MIGRATION_ENVIRONMENT" ]]; then
  yq write -i $CONFIG_FILE cfMigrationConfig.environment "$CF_MIGRATION_ENVIRONMENT"
fi

replace_key_value featureFlagConfig.featureFlagSystem "$FEATURE_FLAG_SYSTEM"
replace_key_value featureFlagConfig.syncFeaturesToCF "$SYNC_FEATURES_TO_CF"


if [[ "" != "$MONGO_LOCK_URI" ]]; then
  yq write -i $CONFIG_FILE mongo.locksUri "${MONGO_LOCK_URI//\\&/&}"
fi

yq write -i $CONFIG_FILE server.requestLog.appenders[0].threshold "TRACE"

if [[ "$STACK_DRIVER_LOGGING_ENABLED" == "true" ]]; then
  yq delete -i $CONFIG_FILE 'logging.appenders.(type==file)'
  yq delete -i $CONFIG_FILE 'logging.appenders.(type==console)'
  yq write -i $CONFIG_FILE 'logging.appenders.(type==gke-console).stackdriverLogEnabled' "true"
else
  if [[ "$ROLLING_FILE_LOGGING_ENABLED" == "true" ]]; then
    yq delete -i $CONFIG_FILE 'logging.appenders.(type==gke-console)'
    yq write -i $CONFIG_FILE 'logging.appenders.(type==file).currentLogFilename' "/opt/harness/logs/delegate-service.log"
    yq write -i $CONFIG_FILE 'logging.appenders.(type==file).archivedLogFilenamePattern' "/opt/harness/logs/delegate-service.%d.%i.log"
  else
    yq delete -i $CONFIG_FILE 'logging.appenders.(type==file)'
    yq delete -i $CONFIG_FILE 'logging.appenders.(type==gke-console)'
  fi
fi

if [[ "" != "$WATCHER_METADATA_URL" ]]; then
  yq write -i $CONFIG_FILE watcherMetadataUrl "$WATCHER_METADATA_URL"
fi

if [[ "" != "$DELEGATE_METADATA_URL" ]]; then
  yq write -i $CONFIG_FILE delegateMetadataUrl "$DELEGATE_METADATA_URL"
fi

if [[ "" != "$API_URL" ]]; then
  yq write -i $CONFIG_FILE apiUrl "$API_URL"
fi

if [[ "" != "$ENV_PATH" ]]; then
  yq write -i $CONFIG_FILE envPath "$ENV_PATH"
fi

if [[ "" != "$DEPLOY_MODE" ]]; then
  yq write -i $CONFIG_FILE deployMode "$DEPLOY_MODE"
fi


if [[ "" != "$jwtPasswordSecret" ]]; then
  yq write -i $CONFIG_FILE portal.jwtPasswordSecret "$jwtPasswordSecret"
fi

if [[ "" != "$jwtExternalServiceSecret" ]]; then
  yq write -i $CONFIG_FILE portal.jwtExternalServiceSecret "$jwtExternalServiceSecret"
fi

if [[ "" != "$jwtZendeskSecret" ]]; then
  yq write -i $CONFIG_FILE portal.jwtZendeskSecret "$jwtZendeskSecret"
fi

if [[ "" != "$jwtMultiAuthSecret" ]]; then
  yq write -i $CONFIG_FILE portal.jwtMultiAuthSecret "$jwtMultiAuthSecret"
fi

if [[ "" != "$jwtSsoRedirectSecret" ]]; then
  yq write -i $CONFIG_FILE portal.jwtSsoRedirectSecret "$jwtSsoRedirectSecret"
fi

if [[ "" != "$jwtAuthSecret" ]]; then
  yq write -i $CONFIG_FILE portal.jwtAuthSecret "$jwtAuthSecret"
fi

if [[ "" != "$jwtMarketPlaceSecret" ]]; then
  yq write -i $CONFIG_FILE portal.jwtMarketPlaceSecret "$jwtMarketPlaceSecret"
fi

if [[ "" != "$jwtIdentityServiceSecret" ]]; then
  yq write -i $CONFIG_FILE portal.jwtIdentityServiceSecret "$jwtIdentityServiceSecret"
fi

if [[ "" != "$jwtDataHandlerSecret" ]]; then
  yq write -i $CONFIG_FILE portal.jwtDataHandlerSecret "$jwtDataHandlerSecret"
fi

if [[ "" != "$jwtNextGenManagerSecret" ]]; then
  yq write -i $CONFIG_FILE portal.jwtNextGenManagerSecret "$jwtNextGenManagerSecret"
fi

if [[ "" != "$DELEGATE_DOCKER_IMAGE" ]]; then
  yq write -i $CONFIG_FILE portal.delegateDockerImage "$DELEGATE_DOCKER_IMAGE"
fi

if [[ "" != "$OPTIONAL_DELEGATE_TASK_REJECT_AT_LIMIT" ]]; then
  yq write -i $CONFIG_FILE portal.optionalDelegateTaskRejectAtLimit "$OPTIONAL_DELEGATE_TASK_REJECT_AT_LIMIT"
fi

if [[ "" != "$BACKGROUND_SCHEDULER_CLUSTERED" ]]; then
  yq write -i $CONFIG_FILE backgroundScheduler.clustered "$BACKGROUND_SCHEDULER_CLUSTERED"
fi

if [[ "" != "$ENABLE_CRONS" ]]; then
  yq write -i $CONFIG_FILE enableIterators "$ENABLE_CRONS"
  yq write -i $CONFIG_FILE backgroundScheduler.enabled "$ENABLE_CRONS"
  yq write -i $CONFIG_FILE serviceScheduler.enabled "$ENABLE_CRONS"
fi

if [[ "" != "$WORKERS" ]]; then
  IFS=',' read -ra WORKER_ITEMS <<< "$WORKERS"
  for ITEM in "${WORKER_ITEMS[@]}"; do
    WORKER=`echo $ITEM | awk -F= '{print $1}'`
    WORKER_FLAG=`echo $ITEM | awk -F= '{print $2}'`
    yq write -i $CONFIG_FILE workers.active.[$WORKER] "${WORKER_FLAG}"
  done
fi

if [[ "" != "$PUBLISHERS" ]]; then
  IFS=',' read -ra PUBLISHER_ITEMS <<< "$PUBLISHERS"
  for ITEM in "${PUBLISHER_ITEMS[@]}"; do
    PUBLISHER=`echo $ITEM | awk -F= '{print $1}'`
    PUBLISHER_FLAG=`echo $ITEM | awk -F= '{print $2}'`
    yq write -i $CONFIG_FILE publishers.active.[$PUBLISHER] "${PUBLISHER_FLAG}"
  done
fi

if [[ "" != "$DISTRIBUTED_LOCK_IMPLEMENTATION" ]]; then
  yq write -i $CONFIG_FILE distributedLockImplementation "$DISTRIBUTED_LOCK_IMPLEMENTATION"
fi

if [[ "" != "$ATMOSPHERE_BACKEND" ]]; then
  yq write -i $CONFIG_FILE atmosphereBroadcaster "$ATMOSPHERE_BACKEND"
fi

yq delete -i $REDISSON_CACHE_FILE codec

if [[ "" != "$REDIS_URL" ]]; then
  yq write -i $CONFIG_FILE redisLockConfig.redisUrl "$REDIS_URL"
  yq write -i $CONFIG_FILE redisAtmosphereConfig.redisUrl "$REDIS_URL"
  yq write -i $REDISSON_CACHE_FILE singleServerConfig.address "$REDIS_URL"
fi

if [[ "$REDIS_SENTINEL" == "true" ]]; then
  yq write -i $CONFIG_FILE redisLockConfig.sentinel true
  yq write -i $CONFIG_FILE redisAtmosphereConfig.sentinel true
  yq delete -i $REDISSON_CACHE_FILE singleServerConfig
fi

if [[ "" != "$REDIS_MASTER_NAME" ]]; then
  yq write -i $CONFIG_FILE redisLockConfig.masterName "$REDIS_MASTER_NAME"
  yq write -i $CONFIG_FILE redisAtmosphereConfig.masterName "$REDIS_MASTER_NAME"
  yq write -i $REDISSON_CACHE_FILE sentinelServersConfig.masterName "$REDIS_MASTER_NAME"
fi

if [[ "" != "$REDIS_SENTINELS" ]]; then
  IFS=',' read -ra REDIS_SENTINEL_URLS <<< "$REDIS_SENTINELS"
  INDEX=0
  for REDIS_SENTINEL_URL in "${REDIS_SENTINEL_URLS[@]}"; do
    yq write -i $CONFIG_FILE redisLockConfig.sentinelUrls.[$INDEX] "${REDIS_SENTINEL_URL}"
    yq write -i $CONFIG_FILE redisAtmosphereConfig.sentinelUrls.[$INDEX] "${REDIS_SENTINEL_URL}"
    yq write -i $REDISSON_CACHE_FILE sentinelServersConfig.sentinelAddresses.[$INDEX] "${REDIS_SENTINEL_URL}"
    INDEX=$(expr $INDEX + 1)
  done
fi

if [[ "" != "$REDIS_ENV_NAMESPACE" ]]; then
    yq write -i $CONFIG_FILE redisLockConfig.envNamespace "$REDIS_ENV_NAMESPACE"
    yq write -i $CONFIG_FILE redisAtmosphereConfig.envNamespace "$REDIS_ENV_NAMESPACE"
fi

if [[ "" != "$REDIS_NETTY_THREADS" ]]; then
  yq write -i $CONFIG_FILE redisLockConfig.nettyThreads "$REDIS_NETTY_THREADS"
  yq write -i $CONFIG_FILE redisAtmosphereConfig.nettyThreads "$REDIS_NETTY_THREADS"
  yq write -i $REDISSON_CACHE_FILE nettyThreads "$REDIS_NETTY_THREADS"
fi

if [[ "$REDIS_SCRIPT_CACHE" == "false" ]]; then
  yq write -i $CONFIG_FILE redisLockConfig.useScriptCache false
  yq write -i $CONFIG_FILE redisAtmosphereConfig.useScriptCache false
  yq write -i $REDISSON_CACHE_FILE useScriptCache false
fi

if [[ "" != "$CACHE_NAMESPACE" ]]; then
    yq write -i $CONFIG_FILE cacheConfig.cacheNamespace "$CACHE_NAMESPACE"
fi

if [[ "" != "$CACHE_BACKEND" ]]; then
    yq write -i $CONFIG_FILE cacheConfig.cacheBackend "$CACHE_BACKEND"
fi

if [[ "" != "$DELEGATE_SERVICE_TARGET" ]]; then
  yq write -i $CONFIG_FILE grpcDelegateServiceClientConfig.target "$DELEGATE_SERVICE_TARGET"
fi

if [[ "" != "$DELEGATE_SERVICE_AUTHORITY" ]]; then
  yq write -i $CONFIG_FILE grpcDelegateServiceClientConfig.authority "$DELEGATE_SERVICE_AUTHORITY"
fi

if [[ "" != "$LOG_STREAMING_SERVICE_BASEURL" ]]; then
  yq write -i $CONFIG_FILE logStreamingServiceConfig.baseUrl "$LOG_STREAMING_SERVICE_BASEURL"
fi

if [[ "" != "$LOG_STREAMING_SERVICE_TOKEN" ]]; then
  yq write -i $CONFIG_FILE logStreamingServiceConfig.serviceToken "$LOG_STREAMING_SERVICE_TOKEN"
fi

if [[ "" != "$ACCESS_CONTROL_ENABLED" ]]; then
  yq write -i $CONFIG_FILE accessControlClient.enableAccessControl $ACCESS_CONTROL_ENABLED
fi

if [[ "" != "$ACCESS_CONTROL_BASE_URL" ]]; then
  yq write -i $CONFIG_FILE accessControlClient.accessControlServiceConfig.baseUrl $ACCESS_CONTROL_BASE_URL
fi

if [[ "" != "$ACCESS_CONTROL_SECRET" ]]; then
  yq write -i $CONFIG_FILE accessControlClient.accessControlServiceSecret $ACCESS_CONTROL_SECRET
fi

if [[ "" != "$ENABLE_AUDIT" ]]; then
  yq write -i $CONFIG_FILE enableAudit $ENABLE_AUDIT
fi

if [[ "" != "$EVENTS_FRAMEWORK_REDIS_SENTINELS" ]]; then
  IFS=',' read -ra SENTINEL_URLS <<< "$EVENTS_FRAMEWORK_REDIS_SENTINELS"
  INDEX=0
  for REDIS_SENTINEL_URL in "${SENTINEL_URLS[@]}"; do
    yq write -i $CONFIG_FILE eventsFramework.redis.sentinelUrls.[$INDEX] "${REDIS_SENTINEL_URL}"
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
  yq write -i $CONFIG_FILE ngManagerServiceHttpClientConfig.baseUrl "$NG_MANAGER_BASE_URL"
fi

if [[ "" != "$ENABLE_USER_CHANGESTREAM" ]]; then
  yq write -i $CONFIG_FILE userChangeStreamEnabled "$ENABLE_USER_CHANGESTREAM"
fi

if [[ "" != "$DELEGATE_SERVICE_SECRET" ]]; then
  yq write -i $CONFIG_FILE dmsSecret $DELEGATE_SERVICE_SECRET
fi

if [[ "" != "$CDN_URL" ]]; then
  yq write -i $CONFIG_FILE cdnConfig.url "$CDN_URL"
fi

if [[ "" != "$CDN_KEY" ]]; then
  yq write -i $CONFIG_FILE cdnConfig.keyName "$CDN_KEY"
fi

if [[ "" != "$CDN_KEY_SECRET" ]]; then
  yq write -i $CONFIG_FILE cdnConfig.keySecret "$CDN_KEY_SECRET"
fi

if [[ "" != "$CDN_DELEGATE_JAR_PATH" ]]; then
  yq write -i $CONFIG_FILE cdnConfig.delegateJarPath "$CDN_DELEGATE_JAR_PATH"
fi

if [[ "" != "$CDN_WATCHER_JAR_BASE_PATH" ]]; then
  yq write -i $CONFIG_FILE cdnConfig.watcherJarBasePath "$CDN_WATCHER_JAR_BASE_PATH"
fi

if [[ "" != "$CDN_WATCHER_JAR_PATH" ]]; then
  yq write -i $CONFIG_FILE cdnConfig.watcherJarPath "$CDN_WATCHER_JAR_PATH"
fi

if [[ "" != "$CDN_WATCHER_METADATA_FILE_PATH" ]]; then
  yq write -i $CONFIG_FILE cdnConfig.watcherMetaDataFilePath "$CDN_WATCHER_METADATA_FILE_PATH"
fi

if [[ "" != "$CDN_ORACLE_JRE_TAR_PATH" ]]; then
  yq write -i $CONFIG_FILE cdnConfig.cdnJreTarPaths.oracle8u191 "$CDN_ORACLE_JRE_TAR_PATH"
fi

if [[ "" != "$CDN_OPENJDK_JRE_TAR_PATH" ]]; then
  yq write -i $CONFIG_FILE cdnConfig.cdnJreTarPaths.openjdk8u242 "$CDN_OPENJDK_JRE_TAR_PATH"
fi

if [[ "" != "$CURRENT_JRE" ]]; then
  yq write -i $CONFIG_FILE currentJre "$CURRENT_JRE"
fi

if [[ "" != "$MIGRATE_TO_JRE" ]]; then
  yq write -i $CONFIG_FILE migrateToJre "$MIGRATE_TO_JRE"
fi

if [[ "" != "$ORACLE_JRE_TAR_PATH" ]]; then
  yq write -i $CONFIG_FILE jreConfigs.oracle8u191.jreTarPath "$ORACLE_JRE_TAR_PATH"
fi

if [[ "" != "$OPENJDK_JRE_TAR_PATH" ]]; then
  yq write -i $CONFIG_FILE jreConfigs.openjdk8u242.jreTarPath "$OPENJDK_JRE_TAR_PATH"
fi

if [[ "" != "$FILE_STORAGE" ]]; then
  yq write -i $CONFIG_FILE fileStorageMode "$FILE_STORAGE"
fi

if [[ "" != "$CLUSTER_NAME" ]]; then
  yq write -i $CONFIG_FILE clusterName "$CLUSTER_NAME"
fi

if [[ "" != "$DELEGATE_MTLS_SUBDOMAIN" ]]; then
  yq write -i $CONFIG_FILE delegateMtlsSubdomain "$DELEGATE_MTLS_SUBDOMAIN"
fi
