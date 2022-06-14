#!/usr/bin/env bash
# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

CONFIG_FILE=/opt/harness/config.yml
NEWRELIC_FILE=/opt/harness/newrelic.yml
REDISSON_CACHE_FILE=/opt/harness/redisson-jcache.yaml

replace_key_value () {
  CONFIG_KEY="$1";
  CONFIG_VALUE="$2";
  if [[ "" != "$CONFIG_VALUE" ]]; then
    yq write -i $CONFIG_FILE $CONFIG_KEY $CONFIG_VALUE
  fi
}

yq delete -i $CONFIG_FILE server.applicationConnectors[0]
yq delete -i $CONFIG_FILE grpcServerConfig.connectors[0]

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
  yq write -i $CONFIG_FILE server.applicationConnectors[0].port "9090"
fi

if [[ "" != "$GRPC_SERVER_PORT" ]]; then
  yq write -i $CONFIG_FILE grpcServerConfig.connectors[0].port "$GRPC_SERVER_PORT"
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


if [[ "" != "$ELASTICSEARCH_URI" ]]; then
  yq write -i $CONFIG_FILE elasticsearch.uri "$ELASTICSEARCH_URI"
fi

if [[ "" != "$ELASTICSEARCH_INDEX_SUFFIX" ]]; then
  yq write -i $CONFIG_FILE elasticsearch.indexSuffix "$ELASTICSEARCH_INDEX_SUFFIX"
fi

if [[ "" != "$ELASTICSEARCH_MONGO_TAG_NAME" ]]; then
 yq write -i $CONFIG_FILE elasticsearch.mongoTagKey "$ELASTICSEARCH_MONGO_TAG_NAME"
fi

if [[ "" != "$ELASTICSEARCH_MONGO_TAG_VALUE" ]]; then
 yq write -i $CONFIG_FILE elasticsearch.mongoTagValue "$ELASTICSEARCH_MONGO_TAG_VALUE"
fi

if [[ "" != "$MONGO_LOCK_URI" ]]; then
  yq write -i $CONFIG_FILE mongo.locksUri "${MONGO_LOCK_URI//\\&/&}"
fi

yq write -i $CONFIG_FILE server.requestLog.appenders[0].threshold "TRACE"

if [[ "$STACK_DRIVER_LOGGING_ENABLED" == "true" ]]; then
  yq delete -i $CONFIG_FILE logging.appenders[2]
  yq delete -i $CONFIG_FILE logging.appenders[0]
  yq write -i $CONFIG_FILE logging.appenders[0].stackdriverLogEnabled "true"
else
  if [[ "$ROLLING_FILE_LOGGING_ENABLED" == "true" ]]; then
    yq delete -i $CONFIG_FILE logging.appenders[1]
    yq write -i $CONFIG_FILE logging.appenders[1].currentLogFilename "/opt/harness/logs/portal.log"
    yq write -i $CONFIG_FILE logging.appenders[1].archivedLogFilenamePattern "/opt/harness/logs/portal.%d.%i.log"
  else
    yq delete -i $CONFIG_FILE logging.appenders[2]
    yq delete -i $CONFIG_FILE logging.appenders[1]
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

if [[ "" != "$KUBECTL_VERSION" ]]; then
  yq write -i $CONFIG_FILE kubectlVersion "$KUBECTL_VERSION"
fi

yq write -i $NEWRELIC_FILE common.license_key "$NEWRELIC_LICENSE_KEY"

if [[ "$DISABLE_NEW_RELIC" == "true" ]]; then
  yq write -i $NEWRELIC_FILE common.agent_enabled false
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


if [[ "" != "$FEATURES" ]]; then
  yq write -i $CONFIG_FILE featuresEnabled "$FEATURES"
fi

if [[ "" != "$SAMPLE_TARGET_ENV" ]]; then
  yq write -i $CONFIG_FILE sampleTargetEnv "$SAMPLE_TARGET_ENV"
fi

if [[ "" != "$SAMPLE_TARGET_STATUS_HOST" ]]; then
  yq write -i $CONFIG_FILE sampleTargetStatusHost "$SAMPLE_TARGET_STATUS_HOST"
fi

if [[ "" != "$GLOBAL_WHITELIST" ]]; then
  yq write -i $CONFIG_FILE globalWhitelistConfig.filters "$GLOBAL_WHITELIST"
