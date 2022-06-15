#!/usr/bin/env bash
# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

CONFIG_FILE=/opt/harness/config.yml
REDISSON_CACHE_FILE=/opt/harness/redisson-jcache.yaml

replace_key_value () {
  CONFIG_KEY="$1";
  CONFIG_VALUE="$2";
  if [[ "" != "$CONFIG_VALUE" ]]; then
    yq write -i $CONFIG_FILE $CONFIG_KEY $CONFIG_VALUE
  fi
}

write_mongo_hosts_and_ports() {
  IFS=',' read -ra HOST_AND_PORT <<< "$2"
  for INDEX in "${!HOST_AND_PORT[@]}"; do
    HOST=$(cut -d: -f 1 <<< "${HOST_AND_PORT[$INDEX]}")
    PORT=$(cut -d: -f 2 -s <<< "${HOST_AND_PORT[$INDEX]}")

    yq write -i $CONFIG_FILE $1.hosts[$INDEX].host "$HOST"
    if [[ "" != "$PORT" ]]; then
      yq write -i $CONFIG_FILE $1.hosts[$INDEX].port "$PORT"
    fi
  done
}

write_mongo_params() {
  IFS='&' read -ra PARAMS <<< "$2"
  for PARAM_PAIR in "${PARAMS[@]}"; do
    NAME=$(cut -d= -f 1 <<< "$PARAM_PAIR")
    VALUE=$(cut -d= -f 2 <<< "$PARAM_PAIR")
    yq write -i $CONFIG_FILE $1.params.$NAME "$VALUE"
  done
}

yq delete -i $CONFIG_FILE 'server.applicationConnectors.(type==https)'
yq write -i $CONFIG_FILE server.adminConnectors "[]"

yq delete -i $CONFIG_FILE 'grpcServer.connectors.(secure==true)'
yq delete -i $CONFIG_FILE 'pmsSdkGrpcServerConfig.connectors.(secure==true)'
yq delete -i $CONFIG_FILE 'gitSyncServerConfig.connectors.(secure==true)'

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
  yq write -i $CONFIG_FILE server.applicationConnectors[0].port "7090"
fi


if [[ "" != "$SERVER_MAX_THREADS" ]]; then
  yq write -i $CONFIG_FILE server.maxThreads "$SERVER_MAX_THREADS"
fi

if [[ "" != "$ALLOWED_ORIGINS" ]]; then
  yq delete -i $CONFIG_FILE allowedOrigins
  yq write -i $CONFIG_FILE allowedOrigins "$ALLOWED_ORIGINS"
fi

if [[ "" != "$MONGO_URI" ]]; then
  yq write -i $CONFIG_FILE mongo.uri "${MONGO_URI//\\&/&}"
fi

if [[ "" != "$MONGO_HOSTS_AND_PORTS" ]]; then
  yq delete -i $CONFIG_FILE mongo.uri
  yq write -i $CONFIG_FILE mongo.username "$MONGO_USERNAME"
  yq write -i $CONFIG_FILE mongo.password "$MONGO_PASSWORD"
  yq write -i $CONFIG_FILE mongo.database "$MONGO_DATABASE"
  yq write -i $CONFIG_FILE mongo.schema "$MONGO_SCHEMA"
  write_mongo_hosts_and_ports mongo "$MONGO_HOSTS_AND_PORTS"
  write_mongo_params mongo "$MONGO_PARAMS"
fi

if [[ "" != "$MONGO_TRACE_MODE" ]]; then
  yq write -i $CONFIG_FILE mongo.traceMode $MONGO_TRACE_MODE
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

if [[ "" != "$MONGO_TRANSACTIONS_ALLOWED" ]]; then
  yq write -i $CONFIG_FILE mongo.transactionsEnabled $MONGO_TRANSACTIONS_ALLOWED
fi

if [[ "" != "$PMS_MONGO_URI" ]]; then
  yq write -i $CONFIG_FILE pmsMongo.uri "${PMS_MONGO_URI//\\&/&}"
fi

