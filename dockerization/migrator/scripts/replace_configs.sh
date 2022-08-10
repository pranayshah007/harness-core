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

yq delete -i $CONFIG_FILE 'server.applicationConnectors.(type==h2)'
yq delete -i $CONFIG_FILE 'cg.grpcServerConfig.connectors.(secure==true)'

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
  yq write -i $CONFIG_FILE cg.grpcServerConfig.connectors[0].port "$GRPC_SERVER_PORT"
fi

if [[ "" != "$SERVER_MAX_THREADS" ]]; then
  yq write -i $CONFIG_FILE server.maxThreads "$SERVER_MAX_THREADS"
fi

if [[ "" != "$UI_SERVER_URL" ]]; then
  yq write -i $CONFIG_FILE cg.portal.url "$UI_SERVER_URL"
fi

if [[ "" != "$AUTHTOKENEXPIRYINMILLIS" ]]; then
  yq write -i $CONFIG_FILE cg.portal.authTokenExpiryInMillis "$AUTHTOKENEXPIRYINMILLIS"
fi

if [[ "" != "$EXTERNAL_GRAPHQL_RATE_LIMIT" ]]; then
  yq write -i $CONFIG_FILE cg.portal.externalGraphQLRateLimitPerMinute "$EXTERNAL_GRAPHQL_RATE_LIMIT"
fi

if [[ "" != "$CUSTOM_DASH_GRAPHQL_RATE_LIMIT" ]]; then
  yq write -i $CONFIG_FILE cg.portal.customDashGraphQLRateLimitPerMinute "$CUSTOM_DASH_GRAPHQL_RATE_LIMIT"
fi

if [[ "" != "$ALLOWED_ORIGINS" ]]; then
  yq write -i $CONFIG_FILE cg.portal.allowedOrigins "$ALLOWED_ORIGINS"
fi

if [[ "" != "$STORE_REQUEST_PAYLOAD" ]]; then
  yq write -i $CONFIG_FILE cg.auditConfig.storeRequestPayload "$STORE_REQUEST_PAYLOAD"
fi

if [[ "" != "$STORE_RESPONSE_PAYLOAD" ]]; then
  yq write -i $CONFIG_FILE cg.auditConfig.storeResponsePayload "$STORE_RESPONSE_PAYLOAD"
fi

if [[ "" != "$MONGO_URI" ]]; then
  yq write -i $CONFIG_FILE cg.mongo.uri "${MONGO_URI//\\&/&}"
fi

if [[ "" != "$MONGO_HOSTS_AND_PORTS" ]]; then
  yq delete -i $CONFIG_FILE cg.mongo.uri
  yq write -i $CONFIG_FILE cg.mongo.username "$MONGO_USERNAME"
  yq write -i $CONFIG_FILE cg.mongo.password "$MONGO_PASSWORD"
  yq write -i $CONFIG_FILE cg.mongo.database "$MONGO_DATABASE"
  yq write -i $CONFIG_FILE cg.mongo.schema "$MONGO_SCHEMA"
  write_mongo_hosts_and_ports cg.mongo "$MONGO_HOSTS_AND_PORTS"
  write_mongo_params cg.mongo "$MONGO_PARAMS"
fi

if [[ "" != "$MONGO_TRACE_MODE" ]]; then
  yq write -i $CONFIG_FILE cg.mongo.traceMode $MONGO_TRACE_MODE
fi

if [[ "" != "$MONGO_SSL_CONFIG" ]]; then
  yq write -i $CONFIG_FILE cg.mongo.mongoSSLConfig.mongoSSLEnabled "$MONGO_SSL_CONFIG"
fi

if [[ "" != "$MONGO_SSL_CA_TRUST_STORE_PATH" ]]; then
  yq write -i $CONFIG_FILE cg.mongo.mongoSSLConfig.mongoTrustStorePath "$MONGO_SSL_CA_TRUST_STORE_PATH"
fi

if [[ "" != "$MONGO_SSL_CA_TRUST_STORE_PASSWORD" ]]; then
  yq write -i $CONFIG_FILE cg.mongo.mongoSSLConfig.mongoTrustStorePassword "$MONGO_SSL_CA_TRUST_STORE_PASSWORD"
fi

if [[ "" != "$MONGO_CONNECT_TIMEOUT" ]]; then
  yq write -i $CONFIG_FILE cg.mongo.connectTimeout $MONGO_CONNECT_TIMEOUT
fi

if [[ "" != "$MONGO_SERVER_SELECTION_TIMEOUT" ]]; then
  yq write -i $CONFIG_FILE cg.mongo.serverSelectionTimeout $MONGO_SERVER_SELECTION_TIMEOUT
fi

if [[ "" != "$MAX_CONNECTION_IDLE_TIME" ]]; then
  yq write -i $CONFIG_FILE cg.mongo.maxConnectionIdleTime $MAX_CONNECTION_IDLE_TIME
fi

if [[ "" != "$MONGO_CONNECTIONS_PER_HOST" ]]; then
  yq write -i $CONFIG_FILE cg.mongo.connectionsPerHost $MONGO_CONNECTIONS_PER_HOST
fi

if [[ "" != "$MONGO_INDEX_MANAGER_MODE" ]]; then
  yq write -i $CONFIG_FILE cg.mongo.indexManagerMode $MONGO_INDEX_MANAGER_MODE
fi

if [[ "" != "$EVEMTS_MONGO_INDEX_MANAGER_MODE" ]]; then
  yq write -i $CONFIG_FILE cg.events-mongo.indexManagerMode $EVEMTS_MONGO_INDEX_MANAGER_MODE
fi

if [[ "" != "$EVENTS_MONGO_URI" ]]; then
  yq write -i $CONFIG_FILE cg.events-mongo.uri "$EVENTS_MONGO_URI"
else
  if [[ "" != "$EVENTS_MONGO_HOSTS_AND_PORTS" ]]; then
    yq delete -i $CONFIG_FILE cg.events-mongo.uri
    yq write -i $CONFIG_FILE cg.events-mongo.username "$EVENTS_MONGO_USERNAME"
    yq write -i $CONFIG_FILE cg.events-mongo.password "$EVENTS_MONGO_PASSWORD"
    yq write -i $CONFIG_FILE cg.events-mongo.database "$EVENTS_MONGO_DATABASE"
    yq write -i $CONFIG_FILE cg.events-mongo.schema "$EVENTS_MONGO_SCHEMA"
    write_mongo_hosts_and_ports cg.events-mongo "$EVENTS_MONGO_HOSTS_AND_PORTS"
    write_mongo_params cg.events-mongo "$EVENTS_MONGO_PARAMS"
  else
    yq delete -i $CONFIG_FILE cg.events-mongo
  fi
fi

if [[ "" != "$CF_CLIENT_API_KEY" ]]; then
  yq write -i $CONFIG_FILE cg.cfClientConfig.apiKey "$CF_CLIENT_API_KEY"
