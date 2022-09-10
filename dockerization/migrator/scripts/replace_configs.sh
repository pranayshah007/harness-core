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
    yq -i '.env(CONFIG_KEY)=env(CONFIG_VALUE)' $CONFIG_FILE
  fi
}

write_mongo_hosts_and_ports() {
  IFS=',' read -ra HOST_AND_PORT <<< "$2"
  for INDEX in "${!HOST_AND_PORT[@]}"; do
    HOST=$(cut -d: -f 1 <<< "${HOST_AND_PORT[$INDEX]}")
    PORT=$(cut -d: -f 2 -s <<< "${HOST_AND_PORT[$INDEX]}")

    export ARG1=$1; yq -i '.env(ARG1).env(INDEX).host=env(HOST)' $CONFIG_FILE
    if [[ "" != "$PORT" ]]; then
      export ARG1=$1; yq -i '.env(ARG1).env(INDEX).port=env(PORT)' $CONFIG_FILE
    fi
  done
}

write_mongo_params() {
  IFS='&' read -ra PARAMS <<< "$2"
  for PARAM_PAIR in "${PARAMS[@]}"; do
    NAME=$(cut -d= -f 1 <<< "$PARAM_PAIR")
    VALUE=$(cut -d= -f 2 <<< "$PARAM_PAIR")
    export ARG1=$1; yq -i '.env(ARG1).params.env(NAME)=env(VALUE)' $CONFIG_FILE
  done
}

yq -i 'del(.server.applicationConnectors.[] | select(.type == "h2"))' $CONFIG_FILE
yq -i 'del(.cg.grpcServerConfig.connectors.[] | select(.secure == true))' $CONFIG_FILE

yq -i '.server.adminConnectors=[]' $CONFIG_FILE

if [[ "" != "$LOGGING_LEVEL" ]]; then
    yq -i '.logging.level=env(LOGGING_LEVEL)' $CONFIG_FILE
fi

if [[ "" != "$LOGGERS" ]]; then
  IFS=',' read -ra LOGGER_ITEMS <<< "$LOGGERS"
  for ITEM in "${LOGGER_ITEMS[@]}"; do
    LOGGER=`echo $ITEM | awk -F= '{print $1}'`
    LOGGER_LEVEL=`echo $ITEM | awk -F= '{print $2}'`
    yq -i '.logging.loggers.env(LOGGER)=env(LOGGER_LEVEL)' $CONFIG_FILE
  done
fi

if [[ "" != "$SERVER_PORT" ]]; then
  yq -i '.server.applicationConnectors[0].port=env(SERVER_PORT)' $CONFIG_FILE
else
  yq -i '.server.applicationConnectors[0].port=9080' $CONFIG_FILE
fi

if [[ "" != "$GRPC_SERVER_PORT" ]]; then
  yq -i '.cg.grpcServerConfig.connectors[0].port=env(GRPC_SERVER_PORT)' $CONFIG_FILE
fi

if [[ "" != "$SERVER_MAX_THREADS" ]]; then
  yq -i '.server.maxThreads=env(SERVER_MAX_THREADS)' $CONFIG_FILE
fi

if [[ "" != "$UI_SERVER_URL" ]]; then
  yq -i '.cg.portal.url=env(UI_SERVER_URL)' $CONFIG_FILE
fi

if [[ "" != "$AUTHTOKENEXPIRYINMILLIS" ]]; then
  yq -i '.cg.portal.authTokenExpiryInMillis=env(AUTHTOKENEXPIRYINMILLIS)' $CONFIG_FILE
fi

if [[ "" != "$EXTERNAL_GRAPHQL_RATE_LIMIT" ]]; then
  yq -i '.cg.portal.externalGraphQLRateLimitPerMinute=env(EXTERNAL_GRAPHQL_RATE_LIMIT)' $CONFIG_FILE
fi

if [[ "" != "$CUSTOM_DASH_GRAPHQL_RATE_LIMIT" ]]; then
  yq -i '.cg.portal.customDashGraphQLRateLimitPerMinute=env(CUSTOM_DASH_GRAPHQL_RATE_LIMIT)' $CONFIG_FILE
fi

if [[ "" != "$ALLOWED_ORIGINS" ]]; then
  yq -i '.cg.portal.allowedOrigins=env(ALLOWED_ORIGINS)' $CONFIG_FILE
fi

if [[ "" != "$STORE_REQUEST_PAYLOAD" ]]; then
  yq -i '.cg.auditConfig.storeRequestPayload=env(STORE_REQUEST_PAYLOAD)' $CONFIG_FILE
fi

if [[ "" != "$STORE_RESPONSE_PAYLOAD" ]]; then
  yq -i '.cg.auditConfig.storeResponsePayload=env(STORE_RESPONSE_PAYLOAD)' $CONFIG_FILE
fi

if [[ "" != "$MONGO_URI" ]]; then
  yq -i '.cg.mongo.uri=env(MONGO_URI)' $CONFIG_FILE
fi

if [[ "" != "$MONGO_HOSTS_AND_PORTS" ]]; then
  yq -i 'del(.cg.mongo.uri)' $CONFIG_FILE
  yq -i '.cg.mongo.username=env(MONGO_USERNAME)' $CONFIG_FILE
  yq -i '.cg.mongo.password=env(MONGO_PASSWORD)' $CONFIG_FILE
  yq -i '.cg.mongo.database=env(MONGO_DATABASE)' $CONFIG_FILE
  yq -i '.cg.mongo.schema=env(MONGO_SCHEMA)' $CONFIG_FILE
  write_mongo_hosts_and_ports cg.mongo "$MONGO_HOSTS_AND_PORTS"
  write_mongo_params cg.mongo "$MONGO_PARAMS"
fi

if [[ "" != "$MONGO_TRACE_MODE" ]]; then
  yq -i '.cg.mongo.traceMode=env(MONGO_TRACE_MODE)' $CONFIG_FILE
fi

if [[ "" != "$MONGO_SSL_CONFIG" ]]; then
  yq -i '.cg.mongo.mongoSSLConfig.mongoSSLEnabled=env(MONGO_SSL_CONFIG)' $CONFIG_FILE
fi

if [[ "" != "$MONGO_SSL_CA_TRUST_STORE_PATH" ]]; then
  yq -i '.cg.mongo.mongoSSLConfig.mongoTrustStorePath=env(MONGO_SSL_CA_TRUST_STORE_PATH)' $CONFIG_FILE
fi

if [[ "" != "$MONGO_SSL_CA_TRUST_STORE_PASSWORD" ]]; then
  yq -i '.cg.mongo.mongoSSLConfig.mongoTrustStorePassword=env(MONGO_SSL_CA_TRUST_STORE_PASSWORD)' $CONFIG_FILE
fi

if [[ "" != "$MONGO_CONNECT_TIMEOUT" ]]; then
  yq -i '.cg.mongo.connectTimeout=env(MONGO_CONNECT_TIMEOUT)' $CONFIG_FILE
fi

if [[ "" != "$MONGO_SERVER_SELECTION_TIMEOUT" ]]; then
  yq -i '.cg.mongo.serverSelectionTimeout=env(MONGO_SERVER_SELECTION_TIMEOUT)' $CONFIG_FILE