fi

if [[ "" != "$SMTP_HOST" ]]; then
  yq write -i $CONFIG_FILE smtp.host "$SMTP_HOST"
fi

if [[ "" != "$SMTP_USERNAME" ]]; then
  yq write -i $CONFIG_FILE smtp.username "$SMTP_USERNAME"
fi

if [[ "" != "$SMTP_PASSWORD" ]]; then
  yq write -i $CONFIG_FILE smtp.password "$SMTP_PASSWORD"
fi

if [[ "" != "$SMTP_USE_SSL" ]]; then
  yq write -i $CONFIG_FILE smtp.useSSL "$SMTP_USE_SSL"
fi

if [[ "" != "$MARKETO_ENABLED" ]]; then
  yq write -i $CONFIG_FILE marketoConfig.enabled "$MARKETO_ENABLED"
fi

if [[ "" != "$MARKETO_URL" ]]; then
  yq write -i $CONFIG_FILE marketoConfig.url "$MARKETO_URL"
fi

if [[ "" != "$MARKETO_CLIENT_ID" ]]; then
  yq write -i $CONFIG_FILE marketoConfig.clientId "$MARKETO_CLIENT_ID"
fi

if [[ "" != "$MARKETO_CLIENT_SECRET" ]]; then
  yq write -i $CONFIG_FILE marketoConfig.clientSecret "$MARKETO_CLIENT_SECRET"
fi

if [[ "" != "$SEGMENT_ENABLED" ]]; then
  yq write -i $CONFIG_FILE segmentConfig.enabled "$SEGMENT_ENABLED"
fi

if [[ "" != "$SEGMENT_URL" ]]; then
  yq write -i $CONFIG_FILE segmentConfig.url "$SEGMENT_URL"
fi

if [[ "" != "$SEGMENT_APIKEY" ]]; then
  yq write -i $CONFIG_FILE segmentConfig.apiKey "$SEGMENT_APIKEY"
fi

if [[ "" != "$SALESFORCE_USERNAME" ]]; then
  yq write -i $CONFIG_FILE salesforceConfig.userName "$SALESFORCE_USERNAME"
fi

if [[ "" != "$SALESFORCE_PASSWORD" ]]; then
  yq write -i $CONFIG_FILE salesforceConfig.password "$SALESFORCE_PASSWORD"
fi

if [[ "" != "$SALESFORCE_CONSUMER_KEY" ]]; then
  yq write -i $CONFIG_FILE salesforceConfig.consumerKey "$SALESFORCE_CONSUMER_KEY"
fi

if [[ "" != "$SALESFORCE_CONSUMER_SECRET" ]]; then
  yq write -i $CONFIG_FILE salesforceConfig.consumerSecret "$SALESFORCE_CONSUMER_SECRET"
fi

if [[ "" != "$SALESFORCE_GRANT_TYPE" ]]; then
  yq write -i $CONFIG_FILE salesforceConfig.grantType "$SALESFORCE_GRANT_TYPE"
fi

if [[ "" != "$SALESFORCE_LOGIN_INSTANCE_DOMAIN" ]]; then
  yq write -i $CONFIG_FILE salesforceConfig.loginInstanceDomain "$SALESFORCE_LOGIN_INSTANCE_DOMAIN"
fi

if [[ "" != "$SALESFORCE_API_VERSION" ]]; then
  yq write -i $CONFIG_FILE salesforceConfig.apiVersion "$SALESFORCE_API_VERSION"
fi

if [[ "" != "$SALESFORCE_INTEGRATION_ENABLED" ]]; then
  yq write -i $CONFIG_FILE salesforceConfig.enabled "$SALESFORCE_INTEGRATION_ENABLED"
fi

if [[ "" != "$CE_SETUP_CONFIG_AWS_ACCOUNT_ID" ]]; then
  yq write -i $CONFIG_FILE ceSetUpConfig.awsAccountId "$CE_SETUP_CONFIG_AWS_ACCOUNT_ID"
fi

if [[ "" != "$CE_SETUP_CONFIG_AWS_S3_BUCKET_NAME" ]]; then
  yq write -i $CONFIG_FILE ceSetUpConfig.awsS3BucketName "$CE_SETUP_CONFIG_AWS_S3_BUCKET_NAME"
