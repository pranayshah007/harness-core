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
    yq write -i $CONFIG_FILE $CONFIG_KEY $CONFIG_VALUE
  fi
}

write_mongo_params() {
  IFS='&' read -ra PARAMS <<< "$2"
  for PARAM_PAIR in "${PARAMS[@]}"; do
    NAME=$(cut -d= -f 1 <<< "$PARAM_PAIR")
    VALUE=$(cut -d= -f 2 <<< "$PARAM_PAIR")
    yq write -i $CONFIG_FILE $1.params.$NAME "$VALUE"
  done
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
  yq write -i $CONFIG_FILE server.applicationConnectors[0].port "9005"
fi

if [[ "" != "$SERVER_MAX_THREADS" ]]; then
  yq write -i $CONFIG_FILE server.maxThreads "$SERVER_MAX_THREADS"
fi

if [[ "" != "$ALLOWED_ORIGINS" ]]; then
  yq delete -i $CONFIG_FILE allowedOrigins
  yq write -i $CONFIG_FILE allowedOrigins "$ALLOWED_ORIGINS"
fi

if [[ "" != "$MONGO_URI" ]]; then
  yq write -i $CONFIG_FILE notificationServiceConfig.mongo.uri "${MONGO_URI//\\&/&}"
fi

if [[ "" != "$MONGO_CONNECT_TIMEOUT" ]]; then
  yq write -i $CONFIG_FILE notificationServiceConfig.mongo.connectTimeout $MONGO_CONNECT_TIMEOUT
fi

if [[ "" != "$MONGO_SERVER_SELECTION_TIMEOUT" ]]; then
  yq write -i $CONFIG_FILE notificationServiceConfig.mongo.serverSelectionTimeout $MONGO_SERVER_SELECTION_TIMEOUT
fi

if [[ "" != "$MAX_CONNECTION_IDLE_TIME" ]]; then
  yq write -i $CONFIG_FILE notificationServiceConfig.mongo.maxConnectionIdleTime $MAX_CONNECTION_IDLE_TIME
fi

if [[ "" != "$MONGO_CONNECTIONS_PER_HOST" ]]; then
  yq write -i $CONFIG_FILE notificationServiceConfig.mongo.connectionsPerHost $MONGO_CONNECTIONS_PER_HOST
fi

if [[ "" != "$MANAGER_CLIENT_SECRET" ]]; then
  yq write -i $CONFIG_FILE secrets.managerServiceSecret "$MANAGER_CLIENT_SECRET"
fi

if [[ "" != "$AUTH_ENABLED" ]]; then
  yq write -i $CONFIG_FILE enableAuth "$AUTH_ENABLED"
fi

if [[ "" != "$MANAGER_CLIENT_BASEURL" ]]; then
  yq write -i $CONFIG_FILE managerClientConfig.baseUrl "$MANAGER_CLIENT_BASEURL"
fi

if [[ "" != "$SMTP_HOST" ]]; then
  yq write -i $CONFIG_FILE notificationServiceConfig.smtp.host "$SMTP_HOST"
fi

if [[ "" != "$SMTP_PORT" ]]; then
  yq write -i $CONFIG_FILE notificationServiceConfig.smtp.port "$SMTP_PORT"
fi

if [[ "" != "$SMTP_USERNAME" ]]; then
  yq write -i $CONFIG_FILE notificationServiceConfig.smtp.username "$SMTP_USERNAME"
fi

if [[ "" != "$SMTP_PASSWORD" ]]; then
  yq write -i $CONFIG_FILE notificationServiceConfig.smtp.password "$SMTP_PASSWORD"
fi

if [[ "" != "$SMTP_USE_SSL" ]]; then
  yq write -i $CONFIG_FILE notificationServiceConfig.smtp.useSSL "$SMTP_USE_SSL"
fi

if [[ "" != "$OVERRIDE_PREDEFINED_TEMPLATES" ]]; then
  yq write -i $CONFIG_FILE notificationServiceConfig.seedDataConfiguration.shouldOverrideAllPredefinedTemplates "$OVERRIDE_PREDEFINED_TEMPLATES"
fi

if [[ "" != "$MONGO_MESSAGE_BROKER_URI" ]]; then
  yq write -i $CONFIG_FILE notificationClient.messageBroker.uri "$MONGO_MESSAGE_BROKER_URI"
fi

if [[ "" != "$RBAC_URL" ]]; then
  yq write -i $CONFIG_FILE rbacServiceConfig.baseUrl "$RBAC_URL"
fi

if [[ "" != "$NEXT_GEN_MANAGER_SECRET" ]]; then
  yq write -i $CONFIG_FILE secrets.ngManagerServiceSecret "$NEXT_GEN_MANAGER_SECRET"
fi

if [[ "" != "$JWT_AUTH_SECRET" ]]; then
  yq write -i $CONFIG_FILE secrets.jwtAuthSecret "$JWT_AUTH_SECRET"
fi

if [[ "" != "$JWT_IDENTITY_SERVICE_SECRET" ]]; then
  yq write -i $CONFIG_FILE secrets.jwtIdentityServiceSecret "$JWT_IDENTITY_SERVICE_SECRET"
fi

if [[ "" != "$GRPC_MANAGER_TARGET" ]]; then
  yq write -i $CONFIG_FILE notificationServiceConfig.delegateServiceGrpcConfig.target $GRPC_MANAGER_TARGET
fi

if [[ "" != "$GRPC_MANAGER_AUTHORITY" ]]; then
  yq write -i $CONFIG_FILE notificationServiceConfig.delegateServiceGrpcConfig.authority $GRPC_MANAGER_AUTHORITY
fi

if [[ "$STACK_DRIVER_LOGGING_ENABLED" == "true" ]]; then
  yq delete -i $CONFIG_FILE 'logging.appenders.(type==console)'
  yq write -i $CONFIG_FILE 'logging.appenders.(type==gke-console).stackdriverLogEnabled' "true"
else
  yq delete -i $CONFIG_FILE 'logging.appenders.(type==gke-console)'
fi

if [[ "" != "$AUDIT_MONGO_URI" ]]; then
  yq write -i $CONFIG_FILE auditServiceConfig.mongo.uri "${AUDIT_MONGO_URI//\\&/&}"
fi

if [[ "" != "$AUDIT_MONGO_CONNECT_TIMEOUT" ]]; then
  yq write -i $CONFIG_FILE auditServiceConfig.mongo.connectTimeout $AUDIT_MONGO_CONNECT_TIMEOUT
fi

if [[ "" != "$AUDIT_MONGO_SERVER_SELECTION_TIMEOUT" ]]; then
  yq write -i $CONFIG_FILE auditServiceConfig.mongo.serverSelectionTimeout $AUDIT_MONGO_SERVER_SELECTION_TIMEOUT
fi

if [[ "" != "$AUDIT_MAX_CONNECTION_IDLE_TIME" ]]; then
  yq write -i $CONFIG_FILE auditServiceConfig.mongo.maxConnectionIdleTime $AUDIT_MAX_CONNECTION_IDLE_TIME
fi

if [[ "" != "$AUDIT_MONGO_CONNECTIONS_PER_HOST" ]]; then
  yq write -i $CONFIG_FILE auditServiceConfig.mongo.connectionsPerHost $AUDIT_MONGO_CONNECTIONS_PER_HOST
fi

if [[ "" != "$AUDIT_MONGO_INDEX_MANAGER_MODE" ]]; then
  yq write -i $CONFIG_FILE auditServiceConfig.mongo.indexManagerMode $AUDIT_MONGO_INDEX_MANAGER_MODE
fi

if [[ "" != "$ENABLE_AUDIT_SERVICE" ]]; then
  yq write -i $CONFIG_FILE auditServiceConfig.enableAuditService $ENABLE_AUDIT_SERVICE
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
if [[ "" != "$EVENTS_FRAMEWORK_REDIS_URL" ]]; then
  yq write -i $CONFIG_FILE resourceGroupServiceConfig.redis.redisUrl "$EVENTS_FRAMEWORK_REDIS_URL"
fi

if [[ "" != "$EVENTS_FRAMEWORK_ENV_NAMESPACE" ]]; then
  yq write -i $CONFIG_FILE resourceGroupServiceConfig.redis.envNamespace "$EVENTS_FRAMEWORK_ENV_NAMESPACE"
fi

if [[ "" != "$EVENTS_FRAMEWORK_USE_SENTINEL" ]]; then
  yq write -i $CONFIG_FILE resourceGroupServiceConfig.redis.sentinel "$EVENTS_FRAMEWORK_USE_SENTINEL"
fi

if [[ "" != "$EVENTS_FRAMEWORK_SENTINEL_MASTER_NAME" ]]; then
  yq write -i $CONFIG_FILE resourceGroupServiceConfig.redis.masterName "$EVENTS_FRAMEWORK_SENTINEL_MASTER_NAME"
fi

if [[ "" != "$EVENTS_FRAMEWORK_REDIS_USERNAME" ]]; then
  yq write -i $CONFIG_FILE resourceGroupServiceConfig.redis.userName "$EVENTS_FRAMEWORK_REDIS_USERNAME"
fi

if [[ "" != "$EVENTS_FRAMEWORK_REDIS_PASSWORD" ]]; then
  yq write -i $CONFIG_FILE resourceGroupServiceConfig.redis.password "$EVENTS_FRAMEWORK_REDIS_PASSWORD"
fi

if [[ "" != "$EVENTS_FRAMEWORK_REDIS_SENTINELS" ]]; then
  IFS=',' read -ra SENTINEL_URLS <<< "$EVENTS_FRAMEWORK_REDIS_SENTINELS"
  INDEX=0
  for REDIS_SENTINEL_URL in "${SENTINEL_URLS[@]}"; do
    yq write -i $CONFIG_FILE resourceGroupServiceConfig.redis.sentinelUrls.[$INDEX] "${REDIS_SENTINEL_URL}"
    INDEX=$(expr $INDEX + 1)
  done
fi

if [[ "" != "$LOCK_CONFIG_REDIS_SENTINELS" ]]; then
  IFS=',' read -ra SENTINEL_URLS <<< "$LOCK_CONFIG_REDIS_SENTINELS"
  INDEX=0
  for REDIS_SENTINEL_URL in "${SENTINEL_URLS[@]}"; do
    yq write -i $CONFIG_FILE resourceGroupServiceConfig.redisLockConfig.sentinelUrls.[$INDEX] "${REDIS_SENTINEL_URL}"
    INDEX=$(expr $INDEX + 1)
  done
fi

if [[ "" != "$NOTIFICATION_MONGO_HOSTS_AND_PORTS" ]]; then
  yq delete -i $CONFIG_FILE notificationServiceConfig.mongo.uri
  write_mongo_hosts_and_ports notificationServiceConfig.mongo "$NOTIFICATION_MONGO_HOSTS_AND_PORTS"
  write_mongo_params notificationServiceConfig.mongo "$NOTIFICATION_MONGO_PARAMS"
fi

if [[ "" != "$AUDIT_MONGO_HOSTS_AND_PORTS" ]]; then
  yq delete -i $CONFIG_FILE auditServiceConfig.mongo.uri
  write_mongo_hosts_and_ports auditServiceConfig.mongo "$AUDIT_MONGO_HOSTS_AND_PORTS"
  write_mongo_params auditServiceConfig.mongo "$AUDIT_MONGO_PARAMS"
fi

if [[ "" != "$RESOURCE_GROUP_MONGO_HOSTS_AND_PORTS" ]]; then
  yq delete -i $CONFIG_FILE resourceGroupServiceConfig.mongo.uri
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