fi

if [[ "" != "$CF_CLIENT_CONFIG_URL" ]]; then
  yq write -i $CONFIG_FILE cg.cfClientConfig.configUrl "$CF_CLIENT_CONFIG_URL"
fi

if [[ "" != "$CF_CLIENT_EVENT_URL" ]]; then
  yq write -i $CONFIG_FILE cg.cfClientConfig.eventUrl "$CF_CLIENT_EVENT_URL"
fi

if [[ "" != "$CF_CLIENT_ANALYTICS_ENABLED" ]]; then
  yq write -i $CONFIG_FILE cg.cfClientConfig.analyticsEnabled "$CF_CLIENT_ANALYTICS_ENABLED"
fi

if [[ "" != "$CF_CLIENT_CONNECTION_TIMEOUT" ]]; then
  yq write -i $CONFIG_FILE cg.cfClientConfig.connectionTimeout "$CF_CLIENT_CONNECTION_TIMEOUT"
fi

if [[ "" != "$CF_CLIENT_READ_TIMEOUT" ]]; then
  yq write -i $CONFIG_FILE cg.cfClientConfig.readTimeout "$CF_CLIENT_READ_TIMEOUT"
fi

if [[ "" != "$CF_MIGRATION_ENABLED" ]]; then
  yq write -i $CONFIG_FILE cg.cfMigrationConfig.enabled "$CF_MIGRATION_ENABLED"
fi

if [[ "" != "$CF_MIGRATION_ADMIN_URL" ]]; then
  yq write -i $CONFIG_FILE cg.cfMigrationConfig.adminUrl "$CF_MIGRATION_ADMIN_URL"
fi

if [[ "" != "$CF_MIGRATION_API_KEY" ]]; then
  yq write -i $CONFIG_FILE cg.cfMigrationConfig.apiKey "$CF_MIGRATION_API_KEY"
fi

if [[ "" != "$CF_MIGRATION_ACCOUNT" ]]; then
  yq write -i $CONFIG_FILE cg.cfMigrationConfig.account "$CF_MIGRATION_ACCOUNT"
fi

if [[ "" != "$CF_MIGRATION_ORG" ]]; then
  yq write -i $CONFIG_FILE cg.cfMigrationConfig.org "$CF_MIGRATION_ORG"
fi

if [[ "" != "$CF_MIGRATION_PROJECT" ]]; then
  yq write -i $CONFIG_FILE cg.cfMigrationConfig.project "$CF_MIGRATION_PROJECT"
fi

if [[ "" != "$CF_MIGRATION_ENVIRONMENT" ]]; then
  yq write -i $CONFIG_FILE cg.cfMigrationConfig.environment "$CF_MIGRATION_ENVIRONMENT"
fi

replace_key_value cg.featureFlagConfig.featureFlagSystem "$FEATURE_FLAG_SYSTEM"
replace_key_value cg.featureFlagConfig.syncFeaturesToCF "$SYNC_FEATURES_TO_CF"


if [[ "" != "$ELASTICSEARCH_URI" ]]; then
  yq write -i $CONFIG_FILE cg.elasticsearch.uri "$ELASTICSEARCH_URI"
fi

if [[ "" != "$ELASTICSEARCH_INDEX_SUFFIX" ]]; then
  yq write -i $CONFIG_FILE cg.elasticsearch.indexSuffix "$ELASTICSEARCH_INDEX_SUFFIX"
fi

if [[ "" != "$ELASTICSEARCH_MONGO_TAG_NAME" ]]; then
 yq write -i $CONFIG_FILE cg.elasticsearch.mongoTagKey "$ELASTICSEARCH_MONGO_TAG_NAME"
fi

if [[ "" != "$ELASTICSEARCH_MONGO_TAG_VALUE" ]]; then
 yq write -i $CONFIG_FILE cg.elasticsearch.mongoTagValue "$ELASTICSEARCH_MONGO_TAG_VALUE"
fi

if [[ "" != "$MONGO_LOCK_URI" ]]; then
  yq write -i $CONFIG_FILE cg.mongo.locksUri "${MONGO_LOCK_URI//\\&/&}"
fi

yq write -i $CONFIG_FILE server.requestLog.appenders[0].threshold "TRACE"

if [[ "$STACK_DRIVER_LOGGING_ENABLED" == "true" ]]; then
  yq delete -i $CONFIG_FILE 'logging.appenders.(type==file)'
  yq delete -i $CONFIG_FILE 'logging.appenders.(type==console)'
  yq write -i $CONFIG_FILE 'logging.appenders.(type==gke-console).stackdriverLogEnabled' "true"
else
  if [[ "$ROLLING_FILE_LOGGING_ENABLED" == "true" ]]; then
    yq delete -i $CONFIG_FILE 'logging.appenders.(type==gke-console)'
    yq write -i $CONFIG_FILE 'logging.appenders.(type==file).currentLogFilename' "/opt/harness/logs/portal.log"
    yq write -i $CONFIG_FILE 'logging.appenders.(type==file).archivedLogFilenamePattern' "/opt/harness/logs/portal.%d.%i.log"
  else
    yq delete -i $CONFIG_FILE 'logging.appenders.(type==file)'
    yq delete -i $CONFIG_FILE 'logging.appenders.(type==gke-console)'
  fi
fi

if [[ "" != "$WATCHER_METADATA_URL" ]]; then
  yq write -i $CONFIG_FILE cg.watcherMetadataUrl "$WATCHER_METADATA_URL"
fi

if [[ "" != "$DELEGATE_METADATA_URL" ]]; then
  yq write -i $CONFIG_FILE cg.delegateMetadataUrl "$DELEGATE_METADATA_URL"
fi

if [[ "" != "$API_URL" ]]; then
  yq write -i $CONFIG_FILE cg.apiUrl "$API_URL"
fi

if [[ "" != "$ENV_PATH" ]]; then
  yq write -i $CONFIG_FILE cg.envPath "$ENV_PATH"
fi

if [[ "" != "$DEPLOY_MODE" ]]; then
  yq write -i $CONFIG_FILE cg.deployMode "$DEPLOY_MODE"
fi

yq write -i $NEWRELIC_FILE cg.common.license_key "$NEWRELIC_LICENSE_KEY"

if [[ "$DISABLE_NEW_RELIC" == "true" ]]; then
  yq write -i $NEWRELIC_FILE cg.common.agent_enabled false
fi

if [[ "" != "$jwtPasswordSecret" ]]; then
  yq write -i $CONFIG_FILE cg.portal.jwtPasswordSecret "$jwtPasswordSecret"
fi

if [[ "" != "$jwtExternalServiceSecret" ]]; then
  yq write -i $CONFIG_FILE cg.portal.jwtExternalServiceSecret "$jwtExternalServiceSecret"
fi

if [[ "" != "$jwtZendeskSecret" ]]; then
  yq write -i $CONFIG_FILE cg.portal.jwtZendeskSecret "$jwtZendeskSecret"
