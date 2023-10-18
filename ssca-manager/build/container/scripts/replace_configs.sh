# Copyright 2023 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

CONFIG_FILE=/opt/harness/ssca-manager-config.yml

replace_key_value () {
  CONFIG_KEY="$1";
  CONFIG_VALUE="$2";
  if [[ "" != "$CONFIG_VALUE" ]]; then
    export CONFIG_VALUE; export CONFIG_KEY; export CONFIG_KEY=.$CONFIG_KEY; yq -i 'eval(strenv(CONFIG_KEY))=env(CONFIG_VALUE)' $CONFIG_FILE
  fi
}

yq -i '.server.adminConnectors=[]' $CONFIG_FILE

if [[ "" != "$LOGGING_LEVEL" ]]; then
    export LOGGING_LEVEL; yq -i '.logging.level=env(LOGGING_LEVEL)' $CONFIG_FILE
fi

if [[ "$STACK_DRIVER_LOGGING_ENABLED" == "true" ]]; then
  yq -i 'del(.logging.appenders.[] | select(.type == "console"))' $CONFIG_FILE
  yq -i '(.logging.appenders.[] | select(.type == "gke-console") | .stackdriverLogEnabled) = true' $CONFIG_FILE
else
  yq -i 'del(.logging.appenders.[] | select(.type == "gke-console"))' $CONFIG_FILE
fi

if [[ "" != "$STATIC_SCHEMA_FILE_URL" ]]; then
  export STATIC_SCHEMA_FILE_URL; yq -i '.staticSchemaFileURL=env(STATIC_SCHEMA_FILE_URL)' $CONFIG_FILE
fi

if [[ "" != "$SERVER_PORT" ]]; then
  export SERVER_PORT; yq -i '.server.applicationConnectors[0].port=env(SERVER_PORT)' $CONFIG_FILE
else
  yq -i '.server.applicationConnectors[0].port=8188' $CONFIG_FILE
fi

if [[ "" != "$LOGGERS" ]]; then
  IFS=',' read -ra LOGGER_ITEMS <<< "$LOGGERS"
  for ITEM in "${LOGGER_ITEMS[@]}"; do
    LOGGER=`echo $ITEM | awk -F= '{print $1}'`
    LOGGER_LEVEL=`echo $ITEM | awk -F= '{print $2}'`
    export LOGGER_LEVEL; export LOGGER; yq -i '.logging.loggers.[env(LOGGER)]=env(LOGGER_LEVEL)' $CONFIG_FILE
  done
fi