fi

if [[ "" != "$MAX_CONNECTION_IDLE_TIME" ]]; then
  yq -i '.cg.mongo.maxConnectionIdleTime=env(MAX_CONNECTION_IDLE_TIME)' $CONFIG_FILE
fi

if [[ "" != "$MONGO_CONNECTIONS_PER_HOST" ]]; then
  yq -i '.cg.mongo.connectionsPerHost=env(MONGO_CONNECTIONS_PER_HOST)' $CONFIG_FILE
fi

if [[ "" != "$MONGO_INDEX_MANAGER_MODE" ]]; then
  yq -i '.cg.mongo.indexManagerMode=env(MONGO_INDEX_MANAGER_MODE)' $CONFIG_FILE
fi

if [[ "" != "$EVEMTS_MONGO_INDEX_MANAGER_MODE" ]]; then
  yq -i '.cg.events-mongo.indexManagerMode=env(EVEMTS_MONGO_INDEX_MANAGER_MODE)' $CONFIG_FILE
fi

if [[ "" != "$EVENTS_MONGO_URI" ]]; then
  yq -i '.cg.events-mongo.uri=env(EVENTS_MONGO_URI)' $CONFIG_FILE
else
  if [[ "" != "$EVENTS_MONGO_HOSTS_AND_PORTS" ]]; then
    yq -i 'del(.cg.events-mongo.uri)' $CONFIG_FILE
    yq -i '.cg.events-mongo.username=env(EVENTS_MONGO_USERNAME)' $CONFIG_FILE
    yq -i '.cg.events-mongo.password=env(EVENTS_MONGO_PASSWORD)' $CONFIG_FILE
    yq -i '.cg.events-mongo.database=env(EVENTS_MONGO_DATABASE)' $CONFIG_FILE
    yq -i '.cg.events-mongo.schema=env(EVENTS_MONGO_SCHEMA)' $CONFIG_FILE
    write_mongo_hosts_and_ports cg.events-mongo "$EVENTS_MONGO_HOSTS_AND_PORTS"
    write_mongo_params cg.events-mongo "$EVENTS_MONGO_PARAMS"
  else
    yq -i 'del(.cg.events-mongo)' $CONFIG_FILE
  fi
fi

if [[ "" != "$CF_CLIENT_API_KEY" ]]; then
  yq -i '.cg.cfClientConfig.apiKey=env(CF_CLIENT_API_KEY)' $CONFIG_FILE
fi

if [[ "" != "$CF_CLIENT_CONFIG_URL" ]]; then
  yq -i '.cg.cfClientConfig.configUrl=env(CF_CLIENT_CONFIG_URL)' $CONFIG_FILE
fi

if [[ "" != "$CF_CLIENT_EVENT_URL" ]]; then
  yq -i '.cg.cfClientConfig.eventUrl=env(CF_CLIENT_EVENT_URL)' $CONFIG_FILE
fi

if [[ "" != "$CF_CLIENT_ANALYTICS_ENABLED" ]]; then
  yq -i '.cg.cfClientConfig.analyticsEnabled=env(CF_CLIENT_ANALYTICS_ENABLED)' $CONFIG_FILE
fi

if [[ "" != "$CF_CLIENT_CONNECTION_TIMEOUT" ]]; then
  yq -i '.cg.cfClientConfig.connectionTimeout=env(CF_CLIENT_CONNECTION_TIMEOUT)' $CONFIG_FILE
fi

if [[ "" != "$CF_CLIENT_READ_TIMEOUT" ]]; then
  yq -i '.cg.cfClientConfig.readTimeout=env(CF_CLIENT_READ_TIMEOUT)' $CONFIG_FILE
fi

if [[ "" != "$CF_MIGRATION_ENABLED" ]]; then
  yq -i '.cg.cfMigrationConfig.enabled=env(CF_MIGRATION_ENABLED)' $CONFIG_FILE
fi

if [[ "" != "$CF_MIGRATION_ADMIN_URL" ]]; then
  yq -i '.cg.cfMigrationConfig.adminUrl=env(CF_MIGRATION_ADMIN_URL)' $CONFIG_FILE
fi

if [[ "" != "$CF_MIGRATION_API_KEY" ]]; then
  yq -i '.cg.cfMigrationConfig.apiKey=env(CF_MIGRATION_API_KEY)' $CONFIG_FILE
fi

if [[ "" != "$CF_MIGRATION_ACCOUNT" ]]; then
  yq -i '.cg.cfMigrationConfig.account=env(CF_MIGRATION_ACCOUNT)' $CONFIG_FILE
fi

if [[ "" != "$CF_MIGRATION_ORG" ]]; then
  yq -i '.cg.cfMigrationConfig.org=env(CF_MIGRATION_ORG)' $CONFIG_FILE
fi

if [[ "" != "$CF_MIGRATION_PROJECT" ]]; then
  yq -i '.cg.cfMigrationConfig.project=env(CF_MIGRATION_PROJECT)' $CONFIG_FILE
fi

if [[ "" != "$CF_MIGRATION_ENVIRONMENT" ]]; then
  yq -i '.cg.cfMigrationConfig.environment=env(CF_MIGRATION_ENVIRONMENT)' $CONFIG_FILE
fi

replace_key_value cg.featureFlagConfig.featureFlagSystem "$FEATURE_FLAG_SYSTEM"
replace_key_value cg.featureFlagConfig.syncFeaturesToCF "$SYNC_FEATURES_TO_CF"


if [[ "" != "$ELASTICSEARCH_URI" ]]; then
  yq -i '.cg.elasticsearch.uri=env(ELASTICSEARCH_URI)' $CONFIG_FILE
fi

if [[ "" != "$ELASTICSEARCH_INDEX_SUFFIX" ]]; then
  yq -i '.cg.elasticsearch.indexSuffix=env(ELASTICSEARCH_INDEX_SUFFIX)' $CONFIG_FILE
fi

if [[ "" != "$ELASTICSEARCH_MONGO_TAG_NAME" ]]; then
 yq -i '.cg.elasticsearch.mongoTagKey=env(ELASTICSEARCH_MONGO_TAG_NAME)' $CONFIG_FILE
fi

if [[ "" != "$ELASTICSEARCH_MONGO_TAG_VALUE" ]]; then
 yq -i '.cg.elasticsearch.mongoTagValue=env(ELASTICSEARCH_MONGO_TAG_VALUE)' $CONFIG_FILE
fi

if [[ "" != "$MONGO_LOCK_URI" ]]; then
  yq -i '.cg.mongo.locksUri=env(MONGO_LOCK_URI)' $CONFIG_FILE
fi

yq -i '.server.requestLog.appenders[0].threshold="TRACE"' $CONFIG_FILE

if [[ "$STACK_DRIVER_LOGGING_ENABLED" == "true" ]]; then
  yq -i 'del(.logging.appenders.[] | select(.type == "file"))' $CONFIG_FILE
  yq -i 'del(.logging.appenders.[] | select(.type == "console"))' $CONFIG_FILE
  yq -i '(.logging.appenders | select(.type == gke-console) | .stackdriverLogEnabled) = true' $CONFIG_FILE
