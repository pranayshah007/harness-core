#!/usr/bin/env bash
# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

CONFIG_FILE=/opt/harness/sto-manager-config.yml
REDISSON_CACHE_FILE=/opt/harness/redisson-jcache.yaml


replace_key_value () {
  CONFIG_KEY="$1";
  CONFIG_VALUE="$2";
  if [[ "" != "$CONFIG_VALUE" ]]; then
    yq write -i $CONFIG_FILE $CONFIG_KEY $CONFIG_VALUE
  fi
}

yq delete -i $CONFIG_FILE server.applicationConnectors[0]
yq write -i $CONFIG_FILE server.adminConnectors "[]"

yq delete -i $CONFIG_FILE pmsSdkGrpcServerConfig.connectors[0]

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

if [[ "" != "$MANAGER_URL" ]]; then
  yq write -i $CONFIG_FILE managerClientConfig.baseUrl "$MANAGER_URL"
fi

if [[ "" != "$NG_MANAGER_URL" ]]; then
  yq write -i $CONFIG_FILE ngManagerClientConfig.baseUrl "$NG_MANAGER_URL"
fi

if [[ "" != "$ADDON_IMAGE" ]]; then
  yq write -i $CONFIG_FILE ciExecutionServiceConfig.addonImage "$ADDON_IMAGE"
fi
if [[ "" != "$LE_IMAGE" ]]; then
  yq write -i $CONFIG_FILE ciExecutionServiceConfig.liteEngineImage "$LE_IMAGE"
fi

if [[ "" != "$GIT_CLONE_IMAGE" ]]; then
  yq write -i $CONFIG_FILE ciExecutionServiceConfig.stepConfig.gitCloneConfig.image "$GIT_CLONE_IMAGE"
fi

if [[ "" != "$DOCKER_PUSH_IMAGE" ]]; then
  yq write -i $CONFIG_FILE ciExecutionServiceConfig.stepConfig.buildAndPushDockerRegistryConfig.image "$DOCKER_PUSH_IMAGE"
fi

if [[ "" != "$ECR_PUSH_IMAGE" ]]; then
  yq write -i $CONFIG_FILE ciExecutionServiceConfig.stepConfig.buildAndPushECRConfig.image "$ECR_PUSH_IMAGE"
fi

if [[ "" != "$GCR_PUSH_IMAGE" ]]; then
  yq write -i $CONFIG_FILE ciExecutionServiceConfig.stepConfig.buildAndPushGCRConfig.image "$GCR_PUSH_IMAGE"
fi

if [[ "" != "$GCS_UPLOAD_IMAGE" ]]; then
  yq write -i $CONFIG_FILE ciExecutionServiceConfig.stepConfig.gcsUploadConfig.image "$GCS_UPLOAD_IMAGE"
fi

if [[ "" != "$S3_UPLOAD_IMAGE" ]]; then
  yq write -i $CONFIG_FILE ciExecutionServiceConfig.stepConfig.s3UploadConfig.image "$S3_UPLOAD_IMAGE"
fi

if [[ "" != "$SECURITY_IMAGE" ]]; then
  yq write -i $CONFIG_FILE ciExecutionServiceConfig.stepConfig.securityConfig.image "$SECURITY_IMAGE"
fi

if [[ "" != "$ARTIFACTORY_UPLOAD_IMAGE" ]]; then
  yq write -i $CONFIG_FILE ciExecutionServiceConfig.stepConfig.artifactoryUploadConfig.image "$ARTIFACTORY_UPLOAD_IMAGE"
fi

if [[ "" != "$GCS_CACHE_IMAGE" ]]; then
  yq write -i $CONFIG_FILE ciExecutionServiceConfig.stepConfig.cacheGCSConfig.image "$GCS_CACHE_IMAGE"
fi

if [[ "" != "$S3_CACHE_IMAGE" ]]; then
  yq write -i $CONFIG_FILE ciExecutionServiceConfig.stepConfig.cacheS3Config.image "$S3_CACHE_IMAGE"
fi

if [[ "" != "$VM_GIT_CLONE_IMAGE" ]]; then
  yq write -i $CONFIG_FILE ciExecutionServiceConfig.stepConfig.vmImageConfig.gitClone "$VM_GIT_CLONE_IMAGE"
