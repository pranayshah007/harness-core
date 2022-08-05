#!/usr/bin/env bash
# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

CONFIG_FILE=/opt/harness/config.yml
REDISSON_CACHE_FILE=/opt/harness/redisson-jcache.yaml
ENTERPRISE_REDISSON_CACHE_FILE=/opt/harness/enterprise-redisson-jcache.yaml

replace_key_value () {
  CONFIG_KEY="$1";
  CONFIG_VALUE="$2";
  if [[ "" != "$CONFIG_VALUE" ]]; then
    yq -i '.$CONFIG_KEY="$CONFIG_VALUE"' $CONFIG_FILE
  fi
}

write_mongo_hosts_and_ports() {
  IFS=',' read -ra HOST_AND_PORT <<< "$2"
  for INDEX in "${!HOST_AND_PORT[@]}"; do
    HOST=$(cut -d: -f 1 <<< "${HOST_AND_PORT[$INDEX]}")
    PORT=$(cut -d: -f 2 -s <<< "${HOST_AND_PORT[$INDEX]}")

    yq -i '.$1.hosts[$INDEX].host="$HOST"' $CONFIG_FILE
    if [[ "" != "$PORT" ]]; then
      yq -i '.$1.hosts[$INDEX].port="$PORT"' $CONFIG_FILE
    fi
  done
}

write_mongo_params() {
  IFS='&' read -ra PARAMS <<< "$2"
  for PARAM_PAIR in "${PARAMS[@]}"; do
    NAME=$(cut -d= -f 1 <<< "$PARAM_PAIR")
    VALUE=$(cut -d= -f 2 <<< "$PARAM_PAIR")
    yq -i '.$1.params.$NAME="$VALUE"' $CONFIG_FILE
  done
}

yq 'del(.'server.applicationConnectors.(type==https)')' $CONFIG_FILE
yq -i '.server.adminConnectors="[]"' $CONFIG_FILE

yq 'del(.'grpcServer.connectors.(secure==true)')' $CONFIG_FILE
yq 'del(.'pmsSdkGrpcServerConfig.connectors.(secure==true)')' $CONFIG_FILE
yq 'del(.'gitSyncServerConfig.connectors.(secure==true)')' $CONFIG_FILE

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
  yq -i '.server.applicationConnectors[0].port="7090"' $CONFIG_FILE
fi


if [[ "" != "$SERVER_MAX_THREADS" ]]; then
  yq -i '.server.maxThreads="$SERVER_MAX_THREADS"' $CONFIG_FILE
fi

if [[ "" != "$ALLOWED_ORIGINS" ]]; then
  yq 'del(.allowedOrigins)' $CONFIG_FILE
  yq -i '.allowedOrigins="$ALLOWED_ORIGINS"' $CONFIG_FILE
fi

if [[ "" != "$MONGO_URI" ]]; then
  yq -i '.mongo.uri="${MONGO_URI//\\&/&}"' $CONFIG_FILE
fi

if [[ "" != "$MONGO_HOSTS_AND_PORTS" ]]; then
  yq 'del(.mongo.uri)' $CONFIG_FILE
  yq -i '.mongo.username="$MONGO_USERNAME"' $CONFIG_FILE
  yq -i '.mongo.password="$MONGO_PASSWORD"' $CONFIG_FILE
  yq -i '.mongo.database="$MONGO_DATABASE"' $CONFIG_FILE
  yq -i '.mongo.schema="$MONGO_SCHEMA"' $CONFIG_FILE
  write_mongo_hosts_and_ports mongo "$MONGO_HOSTS_AND_PORTS"
  write_mongo_params mongo "$MONGO_PARAMS"
fi

if [[ "" != "$MONGO_TRACE_MODE" ]]; then
  yq -i '.mongo.traceMode="$MONGO_TRACE_MODE"' $CONFIG_FILE
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

if [[ "" != "$MONGO_TRANSACTIONS_ALLOWED" ]]; then
  yq -i '.mongo.transactionsEnabled="$MONGO_TRANSACTIONS_ALLOWED"' $CONFIG_FILE
fi