if [[ "" != "$MONGO_URI" ]]; then
  export MONGO_URI=${MONGO_URI//\\&/&}; yq -i '.mongo.uri=env(MONGO_URI)' $CONFIG_FILE
fi

if [[ "" != "$MONGO_TRACE_MODE" ]]; then
  export MONGO_TRACE_MODE; yq -i '.mongo.traceMode=env(MONGO_TRACE_MODE)' $CONFIG_FILE
fi

if [[ "" != "$MONGO_CONNECT_TIMEOUT" ]]; then
  export MONGO_CONNECT_TIMEOUT; yq -i '.mongo.connectTimeout=env(MONGO_CONNECT_TIMEOUT)' $CONFIG_FILE
fi

if [[ "" != "$MONGO_SERVER_SELECTION_TIMEOUT" ]]; then
  export MONGO_SERVER_SELECTION_TIMEOUT; yq -i '.mongo.serverSelectionTimeout=env(MONGO_SERVER_SELECTION_TIMEOUT)' $CONFIG_FILE
fi

if [[ "" != "$MONGO_SOCKET_TIMEOUT" ]]; then
  export MONGO_SOCKET_TIMEOUT; yq -i '.mongo.socketTimeout=env(MONGO_SOCKET_TIMEOUT)' $CONFIG_FILE
fi

if [[ "" != "$MAX_CONNECTION_IDLE_TIME" ]]; then
  export MAX_CONNECTION_IDLE_TIME; yq -i '.mongo.maxConnectionIdleTime=env(MAX_CONNECTION_IDLE_TIME)' $CONFIG_FILE
fi

if [[ "" != "$MONGO_CONNECTIONS_PER_HOST" ]]; then
  export MONGO_CONNECTIONS_PER_HOST; yq -i '.mongo.connectionsPerHost=env(MONGO_CONNECTIONS_PER_HOST)' $CONFIG_FILE
fi

if [[ "" != "$MONGO_INDEX_MANAGER_MODE" ]]; then
  export MONGO_INDEX_MANAGER_MODE; yq -i '.mongo.indexManagerMode=env(MONGO_INDEX_MANAGER_MODE)' $CONFIG_FILE
fi

if [[ "" != "$MONGO_TRANSACTIONS_ALLOWED" ]]; then
  export MONGO_TRANSACTIONS_ALLOWED; yq -i '.mongo.transactionsEnabled=env(MONGO_TRANSACTIONS_ALLOWED)' $CONFIG_FILE
fi

if [[ "" != "$NG_MANAGER_SERVICE_SECRET" ]]; then
  export NG_MANAGER_SERVICE_SECRET; yq -i '.ngManagerServiceSecret=env(NG_MANAGER_SERVICE_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$SSCA_MANAGER_SERVICE_SECRET" ]]; then
  export SSCA_MANAGER_SERVICE_SECRET; yq -i '.sscaManagerServiceSecret=env(SSCA_MANAGER_SERVICE_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$JWT_AUTH_SECRET" ]]; then
  export JWT_AUTH_SECRET; yq -i '.jwtAuthSecret=env(JWT_AUTH_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$JWT_IDENTITY_SERVICE_SECRET" ]]; then
  export JWT_IDENTITY_SERVICE_SECRET; yq -i '.jwtIdentityServiceSecret=env(JWT_IDENTITY_SERVICE_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$NG_MANAGER_BASE_URL" ]]; then
  export NG_MANAGER_BASE_URL; yq -i '.ngManagerServiceHttpClientConfig.baseUrl=env(NG_MANAGER_BASE_URL)' $CONFIG_FILE
fi

if [[ "" != "$S3_REGION" ]]; then
  export S3_REGION; yq -i '.s3Config.region=env(S3_REGION)' $CONFIG_FILE
fi

if [[ "" != "$S3_BUCKET" ]]; then
  export S3_BUCKET; yq -i '.s3Config.bucket=env(S3_BUCKET)' $CONFIG_FILE
fi

if [[ "" != "$S3_ENDPOINT" ]]; then
  export S3_ENDPOINT; yq -i '.s3Config.endpoint=env(S3_ENDPOINT)' $CONFIG_FILE
fi

if [[ "" != "$S3_POLICY_BUCKET" ]]; then
  export S3_POLICY_BUCKET; yq -i '.s3Config.policyBucket=env(S3_POLICY_BUCKET)' $CONFIG_FILE
fi

if [[ "" != "$S3_ACCESS_KEY_ID" ]]; then
  export S3_ACCESS_KEY_ID; yq -i '.s3Config.accessKeyId=env(S3_ACCESS_KEY_ID)' $CONFIG_FILE
fi

if [[ "" != "$S3_ACCESS_SECRET_KEY" ]]; then
  export S3_ACCESS_SECRET_KEY; yq -i '.s3Config.accessSecretKey=env(S3_ACCESS_SECRET_KEY)' $CONFIG_FILE
fi

if [[ "" != "$DISTRIBUTED_LOCK_IMPLEMENTATION" ]]; then
  export DISTRIBUTED_LOCK_IMPLEMENTATION; yq -i '.distributedLockImplementation=env(DISTRIBUTED_LOCK_IMPLEMENTATION)' $CONFIG_FILE
fi

if [[ "" != "$DEBEZIUM_CONSUMER_TOPIC_NAME" ]]; then
  export DEBEZIUM_CONSUMER_TOPIC_NAME; yq -i '.debeziumConsumerConfigs.instanceNGConsumer.topic=env(DEBEZIUM_CONSUMER_TOPIC_NAME)' $CONFIG_FILE
fi

if [[ "" != "$DEBEZIUM_CONSUMER_THREADS" ]]; then
  export DEBEZIUM_CONSUMER_THREADS; yq -i '.debeziumConsumerConfigs.instanceNGConsumer.threads=env(DEBEZIUM_CONSUMER_THREADS)' $CONFIG_FILE
fi

if [[ "" != "$DEBEZIUM_CONSUMER_BATCH_SIZE" ]]; then
  export DEBEZIUM_CONSUMER_BATCH_SIZE; yq -i '.debeziumConsumerConfigs.instanceNGConsumer.batchSize=env(DEBEZIUM_CONSUMER_BATCH_SIZE)' $CONFIG_FILE
fi

if [[ "" != "$EVENTS_FRAMEWORK_REDIS_SENTINELS" ]]; then
  IFS=',' read -ra SENTINEL_URLS <<< "$EVENTS_FRAMEWORK_REDIS_SENTINELS"
  INDEX=0
  for REDIS_SENTINEL_URL in "${SENTINEL_URLS[@]}"; do
    export REDIS_SENTINEL_URL; export INDEX; yq -i '.eventsFramework.redis.sentinelUrls.[env(INDEX)]=env(REDIS_SENTINEL_URL)' $CONFIG_FILE
    INDEX=$(expr $INDEX + 1)
  done
fi

if [[ "" != "$LOCK_CONFIG_REDIS_SENTINELS" ]]; then
  IFS=',' read -ra SENTINEL_URLS <<< "$LOCK_CONFIG_REDIS_SENTINELS"
  INDEX=0
  for REDIS_SENTINEL_URL in "${SENTINEL_URLS[@]}"; do
    export REDIS_SENTINEL_URL; export INDEX; yq -i '.redisLockConfig.sentinelUrls.[env(INDEX)]=env(REDIS_SENTINEL_URL)' $CONFIG_FILE
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

replace_key_value redisLockConfig.redisUrl "$LOCK_CONFIG_REDIS_URL"
replace_key_value redisLockConfig.envNamespace "$LOCK_CONFIG_ENV_NAMESPACE"
replace_key_value redisLockConfig.sentinel "$LOCK_CONFIG_USE_SENTINEL"
replace_key_value redisLockConfig.masterName "$LOCK_CONFIG_SENTINEL_MASTER_NAME"
replace_key_value redisLockConfig.userName "$LOCK_CONFIG_REDIS_USERNAME"
replace_key_value redisLockConfig.password "$LOCK_CONFIG_REDIS_PASSWORD"
replace_key_value redisLockConfig.nettyThreads "$REDIS_NETTY_THREADS"

replace_key_value cacheConfig.cacheNamespace $CACHE_NAMESPACE
replace_key_value cacheConfig.cacheBackend $CACHE_BACKEND
replace_key_value cacheConfig.enterpriseCacheEnabled $ENTERPRISE_CACHE_ENABLED