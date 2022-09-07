#!/usr/bin/env bash
# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

CONFIG_FILE=/opt/harness/ci-manager-config.yml
REDISSON_CACHE_FILE=/opt/harness/redisson-jcache.yaml
ENTERPRISE_REDISSON_CACHE_FILE=/opt/harness/enterprise-redisson-jcache.yaml

replace_key_value () {
  CONFIG_KEY="$1";
  CONFIG_VALUE="$2";
  if [[ "" != "$CONFIG_VALUE" ]]; then
    yq -i '.$CONFIG_KEY="$CONFIG_VALUE"' $CONFIG_FILE
  fi
}

yq -i 'del(.server.applicationConnectors | select(.type == "https"))' $CONFIG_FILE
yq -i '.server.adminConnectors="[]"' $CONFIG_FILE

yq -i 'del(.pmsSdkGrpcServerConfig.connectors | select(.secure == "true"))' $CONFIG_FILE

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

if [[ "" != "$MANAGER_URL" ]]; then
  yq -i '.managerClientConfig.baseUrl="$MANAGER_URL"' $CONFIG_FILE
fi

if [[ "" != "$NG_MANAGER_URL" ]]; then
  yq -i '.ngManagerClientConfig.baseUrl="$NG_MANAGER_URL"' $CONFIG_FILE
fi

if [[ "" != "$ADDON_IMAGE" ]]; then
  yq -i '.ciExecutionServiceConfig.addonImage="$ADDON_IMAGE"' $CONFIG_FILE
fi
if [[ "" != "$LE_IMAGE" ]]; then
  yq -i '.ciExecutionServiceConfig.liteEngineImage="$LE_IMAGE"' $CONFIG_FILE
fi

if [[ "" != "$GIT_CLONE_IMAGE" ]]; then
  yq -i '.ciExecutionServiceConfig.stepConfig.gitCloneConfig.image="$GIT_CLONE_IMAGE"' $CONFIG_FILE
fi

if [[ "" != "$DOCKER_PUSH_IMAGE" ]]; then
  yq -i '.ciExecutionServiceConfig.stepConfig.buildAndPushDockerRegistryConfig.image="$DOCKER_PUSH_IMAGE"' $CONFIG_FILE
fi

if [[ "" != "$ECR_PUSH_IMAGE" ]]; then
  yq -i '.ciExecutionServiceConfig.stepConfig.buildAndPushECRConfig.image="$ECR_PUSH_IMAGE"' $CONFIG_FILE
fi

if [[ "" != "$GCR_PUSH_IMAGE" ]]; then
  yq -i '.ciExecutionServiceConfig.stepConfig.buildAndPushGCRConfig.image="$GCR_PUSH_IMAGE"' $CONFIG_FILE
fi

if [[ "" != "$ENABLE_AUTH" ]]; then
  yq -i '.enableAuth="$ENABLE_AUTH"' $CONFIG_FILE
fi

if [[ "" != "$GCS_UPLOAD_IMAGE" ]]; then
  yq -i '.ciExecutionServiceConfig.stepConfig.gcsUploadConfig.image="$GCS_UPLOAD_IMAGE"' $CONFIG_FILE
fi

if [[ "" != "$S3_UPLOAD_IMAGE" ]]; then
  yq -i '.ciExecutionServiceConfig.stepConfig.s3UploadConfig.image="$S3_UPLOAD_IMAGE"' $CONFIG_FILE
fi

if [[ "" != "$SECURITY_IMAGE" ]]; then
  yq -i '.ciExecutionServiceConfig.stepConfig.securityConfig.image="$SECURITY_IMAGE"' $CONFIG_FILE
fi

if [[ "" != "$ARTIFACTORY_UPLOAD_IMAGE" ]]; then
  yq -i '.ciExecutionServiceConfig.stepConfig.artifactoryUploadConfig.image="$ARTIFACTORY_UPLOAD_IMAGE"' $CONFIG_FILE
fi

if [[ "" != "$GCS_CACHE_IMAGE" ]]; then
  yq -i '.ciExecutionServiceConfig.stepConfig.cacheGCSConfig.image="$GCS_CACHE_IMAGE"' $CONFIG_FILE