<<<<<<< HEAD
=======
if [[ "" != "$PMS_MONGO_URI" ]]; then
  yq -i '.pmsMongo.uri="${PMS_MONGO_URI//\\&/&}"' $CONFIG_FILE
fi

if [[ "" != "$PMS_MONGO_HOSTS_AND_PORTS" ]]; then
  yq 'del(.pmsMongo.uri)' $CONFIG_FILE
  yq -i '.pmsMongo.username="$PMS_MONGO_USERNAME"' $CONFIG_FILE
  yq -i '.pmsMongo.password="$PMS_MONGO_PASSWORD"' $CONFIG_FILE
  yq -i '.pmsMongo.database="$PMS_MONGO_DATABASE"' $CONFIG_FILE
  yq -i '.pmsMongo.schema="$PMS_MONGO_SCHEMA"' $CONFIG_FILE
  write_mongo_hosts_and_ports pmsMongo "$PMS_MONGO_HOSTS_AND_PORTS"
  write_mongo_params pmsMongo "$PMS_MONGO_PARAMS"
fi

>>>>>>> 7655f574839 (feat: [SPG-143]]: upgrade from yq3 to yq4: update write and delete - rebase)
if [[ "" != "$MANAGER_TARGET" ]]; then
  yq -i '.grpcClient.target="$MANAGER_TARGET"' $CONFIG_FILE
fi

if [[ "" != "$MANAGER_AUTHORITY" ]]; then
  yq -i '.grpcClient.authority="$MANAGER_AUTHORITY"' $CONFIG_FILE
fi

if [[ "" != "$GRPC_SERVER_PORT" ]]; then
  yq -i '.grpcServer.connectors[0].port="$GRPC_SERVER_PORT"' $CONFIG_FILE
fi

if [[ "" != "$NEXT_GEN_MANAGER_SECRET" ]]; then
  yq -i '.nextGen.managerServiceSecret="$NEXT_GEN_MANAGER_SECRET"' $CONFIG_FILE
fi

if [[ "" != "$NEXT_GEN_MANAGER_SECRET" ]]; then
  yq -i '.nextGen.ngManagerServiceSecret="$NEXT_GEN_MANAGER_SECRET"' $CONFIG_FILE
fi

if [[ "" != "$USER_VERIFICATION_SECRET" ]]; then
  yq -i '.nextGen.userVerificationSecret="$USER_VERIFICATION_SECRET"' $CONFIG_FILE
fi

if [[ "" != "$JWT_IDENTITY_SERVICE_SECRET" ]]; then
  yq -i '.nextGen.jwtIdentityServiceSecret="$JWT_IDENTITY_SERVICE_SECRET"' $CONFIG_FILE
fi

if [[ "" != "$NEXT_GEN_MANAGER_SECRET" ]]; then
  yq -i '.nextGen.pipelineServiceSecret="$NEXT_GEN_MANAGER_SECRET"' $CONFIG_FILE
fi

if [[ "" != "$NEXT_GEN_MANAGER_SECRET" ]]; then
  yq -i '.nextGen.ciManagerSecret="$NEXT_GEN_MANAGER_SECRET"' $CONFIG_FILE
fi

if [[ "" != "$NEXT_GEN_MANAGER_SECRET" ]]; then
  yq -i '.nextGen.cvngServiceSecret="$NEXT_GEN_MANAGER_SECRET"' $CONFIG_FILE
fi

if [[ "" != "$NEXT_GEN_MANAGER_SECRET" ]]; then
  yq -i '.nextGen.ceNextGenServiceSecret="$NEXT_GEN_MANAGER_SECRET"' $CONFIG_FILE
fi

if [[ "" != "$NEXT_GEN_MANAGER_SECRET" ]]; then
  yq -i '.nextGen.ffServiceSecret="$NEXT_GEN_MANAGER_SECRET"' $CONFIG_FILE
fi

if [[ "" != "$TEMPLATE_SERVICE_ENDPOINT" ]]; then
  yq -i '.templateServiceClientConfig.baseUrl="$TEMPLATE_SERVICE_ENDPOINT"' $CONFIG_FILE
fi

if [[ "" != "$TEMPLATE_SERVICE_SECRET" ]]; then
  yq -i '.nextGen.templateServiceSecret="$TEMPLATE_SERVICE_SECRET"' $CONFIG_FILE