else
  if [[ "$ROLLING_FILE_LOGGING_ENABLED" == "true" ]]; then
    yq -i 'del(.logging.appenders.[] | select(.type == "gke-console"))' $CONFIG_FILE
    yq -i '(.logging.appenders | select(.type == file) | .currentLogFilename) = "/opt/harness/logs/portal.log"' $CONFIG_FILE
    yq -i '(.logging.appenders | select(.type == file) | .archivedLogFilenamePattern) = "/opt/harness/logs/portal.%d.%i.log"' $CONFIG_FILE
  else
    yq -i 'del(.logging.appenders.[] | select(.type == "file"))' $CONFIG_FILE
    yq -i 'del(.logging.appenders.[] | select(.type == "gke-console"))' $CONFIG_FILE
  fi
fi

if [[ "" != "$WATCHER_METADATA_URL" ]]; then
  yq -i '.cg.watcherMetadataUrl=env(WATCHER_METADATA_URL)' $CONFIG_FILE
fi

if [[ "" != "$DELEGATE_METADATA_URL" ]]; then
  yq -i '.cg.delegateMetadataUrl=env(DELEGATE_METADATA_URL)' $CONFIG_FILE
fi

if [[ "" != "$API_URL" ]]; then
  yq -i '.cg.apiUrl=env(API_URL)' $CONFIG_FILE
fi

if [[ "" != "$ENV_PATH" ]]; then
  yq -i '.cg.envPath=env(ENV_PATH)' $CONFIG_FILE
fi

if [[ "" != "$DEPLOY_MODE" ]]; then
  yq -i '.cg.deployMode=env(DEPLOY_MODE)' $CONFIG_FILE
fi

yq -i '.cg.common.license_key=env(NEWRELIC_LICENSE_KEY)' $NEWRELIC_FILE

if [[ "$DISABLE_NEW_RELIC" == "true" ]]; then
  yq -i '.cg.common.agent_enabled=false' $NEWRELIC_FILE
fi

if [[ "" != "$jwtPasswordSecret" ]]; then
  yq -i '.cg.portal.jwtPasswordSecret=env(jwtPasswordSecret)' $CONFIG_FILE
fi

if [[ "" != "$jwtExternalServiceSecret" ]]; then
  yq -i '.cg.portal.jwtExternalServiceSecret=env(jwtExternalServiceSecret)' $CONFIG_FILE
fi

if [[ "" != "$jwtZendeskSecret" ]]; then
  yq -i '.cg.portal.jwtZendeskSecret=env(jwtZendeskSecret)' $CONFIG_FILE
fi

if [[ "" != "$jwtMultiAuthSecret" ]]; then
  yq -i '.cg.portal.jwtMultiAuthSecret=env(jwtMultiAuthSecret)' $CONFIG_FILE
fi

if [[ "" != "$jwtSsoRedirectSecret" ]]; then
  yq -i '.cg.portal.jwtSsoRedirectSecret=env(jwtSsoRedirectSecret)' $CONFIG_FILE
fi

if [[ "" != "$jwtAuthSecret" ]]; then
  yq -i '.cg.portal.jwtAuthSecret=env(jwtAuthSecret)' $CONFIG_FILE
fi

if [[ "" != "$jwtMarketPlaceSecret" ]]; then
  yq -i '.cg.portal.jwtMarketPlaceSecret=env(jwtMarketPlaceSecret)' $CONFIG_FILE
fi

if [[ "" != "$jwtIdentityServiceSecret" ]]; then
  yq -i '.cg.portal.jwtIdentityServiceSecret=env(jwtIdentityServiceSecret)' $CONFIG_FILE
fi

if [[ "" != "$jwtDataHandlerSecret" ]]; then
  yq -i '.cg.portal.jwtDataHandlerSecret=env(jwtDataHandlerSecret)' $CONFIG_FILE
fi

if [[ "" != "$jwtNextGenManagerSecret" ]]; then
  yq -i '.cg.portal.jwtNextGenManagerSecret=env(jwtNextGenManagerSecret)' $CONFIG_FILE
fi

if [[ "" != "$FEATURES" ]]; then
  yq -i '.cg.featuresEnabled=env(FEATURES)' $CONFIG_FILE
fi

if [[ "" != "$SAMPLE_TARGET_ENV" ]]; then
  yq -i '.cg.sampleTargetEnv=env(SAMPLE_TARGET_ENV)' $CONFIG_FILE
fi

if [[ "" != "$SAMPLE_TARGET_STATUS_HOST" ]]; then
  yq -i '.cg.sampleTargetStatusHost=env(SAMPLE_TARGET_STATUS_HOST)' $CONFIG_FILE
fi

if [[ "" != "$GLOBAL_WHITELIST" ]]; then
  yq -i '.cg.globalWhitelistConfig.filters=env(GLOBAL_WHITELIST)' $CONFIG_FILE
fi

if [[ "" != "$SMTP_HOST" ]]; then
  yq -i '.cg.smtp.host=env(SMTP_HOST)' $CONFIG_FILE
fi

if [[ "" != "$SMTP_USERNAME" ]]; then
  yq -i '.cg.smtp.username=env(SMTP_USERNAME)' $CONFIG_FILE
fi

if [[ "" != "$SMTP_PASSWORD" ]]; then
  yq -i '.cg.smtp.password=env(SMTP_PASSWORD)' $CONFIG_FILE
fi

if [[ "" != "$SMTP_USE_SSL" ]]; then
  yq -i '.cg.smtp.useSSL=env(SMTP_USE_SSL)' $CONFIG_FILE
fi

if [[ "" != "$MARKETO_ENABLED" ]]; then
  yq -i '.cg.marketoConfig.enabled=env(MARKETO_ENABLED)' $CONFIG_FILE
fi

if [[ "" != "$MARKETO_URL" ]]; then
  yq -i '.cg.marketoConfig.url=env(MARKETO_URL)' $CONFIG_FILE
fi

if [[ "" != "$MARKETO_CLIENT_ID" ]]; then
  yq -i '.cg.marketoConfig.clientId=env(MARKETO_CLIENT_ID)' $CONFIG_FILE
fi

if [[ "" != "$MARKETO_CLIENT_SECRET" ]]; then
  yq -i '.cg.marketoConfig.clientSecret=env(MARKETO_CLIENT_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$SEGMENT_ENABLED" ]]; then
  yq -i '.cg.segmentConfig.enabled=env(SEGMENT_ENABLED)' $CONFIG_FILE
fi

if [[ "" != "$SEGMENT_URL" ]]; then
  yq -i '.cg.segmentConfig.url=env(SEGMENT_URL)' $CONFIG_FILE
fi

if [[ "" != "$SEGMENT_APIKEY" ]]; then
  yq -i '.cg.segmentConfig.apiKey=env(SEGMENT_APIKEY)' $CONFIG_FILE
fi