fi

if [[ "" != "$S3_CACHE_IMAGE" ]]; then
  yq -i '.ciExecutionServiceConfig.stepConfig.cacheS3Config.image="$S3_CACHE_IMAGE"' $CONFIG_FILE
fi

if [[ "" != "$VM_GIT_CLONE_IMAGE" ]]; then
  yq -i '.ciExecutionServiceConfig.stepConfig.vmImageConfig.gitClone="$VM_GIT_CLONE_IMAGE"' $CONFIG_FILE
fi

if [[ "" != "$VM_DOCKER_PUSH_IMAGE" ]]; then
  yq -i '.ciExecutionServiceConfig.stepConfig.vmImageConfig.buildAndPushDockerRegistry="$VM_DOCKER_PUSH_IMAGE"' $CONFIG_FILE
fi

if [[ "" != "$VM_ECR_PUSH_IMAGE" ]]; then
  yq -i '.ciExecutionServiceConfig.stepConfig.vmImageConfig.buildAndPushECR="$VM_ECR_PUSH_IMAGE"' $CONFIG_FILE
fi

if [[ "" != "$VM_GCR_PUSH_IMAGE" ]]; then
  yq -i '.ciExecutionServiceConfig.stepConfig.vmImageConfig.buildAndPushGCR="$VM_GCR_PUSH_IMAGE"' $CONFIG_FILE
fi

if [[ "" != "$VM_GCS_UPLOAD_IMAGE" ]]; then
  yq -i '.ciExecutionServiceConfig.stepConfig.vmImageConfig.gcsUpload="$VM_GCS_UPLOAD_IMAGE"' $CONFIG_FILE
fi

if [[ "" != "$VM_S3_UPLOAD_IMAGE" ]]; then
  yq -i '.ciExecutionServiceConfig.stepConfig.vmImageConfig.s3Upload="$VM_S3_UPLOAD_IMAGE"' $CONFIG_FILE
fi

if [[ "" != "$VM_ARTIFACTORY_UPLOAD_IMAGE" ]]; then
  yq -i '.ciExecutionServiceConfig.stepConfig.vmImageConfig.artifactoryUpload="$VM_ARTIFACTORY_UPLOAD_IMAGE"' $CONFIG_FILE
fi

if [[ "" != "$VM_GCS_CACHE_IMAGE" ]]; then
  yq -i '.ciExecutionServiceConfig.stepConfig.vmImageConfig.cacheGCS="$VM_GCS_CACHE_IMAGE"' $CONFIG_FILE
fi

if [[ "" != "$VM_S3_CACHE_IMAGE" ]]; then
  yq -i '.ciExecutionServiceConfig.stepConfig.vmImageConfig.cacheS3="$VM_S3_CACHE_IMAGE"' $CONFIG_FILE
fi

if [[ "" != "$VM_SECURITY_IMAGE" ]]; then
  yq -i '.ciExecutionServiceConfig.stepConfig.vmImageConfig.security="$VM_SECURITY_IMAGE"' $CONFIG_FILE
fi

if [[ "" != "$DEFAULT_MEMORY_LIMIT" ]]; then
  yq -i '.ciExecutionServiceConfig.defaultMemoryLimit="$DEFAULT_MEMORY_LIMIT"' $CONFIG_FILE
fi
if [[ "" != "$DEFAULT_CPU_LIMIT" ]]; then
  yq -i '.ciExecutionServiceConfig.defaultCPULimit="$DEFAULT_CPU_LIMIT"' $CONFIG_FILE
fi
if [[ "" != "$DEFAULT_INTERNAL_IMAGE_CONNECTOR" ]]; then
  yq -i '.ciExecutionServiceConfig.defaultInternalImageConnector="$DEFAULT_INTERNAL_IMAGE_CONNECTOR"' $CONFIG_FILE
fi
if [[ "" != "$PVC_DEFAULT_STORAGE_SIZE" ]]; then
  yq -i '.ciExecutionServiceConfig.pvcDefaultStorageSize="$PVC_DEFAULT_STORAGE_SIZE"' $CONFIG_FILE