fi

if [[ "" != "$AUTH_ENABLED" ]]; then
  yq -i '.enableAuth="$AUTH_ENABLED"' $CONFIG_FILE
fi

if [[ "" != "$AUDIT_ENABLED" ]]; then
  yq -i '.enableAudit="$AUDIT_ENABLED"' $CONFIG_FILE
fi

if [[ "" != "$MANAGER_CLIENT_BASEURL" ]]; then
  yq -i '.managerClientConfig.baseUrl="$MANAGER_CLIENT_BASEURL"' $CONFIG_FILE
fi

if [[ "" != "$NG_MANAGER_CLIENT_BASEURL" ]]; then
  yq -i '.ngManagerClientConfig.baseUrl="$NG_MANAGER_CLIENT_BASEURL"' $CONFIG_FILE
fi

if [[ "" != "$CENG_CLIENT_BASEURL" ]]; then
  yq -i '.ceNextGenClientConfig.baseUrl="$CENG_CLIENT_BASEURL"' $CONFIG_FILE
fi

if [[ "" != "$CENG_CLIENT_READ_TIMEOUT" ]]; then
  yq -i '.ceNextGenClientConfig.readTimeOutSeconds="$CENG_CLIENT_READ_TIMEOUT"' $CONFIG_FILE
fi

if [[ "" != "$CENG_CLIENT_CONNECT_TIMEOUT" ]]; then
  yq -i '.ceNextGenClientConfig.connectTimeOutSeconds="$CENG_CLIENT_CONNECT_TIMEOUT"' $CONFIG_FILE
fi

if [[ "" != "$JWT_AUTH_SECRET" ]]; then
  yq -i '.nextGen.jwtAuthSecret="$JWT_AUTH_SECRET"' $CONFIG_FILE
fi

if [[ "" != "$EVENTS_FRAMEWORK_REDIS_SENTINELS" ]]; then
  IFS=',' read -ra SENTINEL_URLS <<< "$EVENTS_FRAMEWORK_REDIS_SENTINELS"
  INDEX=0
  for REDIS_SENTINEL_URL in "${SENTINEL_URLS[@]}"; do
    yq -i '.eventsFramework.redis.sentinelUrls.[$INDEX]="${REDIS_SENTINEL_URL}"' $CONFIG_FILE
    INDEX=$(expr $INDEX + 1)
  done
fi

if [[ "" != "$GRPC_SERVER_PORT" ]]; then
  yq -i '.pmsSdkGrpcServerConfig.connectors[0].port="$GRPC_SERVER_PORT"' $CONFIG_FILE
fi


if [[ "" != "$SHOULD_CONFIGURE_WITH_PMS" ]]; then
  yq -i '.shouldConfigureWithPMS="$SHOULD_CONFIGURE_WITH_PMS"' $CONFIG_FILE
fi

if [[ "" != "$PMS_TARGET" ]]; then
  yq -i '.pmsGrpcClientConfig.target="$PMS_TARGET"' $CONFIG_FILE
fi

if [[ "" != "$PMS_AUTHORITY" ]]; then
  yq -i '.pmsGrpcClientConfig.authority="$PMS_AUTHORITY"' $CONFIG_FILE
fi

if [[ "" != "$NG_MANAGER_TARGET" ]]; then
 yq -i '.gitGrpcClientConfigs.core.target="$NG_MANAGER_TARGET"' $CONFIG_FILE
fi

if [[ "" != "$NG_MANAGER_AUTHORITY" ]]; then
  yq -i '.gitGrpcClientConfigs.core.authority="$NG_MANAGER_AUTHORITY"' $CONFIG_FILE
fi

if [[ "" != "$NG_MANAGER_TARGET" ]]; then
  yq -i '.gitSdkConfiguration.gitManagerGrpcClientConfig.target="$NG_MANAGER_TARGET"' $CONFIG_FILE
fi

if [[ "" != "$NG_MANAGER_AUTHORITY" ]]; then
  yq -i '.gitSdkConfiguration.gitManagerGrpcClientConfig.authority="$NG_MANAGER_AUTHORITY"' $CONFIG_FILE