fi

if [[ "" != "$jwtMultiAuthSecret" ]]; then
  yq write -i $CONFIG_FILE cg.portal.jwtMultiAuthSecret "$jwtMultiAuthSecret"
fi

if [[ "" != "$jwtSsoRedirectSecret" ]]; then
  yq write -i $CONFIG_FILE cg.portal.jwtSsoRedirectSecret "$jwtSsoRedirectSecret"
fi

if [[ "" != "$jwtAuthSecret" ]]; then
  yq write -i $CONFIG_FILE cg.portal.jwtAuthSecret "$jwtAuthSecret"
fi

if [[ "" != "$jwtMarketPlaceSecret" ]]; then
  yq write -i $CONFIG_FILE cg.portal.jwtMarketPlaceSecret "$jwtMarketPlaceSecret"
fi

if [[ "" != "$jwtIdentityServiceSecret" ]]; then
  yq write -i $CONFIG_FILE cg.portal.jwtIdentityServiceSecret "$jwtIdentityServiceSecret"
fi

if [[ "" != "$jwtDataHandlerSecret" ]]; then
  yq write -i $CONFIG_FILE cg.portal.jwtDataHandlerSecret "$jwtDataHandlerSecret"
fi

if [[ "" != "$jwtNextGenManagerSecret" ]]; then
  yq write -i $CONFIG_FILE cg.portal.jwtNextGenManagerSecret "$jwtNextGenManagerSecret"
fi

if [[ "" != "$FEATURES" ]]; then
  yq write -i $CONFIG_FILE cg.featuresEnabled "$FEATURES"
fi

if [[ "" != "$SAMPLE_TARGET_ENV" ]]; then
  yq write -i $CONFIG_FILE cg.sampleTargetEnv "$SAMPLE_TARGET_ENV"
fi

if [[ "" != "$SAMPLE_TARGET_STATUS_HOST" ]]; then
  yq write -i $CONFIG_FILE cg.sampleTargetStatusHost "$SAMPLE_TARGET_STATUS_HOST"
fi

if [[ "" != "$GLOBAL_WHITELIST" ]]; then
  yq write -i $CONFIG_FILE cg.globalWhitelistConfig.filters "$GLOBAL_WHITELIST"
fi

if [[ "" != "$SMTP_HOST" ]]; then
  yq write -i $CONFIG_FILE cg.smtp.host "$SMTP_HOST"
fi

if [[ "" != "$SMTP_USERNAME" ]]; then
  yq write -i $CONFIG_FILE cg.smtp.username "$SMTP_USERNAME"
fi

if [[ "" != "$SMTP_PASSWORD" ]]; then
  yq write -i $CONFIG_FILE cg.smtp.password "$SMTP_PASSWORD"
fi

if [[ "" != "$SMTP_USE_SSL" ]]; then
  yq write -i $CONFIG_FILE cg.smtp.useSSL "$SMTP_USE_SSL"
fi

if [[ "" != "$MARKETO_ENABLED" ]]; then
  yq write -i $CONFIG_FILE cg.marketoConfig.enabled "$MARKETO_ENABLED"
fi

if [[ "" != "$MARKETO_URL" ]]; then
  yq write -i $CONFIG_FILE cg.marketoConfig.url "$MARKETO_URL"
fi

if [[ "" != "$MARKETO_CLIENT_ID" ]]; then
  yq write -i $CONFIG_FILE cg.marketoConfig.clientId "$MARKETO_CLIENT_ID"
fi

if [[ "" != "$MARKETO_CLIENT_SECRET" ]]; then
  yq write -i $CONFIG_FILE cg.marketoConfig.clientSecret "$MARKETO_CLIENT_SECRET"
fi

if [[ "" != "$SEGMENT_ENABLED" ]]; then
  yq write -i $CONFIG_FILE cg.segmentConfig.enabled "$SEGMENT_ENABLED"
fi

if [[ "" != "$SEGMENT_URL" ]]; then
  yq write -i $CONFIG_FILE cg.segmentConfig.url "$SEGMENT_URL"
fi

if [[ "" != "$SEGMENT_APIKEY" ]]; then
  yq write -i $CONFIG_FILE cg.segmentConfig.apiKey "$SEGMENT_APIKEY"
fi

if [[ "" != "$SALESFORCE_USERNAME" ]]; then
  yq write -i $CONFIG_FILE cg.salesforceConfig.userName "$SALESFORCE_USERNAME"
fi

if [[ "" != "$SALESFORCE_PASSWORD" ]]; then
  yq write -i $CONFIG_FILE cg.salesforceConfig.password "$SALESFORCE_PASSWORD"
fi

if [[ "" != "$SALESFORCE_CONSUMER_KEY" ]]; then
  yq write -i $CONFIG_FILE cg.salesforceConfig.consumerKey "$SALESFORCE_CONSUMER_KEY"
fi

if [[ "" != "$SALESFORCE_CONSUMER_SECRET" ]]; then
  yq write -i $CONFIG_FILE cg.salesforceConfig.consumerSecret "$SALESFORCE_CONSUMER_SECRET"
fi

if [[ "" != "$SALESFORCE_GRANT_TYPE" ]]; then
  yq write -i $CONFIG_FILE cg.salesforceConfig.grantType "$SALESFORCE_GRANT_TYPE"
fi

if [[ "" != "$SALESFORCE_LOGIN_INSTANCE_DOMAIN" ]]; then
  yq write -i $CONFIG_FILE cg.salesforceConfig.loginInstanceDomain "$SALESFORCE_LOGIN_INSTANCE_DOMAIN"
fi

if [[ "" != "$SALESFORCE_API_VERSION" ]]; then
  yq write -i $CONFIG_FILE cg.salesforceConfig.apiVersion "$SALESFORCE_API_VERSION"
fi

if [[ "" != "$SALESFORCE_INTEGRATION_ENABLED" ]]; then
  yq write -i $CONFIG_FILE cg.salesforceConfig.enabled "$SALESFORCE_INTEGRATION_ENABLED"
fi

if [[ "" != "$CE_SETUP_CONFIG_AWS_ACCOUNT_ID" ]]; then
  yq write -i $CONFIG_FILE cg.ceSetUpConfig.awsAccountId "$CE_SETUP_CONFIG_AWS_ACCOUNT_ID"
fi

if [[ "" != "$CE_SETUP_CONFIG_AWS_S3_BUCKET_NAME" ]]; then
  yq write -i $CONFIG_FILE cg.ceSetUpConfig.awsS3BucketName "$CE_SETUP_CONFIG_AWS_S3_BUCKET_NAME"
fi

if [[ "" != "$CE_SETUP_CONFIG_GCP_PROJECT_ID" ]]; then
  yq write -i $CONFIG_FILE cg.ceSetUpConfig.gcpProjectId "$CE_SETUP_CONFIG_GCP_PROJECT_ID"
