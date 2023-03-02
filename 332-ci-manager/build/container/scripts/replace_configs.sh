#!/usr/bin/env bash
# Copyright 2022 Harness Inc. All rights reserved.
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
    export CONFIG_VALUE; export CONFIG_KEY; export CONFIG_KEY=.$CONFIG_KEY; yq -i 'eval(strenv(CONFIG_KEY))=env(CONFIG_VALUE)' $CONFIG_FILE
  fi
}

yq -i 'del(.server.applicationConnectors.[] | select(.type == "https"))' $CONFIG_FILE
yq -i '.server.adminConnectors=[]' $CONFIG_FILE

yq -i 'del(.pmsSdkGrpcServerConfig.connectors.[] | select(.secure == true))' $CONFIG_FILE

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
  yq -i '.server.applicationConnectors[0].port=7090' $CONFIG_FILE
fi

if [[ "" != "$MANAGER_URL" ]]; then
  export MANAGER_URL; yq -i '.managerClientConfig.baseUrl=env(MANAGER_URL)' $CONFIG_FILE
fi

if [[ "" != "$NG_MANAGER_URL" ]]; then
  export NG_MANAGER_URL; yq -i '.ngManagerClientConfig.baseUrl=env(NG_MANAGER_URL)' $CONFIG_FILE
fi

if [[ "" != "$ADDON_IMAGE" ]]; then
  export ADDON_IMAGE; yq -i '.ciExecutionServiceConfig.addonImage=env(ADDON_IMAGE)' $CONFIG_FILE
