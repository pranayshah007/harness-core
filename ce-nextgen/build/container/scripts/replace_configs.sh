#!/usr/bin/env bash
# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

CONFIG_FILE=/opt/harness/config.yml

replace_key_value () {
  CONFIG_KEY="$1";
  CONFIG_VALUE="$2";
  if [[ "" != "$CONFIG_VALUE" ]]; then
    export CONFIG_VALUE; export CONFIG_KEY; export CONFIG_KEY=.$CONFIG_KEY; yq -i 'eval(strenv(CONFIG_KEY))=env(CONFIG_VALUE)' $CONFIG_FILE
  fi
}

#
yq -i 'del(.server.adminConnectors)' $CONFIG_FILE
yq -i 'del(.server.applicationConnectors[0])' $CONFIG_FILE

replace_key_value logging.level $LOGGING_LEVEL

replace_key_value server.applicationConnectors[0].port $CE_NEXTGEN_PORT

replace_key_value events-mongo.uri "${EVENTS_MONGO_DB_URL//\\&/&}"
replace_key_value events-mongo.indexManagerMode $EVENTS_MONGO_INDEX_MANAGER_MODE

replace_key_value ngManagerClientConfig.baseUrl $NG_MANAGER_CLIENT_BASEURL
replace_key_value managerClientConfig.baseUrl $MANAGER_CLIENT_BASEURL

replace_key_value ngManagerServiceSecret $NEXT_GEN_MANAGER_SECRET
replace_key_value jwtAuthSecret $JWT_AUTH_SECRET
replace_key_value jwtIdentityServiceSecret $JWT_IDENTITY_SERVICE_SECRET

replace_key_value timescaledb.timescaledbUrl "$TIMESCALEDB_URI"
replace_key_value timescaledb.timescaledbUsername "$TIMESCALEDB_USERNAME"
replace_key_value timescaledb.timescaledbPassword "$TIMESCALEDB_PASSWORD"
replace_key_value timescaledb.sslMode "$TIMESCALEDB_SSL_MODE"
replace_key_value timescaledb.sslRootCert "$TIMESCALEDB_SSL_ROOT_CERT"

replace_key_value enforcementClientConfiguration.enforcementCheckEnabled "$ENFORCEMENT_CHECK_ENABLED"
replace_key_value secretsConfiguration.gcpSecretManagerProject "$GCP_SECRET_MANAGER_PROJECT"
replace_key_value secretsConfiguration.secretResolutionEnabled  "$RESOLVE_SECRETS"

replace_key_value accessControlClient.enableAccessControl "$ACCESS_CONTROL_ENABLED"
replace_key_value accessControlClient.accessControlServiceConfig.baseUrl "$ACCESS_CONTROL_BASE_URL"
replace_key_value accessControlClient.accessControlServiceSecret "$ACCESS_CONTROL_SECRET"
replace_key_value accessControlAdminClient.accessControlServiceConfig.baseUrl "$ACCESS_CONTROL_BASE_URL"
replace_key_value accessControlAdminClient.accessControlServiceSecret "$ACCESS_CONTROL_SECRET"

replace_key_value notificationClient.httpClient.baseUrl "$NOTIFICATION_BASE_URL"
replace_key_value notificationClient.secrets.notificationClientSecret "$NEXT_GEN_MANAGER_SECRET"
replace_key_value notificationClient.messageBroker.uri "${NOTIFICATION_MONGO_URI//\\&/&}"

replace_key_value eventsFramework.redis.sentinel $EVENTS_FRAMEWORK_USE_SENTINEL
replace_key_value eventsFramework.redis.envNamespace $EVENTS_FRAMEWORK_ENV_NAMESPACE
replace_key_value eventsFramework.redis.redisUrl $EVENTS_FRAMEWORK_REDIS_URL
replace_key_value eventsFramework.redis.masterName $EVENTS_FRAMEWORK_SENTINEL_MASTER_NAME
replace_key_value eventsFramework.redis.userName $EVENTS_FRAMEWORK_REDIS_USERNAME
replace_key_value eventsFramework.redis.password $EVENTS_FRAMEWORK_REDIS_PASSWORD
replace_key_value eventsFramework.redis.sslConfig.enabled $EVENTS_FRAMEWORK_REDIS_SSL_ENABLED
replace_key_value eventsFramework.redis.sslConfig.CATrustStorePath $EVENTS_FRAMEWORK_REDIS_SSL_CA_TRUST_STORE_PATH
replace_key_value eventsFramework.redis.sslConfig.CATrustStorePassword $EVENTS_FRAMEWORK_REDIS_SSL_CA_TRUST_STORE_PASSWORD
replace_key_value eventsFramework.redis.retryAttempts $REDIS_RETRY_ATTEMPTS
replace_key_value eventsFramework.redis.retryInterval $REDIS_RETRY_INTERVAL