fi
if [[ "" != "$DELEGATE_SERVICE_ENDPOINT_VARIABLE_VALUE" ]]; then
  yq -i '.ciExecutionServiceConfig.delegateServiceEndpointVariableValue="$DELEGATE_SERVICE_ENDPOINT_VARIABLE_VALUE"' $CONFIG_FILE
fi

if [[ "" != "$SERVER_MAX_THREADS" ]]; then
  yq -i '.server.maxThreads="$SERVER_MAX_THREADS"' $CONFIG_FILE
fi

if [[ "" != "$ALLOWED_ORIGINS" ]]; then
  yq -i 'del(.allowedOrigins)' $CONFIG_FILE
  yq -i '.allowedOrigins="$ALLOWED_ORIGINS"' $CONFIG_FILE
fi

if [[ "" != "$MONGO_URI" ]]; then
  yq -i '.harness-mongo.uri="${MONGO_URI//\\&/&}"' $CONFIG_FILE
fi

if [[ "" != "$MANAGER_TARGET" ]]; then
  yq -i '.managerTarget="$MANAGER_TARGET"' $CONFIG_FILE
fi

if [[ "" != "$MANAGER_AUTHORITY" ]]; then
  yq -i '.managerAuthority="$MANAGER_AUTHORITY"' $CONFIG_FILE
fi

if [[ "" != "$CIMANAGER_MONGO_URI" ]]; then
  yq -i '.cimanager-mongo.uri="$CIMANAGER_MONGO_URI"' $CONFIG_FILE
fi

if [[ "" != "$SCM_SERVICE_URI" ]]; then
  yq -i '.scmConnectionConfig.url="$SCM_SERVICE_URI"' $CONFIG_FILE
fi

if [[ "" != "$LOG_SERVICE_ENDPOINT" ]]; then
  yq -i '.logServiceConfig.baseUrl="$LOG_SERVICE_ENDPOINT"' $CONFIG_FILE
fi

if [[ "" != "$LOG_SERVICE_GLOBAL_TOKEN" ]]; then
  yq -i '.logServiceConfig.globalToken="$LOG_SERVICE_GLOBAL_TOKEN"' $CONFIG_FILE
fi

if [[ "" != "$TI_SERVICE_ENDPOINT" ]]; then
  yq -i '.tiServiceConfig.baseUrl="$TI_SERVICE_ENDPOINT"' $CONFIG_FILE
fi

if [[ "" != "$STO_SERVICE_ENDPOINT" ]]; then
  yq -i '.stoServiceConfig.baseUrl="$STO_SERVICE_ENDPOINT"' $CONFIG_FILE
fi

if [[ "" != "$API_URL" ]]; then
  yq -i '.apiUrl="$API_URL"' $CONFIG_FILE
fi

if [[ "" != "$PMS_TARGET" ]]; then
  yq -i '.pmsGrpcClientConfig.target="$PMS_TARGET"' $CONFIG_FILE
fi

if [[ "" != "$PMS_AUTHORITY" ]]; then
  yq -i '.pmsGrpcClientConfig.authority="$PMS_AUTHORITY"' $CONFIG_FILE
fi

if [[ "" != "$SHOULD_CONFIGURE_WITH_PMS" ]]; then
  yq -i '.shouldConfigureWithPMS="$SHOULD_CONFIGURE_WITH_PMS"' $CONFIG_FILE
fi

if [[ "" != "$PMS_MONGO_URI" ]]; then
  yq -i '.pmsMongo.uri="${PMS_MONGO_URI//\\&/&}"' $CONFIG_FILE
fi

if [[ "" != "$GRPC_SERVER_PORT" ]]; then
  yq -i '.pmsSdkGrpcServerConfig.connectors[0].port="$GRPC_SERVER_PORT"' $CONFIG_FILE
fi

if [[ "" != "$TI_SERVICE_GLOBAL_TOKEN" ]]; then
  yq -i '.tiServiceConfig.globalToken="$TI_SERVICE_GLOBAL_TOKEN"' $CONFIG_FILE