if [[ "" != "$PMS_MONGO_HOSTS_AND_PORTS" ]]; then
  yq delete -i $CONFIG_FILE pmsMongo.uri
  yq write -i $CONFIG_FILE pmsMongo.username "$PMS_MONGO_USERNAME"
  yq write -i $CONFIG_FILE pmsMongo.password "$PMS_MONGO_PASSWORD"
  yq write -i $CONFIG_FILE pmsMongo.database "$PMS_MONGO_DATABASE"
  yq write -i $CONFIG_FILE pmsMongo.schema "$PMS_MONGO_SCHEMA"
  write_mongo_hosts_and_ports pmsMongo "$PMS_MONGO_HOSTS_AND_PORTS"
  write_mongo_params pmsMongo "$PMS_MONGO_PARAMS"
fi

if [[ "" != "$MANAGER_TARGET" ]]; then
  yq write -i $CONFIG_FILE grpcClient.target $MANAGER_TARGET
fi

if [[ "" != "$MANAGER_AUTHORITY" ]]; then
  yq write -i $CONFIG_FILE grpcClient.authority $MANAGER_AUTHORITY
fi

if [[ "" != "$GRPC_SERVER_PORT" ]]; then
  yq write -i $CONFIG_FILE grpcServer.connectors[0].port "$GRPC_SERVER_PORT"
fi

if [[ "" != "$NEXT_GEN_MANAGER_SECRET" ]]; then
  yq write -i $CONFIG_FILE nextGen.managerServiceSecret "$NEXT_GEN_MANAGER_SECRET"
fi

if [[ "" != "$NEXT_GEN_MANAGER_SECRET" ]]; then
  yq write -i $CONFIG_FILE nextGen.ngManagerServiceSecret "$NEXT_GEN_MANAGER_SECRET"
fi

if [[ "" != "$USER_VERIFICATION_SECRET" ]]; then
  yq write -i $CONFIG_FILE nextGen.userVerificationSecret "$USER_VERIFICATION_SECRET"
fi

if [[ "" != "$JWT_IDENTITY_SERVICE_SECRET" ]]; then
  yq write -i $CONFIG_FILE nextGen.jwtIdentityServiceSecret "$JWT_IDENTITY_SERVICE_SECRET"
fi

if [[ "" != "$NEXT_GEN_MANAGER_SECRET" ]]; then
  yq write -i $CONFIG_FILE nextGen.pipelineServiceSecret "$NEXT_GEN_MANAGER_SECRET"
fi

if [[ "" != "$NEXT_GEN_MANAGER_SECRET" ]]; then
  yq write -i $CONFIG_FILE nextGen.ciManagerSecret "$NEXT_GEN_MANAGER_SECRET"
fi

if [[ "" != "$NEXT_GEN_MANAGER_SECRET" ]]; then
  yq write -i $CONFIG_FILE nextGen.ceNextGenServiceSecret "$NEXT_GEN_MANAGER_SECRET"
fi

if [[ "" != "$NEXT_GEN_MANAGER_SECRET" ]]; then
  yq write -i $CONFIG_FILE nextGen.ffServiceSecret "$NEXT_GEN_MANAGER_SECRET"
fi

if [[ "" != "$TEMPLATE_SERVICE_ENDPOINT" ]]; then
  yq write -i $CONFIG_FILE templateServiceClientConfig.baseUrl $TEMPLATE_SERVICE_ENDPOINT
fi

if [[ "" != "$TEMPLATE_SERVICE_SECRET" ]]; then
  yq write -i $CONFIG_FILE nextGen.templateServiceSecret $TEMPLATE_SERVICE_SECRET
fi

if [[ "" != "$AUTH_ENABLED" ]]; then
  yq write -i $CONFIG_FILE enableAuth "$AUTH_ENABLED"
fi

if [[ "" != "$AUDIT_ENABLED" ]]; then
  yq write -i $CONFIG_FILE enableAudit "$AUDIT_ENABLED"
fi

if [[ "" != "$MANAGER_CLIENT_BASEURL" ]]; then
  yq write -i $CONFIG_FILE managerClientConfig.baseUrl "$MANAGER_CLIENT_BASEURL"
fi

if [[ "" != "$NG_MANAGER_CLIENT_BASEURL" ]]; then
  yq write -i $CONFIG_FILE ngManagerClientConfig.baseUrl "$NG_MANAGER_CLIENT_BASEURL"
fi

