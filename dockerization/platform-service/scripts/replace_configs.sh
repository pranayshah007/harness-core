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

write_mongo_params() {
  IFS='&' read -ra PARAMS <<< "$2"
  for PARAM_PAIR in "${PARAMS[@]}"; do
    NAME=$(cut -d= -f 1 <<< "$PARAM_PAIR")
    VALUE=$(cut -d= -f 2 <<< "$PARAM_PAIR")
    export VALUE; export ARG1=$1; export NAME; yq -i '.env(ARG1).params.env(NAME)=env(VALUE)' $CONFIG_FILE
  done
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
  yq -i '.server.applicationConnectors[0].port=9005' $CONFIG_FILE
fi

if [[ "" != "$SERVER_MAX_THREADS" ]]; then
  export SERVER_MAX_THREADS; yq -i '.server.maxThreads=env(SERVER_MAX_THREADS)' $CONFIG_FILE
fi

if [[ "" != "$ALLOWED_ORIGINS" ]]; then
  yq -i 'del(.allowedOrigins)' $CONFIG_FILE
  export ALLOWED_ORIGINS; yq -i '.allowedOrigins=env(ALLOWED_ORIGINS)' $CONFIG_FILE
fi

if [[ "" != "$MONGO_URI" ]]; then
  export MONGO_URI=${MONGO_URI//\\&/&}; yq -i '.notificationServiceConfig.mongo.uri=env(MONGO_URI)' $CONFIG_FILE
fi

if [[ "" != "$MONGO_CONNECT_TIMEOUT" ]]; then
  export MONGO_CONNECT_TIMEOUT; yq -i '.notificationServiceConfig.mongo.connectTimeout=env(MONGO_CONNECT_TIMEOUT)' $CONFIG_FILE
fi

if [[ "" != "$MONGO_SERVER_SELECTION_TIMEOUT" ]]; then
  export MONGO_SERVER_SELECTION_TIMEOUT; yq -i '.notificationServiceConfig.mongo.serverSelectionTimeout=env(MONGO_SERVER_SELECTION_TIMEOUT)' $CONFIG_FILE
fi

if [[ "" != "$MONGO_SOCKET_TIMEOUT" ]]; then
  export MONGO_SOCKET_TIMEOUT; yq -i '.notificationServiceConfig.mongo.socketTimeout=env(MONGO_SOCKET_TIMEOUT)' $CONFIG_FILE
fi

if [[ "" != "$MAX_CONNECTION_IDLE_TIME" ]]; then
  export MAX_CONNECTION_IDLE_TIME; yq -i '.notificationServiceConfig.mongo.maxConnectionIdleTime=env(MAX_CONNECTION_IDLE_TIME)' $CONFIG_FILE
fi

if [[ "" != "$MONGO_CONNECTIONS_PER_HOST" ]]; then
  export MONGO_CONNECTIONS_PER_HOST; yq -i '.notificationServiceConfig.mongo.connectionsPerHost=env(MONGO_CONNECTIONS_PER_HOST)' $CONFIG_FILE
fi

if [[ "" != "$MANAGER_CLIENT_SECRET" ]]; then
  export MANAGER_CLIENT_SECRET; yq -i '.secrets.managerServiceSecret=env(MANAGER_CLIENT_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$AUTH_ENABLED" ]]; then
  export AUTH_ENABLED; yq -i '.enableAuth=env(AUTH_ENABLED)' $CONFIG_FILE
fi

if [[ "" != "$MANAGER_CLIENT_BASEURL" ]]; then
  export MANAGER_CLIENT_BASEURL; yq -i '.managerClientConfig.baseUrl=env(MANAGER_CLIENT_BASEURL)' $CONFIG_FILE
fi

if [[ "" != "$SMTP_HOST" ]]; then
  export SMTP_HOST; yq -i '.notificationServiceConfig.smtp.host=env(SMTP_HOST)' $CONFIG_FILE
fi

if [[ "" != "$SMTP_PORT" ]]; then
  export SMTP_PORT; yq -i '.notificationServiceConfig.smtp.port=env(SMTP_PORT)' $CONFIG_FILE
fi

if [[ "" != "$SMTP_USERNAME" ]]; then
  export SMTP_USERNAME; yq -i '.notificationServiceConfig.smtp.username=env(SMTP_USERNAME)' $CONFIG_FILE
fi

if [[ "" != "$SMTP_PASSWORD" ]]; then
  export SMTP_PASSWORD; yq -i '.notificationServiceConfig.smtp.password=env(SMTP_PASSWORD)' $CONFIG_FILE
fi

if [[ "" != "$SMTP_USE_SSL" ]]; then
  export SMTP_USE_SSL; yq -i '.notificationServiceConfig.smtp.useSSL=env(SMTP_USE_SSL)' $CONFIG_FILE
fi

if [[ "" != "$OVERRIDE_PREDEFINED_TEMPLATES" ]]; then
  export OVERRIDE_PREDEFINED_TEMPLATES; yq -i '.notificationServiceConfig.seedDataConfiguration.shouldOverrideAllPredefinedTemplates=env(OVERRIDE_PREDEFINED_TEMPLATES)' $CONFIG_FILE
fi

if [[ "" != "$MONGO_MESSAGE_BROKER_URI" ]]; then
  export MONGO_MESSAGE_BROKER_URI; yq -i '.notificationClient.messageBroker.uri=env(MONGO_MESSAGE_BROKER_URI)' $CONFIG_FILE
fi

if [[ "" != "$RBAC_URL" ]]; then
  export RBAC_URL; yq -i '.rbacServiceConfig.baseUrl=env(RBAC_URL)' $CONFIG_FILE
fi

if [[ "" != "$NEXT_GEN_MANAGER_SECRET" ]]; then
  export NEXT_GEN_MANAGER_SECRET; yq -i '.secrets.ngManagerServiceSecret=env(NEXT_GEN_MANAGER_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$JWT_AUTH_SECRET" ]]; then
  export JWT_AUTH_SECRET; yq -i '.secrets.jwtAuthSecret=env(JWT_AUTH_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$JWT_IDENTITY_SERVICE_SECRET" ]]; then
  export JWT_IDENTITY_SERVICE_SECRET; yq -i '.secrets.jwtIdentityServiceSecret=env(JWT_IDENTITY_SERVICE_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$GRPC_MANAGER_TARGET" ]]; then
  export GRPC_MANAGER_TARGET; yq -i '.notificationServiceConfig.delegateServiceGrpcConfig.target=env(GRPC_MANAGER_TARGET)' $CONFIG_FILE
fi

if [[ "" != "$GRPC_MANAGER_AUTHORITY" ]]; then
  export GRPC_MANAGER_AUTHORITY; yq -i '.notificationServiceConfig.delegateServiceGrpcConfig.authority=env(GRPC_MANAGER_AUTHORITY)' $CONFIG_FILE
fi

if [[ "$STACK_DRIVER_LOGGING_ENABLED" == "true" ]]; then
  yq -i 'del(.logging.appenders.[] | select(.type == "console"))' $CONFIG_FILE
  yq -i '(.logging.appenders.[] | select(.type == "gke-console") | .stackdriverLogEnabled) = true' $CONFIG_FILE
else
  yq -i 'del(.logging.appenders.[] | select(.type == "gke-console"))' $CONFIG_FILE
fi

if [[ "" != "$AUDIT_MONGO_URI" ]]; then
  export AUDIT_MONGO_URI=${AUDIT_MONGO_URI//\\&/&}; yq -i '.auditServiceConfig.mongo.uri=env(AUDIT_MONGO_URI)' $CONFIG_FILE
fi

if [[ "" != "$AUDIT_MONGO_CONNECT_TIMEOUT" ]]; then
  export AUDIT_MONGO_CONNECT_TIMEOUT; yq -i '.auditServiceConfig.mongo.connectTimeout=env(AUDIT_MONGO_CONNECT_TIMEOUT)' $CONFIG_FILE
fi

if [[ "" != "$AUDIT_MONGO_SERVER_SELECTION_TIMEOUT" ]]; then
  export AUDIT_MONGO_SERVER_SELECTION_TIMEOUT; yq -i '.auditServiceConfig.mongo.serverSelectionTimeout=env(AUDIT_MONGO_SERVER_SELECTION_TIMEOUT)' $CONFIG_FILE
fi

if [[ "" != "$AUDIT_MONGO_SOCKET_TIMEOUT" ]]; then
  export AUDIT_MONGO_SOCKET_TIMEOUT; yq -i '.auditServiceConfig.mongo.socketTimeout=env(AUDIT_MONGO_SOCKET_TIMEOUT)' $CONFIG_FILE
fi

if [[ "" != "$AUDIT_MAX_CONNECTION_IDLE_TIME" ]]; then
  export AUDIT_MAX_CONNECTION_IDLE_TIME; yq -i '.auditServiceConfig.mongo.maxConnectionIdleTime=env(AUDIT_MAX_CONNECTION_IDLE_TIME)' $CONFIG_FILE
fi

if [[ "" != "$AUDIT_MONGO_CONNECTIONS_PER_HOST" ]]; then
  export AUDIT_MONGO_CONNECTIONS_PER_HOST; yq -i '.auditServiceConfig.mongo.connectionsPerHost=env(AUDIT_MONGO_CONNECTIONS_PER_HOST)' $CONFIG_FILE
fi

if [[ "" != "$AUDIT_MONGO_INDEX_MANAGER_MODE" ]]; then
  export AUDIT_MONGO_INDEX_MANAGER_MODE; yq -i '.auditServiceConfig.mongo.indexManagerMode=env(AUDIT_MONGO_INDEX_MANAGER_MODE)' $CONFIG_FILE
fi

if [[ "" != "$ENABLE_AUDIT_SERVICE" ]]; then
  export ENABLE_AUDIT_SERVICE; yq -i '.auditServiceConfig.enableAuditService=env(ENABLE_AUDIT_SERVICE)' $CONFIG_FILE
fi

if [[ "" != "$ACCESS_CONTROL_ENABLED" ]]; then
  export ACCESS_CONTROL_ENABLED; yq -i '.accessControlClient.enableAccessControl=env(ACCESS_CONTROL_ENABLED)' $CONFIG_FILE
fi

if [[ "" != "$ACCESS_CONTROL_BASE_URL" ]]; then
  export ACCESS_CONTROL_BASE_URL; yq -i '.accessControlClient.accessControlServiceConfig.baseUrl=env(ACCESS_CONTROL_BASE_URL)' $CONFIG_FILE
fi

if [[ "" != "$ACCESS_CONTROL_SECRET" ]]; then
  export ACCESS_CONTROL_SECRET; yq -i '.accessControlClient.accessControlServiceSecret=env(ACCESS_CONTROL_SECRET)' $CONFIG_FILE
fi
if [[ "" != "$EVENTS_FRAMEWORK_REDIS_URL" ]]; then
  export EVENTS_FRAMEWORK_REDIS_URL; yq -i '.resourceGroupServiceConfig.redis.redisUrl=env(EVENTS_FRAMEWORK_REDIS_URL)' $CONFIG_FILE
fi

if [[ "" != "$EVENTS_FRAMEWORK_ENV_NAMESPACE" ]]; then
  export EVENTS_FRAMEWORK_ENV_NAMESPACE; yq -i '.resourceGroupServiceConfig.redis.envNamespace=env(EVENTS_FRAMEWORK_ENV_NAMESPACE)' $CONFIG_FILE
fi

if [[ "" != "$EVENTS_FRAMEWORK_USE_SENTINEL" ]]; then
  export EVENTS_FRAMEWORK_USE_SENTINEL; yq -i '.resourceGroupServiceConfig.redis.sentinel=env(EVENTS_FRAMEWORK_USE_SENTINEL)' $CONFIG_FILE
fi

if [[ "" != "$EVENTS_FRAMEWORK_SENTINEL_MASTER_NAME" ]]; then
  export EVENTS_FRAMEWORK_SENTINEL_MASTER_NAME; yq -i '.resourceGroupServiceConfig.redis.masterName=env(EVENTS_FRAMEWORK_SENTINEL_MASTER_NAME)' $CONFIG_FILE
fi

if [[ "" != "$EVENTS_FRAMEWORK_REDIS_USERNAME" ]]; then
  export EVENTS_FRAMEWORK_REDIS_USERNAME; yq -i '.resourceGroupServiceConfig.redis.userName=env(EVENTS_FRAMEWORK_REDIS_USERNAME)' $CONFIG_FILE
fi

if [[ "" != "$EVENTS_FRAMEWORK_REDIS_PASSWORD" ]]; then
  export EVENTS_FRAMEWORK_REDIS_PASSWORD; yq -i '.resourceGroupServiceConfig.redis.password=env(EVENTS_FRAMEWORK_REDIS_PASSWORD)' $CONFIG_FILE
fi

if [[ "" != "$PROD_QA_ZENDESK_BASE_URL" ]]; then
  export PROD_QA_ZENDESK_BASE_URL; yq -i '.zendeskApiConfig.baseUrl=env(PROD_QA_ZENDESK_BASE_URL)' $CONFIG_FILE
fi

if [[ "" != "$PROD_QA_ZENDESK_TOKEN" ]]; then
  export PROD_QA_ZENDESK_TOKEN; yq -i '.zendeskApiConfig.token=env(PROD_QA_ZENDESK_TOKEN)' $CONFIG_FILE
fi

if [[ "" != "$PROD_QA_COVEO_TOKEN" ]]; then
  export PROD_QA_COVEO_TOKEN; yq -i '.zendeskApiConfig.coveoToken=env(PROD_QA_COVEO_TOKEN)' $CONFIG_FILE
fi

if [[ "" != "$EVENTS_FRAMEWORK_REDIS_SENTINELS" ]]; then
  IFS=',' read -ra SENTINEL_URLS <<< "$EVENTS_FRAMEWORK_REDIS_SENTINELS"
  INDEX=0
  for REDIS_SENTINEL_URL in "${SENTINEL_URLS[@]}"; do
    export REDIS_SENTINEL_URL; export INDEX; yq -i '.resourceGroupServiceConfig.redis.sentinelUrls.[env(INDEX)]=env(REDIS_SENTINEL_URL)' $CONFIG_FILE
    INDEX=$(expr $INDEX + 1)
  done
fi

if [[ "" != "$LOCK_CONFIG_REDIS_SENTINELS" ]]; then
  IFS=',' read -ra SENTINEL_URLS <<< "$LOCK_CONFIG_REDIS_SENTINELS"
  INDEX=0
  for REDIS_SENTINEL_URL in "${SENTINEL_URLS[@]}"; do
    export REDIS_SENTINEL_URL; export INDEX; yq -i '.resourceGroupServiceConfig.redisLockConfig.sentinelUrls.[env(INDEX)]=env(REDIS_SENTINEL_URL)' $CONFIG_FILE
    INDEX=$(expr $INDEX + 1)
  done
fi

if [[ "" != "$NOTIFICATION_MONGO_HOSTS_AND_PORTS" ]]; then
  yq -i 'del(.notificationServiceConfig.mongo.uri)' $CONFIG_FILE
  write_mongo_hosts_and_ports notificationServiceConfig.mongo "$NOTIFICATION_MONGO_HOSTS_AND_PORTS"
  write_mongo_params notificationServiceConfig.mongo "$NOTIFICATION_MONGO_PARAMS"
fi

if [[ "" != "$AUDIT_MONGO_HOSTS_AND_PORTS" ]]; then
  yq -i 'del(.auditServiceConfig.mongo.uri)' $CONFIG_FILE
  write_mongo_hosts_and_ports auditServiceConfig.mongo "$AUDIT_MONGO_HOSTS_AND_PORTS"
  write_mongo_params auditServiceConfig.mongo "$AUDIT_MONGO_PARAMS"
fi

if [[ "" != "$RESOURCE_GROUP_MONGO_HOSTS_AND_PORTS" ]]; then
  yq -i 'del(.resourceGroupServiceConfig.mongo.uri)' $CONFIG_FILE
  write_mongo_hosts_and_ports resourceGroupServiceConfig.mongo "$RESOURCE_GROUP_MONGO_HOSTS_AND_PORTS"
  write_mongo_params resourceGroupServiceConfig.mongo "$RESOURCE_GROUP_MONGO_PARAMS"
fi

replace_key_value ngManagerClientConfig.baseUrl "$NG_MANAGER_CLIENT_BASEURL"

replace_key_value resourceGroupServiceConfig.mongo.username "$RESOURCE_GROUP_MONGO_USERNAME"
replace_key_value resourceGroupServiceConfig.mongo.password "$RESOURCE_GROUP_MONGO_PASSWORD"
replace_key_value resourceGroupServiceConfig.mongo.schema "$RESOURCE_GROUP_MONGO_SCHEMA"
replace_key_value resourceGroupServiceConfig.mongo.database "$RESOURCE_GROUP_MONGO_DATABASE"

replace_key_value auditServiceConfig.mongo.username "$AUDIT_MONGO_USERNAME"
replace_key_value auditServiceConfig.mongo.password "$AUDIT_MONGO_PASSWORD"
replace_key_value auditServiceConfig.mongo.schema "$AUDIT_MONGO_SCHEMA"
replace_key_value auditServiceConfig.mongo.database "$AUDIT_MONGO_DATABASE"

replace_key_value notificationServiceConfig.mongo.username "$NOTIFICATION_MONGO_USERNAME"
replace_key_value notificationServiceConfig.mongo.password "$NOTIFICATION_MONGO_PASSWORD"
replace_key_value notificationServiceConfig.mongo.schema "$NOTIFICATION_MONGO_SCHEMA"
replace_key_value notificationServiceConfig.mongo.database "$NOTIFICATION_MONGO_DATABASE"

replace_key_value resourceGroupServiceConfig.redisLockConfig.redisUrl "$LOCK_CONFIG_REDIS_URL"

replace_key_value resourceGroupServiceConfig.redisLockConfig.envNamespace "$LOCK_CONFIG_ENV_NAMESPACE"

replace_key_value resourceGroupServiceConfig.redisLockConfig.sentinel "$LOCK_CONFIG_USE_SENTINEL"

replace_key_value resourceGroupServiceConfig.redisLockConfig.masterName "$LOCK_CONFIG_SENTINEL_MASTER_NAME"

replace_key_value resourceGroupServiceConfig.redisLockConfig.userName "$LOCK_CONFIG_REDIS_USERNAME"

replace_key_value resourceGroupServiceConfig.redisLockConfig.password "$LOCK_CONFIG_REDIS_PASSWORD"

replace_key_value resourceGroupServiceConfig.redisLockConfig.nettyThreads "$REDIS_NETTY_THREADS"

replace_key_value resourceGroupServiceConfig.distributedLockImplementation "$DISTRIBUTED_LOCK_IMPLEMENTATION"

replace_key_value resourceGroupServiceConfig.auditClientConfig.baseUrl "$AUDIT_CLIENT_BASEURL"

replace_key_value resourceGroupServiceConfig.enableAudit "$AUDIT_ENABLED"

replace_key_value resourceGroupServiceConfig.exportMetricsToStackDriver "$EXPORT_METRICS_TO_STACK_DRIVER"

replace_key_value resourceGroupServiceConfig.accessControlAdminClient.accessControlServiceConfig.baseUrl "$ACCESS_CONTROL_BASE_URL"

replace_key_value resourceGroupServiceConfig.accessControlAdminClient.accessControlServiceSecret "$ACCESS_CONTROL_SECRET"

replace_key_value resourceGroupServiceConfig.accessControlAdminClient.mockAccessControlService "$MOCK_ACCESS_CONTROL_SERVICE"

replace_key_value resourceGroupServiceConfig.resourceClients.ng-manager.baseUrl "$NG_MANAGER_CLIENT_BASEURL"

replace_key_value resourceGroupServiceConfig.resourceClients.ng-manager.secret "$NEXT_GEN_MANAGER_SECRET"

replace_key_value resourceGroupServiceConfig.resourceClients.pipeline-service.baseUrl "$PIPELINE_SERVICE_CLIENT_BASEURL"

replace_key_value resourceGroupServiceConfig.resourceClients.pipeline-service.secret "$PIPELINE_SERVICE_SECRET"

replace_key_value resourceGroupServiceConfig.resourceClients.manager.baseUrl "$MANAGER_CLIENT_BASEURL"

replace_key_value resourceGroupServiceConfig.resourceClients.manager.secret "$NEXT_GEN_MANAGER_SECRET"

replace_key_value resourceGroupServiceConfig.resourceClients.resourceGroup.baseUrl "$RESOURCE_GROUP_CLIENT_BASEURL"

replace_key_value resourceGroupServiceConfig.resourceClients.resourceGroup.secret "$RESOURCE_GROUP_SECRET"

replace_key_value resourceGroupServiceConfig.mongo.uri "${RESOURCE_GROUP_MONGO_URI//\\&/&}"

replace_key_value resourceGroupServiceConfig.redis.nettyThreads "$EVENTS_FRAMEWORK_NETTY_THREADS"

replace_key_value resourceGroupServiceConfig.redis.sslConfig.enabled "$EVENTS_FRAMEWORK_REDIS_SSL_ENABLED"

replace_key_value resourceGroupServiceConfig.redis.sslConfig.CATrustStorePath "$EVENTS_FRAMEWORK_REDIS_SSL_CA_TRUST_STORE_PATH"

replace_key_value resourceGroupServiceConfig.redis.sslConfig.CATrustStorePassword "$EVENTS_FRAMEWORK_REDIS_SSL_CA_TRUST_STORE_PASSWORD"

replace_key_value notificationServiceConfig.mongo.indexManagerMode "$MONGO_INDEX_MANAGER_MODE"

replace_key_value resourceGroupServiceConfig.mongo.indexManagerMode "$MONGO_INDEX_MANAGER_MODE"

replace_key_value auditServiceConfig.mongo.indexManagerMode "$MONGO_INDEX_MANAGER_MODE"

replace_key_value resourceGroupServiceConfig.enableResourceGroup "${ENABLE_RESOURCE_GROUP:-false}"

replace_key_value resourceGroupServiceConfig.resourceClients.template-service.baseUrl "$TEMPLATE_SERVICE_CLIENT_BASEURL"

replace_key_value resourceGroupServiceConfig.resourceClients.template-service.secret "$TEMPLATE_SERVICE_SECRET"

replace_key_value resourceGroupServiceConfig.resourceClients.gitops-service.baseUrl "$GITOPS_SERVICE_CLIENT_BASEURL"

replace_key_value resourceGroupServiceConfig.resourceClients.gitops-service.secret "$GITOPS_SERVICE_SECRET"

replace_key_value enforcementClientConfiguration.enforcementCheckEnabled "$ENFORCEMENT_CHECK_ENABLED"

replace_key_value secretsConfiguration.gcpSecretManagerProject "$GCP_SECRET_MANAGER_PROJECT"
replace_key_value secretsConfiguration.secretResolutionEnabled "$RESOLVE_SECRETS"

replace_key_value enableOpentelemetry "$ENABLE_OPENTELEMETRY"

replace_key_value resourceGroupServiceConfig.resourceClients.ce-nextgen.baseUrl "$CE_NEXTGEN_CLIENT_BASEURL"
replace_key_value resourceGroupServiceConfig.resourceClients.ce-nextgen.secret "$CE_NEXTGEN_SECRET"