fi

if [[ "" != "$CE_SETUP_CONFIG_GCP_PROJECT_ID" ]]; then
  yq write -i $CONFIG_FILE ceSetUpConfig.gcpProjectId "$CE_SETUP_CONFIG_GCP_PROJECT_ID"
fi

if [[ "" != "$CE_SETUP_CONFIG_AWS_ROLE_NAME" ]]; then
  yq write -i $CONFIG_FILE ceSetUpConfig.awsRoleName "$CE_SETUP_CONFIG_AWS_ROLE_NAME"
fi

if [[ "" != "$CE_SETUP_CONFIG_SAMPLE_ACCOUNT_ID" ]]; then
  yq write -i $CONFIG_FILE ceSetUpConfig.sampleAccountId "$CE_SETUP_CONFIG_SAMPLE_ACCOUNT_ID"
fi

if [[ "" != "$CE_SETUP_CONFIG_AWS_ACCESS_KEY" ]]; then
  yq write -i $CONFIG_FILE ceSetUpConfig.awsAccessKey "$CE_SETUP_CONFIG_AWS_ACCESS_KEY"
fi

if [[ "" != "$CE_SETUP_CONFIG_AWS_SECRET_KEY" ]]; then
  yq write -i $CONFIG_FILE ceSetUpConfig.awsSecretKey "$CE_SETUP_CONFIG_AWS_SECRET_KEY"
fi

if [[ "" != "$CE_SETUP_CONFIG_MASTER_CLOUD_FORMATION" ]]; then
  yq write -i $CONFIG_FILE ceSetUpConfig.masterAccountCloudFormationTemplateLink "$CE_SETUP_CONFIG_MASTER_CLOUD_FORMATION"
fi

if [[ "" != "$CE_SETUP_CONFIG_LINKED_CLOUD_FORMATION" ]]; then
  yq write -i $CONFIG_FILE ceSetUpConfig.linkedAccountCloudFormationTemplateLink "$CE_SETUP_CONFIG_LINKED_CLOUD_FORMATION"
fi

if [[ "" != "$CE_SETUP_CONFIG_AZURE_CLIENTSECRET" ]]; then
  yq write -i $CONFIG_FILE ceSetUpConfig.azureAppClientSecret "$CE_SETUP_CONFIG_AZURE_CLIENTSECRET"
fi

if [[ "" != "$CE_SETUP_CONFIG_AZURE_CLIENTID" ]]; then
  yq write -i $CONFIG_FILE ceSetUpConfig.azureAppClientId "$CE_SETUP_CONFIG_AZURE_CLIENTID"
fi

if [[ "" != "$DATADOG_ENABLED" ]]; then
  yq write -i $CONFIG_FILE datadogConfig.enabled "$DATADOG_ENABLED"
fi

if [[ "" != "$DATADOG_APIKEY" ]]; then
  yq write -i $CONFIG_FILE datadogConfig.apiKey "$DATADOG_APIKEY"
fi

if [[ "" != "$DELEGATE_DOCKER_IMAGE" ]]; then
  yq write -i $CONFIG_FILE portal.delegateDockerImage "$DELEGATE_DOCKER_IMAGE"
fi

if [[ "" != "$OPTIONAL_DELEGATE_TASK_REJECT_AT_LIMIT" ]]; then
  yq write -i $CONFIG_FILE portal.optionalDelegateTaskRejectAtLimit "$OPTIONAL_DELEGATE_TASK_REJECT_AT_LIMIT"
fi

if [[ "" != "$EXECUTION_LOG_DATA_STORE" ]]; then
  yq write -i $CONFIG_FILE executionLogStorageMode "$EXECUTION_LOG_DATA_STORE"
fi

if [[ "" != "$FILE_STORAGE" ]]; then
  yq write -i $CONFIG_FILE fileStorageMode "$FILE_STORAGE"
fi

if [[ "" != "$CLUSTER_NAME" ]]; then
  yq write -i $CONFIG_FILE clusterName "$CLUSTER_NAME"
fi

if [[ "" != "$DEPLOYMENT_CLUSTER_NAME" ]]; then
  yq write -i $CONFIG_FILE deploymentClusterName "$DEPLOYMENT_CLUSTER_NAME"
