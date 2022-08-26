#!/usr/bin/env bash
# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Shield 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.

CONFIG_FILE=/opt/harness/config.yml
NEWRELIC_FILE=/opt/harness/newrelic.yml
REDISSON_CACHE_FILE=/opt/harness/redisson-jcache.yaml

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

yq 'del(.'server.applicationConnectors.(type==h2)')' $CONFIG_FILE
yq 'del(.'grpcServerConfig.connectors.(secure==true)')' $CONFIG_FILE

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
  yq -i '.server.applicationConnectors[0].port="9090"' $CONFIG_FILE
fi

if [[ "" != "$GRPC_SERVER_PORT" ]]; then
  yq -i '.grpcServerConfig.connectors[0].port="$GRPC_SERVER_PORT"' $CONFIG_FILE
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

if [[ "" != "$ANALYTIC_MONGO_TAG_NAME" ]]; then
 yq -i '.mongo.analyticNodeConfig.mongoTagKey="$ANALYTIC_MONGO_TAG_NAME"' $CONFIG_FILE
fi

if [[ "" != "$ANALYTIC_MONGO_TAG_VALUE" ]]; then
 yq -i '.mongo.analyticNodeConfig.mongoTagValue="$ANALYTIC_MONGO_TAG_VALUE"' $CONFIG_FILE
fi

if [[ "" != "$EVEMTS_MONGO_INDEX_MANAGER_MODE" ]]; then
  yq -i '.events-mongo.indexManagerMode="$EVEMTS_MONGO_INDEX_MANAGER_MODE"' $CONFIG_FILE
fi

if [[ "" != "$EVENTS_MONGO_URI" ]]; then
  yq -i '.events-mongo.uri="$EVENTS_MONGO_URI"' $CONFIG_FILE
else
  if [[ "" != "$EVENTS_MONGO_HOSTS_AND_PORTS" ]]; then
    yq 'del(.events-mongo.uri)' $CONFIG_FILE
    yq -i '.events-mongo.username="$EVENTS_MONGO_USERNAME"' $CONFIG_FILE
    yq -i '.events-mongo.password="$EVENTS_MONGO_PASSWORD"' $CONFIG_FILE
    yq -i '.events-mongo.database="$EVENTS_MONGO_DATABASE"' $CONFIG_FILE
    yq -i '.events-mongo.schema="$EVENTS_MONGO_SCHEMA"' $CONFIG_FILE
    write_mongo_hosts_and_ports events-mongo "$EVENTS_MONGO_HOSTS_AND_PORTS"
    write_mongo_params events-mongo "$EVENTS_MONGO_PARAMS"
  else
    yq 'del(.events-mongo)' $CONFIG_FILE
  fi
fi

if [[ "" != "$CF_CLIENT_API_KEY" ]]; then
  yq -i '.cfClientConfig.apiKey="$CF_CLIENT_API_KEY"' $CONFIG_FILE
fi

if [[ "" != "$CF_CLIENT_CONFIG_URL" ]]; then
  yq -i '.cfClientConfig.configUrl="$CF_CLIENT_CONFIG_URL"' $CONFIG_FILE
fi

if [[ "" != "$CF_CLIENT_BUFFER_SIZE" ]]; then
  yq -i '.cfClientConfig.bufferSize="$CF_CLIENT_BUFFER_SIZE"' $CONFIG_FILE
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


if [[ "" != "$ELASTICSEARCH_URI" ]]; then
  yq -i '.elasticsearch.uri="$ELASTICSEARCH_URI"' $CONFIG_FILE
fi

if [[ "" != "$ELASTICSEARCH_INDEX_SUFFIX" ]]; then
  yq -i '.elasticsearch.indexSuffix="$ELASTICSEARCH_INDEX_SUFFIX"' $CONFIG_FILE
fi

if [[ "" != "$ELASTICSEARCH_MONGO_TAG_NAME" ]]; then
 yq -i '.elasticsearch.mongoTagKey="$ELASTICSEARCH_MONGO_TAG_NAME"' $CONFIG_FILE
fi

if [[ "" != "$ELASTICSEARCH_MONGO_TAG_VALUE" ]]; then
 yq -i '.elasticsearch.mongoTagValue="$ELASTICSEARCH_MONGO_TAG_VALUE"' $CONFIG_FILE
fi

if [[ "" != "$MONGO_LOCK_URI" ]]; then
  yq -i '.mongo.locksUri="${MONGO_LOCK_URI//\\&/&}"' $CONFIG_FILE
fi