if [[ "" != "$SALESFORCE_USERNAME" ]]; then
  yq -i '.cg.salesforceConfig.userName=env(SALESFORCE_USERNAME)' $CONFIG_FILE
fi

if [[ "" != "$SALESFORCE_PASSWORD" ]]; then
  yq -i '.cg.salesforceConfig.password=env(SALESFORCE_PASSWORD)' $CONFIG_FILE
fi

if [[ "" != "$SALESFORCE_CONSUMER_KEY" ]]; then
  yq -i '.cg.salesforceConfig.consumerKey=env(SALESFORCE_CONSUMER_KEY)' $CONFIG_FILE
fi

if [[ "" != "$SALESFORCE_CONSUMER_SECRET" ]]; then
  yq -i '.cg.salesforceConfig.consumerSecret=env(SALESFORCE_CONSUMER_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$SALESFORCE_GRANT_TYPE" ]]; then
  yq -i '.cg.salesforceConfig.grantType=env(SALESFORCE_GRANT_TYPE)' $CONFIG_FILE
fi

if [[ "" != "$SALESFORCE_LOGIN_INSTANCE_DOMAIN" ]]; then
  yq -i '.cg.salesforceConfig.loginInstanceDomain=env(SALESFORCE_LOGIN_INSTANCE_DOMAIN)' $CONFIG_FILE
fi

if [[ "" != "$SALESFORCE_API_VERSION" ]]; then
  yq -i '.cg.salesforceConfig.apiVersion=env(SALESFORCE_API_VERSION)' $CONFIG_FILE
fi

if [[ "" != "$SALESFORCE_INTEGRATION_ENABLED" ]]; then
  yq -i '.cg.salesforceConfig.enabled=env(SALESFORCE_INTEGRATION_ENABLED)' $CONFIG_FILE
fi

if [[ "" != "$CE_SETUP_CONFIG_AWS_ACCOUNT_ID" ]]; then
  yq -i '.cg.ceSetUpConfig.awsAccountId=env(CE_SETUP_CONFIG_AWS_ACCOUNT_ID)' $CONFIG_FILE
fi

if [[ "" != "$CE_SETUP_CONFIG_AWS_S3_BUCKET_NAME" ]]; then
  yq -i '.cg.ceSetUpConfig.awsS3BucketName=env(CE_SETUP_CONFIG_AWS_S3_BUCKET_NAME)' $CONFIG_FILE
fi

if [[ "" != "$CE_SETUP_CONFIG_GCP_PROJECT_ID" ]]; then
  yq -i '.cg.ceSetUpConfig.gcpProjectId=env(CE_SETUP_CONFIG_GCP_PROJECT_ID)' $CONFIG_FILE
fi

if [[ "" != "$CE_SETUP_CONFIG_AWS_ROLE_NAME" ]]; then
  yq -i '.cg.ceSetUpConfig.awsRoleName=env(CE_SETUP_CONFIG_AWS_ROLE_NAME)' $CONFIG_FILE
fi

if [[ "" != "$CE_SETUP_CONFIG_SAMPLE_ACCOUNT_ID" ]]; then
  yq -i '.cg.ceSetUpConfig.sampleAccountId=env(CE_SETUP_CONFIG_SAMPLE_ACCOUNT_ID)' $CONFIG_FILE
fi

if [[ "" != "$CE_SETUP_CONFIG_AWS_ACCESS_KEY" ]]; then
  yq -i '.cg.ceSetUpConfig.awsAccessKey=env(CE_SETUP_CONFIG_AWS_ACCESS_KEY)' $CONFIG_FILE
fi

if [[ "" != "$CE_SETUP_CONFIG_AWS_SECRET_KEY" ]]; then
  yq -i '.cg.ceSetUpConfig.awsSecretKey=env(CE_SETUP_CONFIG_AWS_SECRET_KEY)' $CONFIG_FILE
fi

if [[ "" != "$CE_SETUP_CONFIG_MASTER_CLOUD_FORMATION" ]]; then
  yq -i '.cg.ceSetUpConfig.masterAccountCloudFormationTemplateLink=env(CE_SETUP_CONFIG_MASTER_CLOUD_FORMATION)' $CONFIG_FILE
fi

if [[ "" != "$CE_SETUP_CONFIG_LINKED_CLOUD_FORMATION" ]]; then
  yq -i '.cg.ceSetUpConfig.linkedAccountCloudFormationTemplateLink=env(CE_SETUP_CONFIG_LINKED_CLOUD_FORMATION)' $CONFIG_FILE
fi

if [[ "" != "$CE_SETUP_CONFIG_AZURE_CLIENTSECRET" ]]; then
  yq -i '.cg.ceSetUpConfig.azureAppClientSecret=env(CE_SETUP_CONFIG_AZURE_CLIENTSECRET)' $CONFIG_FILE
fi

if [[ "" != "$CE_SETUP_CONFIG_AZURE_CLIENTID" ]]; then
  yq -i '.cg.ceSetUpConfig.azureAppClientId=env(CE_SETUP_CONFIG_AZURE_CLIENTID)' $CONFIG_FILE
fi

if [[ "" != "$DATADOG_ENABLED" ]]; then
  yq -i '.cg.datadogConfig.enabled=env(DATADOG_ENABLED)' $CONFIG_FILE
fi

if [[ "" != "$DATADOG_APIKEY" ]]; then
  yq -i '.cg.datadogConfig.apiKey=env(DATADOG_APIKEY)' $CONFIG_FILE
fi

if [[ "" != "$DELEGATE_DOCKER_IMAGE" ]]; then
  yq -i '.cg.portal.delegateDockerImage=env(DELEGATE_DOCKER_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$OPTIONAL_DELEGATE_TASK_REJECT_AT_LIMIT" ]]; then
  yq -i '.portal.optionalDelegateTaskRejectAtLimit=env(OPTIONAL_DELEGATE_TASK_REJECT_AT_LIMIT)' $CONFIG_FILE
fi

if [[ "" != "$EXECUTION_LOG_DATA_STORE" ]]; then
  yq -i '.cg.executionLogStorageMode=env(EXECUTION_LOG_DATA_STORE)' $CONFIG_FILE
fi

if [[ "" != "$FILE_STORAGE" ]]; then
  yq -i '.cg.fileStorageMode=env(FILE_STORAGE)' $CONFIG_FILE
fi

if [[ "" != "$CLUSTER_NAME" ]]; then
  yq -i '.cg.clusterName=env(CLUSTER_NAME)' $CONFIG_FILE
fi

if [[ "" != "$DEPLOYMENT_CLUSTER_NAME" ]]; then
  yq -i '.cg.deploymentClusterName=env(DEPLOYMENT_CLUSTER_NAME)' $CONFIG_FILE
fi

if [[ "" != "$BACKGROUND_SCHEDULER_CLUSTERED" ]]; then
  yq -i '.cg.backgroundScheduler.clustered=env(BACKGROUND_SCHEDULER_CLUSTERED)' $CONFIG_FILE
fi

if [[ "" != "$ALLOW_TRIAL_REGISTRATION" ]]; then
  yq -i '.cg.trialRegistrationAllowed=env(ALLOW_TRIAL_REGISTRATION)' $CONFIG_FILE