if [[ "" != "$CENG_CLIENT_BASEURL" ]]; then
  yq write -i $CONFIG_FILE ceNextGenClientConfig.baseUrl "$CENG_CLIENT_BASEURL"
fi

if [[ "" != "$CENG_CLIENT_READ_TIMEOUT" ]]; then
  yq write -i $CONFIG_FILE ceNextGenClientConfig.readTimeOutSeconds "$CENG_CLIENT_READ_TIMEOUT"
fi

if [[ "" != "$CENG_CLIENT_CONNECT_TIMEOUT" ]]; then
  yq write -i $CONFIG_FILE ceNextGenClientConfig.connectTimeOutSeconds "$CENG_CLIENT_CONNECT_TIMEOUT"
fi

if [[ "" != "$JWT_AUTH_SECRET" ]]; then
  yq write -i $CONFIG_FILE nextGen.jwtAuthSecret "$JWT_AUTH_SECRET"
fi

if [[ "" != "$EVENTS_FRAMEWORK_REDIS_SENTINELS" ]]; then
  IFS=',' read -ra SENTINEL_URLS <<< "$EVENTS_FRAMEWORK_REDIS_SENTINELS"
  INDEX=0
  for REDIS_SENTINEL_URL in "${SENTINEL_URLS[@]}"; do
    yq write -i $CONFIG_FILE eventsFramework.redis.sentinelUrls.[$INDEX] "${REDIS_SENTINEL_URL}"
    INDEX=$(expr $INDEX + 1)
  done
fi

if [[ "" != "$GRPC_SERVER_PORT" ]]; then
  yq write -i $CONFIG_FILE pmsSdkGrpcServerConfig.connectors[0].port "$GRPC_SERVER_PORT"
fi


if [[ "" != "$SHOULD_CONFIGURE_WITH_PMS" ]]; then
  yq write -i $CONFIG_FILE shouldConfigureWithPMS $SHOULD_CONFIGURE_WITH_PMS
fi

if [[ "" != "$PMS_TARGET" ]]; then
  yq write -i $CONFIG_FILE pmsGrpcClientConfig.target $PMS_TARGET
fi

if [[ "" != "$PMS_AUTHORITY" ]]; then
  yq write -i $CONFIG_FILE pmsGrpcClientConfig.authority $PMS_AUTHORITY
fi

if [[ "" != "$NG_MANAGER_TARGET" ]]; then
 yq write -i $CONFIG_FILE gitGrpcClientConfigs.core.target $NG_MANAGER_TARGET
fi

if [[ "" != "$NG_MANAGER_AUTHORITY" ]]; then
  yq write -i $CONFIG_FILE gitGrpcClientConfigs.core.authority $NG_MANAGER_AUTHORITY
fi

if [[ "" != "$NG_MANAGER_TARGET" ]]; then
  yq write -i $CONFIG_FILE gitSdkConfiguration.gitManagerGrpcClientConfig.target $NG_MANAGER_TARGET
fi

if [[ "" != "$NG_MANAGER_AUTHORITY" ]]; then
  yq write -i $CONFIG_FILE gitSdkConfiguration.gitManagerGrpcClientConfig.authority $NG_MANAGER_AUTHORITY
fi


if [[ "" != "$HARNESS_IMAGE_USER_NAME" ]]; then
  yq write -i $CONFIG_FILE ciDefaultEntityConfiguration.harnessImageUseName $HARNESS_IMAGE_USER_NAME
fi

if [[ "" != "$HARNESS_IMAGE_PASSWORD" ]]; then
  yq write -i $CONFIG_FILE ciDefaultEntityConfiguration.harnessImagePassword $HARNESS_IMAGE_PASSWORD
fi

if [[ "" != "$CE_NG_CLIENT_BASEURL" ]]; then
  yq write -i $CONFIG_FILE ceNextGenClientConfig.baseUrl "$CE_NG_CLIENT_BASEURL"
fi

if [[ "" != "$LW_CLIENT_BASEURL" ]]; then
  yq write -i $CONFIG_FILE lightwingClientConfig.baseUrl "$LW_CLIENT_BASEURL"
fi

if [[ "" != "$CF_CLIENT_BASEURL" ]]; then
  yq write -i $CONFIG_FILE ffServerClientConfig.baseUrl "$CF_CLIENT_BASEURL"
fi