yq -i '.server.requestLog.appenders[0].threshold="TRACE"' $CONFIG_FILE

if [[ "$STACK_DRIVER_LOGGING_ENABLED" == "true" ]]; then
  yq 'del(.'logging.appenders.(type==file)')' $CONFIG_FILE
  yq 'del(.'logging.appenders.(type==console)')' $CONFIG_FILE
  yq -i '.'logging.appenders.(type==gke-console).stackdriverLogEnabled'="true"' $CONFIG_FILE
else
  if [[ "$ROLLING_FILE_LOGGING_ENABLED" == "true" ]]; then
    yq 'del(.'logging.appenders.(type==gke-console)')' $CONFIG_FILE
    yq -i '.'logging.appenders.(type==file).currentLogFilename'="/opt/harness/logs/portal.log"' $CONFIG_FILE
    yq -i '.'logging.appenders.(type==file).archivedLogFilenamePattern'="/opt/harness/logs/portal.%d.%i.log"' $CONFIG_FILE
  else
    yq 'del(.'logging.appenders.(type==file)')' $CONFIG_FILE
    yq 'del(.'logging.appenders.(type==gke-console)')' $CONFIG_FILE
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

yq -i '.common.license_key="$NEWRELIC_LICENSE_KEY"' $NEWRELIC_FILE

if [[ "$DISABLE_NEW_RELIC" == "true" ]]; then
  yq -i '.common.agent_enabled="false"' $NEWRELIC_FILE
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


if [[ "" != "$FEATURES" ]]; then
  yq -i '.featuresEnabled="$FEATURES"' $CONFIG_FILE
fi

if [[ "" != "$SAMPLE_TARGET_ENV" ]]; then
  yq -i '.sampleTargetEnv="$SAMPLE_TARGET_ENV"' $CONFIG_FILE
fi

if [[ "" != "$SAMPLE_TARGET_STATUS_HOST" ]]; then
  yq -i '.sampleTargetStatusHost="$SAMPLE_TARGET_STATUS_HOST"' $CONFIG_FILE
fi

if [[ "" != "$GLOBAL_WHITELIST" ]]; then
  yq -i '.globalWhitelistConfig.filters="$GLOBAL_WHITELIST"' $CONFIG_FILE
fi

if [[ "" != "$SMTP_HOST" ]]; then
  yq -i '.smtp.host="$SMTP_HOST"' $CONFIG_FILE
fi

if [[ "" != "$SMTP_USERNAME" ]]; then
  yq -i '.smtp.username="$SMTP_USERNAME"' $CONFIG_FILE
fi

if [[ "" != "$SMTP_PASSWORD" ]]; then
  yq -i '.smtp.password="$SMTP_PASSWORD"' $CONFIG_FILE
fi

if [[ "" != "$SMTP_USE_SSL" ]]; then
  yq -i '.smtp.useSSL="$SMTP_USE_SSL"' $CONFIG_FILE
fi

if [[ "" != "$MARKETO_ENABLED" ]]; then
  yq -i '.marketoConfig.enabled="$MARKETO_ENABLED"' $CONFIG_FILE
fi

if [[ "" != "$MARKETO_URL" ]]; then
  yq -i '.marketoConfig.url="$MARKETO_URL"' $CONFIG_FILE
fi

if [[ "" != "$MARKETO_CLIENT_ID" ]]; then
  yq -i '.marketoConfig.clientId="$MARKETO_CLIENT_ID"' $CONFIG_FILE
fi

if [[ "" != "$MARKETO_CLIENT_SECRET" ]]; then
  yq -i '.marketoConfig.clientSecret="$MARKETO_CLIENT_SECRET"' $CONFIG_FILE
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

if [[ "" != "$SALESFORCE_USERNAME" ]]; then
  yq -i '.salesforceConfig.userName="$SALESFORCE_USERNAME"' $CONFIG_FILE
fi

if [[ "" != "$SALESFORCE_PASSWORD" ]]; then
  yq -i '.salesforceConfig.password="$SALESFORCE_PASSWORD"' $CONFIG_FILE
fi

if [[ "" != "$SALESFORCE_CONSUMER_KEY" ]]; then
  yq -i '.salesforceConfig.consumerKey="$SALESFORCE_CONSUMER_KEY"' $CONFIG_FILE
fi

if [[ "" != "$SALESFORCE_CONSUMER_SECRET" ]]; then
  yq -i '.salesforceConfig.consumerSecret="$SALESFORCE_CONSUMER_SECRET"' $CONFIG_FILE
fi