fi

if [[ "" != "$STO_SERVICE_GLOBAL_TOKEN" ]]; then
  yq -i '.stoServiceConfig.globalToken="$STO_SERVICE_GLOBAL_TOKEN"' $CONFIG_FILE
fi

if [[ "" != "$NEXT_GEN_MANAGER_SECRET" ]]; then
  yq -i '.ngManagerServiceSecret="$NEXT_GEN_MANAGER_SECRET"' $CONFIG_FILE
fi

if [[ "" != "$JWT_AUTH_SECRET" ]]; then
  yq -i '.jwtAuthSecret="$JWT_AUTH_SECRET"' $CONFIG_FILE
fi

if [[ "" != "$JWT_IDENTITY_SERVICE_SECRET" ]]; then
  yq -i '.jwtIdentityServiceSecret="$JWT_IDENTITY_SERVICE_SECRET"' $CONFIG_FILE
fi

if [[ "" != "$API_URL" ]]; then
  yq -i '.apiUrl="$API_URL"' $CONFIG_FILE
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

if [[ "" != "$MANAGER_SECRET" ]]; then
  yq -i '.managerServiceSecret="$MANAGER_SECRET"' $CONFIG_FILE
fi

if [[ "" != "$MONGO_INDEX_MANAGER_MODE" ]]; then
  yq -i '.cimanager-mongo.indexManagerMode="$MONGO_INDEX_MANAGER_MODE"' $CONFIG_FILE
fi

if [[ "$STACK_DRIVER_LOGGING_ENABLED" == "true" ]]; then
  yq -i 'del(.logging.appenders | select(.type == "console"))' $CONFIG_FILE
  yq -i '(.logging.appenders | select(.type == "gke-console") | .stackdriverLogEnabled) = "true"' $CONFIG_FILE
else
  yq -i 'del(.logging.appenders | select(.type == "gke-console"))' $CONFIG_FILE
fi

replace_key_value accessControlClient.enableAccessControl "$ACCESS_CONTROL_ENABLED"

replace_key_value accessControlClient.accessControlServiceConfig.baseUrl "$ACCESS_CONTROL_BASE_URL"

replace_key_value accessControlClient.accessControlServiceSecret "$ACCESS_CONTROL_SECRET"

if [[ "" != "$EVENTS_FRAMEWORK_REDIS_SENTINELS" ]]; then
  IFS=',' read -ra SENTINEL_URLS <<< "$EVENTS_FRAMEWORK_REDIS_SENTINELS"
  INDEX=0
  for REDIS_SENTINEL_URL in "${SENTINEL_URLS[@]}"; do
    yq -i '.eventsFramework.redis.sentinelUrls.[$INDEX]="${REDIS_SENTINEL_URL}"' $CONFIG_FILE
    INDEX=$(expr $INDEX + 1)
  done
fi

yq -i 'del(.codec)' $REDISSON_CACHE_FILE

if [[ "$REDIS_SCRIPT_CACHE" == "false" ]]; then
  yq -i '.useScriptCache="false"' $REDISSON_CACHE_FILE
fi


if [[ "" != "$CACHE_CONFIG_REDIS_URL" ]]; then
  yq -i '.singleServerConfig.address="$CACHE_CONFIG_REDIS_URL"' $REDISSON_CACHE_FILE
fi

if [[ "$CACHE_CONFIG_USE_SENTINEL" == "true" ]]; then
  yq -i 'del(.singleServerConfig)' $REDISSON_CACHE_FILE
fi

if [[ "" != "$CACHE_CONFIG_SENTINEL_MASTER_NAME" ]]; then
  yq -i '.sentinelServersConfig.masterName="$CACHE_CONFIG_SENTINEL_MASTER_NAME"' $REDISSON_CACHE_FILE
fi

if [[ "" != "$CACHE_CONFIG_REDIS_SENTINELS" ]]; then
  IFS=',' read -ra SENTINEL_URLS <<< "$CACHE_CONFIG_REDIS_SENTINELS"
  INDEX=0
  for REDIS_SENTINEL_URL in "${SENTINEL_URLS[@]}"; do
    yq -i '.sentinelServersConfig.sentinelAddresses.[$INDEX]="${REDIS_SENTINEL_URL}"' $REDISSON_CACHE_FILE
    INDEX=$(expr $INDEX + 1)
  done
