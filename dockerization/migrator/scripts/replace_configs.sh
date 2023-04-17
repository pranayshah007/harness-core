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
    export CONFIG_VALUE; export CONFIG_KEY; export CONFIG_KEY=.$CONFIG_KEY; yq -i 'eval(strenv(CONFIG_KEY))=env(CONFIG_VALUE)' $CONFIG_FILE
  fi
}

write_mongo_hosts_and_ports() {
  IFS=',' read -ra HOST_AND_PORT <<< "$2"
  for INDEX in "${!HOST_AND_PORT[@]}"; do
    HOST=$(cut -d: -f 1 <<< "${HOST_AND_PORT[$INDEX]}")
    PORT=$(cut -d: -f 2 -s <<< "${HOST_AND_PORT[$INDEX]}")

    export HOST; export ARG1=$1; export INDEX; yq -i '.env(ARG1).[env(INDEX)].host=env(HOST)' $CONFIG_FILE
    if [[ "" != "$PORT" ]]; then
      export PORT; export ARG1=$1; export INDEX; yq -i '.env(ARG1).[env(INDEX)].port=env(PORT)' $CONFIG_FILE
    fi
  done
}

write_mongo_params() {
  IFS='&' read -ra PARAMS <<< "$2"
  for PARAM_PAIR in "${PARAMS[@]}"; do
    NAME=$(cut -d= -f 1 <<< "$PARAM_PAIR")
    VALUE=$(cut -d= -f 2 <<< "$PARAM_PAIR")
    export VALUE; export ARG1=$1; export NAME; yq -i '.env(ARG1).params.env(NAME)=env(VALUE)' $CONFIG_FILE
  done
}

yq -i 'del(.server.applicationConnectors.[] | select(.type == "h2"))' $CONFIG_FILE
yq -i 'del(.cg.grpcServerConfig.connectors.[] | select(.secure == true))' $CONFIG_FILE

yq -i '.server.adminConnectors=[]' $CONFIG_FILE

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

if [[ "" != "$SERVER_PORT" ]]; then
  export SERVER_PORT; yq -i '.server.applicationConnectors[0].port=env(SERVER_PORT)' $CONFIG_FILE
else
  yq -i '.server.applicationConnectors[0].port=9080' $CONFIG_FILE
fi

if [[ "" != "$GRPC_SERVER_PORT" ]]; then
  export GRPC_SERVER_PORT; yq -i '.cg.grpcServerConfig.connectors[0].port=env(GRPC_SERVER_PORT)' $CONFIG_FILE
fi

if [[ "" != "$SERVER_MAX_THREADS" ]]; then
  export SERVER_MAX_THREADS; yq -i '.server.maxThreads=env(SERVER_MAX_THREADS)' $CONFIG_FILE
fi

if [[ "" != "$UI_SERVER_URL" ]]; then
  export UI_SERVER_URL; yq -i '.cg.portal.url=env(UI_SERVER_URL)' $CONFIG_FILE
fi

if [[ "" != "$AUTHTOKENEXPIRYINMILLIS" ]]; then
  export AUTHTOKENEXPIRYINMILLIS; yq -i '.cg.portal.authTokenExpiryInMillis=env(AUTHTOKENEXPIRYINMILLIS)' $CONFIG_FILE
fi

if [[ "" != "$EXTERNAL_GRAPHQL_RATE_LIMIT" ]]; then
  export EXTERNAL_GRAPHQL_RATE_LIMIT; yq -i '.cg.portal.externalGraphQLRateLimitPerMinute=env(EXTERNAL_GRAPHQL_RATE_LIMIT)' $CONFIG_FILE
fi

if [[ "" != "$CUSTOM_DASH_GRAPHQL_RATE_LIMIT" ]]; then
  export CUSTOM_DASH_GRAPHQL_RATE_LIMIT; yq -i '.cg.portal.customDashGraphQLRateLimitPerMinute=env(CUSTOM_DASH_GRAPHQL_RATE_LIMIT)' $CONFIG_FILE
fi

if [[ "" != "$ALLOWED_ORIGINS" ]]; then
  export ALLOWED_ORIGINS; yq -i '.cg.portal.allowedOrigins=env(ALLOWED_ORIGINS)' $CONFIG_FILE
fi

if [[ "" != "$STORE_REQUEST_PAYLOAD" ]]; then
  export STORE_REQUEST_PAYLOAD; yq -i '.cg.auditConfig.storeRequestPayload=env(STORE_REQUEST_PAYLOAD)' $CONFIG_FILE
fi

if [[ "" != "$STORE_RESPONSE_PAYLOAD" ]]; then
  export STORE_RESPONSE_PAYLOAD; yq -i '.cg.auditConfig.storeResponsePayload=env(STORE_RESPONSE_PAYLOAD)' $CONFIG_FILE
fi