replace_key_value gcpConfig.gcpProjectId "$GCP_PROJECT_ID"
replace_key_value gcpConfig.gcpAwsConnectorCrudPubSubTopic "$GCP_AWS_CONNECTOR_CRUD_PUBSUB_TOPIC"
replace_key_value gcpConfig.gcpGcpConnectorCrudPubSubTopic "$GCP_GCP_CONNECTOR_CRUD_PUBSUB_TOPIC"
replace_key_value gcpConfig.gcpAzureConnectorCrudPubSubTopic "$GCP_AZURE_CONNECTOR_CRUD_PUBSUB_TOPIC"

replace_key_value ceAzureSetupConfig.azureAppClientId "$AZURE_APP_CLIENT_ID"
replace_key_value ceAzureSetupConfig.azureAppClientSecret "$AZURE_APP_CLIENT_SECRET"
replace_key_value ceAzureSetupConfig.enableFileCheckAtSource "$AZURE_ENABLE_FILE_CHECK_AT_SOURCE"

replace_key_value ceGcpSetupConfig.enableServiceAccountPermissionsCheck "$GCP_ENABLE_SERVICE_ACCOUNT_PERMISSIONS_CHECK"
replace_key_value ceGcpSetupConfig.serviceAccountEmail "$GCP_SERVICE_ACCOUNT_EMAIL"

replace_key_value deploymentClusterName "$DEPLOYMENT_CLUSTER_NAME"

replace_key_value awsConfig.accessKey "$AWS_ACCESS_KEY"
replace_key_value awsConfig.secretKey "$AWS_SECRET_KEY"
replace_key_value awsConfig.destinationBucket "$AWS_DESTINATION_BUCKET"
replace_key_value awsConfig.harnessAwsAccountId "$AWS_ACCOUNT_ID"
replace_key_value awsConfig.awsConnectorTemplate "$AWS_TEMPLATE_LINK"
replace_key_value awsGovCloudConfig.accessKey "$AWS_GOV_CLOUD_ACCESS_KEY"
replace_key_value awsGovCloudConfig.secretKey "$AWS_GOV_CLOUD_SECRET_KEY"
replace_key_value awsGovCloudConfig.harnessAwsAccountId "$AWS_GOV_CLOUD_ACCOUNT_ID"
replace_key_value awsGovCloudConfig.awsConnectorTemplate "$AWS_GOV_CLOUD_TEMPLATE_LINK"
replace_key_value awsGovCloudConfig.awsRegionName "$AWS_GOV_CLOUD_REGION_NAME"
replace_key_value awsGovCloudCftUrl "$AWS_GOV_CLOUD_CFT_URL"
replace_key_value cfClientConfig.apiKey "$CF_CLIENT_API_KEY"
replace_key_value cfClientConfig.configUrl "$CF_CLIENT_CONFIG_URL"
replace_key_value cfClientConfig.eventUrl "$CF_CLIENT_EVENT_URL"
replace_key_value cfClientConfig.analyticsEnabled "$CF_CLIENT_ANALYTICS_ENABLED"
replace_key_value cfClientConfig.connectionTimeout "$CF_CLIENT_CONNECTION_TIMEOUT"
replace_key_value cfClientConfig.readTimeout "$CF_CLIENT_READ_TIMEOUT"
replace_key_value cfClientConfig.bufferSize "$CF_CLIENT_BUFFER_SIZE"
replace_key_value featureFlagConfig.featureFlagSystem "$FEATURE_FLAG_SYSTEM"
replace_key_value featureFlagConfig.syncFeaturesToCF "$SYNC_FEATURES_TO_CF"

replace_key_value grpcClient.target "$MANAGER_TARGET"
replace_key_value grpcClient.authority "$MANAGER_AUTHORITY"
replace_key_value awsConnectorCreatedInstantForPolicyCheck $AWS_CONNECTOR_CREATED_INSTANT_FOR_POLICY_CHECK

replace_key_value notificationClient.secrets.notificationClientSecret "$NOTIFICATION_CLIENT_SECRET"