fi

if [[ "" != "$BACKGROUND_SCHEDULER_CLUSTERED" ]]; then
  yq write -i $CONFIG_FILE backgroundScheduler.clustered "$BACKGROUND_SCHEDULER_CLUSTERED"
fi

if [[ "" != "$ENABLE_CRONS" ]]; then
  yq write -i $CONFIG_FILE enableIterators "$ENABLE_CRONS"
  yq write -i $CONFIG_FILE backgroundScheduler.enabled "$ENABLE_CRONS"
  yq write -i $CONFIG_FILE serviceScheduler.enabled "$ENABLE_CRONS"
fi

if [[ "" != "$ALLOW_TRIAL_REGISTRATION" ]]; then
  yq write -i $CONFIG_FILE trialRegistrationAllowed "$ALLOW_TRIAL_REGISTRATION"
fi

if [[ "" != "$EVENTS_FRAMEWORK_AVAILABLE_IN_ONPREM" ]]; then
  yq write -i $CONFIG_FILE eventsFrameworkAvailableInOnPrem "$EVENTS_FRAMEWORK_AVAILABLE_IN_ONPREM"
else
  yq write -i $CONFIG_FILE eventsFrameworkAvailableInOnPrem "false"
fi

if [[ "" != "$ALLOW_TRIAL_REGISTRATION_FOR_BUGATHON" ]]; then
  yq write -i $CONFIG_FILE trialRegistrationAllowedForBugathon "$ALLOW_TRIAL_REGISTRATION_FOR_BUGATHON"
fi

if [[ "" != "$GITHUB_OAUTH_CLIENT" ]]; then
  yq write -i $CONFIG_FILE githubConfig.clientId "$GITHUB_OAUTH_CLIENT"
fi

if [[ "" != "$GITHUB_OAUTH_SECRET" ]]; then
  yq write -i $CONFIG_FILE githubConfig.clientSecret "$GITHUB_OAUTH_SECRET"
fi

if [[ "" != "$GITHUB_OAUTH_CALLBACK_URL" ]]; then
  yq write -i $CONFIG_FILE githubConfig.callbackUrl "$GITHUB_OAUTH_CALLBACK_URL"
fi

if [[ "" != "$AZURE_OAUTH_CLIENT" ]]; then
  yq write -i $CONFIG_FILE azureConfig.clientId "$AZURE_OAUTH_CLIENT"
fi

if [[ "" != "$AZURE_OAUTH_SECRET" ]]; then
  yq write -i $CONFIG_FILE azureConfig.clientSecret "$AZURE_OAUTH_SECRET"
fi

if [[ "" != "$AZURE_OAUTH_CALLBACK_URL" ]]; then
  yq write -i $CONFIG_FILE azureConfig.callbackUrl "$AZURE_OAUTH_CALLBACK_URL"
fi

if [[ "" != "$GOOGLE_OAUTH_CLIENT" ]]; then
  yq write -i $CONFIG_FILE googleConfig.clientId "$GOOGLE_OAUTH_CLIENT"
fi

if [[ "" != "$GOOGLE_OAUTH_SECRET" ]]; then
  yq write -i $CONFIG_FILE googleConfig.clientSecret "$GOOGLE_OAUTH_SECRET"
fi

if [[ "" != "$GOOGLE_OAUTH_CALLBACK_URL" ]]; then
  yq write -i $CONFIG_FILE googleConfig.callbackUrl "$GOOGLE_OAUTH_CALLBACK_URL"
fi

if [[ "" != "$BITBUCKET_OAUTH_CLIENT" ]]; then
  yq write -i $CONFIG_FILE bitbucketConfig.clientId "$BITBUCKET_OAUTH_CLIENT"
fi

if [[ "" != "$BITBUCKET_OAUTH_SECRET" ]]; then
  yq write -i $CONFIG_FILE bitbucketConfig.clientSecret "$BITBUCKET_OAUTH_SECRET"
fi

if [[ "" != "$BITBUCKET_OAUTH_CALLBACK_URL" ]]; then
  yq write -i $CONFIG_FILE bitbucketConfig.callbackUrl "$BITBUCKET_OAUTH_CALLBACK_URL"
fi