fi

if [[ "" != "$CE_SETUP_CONFIG_AWS_ROLE_NAME" ]]; then
  yq write -i $CONFIG_FILE cg.ceSetUpConfig.awsRoleName "$CE_SETUP_CONFIG_AWS_ROLE_NAME"
fi

if [[ "" != "$CE_SETUP_CONFIG_SAMPLE_ACCOUNT_ID" ]]; then
  yq write -i $CONFIG_FILE cg.ceSetUpConfig.sampleAccountId "$CE_SETUP_CONFIG_SAMPLE_ACCOUNT_ID"
fi

if [[ "" != "$CE_SETUP_CONFIG_AWS_ACCESS_KEY" ]]; then
  yq write -i $CONFIG_FILE cg.ceSetUpConfig.awsAccessKey "$CE_SETUP_CONFIG_AWS_ACCESS_KEY"
fi

if [[ "" != "$CE_SETUP_CONFIG_AWS_SECRET_KEY" ]]; then
  yq write -i $CONFIG_FILE cg.ceSetUpConfig.awsSecretKey "$CE_SETUP_CONFIG_AWS_SECRET_KEY"
fi

if [[ "" != "$CE_SETUP_CONFIG_MASTER_CLOUD_FORMATION" ]]; then
  yq write -i $CONFIG_FILE cg.ceSetUpConfig.masterAccountCloudFormationTemplateLink "$CE_SETUP_CONFIG_MASTER_CLOUD_FORMATION"
fi

if [[ "" != "$CE_SETUP_CONFIG_LINKED_CLOUD_FORMATION" ]]; then
  yq write -i $CONFIG_FILE cg.ceSetUpConfig.linkedAccountCloudFormationTemplateLink "$CE_SETUP_CONFIG_LINKED_CLOUD_FORMATION"
fi

if [[ "" != "$CE_SETUP_CONFIG_AZURE_CLIENTSECRET" ]]; then
  yq write -i $CONFIG_FILE cg.ceSetUpConfig.azureAppClientSecret "$CE_SETUP_CONFIG_AZURE_CLIENTSECRET"
fi

if [[ "" != "$CE_SETUP_CONFIG_AZURE_CLIENTID" ]]; then
  yq write -i $CONFIG_FILE cg.ceSetUpConfig.azureAppClientId "$CE_SETUP_CONFIG_AZURE_CLIENTID"
fi

if [[ "" != "$DATADOG_ENABLED" ]]; then
  yq write -i $CONFIG_FILE cg.datadogConfig.enabled "$DATADOG_ENABLED"
fi

if [[ "" != "$DATADOG_APIKEY" ]]; then
  yq write -i $CONFIG_FILE cg.datadogConfig.apiKey "$DATADOG_APIKEY"
fi

if [[ "" != "$DELEGATE_DOCKER_IMAGE" ]]; then
  yq write -i $CONFIG_FILE cg.portal.delegateDockerImage "$DELEGATE_DOCKER_IMAGE"
fi

if [[ "" != "$OPTIONAL_DELEGATE_TASK_REJECT_AT_LIMIT" ]]; then
  yq write -i $CONFIG_FILE portal.optionalDelegateTaskRejectAtLimit "$OPTIONAL_DELEGATE_TASK_REJECT_AT_LIMIT"
fi

if [[ "" != "$EXECUTION_LOG_DATA_STORE" ]]; then
  yq write -i $CONFIG_FILE cg.executionLogStorageMode "$EXECUTION_LOG_DATA_STORE"
fi

if [[ "" != "$FILE_STORAGE" ]]; then
  yq write -i $CONFIG_FILE cg.fileStorageMode "$FILE_STORAGE"
fi

if [[ "" != "$CLUSTER_NAME" ]]; then
  yq write -i $CONFIG_FILE cg.clusterName "$CLUSTER_NAME"
fi

if [[ "" != "$DEPLOYMENT_CLUSTER_NAME" ]]; then
  yq write -i $CONFIG_FILE cg.deploymentClusterName "$DEPLOYMENT_CLUSTER_NAME"
fi

if [[ "" != "$BACKGROUND_SCHEDULER_CLUSTERED" ]]; then
  yq write -i $CONFIG_FILE cg.backgroundScheduler.clustered "$BACKGROUND_SCHEDULER_CLUSTERED"
fi

if [[ "" != "$ALLOW_TRIAL_REGISTRATION" ]]; then
  yq write -i $CONFIG_FILE cg.trialRegistrationAllowed "$ALLOW_TRIAL_REGISTRATION"
fi

if [[ "" != "$GITHUB_OAUTH_CLIENT" ]]; then
  yq write -i $CONFIG_FILE cg.githubConfig.clientId "$GITHUB_OAUTH_CLIENT"
fi

if [[ "" != "$GITHUB_OAUTH_SECRET" ]]; then
  yq write -i $CONFIG_FILE cg.githubConfig.clientSecret "$GITHUB_OAUTH_SECRET"
fi

if [[ "" != "$GITHUB_OAUTH_CALLBACK_URL" ]]; then
  yq write -i $CONFIG_FILE cg.githubConfig.callbackUrl "$GITHUB_OAUTH_CALLBACK_URL"
fi

if [[ "" != "$AZURE_OAUTH_CLIENT" ]]; then
  yq write -i $CONFIG_FILE cg.azureConfig.clientId "$AZURE_OAUTH_CLIENT"
fi

if [[ "" != "$AZURE_OAUTH_SECRET" ]]; then
  yq write -i $CONFIG_FILE cg.azureConfig.clientSecret "$AZURE_OAUTH_SECRET"
fi

if [[ "" != "$AZURE_OAUTH_CALLBACK_URL" ]]; then
  yq write -i $CONFIG_FILE cg.azureConfig.callbackUrl "$AZURE_OAUTH_CALLBACK_URL"
fi

if [[ "" != "$GOOGLE_OAUTH_CLIENT" ]]; then
  yq write -i $CONFIG_FILE cg.googleConfig.clientId "$GOOGLE_OAUTH_CLIENT"
fi

if [[ "" != "$GOOGLE_OAUTH_SECRET" ]]; then
  yq write -i $CONFIG_FILE cg.googleConfig.clientSecret "$GOOGLE_OAUTH_SECRET"
fi

if [[ "" != "$GOOGLE_OAUTH_CALLBACK_URL" ]]; then
  yq write -i $CONFIG_FILE cg.googleConfig.callbackUrl "$GOOGLE_OAUTH_CALLBACK_URL"
fi

if [[ "" != "$BITBUCKET_OAUTH_CLIENT" ]]; then
  yq write -i $CONFIG_FILE cg.bitbucketConfig.clientId "$BITBUCKET_OAUTH_CLIENT"
fi

if [[ "" != "$BITBUCKET_OAUTH_SECRET" ]]; then
  yq write -i $CONFIG_FILE cg.bitbucketConfig.clientSecret "$BITBUCKET_OAUTH_SECRET"