if [[ "" != "$SALESFORCE_GRANT_TYPE" ]]; then
  yq -i '.salesforceConfig.grantType="$SALESFORCE_GRANT_TYPE"' $CONFIG_FILE
fi

if [[ "" != "$SALESFORCE_LOGIN_INSTANCE_DOMAIN" ]]; then
  yq -i '.salesforceConfig.loginInstanceDomain="$SALESFORCE_LOGIN_INSTANCE_DOMAIN"' $CONFIG_FILE
fi

if [[ "" != "$SALESFORCE_API_VERSION" ]]; then
  yq -i '.salesforceConfig.apiVersion="$SALESFORCE_API_VERSION"' $CONFIG_FILE
fi

if [[ "" != "$SALESFORCE_INTEGRATION_ENABLED" ]]; then
  yq -i '.salesforceConfig.enabled="$SALESFORCE_INTEGRATION_ENABLED"' $CONFIG_FILE
fi

if [[ "" != "$CE_SETUP_CONFIG_AWS_ACCOUNT_ID" ]]; then
  yq -i '.ceSetUpConfig.awsAccountId="$CE_SETUP_CONFIG_AWS_ACCOUNT_ID"' $CONFIG_FILE
fi

if [[ "" != "$CE_SETUP_CONFIG_AWS_S3_BUCKET_NAME" ]]; then
  yq -i '.ceSetUpConfig.awsS3BucketName="$CE_SETUP_CONFIG_AWS_S3_BUCKET_NAME"' $CONFIG_FILE
fi

if [[ "" != "$CE_SETUP_CONFIG_GCP_PROJECT_ID" ]]; then
  yq -i '.ceSetUpConfig.gcpProjectId="$CE_SETUP_CONFIG_GCP_PROJECT_ID"' $CONFIG_FILE
fi

if [[ "" != "$CE_SETUP_CONFIG_AWS_ROLE_NAME" ]]; then
  yq -i '.ceSetUpConfig.awsRoleName="$CE_SETUP_CONFIG_AWS_ROLE_NAME"' $CONFIG_FILE
fi

if [[ "" != "$CE_SETUP_CONFIG_SAMPLE_ACCOUNT_ID" ]]; then
  yq -i '.ceSetUpConfig.sampleAccountId="$CE_SETUP_CONFIG_SAMPLE_ACCOUNT_ID"' $CONFIG_FILE
fi

if [[ "" != "$CE_SETUP_CONFIG_AWS_ACCESS_KEY" ]]; then
  yq -i '.ceSetUpConfig.awsAccessKey="$CE_SETUP_CONFIG_AWS_ACCESS_KEY"' $CONFIG_FILE
fi

if [[ "" != "$CE_SETUP_CONFIG_AWS_SECRET_KEY" ]]; then
  yq -i '.ceSetUpConfig.awsSecretKey="$CE_SETUP_CONFIG_AWS_SECRET_KEY"' $CONFIG_FILE
fi

if [[ "" != "$CE_SETUP_CONFIG_MASTER_CLOUD_FORMATION" ]]; then
  yq -i '.ceSetUpConfig.masterAccountCloudFormationTemplateLink="$CE_SETUP_CONFIG_MASTER_CLOUD_FORMATION"' $CONFIG_FILE
fi

if [[ "" != "$CE_SETUP_CONFIG_LINKED_CLOUD_FORMATION" ]]; then
  yq -i '.ceSetUpConfig.linkedAccountCloudFormationTemplateLink="$CE_SETUP_CONFIG_LINKED_CLOUD_FORMATION"' $CONFIG_FILE
fi

if [[ "" != "$CE_SETUP_CONFIG_AZURE_CLIENTSECRET" ]]; then
  yq -i '.ceSetUpConfig.azureAppClientSecret="$CE_SETUP_CONFIG_AZURE_CLIENTSECRET"' $CONFIG_FILE
fi

if [[ "" != "$CE_SETUP_CONFIG_AZURE_CLIENTID" ]]; then
  yq -i '.ceSetUpConfig.azureAppClientId="$CE_SETUP_CONFIG_AZURE_CLIENTID"' $CONFIG_FILE
fi

if [[ "" != "$DATADOG_ENABLED" ]]; then
  yq -i '.datadogConfig.enabled="$DATADOG_ENABLED"' $CONFIG_FILE
fi

if [[ "" != "$DATADOG_APIKEY" ]]; then
  yq -i '.datadogConfig.apiKey="$DATADOG_APIKEY"' $CONFIG_FILE
fi