if [[ "" != "$GITLAB_OAUTH_CLIENT" ]]; then
  yq write -i $CONFIG_FILE gitlabConfig.clientId "$GITLAB_OAUTH_CLIENT"
fi

if [[ "" != "$GITLAB_OAUTH_SECRET" ]]; then
  yq write -i $CONFIG_FILE gitlabConfig.clientSecret "$GITLAB_OAUTH_SECRET"
fi

if [[ "" != "$GITLAB_OAUTH_CALLBACK_URL" ]]; then
  yq write -i $CONFIG_FILE gitlabConfig.callbackUrl "$GITLAB_OAUTH_CALLBACK_URL"
fi

if [[ "" != "$LINKEDIN_OAUTH_CLIENT" ]]; then
  yq write -i $CONFIG_FILE linkedinConfig.clientId "$LINKEDIN_OAUTH_CLIENT"
fi

if [[ "" != "$LINKEDIN_OAUTH_SECRET" ]]; then
  yq write -i $CONFIG_FILE linkedinConfig.clientSecret "$LINKEDIN_OAUTH_SECRET"
fi

if [[ "" != "$LINKEDIN_OAUTH_CALLBACK_URL" ]]; then
  yq write -i $CONFIG_FILE linkedinConfig.callbackUrl "$LINKEDIN_OAUTH_CALLBACK_URL"
fi

if [[ "" != "$AWS_MARKETPLACE_ACCESSKEY" ]]; then
  yq write -i $CONFIG_FILE mktPlaceConfig.awsAccessKey "$AWS_MARKETPLACE_ACCESSKEY"
fi

if [[ "" != "$AWS_MARKETPLACE_SECRETKEY" ]]; then
  yq write -i $CONFIG_FILE mktPlaceConfig.awsSecretKey "$AWS_MARKETPLACE_SECRETKEY"
fi

if [[ "" != "$AWS_MARKETPLACE_PRODUCTCODE" ]]; then
  yq write -i $CONFIG_FILE mktPlaceConfig.awsMarketPlaceProductCode "$AWS_MARKETPLACE_PRODUCTCODE"
fi

if [[ "" != "$AWS_MARKETPLACE_CE_PRODUCTCODE" ]]; then
  yq write -i $CONFIG_FILE mktPlaceConfig.awsMarketPlaceCeProductCode "$AWS_MARKETPLACE_CE_PRODUCTCODE"
fi

if [[ "" != "$ALLOW_BLACKLISTED_EMAIL_DOMAINS" ]]; then
  yq write -i $CONFIG_FILE blacklistedEmailDomainsAllowed "$ALLOW_BLACKLISTED_EMAIL_DOMAINS"
fi

if [[ "" != "$ALLOW_PWNED_PASSWORDS" ]]; then
  yq write -i $CONFIG_FILE pwnedPasswordsAllowed "$ALLOW_PWNED_PASSWORDS"
fi

if [[ "" != "$TIMESCALEDB_URI" ]]; then
  yq write -i $CONFIG_FILE timescaledb.timescaledbUrl "$TIMESCALEDB_URI"
fi

if [[ "" != "$TIMESCALEDB_USERNAME" ]]; then
  yq write -i $CONFIG_FILE timescaledb.timescaledbUsername "$TIMESCALEDB_USERNAME"
fi

if [[ "" != "$TIMESCALEDB_PASSWORD" ]]; then
  yq write -i $CONFIG_FILE timescaledb.timescaledbPassword "$TIMESCALEDB_PASSWORD"
fi

if [[ "" != "$TIMESCALEDB_CONNECT_TIMEOUT" ]]; then
  yq write -i $CONFIG_FILE timescaledb.connectTimeout "$TIMESCALEDB_CONNECT_TIMEOUT"
fi

if [[ "" != "$TIMESCALEDB_SOCKET_TIMEOUT" ]]; then
  yq write -i $CONFIG_FILE timescaledb.socketTimeout "$TIMESCALEDB_SOCKET_TIMEOUT"
fi

if [[ "" != "$TIMESCALEDB_LOGUNCLOSED" ]]; then
  yq write -i $CONFIG_FILE timescaledb.logUnclosedConnections "$TIMESCALEDB_LOGUNCLOSED"
fi

if [[ "" != "$TIMESCALEDB_LOGGERLEVEL" ]]; then
  yq write -i $CONFIG_FILE timescaledb.loggerLevel "$TIMESCALEDB_LOGGERLEVEL"