fi

if [[ "" != "$GITHUB_OAUTH_CLIENT" ]]; then
  yq -i '.cg.githubConfig.clientId=env(GITHUB_OAUTH_CLIENT)' $CONFIG_FILE
fi

if [[ "" != "$GITHUB_OAUTH_SECRET" ]]; then
  yq -i '.cg.githubConfig.clientSecret=env(GITHUB_OAUTH_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$GITHUB_OAUTH_CALLBACK_URL" ]]; then
  yq -i '.cg.githubConfig.callbackUrl=env(GITHUB_OAUTH_CALLBACK_URL)' $CONFIG_FILE
fi

if [[ "" != "$AZURE_OAUTH_CLIENT" ]]; then
  yq -i '.cg.azureConfig.clientId=env(AZURE_OAUTH_CLIENT)' $CONFIG_FILE
fi

if [[ "" != "$AZURE_OAUTH_SECRET" ]]; then
  yq -i '.cg.azureConfig.clientSecret=env(AZURE_OAUTH_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$AZURE_OAUTH_CALLBACK_URL" ]]; then
  yq -i '.cg.azureConfig.callbackUrl=env(AZURE_OAUTH_CALLBACK_URL)' $CONFIG_FILE
fi

if [[ "" != "$GOOGLE_OAUTH_CLIENT" ]]; then
  yq -i '.cg.googleConfig.clientId=env(GOOGLE_OAUTH_CLIENT)' $CONFIG_FILE
fi

if [[ "" != "$GOOGLE_OAUTH_SECRET" ]]; then
  yq -i '.cg.googleConfig.clientSecret=env(GOOGLE_OAUTH_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$GOOGLE_OAUTH_CALLBACK_URL" ]]; then
  yq -i '.cg.googleConfig.callbackUrl=env(GOOGLE_OAUTH_CALLBACK_URL)' $CONFIG_FILE
fi

if [[ "" != "$BITBUCKET_OAUTH_CLIENT" ]]; then
  yq -i '.cg.bitbucketConfig.clientId=env(BITBUCKET_OAUTH_CLIENT)' $CONFIG_FILE
fi

if [[ "" != "$BITBUCKET_OAUTH_SECRET" ]]; then
  yq -i '.cg.bitbucketConfig.clientSecret=env(BITBUCKET_OAUTH_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$BITBUCKET_OAUTH_CALLBACK_URL" ]]; then
  yq -i '.cg.bitbucketConfig.callbackUrl=env(BITBUCKET_OAUTH_CALLBACK_URL)' $CONFIG_FILE
fi

if [[ "" != "$GITLAB_OAUTH_CLIENT" ]]; then
  yq -i '.cg.gitlabConfig.clientId=env(GITLAB_OAUTH_CLIENT)' $CONFIG_FILE
fi

if [[ "" != "$GITLAB_OAUTH_SECRET" ]]; then
  yq -i '.cg.gitlabConfig.clientSecret=env(GITLAB_OAUTH_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$GITLAB_OAUTH_CALLBACK_URL" ]]; then
  yq -i '.cg.gitlabConfig.callbackUrl=env(GITLAB_OAUTH_CALLBACK_URL)' $CONFIG_FILE
fi

if [[ "" != "$LINKEDIN_OAUTH_CLIENT" ]]; then
  yq -i '.cg.linkedinConfig.clientId=env(LINKEDIN_OAUTH_CLIENT)' $CONFIG_FILE
fi

if [[ "" != "$LINKEDIN_OAUTH_SECRET" ]]; then
  yq -i '.cg.linkedinConfig.clientSecret=env(LINKEDIN_OAUTH_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$LINKEDIN_OAUTH_CALLBACK_URL" ]]; then
  yq -i '.cg.linkedinConfig.callbackUrl=env(LINKEDIN_OAUTH_CALLBACK_URL)' $CONFIG_FILE
fi

if [[ "" != "$AWS_MARKETPLACE_ACCESSKEY" ]]; then
  yq -i '.cg.mktPlaceConfig.awsAccessKey=env(AWS_MARKETPLACE_ACCESSKEY)' $CONFIG_FILE
fi

if [[ "" != "$AWS_MARKETPLACE_SECRETKEY" ]]; then
  yq -i '.cg.mktPlaceConfig.awsSecretKey=env(AWS_MARKETPLACE_SECRETKEY)' $CONFIG_FILE
fi

if [[ "" != "$AWS_MARKETPLACE_PRODUCTCODE" ]]; then
  yq -i '.cg.mktPlaceConfig.awsMarketPlaceProductCode=env(AWS_MARKETPLACE_PRODUCTCODE)' $CONFIG_FILE
fi

if [[ "" != "$AWS_MARKETPLACE_CE_PRODUCTCODE" ]]; then
  yq -i '.cg.mktPlaceConfig.awsMarketPlaceCeProductCode=env(AWS_MARKETPLACE_CE_PRODUCTCODE)' $CONFIG_FILE
fi

if [[ "" != "$ALLOW_BLACKLISTED_EMAIL_DOMAINS" ]]; then
  yq -i '.cg.blacklistedEmailDomainsAllowed=env(ALLOW_BLACKLISTED_EMAIL_DOMAINS)' $CONFIG_FILE
fi

if [[ "" != "$ALLOW_PWNED_PASSWORDS" ]]; then
  yq -i '.cg.pwnedPasswordsAllowed=env(ALLOW_PWNED_PASSWORDS)' $CONFIG_FILE
fi

if [[ "" != "$TIMESCALEDB_URI" ]]; then
  yq -i '.cg.timescaledb.timescaledbUrl=env(TIMESCALEDB_URI)' $CONFIG_FILE
fi

if [[ "" != "$TIMESCALEDB_USERNAME" ]]; then
  yq -i '.cg.timescaledb.timescaledbUsername=env(TIMESCALEDB_USERNAME)' $CONFIG_FILE
fi

if [[ "" != "$TIMESCALEDB_PASSWORD" ]]; then
  yq -i '.cg.timescaledb.timescaledbPassword=env(TIMESCALEDB_PASSWORD)' $CONFIG_FILE
fi

if [[ "" != "$TIMESCALEDB_CONNECT_TIMEOUT" ]]; then
  yq -i '.cg.timescaledb.connectTimeout=env(TIMESCALEDB_CONNECT_TIMEOUT)' $CONFIG_FILE
fi

if [[ "" != "$TIMESCALEDB_SOCKET_TIMEOUT" ]]; then
  yq -i '.cg.timescaledb.socketTimeout=env(TIMESCALEDB_SOCKET_TIMEOUT)' $CONFIG_FILE
fi

if [[ "" != "$TIMESCALEDB_LOGUNCLOSED" ]]; then
  yq -i '.cg.timescaledb.logUnclosedConnections=env(TIMESCALEDB_LOGUNCLOSED)' $CONFIG_FILE
fi

if [[ "" != "$TIMESCALEDB_LOGGERLEVEL" ]]; then
  yq -i '.cg.timescaledb.loggerLevel=env(TIMESCALEDB_LOGGERLEVEL)' $CONFIG_FILE