if [[ "" != "$DELEGATE_DOCKER_IMAGE" ]]; then
  yq -i '.portal.delegateDockerImage="$DELEGATE_DOCKER_IMAGE"' $CONFIG_FILE
fi

if [[ "" != "$OPTIONAL_DELEGATE_TASK_REJECT_AT_LIMIT" ]]; then
  yq -i '.portal.optionalDelegateTaskRejectAtLimit="$OPTIONAL_DELEGATE_TASK_REJECT_AT_LIMIT"' $CONFIG_FILE
fi

if [[ "" != "$EXECUTION_LOG_DATA_STORE" ]]; then
  yq -i '.executionLogStorageMode="$EXECUTION_LOG_DATA_STORE"' $CONFIG_FILE
fi

if [[ "" != "$FILE_STORAGE" ]]; then
  yq -i '.fileStorageMode="$FILE_STORAGE"' $CONFIG_FILE
fi

if [[ "" != "$CLUSTER_NAME" ]]; then
  yq -i '.clusterName="$CLUSTER_NAME"' $CONFIG_FILE
fi

if [[ "" != "$DEPLOYMENT_CLUSTER_NAME" ]]; then
  yq -i '.deploymentClusterName="$DEPLOYMENT_CLUSTER_NAME"' $CONFIG_FILE
fi

if [[ "" != "$BACKGROUND_SCHEDULER_CLUSTERED" ]]; then
  yq -i '.backgroundScheduler.clustered="$BACKGROUND_SCHEDULER_CLUSTERED"' $CONFIG_FILE
fi

if [[ "" != "$ENABLE_CRONS" ]]; then
  yq -i '.enableIterators="$ENABLE_CRONS"' $CONFIG_FILE
  yq -i '.backgroundScheduler.enabled="$ENABLE_CRONS"' $CONFIG_FILE
  yq -i '.serviceScheduler.enabled="$ENABLE_CRONS"' $CONFIG_FILE
fi

if [[ "" != "$ALLOW_TRIAL_REGISTRATION" ]]; then
  yq -i '.trialRegistrationAllowed="$ALLOW_TRIAL_REGISTRATION"' $CONFIG_FILE
fi

if [[ "" != "$EVENTS_FRAMEWORK_AVAILABLE_IN_ONPREM" ]]; then
  yq -i '.eventsFrameworkAvailableInOnPrem="$EVENTS_FRAMEWORK_AVAILABLE_IN_ONPREM"' $CONFIG_FILE
else
  yq -i '.eventsFrameworkAvailableInOnPrem="false"' $CONFIG_FILE
fi

if [[ "" != "$ALLOW_TRIAL_REGISTRATION_FOR_BUGATHON" ]]; then
  yq -i '.trialRegistrationAllowedForBugathon="$ALLOW_TRIAL_REGISTRATION_FOR_BUGATHON"' $CONFIG_FILE
fi

if [[ "" != "$GITHUB_OAUTH_CLIENT" ]]; then
  yq -i '.githubConfig.clientId="$GITHUB_OAUTH_CLIENT"' $CONFIG_FILE
fi

if [[ "" != "$GITHUB_OAUTH_SECRET" ]]; then
  yq -i '.githubConfig.clientSecret="$GITHUB_OAUTH_SECRET"' $CONFIG_FILE
fi

if [[ "" != "$GITHUB_OAUTH_CALLBACK_URL" ]]; then
  yq -i '.githubConfig.callbackUrl="$GITHUB_OAUTH_CALLBACK_URL"' $CONFIG_FILE
fi

if [[ "" != "$AZURE_OAUTH_CLIENT" ]]; then
  yq -i '.azureConfig.clientId="$AZURE_OAUTH_CLIENT"' $CONFIG_FILE
fi

if [[ "" != "$AZURE_OAUTH_SECRET" ]]; then
  yq -i '.azureConfig.clientSecret="$AZURE_OAUTH_SECRET"' $CONFIG_FILE
fi

if [[ "" != "$AZURE_OAUTH_CALLBACK_URL" ]]; then
  yq -i '.azureConfig.callbackUrl="$AZURE_OAUTH_CALLBACK_URL"' $CONFIG_FILE
fi

if [[ "" != "$GOOGLE_OAUTH_CLIENT" ]]; then
  yq -i '.googleConfig.clientId="$GOOGLE_OAUTH_CLIENT"' $CONFIG_FILE
fi

if [[ "" != "$GOOGLE_OAUTH_SECRET" ]]; then
  yq -i '.googleConfig.clientSecret="$GOOGLE_OAUTH_SECRET"' $CONFIG_FILE