fi

if [[ "" != "$VM_DOCKER_PUSH_IMAGE" ]]; then
  yq write -i $CONFIG_FILE ciExecutionServiceConfig.stepConfig.vmImageConfig.buildAndPushDockerRegistry "$VM_DOCKER_PUSH_IMAGE"
fi

if [[ "" != "$VM_ECR_PUSH_IMAGE" ]]; then
  yq write -i $CONFIG_FILE ciExecutionServiceConfig.stepConfig.vmImageConfig.buildAndPushECR "$VM_ECR_PUSH_IMAGE"
fi

if [[ "" != "$VM_GCR_PUSH_IMAGE" ]]; then
  yq write -i $CONFIG_FILE ciExecutionServiceConfig.stepConfig.vmImageConfig.buildAndPushGCR "$VM_GCR_PUSH_IMAGE"
fi

if [[ "" != "$VM_GCS_UPLOAD_IMAGE" ]]; then
  yq write -i $CONFIG_FILE ciExecutionServiceConfig.stepConfig.vmImageConfig.gcsUpload "$VM_GCS_UPLOAD_IMAGE"
fi

if [[ "" != "$VM_S3_UPLOAD_IMAGE" ]]; then
  yq write -i $CONFIG_FILE ciExecutionServiceConfig.stepConfig.vmImageConfig.s3Upload "$VM_S3_UPLOAD_IMAGE"
fi

if [[ "" != "$VM_ARTIFACTORY_UPLOAD_IMAGE" ]]; then
  yq write -i $CONFIG_FILE ciExecutionServiceConfig.stepConfig.vmImageConfig.artifactoryUpload "$VM_ARTIFACTORY_UPLOAD_IMAGE"
fi

if [[ "" != "$VM_GCS_CACHE_IMAGE" ]]; then
  yq write -i $CONFIG_FILE ciExecutionServiceConfig.stepConfig.vmImageConfig.cacheGCS "$VM_GCS_CACHE_IMAGE"
fi

if [[ "" != "$VM_S3_CACHE_IMAGE" ]]; then
  yq write -i $CONFIG_FILE ciExecutionServiceConfig.stepConfig.vmImageConfig.cacheS3 "$VM_S3_CACHE_IMAGE"
fi

if [[ "" != "$DEFAULT_MEMORY_LIMIT" ]]; then
  yq write -i $CONFIG_FILE ciExecutionServiceConfig.defaultMemoryLimit "$DEFAULT_MEMORY_LIMIT"
fi
if [[ "" != "$DEFAULT_CPU_LIMIT" ]]; then
  yq write -i $CONFIG_FILE ciExecutionServiceConfig.defaultCPULimit "$DEFAULT_CPU_LIMIT"
fi
if [[ "" != "$DEFAULT_INTERNAL_IMAGE_CONNECTOR" ]]; then
  yq write -i $CONFIG_FILE ciExecutionServiceConfig.defaultInternalImageConnector "$DEFAULT_INTERNAL_IMAGE_CONNECTOR"
fi
if [[ "" != "$PVC_DEFAULT_STORAGE_SIZE" ]]; then
  yq write -i $CONFIG_FILE ciExecutionServiceConfig.pvcDefaultStorageSize "$PVC_DEFAULT_STORAGE_SIZE"
fi
if [[ "" != "$DELEGATE_SERVICE_ENDPOINT_VARIABLE_VALUE" ]]; then
  yq write -i $CONFIG_FILE ciExecutionServiceConfig.delegateServiceEndpointVariableValue "$DELEGATE_SERVICE_ENDPOINT_VARIABLE_VALUE"
fi

if [[ "" != "$SERVER_MAX_THREADS" ]]; then
  yq write -i $CONFIG_FILE server.maxThreads "$SERVER_MAX_THREADS"
fi

if [[ "" != "$ALLOWED_ORIGINS" ]]; then
  yq delete -i $CONFIG_FILE allowedOrigins
  yq write -i $CONFIG_FILE allowedOrigins "$ALLOWED_ORIGINS"
fi

if [[ "" != "$MANAGER_TARGET" ]]; then
  yq write -i $CONFIG_FILE managerTarget $MANAGER_TARGET