fi


if [[ "" != "$HARNESS_IMAGE_USER_NAME" ]]; then
  yq -i '.ciDefaultEntityConfiguration.harnessImageUseName="$HARNESS_IMAGE_USER_NAME"' $CONFIG_FILE
fi

if [[ "" != "$HARNESS_IMAGE_PASSWORD" ]]; then
  yq -i '.ciDefaultEntityConfiguration.harnessImagePassword="$HARNESS_IMAGE_PASSWORD"' $CONFIG_FILE
fi

if [[ "" != "$CE_NG_CLIENT_BASEURL" ]]; then
  yq -i '.ceNextGenClientConfig.baseUrl="$CE_NG_CLIENT_BASEURL"' $CONFIG_FILE
fi

if [[ "" != "$LW_CLIENT_BASEURL" ]]; then
  yq -i '.lightwingClientConfig.baseUrl="$LW_CLIENT_BASEURL"' $CONFIG_FILE
fi

if [[ "" != "$CF_CLIENT_BASEURL" ]]; then
  yq -i '.ffServerClientConfig.baseUrl="$CF_CLIENT_BASEURL"' $CONFIG_FILE
fi

if [[ "" != "$AUDIT_CLIENT_BASEURL" ]]; then
  yq -i '.auditClientConfig.baseUrl="$AUDIT_CLIENT_BASEURL"' $CONFIG_FILE
fi

if [[ "" != "$SCM_SERVICE_URI" ]]; then
  yq -i '.gitSdkConfiguration.scmConnectionConfig.url="$SCM_SERVICE_URI"' $CONFIG_FILE
fi

if [[ "" != "$LOG_STREAMING_SERVICE_BASEURL" ]]; then
  yq -i '.logStreamingServiceConfig.baseUrl="$LOG_STREAMING_SERVICE_BASEURL"' $CONFIG_FILE
fi

if [[ "" != "$LOG_STREAMING_SERVICE_TOKEN" ]]; then
  yq -i '.logStreamingServiceConfig.serviceToken="$LOG_STREAMING_SERVICE_TOKEN"' $CONFIG_FILE
fi

if [[ "$STACK_DRIVER_LOGGING_ENABLED" == "true" ]]; then
  yq 'del(.'logging.appenders.(type==console)')' $CONFIG_FILE
  yq -i '.'logging.appenders.(type==gke-console).stackdriverLogEnabled'="true"' $CONFIG_FILE
else
  yq 'del(.'logging.appenders.(type==gke-console)')' $CONFIG_FILE
fi

if [[ "" != "$TIMESCALE_PASSWORD" ]]; then
  yq -i '.timescaledb.timescaledbPassword="$TIMESCALE_PASSWORD"' $CONFIG_FILE
fi

if [[ "" != "$TIMESCALE_URI" ]]; then
  yq -i '.timescaledb.timescaledbUrl="$TIMESCALE_URI"' $CONFIG_FILE
fi

if [[ "" != "$TIMESCALEDB_USERNAME" ]]; then
  yq -i '.timescaledb.timescaledbUsername="$TIMESCALEDB_USERNAME"' $CONFIG_FILE
fi

if [[ "" != "$ENABLE_DASHBOARD_TIMESCALE" ]]; then
  yq -i '.enableDashboardTimescale="$ENABLE_DASHBOARD_TIMESCALE"' $CONFIG_FILE
fi

if [[ "" != "$FILE_STORAGE_MODE" ]]; then
  yq -i '.fileServiceConfiguration.fileStorageMode="$FILE_STORAGE_MODE"' $CONFIG_FILE
fi

if [[ "" != "$FILE_STORAGE_CLUSTER_NAME" ]]; then
  yq -i '.fileServiceConfiguration.clusterName="$FILE_STORAGE_CLUSTER_NAME"' $CONFIG_FILE
fi

yq 'del(.codec)' $REDISSON_CACHE_FILE

if [[ "$REDIS_SCRIPT_CACHE" == "false" ]]; then
  yq -i '.redisLockConfig.useScriptCache="false"' $CONFIG_FILE
  yq -i '.useScriptCache="false"' $REDISSON_CACHE_FILE
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
  yq -i '.singleServerConfig.address="$LOCK_CONFIG_REDIS_URL"' $REDISSON_CACHE_FILE