fi

if [[ "$TIMESCALEDB_HEALTH_CHECK_NEEDED" == "true" ]]; then
  yq -i '.cg.timescaledb.isHealthCheckNeeded=env(TIMESCALEDB_HEALTH_CHECK_NEEDED)' $CONFIG_FILE
fi

if [[ "$MONGO_DEBUGGING_ENABLED" == "true" ]]; then
  yq -i '.logging.loggers.[org.mongodb.morphia.query]=TRACE' $CONFIG_FILE
  yq -i '.logging.loggers.connection=TRACE' $CONFIG_FILE
fi

if [[ "" != "$AZURE_MARKETPLACE_ACCESSKEY" ]]; then
  yq -i '.cg.mktPlaceConfig.azureMarketplaceAccessKey=env(AZURE_MARKETPLACE_ACCESSKEY)' $CONFIG_FILE
fi

if [[ "" != "$AZURE_MARKETPLACE_SECRETKEY" ]]; then
  yq -i '.cg.mktPlaceConfig.azureMarketplaceSecretKey=env(AZURE_MARKETPLACE_SECRETKEY)' $CONFIG_FILE
fi

if [[ "" != "$WORKERS" ]]; then
  IFS=',' read -ra WORKER_ITEMS <<< "$WORKERS"
  for ITEM in "${WORKER_ITEMS[@]}"; do
    WORKER=`echo $ITEM | awk -F= '{print $1}'`
    WORKER_FLAG=`echo $ITEM | awk -F= '{print $2}'`
    yq -i '.cg.workers.active.env(WORKER)=env(WORKER_FLAG)' $CONFIG_FILE
  done
fi

if [[ "" != "$PUBLISHERS" ]]; then
  IFS=',' read -ra PUBLISHER_ITEMS <<< "$PUBLISHERS"
  for ITEM in "${PUBLISHER_ITEMS[@]}"; do
    PUBLISHER=`echo $ITEM | awk -F= '{print $1}'`
    PUBLISHER_FLAG=`echo $ITEM | awk -F= '{print $2}'`
    yq -i '.cg.publishers.active.env(PUBLISHER)=env(PUBLISHER_FLAG)' $CONFIG_FILE
  done
fi

if [[ "" != "$DISTRIBUTED_LOCK_IMPLEMENTATION" ]]; then
  yq -i '.cg.distributedLockImplementation=env(DISTRIBUTED_LOCK_IMPLEMENTATION)' $CONFIG_FILE
fi

if [[ "" != "$ATMOSPHERE_BACKEND" ]]; then
  yq -i '.cg.atmosphereBroadcaster=env(ATMOSPHERE_BACKEND)' $CONFIG_FILE
fi

yq -i 'del(.codec)' $REDISSON_CACHE_FILE

if [[ "" != "$REDIS_URL" ]]; then
  yq -i '.cg.redisLockConfig.redisUrl=env(REDIS_URL)' $CONFIG_FILE
  yq -i '.cg.redisAtmosphereConfig.redisUrl=env(REDIS_URL)' $CONFIG_FILE
  yq -i '.singleServerConfig.address=env(REDIS_URL)' $REDISSON_CACHE_FILE
fi

if [[ "$REDIS_SENTINEL" == "true" ]]; then
  yq -i '.cg.redisLockConfig.sentinel=true' $CONFIG_FILE
  yq -i '.cg.redisAtmosphereConfig.sentinel=true' $CONFIG_FILE
  yq -i 'del(.singleServerConfig)' $REDISSON_CACHE_FILE
fi

if [[ "" != "$REDIS_MASTER_NAME" ]]; then
  yq -i '.cg.redisLockConfig.masterName=env(REDIS_MASTER_NAME)' $CONFIG_FILE
  yq -i '.cg.redisAtmosphereConfig.masterName=env(REDIS_MASTER_NAME)' $CONFIG_FILE
  yq -i '.sentinelServersConfig.masterName=env(REDIS_MASTER_NAME)' $REDISSON_CACHE_FILE
fi

if [[ "" != "$REDIS_SENTINELS" ]]; then
  IFS=',' read -ra REDIS_SENTINEL_URLS <<< "$REDIS_SENTINELS"
  INDEX=0
  for REDIS_SENTINEL_URL in "${REDIS_SENTINEL_URLS[@]}"; do
    yq -i '.cg.redisLockConfig.sentinelUrls.env(INDEX)=env(REDIS_SENTINEL_URL)' $CONFIG_FILE
    yq -i '.cg.redisAtmosphereConfig.sentinelUrls.env(INDEX)=env(REDIS_SENTINEL_URL)' $CONFIG_FILE
    yq -i '.sentinelServersConfig.sentinelAddresses.env(INDEX)=env(REDIS_SENTINEL_URL)' $REDISSON_CACHE_FILE
    INDEX=$(expr $INDEX + 1)
  done
fi

if [[ "" != "$REDIS_ENV_NAMESPACE" ]]; then
    yq -i '.cg.redisLockConfig.envNamespace=env(REDIS_ENV_NAMESPACE)' $CONFIG_FILE
    yq -i '.cg.redisAtmosphereConfig.envNamespace=env(REDIS_ENV_NAMESPACE)' $CONFIG_FILE
fi

if [[ "" != "$REDIS_NETTY_THREADS" ]]; then
  yq -i '.cg.redisLockConfig.nettyThreads=env(REDIS_NETTY_THREADS)' $CONFIG_FILE
  yq -i '.cg.redisAtmosphereConfig.nettyThreads=env(REDIS_NETTY_THREADS)' $CONFIG_FILE
  yq -i '.nettyThreads=env(REDIS_NETTY_THREADS)' $REDISSON_CACHE_FILE
fi

if [[ "$REDIS_SCRIPT_CACHE" == "false" ]]; then
  yq -i '.cg.redisLockConfig.useScriptCache=false' $CONFIG_FILE
  yq -i '.cg.redisAtmosphereConfig.useScriptCache=false' $CONFIG_FILE
  yq -i '.useScriptCache=false' $REDISSON_CACHE_FILE
fi

if [[ "" != "$CACHE_NAMESPACE" ]]; then
    yq -i '.cg.cacheConfig.cacheNamespace=env(CACHE_NAMESPACE)' $CONFIG_FILE
fi

if [[ "" != "$CACHE_BACKEND" ]]; then
    yq -i '.cg.cacheConfig.cacheBackend=env(CACHE_BACKEND)' $CONFIG_FILE
fi

if [[ "" != "$GCP_MARKETPLACE_ENABLED" ]]; then
    yq -i '.cg.gcpMarketplaceConfig.enabled=env(GCP_MARKETPLACE_ENABLED)' $CONFIG_FILE
fi

if [[ "" != "$GCP_MARKETPLACE_SUBSCRIPTION_NAME" ]]; then
    yq -i '.cg.gcpMarketplaceConfig.subscriptionName=env(GCP_MARKETPLACE_SUBSCRIPTION_NAME)' $CONFIG_FILE
fi