fi

if [[ "" != "$MANAGER_AUTHORITY" ]]; then
  yq write -i $CONFIG_FILE managerAuthority $MANAGER_AUTHORITY
fi

if [[ "" != "$STOMANAGER_MONGO_URI" ]]; then
  yq write -i $CONFIG_FILE stomanager-mongo.uri "$STOMANAGER_MONGO_URI"
fi

if [[ "" != "$LOG_SERVICE_ENDPOINT" ]]; then
  yq write -i $CONFIG_FILE logServiceConfig.baseUrl "$LOG_SERVICE_ENDPOINT"
fi

if [[ "" != "$LOG_SERVICE_GLOBAL_TOKEN" ]]; then
  yq write -i $CONFIG_FILE logServiceConfig.globalToken "$LOG_SERVICE_GLOBAL_TOKEN"
fi

if [[ "" != "$TI_SERVICE_ENDPOINT" ]]; then
  yq write -i $CONFIG_FILE tiServiceConfig.baseUrl "$TI_SERVICE_ENDPOINT"
fi

if [[ "" != "$STO_SERVICE_ENDPOINT" ]]; then
  yq write -i $CONFIG_FILE stoServiceConfig.baseUrl "$STO_SERVICE_ENDPOINT"
fi

if [[ "" != "$API_URL" ]]; then
  yq write -i $CONFIG_FILE apiUrl "$API_URL"
fi

if [[ "" != "$PMS_TARGET" ]]; then
  yq write -i $CONFIG_FILE pmsGrpcClientConfig.target $PMS_TARGET
fi

if [[ "" != "$PMS_AUTHORITY" ]]; then
  yq write -i $CONFIG_FILE pmsGrpcClientConfig.authority $PMS_AUTHORITY
fi

if [[ "" != "$SHOULD_CONFIGURE_WITH_PMS" ]]; then
  yq write -i $CONFIG_FILE shouldConfigureWithPMS $SHOULD_CONFIGURE_WITH_PMS
fi

if [[ "" != "$GRPC_SERVER_PORT" ]]; then
  yq write -i $CONFIG_FILE pmsSdkGrpcServerConfig.connectors[0].port "$GRPC_SERVER_PORT"
fi

if [[ "" != "$TI_SERVICE_GLOBAL_TOKEN" ]]; then
  yq write -i $CONFIG_FILE tiServiceConfig.globalToken "$TI_SERVICE_GLOBAL_TOKEN"
fi

if [[ "" != "$STO_SERVICE_GLOBAL_TOKEN" ]]; then
  yq write -i $CONFIG_FILE stoServiceConfig.globalToken "$STO_SERVICE_GLOBAL_TOKEN"
fi

if [[ "" != "$NEXT_GEN_MANAGER_SECRET" ]]; then
  yq write -i $CONFIG_FILE ngManagerServiceSecret "$NEXT_GEN_MANAGER_SECRET"
fi

if [[ "" != "$JWT_AUTH_SECRET" ]]; then
  yq write -i $CONFIG_FILE jwtAuthSecret "$JWT_AUTH_SECRET"
fi

if [[ "" != "$ENABLE_AUTH" ]]; then
  yq write -i $CONFIG_FILE enableAuth "$ENABLE_AUTH"
fi

if [[ "" != "$JWT_IDENTITY_SERVICE_SECRET" ]]; then
  yq write -i $CONFIG_FILE jwtIdentityServiceSecret "$JWT_IDENTITY_SERVICE_SECRET"
fi

if [[ "" != "$API_URL" ]]; then
  yq write -i $CONFIG_FILE apiUrl "$API_URL"
fi

if [[ "" != "$MANAGER_SECRET" ]]; then
  yq write -i $CONFIG_FILE managerServiceSecret "$MANAGER_SECRET"
fi

if [[ "$STACK_DRIVER_LOGGING_ENABLED" == "true" ]]; then
  yq delete -i $CONFIG_FILE logging.appenders[0]
  yq write -i $CONFIG_FILE logging.appenders[0].stackdriverLogEnabled "true"
else
  yq delete -i $CONFIG_FILE logging.appenders[1]
fi

replace_key_value accessControlClient.enableAccessControl "$ACCESS_CONTROL_ENABLED"