fi

if [[ "" != "$GOOGLE_OAUTH_CALLBACK_URL" ]]; then
  yq -i '.googleConfig.callbackUrl="$GOOGLE_OAUTH_CALLBACK_URL"' $CONFIG_FILE
fi

if [[ "" != "$BITBUCKET_OAUTH_CLIENT" ]]; then
  yq -i '.bitbucketConfig.clientId="$BITBUCKET_OAUTH_CLIENT"' $CONFIG_FILE
fi

if [[ "" != "$BITBUCKET_OAUTH_SECRET" ]]; then
  yq -i '.bitbucketConfig.clientSecret="$BITBUCKET_OAUTH_SECRET"' $CONFIG_FILE
fi

if [[ "" != "$BITBUCKET_OAUTH_CALLBACK_URL" ]]; then
  yq -i '.bitbucketConfig.callbackUrl="$BITBUCKET_OAUTH_CALLBACK_URL"' $CONFIG_FILE
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

if [[ "" != "$LINKEDIN_OAUTH_CLIENT" ]]; then
  yq -i '.linkedinConfig.clientId="$LINKEDIN_OAUTH_CLIENT"' $CONFIG_FILE
fi

if [[ "" != "$LINKEDIN_OAUTH_SECRET" ]]; then
  yq -i '.linkedinConfig.clientSecret="$LINKEDIN_OAUTH_SECRET"' $CONFIG_FILE
fi

if [[ "" != "$LINKEDIN_OAUTH_CALLBACK_URL" ]]; then
  yq -i '.linkedinConfig.callbackUrl="$LINKEDIN_OAUTH_CALLBACK_URL"' $CONFIG_FILE
fi

if [[ "" != "$AWS_MARKETPLACE_ACCESSKEY" ]]; then
  yq -i '.mktPlaceConfig.awsAccessKey="$AWS_MARKETPLACE_ACCESSKEY"' $CONFIG_FILE
fi

if [[ "" != "$AWS_MARKETPLACE_SECRETKEY" ]]; then
  yq -i '.mktPlaceConfig.awsSecretKey="$AWS_MARKETPLACE_SECRETKEY"' $CONFIG_FILE
fi

if [[ "" != "$AWS_MARKETPLACE_PRODUCTCODE" ]]; then
  yq -i '.mktPlaceConfig.awsMarketPlaceProductCode="$AWS_MARKETPLACE_PRODUCTCODE"' $CONFIG_FILE
fi

if [[ "" != "$AWS_MARKETPLACE_CE_PRODUCTCODE" ]]; then
  yq -i '.mktPlaceConfig.awsMarketPlaceCeProductCode="$AWS_MARKETPLACE_CE_PRODUCTCODE"' $CONFIG_FILE
fi

if [[ "" != "$ALLOW_BLACKLISTED_EMAIL_DOMAINS" ]]; then
  yq -i '.blacklistedEmailDomainsAllowed="$ALLOW_BLACKLISTED_EMAIL_DOMAINS"' $CONFIG_FILE
fi

if [[ "" != "$ALLOW_PWNED_PASSWORDS" ]]; then
  yq -i '.pwnedPasswordsAllowed="$ALLOW_PWNED_PASSWORDS"' $CONFIG_FILE
fi

if [[ "" != "$TIMESCALEDB_URI" ]]; then
  yq -i '.timescaledb.timescaledbUrl="$TIMESCALEDB_URI"' $CONFIG_FILE
fi

if [[ "" != "$TIMESCALEDB_USERNAME" ]]; then
  yq -i '.timescaledb.timescaledbUsername="$TIMESCALEDB_USERNAME"' $CONFIG_FILE
fi

if [[ "" != "$TIMESCALEDB_PASSWORD" ]]; then
  yq -i '.timescaledb.timescaledbPassword="$TIMESCALEDB_PASSWORD"' $CONFIG_FILE
fi

if [[ "" != "$TIMESCALEDB_CONNECT_TIMEOUT" ]]; then
  yq -i '.timescaledb.connectTimeout="$TIMESCALEDB_CONNECT_TIMEOUT"' $CONFIG_FILE
fi

if [[ "" != "$TIMESCALEDB_SOCKET_TIMEOUT" ]]; then
  yq -i '.timescaledb.socketTimeout="$TIMESCALEDB_SOCKET_TIMEOUT"' $CONFIG_FILE
fi

if [[ "" != "$TIMESCALEDB_LOGUNCLOSED" ]]; then
  yq -i '.timescaledb.logUnclosedConnections="$TIMESCALEDB_LOGUNCLOSED"' $CONFIG_FILE