if [[ "" != "$AUDIT_CLIENT_BASEURL" ]]; then
  yq write -i $CONFIG_FILE auditClientConfig.baseUrl "$AUDIT_CLIENT_BASEURL"
fi

if [[ "" != "$SCM_SERVICE_URI" ]]; then
  yq write -i $CONFIG_FILE gitSdkConfiguration.scmConnectionConfig.url "$SCM_SERVICE_URI"
fi

if [[ "" != "$LOG_STREAMING_SERVICE_BASEURL" ]]; then
  yq write -i $CONFIG_FILE logStreamingServiceConfig.baseUrl "$LOG_STREAMING_SERVICE_BASEURL"
fi

if [[ "" != "$LOG_STREAMING_SERVICE_TOKEN" ]]; then
  yq write -i $CONFIG_FILE logStreamingServiceConfig.serviceToken "$LOG_STREAMING_SERVICE_TOKEN"
fi

if [[ "$STACK_DRIVER_LOGGING_ENABLED" == "true" ]]; then
  yq delete -i $CONFIG_FILE 'logging.appenders.(type==console)'
  yq write -i $CONFIG_FILE 'logging.appenders.(type==gke-console).stackdriverLogEnabled' "true"
else
  yq delete -i $CONFIG_FILE 'logging.appenders.(type==gke-console)'
fi

if [[ "" != "$TIMESCALE_PASSWORD" ]]; then
  yq write -i $CONFIG_FILE timescaledb.timescaledbPassword "$TIMESCALE_PASSWORD"
fi

if [[ "" != "$TIMESCALE_URI" ]]; then
  yq write -i $CONFIG_FILE timescaledb.timescaledbUrl "$TIMESCALE_URI"
fi

if [[ "" != "$TIMESCALEDB_USERNAME" ]]; then
  yq write -i $CONFIG_FILE timescaledb.timescaledbUsername "$TIMESCALEDB_USERNAME"
fi

if [[ "" != "$ENABLE_DASHBOARD_TIMESCALE" ]]; then
  yq write -i $CONFIG_FILE enableDashboardTimescale $ENABLE_DASHBOARD_TIMESCALE
fi

if [[ "" != "$FILE_STORAGE_MODE" ]]; then
  yq write -i $CONFIG_FILE fileServiceConfiguration.fileStorageMode "$FILE_STORAGE_MODE"
fi

if [[ "" != "$FILE_STORAGE_CLUSTER_NAME" ]]; then
  yq write -i $CONFIG_FILE fileServiceConfiguration.clusterName "$FILE_STORAGE_CLUSTER_NAME"
fi

yq delete -i $REDISSON_CACHE_FILE codec

if [[ "$REDIS_SCRIPT_CACHE" == "false" ]]; then
  yq write -i $CONFIG_FILE redisLockConfig.useScriptCache false
  yq write -i $REDISSON_CACHE_FILE useScriptCache false
fi

replace_key_value distributedLockImplementation $DISTRIBUTED_LOCK_IMPLEMENTATION

replace_key_value redisLockConfig.sentinel $LOCK_CONFIG_USE_SENTINEL
replace_key_value redisLockConfig.envNamespace $LOCK_CONFIG_ENV_NAMESPACE
replace_key_value redisLockConfig.redisUrl $LOCK_CONFIG_REDIS_URL
replace_key_value redisLockConfig.masterName $LOCK_CONFIG_SENTINEL_MASTER_NAME
replace_key_value redisLockConfig.userName $LOCK_CONFIG_REDIS_USERNAME
replace_key_value redisLockConfig.password $LOCK_CONFIG_REDIS_PASSWORD
replace_key_value redisLockConfig.nettyThreads $REDIS_NETTY_THREADS

if [[ "" != "$LOCK_CONFIG_REDIS_URL" ]]; then
  yq write -i $REDISSON_CACHE_FILE singleServerConfig.address "$LOCK_CONFIG_REDIS_URL"
fi

if [[ "" != "$DELEGATE_STATUS_ENDPOINT" ]]; then
  yq write -i $CONFIG_FILE delegateStatusEndpoint "$DELEGATE_STATUS_ENDPOINT"
fi

if [[ "" != "$SIGNUP_TARGET_ENV" ]]; then
  yq write -i $CONFIG_FILE signupTargetEnv "$SIGNUP_TARGET_ENV"