fi

if [[ "$TIMESCALEDB_HEALTH_CHECK_NEEDED" == "true" ]]; then
  yq write -i $CONFIG_FILE timescaledb.isHealthCheckNeeded "$TIMESCALEDB_HEALTH_CHECK_NEEDED"
fi

if [[ "$SEARCH_ENABLED" == "true" ]]; then
  yq write -i $CONFIG_FILE searchEnabled true
fi

if [[ "$GRAPHQL_ENABLED" == "false" ]]; then
  yq write -i $CONFIG_FILE graphQLEnabled false
fi

if [[ "$MONGO_DEBUGGING_ENABLED" == "true" ]]; then
  yq write -i $CONFIG_FILE logging.loggers.[org.mongodb.morphia.query] TRACE
  yq write -i $CONFIG_FILE logging.loggers.connection TRACE
fi

if [[ "" != "$AZURE_MARKETPLACE_ACCESSKEY" ]]; then
  yq write -i $CONFIG_FILE mktPlaceConfig.azureMarketplaceAccessKey "$AZURE_MARKETPLACE_ACCESSKEY"
fi

if [[ "" != "$AZURE_MARKETPLACE_SECRETKEY" ]]; then
  yq write -i $CONFIG_FILE mktPlaceConfig.azureMarketplaceSecretKey "$AZURE_MARKETPLACE_SECRETKEY"
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
    yq write -i $REDISSON_CACHE_FILE sentinelServersConfig.sentinelAddresses.[+] "${REDIS_SENTINEL_URL}"
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

if [[ "" != "$GCP_MARKETPLACE_ENABLED" ]]; then
    yq write -i $CONFIG_FILE gcpMarketplaceConfig.enabled "$GCP_MARKETPLACE_ENABLED"
fi

if [[ "" != "$GCP_MARKETPLACE_SUBSCRIPTION_NAME" ]]; then
    yq write -i $CONFIG_FILE gcpMarketplaceConfig.subscriptionName "$GCP_MARKETPLACE_SUBSCRIPTION_NAME"
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

if [[ "" != "$COMMAND_LIBRARY_SERVICE_BASE_URL" ]]; then
  yq write -i $CONFIG_FILE commandLibraryServiceConfig.baseUrl "$COMMAND_LIBRARY_SERVICE_BASE_URL"
fi

if [[ "" != "$BUGSNAG_API_KEY" ]]; then
  yq write -i $CONFIG_FILE bugsnagApiKey "$BUGSNAG_API_KEY"
fi

if [[ "" != "$ACCOUNT_LICENSE_CHECK_JOB_FREQUENCY" ]]; then
  yq write -i $CONFIG_FILE jobsFrequencyConfig.accountLicenseCheckJobFrequencyInMinutes "$ACCOUNT_LICENSE_CHECK_JOB_FREQUENCY"
fi

if [[ "" != "$ACCOUNT_DELETION_JOB_FREQUENCY" ]]; then
  yq write -i $CONFIG_FILE jobsFrequencyConfig.accountDeletionJobFrequencyInMinutes "$ACCOUNT_DELETION_JOB_FREQUENCY"
fi

if [[ "" != "$MANAGER_TO_COMMAND_LIBRARY_SERVICE_SECRET" ]]; then
  yq write -i $CONFIG_FILE commandLibraryServiceConfig.managerToCommandLibraryServiceSecret "$MANAGER_TO_COMMAND_LIBRARY_SERVICE_SECRET"
fi

if [[ "" != "$DELEGATE_SERVICE_TARGET" ]]; then
  yq write -i $CONFIG_FILE grpcDelegateServiceClientConfig.target "$DELEGATE_SERVICE_TARGET"
fi

if [[ "" != "$DELEGATE_SERVICE_AUTHORITY" ]]; then
  yq write -i $CONFIG_FILE grpcDelegateServiceClientConfig.authority "$DELEGATE_SERVICE_AUTHORITY"
fi

if [[ "" != "$DELEGATE_SERVICE_MANAGEMENT_AUTHORITY" ]]; then
  yq write -i $CONFIG_FILE grpcDMSClientConfig.authority "$DELEGATE_SERVICE_MANAGEMENT_AUTHORITY"