fi

if [[ "" != "$GITLAB_OAUTH_CLIENT" ]]; then
  yq -i '.gitlabConfig.clientId="$GITLAB_OAUTH_CLIENT"' $CONFIG_FILE
fi

if [[ "" != "$GITLAB_OAUTH_SECRET" ]]; then
  yq -i '.gitlabConfig.clientSecret="$GITLAB_OAUTH_SECRET"' $CONFIG_FILE
fi

if [[ "" != "$GITLAB_OAUTH_CALLBACK_URL" ]]; then
  yq -i '.gitlabConfig.callbackUrl="$GITLAB_OAUTH_CALLBACK_URL"' $CONFIG_FILE
fi

if [[ "" != "$OAUTH_REFRESH_FREQUECY" ]]; then
  yq -i '.oauthRefreshFrequency="$OAUTH_REFRESH_FREQUECY"' $CONFIG_FILE
fi

if [[ "" != "$OAUTH_REFRESH_ENABLED" ]]; then
  yq -i '.oauthRefreshEnabled="$OAUTH_REFRESH_ENABLED"' $CONFIG_FILE
fi

if [[ "" != "$DELEGATE_STATUS_ENDPOINT" ]]; then
  yq -i '.delegateStatusEndpoint="$DELEGATE_STATUS_ENDPOINT"' $CONFIG_FILE
fi

if [[ "" != "$SIGNUP_TARGET_ENV" ]]; then
  yq -i '.signupTargetEnv="$SIGNUP_TARGET_ENV"' $CONFIG_FILE
fi

if [[ "$LOCK_CONFIG_USE_SENTINEL" == "true" ]]; then
  yq 'del(.singleServerConfig)' $REDISSON_CACHE_FILE
fi

if [[ "" != "$LOCK_CONFIG_SENTINEL_MASTER_NAME" ]]; then
  yq -i '.sentinelServersConfig.masterName="$LOCK_CONFIG_SENTINEL_MASTER_NAME"' $REDISSON_CACHE_FILE
fi

if [[ "" != "$LOCK_CONFIG_REDIS_SENTINELS" ]]; then
  IFS=',' read -ra SENTINEL_URLS <<< "$LOCK_CONFIG_REDIS_SENTINELS"
  INDEX=0
  for REDIS_SENTINEL_URL in "${SENTINEL_URLS[@]}"; do
    yq -i '.redisLockConfig.sentinelUrls.[$INDEX]="${REDIS_SENTINEL_URL}"' $CONFIG_FILE
    yq -i '.sentinelServersConfig.sentinelAddresses.[$INDEX]="${REDIS_SENTINEL_URL}"' $REDISSON_CACHE_FILE
    INDEX=$(expr $INDEX + 1)
  done
fi

if [[ "" != "$REDIS_NETTY_THREADS" ]]; then
  yq -i '.nettyThreads="$REDIS_NETTY_THREADS"' $REDISSON_CACHE_FILE
fi

yq delete -i $ENTERPRISE_REDISSON_CACHE_FILE codec

if [[ "$REDIS_SCRIPT_CACHE" == "false" ]]; then
  yq write -i $ENTERPRISE_REDISSON_CACHE_FILE useScriptCache false
fi

if [[ "" != "$EVENTS_FRAMEWORK_NETTY_THREADS" ]]; then
  yq write -i $ENTERPRISE_REDISSON_CACHE_FILE nettyThreads "$EVENTS_FRAMEWORK_NETTY_THREADS"
fi

if [[ "" != "$EVENTS_FRAMEWORK_REDIS_URL" ]]; then
  yq write -i $ENTERPRISE_REDISSON_CACHE_FILE singleServerConfig.address "$EVENTS_FRAMEWORK_REDIS_URL"
fi

if [[ "" != "$EVENTS_FRAMEWORK_REDIS_USERNAME" ]]; then
  yq write -i $ENTERPRISE_REDISSON_CACHE_FILE singleServerConfig.username "$EVENTS_FRAMEWORK_REDIS_USERNAME"