fi

if [[ "$LOCK_CONFIG_USE_SENTINEL" == "true" ]]; then
  yq delete -i $REDISSON_CACHE_FILE singleServerConfig
fi

if [[ "" != "$LOCK_CONFIG_SENTINEL_MASTER_NAME" ]]; then
  yq write -i $REDISSON_CACHE_FILE sentinelServersConfig.masterName "$LOCK_CONFIG_SENTINEL_MASTER_NAME"
fi

if [[ "" != "$LOCK_CONFIG_REDIS_SENTINELS" ]]; then
  IFS=',' read -ra SENTINEL_URLS <<< "$LOCK_CONFIG_REDIS_SENTINELS"
  INDEX=0
  for REDIS_SENTINEL_URL in "${SENTINEL_URLS[@]}"; do
    yq write -i $CONFIG_FILE redisLockConfig.sentinelUrls.[$INDEX] "${REDIS_SENTINEL_URL}"
    yq write -i $REDISSON_CACHE_FILE sentinelServersConfig.sentinelAddresses.[$INDEX] "${REDIS_SENTINEL_URL}"
    INDEX=$(expr $INDEX + 1)
  done
fi

if [[ "" != "$REDIS_NETTY_THREADS" ]]; then
  yq write -i $REDISSON_CACHE_FILE nettyThreads "$REDIS_NETTY_THREADS"
fi

replace_key_value cacheConfig.cacheNamespace $CACHE_NAMESPACE
replace_key_value cacheConfig.cacheBackend $CACHE_BACKEND

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

replace_key_value ceAwsSetupConfig.accessKey $CE_AWS_ACCESS_KEY

replace_key_value ceAwsSetupConfig.secretKey $CE_AWS_SECRET_KEY

replace_key_value ceAwsSetupConfig.destinationBucket $CE_AWS_DESTINATION_BUCKET

replace_key_value ceAwsSetupConfig.templateURL $CE_AWS_TEMPLATE_URL

replace_key_value ceGcpSetupConfig.gcpProjectId $CE_SETUP_CONFIG_GCP_PROJECT_ID

replace_key_value accessControlClient.enableAccessControl "$ACCESS_CONTROL_ENABLED"

replace_key_value accessControlClient.accessControlServiceConfig.baseUrl "$ACCESS_CONTROL_BASE_URL"

replace_key_value accessControlClient.accessControlServiceSecret "$ACCESS_CONTROL_SECRET"

replace_key_value accessControlAdminClient.accessControlServiceConfig.baseUrl "$ACCESS_CONTROL_BASE_URL"

replace_key_value accessControlAdminClient.accessControlServiceSecret "$ACCESS_CONTROL_SECRET"

replace_key_value outboxPollConfig.initialDelayInSeconds "$OUTBOX_POLL_INITIAL_DELAY"

replace_key_value outboxPollConfig.pollingIntervalInSeconds "$OUTBOX_POLL_INTERVAL"

replace_key_value outboxPollConfig.maximumRetryAttemptsForAnEvent "$OUTBOX_MAX_RETRY_ATTEMPTS"

replace_key_value notificationClient.httpClient.baseUrl "$NOTIFICATION_BASE_URL"

replace_key_value notificationClient.secrets.notificationClientSecret "$NEXT_GEN_MANAGER_SECRET"

replace_key_value notificationClient.messageBroker.uri "${NOTIFICATION_MONGO_URI//\\&/&}"

replace_key_value accessControlAdminClient.mockAccessControlService "${MOCK_ACCESS_CONTROL_SERVICE:-true}"

replace_key_value gitSdkConfiguration.scmConnectionConfig.url "$SCM_SERVICE_URL"

replace_key_value resourceGroupClientConfig.serviceConfig.baseUrl "$RESOURCE_GROUP_BASE_URL"

replace_key_value resourceGroupClientConfig.secret "$NEXT_GEN_MANAGER_SECRET"

replace_key_value baseUrls.currentGenUiUrl "$CURRENT_GEN_UI_URL"
replace_key_value baseUrls.nextGenUiUrl "$NEXT_GEN_UI_URL"
replace_key_value baseUrls.nextGenAuthUiUrl "$NG_AUTH_UI_URL"
replace_key_value baseUrls.webhookBaseUrl "$WEBHOOK_BASE_URL"