if [[ "" != "$MONGO_URI" ]]; then
  export MONGO_URI=${MONGO_URI//\\&/&}; yq -i '.cg.mongo.uri=env(MONGO_URI)' $CONFIG_FILE
fi

if [[ "" != "$MONGO_HOSTS_AND_PORTS" ]]; then
  yq -i 'del(.cg.mongo.uri)' $CONFIG_FILE
  export MONGO_USERNAME; yq -i '.cg.mongo.username=env(MONGO_USERNAME)' $CONFIG_FILE
  export MONGO_PASSWORD; yq -i '.cg.mongo.password=env(MONGO_PASSWORD)' $CONFIG_FILE
  export MONGO_DATABASE; yq -i '.cg.mongo.database=env(MONGO_DATABASE)' $CONFIG_FILE
  export MONGO_SCHEMA; yq -i '.cg.mongo.schema=env(MONGO_SCHEMA)' $CONFIG_FILE
  write_mongo_hosts_and_ports cg.mongo "$MONGO_HOSTS_AND_PORTS"
  write_mongo_params cg.mongo "$MONGO_PARAMS"
fi

if [[ "" != "$MONGO_TRACE_MODE" ]]; then
  export MONGO_TRACE_MODE; yq -i '.cg.mongo.traceMode=env(MONGO_TRACE_MODE)' $CONFIG_FILE
fi

if [[ "" != "$MONGO_SSL_CONFIG" ]]; then
  export MONGO_SSL_CONFIG; yq -i '.cg.mongo.mongoSSLConfig.mongoSSLEnabled=env(MONGO_SSL_CONFIG)' $CONFIG_FILE
fi

if [[ "" != "$MONGO_SSL_CA_TRUST_STORE_PATH" ]]; then
  export MONGO_SSL_CA_TRUST_STORE_PATH; yq -i '.cg.mongo.mongoSSLConfig.mongoTrustStorePath=env(MONGO_SSL_CA_TRUST_STORE_PATH)' $CONFIG_FILE
fi

if [[ "" != "$MONGO_SSL_CA_TRUST_STORE_PASSWORD" ]]; then
  export MONGO_SSL_CA_TRUST_STORE_PASSWORD; yq -i '.cg.mongo.mongoSSLConfig.mongoTrustStorePassword=env(MONGO_SSL_CA_TRUST_STORE_PASSWORD)' $CONFIG_FILE
fi

if [[ "" != "$MONGO_CONNECT_TIMEOUT" ]]; then
  export MONGO_CONNECT_TIMEOUT; yq -i '.cg.mongo.connectTimeout=env(MONGO_CONNECT_TIMEOUT)' $CONFIG_FILE
fi

if [[ "" != "$MONGO_SERVER_SELECTION_TIMEOUT" ]]; then
  export MONGO_SERVER_SELECTION_TIMEOUT; yq -i '.cg.mongo.serverSelectionTimeout=env(MONGO_SERVER_SELECTION_TIMEOUT)' $CONFIG_FILE
fi

if [[ "" != "$MONGO_SOCKET_TIMEOUT" ]]; then
  export MONGO_SOCKET_TIMEOUT; yq -i '.cg.mongo.socketTimeout=env(MONGO_SOCKET_TIMEOUT)' $CONFIG_FILE
fi

if [[ "" != "$MAX_CONNECTION_IDLE_TIME" ]]; then
  export MAX_CONNECTION_IDLE_TIME; yq -i '.cg.mongo.maxConnectionIdleTime=env(MAX_CONNECTION_IDLE_TIME)' $CONFIG_FILE
fi

if [[ "" != "$MONGO_CONNECTIONS_PER_HOST" ]]; then
  export MONGO_CONNECTIONS_PER_HOST; yq -i '.cg.mongo.connectionsPerHost=env(MONGO_CONNECTIONS_PER_HOST)' $CONFIG_FILE
fi

if [[ "" != "$MONGO_INDEX_MANAGER_MODE" ]]; then
  export MONGO_INDEX_MANAGER_MODE; yq -i '.cg.mongo.indexManagerMode=env(MONGO_INDEX_MANAGER_MODE)' $CONFIG_FILE
fi

if [[ "" != "$EVEMTS_MONGO_INDEX_MANAGER_MODE" ]]; then
  export EVEMTS_MONGO_INDEX_MANAGER_MODE; yq -i '.cg.events-mongo.indexManagerMode=env(EVEMTS_MONGO_INDEX_MANAGER_MODE)' $CONFIG_FILE
fi

if [[ "" != "$EVENTS_MONGO_URI" ]]; then
  export EVENTS_MONGO_URI; yq -i '.cg.events-mongo.uri=env(EVENTS_MONGO_URI)' $CONFIG_FILE
else
  if [[ "" != "$EVENTS_MONGO_HOSTS_AND_PORTS" ]]; then
    yq -i 'del(.cg.events-mongo.uri)' $CONFIG_FILE
    export EVENTS_MONGO_USERNAME; yq -i '.cg.events-mongo.username=env(EVENTS_MONGO_USERNAME)' $CONFIG_FILE
    export EVENTS_MONGO_PASSWORD; yq -i '.cg.events-mongo.password=env(EVENTS_MONGO_PASSWORD)' $CONFIG_FILE
    export EVENTS_MONGO_DATABASE; yq -i '.cg.events-mongo.database=env(EVENTS_MONGO_DATABASE)' $CONFIG_FILE
    export EVENTS_MONGO_SCHEMA; yq -i '.cg.events-mongo.schema=env(EVENTS_MONGO_SCHEMA)' $CONFIG_FILE
    write_mongo_hosts_and_ports cg.events-mongo "$EVENTS_MONGO_HOSTS_AND_PORTS"
    write_mongo_params cg.events-mongo "$EVENTS_MONGO_PARAMS"
  else
    yq -i 'del(.cg.events-mongo)' $CONFIG_FILE
  fi
fi

if [[ "" != "$CF_CLIENT_API_KEY" ]]; then
  export CF_CLIENT_API_KEY; yq -i '.cg.cfClientConfig.apiKey=env(CF_CLIENT_API_KEY)' $CONFIG_FILE
fi

if [[ "" != "$CF_CLIENT_CONFIG_URL" ]]; then
  export CF_CLIENT_CONFIG_URL; yq -i '.cg.cfClientConfig.configUrl=env(CF_CLIENT_CONFIG_URL)' $CONFIG_FILE
fi

if [[ "" != "$CF_CLIENT_EVENT_URL" ]]; then
  export CF_CLIENT_EVENT_URL; yq -i '.cg.cfClientConfig.eventUrl=env(CF_CLIENT_EVENT_URL)' $CONFIG_FILE
fi

if [[ "" != "$CF_CLIENT_ANALYTICS_ENABLED" ]]; then
  export CF_CLIENT_ANALYTICS_ENABLED; yq -i '.cg.cfClientConfig.analyticsEnabled=env(CF_CLIENT_ANALYTICS_ENABLED)' $CONFIG_FILE
fi

if [[ "" != "$CF_CLIENT_CONNECTION_TIMEOUT" ]]; then
  export CF_CLIENT_CONNECTION_TIMEOUT; yq -i '.cg.cfClientConfig.connectionTimeout=env(CF_CLIENT_CONNECTION_TIMEOUT)' $CONFIG_FILE
fi

if [[ "" != "$CF_CLIENT_READ_TIMEOUT" ]]; then
  export CF_CLIENT_READ_TIMEOUT; yq -i '.cg.cfClientConfig.readTimeout=env(CF_CLIENT_READ_TIMEOUT)' $CONFIG_FILE
fi

if [[ "" != "$CF_MIGRATION_ENABLED" ]]; then
  export CF_MIGRATION_ENABLED; yq -i '.cg.cfMigrationConfig.enabled=env(CF_MIGRATION_ENABLED)' $CONFIG_FILE
fi

if [[ "" != "$CF_MIGRATION_ADMIN_URL" ]]; then
  export CF_MIGRATION_ADMIN_URL; yq -i '.cg.cfMigrationConfig.adminUrl=env(CF_MIGRATION_ADMIN_URL)' $CONFIG_FILE
fi

if [[ "" != "$CF_MIGRATION_API_KEY" ]]; then
  export CF_MIGRATION_API_KEY; yq -i '.cg.cfMigrationConfig.apiKey=env(CF_MIGRATION_API_KEY)' $CONFIG_FILE
fi

if [[ "" != "$CF_MIGRATION_ACCOUNT" ]]; then
  export CF_MIGRATION_ACCOUNT; yq -i '.cg.cfMigrationConfig.account=env(CF_MIGRATION_ACCOUNT)' $CONFIG_FILE
fi

if [[ "" != "$CF_MIGRATION_ORG" ]]; then
  export CF_MIGRATION_ORG; yq -i '.cg.cfMigrationConfig.org=env(CF_MIGRATION_ORG)' $CONFIG_FILE
fi

if [[ "" != "$CF_MIGRATION_PROJECT" ]]; then
  export CF_MIGRATION_PROJECT; yq -i '.cg.cfMigrationConfig.project=env(CF_MIGRATION_PROJECT)' $CONFIG_FILE
fi

if [[ "" != "$CF_MIGRATION_ENVIRONMENT" ]]; then
  export CF_MIGRATION_ENVIRONMENT; yq -i '.cg.cfMigrationConfig.environment=env(CF_MIGRATION_ENVIRONMENT)' $CONFIG_FILE
fi

replace_key_value cg.featureFlagConfig.featureFlagSystem "$FEATURE_FLAG_SYSTEM"
replace_key_value cg.featureFlagConfig.syncFeaturesToCF "$SYNC_FEATURES_TO_CF"


if [[ "" != "$ELASTICSEARCH_URI" ]]; then
  export ELASTICSEARCH_URI; yq -i '.cg.elasticsearch.uri=env(ELASTICSEARCH_URI)' $CONFIG_FILE
fi

if [[ "" != "$ELASTICSEARCH_INDEX_SUFFIX" ]]; then
  export ELASTICSEARCH_INDEX_SUFFIX; yq -i '.cg.elasticsearch.indexSuffix=env(ELASTICSEARCH_INDEX_SUFFIX)' $CONFIG_FILE
fi

if [[ "" != "$ELASTICSEARCH_MONGO_TAG_NAME" ]]; then
 export ELASTICSEARCH_MONGO_TAG_NAME; yq -i '.cg.elasticsearch.mongoTagKey=env(ELASTICSEARCH_MONGO_TAG_NAME)' $CONFIG_FILE
fi

if [[ "" != "$ELASTICSEARCH_MONGO_TAG_VALUE" ]]; then
 export ELASTICSEARCH_MONGO_TAG_VALUE; yq -i '.cg.elasticsearch.mongoTagValue=env(ELASTICSEARCH_MONGO_TAG_VALUE)' $CONFIG_FILE
fi

if [[ "" != "$MONGO_LOCK_URI" ]]; then
  export MONGO_LOCK_URI=${MONGO_LOCK_URI//\\&/&}; yq -i '.cg.mongo.locksUri=env(MONGO_LOCK_URI)' $CONFIG_FILE
fi

yq -i '.server.requestLog.appenders[0].threshold="TRACE"' $CONFIG_FILE

if [[ "$STACK_DRIVER_LOGGING_ENABLED" == "true" ]]; then
  yq -i 'del(.logging.appenders.[] | select(.type == "file"))' $CONFIG_FILE
  yq -i 'del(.logging.appenders.[] | select(.type == "console"))' $CONFIG_FILE
  yq -i '(.logging.appenders.[] | select(.type == "gke-console") | .stackdriverLogEnabled) = true' $CONFIG_FILE
else
  if [[ "$ROLLING_FILE_LOGGING_ENABLED" == "true" ]]; then
    yq -i 'del(.logging.appenders.[] | select(.type == "gke-console"))' $CONFIG_FILE
    yq -i '(.logging.appenders.[] | select(.type == "file") | .currentLogFilename) = "/opt/harness/logs/portal.log"' $CONFIG_FILE
    yq -i '(.logging.appenders.[] | select(.type == "file") | .archivedLogFilenamePattern) = "/opt/harness/logs/portal.%d.%i.log"' $CONFIG_FILE
  else
    yq -i 'del(.logging.appenders.[] | select(.type == "file"))' $CONFIG_FILE
    yq -i 'del(.logging.appenders.[] | select(.type == "gke-console"))' $CONFIG_FILE
  fi
fi

if [[ "" != "$WATCHER_METADATA_URL" ]]; then
  export WATCHER_METADATA_URL; yq -i '.cg.watcherMetadataUrl=env(WATCHER_METADATA_URL)' $CONFIG_FILE
fi

if [[ "" != "$DELEGATE_METADATA_URL" ]]; then
  export DELEGATE_METADATA_URL; yq -i '.cg.delegateMetadataUrl=env(DELEGATE_METADATA_URL)' $CONFIG_FILE
fi

if [[ "" != "$API_URL" ]]; then
  export API_URL; yq -i '.cg.apiUrl=env(API_URL)' $CONFIG_FILE
fi

if [[ "" != "$ENV_PATH" ]]; then
  export ENV_PATH; yq -i '.cg.envPath=env(ENV_PATH)' $CONFIG_FILE
fi

if [[ "" != "$DEPLOY_MODE" ]]; then
  export DEPLOY_MODE; yq -i '.cg.deployMode=env(DEPLOY_MODE)' $CONFIG_FILE
fi

if [[ "" != "$NEWRELIC_LICENSE_KEY" ]]; then
  export NEWRELIC_LICENSE_KEY; yq -i '.cg.common.license_key=env(NEWRELIC_LICENSE_KEY)' $NEWRELIC_FILE
fi

if [[ "$DISABLE_NEW_RELIC" == "true" ]]; then
  yq -i '.cg.common.agent_enabled=false' $NEWRELIC_FILE
fi

if [[ "" != "$jwtPasswordSecret" ]]; then
  export jwtPasswordSecret; yq -i '.cg.portal.jwtPasswordSecret=env(jwtPasswordSecret)' $CONFIG_FILE
fi

if [[ "" != "$jwtExternalServiceSecret" ]]; then
  export jwtExternalServiceSecret; yq -i '.cg.portal.jwtExternalServiceSecret=env(jwtExternalServiceSecret)' $CONFIG_FILE
fi

if [[ "" != "$jwtZendeskSecret" ]]; then
  export jwtZendeskSecret; yq -i '.cg.portal.jwtZendeskSecret=env(jwtZendeskSecret)' $CONFIG_FILE
fi

if [[ "" != "$jwtMultiAuthSecret" ]]; then
  export jwtMultiAuthSecret; yq -i '.cg.portal.jwtMultiAuthSecret=env(jwtMultiAuthSecret)' $CONFIG_FILE
fi

if [[ "" != "$jwtSsoRedirectSecret" ]]; then
  export jwtSsoRedirectSecret; yq -i '.cg.portal.jwtSsoRedirectSecret=env(jwtSsoRedirectSecret)' $CONFIG_FILE
fi

if [[ "" != "$jwtAuthSecret" ]]; then
  export jwtAuthSecret; yq -i '.cg.portal.jwtAuthSecret=env(jwtAuthSecret)' $CONFIG_FILE
fi

if [[ "" != "$jwtMarketPlaceSecret" ]]; then
  export jwtMarketPlaceSecret; yq -i '.cg.portal.jwtMarketPlaceSecret=env(jwtMarketPlaceSecret)' $CONFIG_FILE
fi

if [[ "" != "$jwtIdentityServiceSecret" ]]; then
  export jwtIdentityServiceSecret; yq -i '.cg.portal.jwtIdentityServiceSecret=env(jwtIdentityServiceSecret)' $CONFIG_FILE
fi

if [[ "" != "$jwtDataHandlerSecret" ]]; then
  export jwtDataHandlerSecret; yq -i '.cg.portal.jwtDataHandlerSecret=env(jwtDataHandlerSecret)' $CONFIG_FILE
fi

if [[ "" != "$jwtNextGenManagerSecret" ]]; then
  export jwtNextGenManagerSecret; yq -i '.cg.portal.jwtNextGenManagerSecret=env(jwtNextGenManagerSecret)' $CONFIG_FILE
fi

if [[ "" != "$FEATURES" ]]; then
  export FEATURES; yq -i '.cg.featuresEnabled=env(FEATURES)' $CONFIG_FILE
fi

if [[ "" != "$SAMPLE_TARGET_ENV" ]]; then
  export SAMPLE_TARGET_ENV; yq -i '.cg.sampleTargetEnv=env(SAMPLE_TARGET_ENV)' $CONFIG_FILE
fi

if [[ "" != "$SAMPLE_TARGET_STATUS_HOST" ]]; then
  export SAMPLE_TARGET_STATUS_HOST; yq -i '.cg.sampleTargetStatusHost=env(SAMPLE_TARGET_STATUS_HOST)' $CONFIG_FILE
fi

if [[ "" != "$GLOBAL_WHITELIST" ]]; then
  export GLOBAL_WHITELIST; yq -i '.cg.globalWhitelistConfig.filters=env(GLOBAL_WHITELIST)' $CONFIG_FILE
fi

if [[ "" != "$SMTP_HOST" ]]; then
  export SMTP_HOST; yq -i '.cg.smtp.host=env(SMTP_HOST)' $CONFIG_FILE
fi

if [[ "" != "$SMTP_USERNAME" ]]; then
  export SMTP_USERNAME; yq -i '.cg.smtp.username=env(SMTP_USERNAME)' $CONFIG_FILE
fi

if [[ "" != "$SMTP_PASSWORD" ]]; then
  export SMTP_PASSWORD; yq -i '.cg.smtp.password=env(SMTP_PASSWORD)' $CONFIG_FILE
fi

if [[ "" != "$SMTP_USE_SSL" ]]; then
  export SMTP_USE_SSL; yq -i '.cg.smtp.useSSL=env(SMTP_USE_SSL)' $CONFIG_FILE
fi

if [[ "" != "$MARKETO_ENABLED" ]]; then
  export MARKETO_ENABLED; yq -i '.cg.marketoConfig.enabled=env(MARKETO_ENABLED)' $CONFIG_FILE
fi

if [[ "" != "$MARKETO_URL" ]]; then
  export MARKETO_URL; yq -i '.cg.marketoConfig.url=env(MARKETO_URL)' $CONFIG_FILE
fi

if [[ "" != "$MARKETO_CLIENT_ID" ]]; then
  export MARKETO_CLIENT_ID; yq -i '.cg.marketoConfig.clientId=env(MARKETO_CLIENT_ID)' $CONFIG_FILE
fi

if [[ "" != "$MARKETO_CLIENT_SECRET" ]]; then
  export MARKETO_CLIENT_SECRET; yq -i '.cg.marketoConfig.clientSecret=env(MARKETO_CLIENT_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$SEGMENT_ENABLED" ]]; then
  export SEGMENT_ENABLED; yq -i '.cg.segmentConfig.enabled=env(SEGMENT_ENABLED)' $CONFIG_FILE
fi

if [[ "" != "$SEGMENT_URL" ]]; then
  export SEGMENT_URL; yq -i '.cg.segmentConfig.url=env(SEGMENT_URL)' $CONFIG_FILE
fi

if [[ "" != "$SEGMENT_APIKEY" ]]; then
  export SEGMENT_APIKEY; yq -i '.cg.segmentConfig.apiKey=env(SEGMENT_APIKEY)' $CONFIG_FILE
fi

if [[ "" != "$SALESFORCE_USERNAME" ]]; then
  export SALESFORCE_USERNAME; yq -i '.cg.salesforceConfig.userName=env(SALESFORCE_USERNAME)' $CONFIG_FILE
fi

if [[ "" != "$SALESFORCE_PASSWORD" ]]; then
  export SALESFORCE_PASSWORD; yq -i '.cg.salesforceConfig.password=env(SALESFORCE_PASSWORD)' $CONFIG_FILE
fi

if [[ "" != "$SALESFORCE_CONSUMER_KEY" ]]; then
  export SALESFORCE_CONSUMER_KEY; yq -i '.cg.salesforceConfig.consumerKey=env(SALESFORCE_CONSUMER_KEY)' $CONFIG_FILE
fi

if [[ "" != "$SALESFORCE_CONSUMER_SECRET" ]]; then
  export SALESFORCE_CONSUMER_SECRET; yq -i '.cg.salesforceConfig.consumerSecret=env(SALESFORCE_CONSUMER_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$SALESFORCE_GRANT_TYPE" ]]; then
  export SALESFORCE_GRANT_TYPE; yq -i '.cg.salesforceConfig.grantType=env(SALESFORCE_GRANT_TYPE)' $CONFIG_FILE
fi

if [[ "" != "$SALESFORCE_LOGIN_INSTANCE_DOMAIN" ]]; then
  export SALESFORCE_LOGIN_INSTANCE_DOMAIN; yq -i '.cg.salesforceConfig.loginInstanceDomain=env(SALESFORCE_LOGIN_INSTANCE_DOMAIN)' $CONFIG_FILE
fi

if [[ "" != "$SALESFORCE_API_VERSION" ]]; then
  export SALESFORCE_API_VERSION; yq -i '.cg.salesforceConfig.apiVersion=env(SALESFORCE_API_VERSION)' $CONFIG_FILE
fi

if [[ "" != "$SALESFORCE_INTEGRATION_ENABLED" ]]; then
  export SALESFORCE_INTEGRATION_ENABLED; yq -i '.cg.salesforceConfig.enabled=env(SALESFORCE_INTEGRATION_ENABLED)' $CONFIG_FILE
fi

if [[ "" != "$CE_SETUP_CONFIG_AWS_ACCOUNT_ID" ]]; then
  export CE_SETUP_CONFIG_AWS_ACCOUNT_ID; yq -i '.cg.ceSetUpConfig.awsAccountId=env(CE_SETUP_CONFIG_AWS_ACCOUNT_ID)' $CONFIG_FILE
fi

if [[ "" != "$CE_SETUP_CONFIG_AWS_S3_BUCKET_NAME" ]]; then
  export CE_SETUP_CONFIG_AWS_S3_BUCKET_NAME; yq -i '.cg.ceSetUpConfig.awsS3BucketName=env(CE_SETUP_CONFIG_AWS_S3_BUCKET_NAME)' $CONFIG_FILE
fi

if [[ "" != "$CE_SETUP_CONFIG_GCP_PROJECT_ID" ]]; then
  export CE_SETUP_CONFIG_GCP_PROJECT_ID; yq -i '.cg.ceSetUpConfig.gcpProjectId=env(CE_SETUP_CONFIG_GCP_PROJECT_ID)' $CONFIG_FILE
fi

if [[ "" != "$CE_SETUP_CONFIG_AWS_ROLE_NAME" ]]; then
  export CE_SETUP_CONFIG_AWS_ROLE_NAME; yq -i '.cg.ceSetUpConfig.awsRoleName=env(CE_SETUP_CONFIG_AWS_ROLE_NAME)' $CONFIG_FILE
fi

if [[ "" != "$CE_SETUP_CONFIG_SAMPLE_ACCOUNT_ID" ]]; then
  export CE_SETUP_CONFIG_SAMPLE_ACCOUNT_ID; yq -i '.cg.ceSetUpConfig.sampleAccountId=env(CE_SETUP_CONFIG_SAMPLE_ACCOUNT_ID)' $CONFIG_FILE
fi

if [[ "" != "$CE_SETUP_CONFIG_AWS_ACCESS_KEY" ]]; then
  export CE_SETUP_CONFIG_AWS_ACCESS_KEY; yq -i '.cg.ceSetUpConfig.awsAccessKey=env(CE_SETUP_CONFIG_AWS_ACCESS_KEY)' $CONFIG_FILE
fi

if [[ "" != "$CE_SETUP_CONFIG_AWS_SECRET_KEY" ]]; then
  export CE_SETUP_CONFIG_AWS_SECRET_KEY; yq -i '.cg.ceSetUpConfig.awsSecretKey=env(CE_SETUP_CONFIG_AWS_SECRET_KEY)' $CONFIG_FILE
fi

if [[ "" != "$CE_SETUP_CONFIG_MASTER_CLOUD_FORMATION" ]]; then
  export CE_SETUP_CONFIG_MASTER_CLOUD_FORMATION; yq -i '.cg.ceSetUpConfig.masterAccountCloudFormationTemplateLink=env(CE_SETUP_CONFIG_MASTER_CLOUD_FORMATION)' $CONFIG_FILE
fi

if [[ "" != "$CE_SETUP_CONFIG_LINKED_CLOUD_FORMATION" ]]; then
  export CE_SETUP_CONFIG_LINKED_CLOUD_FORMATION; yq -i '.cg.ceSetUpConfig.linkedAccountCloudFormationTemplateLink=env(CE_SETUP_CONFIG_LINKED_CLOUD_FORMATION)' $CONFIG_FILE
fi

if [[ "" != "$CE_SETUP_CONFIG_AZURE_CLIENTSECRET" ]]; then
  export CE_SETUP_CONFIG_AZURE_CLIENTSECRET; yq -i '.cg.ceSetUpConfig.azureAppClientSecret=env(CE_SETUP_CONFIG_AZURE_CLIENTSECRET)' $CONFIG_FILE
fi

if [[ "" != "$CE_SETUP_CONFIG_AZURE_CLIENTID" ]]; then
  export CE_SETUP_CONFIG_AZURE_CLIENTID; yq -i '.cg.ceSetUpConfig.azureAppClientId=env(CE_SETUP_CONFIG_AZURE_CLIENTID)' $CONFIG_FILE
fi

if [[ "" != "$DATADOG_ENABLED" ]]; then
  export DATADOG_ENABLED; yq -i '.cg.datadogConfig.enabled=env(DATADOG_ENABLED)' $CONFIG_FILE
fi

if [[ "" != "$DATADOG_APIKEY" ]]; then
  export DATADOG_APIKEY; yq -i '.cg.datadogConfig.apiKey=env(DATADOG_APIKEY)' $CONFIG_FILE
fi

if [[ "" != "$DELEGATE_DOCKER_IMAGE" ]]; then
  export DELEGATE_DOCKER_IMAGE; yq -i '.cg.portal.delegateDockerImage=env(DELEGATE_DOCKER_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$OPTIONAL_DELEGATE_TASK_REJECT_AT_LIMIT" ]]; then
  export OPTIONAL_DELEGATE_TASK_REJECT_AT_LIMIT; yq -i '.portal.optionalDelegateTaskRejectAtLimit=env(OPTIONAL_DELEGATE_TASK_REJECT_AT_LIMIT)' $CONFIG_FILE
fi

if [[ "" != "$EXECUTION_LOG_DATA_STORE" ]]; then
  export EXECUTION_LOG_DATA_STORE; yq -i '.cg.executionLogStorageMode=env(EXECUTION_LOG_DATA_STORE)' $CONFIG_FILE
fi

if [[ "" != "$FILE_STORAGE" ]]; then
  export FILE_STORAGE; yq -i '.cg.fileStorageMode=env(FILE_STORAGE)' $CONFIG_FILE
fi

if [[ "" != "$CLUSTER_NAME" ]]; then
  export CLUSTER_NAME; yq -i '.cg.clusterName=env(CLUSTER_NAME)' $CONFIG_FILE
fi

if [[ "" != "$DEPLOYMENT_CLUSTER_NAME" ]]; then
  export DEPLOYMENT_CLUSTER_NAME; yq -i '.cg.deploymentClusterName=env(DEPLOYMENT_CLUSTER_NAME)' $CONFIG_FILE
fi

if [[ "" != "$BACKGROUND_SCHEDULER_CLUSTERED" ]]; then
  export BACKGROUND_SCHEDULER_CLUSTERED; yq -i '.cg.backgroundScheduler.clustered=env(BACKGROUND_SCHEDULER_CLUSTERED)' $CONFIG_FILE
fi

if [[ "" != "$ALLOW_TRIAL_REGISTRATION" ]]; then
  export ALLOW_TRIAL_REGISTRATION; yq -i '.cg.trialRegistrationAllowed=env(ALLOW_TRIAL_REGISTRATION)' $CONFIG_FILE
fi

if [[ "" != "$GITHUB_OAUTH_CLIENT" ]]; then
  export GITHUB_OAUTH_CLIENT; yq -i '.cg.githubConfig.clientId=env(GITHUB_OAUTH_CLIENT)' $CONFIG_FILE
fi

if [[ "" != "$GITHUB_OAUTH_SECRET" ]]; then
  export GITHUB_OAUTH_SECRET; yq -i '.cg.githubConfig.clientSecret=env(GITHUB_OAUTH_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$GITHUB_OAUTH_CALLBACK_URL" ]]; then
  export GITHUB_OAUTH_CALLBACK_URL; yq -i '.cg.githubConfig.callbackUrl=env(GITHUB_OAUTH_CALLBACK_URL)' $CONFIG_FILE
fi

if [[ "" != "$AZURE_OAUTH_CLIENT" ]]; then
  export AZURE_OAUTH_CLIENT; yq -i '.cg.azureConfig.clientId=env(AZURE_OAUTH_CLIENT)' $CONFIG_FILE
fi

if [[ "" != "$AZURE_OAUTH_SECRET" ]]; then
  export AZURE_OAUTH_SECRET; yq -i '.cg.azureConfig.clientSecret=env(AZURE_OAUTH_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$AZURE_OAUTH_CALLBACK_URL" ]]; then
  export AZURE_OAUTH_CALLBACK_URL; yq -i '.cg.azureConfig.callbackUrl=env(AZURE_OAUTH_CALLBACK_URL)' $CONFIG_FILE
fi

if [[ "" != "$GOOGLE_OAUTH_CLIENT" ]]; then
  export GOOGLE_OAUTH_CLIENT; yq -i '.cg.googleConfig.clientId=env(GOOGLE_OAUTH_CLIENT)' $CONFIG_FILE
fi

if [[ "" != "$GOOGLE_OAUTH_SECRET" ]]; then
  export GOOGLE_OAUTH_SECRET; yq -i '.cg.googleConfig.clientSecret=env(GOOGLE_OAUTH_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$GOOGLE_OAUTH_CALLBACK_URL" ]]; then
  export GOOGLE_OAUTH_CALLBACK_URL; yq -i '.cg.googleConfig.callbackUrl=env(GOOGLE_OAUTH_CALLBACK_URL)' $CONFIG_FILE
fi

if [[ "" != "$BITBUCKET_OAUTH_CLIENT" ]]; then
  export BITBUCKET_OAUTH_CLIENT; yq -i '.cg.bitbucketConfig.clientId=env(BITBUCKET_OAUTH_CLIENT)' $CONFIG_FILE
fi

if [[ "" != "$BITBUCKET_OAUTH_SECRET" ]]; then
  export BITBUCKET_OAUTH_SECRET; yq -i '.cg.bitbucketConfig.clientSecret=env(BITBUCKET_OAUTH_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$BITBUCKET_OAUTH_CALLBACK_URL" ]]; then
  export BITBUCKET_OAUTH_CALLBACK_URL; yq -i '.cg.bitbucketConfig.callbackUrl=env(BITBUCKET_OAUTH_CALLBACK_URL)' $CONFIG_FILE
fi

if [[ "" != "$GITLAB_OAUTH_CLIENT" ]]; then
  export GITLAB_OAUTH_CLIENT; yq -i '.cg.gitlabConfig.clientId=env(GITLAB_OAUTH_CLIENT)' $CONFIG_FILE
fi

if [[ "" != "$GITLAB_OAUTH_SECRET" ]]; then
  export GITLAB_OAUTH_SECRET; yq -i '.cg.gitlabConfig.clientSecret=env(GITLAB_OAUTH_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$GITLAB_OAUTH_CALLBACK_URL" ]]; then
  export GITLAB_OAUTH_CALLBACK_URL; yq -i '.cg.gitlabConfig.callbackUrl=env(GITLAB_OAUTH_CALLBACK_URL)' $CONFIG_FILE
fi

if [[ "" != "$LINKEDIN_OAUTH_CLIENT" ]]; then
  export LINKEDIN_OAUTH_CLIENT; yq -i '.cg.linkedinConfig.clientId=env(LINKEDIN_OAUTH_CLIENT)' $CONFIG_FILE
fi

if [[ "" != "$LINKEDIN_OAUTH_SECRET" ]]; then
  export LINKEDIN_OAUTH_SECRET; yq -i '.cg.linkedinConfig.clientSecret=env(LINKEDIN_OAUTH_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$LINKEDIN_OAUTH_CALLBACK_URL" ]]; then
  export LINKEDIN_OAUTH_CALLBACK_URL; yq -i '.cg.linkedinConfig.callbackUrl=env(LINKEDIN_OAUTH_CALLBACK_URL)' $CONFIG_FILE
fi

if [[ "" != "$AWS_MARKETPLACE_ACCESSKEY" ]]; then
  export AWS_MARKETPLACE_ACCESSKEY; yq -i '.cg.mktPlaceConfig.awsAccessKey=env(AWS_MARKETPLACE_ACCESSKEY)' $CONFIG_FILE
fi

if [[ "" != "$AWS_MARKETPLACE_SECRETKEY" ]]; then
  export AWS_MARKETPLACE_SECRETKEY; yq -i '.cg.mktPlaceConfig.awsSecretKey=env(AWS_MARKETPLACE_SECRETKEY)' $CONFIG_FILE
fi

if [[ "" != "$AWS_MARKETPLACE_PRODUCTCODE" ]]; then
  export AWS_MARKETPLACE_PRODUCTCODE; yq -i '.cg.mktPlaceConfig.awsMarketPlaceProductCode=env(AWS_MARKETPLACE_PRODUCTCODE)' $CONFIG_FILE
fi

if [[ "" != "$AWS_MARKETPLACE_CE_PRODUCTCODE" ]]; then
  export AWS_MARKETPLACE_CE_PRODUCTCODE; yq -i '.cg.mktPlaceConfig.awsMarketPlaceCeProductCode=env(AWS_MARKETPLACE_CE_PRODUCTCODE)' $CONFIG_FILE
fi

if [[ "" != "$AWS_MARKETPLACE_FF_PRODUCTCODE" ]]; then
  export AWS_MARKETPLACE_FF_PRODUCTCODE; yq -i '.cg.mktPlaceConfig.awsMarketPlaceFfProductCode=env(AWS_MARKETPLACE_FF_PRODUCTCODE)' $CONFIG_FILE
fi

if [[ "" != "$AWS_MARKETPLACE_CI_PRODUCTCODE" ]]; then
  export AWS_MARKETPLACE_CI_PRODUCTCODE; yq -i '.cg.mktPlaceConfig.awsMarketPlaceCiProductCode=env(AWS_MARKETPLACE_CI_PRODUCTCODE)' $CONFIG_FILE
fi

if [[ "" != "$AWS_MARKETPLACE_SRM_PRODUCTCODE" ]]; then
  export AWS_MARKETPLACE_SRM_PRODUCTCODE; yq -i '.cg.mktPlaceConfig.awsMarketPlaceSrmProductCode=env(AWS_MARKETPLACE_SRM_PRODUCTCODE)' $CONFIG_FILE
fi

if [[ "" != "$AWS_MARKETPLACE_STO_PRODUCTCODE" ]]; then
  export AWS_MARKETPLACE_STO_PRODUCTCODE; yq -i '.cg.mktPlaceConfig.awsMarketPlaceStoProductCode=env(AWS_MARKETPLACE_STO_PRODUCTCODE)' $CONFIG_FILE
fi

if [[ "" != "$AWS_MARKETPLACE_CD_PRODUCTCODE" ]]; then
  export AWS_MARKETPLACE_CD_PRODUCTCODE; yq -i '.cg.mktPlaceConfig.awsMarketPlaceCdProductCode=env(AWS_MARKETPLACE_CD_PRODUCTCODE)' $CONFIG_FILE
fi

if [[ "" != "$AWS_MARKETPLACE_CCM_PRODUCTCODE" ]]; then
  export AWS_MARKETPLACE_CCM_PRODUCTCODE; yq -i '.cg.mktPlaceConfig.awsMarketPlaceCcmProductCode=env(AWS_MARKETPLACE_CCM_PRODUCTCODE)' $CONFIG_FILE
fi

if [[ "" != "$ALLOW_BLACKLISTED_EMAIL_DOMAINS" ]]; then
  export ALLOW_BLACKLISTED_EMAIL_DOMAINS; yq -i '.cg.blacklistedEmailDomainsAllowed=env(ALLOW_BLACKLISTED_EMAIL_DOMAINS)' $CONFIG_FILE
fi

if [[ "" != "$ALLOW_PWNED_PASSWORDS" ]]; then
  export ALLOW_PWNED_PASSWORDS; yq -i '.cg.pwnedPasswordsAllowed=env(ALLOW_PWNED_PASSWORDS)' $CONFIG_FILE
fi

if [[ "" != "$TIMESCALEDB_URI" ]]; then
  export TIMESCALEDB_URI; yq -i '.cg.timescaledb.timescaledbUrl=env(TIMESCALEDB_URI)' $CONFIG_FILE
fi

if [[ "" != "$TIMESCALEDB_USERNAME" ]]; then
  export TIMESCALEDB_USERNAME; yq -i '.cg.timescaledb.timescaledbUsername=env(TIMESCALEDB_USERNAME)' $CONFIG_FILE
fi

if [[ "" != "$TIMESCALEDB_PASSWORD" ]]; then
  export TIMESCALEDB_PASSWORD; yq -i '.cg.timescaledb.timescaledbPassword=env(TIMESCALEDB_PASSWORD)' $CONFIG_FILE
fi

if [[ "" != "$TIMESCALEDB_CONNECT_TIMEOUT" ]]; then
  export TIMESCALEDB_CONNECT_TIMEOUT; yq -i '.cg.timescaledb.connectTimeout=env(TIMESCALEDB_CONNECT_TIMEOUT)' $CONFIG_FILE
fi

if [[ "" != "$TIMESCALEDB_SOCKET_TIMEOUT" ]]; then
  export TIMESCALEDB_SOCKET_TIMEOUT; yq -i '.cg.timescaledb.socketTimeout=env(TIMESCALEDB_SOCKET_TIMEOUT)' $CONFIG_FILE
fi

if [[ "" != "$TIMESCALEDB_LOGUNCLOSED" ]]; then
  export TIMESCALEDB_LOGUNCLOSED; yq -i '.cg.timescaledb.logUnclosedConnections=env(TIMESCALEDB_LOGUNCLOSED)' $CONFIG_FILE
fi

if [[ "" != "$TIMESCALEDB_LOGGERLEVEL" ]]; then
  export TIMESCALEDB_LOGGERLEVEL; yq -i '.cg.timescaledb.loggerLevel=env(TIMESCALEDB_LOGGERLEVEL)' $CONFIG_FILE
fi

if [[ "$TIMESCALEDB_HEALTH_CHECK_NEEDED" == "true" ]]; then
  export TIMESCALEDB_HEALTH_CHECK_NEEDED; yq -i '.cg.timescaledb.isHealthCheckNeeded=env(TIMESCALEDB_HEALTH_CHECK_NEEDED)' $CONFIG_FILE
fi

if [[ "" != "$TIMESCALEDB_SSL_MODE" ]]; then
  export TIMESCALEDB_SSL_MODE; yq -i '.timescaledb.sslMode=env(TIMESCALEDB_SSL_MODE)' $CONFIG_FILE
fi

if [[ "" != "$TIMESCALEDB_SSL_ROOT_CERT" ]]; then
  export TIMESCALEDB_SSL_ROOT_CERT; yq -i '.timescaledb.sslRootCert=env(TIMESCALEDB_SSL_ROOT_CERT)' $CONFIG_FILE
fi

if [[ "$MONGO_DEBUGGING_ENABLED" == "true" ]]; then
  yq -i '.logging.loggers.["dev.morphia.query"]="TRACE"' $CONFIG_FILE
  yq -i '.logging.loggers.connection="TRACE"' $CONFIG_FILE
fi

if [[ "" != "$AZURE_MARKETPLACE_ACCESSKEY" ]]; then
  export AZURE_MARKETPLACE_ACCESSKEY; yq -i '.cg.mktPlaceConfig.azureMarketplaceAccessKey=env(AZURE_MARKETPLACE_ACCESSKEY)' $CONFIG_FILE
fi

if [[ "" != "$AZURE_MARKETPLACE_SECRETKEY" ]]; then
  export AZURE_MARKETPLACE_SECRETKEY; yq -i '.cg.mktPlaceConfig.azureMarketplaceSecretKey=env(AZURE_MARKETPLACE_SECRETKEY)' $CONFIG_FILE
fi

if [[ "" != "$WORKERS" ]]; then
  IFS=',' read -ra WORKER_ITEMS <<< "$WORKERS"
  for ITEM in "${WORKER_ITEMS[@]}"; do
    WORKER=`echo $ITEM | awk -F= '{print $1}'`
    WORKER_FLAG=`echo $ITEM | awk -F= '{print $2}'`
    export WORKER_FLAG; export WORKER; yq -i '.cg.workers.active.env(WORKER)=env(WORKER_FLAG)' $CONFIG_FILE
  done
fi

if [[ "" != "$PUBLISHERS" ]]; then
  IFS=',' read -ra PUBLISHER_ITEMS <<< "$PUBLISHERS"
  for ITEM in "${PUBLISHER_ITEMS[@]}"; do
    PUBLISHER=`echo $ITEM | awk -F= '{print $1}'`
    PUBLISHER_FLAG=`echo $ITEM | awk -F= '{print $2}'`
    export PUBLISHER_FLAG; export PUBLISHER; yq -i '.cg.publishers.active.env(PUBLISHER)=env(PUBLISHER_FLAG)' $CONFIG_FILE
  done
fi

if [[ "" != "$DISTRIBUTED_LOCK_IMPLEMENTATION" ]]; then
  export DISTRIBUTED_LOCK_IMPLEMENTATION; yq -i '.cg.distributedLockImplementation=env(DISTRIBUTED_LOCK_IMPLEMENTATION)' $CONFIG_FILE
fi

if [[ "" != "$ATMOSPHERE_BACKEND" ]]; then
  export ATMOSPHERE_BACKEND; yq -i '.cg.atmosphereBroadcaster=env(ATMOSPHERE_BACKEND)' $CONFIG_FILE
fi

yq -i 'del(.codec)' $REDISSON_CACHE_FILE

if [[ "" != "$REDIS_URL" ]]; then
  export REDIS_URL; yq -i '.cg.redisLockConfig.redisUrl=env(REDIS_URL)' $CONFIG_FILE
  export REDIS_URL; yq -i '.cg.redisAtmosphereConfig.redisUrl=env(REDIS_URL)' $CONFIG_FILE
  export REDIS_URL; yq -i '.singleServerConfig.address=env(REDIS_URL)' $REDISSON_CACHE_FILE
fi

if [[ "" != "$ATMOSPHERE_REDIS_URL" ]]; then
  export ATMOSPHERE_REDIS_URL; yq -i '.cg.redisAtmosphereConfig.redisUrl=env(ATMOSPHERE_REDIS_URL)' $CONFIG_FILE
fi

if [[ "$REDIS_SENTINEL" == "true" ]]; then
  yq -i '.cg.redisLockConfig.sentinel=true' $CONFIG_FILE
  yq -i '.cg.redisAtmosphereConfig.sentinel=true' $CONFIG_FILE
  yq -i 'del(.singleServerConfig)' $REDISSON_CACHE_FILE
fi

if [[ "" != "$REDIS_MASTER_NAME" ]]; then
  export REDIS_MASTER_NAME; yq -i '.cg.redisLockConfig.masterName=env(REDIS_MASTER_NAME)' $CONFIG_FILE
  export REDIS_MASTER_NAME; yq -i '.cg.redisAtmosphereConfig.masterName=env(REDIS_MASTER_NAME)' $CONFIG_FILE
  export REDIS_MASTER_NAME; yq -i '.sentinelServersConfig.masterName=env(REDIS_MASTER_NAME)' $REDISSON_CACHE_FILE
fi

if [[ "" != "$REDIS_SENTINELS" ]]; then
  IFS=',' read -ra REDIS_SENTINEL_URLS <<< "$REDIS_SENTINELS"
  INDEX=0
  for REDIS_SENTINEL_URL in "${REDIS_SENTINEL_URLS[@]}"; do
    export REDIS_SENTINEL_URL; export INDEX; yq -i '.cg.redisLockConfig.sentinelUrls.[env(INDEX)]=env(REDIS_SENTINEL_URL)' $CONFIG_FILE
    export REDIS_SENTINEL_URL; export INDEX; yq -i '.cg.redisAtmosphereConfig.sentinelUrls.[env(INDEX)]=env(REDIS_SENTINEL_URL)' $CONFIG_FILE
    export REDIS_SENTINEL_URL; export INDEX; yq -i '.sentinelServersConfig.sentinelAddresses.[env(INDEX)]=env(REDIS_SENTINEL_URL)' $REDISSON_CACHE_FILE
    INDEX=$(expr $INDEX + 1)
  done
fi

if [[ "" != "$REDIS_ENV_NAMESPACE" ]]; then
    export REDIS_ENV_NAMESPACE; yq -i '.cg.redisLockConfig.envNamespace=env(REDIS_ENV_NAMESPACE)' $CONFIG_FILE
    export REDIS_ENV_NAMESPACE; yq -i '.cg.redisAtmosphereConfig.envNamespace=env(REDIS_ENV_NAMESPACE)' $CONFIG_FILE
fi

if [[ "" != "$REDIS_NETTY_THREADS" ]]; then
  export REDIS_NETTY_THREADS; yq -i '.cg.redisLockConfig.nettyThreads=env(REDIS_NETTY_THREADS)' $CONFIG_FILE
  export REDIS_NETTY_THREADS; yq -i '.cg.redisAtmosphereConfig.nettyThreads=env(REDIS_NETTY_THREADS)' $CONFIG_FILE
  export REDIS_NETTY_THREADS; yq -i '.nettyThreads=env(REDIS_NETTY_THREADS)' $REDISSON_CACHE_FILE
fi

if [[ "" != "$REDIS_CONNECTION_POOL_SIZE" ]]; then
  export REDIS_CONNECTION_POOL_SIZE; yq -i '.cg.redisLockConfig.connectionPoolSize=env(REDIS_CONNECTION_POOL_SIZE)' $CONFIG_FILE
  export REDIS_CONNECTION_POOL_SIZE; yq -i '.cg.redisAtmosphereConfig.connectionPoolSize=env(REDIS_CONNECTION_POOL_SIZE)' $CONFIG_FILE
  export REDIS_CONNECTION_POOL_SIZE; yq -i '.singleServerConfig.connectionPoolSize=env(REDIS_CONNECTION_POOL_SIZE)' $REDISSON_CACHE_FILE
fi

if [[ "" != "$REDIS_RETRY_INTERVAL" ]]; then
  export REDIS_RETRY_INTERVAL; yq -i '.cg.redisLockConfig.retryInterval=env(REDIS_RETRY_INTERVAL)' $CONFIG_FILE
  export REDIS_RETRY_INTERVAL; yq -i '.cg.redisAtmosphereConfig.retryInterval=env(REDIS_RETRY_INTERVAL)' $CONFIG_FILE
  export REDIS_RETRY_INTERVAL; yq -i '.singleServerConfig.retryInterval=env(REDIS_RETRY_INTERVAL)' $REDISSON_CACHE_FILE
fi

if [[ "" != "$REDIS_RETRY_ATTEMPTS" ]]; then
  export REDIS_RETRY_ATTEMPTS; yq -i '.cg.redisLockConfig.retryAttempts=env(REDIS_RETRY_ATTEMPTS)' $CONFIG_FILE
  export REDIS_RETRY_ATTEMPTS; yq -i '.cg.redisAtmosphereConfig.retryAttempts=env(REDIS_RETRY_ATTEMPTS)' $CONFIG_FILE
  export REDIS_RETRY_ATTEMPTS; yq -i '.singleServerConfig.retryAttempts=env(REDIS_RETRY_ATTEMPTS)' $REDISSON_CACHE_FILE
fi

if [[ "" != "$REDIS_TIMEOUT" ]]; then
  export REDIS_TIMEOUT; yq -i '.cg.redisLockConfig.timeout=env(REDIS_TIMEOUT)' $CONFIG_FILE
  export REDIS_TIMEOUT; yq -i '.cg.redisAtmosphereConfig.timeout=env(REDIS_TIMEOUT)' $CONFIG_FILE
  export REDIS_TIMEOUT; yq -i '.singleServerConfig.timeout=env(REDIS_TIMEOUT)' $REDISSON_CACHE_FILE
fi

if [[ "$REDIS_SCRIPT_CACHE" == "false" ]]; then
  yq -i '.cg.redisLockConfig.useScriptCache=false' $CONFIG_FILE
  yq -i '.cg.redisAtmosphereConfig.useScriptCache=false' $CONFIG_FILE
  yq -i '.useScriptCache=false' $REDISSON_CACHE_FILE
fi

if [[ "" != "$CACHE_NAMESPACE" ]]; then
    export CACHE_NAMESPACE; yq -i '.cg.cacheConfig.cacheNamespace=env(CACHE_NAMESPACE)' $CONFIG_FILE
fi

if [[ "" != "$CACHE_BACKEND" ]]; then
    export CACHE_BACKEND; yq -i '.cg.cacheConfig.cacheBackend=env(CACHE_BACKEND)' $CONFIG_FILE
fi

if [[ "" != "$GCP_MARKETPLACE_ENABLED" ]]; then
    export GCP_MARKETPLACE_ENABLED; yq -i '.cg.gcpMarketplaceConfig.enabled=env(GCP_MARKETPLACE_ENABLED)' $CONFIG_FILE
fi

if [[ "" != "$GCP_MARKETPLACE_SUBSCRIPTION_NAME" ]]; then
    export GCP_MARKETPLACE_SUBSCRIPTION_NAME; yq -i '.cg.gcpMarketplaceConfig.subscriptionName=env(GCP_MARKETPLACE_SUBSCRIPTION_NAME)' $CONFIG_FILE
fi

if [[ "" != "$CURRENT_JRE" ]]; then
  export CURRENT_JRE; yq -i '.cg.currentJre=env(CURRENT_JRE)' $CONFIG_FILE
fi

if [[ "" != "$MIGRATE_TO_JRE" ]]; then
  export MIGRATE_TO_JRE; yq -i '.cg.migrateToJre=env(MIGRATE_TO_JRE)' $CONFIG_FILE
fi

if [[ "" != "$ORACLE_JRE_TAR_PATH" ]]; then
  export ORACLE_JRE_TAR_PATH; yq -i '.cg.jreConfigs.oracle8u191.jreTarPath=env(ORACLE_JRE_TAR_PATH)' $CONFIG_FILE
fi

if [[ "" != "$OPENJDK_JRE_TAR_PATH" ]]; then
  export OPENJDK_JRE_TAR_PATH; yq -i '.cg.jreConfigs.openjdk8u242.jreTarPath=env(OPENJDK_JRE_TAR_PATH)' $CONFIG_FILE
fi

if [[ "" != "$CDN_URL" ]]; then
  export CDN_URL; yq -i '.cg.cdnConfig.url=env(CDN_URL)' $CONFIG_FILE
fi

if [[ "" != "$CDN_KEY" ]]; then
  export CDN_KEY; yq -i '.cg.cdnConfig.keyName=env(CDN_KEY)' $CONFIG_FILE
fi

if [[ "" != "$CDN_KEY_SECRET" ]]; then
  export CDN_KEY_SECRET; yq -i '.cg.cdnConfig.keySecret=env(CDN_KEY_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$CDN_DELEGATE_JAR_PATH" ]]; then
  export CDN_DELEGATE_JAR_PATH; yq -i '.cg.cdnConfig.delegateJarPath=env(CDN_DELEGATE_JAR_PATH)' $CONFIG_FILE
fi

if [[ "" != "$CDN_WATCHER_JAR_BASE_PATH" ]]; then
  export CDN_WATCHER_JAR_BASE_PATH; yq -i '.cg.cdnConfig.watcherJarBasePath=env(CDN_WATCHER_JAR_BASE_PATH)' $CONFIG_FILE
fi

if [[ "" != "$CDN_WATCHER_JAR_PATH" ]]; then
  export CDN_WATCHER_JAR_PATH; yq -i '.cg.cdnConfig.watcherJarPath=env(CDN_WATCHER_JAR_PATH)' $CONFIG_FILE
fi

if [[ "" != "$CDN_WATCHER_METADATA_FILE_PATH" ]]; then
  export CDN_WATCHER_METADATA_FILE_PATH; yq -i '.cg.cdnConfig.watcherMetaDataFilePath=env(CDN_WATCHER_METADATA_FILE_PATH)' $CONFIG_FILE
fi

if [[ "" != "$CDN_ORACLE_JRE_TAR_PATH" ]]; then
  export CDN_ORACLE_JRE_TAR_PATH; yq -i '.cg.cdnConfig.cdnJreTarPaths.oracle8u191=env(CDN_ORACLE_JRE_TAR_PATH)' $CONFIG_FILE
fi

if [[ "" != "$CDN_OPENJDK_JRE_TAR_PATH" ]]; then
  export CDN_OPENJDK_JRE_TAR_PATH; yq -i '.cg.cdnConfig.cdnJreTarPaths.openjdk8u242=env(CDN_OPENJDK_JRE_TAR_PATH)' $CONFIG_FILE
fi

if [[ "" != "$COMMAND_LIBRARY_SERVICE_BASE_URL" ]]; then
  export COMMAND_LIBRARY_SERVICE_BASE_URL; yq -i '.cg.commandLibraryServiceConfig.baseUrl=env(COMMAND_LIBRARY_SERVICE_BASE_URL)' $CONFIG_FILE
fi

if [[ "" != "$BUGSNAG_API_KEY" ]]; then
  export BUGSNAG_API_KEY; yq -i '.cg.bugsnagApiKey=env(BUGSNAG_API_KEY)' $CONFIG_FILE
fi

if [[ "" != "$ACCOUNT_LICENSE_CHECK_JOB_FREQUENCY" ]]; then
  export ACCOUNT_LICENSE_CHECK_JOB_FREQUENCY; yq -i '.cg.jobsFrequencyConfig.accountLicenseCheckJobFrequencyInMinutes=env(ACCOUNT_LICENSE_CHECK_JOB_FREQUENCY)' $CONFIG_FILE
fi

if [[ "" != "$ACCOUNT_DELETION_JOB_FREQUENCY" ]]; then
  export ACCOUNT_DELETION_JOB_FREQUENCY; yq -i '.cg.jobsFrequencyConfig.accountDeletionJobFrequencyInMinutes=env(ACCOUNT_DELETION_JOB_FREQUENCY)' $CONFIG_FILE
fi

if [[ "" != "$MANAGER_TO_COMMAND_LIBRARY_SERVICE_SECRET" ]]; then
  export MANAGER_TO_COMMAND_LIBRARY_SERVICE_SECRET; yq -i '.cg.commandLibraryServiceConfig.managerToCommandLibraryServiceSecret=env(MANAGER_TO_COMMAND_LIBRARY_SERVICE_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$DELEGATE_SERVICE_TARGET" ]]; then
  export DELEGATE_SERVICE_TARGET; yq -i '.cg.grpcDelegateServiceClientConfig.target=env(DELEGATE_SERVICE_TARGET)' $CONFIG_FILE
fi

if [[ "" != "$DELEGATE_SERVICE_AUTHORITY" ]]; then
  export DELEGATE_SERVICE_AUTHORITY; yq -i '.cg.grpcDelegateServiceClientConfig.authority=env(DELEGATE_SERVICE_AUTHORITY)' $CONFIG_FILE
fi

if [[ "" != "$DELEGATE_SERVICE_MANAGEMENT_AUTHORITY" ]]; then
  export DELEGATE_SERVICE_MANAGEMENT_AUTHORITY; yq -i '.cg.grpcDMSClientConfig.authority=env(DELEGATE_SERVICE_MANAGEMENT_AUTHORITY)' $CONFIG_FILE
fi

if [[ "" != "$DELEGATE_SERVICE_MANAGEMENT_TARGET" ]]; then
  export DELEGATE_SERVICE_MANAGEMENT_TARGET; yq -i '.cg.grpcDMSClientConfig.target=env(DELEGATE_SERVICE_MANAGEMENT_TARGET)' $CONFIG_FILE
fi

if [[ "" != "$DELEGATE_SERVICE_MANAGEMENT_SECRET" ]]; then
  export DELEGATE_SERVICE_MANAGEMENT_SECRET; yq -i '.cg.dmsSecret=env(DELEGATE_SERVICE_MANAGEMENT_SECRET)' $CONFIG_FILE
fi


if [[ "" != "$DELEGATE_GRPC_TARGET" ]]; then
  export DELEGATE_GRPC_TARGET; yq -i '.cg.grpcOnpremDelegateClientConfig.target=env(DELEGATE_GRPC_TARGET)' $CONFIG_FILE
fi

if [[ "" != "$DELEGATE_GRPC_AUTHORITY" ]]; then
  export DELEGATE_GRPC_AUTHORITY; yq -i '.cg.grpcOnpremDelegateClientConfig.authority=env(DELEGATE_GRPC_AUTHORITY)' $CONFIG_FILE
fi

if [[ "" != "$NG_MANAGER_AUTHORITY" ]]; then
  export NG_MANAGER_AUTHORITY; yq -i '.cg.grpcClientConfig.authority=env(NG_MANAGER_AUTHORITY)' $CONFIG_FILE
fi

if [[ "" != "$NG_MANAGER_TARGET" ]]; then
  export NG_MANAGER_TARGET; yq -i '.cg.grpcClientConfig.target=env(NG_MANAGER_TARGET)' $CONFIG_FILE
fi

if [[ "" != "$REMINDERS_BEFORE_ACCOUNT_DELETION" ]]; then
  export REMINDERS_BEFORE_ACCOUNT_DELETION; yq -i '.cg.numberOfRemindersBeforeAccountDeletion=env(REMINDERS_BEFORE_ACCOUNT_DELETION)' $CONFIG_FILE
fi

if [[ "" != "$EXPORT_DATA_BATCH_SIZE" ]]; then
  export EXPORT_DATA_BATCH_SIZE; yq -i '.cg.exportAccountDataBatchSize=env(EXPORT_DATA_BATCH_SIZE)' $CONFIG_FILE
fi

if [[ "" != "$COMMAND_LIBRARY_PUBLISHING_ALLOWED" ]]; then
  export COMMAND_LIBRARY_PUBLISHING_ALLOWED; yq -i '.cg.commandLibraryServiceConfig.publishingAllowed=env(COMMAND_LIBRARY_PUBLISHING_ALLOWED)' $CONFIG_FILE
fi

if [[ "" != "$COMMAND_LIBRARY_PUBLISHING_SECRET" ]]; then
  export COMMAND_LIBRARY_PUBLISHING_SECRET; yq -i '.cg.commandLibraryServiceConfig.publishingSecret=env(COMMAND_LIBRARY_PUBLISHING_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$LOG_STREAMING_SERVICE_BASEURL" ]]; then
  export LOG_STREAMING_SERVICE_BASEURL; yq -i '.cg.logStreamingServiceConfig.baseUrl=env(LOG_STREAMING_SERVICE_BASEURL)' $CONFIG_FILE
fi

if [[ "" != "$LOG_STREAMING_SERVICE_TOKEN" ]]; then
  export LOG_STREAMING_SERVICE_TOKEN; yq -i '.cg.logStreamingServiceConfig.serviceToken=env(LOG_STREAMING_SERVICE_TOKEN)' $CONFIG_FILE
fi

if [[ "" != "$ACCESS_CONTROL_ENABLED" ]]; then
  export ACCESS_CONTROL_ENABLED; yq -i '.cg.accessControlClient.enableAccessControl=env(ACCESS_CONTROL_ENABLED)' $CONFIG_FILE
fi

if [[ "" != "$ACCESS_CONTROL_BASE_URL" ]]; then
  export ACCESS_CONTROL_BASE_URL; yq -i '.cg.accessControlClient.accessControlServiceConfig.baseUrl=env(ACCESS_CONTROL_BASE_URL)' $CONFIG_FILE
fi

if [[ "" != "$ACCESS_CONTROL_SECRET" ]]; then
  export ACCESS_CONTROL_SECRET; yq -i '.cg.accessControlClient.accessControlServiceSecret=env(ACCESS_CONTROL_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$ENABLE_AUDIT" ]]; then
  export ENABLE_AUDIT; yq -i '.cg.enableAudit=env(ENABLE_AUDIT)' $CONFIG_FILE
fi

if [[ "" != "$AUDIT_CLIENT_BASEURL" ]]; then
  export AUDIT_CLIENT_BASEURL; yq -i '.cg.auditClientConfig.baseUrl=env(AUDIT_CLIENT_BASEURL)' $CONFIG_FILE
fi

if [[ "" != "$EVENTS_FRAMEWORK_REDIS_SENTINELS" ]]; then
  IFS=',' read -ra SENTINEL_URLS <<< "$EVENTS_FRAMEWORK_REDIS_SENTINELS"
  INDEX=0
  for REDIS_SENTINEL_URL in "${SENTINEL_URLS[@]}"; do
    export REDIS_SENTINEL_URL; export INDEX; yq -i '.cg.eventsFramework.redis.sentinelUrls.[env(INDEX)]=env(REDIS_SENTINEL_URL)' $CONFIG_FILE
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
  export GATEWAY_PATH_PREFIX; yq -i '.cg.portal.gatewayPathPrefix=env(GATEWAY_PATH_PREFIX)' $CONFIG_FILE
fi

if [[ "" != "$NG_MANAGER_BASE_URL" ]]; then
  export NG_MANAGER_BASE_URL; yq -i '.cg.ngManagerServiceHttpClientConfig.baseUrl=env(NG_MANAGER_BASE_URL)' $CONFIG_FILE
fi

if [[ "" != "$ENABLE_USER_CHANGESTREAM" ]]; then
  export ENABLE_USER_CHANGESTREAM; yq -i '.cg.userChangeStreamEnabled=env(ENABLE_USER_CHANGESTREAM)' $CONFIG_FILE
fi

if [[ "" != "$DISABLE_DELEGATE_MGMT_IN_MANAGER" ]]; then
  export DISABLE_DELEGATE_MGMT_IN_MANAGER; yq -i '.cg.disableDelegateMgmtInManager=env(DISABLE_DELEGATE_MGMT_IN_MANAGER)' $CONFIG_FILE
fi

if [[ "" != "$GCP_SECRET_MANAGER_PROJECT" ]]; then
  export GCP_SECRET_MANAGER_PROJECT; yq -i '.cg.secretsConfiguration.gcpSecretManagerProject=env(GCP_SECRET_MANAGER_PROJECT)' $CONFIG_FILE
fi

if [[ "" != "$RESOLVE_SECRETS" ]]; then
  export RESOLVE_SECRETS; yq -i '.cg.secretsConfiguration.secretResolutionEnabled=env(RESOLVE_SECRETS)' $CONFIG_FILE
fi

if [[ "" != "$LDAP_GROUP_SYNC_INTERVAL" ]]; then
  export LDAP_GROUP_SYNC_INTERVAL; yq -i '.cg.ldapSyncJobConfig.syncInterval=env(LDAP_GROUP_SYNC_INTERVAL)' $CONFIG_FILE
fi

if [[ "" != "$LDAP_GROUP_SYNC_POOL_SIZE" ]]; then
  export LDAP_GROUP_SYNC_POOL_SIZE; yq -i '.cg.ldapSyncJobConfig.poolSize=env(LDAP_GROUP_SYNC_POOL_SIZE)' $CONFIG_FILE
fi

if [[ "" != "$LDAP_GROUP_SYNC_DEFAULT_CRON" ]]; then
  export LDAP_GROUP_SYNC_DEFAULT_CRON; yq -i '.cg.ldapSyncJobConfig.defaultCronExpression=env(LDAP_GROUP_SYNC_DEFAULT_CRON)' $CONFIG_FILE
fi

if [[ "" != "$USE_GLOBAL_KMS_AS_BASE_ALGO" ]]; then
  export USE_GLOBAL_KMS_AS_BASE_ALGO; yq -i '.cg.useGlobalKMSAsBaseAlgo=env(USE_GLOBAL_KMS_AS_BASE_ALGO)' $CONFIG_FILE
fi

if [[ "" != "$SEGMENT_ENABLED_NG" ]]; then
  export SEGMENT_ENABLED_NG; yq -i '.cg.segmentConfiguration.enabled=env(SEGMENT_ENABLED_NG)' $CONFIG_FILE
fi

if [[ "" != "$SEGMENT_URL_NG" ]]; then
  export SEGMENT_URL_NG; yq -i '.cg.segmentConfiguration.url=env(SEGMENT_URL_NG)' $CONFIG_FILE
fi

if [[ "" != "$SEGMENT_APIKEY_NG" ]]; then
  export SEGMENT_APIKEY_NG; yq -i '.cg.segmentConfiguration.apiKey=env(SEGMENT_APIKEY_NG)' $CONFIG_FILE
fi

if [[ "" != "$SEGMENT_VERIFY_CERT_NG" ]]; then
  export SEGMENT_VERIFY_CERT_NG; yq -i '.cg.segmentConfiguration.certValidationRequired=env(SEGMENT_VERIFY_CERT_NG)' $CONFIG_FILE
fi

if [[ "" != "$SECOPS_EMAIL" ]]; then
 export SECOPS_EMAIL; yq -i '.cg.totp.secOpsEmail=env(SECOPS_EMAIL)' $CONFIG_FILE
fi

if [[ "" != "$INCORRECT_ATTEMPTS_UNTIL_SECOPS_NOTIFIED" ]]; then
 export INCORRECT_ATTEMPTS_UNTIL_SECOPS_NOTIFIED; yq -i '.cg.totp.incorrectAttemptsUntilSecOpsNotified=env(INCORRECT_ATTEMPTS_UNTIL_SECOPS_NOTIFIED)' $CONFIG_FILE
fi

if [[ "" != "$PIPELINE_SERVICE_CLIENT_BASEURL" ]]; then
  export PIPELINE_SERVICE_CLIENT_BASEURL; yq -i '.pipelineServiceClientConfig.baseUrl=env(PIPELINE_SERVICE_CLIENT_BASEURL)' $CONFIG_FILE
fi

if [[ "" != "$NG_MANAGER_CLIENT_BASEURL" ]]; then
  export NG_MANAGER_CLIENT_BASEURL; yq -i '.ngClientConfig.baseUrl=env(NG_MANAGER_CLIENT_BASEURL)' $CONFIG_FILE
fi

if [[ "" != "$TEMPLATE_SERVICE_ENDPOINT" ]]; then
  export TEMPLATE_SERVICE_ENDPOINT; yq -i '.templateServiceClientConfig.baseUrl=env(TEMPLATE_SERVICE_ENDPOINT)' $CONFIG_FILE
fi

replace_key_value cg.enableOpentelemetry "$ENABLE_OPENTELEMETRY"