fi

if [[ "" != "$EVENTS_FRAMEWORK_REDIS_PASSWORD" ]]; then
  yq write -i $ENTERPRISE_REDISSON_CACHE_FILE singleServerConfig.password "$EVENTS_FRAMEWORK_REDIS_PASSWORD"
fi

if [[ "" != "$EVENTS_FRAMEWORK_REDIS_SSL_CA_TRUST_STORE_PATH" ]]; then
  yq write -i $ENTERPRISE_REDISSON_CACHE_FILE singleServerConfig.sslTruststore file:"$EVENTS_FRAMEWORK_REDIS_SSL_CA_TRUST_STORE_PATH"
fi

if [[ "" != "$EVENTS_FRAMEWORK_REDIS_SSL_CA_TRUST_STORE_PASSWORD" ]]; then
  yq write -i $ENTERPRISE_REDISSON_CACHE_FILE singleServerConfig.sslTruststorePassword "$EVENTS_FRAMEWORK_REDIS_SSL_CA_TRUST_STORE_PASSWORD"
fi

if [[ "$EVENTS_FRAMEWORK_USE_SENTINEL" == "true" ]]; then
  yq delete -i $ENTERPRISE_REDISSON_CACHE_FILE singleServerConfig

  if [[ "" != "$EVENTS_FRAMEWORK_SENTINEL_MASTER_NAME" ]]; then
    yq write -i $ENTERPRISE_REDISSON_CACHE_FILE sentinelServersConfig.masterName "$EVENTS_FRAMEWORK_SENTINEL_MASTER_NAME"
  fi

  if [[ "" != "$EVENTS_FRAMEWORK_REDIS_SENTINELS" ]]; then
    IFS=',' read -ra SENTINEL_URLS <<< "$EVENTS_FRAMEWORK_REDIS_SENTINELS"
    INDEX=0
    for REDIS_SENTINEL_URL in "${SENTINEL_URLS[@]}"; do
      yq write -i $ENTERPRISE_REDISSON_CACHE_FILE sentinelServersConfig.sentinelAddresses.[$INDEX] "${REDIS_SENTINEL_URL}"
      INDEX=$(expr $INDEX + 1)
    done
  fi
fi

if [[ "" != "$CD_TSDB_RETENTION_PERIOD_MONTHS" ]]; then
  yq write -i $CD_TSDB_RETENTION_PERIOD_MONTHS cdTsDbRetentionPeriodMonths "$CD_TSDB_RETENTION_PERIOD_MONTHS"
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
replace_key_value cvngClientConfig.baseUrl "$CVNG_SERVICE_CLIENT_BASEURL"
replace_key_value scopeAccessCheckEnabled "${SCOPE_ACCESS_CHECK:-true}"

replace_key_value enforcementClientConfiguration.enforcementCheckEnabled "$ENFORCEMENT_CHECK_ENABLED"
replace_key_value secretsConfiguration.gcpSecretManagerProject "$GCP_SECRET_MANAGER_PROJECT"
replace_key_value secretsConfiguration.secretResolutionEnabled "$RESOLVE_SECRETS"

replace_key_value opaServerConfig.baseUrl "$OPA_SERVER_BASEURL"
replace_key_value opaServerConfig.secret "$OPA_SERVER_SECRET"
replace_key_value policyManagerSecret "$OPA_SERVER_SECRET"
replace_key_value opaClientConfig.baseUrl "$OPA_SERVER_BASEURL"
if [[ "" != "$OPA_CONNECT_TIMEOUT" ]]; then
  replace_key_value opaClientConfig.connectTimeOutSeconds "$OPA_CONNECT_TIMEOUT"
fi
if [[ "" != "$OPA_READ_TIMEOUT" ]]; then
  replace_key_value opaClientConfig.readTimeOutSeconds "$OPA_READ_TIMEOUT"
fi

replace_key_value subscriptionConfig.stripeApiKey "$STRIPE_API_KEY"

replace_key_value gitopsResourceClientConfig.config.baseUrl "$GITOPS_SERVICE_CLIENT_BASEURL"
replace_key_value gitopsResourceClientConfig.secret "$GITOPS_SERVICE_SECRET"