if [[ "" != "$CURRENT_JRE" ]]; then
  yq -i '.cg.currentJre=env(CURRENT_JRE)' $CONFIG_FILE
fi

if [[ "" != "$MIGRATE_TO_JRE" ]]; then
  yq -i '.cg.migrateToJre=env(MIGRATE_TO_JRE)' $CONFIG_FILE
fi

if [[ "" != "$ORACLE_JRE_TAR_PATH" ]]; then
  yq -i '.cg.jreConfigs.oracle8u191.jreTarPath=env(ORACLE_JRE_TAR_PATH)' $CONFIG_FILE
fi

if [[ "" != "$OPENJDK_JRE_TAR_PATH" ]]; then
  yq -i '.cg.jreConfigs.openjdk8u242.jreTarPath=env(OPENJDK_JRE_TAR_PATH)' $CONFIG_FILE
fi

if [[ "" != "$CDN_URL" ]]; then
  yq -i '.cg.cdnConfig.url=env(CDN_URL)' $CONFIG_FILE
fi

if [[ "" != "$CDN_KEY" ]]; then
  yq -i '.cg.cdnConfig.keyName=env(CDN_KEY)' $CONFIG_FILE
fi

if [[ "" != "$CDN_KEY_SECRET" ]]; then
  yq -i '.cg.cdnConfig.keySecret=env(CDN_KEY_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$CDN_DELEGATE_JAR_PATH" ]]; then
  yq -i '.cg.cdnConfig.delegateJarPath=env(CDN_DELEGATE_JAR_PATH)' $CONFIG_FILE
fi

if [[ "" != "$CDN_WATCHER_JAR_BASE_PATH" ]]; then
  yq -i '.cg.cdnConfig.watcherJarBasePath=env(CDN_WATCHER_JAR_BASE_PATH)' $CONFIG_FILE
fi

if [[ "" != "$CDN_WATCHER_JAR_PATH" ]]; then
  yq -i '.cg.cdnConfig.watcherJarPath=env(CDN_WATCHER_JAR_PATH)' $CONFIG_FILE
fi

if [[ "" != "$CDN_WATCHER_METADATA_FILE_PATH" ]]; then
  yq -i '.cg.cdnConfig.watcherMetaDataFilePath=env(CDN_WATCHER_METADATA_FILE_PATH)' $CONFIG_FILE
fi

if [[ "" != "$CDN_ORACLE_JRE_TAR_PATH" ]]; then
  yq -i '.cg.cdnConfig.cdnJreTarPaths.oracle8u191=env(CDN_ORACLE_JRE_TAR_PATH)' $CONFIG_FILE
fi

if [[ "" != "$CDN_OPENJDK_JRE_TAR_PATH" ]]; then
  yq -i '.cg.cdnConfig.cdnJreTarPaths.openjdk8u242=env(CDN_OPENJDK_JRE_TAR_PATH)' $CONFIG_FILE
fi

if [[ "" != "$COMMAND_LIBRARY_SERVICE_BASE_URL" ]]; then
  yq -i '.cg.commandLibraryServiceConfig.baseUrl=env(COMMAND_LIBRARY_SERVICE_BASE_URL)' $CONFIG_FILE
fi

if [[ "" != "$BUGSNAG_API_KEY" ]]; then
  yq -i '.cg.bugsnagApiKey=env(BUGSNAG_API_KEY)' $CONFIG_FILE
fi

if [[ "" != "$ACCOUNT_LICENSE_CHECK_JOB_FREQUENCY" ]]; then
  yq -i '.cg.jobsFrequencyConfig.accountLicenseCheckJobFrequencyInMinutes=env(ACCOUNT_LICENSE_CHECK_JOB_FREQUENCY)' $CONFIG_FILE
fi

if [[ "" != "$ACCOUNT_DELETION_JOB_FREQUENCY" ]]; then
  yq -i '.cg.jobsFrequencyConfig.accountDeletionJobFrequencyInMinutes=env(ACCOUNT_DELETION_JOB_FREQUENCY)' $CONFIG_FILE
fi

if [[ "" != "$MANAGER_TO_COMMAND_LIBRARY_SERVICE_SECRET" ]]; then
  yq -i '.cg.commandLibraryServiceConfig.managerToCommandLibraryServiceSecret=env(MANAGER_TO_COMMAND_LIBRARY_SERVICE_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$DELEGATE_SERVICE_TARGET" ]]; then
  yq -i '.cg.grpcDelegateServiceClientConfig.target=env(DELEGATE_SERVICE_TARGET)' $CONFIG_FILE
fi

if [[ "" != "$DELEGATE_SERVICE_AUTHORITY" ]]; then
  yq -i '.cg.grpcDelegateServiceClientConfig.authority=env(DELEGATE_SERVICE_AUTHORITY)' $CONFIG_FILE
fi

if [[ "" != "$DELEGATE_SERVICE_MANAGEMENT_AUTHORITY" ]]; then
  yq -i '.cg.grpcDMSClientConfig.authority=env(DELEGATE_SERVICE_MANAGEMENT_AUTHORITY)' $CONFIG_FILE
fi

if [[ "" != "$DELEGATE_SERVICE_MANAGEMENT_TARGET" ]]; then
  yq -i '.cg.grpcDMSClientConfig.target=env(DELEGATE_SERVICE_MANAGEMENT_TARGET)' $CONFIG_FILE
fi

if [[ "" != "$DELEGATE_SERVICE_MANAGEMENT_SECRET" ]]; then
  yq -i '.cg.dmsSecret=env(DELEGATE_SERVICE_MANAGEMENT_SECRET)' $CONFIG_FILE
fi


if [[ "" != "$DELEGATE_GRPC_TARGET" ]]; then
  yq -i '.cg.grpcOnpremDelegateClientConfig.target=env(DELEGATE_GRPC_TARGET)' $CONFIG_FILE
fi

if [[ "" != "$DELEGATE_GRPC_AUTHORITY" ]]; then
  yq -i '.cg.grpcOnpremDelegateClientConfig.authority=env(DELEGATE_GRPC_AUTHORITY)' $CONFIG_FILE
fi

if [[ "" != "$NG_MANAGER_AUTHORITY" ]]; then
  yq -i '.cg.grpcClientConfig.authority=env(NG_MANAGER_AUTHORITY)' $CONFIG_FILE
fi

if [[ "" != "$NG_MANAGER_TARGET" ]]; then
  yq -i '.cg.grpcClientConfig.target=env(NG_MANAGER_TARGET)' $CONFIG_FILE
fi

if [[ "" != "$REMINDERS_BEFORE_ACCOUNT_DELETION" ]]; then
  yq -i '.cg.numberOfRemindersBeforeAccountDeletion=env(REMINDERS_BEFORE_ACCOUNT_DELETION)' $CONFIG_FILE
fi

if [[ "" != "$EXPORT_DATA_BATCH_SIZE" ]]; then
  yq -i '.cg.exportAccountDataBatchSize=env(EXPORT_DATA_BATCH_SIZE)' $CONFIG_FILE
fi