fi

if [[ "" != "$BITBUCKET_OAUTH_CALLBACK_URL" ]]; then
  yq write -i $CONFIG_FILE cg.bitbucketConfig.callbackUrl "$BITBUCKET_OAUTH_CALLBACK_URL"
fi

if [[ "" != "$GITLAB_OAUTH_CLIENT" ]]; then
  yq write -i $CONFIG_FILE cg.gitlabConfig.clientId "$GITLAB_OAUTH_CLIENT"
fi

if [[ "" != "$GITLAB_OAUTH_SECRET" ]]; then
  yq write -i $CONFIG_FILE cg.gitlabConfig.clientSecret "$GITLAB_OAUTH_SECRET"
fi

if [[ "" != "$GITLAB_OAUTH_CALLBACK_URL" ]]; then
  yq write -i $CONFIG_FILE cg.gitlabConfig.callbackUrl "$GITLAB_OAUTH_CALLBACK_URL"
fi

if [[ "" != "$LINKEDIN_OAUTH_CLIENT" ]]; then
  yq write -i $CONFIG_FILE cg.linkedinConfig.clientId "$LINKEDIN_OAUTH_CLIENT"
fi

if [[ "" != "$LINKEDIN_OAUTH_SECRET" ]]; then
  yq write -i $CONFIG_FILE cg.linkedinConfig.clientSecret "$LINKEDIN_OAUTH_SECRET"
fi

if [[ "" != "$LINKEDIN_OAUTH_CALLBACK_URL" ]]; then
  yq write -i $CONFIG_FILE cg.linkedinConfig.callbackUrl "$LINKEDIN_OAUTH_CALLBACK_URL"
fi

if [[ "" != "$AWS_MARKETPLACE_ACCESSKEY" ]]; then
  yq write -i $CONFIG_FILE cg.mktPlaceConfig.awsAccessKey "$AWS_MARKETPLACE_ACCESSKEY"
fi

if [[ "" != "$AWS_MARKETPLACE_SECRETKEY" ]]; then
  yq write -i $CONFIG_FILE cg.mktPlaceConfig.awsSecretKey "$AWS_MARKETPLACE_SECRETKEY"
fi

if [[ "" != "$AWS_MARKETPLACE_PRODUCTCODE" ]]; then
  yq write -i $CONFIG_FILE cg.mktPlaceConfig.awsMarketPlaceProductCode "$AWS_MARKETPLACE_PRODUCTCODE"
fi

if [[ "" != "$AWS_MARKETPLACE_CE_PRODUCTCODE" ]]; then
  yq write -i $CONFIG_FILE cg.mktPlaceConfig.awsMarketPlaceCeProductCode "$AWS_MARKETPLACE_CE_PRODUCTCODE"
fi

if [[ "" != "$ALLOW_BLACKLISTED_EMAIL_DOMAINS" ]]; then
  yq write -i $CONFIG_FILE cg.blacklistedEmailDomainsAllowed "$ALLOW_BLACKLISTED_EMAIL_DOMAINS"
fi

if [[ "" != "$ALLOW_PWNED_PASSWORDS" ]]; then
  yq write -i $CONFIG_FILE cg.pwnedPasswordsAllowed "$ALLOW_PWNED_PASSWORDS"
fi

if [[ "" != "$TIMESCALEDB_URI" ]]; then
  yq write -i $CONFIG_FILE cg.timescaledb.timescaledbUrl "$TIMESCALEDB_URI"
fi

if [[ "" != "$TIMESCALEDB_USERNAME" ]]; then
  yq write -i $CONFIG_FILE cg.timescaledb.timescaledbUsername "$TIMESCALEDB_USERNAME"
fi

if [[ "" != "$TIMESCALEDB_PASSWORD" ]]; then
  yq write -i $CONFIG_FILE cg.timescaledb.timescaledbPassword "$TIMESCALEDB_PASSWORD"
fi

if [[ "" != "$TIMESCALEDB_CONNECT_TIMEOUT" ]]; then
  yq write -i $CONFIG_FILE cg.timescaledb.connectTimeout "$TIMESCALEDB_CONNECT_TIMEOUT"
fi

if [[ "" != "$TIMESCALEDB_SOCKET_TIMEOUT" ]]; then
  yq write -i $CONFIG_FILE cg.timescaledb.socketTimeout "$TIMESCALEDB_SOCKET_TIMEOUT"
fi

if [[ "" != "$TIMESCALEDB_LOGUNCLOSED" ]]; then
  yq write -i $CONFIG_FILE cg.timescaledb.logUnclosedConnections "$TIMESCALEDB_LOGUNCLOSED"
fi

if [[ "" != "$TIMESCALEDB_LOGGERLEVEL" ]]; then
  yq write -i $CONFIG_FILE cg.timescaledb.loggerLevel "$TIMESCALEDB_LOGGERLEVEL"
fi

if [[ "$TIMESCALEDB_HEALTH_CHECK_NEEDED" == "true" ]]; then
  yq write -i $CONFIG_FILE cg.timescaledb.isHealthCheckNeeded "$TIMESCALEDB_HEALTH_CHECK_NEEDED"
fi

if [[ "$MONGO_DEBUGGING_ENABLED" == "true" ]]; then
  yq write -i $CONFIG_FILE logging.loggers.[org.mongodb.morphia.query] TRACE
  yq write -i $CONFIG_FILE logging.loggers.connection TRACE
fi

if [[ "" != "$AZURE_MARKETPLACE_ACCESSKEY" ]]; then
  yq write -i $CONFIG_FILE cg.mktPlaceConfig.azureMarketplaceAccessKey "$AZURE_MARKETPLACE_ACCESSKEY"
fi

if [[ "" != "$AZURE_MARKETPLACE_SECRETKEY" ]]; then
  yq write -i $CONFIG_FILE cg.mktPlaceConfig.azureMarketplaceSecretKey "$AZURE_MARKETPLACE_SECRETKEY"
fi

if [[ "" != "$WORKERS" ]]; then
  IFS=',' read -ra WORKER_ITEMS <<< "$WORKERS"
  for ITEM in "${WORKER_ITEMS[@]}"; do
    WORKER=`echo $ITEM | awk -F= '{print $1}'`
    WORKER_FLAG=`echo $ITEM | awk -F= '{print $2}'`
    yq write -i $CONFIG_FILE cg.workers.active.[$WORKER] "${WORKER_FLAG}"
  done
fi

if [[ "" != "$PUBLISHERS" ]]; then
  IFS=',' read -ra PUBLISHER_ITEMS <<< "$PUBLISHERS"
  for ITEM in "${PUBLISHER_ITEMS[@]}"; do
    PUBLISHER=`echo $ITEM | awk -F= '{print $1}'`
    PUBLISHER_FLAG=`echo $ITEM | awk -F= '{print $2}'`
    yq write -i $CONFIG_FILE cg.publishers.active.[$PUBLISHER] "${PUBLISHER_FLAG}"
  done