if [[ "" != "$EVENTS_FRAMEWORK_REDIS_SENTINELS" ]]; then
  IFS=',' read -ra SENTINEL_URLS <<< "$EVENTS_FRAMEWORK_REDIS_SENTINELS"
  INDEX=0
  for REDIS_SENTINEL_URL in "${SENTINEL_URLS[@]}"; do
    export REDIS_SENTINEL_URL; export INDEX; yq -i '.eventsFramework.redis.sentinelUrls.[env(INDEX)]=env(REDIS_SENTINEL_URL)' $CONFIG_FILE
    INDEX=$(expr $INDEX + 1)
  done
fi

if [[ "$STACK_DRIVER_LOGGING_ENABLED" == "true" ]]; then
  yq -i 'del(.logging.appenders[0])' $CONFIG_FILE
  yq -i '.logging.appenders[0].stackdriverLogEnabled=true' $CONFIG_FILE
else
  yq -i 'del(.logging.appenders[1])' $CONFIG_FILE
fi

if [[ "" != "$SEGMENT_ENABLED" ]]; then
  export SEGMENT_ENABLED; yq -i '.segmentConfiguration.enabled=env(SEGMENT_ENABLED)' $CONFIG_FILE
fi

if [[ "" != "$SEGMENT_APIKEY" ]]; then
  export SEGMENT_APIKEY; yq -i '.segmentConfiguration.apiKey=env(SEGMENT_APIKEY)' $CONFIG_FILE
fi

if [[ "" != "$AUDIT_CLIENT_BASEURL" ]]; then
  export AUDIT_CLIENT_BASEURL; yq -i '.auditClientConfig.baseUrl=env(AUDIT_CLIENT_BASEURL)' $CONFIG_FILE
fi

if [[ "" != "$AUDIT_ENABLED" ]]; then
  export AUDIT_ENABLED; yq -i '.enableAudit=env(AUDIT_ENABLED)' $CONFIG_FILE
fi


replace_key_value outboxPollConfig.initialDelayInSeconds "$OUTBOX_POLL_INITIAL_DELAY"
replace_key_value outboxPollConfig.pollingIntervalInSeconds "$OUTBOX_POLL_INTERVAL"
replace_key_value outboxPollConfig.maximumRetryAttemptsForAnEvent "$OUTBOX_MAX_RETRY_ATTEMPTS"

replace_key_value exportMetricsToStackDriver "$EXPORT_METRICS_TO_STACK_DRIVER"

replace_key_value lightwingAutoCUDClientConfig.baseUrl "$LIGHTWING_AUTOCUD_CLIENT_CONFIG_BASEURL"
replace_key_value enableLightwingAutoCUDDC "$ENABLE_LIGHTWING_AUTOCUD_DC"

replace_key_value governanceConfig.useDkron "$GOVERNANCE_USE_DKRON"
replace_key_value governanceConfig.callbackApiEndpoint "$GOVERNANCE_CALLBACK_API_ENDPOINT"
replace_key_value governanceConfig.dkronJobEnabled "$GOVERNANCE_DKRON_JOB_ENABLED"
replace_key_value governanceConfig.awsFaktoryJobType "$GOVERNANCE_AWS_FAKTORY_JOB_TYPE"
replace_key_value governanceConfig.awsFaktoryQueueName "$GOVERNANCE_AWS_FAKTORY_QUEUE_NAME"
replace_key_value governanceConfig.OOTBAccount "$GOVERNANCE_OOTB_ACCOUNT"

replace_key_value dkronClientConfig.baseUrl "$DKRON_CLIENT_BASEURL"
replace_key_value dkronClientConfig.connectTimeOutSeconds "$DKRON_CLIENT_CONNECTTIMEOUT"
replace_key_value dkronClientConfig.readTimeOutSeconds "$DKRON_CLIENT_READTIMEOUT"

replace_key_value currencyPreferences.historicalUpdateMonthsCount "$CURRENCY_PREFERENCE_HISTORICAL_UPDATE_MONTHS_COUNT"

replace_key_value clickHouseConfig.url "$CLICKHOUSE_URL"
replace_key_value clickHouseConfig.username "$CLICKHOUSE_USERNAME"
replace_key_value clickHouseConfig.password "$CLICKHOUSE_PASSWORD"

replace_key_value deployMode "$DEPLOY_MODE"
replace_key_value isClickHouseEnabled "$CLICKHOUSE_ENABLED"

replace_key_value aiEngineConfig.genAIService.genAIServiceSecret "$GENAI_SERVICE_SECRET"
replace_key_value aiEngineConfig.genAIService.apiEndpoint "$GENAI_SERVICE_ENDPOINT"