fi

if [[ "" != "$TIMESCALEDB_LOGGERLEVEL" ]]; then
  yq -i '.timescaledb.loggerLevel="$TIMESCALEDB_LOGGERLEVEL"' $CONFIG_FILE
fi

if [[ "$TIMESCALEDB_HEALTH_CHECK_NEEDED" == "true" ]]; then
  yq -i '.timescaledb.isHealthCheckNeeded="$TIMESCALEDB_HEALTH_CHECK_NEEDED"' $CONFIG_FILE
fi

if [[ "$SEARCH_ENABLED" == "true" ]]; then
  yq -i '.searchEnabled="true"' $CONFIG_FILE
fi

if [[ "$GRAPHQL_ENABLED" == "false" ]]; then
  yq -i '.graphQLEnabled="false"' $CONFIG_FILE
fi

if [[ "$MONGO_DEBUGGING_ENABLED" == "true" ]]; then
  yq -i '.logging.loggers.[org.mongodb.morphia.query]="TRACE"' $CONFIG_FILE
  yq -i '.logging.loggers.connection="TRACE"' $CONFIG_FILE
fi

if [[ "" != "$AZURE_MARKETPLACE_ACCESSKEY" ]]; then
  yq -i '.mktPlaceConfig.azureMarketplaceAccessKey="$AZURE_MARKETPLACE_ACCESSKEY"' $CONFIG_FILE
fi

if [[ "" != "$AZURE_MARKETPLACE_SECRETKEY" ]]; then
  yq -i '.mktPlaceConfig.azureMarketplaceSecretKey="$AZURE_MARKETPLACE_SECRETKEY"' $CONFIG_FILE
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
    yq -i '.sentinelServersConfig.sentinelAddresses.[$INDEX]="${REDIS_SENTINEL_URL}"' $REDISSON_CACHE_FILE
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

if [[ "" != "$REDIS_SUBSCRIPTIONS_PER_CONNECTION" ]]; then
  yq -i '.redisAtmosphereConfig.subscriptionsPerConnection="$REDIS_SUBSCRIPTIONS_PER_CONNECTION"' $CONFIG_FILE
fi

if [[ "" != "$REDIS_SUBSCRIPTION_CONNECTION_POOL_SIZE" ]]; then
  yq -i '.redisAtmosphereConfig.subscriptionConnectionPoolSize="$REDIS_SUBSCRIPTION_CONNECTION_POOL_SIZE"' $CONFIG_FILE
fi

if [[ "" != "$CACHE_NAMESPACE" ]]; then
    yq -i '.cacheConfig.cacheNamespace="$CACHE_NAMESPACE"' $CONFIG_FILE
fi

if [[ "" != "$CACHE_BACKEND" ]]; then
    yq -i '.cacheConfig.cacheBackend="$CACHE_BACKEND"' $CONFIG_FILE
fi

if [[ "" != "$GCP_MARKETPLACE_ENABLED" ]]; then
    yq -i '.gcpMarketplaceConfig.enabled="$GCP_MARKETPLACE_ENABLED"' $CONFIG_FILE
fi

if [[ "" != "$GCP_MARKETPLACE_SUBSCRIPTION_NAME" ]]; then
    yq -i '.gcpMarketplaceConfig.subscriptionName="$GCP_MARKETPLACE_SUBSCRIPTION_NAME"' $CONFIG_FILE
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

if [[ "" != "$COMMAND_LIBRARY_SERVICE_BASE_URL" ]]; then
  yq -i '.commandLibraryServiceConfig.baseUrl="$COMMAND_LIBRARY_SERVICE_BASE_URL"' $CONFIG_FILE
fi

if [[ "" != "$BUGSNAG_API_KEY" ]]; then
  yq -i '.bugsnagApiKey="$BUGSNAG_API_KEY"' $CONFIG_FILE
fi

if [[ "" != "$ACCOUNT_LICENSE_CHECK_JOB_FREQUENCY" ]]; then
  yq -i '.jobsFrequencyConfig.accountLicenseCheckJobFrequencyInMinutes="$ACCOUNT_LICENSE_CHECK_JOB_FREQUENCY"' $CONFIG_FILE
fi

if [[ "" != "$ACCOUNT_DELETION_JOB_FREQUENCY" ]]; then
  yq -i '.jobsFrequencyConfig.accountDeletionJobFrequencyInMinutes="$ACCOUNT_DELETION_JOB_FREQUENCY"' $CONFIG_FILE