fi

if [[ "" != "$DISTRIBUTED_LOCK_IMPLEMENTATION" ]]; then
  yq write -i $CONFIG_FILE cg.distributedLockImplementation "$DISTRIBUTED_LOCK_IMPLEMENTATION"
fi

if [[ "" != "$ATMOSPHERE_BACKEND" ]]; then
  yq write -i $CONFIG_FILE cg.atmosphereBroadcaster "$ATMOSPHERE_BACKEND"
fi

yq delete -i $REDISSON_CACHE_FILE codec

if [[ "" != "$REDIS_URL" ]]; then
  yq write -i $CONFIG_FILE cg.redisLockConfig.redisUrl "$REDIS_URL"
  yq write -i $CONFIG_FILE cg.redisAtmosphereConfig.redisUrl "$REDIS_URL"
  yq write -i $REDISSON_CACHE_FILE singleServerConfig.address "$REDIS_URL"
fi

if [[ "$REDIS_SENTINEL" == "true" ]]; then
  yq write -i $CONFIG_FILE cg.redisLockConfig.sentinel true
  yq write -i $CONFIG_FILE cg.redisAtmosphereConfig.sentinel true
  yq delete -i $REDISSON_CACHE_FILE singleServerConfig
fi

if [[ "" != "$REDIS_MASTER_NAME" ]]; then
  yq write -i $CONFIG_FILE cg.redisLockConfig.masterName "$REDIS_MASTER_NAME"
  yq write -i $CONFIG_FILE cg.redisAtmosphereConfig.masterName "$REDIS_MASTER_NAME"
  yq write -i $REDISSON_CACHE_FILE sentinelServersConfig.masterName "$REDIS_MASTER_NAME"
fi

if [[ "" != "$REDIS_SENTINELS" ]]; then
  IFS=',' read -ra REDIS_SENTINEL_URLS <<< "$REDIS_SENTINELS"
  INDEX=0
  for REDIS_SENTINEL_URL in "${REDIS_SENTINEL_URLS[@]}"; do
    yq write -i $CONFIG_FILE cg.redisLockConfig.sentinelUrls.[$INDEX] "${REDIS_SENTINEL_URL}"
    yq write -i $CONFIG_FILE cg.redisAtmosphereConfig.sentinelUrls.[$INDEX] "${REDIS_SENTINEL_URL}"
    yq write -i $REDISSON_CACHE_FILE sentinelServersConfig.sentinelAddresses.[$INDEX] "${REDIS_SENTINEL_URL}"
    INDEX=$(expr $INDEX + 1)
  done
fi

if [[ "" != "$REDIS_ENV_NAMESPACE" ]]; then
    yq write -i $CONFIG_FILE cg.redisLockConfig.envNamespace "$REDIS_ENV_NAMESPACE"
    yq write -i $CONFIG_FILE cg.redisAtmosphereConfig.envNamespace "$REDIS_ENV_NAMESPACE"
fi

if [[ "" != "$REDIS_NETTY_THREADS" ]]; then
  yq write -i $CONFIG_FILE cg.redisLockConfig.nettyThreads "$REDIS_NETTY_THREADS"
  yq write -i $CONFIG_FILE cg.redisAtmosphereConfig.nettyThreads "$REDIS_NETTY_THREADS"
  yq write -i $REDISSON_CACHE_FILE nettyThreads "$REDIS_NETTY_THREADS"
fi

if [[ "$REDIS_SCRIPT_CACHE" == "false" ]]; then
  yq write -i $CONFIG_FILE cg.redisLockConfig.useScriptCache false
  yq write -i $CONFIG_FILE cg.redisAtmosphereConfig.useScriptCache false
  yq write -i $REDISSON_CACHE_FILE useScriptCache false
fi

if [[ "" != "$CACHE_NAMESPACE" ]]; then
    yq write -i $CONFIG_FILE cg.cacheConfig.cacheNamespace "$CACHE_NAMESPACE"
fi

if [[ "" != "$CACHE_BACKEND" ]]; then
    yq write -i $CONFIG_FILE cg.cacheConfig.cacheBackend "$CACHE_BACKEND"
fi

if [[ "" != "$GCP_MARKETPLACE_ENABLED" ]]; then
    yq write -i $CONFIG_FILE cg.gcpMarketplaceConfig.enabled "$GCP_MARKETPLACE_ENABLED"
fi

if [[ "" != "$GCP_MARKETPLACE_SUBSCRIPTION_NAME" ]]; then
    yq write -i $CONFIG_FILE cg.gcpMarketplaceConfig.subscriptionName "$GCP_MARKETPLACE_SUBSCRIPTION_NAME"
fi

if [[ "" != "$CURRENT_JRE" ]]; then
  yq write -i $CONFIG_FILE cg.currentJre "$CURRENT_JRE"
fi

if [[ "" != "$MIGRATE_TO_JRE" ]]; then
  yq write -i $CONFIG_FILE cg.migrateToJre "$MIGRATE_TO_JRE"
fi

if [[ "" != "$ORACLE_JRE_TAR_PATH" ]]; then
  yq write -i $CONFIG_FILE cg.jreConfigs.oracle8u191.jreTarPath "$ORACLE_JRE_TAR_PATH"
fi

if [[ "" != "$OPENJDK_JRE_TAR_PATH" ]]; then
  yq write -i $CONFIG_FILE cg.jreConfigs.openjdk8u242.jreTarPath "$OPENJDK_JRE_TAR_PATH"
fi

if [[ "" != "$CDN_URL" ]]; then
  yq write -i $CONFIG_FILE cg.cdnConfig.url "$CDN_URL"
fi

if [[ "" != "$CDN_KEY" ]]; then
  yq write -i $CONFIG_FILE cg.cdnConfig.keyName "$CDN_KEY"
fi

if [[ "" != "$CDN_KEY_SECRET" ]]; then
  yq write -i $CONFIG_FILE cg.cdnConfig.keySecret "$CDN_KEY_SECRET"
fi

if [[ "" != "$CDN_DELEGATE_JAR_PATH" ]]; then
  yq write -i $CONFIG_FILE cg.cdnConfig.delegateJarPath "$CDN_DELEGATE_JAR_PATH"
fi

if [[ "" != "$CDN_WATCHER_JAR_BASE_PATH" ]]; then
  yq write -i $CONFIG_FILE cg.cdnConfig.watcherJarBasePath "$CDN_WATCHER_JAR_BASE_PATH"
fi

if [[ "" != "$CDN_WATCHER_JAR_PATH" ]]; then
  yq write -i $CONFIG_FILE cg.cdnConfig.watcherJarPath "$CDN_WATCHER_JAR_PATH"
fi