fi
if [[ "" != "$LE_IMAGE" ]]; then
  export LE_IMAGE; yq -i '.ciExecutionServiceConfig.liteEngineImage=env(LE_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$GIT_CLONE_IMAGE" ]]; then
  export GIT_CLONE_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.gitCloneConfig.image=env(GIT_CLONE_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$DOCKER_PUSH_IMAGE" ]]; then
  export DOCKER_PUSH_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.buildAndPushDockerRegistryConfig.image=env(DOCKER_PUSH_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$ECR_PUSH_IMAGE" ]]; then
  export ECR_PUSH_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.buildAndPushECRConfig.image=env(ECR_PUSH_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$ACR_PUSH_IMAGE" ]]; then
  export ACR_PUSH_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.buildAndPushACRConfig.image=env(ACR_PUSH_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$GCR_PUSH_IMAGE" ]]; then
  export GCR_PUSH_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.buildAndPushGCRConfig.image=env(GCR_PUSH_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$ENABLE_AUTH" ]]; then
  export ENABLE_AUTH; yq -i '.enableAuth=env(ENABLE_AUTH)' $CONFIG_FILE
fi

if [[ "" != "$GCS_UPLOAD_IMAGE" ]]; then
  export GCS_UPLOAD_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.gcsUploadConfig.image=env(GCS_UPLOAD_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$S3_UPLOAD_IMAGE" ]]; then
  export S3_UPLOAD_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.s3UploadConfig.image=env(S3_UPLOAD_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$SECURITY_IMAGE" ]]; then
  export SECURITY_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.securityConfig.image=env(SECURITY_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$ARTIFACTORY_UPLOAD_IMAGE" ]]; then
  export ARTIFACTORY_UPLOAD_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.artifactoryUploadConfig.image=env(ARTIFACTORY_UPLOAD_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$GCS_CACHE_IMAGE" ]]; then
  export GCS_CACHE_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.cacheGCSConfig.image=env(GCS_CACHE_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$S3_CACHE_IMAGE" ]]; then
  export S3_CACHE_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.cacheS3Config.image=env(S3_CACHE_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$VM_GIT_CLONE_IMAGE" ]]; then
  export VM_GIT_CLONE_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.vmImageConfig.gitClone=env(VM_GIT_CLONE_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$VM_DOCKER_PUSH_IMAGE" ]]; then
  export VM_DOCKER_PUSH_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.vmImageConfig.buildAndPushDockerRegistry=env(VM_DOCKER_PUSH_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$VM_ECR_PUSH_IMAGE" ]]; then
  export VM_ECR_PUSH_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.vmImageConfig.buildAndPushECR=env(VM_ECR_PUSH_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$VM_GCR_PUSH_IMAGE" ]]; then
  export VM_GCR_PUSH_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.vmImageConfig.buildAndPushGCR=env(VM_GCR_PUSH_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$VM_GCS_UPLOAD_IMAGE" ]]; then
  export VM_GCS_UPLOAD_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.vmImageConfig.gcsUpload=env(VM_GCS_UPLOAD_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$VM_S3_UPLOAD_IMAGE" ]]; then
  export VM_S3_UPLOAD_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.vmImageConfig.s3Upload=env(VM_S3_UPLOAD_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$VM_SECURITY_IMAGE" ]]; then
  export VM_SECURITY_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.vmImageConfig.security=env(VM_SECURITY_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$CACHE_BUCKET" ]]; then
  export CACHE_BUCKET; yq -i '.ciExecutionServiceConfig.cacheIntelligenceConfig.bucket=env(CACHE_BUCKET)' $CONFIG_FILE
fi

if [[ "" != "$CACHE_SERVICE_KEY" ]]; then
  export CACHE_SERVICE_KEY; yq -i '.ciExecutionServiceConfig.cacheIntelligenceConfig.serviceKey=env(CACHE_SERVICE_KEY)' $CONFIG_FILE
fi

if [[ "" != "$HOSTED_VM_SPLIT_LINUX_AMD64_POOL" ]]; then
  export HOSTED_VM_SPLIT_LINUX_AMD64_POOL; yq -i '.ciExecutionServiceConfig.hostedVmConfig.splitLinuxAmd64Pool=env(HOSTED_VM_SPLIT_LINUX_AMD64_POOL)' $CONFIG_FILE
fi

if [[ "" != "$HOSTED_VM_SPLIT_LINUX_ARM64_POOL" ]]; then
  export HOSTED_VM_SPLIT_LINUX_ARM64_POOL; yq -i '.ciExecutionServiceConfig.hostedVmConfig.splitLinuxArm64Pool=env(HOSTED_VM_SPLIT_LINUX_ARM64_POOL)' $CONFIG_FILE
fi

if [[ "" != "$HOSTED_VM_SPLIT_WINDOWS_AMD64_POOL" ]]; then
  export HOSTED_VM_SPLIT_WINDOWS_AMD64_POOL; yq -i '.ciExecutionServiceConfig.hostedVmConfig.splitWindowsAmd64Pool=env(HOSTED_VM_SPLIT_WINDOWS_AMD64_POOL)' $CONFIG_FILE
fi

if [[ "" != "$VM_ARTIFACTORY_UPLOAD_IMAGE" ]]; then
  export VM_ARTIFACTORY_UPLOAD_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.vmImageConfig.artifactoryUpload=env(VM_ARTIFACTORY_UPLOAD_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$VM_GCS_CACHE_IMAGE" ]]; then
  export VM_GCS_CACHE_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.vmImageConfig.cacheGCS=env(VM_GCS_CACHE_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$VM_S3_CACHE_IMAGE" ]]; then
  export VM_S3_CACHE_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.vmImageConfig.cacheS3=env(VM_S3_CACHE_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$DEFAULT_MEMORY_LIMIT" ]]; then
  export DEFAULT_MEMORY_LIMIT; yq -i '.ciExecutionServiceConfig.defaultMemoryLimit=env(DEFAULT_MEMORY_LIMIT)' $CONFIG_FILE
fi
if [[ "" != "$DEFAULT_CPU_LIMIT" ]]; then
  export DEFAULT_CPU_LIMIT; yq -i '.ciExecutionServiceConfig.defaultCPULimit=env(DEFAULT_CPU_LIMIT)' $CONFIG_FILE
fi
if [[ "" != "$DEFAULT_INTERNAL_IMAGE_CONNECTOR" ]]; then
  export DEFAULT_INTERNAL_IMAGE_CONNECTOR; yq -i '.ciExecutionServiceConfig.defaultInternalImageConnector=env(DEFAULT_INTERNAL_IMAGE_CONNECTOR)' $CONFIG_FILE
fi
if [[ "" != "$PVC_DEFAULT_STORAGE_SIZE" ]]; then
  export PVC_DEFAULT_STORAGE_SIZE; yq -i '.ciExecutionServiceConfig.pvcDefaultStorageSize=env(PVC_DEFAULT_STORAGE_SIZE)' $CONFIG_FILE
fi
if [[ "" != "$DELEGATE_SERVICE_ENDPOINT_VARIABLE_VALUE" ]]; then
  export DELEGATE_SERVICE_ENDPOINT_VARIABLE_VALUE; yq -i '.ciExecutionServiceConfig.delegateServiceEndpointVariableValue=env(DELEGATE_SERVICE_ENDPOINT_VARIABLE_VALUE)' $CONFIG_FILE
fi
if [[ "" != "$MINING_GCS_PROJECT_ID" ]]; then
  export MINING_GCS_PROJECT_ID; yq -i '.ciExecutionServiceConfig.miningPatternConfig.projectId=env(MINING_GCS_PROJECT_ID)' $CONFIG_FILE
fi
if [[ "" != "$MINING_GCS_BUCKET_NAME" ]]; then
  export MINING_GCS_BUCKET_NAME; yq -i '.ciExecutionServiceConfig.miningPatternConfig.bucketName=env(MINING_GCS_BUCKET_NAME)' $CONFIG_FILE
fi
if [[ "" != "$MINING_GCS_CREDS" ]]; then
  export MINING_GCS_CREDS; yq -i '.ciExecutionServiceConfig.miningPatternConfig.gcsCreds=env(MINING_GCS_CREDS)' $CONFIG_FILE
fi
if [[ "" != "$SERVER_MAX_THREADS" ]]; then
  export SERVER_MAX_THREADS; yq -i '.server.maxThreads=env(SERVER_MAX_THREADS)' $CONFIG_FILE
fi

if [[ "" != "$ALLOWED_ORIGINS" ]]; then
  yq -i 'del(.allowedOrigins)' $CONFIG_FILE
  export ALLOWED_ORIGINS; yq -i '.allowedOrigins=env(ALLOWED_ORIGINS)' $CONFIG_FILE
fi

if [[ "" != "$MONGO_URI" ]]; then
  export MONGO_URI=${MONGO_URI//\\&/&}; yq -i '.harness-mongo.uri=env(MONGO_URI)' $CONFIG_FILE
fi

if [[ "" != "$MANAGER_TARGET" ]]; then
  export MANAGER_TARGET; yq -i '.managerTarget=env(MANAGER_TARGET)' $CONFIG_FILE
fi

if [[ "" != "$MANAGER_AUTHORITY" ]]; then
  export MANAGER_AUTHORITY; yq -i '.managerAuthority=env(MANAGER_AUTHORITY)' $CONFIG_FILE
fi

if [[ "" != "$CIMANAGER_MONGO_URI" ]]; then
  export CIMANAGER_MONGO_URI; yq -i '.cimanager-mongo.uri=env(CIMANAGER_MONGO_URI)' $CONFIG_FILE
fi

if [[ "" != "$SCM_SERVICE_URI" ]]; then
  export SCM_SERVICE_URI; yq -i '.scmConnectionConfig.url=env(SCM_SERVICE_URI)' $CONFIG_FILE
fi

if [[ "" != "$LOG_SERVICE_ENDPOINT" ]]; then
  export LOG_SERVICE_ENDPOINT; yq -i '.logServiceConfig.baseUrl=env(LOG_SERVICE_ENDPOINT)' $CONFIG_FILE
fi

if [[ "" != "$LOG_SERVICE_GLOBAL_TOKEN" ]]; then
  export LOG_SERVICE_GLOBAL_TOKEN; yq -i '.logServiceConfig.globalToken=env(LOG_SERVICE_GLOBAL_TOKEN)' $CONFIG_FILE
fi

if [[ "" != "$LOG_SERVICE_INTERNAL_URL" ]]; then
  export LOG_SERVICE_INTERNAL_URL; yq -i '.logServiceConfig.internalUrl=env(LOG_SERVICE_INTERNAL_URL)' $CONFIG_FILE
fi

if [[ "" != "$TI_SERVICE_ENDPOINT" ]]; then
  export TI_SERVICE_ENDPOINT; yq -i '.tiServiceConfig.baseUrl=env(TI_SERVICE_ENDPOINT)' $CONFIG_FILE
fi

if [[ "" != "$TI_SERVICE_INTERNAL_URL" ]]; then
  export TI_SERVICE_INTERNAL_URL; yq -i '.tiServiceConfig.internalUrl=env(TI_SERVICE_INTERNAL_URL)' $CONFIG_FILE
fi

if [[ "" != "$STO_SERVICE_ENDPOINT" ]]; then
  export STO_SERVICE_ENDPOINT; yq -i '.stoServiceConfig.baseUrl=env(STO_SERVICE_ENDPOINT)' $CONFIG_FILE
fi

if [[ "" != "$IACM_SERVICE_ENDPOINT" ]]; then
  export $IACM_SERVICE_ENDPOINT; yq -i '.iacmServiceConfig.baseUrl=env(IACM_SERVICE_ENDPOINT)' $CONFIG_FILE
fi

if [[ "" != "$API_URL" ]]; then
  export API_URL; yq -i '.apiUrl=env(API_URL)' $CONFIG_FILE
fi

if [[ "" != "$PMS_TARGET" ]]; then
  export PMS_TARGET; yq -i '.pmsGrpcClientConfig.target=env(PMS_TARGET)' $CONFIG_FILE
fi

if [[ "" != "$PMS_AUTHORITY" ]]; then
  export PMS_AUTHORITY; yq -i '.pmsGrpcClientConfig.authority=env(PMS_AUTHORITY)' $CONFIG_FILE
fi

if [[ "" != "$SHOULD_CONFIGURE_WITH_PMS" ]]; then
  export SHOULD_CONFIGURE_WITH_PMS; yq -i '.shouldConfigureWithPMS=env(SHOULD_CONFIGURE_WITH_PMS)' $CONFIG_FILE
fi

if [[ "" != "$PMS_MONGO_URI" ]]; then
  export PMS_MONGO_URI=${PMS_MONGO_URI//\\&/&}; yq -i '.pmsMongo.uri=env(PMS_MONGO_URI)' $CONFIG_FILE
fi

if [[ "" != "$GRPC_SERVER_PORT" ]]; then
  export GRPC_SERVER_PORT; yq -i '.pmsSdkGrpcServerConfig.connectors[0].port=env(GRPC_SERVER_PORT)' $CONFIG_FILE
fi

if [[ "" != "$TI_SERVICE_GLOBAL_TOKEN" ]]; then
  export TI_SERVICE_GLOBAL_TOKEN; yq -i '.tiServiceConfig.globalToken=env(TI_SERVICE_GLOBAL_TOKEN)' $CONFIG_FILE
fi

if [[ "" != "$STO_SERVICE_GLOBAL_TOKEN" ]]; then
  export STO_SERVICE_GLOBAL_TOKEN; yq -i '.stoServiceConfig.globalToken=env(STO_SERVICE_GLOBAL_TOKEN)' $CONFIG_FILE
fi

if [[ "" != "$IACM_SERVICE_GLOBAL_TOKEN" ]]; then
  export IACM_SERVICE_GLOBAL_TOKEN; yq -i '.iacmServiceConfig.globalToken=env(IACM_SERVICE_GLOBAL_TOKEN)' $CONFIG_FILE
fi

if [[ "" != "$NEXT_GEN_MANAGER_SECRET" ]]; then
  export NEXT_GEN_MANAGER_SECRET; yq -i '.ngManagerServiceSecret=env(NEXT_GEN_MANAGER_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$JWT_AUTH_SECRET" ]]; then
  export JWT_AUTH_SECRET; yq -i '.jwtAuthSecret=env(JWT_AUTH_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$JWT_IDENTITY_SERVICE_SECRET" ]]; then
  export JWT_IDENTITY_SERVICE_SECRET; yq -i '.jwtIdentityServiceSecret=env(JWT_IDENTITY_SERVICE_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$API_URL" ]]; then
  export API_URL; yq -i '.apiUrl=env(API_URL)' $CONFIG_FILE
fi

if [[ "" != "$TIMESCALE_PASSWORD" ]]; then
  export TIMESCALE_PASSWORD; yq -i '.timescaledb.timescaledbPassword=env(TIMESCALE_PASSWORD)' $CONFIG_FILE
fi

if [[ "" != "$TIMESCALE_URI" ]]; then
  export TIMESCALE_URI; yq -i '.timescaledb.timescaledbUrl=env(TIMESCALE_URI)' $CONFIG_FILE
fi

if [[ "" != "$TIMESCALEDB_USERNAME" ]]; then
  export TIMESCALEDB_USERNAME; yq -i '.timescaledb.timescaledbUsername=env(TIMESCALEDB_USERNAME)' $CONFIG_FILE
fi

if [[ "" != "$TIMESCALEDB_SSL_MODE" ]]; then
  export TIMESCALEDB_SSL_MODE; yq -i '.timescaledb.sslMode=env(TIMESCALEDB_SSL_MODE)' $CONFIG_FILE
fi

if [[ "" != "$TIMESCALEDB_SSL_ROOT_CERT" ]]; then
  export TIMESCALEDB_SSL_ROOT_CERT; yq -i '.timescaledb.sslRootCert=env(TIMESCALEDB_SSL_ROOT_CERT)' $CONFIG_FILE
fi

if [[ "" != "$ENABLE_DASHBOARD_TIMESCALE" ]]; then
  export ENABLE_DASHBOARD_TIMESCALE; yq -i '.enableDashboardTimescale=env(ENABLE_DASHBOARD_TIMESCALE)' $CONFIG_FILE
fi

if [[ "" != "$DISTRIBUTED_LOCK_IMPLEMENTATION" ]]; then
  export DISTRIBUTED_LOCK_IMPLEMENTATION; yq -i '.distributedLockImplementation=env(DISTRIBUTED_LOCK_IMPLEMENTATION)' $CONFIG_FILE
fi


if [[ "" != "$MANAGER_SECRET" ]]; then
  export MANAGER_SECRET; yq -i '.managerServiceSecret=env(MANAGER_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$MONGO_INDEX_MANAGER_MODE" ]]; then
  export MONGO_INDEX_MANAGER_MODE; yq -i '.cimanager-mongo.indexManagerMode=env(MONGO_INDEX_MANAGER_MODE)' $CONFIG_FILE
fi

if [[ "$STACK_DRIVER_LOGGING_ENABLED" == "true" ]]; then
  yq -i 'del(.logging.appenders.[] | select(.type == "console"))' $CONFIG_FILE
  yq -i '(.logging.appenders.[] | select(.type == "gke-console") | .stackdriverLogEnabled) = true' $CONFIG_FILE
else
  yq -i 'del(.logging.appenders.[] | select(.type == "gke-console"))' $CONFIG_FILE
fi

replace_key_value accessControlClient.enableAccessControl "$ACCESS_CONTROL_ENABLED"

replace_key_value accessControlClient.accessControlServiceConfig.baseUrl "$ACCESS_CONTROL_BASE_URL"

replace_key_value accessControlClient.accessControlServiceSecret "$ACCESS_CONTROL_SECRET"

if [[ "" != "$EVENTS_FRAMEWORK_REDIS_SENTINELS" ]]; then
  IFS=',' read -ra SENTINEL_URLS <<< "$EVENTS_FRAMEWORK_REDIS_SENTINELS"
  INDEX=0
  for REDIS_SENTINEL_URL in "${SENTINEL_URLS[@]}"; do
    export REDIS_SENTINEL_URL; export INDEX; yq -i '.eventsFramework.redis.sentinelUrls.[env(INDEX)]=env(REDIS_SENTINEL_URL)' $CONFIG_FILE
    INDEX=$(expr $INDEX + 1)
  done
fi

yq -i 'del(.codec)' $REDISSON_CACHE_FILE

if [[ "$REDIS_SCRIPT_CACHE" == "false" ]]; then
  yq -i '.useScriptCache=false' $REDISSON_CACHE_FILE
fi


if [[ "" != "$CACHE_CONFIG_REDIS_URL" ]]; then
  export CACHE_CONFIG_REDIS_URL; yq -i '.singleServerConfig.address=env(CACHE_CONFIG_REDIS_URL)' $REDISSON_CACHE_FILE
fi

if [[ "$CACHE_CONFIG_USE_SENTINEL" == "true" ]]; then
  yq -i 'del(.singleServerConfig)' $REDISSON_CACHE_FILE
fi

if [[ "" != "$CACHE_CONFIG_SENTINEL_MASTER_NAME" ]]; then
  export CACHE_CONFIG_SENTINEL_MASTER_NAME; yq -i '.sentinelServersConfig.masterName=env(CACHE_CONFIG_SENTINEL_MASTER_NAME)' $REDISSON_CACHE_FILE
fi

if [[ "" != "$CACHE_CONFIG_REDIS_SENTINELS" ]]; then
  IFS=',' read -ra SENTINEL_URLS <<< "$CACHE_CONFIG_REDIS_SENTINELS"
  INDEX=0
  for REDIS_SENTINEL_URL in "${SENTINEL_URLS[@]}"; do
    export REDIS_SENTINEL_URL; export INDEX; yq -i '.sentinelServersConfig.sentinelAddresses.[env(INDEX)]=env(REDIS_SENTINEL_URL)' $REDISSON_CACHE_FILE
    INDEX=$(expr $INDEX + 1)
  done
fi

if [[ "" != "$REDIS_NETTY_THREADS" ]]; then
  export REDIS_NETTY_THREADS; yq -i '.nettyThreads=env(REDIS_NETTY_THREADS)' $REDISSON_CACHE_FILE
fi

if [[ "" != "$REDIS_CONNECTION_POOL_SIZE" ]]; then
  export REDIS_CONNECTION_POOL_SIZE; yq -i '.singleServerConfig.connectionPoolSize=env(REDIS_CONNECTION_POOL_SIZE)' $REDISSON_CACHE_FILE
fi

if [[ "" != "$REDIS_RETRY_INTERVAL" ]]; then
  export REDIS_RETRY_INTERVAL; yq -i '.singleServerConfig.retryInterval=env(REDIS_RETRY_INTERVAL)' $REDISSON_CACHE_FILE
fi

if [[ "" != "$REDIS_RETRY_ATTEMPTS" ]]; then
  export REDIS_RETRY_ATTEMPTS; yq -i '.singleServerConfig.retryAttempts=env(REDIS_RETRY_ATTEMPTS)' $REDISSON_CACHE_FILE
fi

if [[ "" != "$REDIS_TIMEOUT" ]]; then
  export REDIS_TIMEOUT; yq -i '.singleServerConfig.timeout=env(REDIS_TIMEOUT)' $REDISSON_CACHE_FILE
fi

yq -i 'del(.codec)' $ENTERPRISE_REDISSON_CACHE_FILE

if [[ "$REDIS_SCRIPT_CACHE" == "false" ]]; then
  yq -i '.useScriptCache=false' $ENTERPRISE_REDISSON_CACHE_FILE
fi

if [[ "" != "$EVENTS_FRAMEWORK_NETTY_THREADS" ]]; then
  export EVENTS_FRAMEWORK_NETTY_THREADS; yq -i '.nettyThreads=env(EVENTS_FRAMEWORK_NETTY_THREADS)' $ENTERPRISE_REDISSON_CACHE_FILE
fi

if [[ "" != "$EVENTS_FRAMEWORK_REDIS_URL" ]]; then
  export EVENTS_FRAMEWORK_REDIS_URL; yq -i '.singleServerConfig.address=env(EVENTS_FRAMEWORK_REDIS_URL)' $ENTERPRISE_REDISSON_CACHE_FILE
fi

if [[ "" != "$EVENTS_FRAMEWORK_REDIS_USERNAME" ]]; then
  export EVENTS_FRAMEWORK_REDIS_USERNAME; yq -i '.singleServerConfig.username=env(EVENTS_FRAMEWORK_REDIS_USERNAME)' $ENTERPRISE_REDISSON_CACHE_FILE
fi

if [[ "" != "$EVENTS_FRAMEWORK_REDIS_PASSWORD" ]]; then
  export EVENTS_FRAMEWORK_REDIS_PASSWORD; yq -i '.singleServerConfig.password=env(EVENTS_FRAMEWORK_REDIS_PASSWORD)' $ENTERPRISE_REDISSON_CACHE_FILE
fi

if [[ "" != "$EVENTS_FRAMEWORK_REDIS_SSL_CA_TRUST_STORE_PATH" ]]; then
  export FILE_VAR="file:$EVENTS_FRAMEWORK_REDIS_SSL_CA_TRUST_STORE_PATH"; yq -i '.singleServerConfig.sslTruststore=env(FILE_VAR)' $ENTERPRISE_REDISSON_CACHE_FILE
fi

if [[ "" != "$EVENTS_FRAMEWORK_REDIS_SSL_CA_TRUST_STORE_PASSWORD" ]]; then
  export EVENTS_FRAMEWORK_REDIS_SSL_CA_TRUST_STORE_PASSWORD; yq -i '.singleServerConfig.sslTruststorePassword=env(EVENTS_FRAMEWORK_REDIS_SSL_CA_TRUST_STORE_PASSWORD)' $ENTERPRISE_REDISSON_CACHE_FILE
fi

if [[ "$EVENTS_FRAMEWORK_USE_SENTINEL" == "true" ]]; then
  yq -i 'del(.singleServerConfig)' $ENTERPRISE_REDISSON_CACHE_FILE

  if [[ "" != "$EVENTS_FRAMEWORK_SENTINEL_MASTER_NAME" ]]; then
    export EVENTS_FRAMEWORK_SENTINEL_MASTER_NAME; yq -i '.sentinelServersConfig.masterName=env(EVENTS_FRAMEWORK_SENTINEL_MASTER_NAME)' $ENTERPRISE_REDISSON_CACHE_FILE
  fi

  if [[ "" != "$EVENTS_FRAMEWORK_REDIS_SENTINELS" ]]; then
    IFS=',' read -ra SENTINEL_URLS <<< "$EVENTS_FRAMEWORK_REDIS_SENTINELS"
    INDEX=0
    for REDIS_SENTINEL_URL in "${SENTINEL_URLS[@]}"; do
      export REDIS_SENTINEL_URL; export INDEX; yq -i '.sentinelServersConfig.sentinelAddresses.[env(INDEX)]=env(REDIS_SENTINEL_URL)' $ENTERPRISE_REDISSON_CACHE_FILE
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
replace_key_value eventsFramework.redis.retryAttempts $REDIS_RETRY_ATTEMPTS
replace_key_value eventsFramework.redis.retryInterval $REDIS_RETRY_INTERVAL

if [[ "" != "$LOCK_CONFIG_REDIS_SENTINELS" ]]; then
  IFS=',' read -ra SENTINEL_URLS <<< "$LOCK_CONFIG_REDIS_SENTINELS"
  INDEX=0
  for REDIS_SENTINEL_URL in "${SENTINEL_URLS[@]}"; do
    export REDIS_SENTINEL_URL; export INDEX; yq -i '.redisLockConfig.sentinelUrls.[env(INDEX)]=env(REDIS_SENTINEL_URL)' $CONFIG_FILE
    INDEX=$(expr $INDEX + 1)
  done
fi

if [[ "" != "$HSQS_BASE_URL" ]]; then
  export HSQS_BASE_URL; yq -i '.ciExecutionServiceConfig.queueServiceClientConfig.httpClientConfig.baseUrl=env(HSQS_BASE_URL)' $CONFIG_FILE
fi

if [[ "" != "$HSQS_AUTH_TOKEN" ]]; then
  export HSQS_AUTH_TOKEN; yq -i '.ciExecutionServiceConfig.queueServiceClientConfig.queueServiceSecret=env(HSQS_AUTH_TOKEN)' $CONFIG_FILE
fi

if [[ "" != "$OVERRIDE_EXEC_LIMIT_FOR_ACCOUNT" ]]; then
  export OVERRIDE_EXEC_LIMIT_FOR_ACCOUNT; yq -i '.ciExecutionServiceConfig.executionLimits.overrideConfig[0]=env(OVERRIDE_EXEC_LIMIT_FOR_ACCOUNT)' $CONFIG_FILE
fi

replace_key_value redisLockConfig.redisUrl "$LOCK_CONFIG_REDIS_URL"
replace_key_value redisLockConfig.envNamespace "$LOCK_CONFIG_ENV_NAMESPACE"
replace_key_value redisLockConfig.sentinel "$LOCK_CONFIG_USE_SENTINEL"
replace_key_value redisLockConfig.masterName "$LOCK_CONFIG_SENTINEL_MASTER_NAME"
replace_key_value redisLockConfig.userName "$LOCK_CONFIG_REDIS_USERNAME"
replace_key_value redisLockConfig.password "$LOCK_CONFIG_REDIS_PASSWORD"
replace_key_value redisLockConfig.nettyThreads "$REDIS_NETTY_THREADS"
replace_key_value redisLockConfig.connectionPoolSize $REDIS_CONNECTION_POOL_SIZE
replace_key_value redisLockConfig.retryInterval $REDIS_RETRY_INTERVAL
replace_key_value redisLockConfig.retryAttempts $REDIS_RETRY_ATTEMPTS
replace_key_value redisLockConfig.timeout $REDIS_TIMEOUT

replace_key_value enableOpentelemetry "$ENABLE_OPENTELEMETRY"
replace_key_value enforcementClientConfiguration.enforcementCheckEnabled "$ENFORCEMENT_CHECK_ENABLED"