replace_key_value ngAuthUIEnabled "$HARNESS_ENABLE_NG_AUTH_UI_PLACEHOLDER"

replace_key_value exportMetricsToStackDriver "$EXPORT_METRICS_TO_STACK_DRIVER"

replace_key_value signupNotificationConfiguration.projectId "$SIGNUP_NOTIFICATION_GCS_PROJECT_ID"
replace_key_value signupNotificationConfiguration.bucketName "$SIGNUP_NOTIFICATION_GCS_BUCKET_NAME"

replace_key_value segmentConfiguration.enabled "$SEGMENT_ENABLED"
replace_key_value segmentConfiguration.url "$SEGMENT_URL"
replace_key_value segmentConfiguration.apiKey "$SEGMENT_APIKEY"
replace_key_value segmentConfiguration.certValidationRequired "$SEGMENT_VERIFY_CERT"

replace_key_value accountConfig.deploymentClusterName "$DEPLOYMENT_CLUSTER_NAME"

replace_key_value gitGrpcClientConfigs.pms.target "$PMS_GITSYNC_TARGET"
replace_key_value gitGrpcClientConfigs.pms.authority "$PMS_GITSYNC_AUTHORITY"

replace_key_value gitGrpcClientConfigs.templateservice.target "$TEMPLATE_GITSYNC_TARGET"
replace_key_value gitGrpcClientConfigs.templateservice.authority "$TEMPLATE_GITSYNC_AUTHORITY"

replace_key_value gitGrpcClientConfigs.cf.target "$CF_GITSYNC_TARGET"
replace_key_value gitGrpcClientConfigs.cf.authority "$CF_GITSYNC_AUTHORITY"

replace_key_value cfClientConfig.apiKey "$CF_CLIENT_API_KEY"
replace_key_value cfClientConfig.configUrl "$CF_CLIENT_CONFIG_URL"
replace_key_value cfClientConfig.eventUrl "$CF_CLIENT_EVENT_URL"
replace_key_value cfClientConfig.analyticsEnabled "$CF_CLIENT_ANALYTICS_ENABLED"
replace_key_value cfClientConfig.connectionTimeout "$CF_CLIENT_CONNECTION_TIMEOUT"
replace_key_value cfClientConfig.readTimeout "$CF_CLIENT_READ_TIMEOUT"
replace_key_value cfClientConfig.bufferSize "$CF_CLIENT_BUFFER_SIZE"
replace_key_value featureFlagConfig.featureFlagSystem "$FEATURE_FLAG_SYSTEM"
replace_key_value featureFlagConfig.syncFeaturesToCF "$SYNC_FEATURES_TO_CF"
replace_key_value ceAzureSetupConfig.azureAppClientId "$AZURE_APP_CLIENT_ID"
replace_key_value ceAzureSetupConfig.azureAppClientSecret "$AZURE_APP_CLIENT_SECRET"
replace_key_value pipelineServiceClientConfig.baseUrl "$PIPELINE_SERVICE_CLIENT_BASEURL"
replace_key_value ciManagerClientConfig.baseUrl "$CI_MANAGER_SERVICE_CLIENT_BASEURL"
replace_key_value scopeAccessCheckEnabled "${SCOPE_ACCESS_CHECK:-true}"

replace_key_value enforcementClientConfiguration.enforcementCheckEnabled "$ENFORCEMENT_CHECK_ENABLED"
replace_key_value secretsConfiguration.gcpSecretManagerProject "$GCP_SECRET_MANAGER_PROJECT"
replace_key_value secretsConfiguration.secretResolutionEnabled "$RESOLVE_SECRETS"

replace_key_value opaServerConfig.baseUrl "$OPA_SERVER_BASEURL"
replace_key_value opaServerConfig.secret "$OPA_SERVER_SECRET"

replace_key_value subscriptionConfig.stripeApiKey "$STRIPE_API_KEY"

replace_key_value gitopsResourceClientConfig.config.baseUrl "$GITOPS_SERVICE_CLIENT_BASEURL"
replace_key_value gitopsResourceClientConfig.secret "$GITOPS_SERVICE_SECRET"