if [[ "" != "$CDN_WATCHER_METADATA_FILE_PATH" ]]; then
  yq write -i $CONFIG_FILE cg.cdnConfig.watcherMetaDataFilePath "$CDN_WATCHER_METADATA_FILE_PATH"
fi

if [[ "" != "$CDN_ORACLE_JRE_TAR_PATH" ]]; then
  yq write -i $CONFIG_FILE cg.cdnConfig.cdnJreTarPaths.oracle8u191 "$CDN_ORACLE_JRE_TAR_PATH"
fi

if [[ "" != "$CDN_OPENJDK_JRE_TAR_PATH" ]]; then
  yq write -i $CONFIG_FILE cg.cdnConfig.cdnJreTarPaths.openjdk8u242 "$CDN_OPENJDK_JRE_TAR_PATH"
fi

if [[ "" != "$COMMAND_LIBRARY_SERVICE_BASE_URL" ]]; then
  yq write -i $CONFIG_FILE cg.commandLibraryServiceConfig.baseUrl "$COMMAND_LIBRARY_SERVICE_BASE_URL"
fi

if [[ "" != "$BUGSNAG_API_KEY" ]]; then
  yq write -i $CONFIG_FILE cg.bugsnagApiKey "$BUGSNAG_API_KEY"
fi

if [[ "" != "$ACCOUNT_LICENSE_CHECK_JOB_FREQUENCY" ]]; then
  yq write -i $CONFIG_FILE cg.jobsFrequencyConfig.accountLicenseCheckJobFrequencyInMinutes "$ACCOUNT_LICENSE_CHECK_JOB_FREQUENCY"
fi

if [[ "" != "$ACCOUNT_DELETION_JOB_FREQUENCY" ]]; then
  yq write -i $CONFIG_FILE cg.jobsFrequencyConfig.accountDeletionJobFrequencyInMinutes "$ACCOUNT_DELETION_JOB_FREQUENCY"
fi

if [[ "" != "$MANAGER_TO_COMMAND_LIBRARY_SERVICE_SECRET" ]]; then
  yq write -i $CONFIG_FILE cg.commandLibraryServiceConfig.managerToCommandLibraryServiceSecret "$MANAGER_TO_COMMAND_LIBRARY_SERVICE_SECRET"
fi

if [[ "" != "$DELEGATE_SERVICE_TARGET" ]]; then
  yq write -i $CONFIG_FILE cg.grpcDelegateServiceClientConfig.target "$DELEGATE_SERVICE_TARGET"
fi

if [[ "" != "$DELEGATE_SERVICE_AUTHORITY" ]]; then
  yq write -i $CONFIG_FILE cg.grpcDelegateServiceClientConfig.authority "$DELEGATE_SERVICE_AUTHORITY"
fi

if [[ "" != "$DELEGATE_SERVICE_MANAGEMENT_AUTHORITY" ]]; then
  yq write -i $CONFIG_FILE cg.grpcDMSClientConfig.authority "$DELEGATE_SERVICE_MANAGEMENT_AUTHORITY"
fi

if [[ "" != "$DELEGATE_SERVICE_MANAGEMENT_TARGET" ]]; then
  yq write -i $CONFIG_FILE cg.grpcDMSClientConfig.target "$DELEGATE_SERVICE_MANAGEMENT_TARGET"
fi

if [[ "" != "$DELEGATE_SERVICE_MANAGEMENT_SECRET" ]]; then
  yq write -i $CONFIG_FILE cg.dmsSecret "$DELEGATE_SERVICE_MANAGEMENT_SECRET"
fi


if [[ "" != "$DELEGATE_GRPC_TARGET" ]]; then
  yq write -i $CONFIG_FILE cg.grpcOnpremDelegateClientConfig.target "$DELEGATE_GRPC_TARGET"
fi

if [[ "" != "$DELEGATE_GRPC_AUTHORITY" ]]; then
  yq write -i $CONFIG_FILE cg.grpcOnpremDelegateClientConfig.authority "$DELEGATE_GRPC_AUTHORITY"
fi

if [[ "" != "$NG_MANAGER_AUTHORITY" ]]; then
  yq write -i $CONFIG_FILE cg.grpcClientConfig.authority "$NG_MANAGER_AUTHORITY"
fi

if [[ "" != "$NG_MANAGER_TARGET" ]]; then
  yq write -i $CONFIG_FILE cg.grpcClientConfig.target "$NG_MANAGER_TARGET"
fi

if [[ "" != "$REMINDERS_BEFORE_ACCOUNT_DELETION" ]]; then
  yq write -i $CONFIG_FILE cg.numberOfRemindersBeforeAccountDeletion "$REMINDERS_BEFORE_ACCOUNT_DELETION"
fi

if [[ "" != "$EXPORT_DATA_BATCH_SIZE" ]]; then
  yq write -i $CONFIG_FILE cg.exportAccountDataBatchSize "$EXPORT_DATA_BATCH_SIZE"
fi

if [[ "" != "$COMMAND_LIBRARY_PUBLISHING_ALLOWED" ]]; then
  yq write -i $CONFIG_FILE cg.commandLibraryServiceConfig.publishingAllowed "$COMMAND_LIBRARY_PUBLISHING_ALLOWED"
fi

if [[ "" != "$COMMAND_LIBRARY_PUBLISHING_SECRET" ]]; then
  yq write -i $CONFIG_FILE cg.commandLibraryServiceConfig.publishingSecret "$COMMAND_LIBRARY_PUBLISHING_SECRET"
fi

if [[ "" != "$LOG_STREAMING_SERVICE_BASEURL" ]]; then
  yq write -i $CONFIG_FILE cg.logStreamingServiceConfig.baseUrl "$LOG_STREAMING_SERVICE_BASEURL"
fi

if [[ "" != "$LOG_STREAMING_SERVICE_TOKEN" ]]; then
  yq write -i $CONFIG_FILE cg.logStreamingServiceConfig.serviceToken "$LOG_STREAMING_SERVICE_TOKEN"
fi

if [[ "" != "$ACCESS_CONTROL_ENABLED" ]]; then
  yq write -i $CONFIG_FILE cg.accessControlClient.enableAccessControl $ACCESS_CONTROL_ENABLED
fi

if [[ "" != "$ACCESS_CONTROL_BASE_URL" ]]; then
  yq write -i $CONFIG_FILE cg.accessControlClient.accessControlServiceConfig.baseUrl $ACCESS_CONTROL_BASE_URL
fi

if [[ "" != "$ACCESS_CONTROL_SECRET" ]]; then
  yq write -i $CONFIG_FILE cg.accessControlClient.accessControlServiceSecret $ACCESS_CONTROL_SECRET
fi

if [[ "" != "$ENABLE_AUDIT" ]]; then
  yq write -i $CONFIG_FILE cg.enableAudit $ENABLE_AUDIT
fi

if [[ "" != "$AUDIT_CLIENT_BASEURL" ]]; then
  yq write -i $CONFIG_FILE cg.auditClientConfig.baseUrl "$AUDIT_CLIENT_BASEURL"