replace_key_value accessControlClient.accessControlServiceConfig.baseUrl "$ACCESS_CONTROL_BASE_URL"

replace_key_value accessControlClient.accessControlServiceSecret "$ACCESS_CONTROL_SECRET"

if [[ "" != "$EVENTS_FRAMEWORK_REDIS_SENTINELS" ]]; then
  IFS=',' read -ra SENTINEL_URLS <<< "$EVENTS_FRAMEWORK_REDIS_SENTINELS"
  INDEX=0
  for REDIS_SENTINEL_URL in "${SENTINEL_URLS[@]}"; do
    yq write -i $CONFIG_FILE eventsFramework.redis.sentinelUrls.[$INDEX] "${REDIS_SENTINEL_URL}"
    INDEX=$(expr $INDEX + 1)
  done
fi

yq delete -i $REDISSON_CACHE_FILE codec

if [[ "$REDIS_SCRIPT_CACHE" == "false" ]]; then
  yq write -i $REDISSON_CACHE_FILE useScriptCache false
fi


if [[ "" != "$CACHE_CONFIG_REDIS_URL" ]]; then
  yq write -i $REDISSON_CACHE_FILE singleServerConfig.address "$CACHE_CONFIG_REDIS_URL"
fi

if [[ "$CACHE_CONFIG_USE_SENTINEL" == "true" ]]; then
  yq delete -i $REDISSON_CACHE_FILE singleServerConfig
fi

if [[ "" != "$CACHE_CONFIG_SENTINEL_MASTER_NAME" ]]; then
  yq write -i $REDISSON_CACHE_FILE sentinelServersConfig.masterName "$CACHE_CONFIG_SENTINEL_MASTER_NAME"
fi

if [[ "" != "$CACHE_CONFIG_REDIS_SENTINELS" ]]; then
  IFS=',' read -ra SENTINEL_URLS <<< "$CACHE_CONFIG_REDIS_SENTINELS"
  INDEX=0
  for REDIS_SENTINEL_URL in "${SENTINEL_URLS[@]}"; do
    yq write -i $REDISSON_CACHE_FILE sentinelServersConfig.sentinelAddresses.[+] "${REDIS_SENTINEL_URL}"
    INDEX=$(expr $INDEX + 1)
  done
fi

if [[ "" != "$REDIS_NETTY_THREADS" ]]; then
  yq write -i $REDISSON_CACHE_FILE nettyThreads "$REDIS_NETTY_THREADS"
fi

replace_key_value cacheConfig.cacheNamespace $CACHE_NAMESPACE
replace_key_value cacheConfig.cacheBackend $CACHE_BACKEND

replace_key_value segmentConfiguration.enabled "$SEGMENT_ENABLED"
replace_key_value segmentConfiguration.url "$SEGMENT_URL"
replace_key_value segmentConfiguration.apiKey "$SEGMENT_APIKEY"
replace_key_value segmentConfiguration.certValidationRequired "$SEGMENT_VERIFY_CERT"

replace_key_value eventsFramework.redis.sentinel $EVENTS_FRAMEWORK_USE_SENTINEL
replace_key_value eventsFramework.redis.envNamespace $EVENTS_FRAMEWORK_ENV_NAMESPACE
replace_key_value eventsFramework.redis.redisUrl $EVENTS_FRAMEWORK_REDIS_URL
replace_key_value eventsFramework.redis.masterName $EVENTS_FRAMEWORK_SENTINEL_MASTER_NAME
replace_key_value eventsFramework.redis.userName $EVENTS_FRAMEWORK_REDIS_USERNAME
replace_key_value eventsFramework.redis.password $EVENTS_FRAMEWORK_REDIS_PASSWORD
replace_key_value eventsFramework.redis.sslConfig.enabled $EVENTS_FRAMEWORK_REDIS_SSL_ENABLED
replace_key_value eventsFramework.redis.sslConfig.CATrustStorePath $EVENTS_FRAMEWORK_REDIS_SSL_CA_TRUST_STORE_PATH
replace_key_value eventsFramework.redis.sslConfig.CATrustStorePassword $EVENTS_FRAMEWORK_REDIS_SSL_CA_TRUST_STORE_PASSWORD