fi

if [[ "" != "$MANAGER_TO_COMMAND_LIBRARY_SERVICE_SECRET" ]]; then
  yq -i '.commandLibraryServiceConfig.managerToCommandLibraryServiceSecret="$MANAGER_TO_COMMAND_LIBRARY_SERVICE_SECRET"' $CONFIG_FILE
fi

if [[ "" != "$DELEGATE_SERVICE_TARGET" ]]; then
  yq -i '.grpcDelegateServiceClientConfig.target="$DELEGATE_SERVICE_TARGET"' $CONFIG_FILE
fi

if [[ "" != "$DELEGATE_SERVICE_AUTHORITY" ]]; then
  yq -i '.grpcDelegateServiceClientConfig.authority="$DELEGATE_SERVICE_AUTHORITY"' $CONFIG_FILE
fi

if [[ "" != "$DELEGATE_SERVICE_MANAGEMENT_AUTHORITY" ]]; then
  yq -i '.grpcDMSClientConfig.authority="$DELEGATE_SERVICE_MANAGEMENT_AUTHORITY"' $CONFIG_FILE
fi

if [[ "" != "$DELEGATE_SERVICE_MANAGEMENT_TARGET" ]]; then
  yq -i '.grpcDMSClientConfig.target="$DELEGATE_SERVICE_MANAGEMENT_TARGET"' $CONFIG_FILE
fi

if [[ "" != "$DELEGATE_SERVICE_MANAGEMENT_SECRET" ]]; then
  yq -i '.dmsSecret="$DELEGATE_SERVICE_MANAGEMENT_SECRET"' $CONFIG_FILE
fi


if [[ "" != "$DELEGATE_GRPC_TARGET" ]]; then
  yq -i '.grpcOnpremDelegateClientConfig.target="$DELEGATE_GRPC_TARGET"' $CONFIG_FILE
fi

if [[ "" != "$DELEGATE_GRPC_AUTHORITY" ]]; then
  yq -i '.grpcOnpremDelegateClientConfig.authority="$DELEGATE_GRPC_AUTHORITY"' $CONFIG_FILE
fi

if [[ "" != "$NG_MANAGER_AUTHORITY" ]]; then
  yq -i '.grpcClientConfig.authority="$NG_MANAGER_AUTHORITY"' $CONFIG_FILE
fi

if [[ "" != "$NG_MANAGER_TARGET" ]]; then
  yq -i '.grpcClientConfig.target="$NG_MANAGER_TARGET"' $CONFIG_FILE
fi

if [[ "" != "$REMINDERS_BEFORE_ACCOUNT_DELETION" ]]; then
  yq -i '.numberOfRemindersBeforeAccountDeletion="$REMINDERS_BEFORE_ACCOUNT_DELETION"' $CONFIG_FILE
fi

if [[ "" != "$EXPORT_DATA_BATCH_SIZE" ]]; then
  yq -i '.exportAccountDataBatchSize="$EXPORT_DATA_BATCH_SIZE"' $CONFIG_FILE
fi

if [[ "" != "$COMMAND_LIBRARY_PUBLISHING_ALLOWED" ]]; then
  yq -i '.commandLibraryServiceConfig.publishingAllowed="$COMMAND_LIBRARY_PUBLISHING_ALLOWED"' $CONFIG_FILE
fi

if [[ "" != "$COMMAND_LIBRARY_PUBLISHING_SECRET" ]]; then
  yq -i '.commandLibraryServiceConfig.publishingSecret="$COMMAND_LIBRARY_PUBLISHING_SECRET"' $CONFIG_FILE
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

if [[ "" != "$AUDIT_CLIENT_BASEURL" ]]; then
  yq -i '.auditClientConfig.baseUrl="$AUDIT_CLIENT_BASEURL"' $CONFIG_FILE
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
replace_key_value eventsFramework.redis.nettyThreads $EVENTS_FRAMEWORK_NETTY_THREADS
replace_key_value eventsFramework.redis.sslConfig.enabled $EVENTS_FRAMEWORK_REDIS_SSL_ENABLED
replace_key_value eventsFramework.redis.sslConfig.CATrustStorePath $EVENTS_FRAMEWORK_REDIS_SSL_CA_TRUST_STORE_PATH
replace_key_value eventsFramework.redis.sslConfig.CATrustStorePassword $EVENTS_FRAMEWORK_REDIS_SSL_CA_TRUST_STORE_PASSWORD
replace_key_value ngAuthUIEnabled "$HARNESS_ENABLE_NG_AUTH_UI_PLACEHOLDER"
replace_key_value portal.zendeskBaseUrl "$ZENDESK_BASE_URL"
replace_key_value deployVariant "$DEPLOY_VERSION"