fi

if [[ "" != "$EVENTS_FRAMEWORK_REDIS_SENTINELS" ]]; then
  IFS=',' read -ra SENTINEL_URLS <<< "$EVENTS_FRAMEWORK_REDIS_SENTINELS"
  INDEX=0
  for REDIS_SENTINEL_URL in "${SENTINEL_URLS[@]}"; do
    yq write -i $CONFIG_FILE cg.eventsFramework.redis.sentinelUrls.[$INDEX] "${REDIS_SENTINEL_URL}"
    INDEX=$(expr $INDEX + 1)
  done
fi

replace_key_value cg.eventsFramework.redis.sentinel $EVENTS_FRAMEWORK_USE_SENTINEL
replace_key_value cg.eventsFramework.redis.envNamespace $EVENTS_FRAMEWORK_ENV_NAMESPACE
replace_key_value cg.eventsFramework.redis.redisUrl $EVENTS_FRAMEWORK_REDIS_URL
replace_key_value cg.eventsFramework.redis.masterName $EVENTS_FRAMEWORK_SENTINEL_MASTER_NAME
replace_key_value cg.eventsFramework.redis.userName $EVENTS_FRAMEWORK_REDIS_USERNAME
replace_key_value cg.eventsFramework.redis.password $EVENTS_FRAMEWORK_REDIS_PASSWORD
replace_key_value cg.eventsFramework.redis.nettyThreads $EVENTS_FRAMEWORK_NETTY_THREADS
replace_key_value cg.eventsFramework.redis.sslConfig.enabled $EVENTS_FRAMEWORK_REDIS_SSL_ENABLED
replace_key_value cg.eventsFramework.redis.sslConfig.CATrustStorePath $EVENTS_FRAMEWORK_REDIS_SSL_CA_TRUST_STORE_PATH
replace_key_value cg.eventsFramework.redis.sslConfig.CATrustStorePassword $EVENTS_FRAMEWORK_REDIS_SSL_CA_TRUST_STORE_PASSWORD
replace_key_value cg.ngAuthUIEnabled "$HARNESS_ENABLE_NG_AUTH_UI_PLACEHOLDER"
replace_key_value cg.portal.zendeskBaseUrl "$ZENDESK_BASE_URL"
replace_key_value cg.deployVariant "$DEPLOY_VERSION"

if [[ "" != ${GATEWAY_PATH_PREFIX+x} ]]; then
  yq write -i $CONFIG_FILE cg.portal.gatewayPathPrefix "$GATEWAY_PATH_PREFIX"
fi

if [[ "" != "$NG_MANAGER_BASE_URL" ]]; then
  yq write -i $CONFIG_FILE cg.ngManagerServiceHttpClientConfig.baseUrl "$NG_MANAGER_BASE_URL"
fi

if [[ "" != "$ENABLE_USER_CHANGESTREAM" ]]; then
  yq write -i $CONFIG_FILE cg.userChangeStreamEnabled "$ENABLE_USER_CHANGESTREAM"
fi

if [[ "" != "$DISABLE_DELEGATE_MGMT_IN_MANAGER" ]]; then
  yq write -i $CONFIG_FILE cg.disableDelegateMgmtInManager "$DISABLE_DELEGATE_MGMT_IN_MANAGER"
fi

if [[ "" != "$GCP_SECRET_MANAGER_PROJECT" ]]; then
  yq write -i $CONFIG_FILE cg.secretsConfiguration.gcpSecretManagerProject "$GCP_SECRET_MANAGER_PROJECT"
fi

if [[ "" != "$RESOLVE_SECRETS" ]]; then
  yq write -i $CONFIG_FILE cg.secretsConfiguration.secretResolutionEnabled "$RESOLVE_SECRETS"
fi

if [[ "" != "$LDAP_GROUP_SYNC_INTERVAL" ]]; then
  yq write -i $CONFIG_FILE cg.ldapSyncJobConfig.syncInterval "$LDAP_GROUP_SYNC_INTERVAL"
fi

if [[ "" != "$LDAP_GROUP_SYNC_POOL_SIZE" ]]; then
  yq write -i $CONFIG_FILE cg.ldapSyncJobConfig.poolSize "$LDAP_GROUP_SYNC_POOL_SIZE"
fi

if [[ "" != "$LDAP_GROUP_SYNC_DEFAULT_CRON" ]]; then
  yq write -i $CONFIG_FILE cg.ldapSyncJobConfig.defaultCronExpression "$LDAP_GROUP_SYNC_DEFAULT_CRON"
fi

if [[ "" != "$USE_GLOBAL_KMS_AS_BASE_ALGO" ]]; then
  yq write -i $CONFIG_FILE cg.useGlobalKMSAsBaseAlgo "$USE_GLOBAL_KMS_AS_BASE_ALGO"
fi

if [[ "" != "$SEGMENT_ENABLED_NG" ]]; then
  yq write -i $CONFIG_FILE cg.segmentConfiguration.enabled "$SEGMENT_ENABLED_NG"
fi

if [[ "" != "$SEGMENT_URL_NG" ]]; then
  yq write -i $CONFIG_FILE cg.segmentConfiguration.url "$SEGMENT_URL_NG"
fi

if [[ "" != "$SEGMENT_APIKEY_NG" ]]; then
  yq write -i $CONFIG_FILE cg.segmentConfiguration.apiKey "$SEGMENT_APIKEY_NG"
fi

if [[ "" != "$SEGMENT_VERIFY_CERT_NG" ]]; then
  yq write -i $CONFIG_FILE cg.segmentConfiguration.certValidationRequired "$SEGMENT_VERIFY_CERT_NG"
fi

if [[ "" != "$SECOPS_EMAIL" ]]; then
 yq write -i config.yml cg.totp.secOpsEmail "$SECOPS_EMAIL"
fi

if [[ "" != "$INCORRECT_ATTEMPTS_UNTIL_SECOPS_NOTIFIED" ]]; then
 yq write -i config.yml cg.totp.incorrectAttemptsUntilSecOpsNotified "$INCORRECT_ATTEMPTS_UNTIL_SECOPS_NOTIFIED"
fi

if [[ "" != "$PIPELINE_SERVICE_CLIENT_BASEURL" ]]; then
  yq write -i $CONFIG_FILE pipelineServiceClientConfig.baseUrl "$PIPELINE_SERVICE_CLIENT_BASEURL"
fi

if [[ "" != "$NG_MANAGER_CLIENT_BASEURL" ]]; then
  yq write -i $CONFIG_FILE ngClientConfig.baseUrl "$NG_MANAGER_CLIENT_BASEURL"
fi

if [[ "" != "$TEMPLATE_SERVICE_ENDPOINT" ]]; then
  yq write -i $CONFIG_FILE templateServiceClientConfig.baseUrl "$TEMPLATE_SERVICE_ENDPOINT"
fi