fi

if [[ "" != "$REDIS_NETTY_THREADS" ]]; then
  yq -i '.nettyThreads="$REDIS_NETTY_THREADS"' $REDISSON_CACHE_FILE
fi

yq -i 'del(.codec)' $ENTERPRISE_REDISSON_CACHE_FILE

if [[ "$REDIS_SCRIPT_CACHE" == "false" ]]; then
  yq -i '.useScriptCache="false"' $ENTERPRISE_REDISSON_CACHE_FILE
fi

if [[ "" != "$EVENTS_FRAMEWORK_NETTY_THREADS" ]]; then
  yq -i '.nettyThreads="$EVENTS_FRAMEWORK_NETTY_THREADS"' $ENTERPRISE_REDISSON_CACHE_FILE
fi

if [[ "" != "$EVENTS_FRAMEWORK_REDIS_URL" ]]; then
  yq -i '.singleServerConfig.address="$EVENTS_FRAMEWORK_REDIS_URL"' $ENTERPRISE_REDISSON_CACHE_FILE
fi

if [[ "" != "$EVENTS_FRAMEWORK_REDIS_USERNAME" ]]; then
  yq -i '.singleServerConfig.username="$EVENTS_FRAMEWORK_REDIS_USERNAME"' $ENTERPRISE_REDISSON_CACHE_FILE
fi

if [[ "" != "$EVENTS_FRAMEWORK_REDIS_PASSWORD" ]]; then
  yq -i '.singleServerConfig.password="$EVENTS_FRAMEWORK_REDIS_PASSWORD"' $ENTERPRISE_REDISSON_CACHE_FILE
fi

if [[ "" != "$EVENTS_FRAMEWORK_REDIS_SSL_CA_TRUST_STORE_PATH" ]]; then
  yq -i '.singleServerConfig.sslTruststore="file:$EVENTS_FRAMEWORK_REDIS_SSL_CA_TRUST_STORE_PATH"' $ENTERPRISE_REDISSON_CACHE_FILE
fi

if [[ "" != "$EVENTS_FRAMEWORK_REDIS_SSL_CA_TRUST_STORE_PASSWORD" ]]; then
  yq -i '.singleServerConfig.sslTruststorePassword="$EVENTS_FRAMEWORK_REDIS_SSL_CA_TRUST_STORE_PASSWORD"' $ENTERPRISE_REDISSON_CACHE_FILE
fi

if [[ "$EVENTS_FRAMEWORK_USE_SENTINEL" == "true" ]]; then
  yq -i 'del(.singleServerConfig)' $ENTERPRISE_REDISSON_CACHE_FILE

  if [[ "" != "$EVENTS_FRAMEWORK_SENTINEL_MASTER_NAME" ]]; then
    yq -i '.sentinelServersConfig.masterName="$EVENTS_FRAMEWORK_SENTINEL_MASTER_NAME"' $ENTERPRISE_REDISSON_CACHE_FILE
  fi

  if [[ "" != "$EVENTS_FRAMEWORK_REDIS_SENTINELS" ]]; then
    IFS=',' read -ra SENTINEL_URLS <<< "$EVENTS_FRAMEWORK_REDIS_SENTINELS"
    INDEX=0
    for REDIS_SENTINEL_URL in "${SENTINEL_URLS[@]}"; do
      yq -i '.sentinelServersConfig.sentinelAddresses.[$INDEX]="${REDIS_SENTINEL_URL}"' $ENTERPRISE_REDISSON_CACHE_FILE
      INDEX=$(expr $INDEX + 1)
    done
  fi
fi

replace_key_value cacheConfig.cacheNamespace $CACHE_NAMESPACE
replace_key_value cacheConfig.cacheBackend $CACHE_BACKEND
replace_key_value cacheConfig.enterpriseCacheEnabled $ENTERPRISE_CACHE_ENABLED

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