if [[ "" != ${GATEWAY_PATH_PREFIX+x} ]]; then
  yq -i '.portal.gatewayPathPrefix="$GATEWAY_PATH_PREFIX"' $CONFIG_FILE
fi

if [[ "" != "$NG_MANAGER_BASE_URL" ]]; then
  yq -i '.ngManagerServiceHttpClientConfig.baseUrl="$NG_MANAGER_BASE_URL"' $CONFIG_FILE
fi

if [[ "" != "$ENABLE_USER_CHANGESTREAM" ]]; then
  yq -i '.userChangeStreamEnabled="$ENABLE_USER_CHANGESTREAM"' $CONFIG_FILE
fi

if [[ "" != "$DISABLE_DELEGATE_MGMT_IN_MANAGER" ]]; then
  yq -i '.disableDelegateMgmtInManager="$DISABLE_DELEGATE_MGMT_IN_MANAGER"' $CONFIG_FILE
fi

if [[ "" != "$GCP_SECRET_MANAGER_PROJECT" ]]; then
  yq -i '.secretsConfiguration.gcpSecretManagerProject="$GCP_SECRET_MANAGER_PROJECT"' $CONFIG_FILE
fi

if [[ "" != "$RESOLVE_SECRETS" ]]; then
  yq -i '.secretsConfiguration.secretResolutionEnabled="$RESOLVE_SECRETS"' $CONFIG_FILE
fi

if [[ "" != "$LDAP_GROUP_SYNC_INTERVAL" ]]; then
  yq -i '.ldapSyncJobConfig.syncInterval="$LDAP_GROUP_SYNC_INTERVAL"' $CONFIG_FILE
fi

if [[ "" != "$LDAP_GROUP_SYNC_POOL_SIZE" ]]; then
  yq -i '.ldapSyncJobConfig.poolSize="$LDAP_GROUP_SYNC_POOL_SIZE"' $CONFIG_FILE
fi

if [[ "" != "$LDAP_GROUP_SYNC_DEFAULT_CRON" ]]; then
  yq -i '.ldapSyncJobConfig.defaultCronExpression="$LDAP_GROUP_SYNC_DEFAULT_CRON"' $CONFIG_FILE
fi

if [[ "" != "$USE_GLOBAL_KMS_AS_BASE_ALGO" ]]; then
  yq -i '.useGlobalKMSAsBaseAlgo="$USE_GLOBAL_KMS_AS_BASE_ALGO"' $CONFIG_FILE
fi

if [[ "" != "$SEGMENT_ENABLED_NG" ]]; then
  yq -i '.segmentConfiguration.enabled="$SEGMENT_ENABLED_NG"' $CONFIG_FILE
fi

if [[ "" != "$SEGMENT_URL_NG" ]]; then
  yq -i '.segmentConfiguration.url="$SEGMENT_URL_NG"' $CONFIG_FILE
fi

if [[ "" != "$SEGMENT_APIKEY_NG" ]]; then
  yq -i '.segmentConfiguration.apiKey="$SEGMENT_APIKEY_NG"' $CONFIG_FILE
fi

if [[ "" != "$SEGMENT_VERIFY_CERT_NG" ]]; then
  yq -i '.segmentConfiguration.certValidationRequired="$SEGMENT_VERIFY_CERT_NG"' $CONFIG_FILE
fi

if [[ "" != "$SECOPS_EMAIL" ]]; then
 yq -i '.totp.secOpsEmail="$SECOPS_EMAIL"' config.yml
fi

if [[ "" != "$INCORRECT_ATTEMPTS_UNTIL_SECOPS_NOTIFIED" ]]; then
 yq -i '.totp.incorrectAttemptsUntilSecOpsNotified="$INCORRECT_ATTEMPTS_UNTIL_SECOPS_NOTIFIED"' config.yml
fi

if [[ "" != "$AGENT_MTLS_SUBDOMAIN" ]]; then
  yq -i '.agentMtlsSubdomain="$AGENT_MTLS_SUBDOMAIN"' $CONFIG_FILE
fi

if [[ "" != "$CD_TSDB_RETENTION_PERIOD_MONTHS" ]]; then
  yq write -i $CD_TSDB_RETENTION_PERIOD_MONTHS cdTsDbRetentionPeriodMonths "$CD_TSDB_RETENTION_PERIOD_MONTHS"
fi