if [[ "" != "$COMMAND_LIBRARY_PUBLISHING_ALLOWED" ]]; then
  yq -i '.cg.commandLibraryServiceConfig.publishingAllowed=env(COMMAND_LIBRARY_PUBLISHING_ALLOWED)' $CONFIG_FILE
fi

if [[ "" != "$COMMAND_LIBRARY_PUBLISHING_SECRET" ]]; then
  yq -i '.cg.commandLibraryServiceConfig.publishingSecret=env(COMMAND_LIBRARY_PUBLISHING_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$LOG_STREAMING_SERVICE_BASEURL" ]]; then
  yq -i '.cg.logStreamingServiceConfig.baseUrl=env(LOG_STREAMING_SERVICE_BASEURL)' $CONFIG_FILE
fi

if [[ "" != "$LOG_STREAMING_SERVICE_TOKEN" ]]; then
  yq -i '.cg.logStreamingServiceConfig.serviceToken=env(LOG_STREAMING_SERVICE_TOKEN)' $CONFIG_FILE
fi

if [[ "" != "$ACCESS_CONTROL_ENABLED" ]]; then
  yq -i '.cg.accessControlClient.enableAccessControl=env(ACCESS_CONTROL_ENABLED)' $CONFIG_FILE
fi

if [[ "" != "$ACCESS_CONTROL_BASE_URL" ]]; then
  yq -i '.cg.accessControlClient.accessControlServiceConfig.baseUrl=env(ACCESS_CONTROL_BASE_URL)' $CONFIG_FILE
fi

if [[ "" != "$ACCESS_CONTROL_SECRET" ]]; then
  yq -i '.cg.accessControlClient.accessControlServiceSecret=env(ACCESS_CONTROL_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$ENABLE_AUDIT" ]]; then
  yq -i '.cg.enableAudit=env(ENABLE_AUDIT)' $CONFIG_FILE
fi

if [[ "" != "$AUDIT_CLIENT_BASEURL" ]]; then
  yq -i '.cg.auditClientConfig.baseUrl=env(AUDIT_CLIENT_BASEURL)' $CONFIG_FILE
fi

if [[ "" != "$EVENTS_FRAMEWORK_REDIS_SENTINELS" ]]; then
  IFS=',' read -ra SENTINEL_URLS <<< "$EVENTS_FRAMEWORK_REDIS_SENTINELS"
  INDEX=0
  for REDIS_SENTINEL_URL in "${SENTINEL_URLS[@]}"; do
    yq -i '.cg.eventsFramework.redis.sentinelUrls.env(INDEX)=env(REDIS_SENTINEL_URL)' $CONFIG_FILE
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
  yq -i '.cg.portal.gatewayPathPrefix=env(GATEWAY_PATH_PREFIX)' $CONFIG_FILE
fi

if [[ "" != "$NG_MANAGER_BASE_URL" ]]; then
  yq -i '.cg.ngManagerServiceHttpClientConfig.baseUrl=env(NG_MANAGER_BASE_URL)' $CONFIG_FILE
fi

if [[ "" != "$ENABLE_USER_CHANGESTREAM" ]]; then
  yq -i '.cg.userChangeStreamEnabled=env(ENABLE_USER_CHANGESTREAM)' $CONFIG_FILE
fi

if [[ "" != "$DISABLE_DELEGATE_MGMT_IN_MANAGER" ]]; then
  yq -i '.cg.disableDelegateMgmtInManager=env(DISABLE_DELEGATE_MGMT_IN_MANAGER)' $CONFIG_FILE
fi

if [[ "" != "$GCP_SECRET_MANAGER_PROJECT" ]]; then
  yq -i '.cg.secretsConfiguration.gcpSecretManagerProject=env(GCP_SECRET_MANAGER_PROJECT)' $CONFIG_FILE
fi

if [[ "" != "$RESOLVE_SECRETS" ]]; then
  yq -i '.cg.secretsConfiguration.secretResolutionEnabled=env(RESOLVE_SECRETS)' $CONFIG_FILE
fi

if [[ "" != "$LDAP_GROUP_SYNC_INTERVAL" ]]; then
  yq -i '.cg.ldapSyncJobConfig.syncInterval=env(LDAP_GROUP_SYNC_INTERVAL)' $CONFIG_FILE
fi

if [[ "" != "$LDAP_GROUP_SYNC_POOL_SIZE" ]]; then
  yq -i '.cg.ldapSyncJobConfig.poolSize=env(LDAP_GROUP_SYNC_POOL_SIZE)' $CONFIG_FILE
fi

if [[ "" != "$LDAP_GROUP_SYNC_DEFAULT_CRON" ]]; then
  yq -i '.cg.ldapSyncJobConfig.defaultCronExpression=env(LDAP_GROUP_SYNC_DEFAULT_CRON)' $CONFIG_FILE
fi

if [[ "" != "$USE_GLOBAL_KMS_AS_BASE_ALGO" ]]; then
  yq -i '.cg.useGlobalKMSAsBaseAlgo=env(USE_GLOBAL_KMS_AS_BASE_ALGO)' $CONFIG_FILE
fi

if [[ "" != "$SEGMENT_ENABLED_NG" ]]; then
  yq -i '.cg.segmentConfiguration.enabled=env(SEGMENT_ENABLED_NG)' $CONFIG_FILE
fi

if [[ "" != "$SEGMENT_URL_NG" ]]; then
  yq -i '.cg.segmentConfiguration.url=env(SEGMENT_URL_NG)' $CONFIG_FILE
fi

if [[ "" != "$SEGMENT_APIKEY_NG" ]]; then
  yq -i '.cg.segmentConfiguration.apiKey=env(SEGMENT_APIKEY_NG)' $CONFIG_FILE
fi

if [[ "" != "$SEGMENT_VERIFY_CERT_NG" ]]; then
  yq -i '.cg.segmentConfiguration.certValidationRequired=env(SEGMENT_VERIFY_CERT_NG)' $CONFIG_FILE
fi

if [[ "" != "$SECOPS_EMAIL" ]]; then
 yq -i '.cg.totp.secOpsEmail=env(SECOPS_EMAIL)' config.yml
fi

if [[ "" != "$INCORRECT_ATTEMPTS_UNTIL_SECOPS_NOTIFIED" ]]; then
 yq -i '.cg.totp.incorrectAttemptsUntilSecOpsNotified=env(INCORRECT_ATTEMPTS_UNTIL_SECOPS_NOTIFIED)' config.yml
fi

if [[ "" != "$PIPELINE_SERVICE_CLIENT_BASEURL" ]]; then
  yq -i '.pipelineServiceClientConfig.baseUrl=env(PIPELINE_SERVICE_CLIENT_BASEURL)' $CONFIG_FILE
fi

if [[ "" != "$NG_MANAGER_CLIENT_BASEURL" ]]; then
  yq -i '.ngClientConfig.baseUrl=env(NG_MANAGER_CLIENT_BASEURL)' $CONFIG_FILE
fi

if [[ "" != "$TEMPLATE_SERVICE_ENDPOINT" ]]; then
  yq -i '.templateServiceClientConfig.baseUrl=env(TEMPLATE_SERVICE_ENDPOINT)' $CONFIG_FILE
fi