fi

if [[ "" != "$DELEGATE_SERVICE_MANAGEMENT_TARGET" ]]; then
  yq write -i $CONFIG_FILE grpcDMSClientConfig.target "$DELEGATE_SERVICE_MANAGEMENT_TARGET"
fi

if [[ "" != "$DELEGATE_SERVICE_MANAGEMENT_SECRET" ]]; then
  yq write -i $CONFIG_FILE dmsSecret "$DELEGATE_SERVICE_MANAGEMENT_SECRET"
fi


if [[ "" != "$DELEGATE_GRPC_TARGET" ]]; then
  yq write -i $CONFIG_FILE grpcOnpremDelegateClientConfig.target "$DELEGATE_GRPC_TARGET"
fi

if [[ "" != "$DELEGATE_GRPC_AUTHORITY" ]]; then
  yq write -i $CONFIG_FILE grpcOnpremDelegateClientConfig.authority "$DELEGATE_GRPC_AUTHORITY"
fi

if [[ "" != "$NG_MANAGER_AUTHORITY" ]]; then
  yq write -i $CONFIG_FILE grpcClientConfig.authority "$NG_MANAGER_AUTHORITY"
fi

if [[ "" != "$NG_MANAGER_TARGET" ]]; then
  yq write -i $CONFIG_FILE grpcClientConfig.target "$NG_MANAGER_TARGET"
fi

if [[ "" != "$REMINDERS_BEFORE_ACCOUNT_DELETION" ]]; then
  yq write -i $CONFIG_FILE numberOfRemindersBeforeAccountDeletion "$REMINDERS_BEFORE_ACCOUNT_DELETION"
fi

if [[ "" != "$EXPORT_DATA_BATCH_SIZE" ]]; then
  yq write -i $CONFIG_FILE exportAccountDataBatchSize "$EXPORT_DATA_BATCH_SIZE"
fi

if [[ "" != "$COMMAND_LIBRARY_PUBLISHING_ALLOWED" ]]; then
  yq write -i $CONFIG_FILE commandLibraryServiceConfig.publishingAllowed "$COMMAND_LIBRARY_PUBLISHING_ALLOWED"
fi

if [[ "" != "$COMMAND_LIBRARY_PUBLISHING_SECRET" ]]; then
  yq write -i $CONFIG_FILE commandLibraryServiceConfig.publishingSecret "$COMMAND_LIBRARY_PUBLISHING_SECRET"
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
replace_key_value ngAuthUIEnabled "$HARNESS_ENABLE_NG_AUTH_UI_PLACEHOLDER"
replace_key_value portal.gatewayPathPrefix "$GATEWAY_PATH_PREFIX"
replace_key_value portal.zendeskBaseUrl "$ZENDESK_BASE_URL"

if [[ "" != "$NG_MANAGER_BASE_URL" ]]; then
  yq write -i $CONFIG_FILE ngManagerServiceHttpClientConfig.baseUrl "$NG_MANAGER_BASE_URL"
fi

if [[ "" != "$ENABLE_USER_CHANGESTREAM" ]]; then
  yq write -i $CONFIG_FILE userChangeStreamEnabled "$ENABLE_USER_CHANGESTREAM"
fi

if [[ "" != "$DISABLE_DELEGATE_MGMT_IN_MANAGER" ]]; then
  yq write -i $CONFIG_FILE disableDelegateMgmtInManager "$DISABLE_DELEGATE_MGMT_IN_MANAGER"
fi

if [[ "" != "$LDAP_GROUP_SYNC_INTERVAL" ]]; then
  yq write -i $CONFIG_FILE ldapSyncJobConfig.syncInterval "$LDAP_GROUP_SYNC_INTERVAL"
fi

if [[ "" != "$LDAP_GROUP_SYNC_POOL_SIZE" ]]; then
  yq write -i $CONFIG_FILE ldapSyncJobConfig.poolSize "$LDAP_GROUP_SYNC_POOL_SIZE"
fi

if [[ "" != "$LDAP_GROUP_SYNC_DEFAULT_CRON" ]]; then
  yq write -i $CONFIG_FILE ldapSyncJobConfig.defaultCronExpression "$LDAP_GROUP_SYNC_DEFAULT_CRON"
fi
