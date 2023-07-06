#!/usr/bin/env bash
# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.


CONFIG_FILE=/opt/harness/batch-processing-config.yml

replace_key_value () {
  CONFIG_KEY="$1";
  CONFIG_VALUE="$2";
  if [[ "" != "$CONFIG_VALUE" ]]; then
    export CONFIG_VALUE; export CONFIG_KEY; export CONFIG_KEY=.$CONFIG_KEY; yq -i 'eval(strenv(CONFIG_KEY))=env(CONFIG_VALUE)' $CONFIG_FILE
  fi
}

if [[ "" != "$MONGO_URI" ]]; then
  export MONGO_URI; yq -i '.harness-mongo.uri=env(MONGO_URI)' $CONFIG_FILE
fi

if [[ "" != "$MONGO_READ_PREF_NAME" ]]; then
  export MONGO_READ_PREF_NAME; yq -i '.harness-mongo.readPref.name=env(MONGO_READ_PREF_NAME)' $CONFIG_FILE
fi

if [[ "" != "$MONGO_READ_PREF_TAGS" ]]; then
  IFS=',' read -ra TAG_ITEMS <<< "$MONGO_READ_PREF_TAGS"
  for ITEM in "${TAG_ITEMS[@]}"; do
    TAG_NAME=$(echo $ITEM | awk -F= '{print $1}')
    TAG_VALUE=$(echo $ITEM | awk -F= '{print $2}')
    export TAG_VALUE; export TAG_NAME; yq -i '.harness-mongo.readPref.tagSet.[env(TAG_NAME)]=env(TAG_VALUE)' $CONFIG_FILE
  done
fi

if [[ "" != "$MONGO_INDEX_MANAGER_MODE" ]]; then
  export MONGO_INDEX_MANAGER_MODE; yq -i '.harness-mongo.indexManagerMode=env(MONGO_INDEX_MANAGER_MODE)' $CONFIG_FILE
fi

if [[ "" != "$EVEMTS_MONGO_INDEX_MANAGER_MODE" ]]; then
  export EVEMTS_MONGO_INDEX_MANAGER_MODE; yq -i '.events-mongo.indexManagerMode=env(EVEMTS_MONGO_INDEX_MANAGER_MODE)' $CONFIG_FILE
fi

if [[ "" != "$MONGO_MAX_OPERATION_TIME_IN_MILLIS" ]]; then
  export MONGO_MAX_OPERATION_TIME_IN_MILLIS; yq -i '.events-mongo.maxOperationTimeInMillis=env(MONGO_MAX_OPERATION_TIME_IN_MILLIS)' $CONFIG_FILE
fi

if [[ "" != "$EVENTS_MONGO_URI" ]]; then
  export EVENTS_MONGO_URI; yq -i '.events-mongo.uri=env(EVENTS_MONGO_URI)' $CONFIG_FILE
fi

if [[ "" != "$TIMESCALEDB_URI" ]]; then
  export TIMESCALEDB_URI; yq -i '.timescaledb.timescaledbUrl=env(TIMESCALEDB_URI)' $CONFIG_FILE
fi

if [[ "" != "$TIMESCALEDB_USERNAME" ]]; then
  export TIMESCALEDB_USERNAME; yq -i '.timescaledb.timescaledbUsername=env(TIMESCALEDB_USERNAME)' $CONFIG_FILE
fi

if [[ "" != "$TIMESCALEDB_PASSWORD" ]]; then
  export TIMESCALEDB_PASSWORD; yq -i '.timescaledb.timescaledbPassword=env(TIMESCALEDB_PASSWORD)' $CONFIG_FILE
fi

if [[ "" != "$TIMESCALEDB_SSL_MODE" ]]; then
  export TIMESCALEDB_SSL_MODE; yq -i '.timescaledb.sslMode=env(TIMESCALEDB_SSL_MODE)' $CONFIG_FILE
fi

if [[ "" != "$TIMESCALEDB_SSL_ROOT_CERT" ]]; then
  export TIMESCALEDB_SSL_ROOT_CERT; yq -i '.timescaledb.sslRootCert=env(TIMESCALEDB_SSL_ROOT_CERT)' $CONFIG_FILE
fi

if [[ "" != "$S3_SYNC_CONFIG_BUCKET_NAME" ]]; then
  export S3_SYNC_CONFIG_BUCKET_NAME; yq -i '.awsS3SyncConfig.awsS3BucketName=env(S3_SYNC_CONFIG_BUCKET_NAME)' $CONFIG_FILE
fi

if [[ "" != "$QUERY_BATCH_SIZE" ]]; then
  export QUERY_BATCH_SIZE; yq -i '.batchQueryConfig.queryBatchSize=env(QUERY_BATCH_SIZE)' $CONFIG_FILE
fi

if [[ "" != "$BILLING_DATA_QUERY_BATCH_SIZE" ]]; then
  export BILLING_DATA_QUERY_BATCH_SIZE; yq -i '.batchQueryConfig.billingDataQueryBatchSize=env(BILLING_DATA_QUERY_BATCH_SIZE)' $CONFIG_FILE
fi

if [[ "" != "$BULK_OPERATION_QUERY_BATCH_SIZE" ]]; then
  export BULK_OPERATION_QUERY_BATCH_SIZE; yq -i '.bulkOperationBatchQueryConfig.queryBatchSize=env(BULK_OPERATION_QUERY_BATCH_SIZE)' $CONFIG_FILE
fi

if [[ "" != "$SYNC_JOB_DISABLED" ]]; then
  export SYNC_JOB_DISABLED; yq -i '.batchQueryConfig.syncJobDisabled=env(SYNC_JOB_DISABLED)' $CONFIG_FILE
fi

if [[ "" != "$S3_SYNC_CONFIG_ACCESSKEY" ]]; then
  export S3_SYNC_CONFIG_ACCESSKEY; yq -i '.awsS3SyncConfig.awsAccessKey=env(S3_SYNC_CONFIG_ACCESSKEY)' $CONFIG_FILE
fi

if [[ "" != "$S3_SYNC_CONFIG_SECRETKEY" ]]; then
  export S3_SYNC_CONFIG_SECRETKEY; yq -i '.awsS3SyncConfig.awsSecretKey=env(S3_SYNC_CONFIG_SECRETKEY)' $CONFIG_FILE
fi

if [[ "" != "$S3_SYNC_CONFIG_REGION" ]]; then
  export S3_SYNC_CONFIG_REGION; yq -i '.awsS3SyncConfig.region=env(S3_SYNC_CONFIG_REGION)' $CONFIG_FILE
fi

if [[ "" != "$DATA_PIPELINE_CONFIG_GCP_PROJECT_ID" ]]; then
  export DATA_PIPELINE_CONFIG_GCP_PROJECT_ID; yq -i '.billingDataPipelineConfig.gcpProjectId=env(DATA_PIPELINE_CONFIG_GCP_PROJECT_ID)' $CONFIG_FILE
fi

if [[ "" != "$DATA_PIPELINE_CONFIG_GCS_BASE_PATH" ]]; then
  export DATA_PIPELINE_CONFIG_GCS_BASE_PATH; yq -i '.billingDataPipelineConfig.gcsBasePath=env(DATA_PIPELINE_CONFIG_GCS_BASE_PATH)' $CONFIG_FILE
fi

if [[ "" != "$GCP_PIPELINE_PUB_SUB_TOPIC" ]]; then
  export GCP_PIPELINE_PUB_SUB_TOPIC; yq -i '.billingDataPipelineConfig.gcpPipelinePubSubTopic=env(GCP_PIPELINE_PUB_SUB_TOPIC)' $CONFIG_FILE
fi

if [[ "" != "$GCP_USE_NEW_PIPELINE" ]]; then
  export GCP_USE_NEW_PIPELINE; yq -i '.billingDataPipelineConfig.gcpUseNewPipeline=env(GCP_USE_NEW_PIPELINE)' $CONFIG_FILE
fi

if [[ "" != "$AWS_USE_NEW_PIPELINE" ]]; then
  export AWS_USE_NEW_PIPELINE; yq -i '.billingDataPipelineConfig.awsUseNewPipeline=env(AWS_USE_NEW_PIPELINE)' $CONFIG_FILE
fi

if [[ "" != "$GCP_SYNC_ENABLED" ]]; then
  export GCP_SYNC_ENABLED; yq -i '.billingDataPipelineConfig.isGcpSyncEnabled=env(GCP_SYNC_ENABLED)' $CONFIG_FILE
fi

if [[ "" != "$CLUSTER_DATA_GCS_BUCKET" ]]; then
  export CLUSTER_DATA_GCS_BUCKET; yq -i '.billingDataPipelineConfig.clusterDataGcsBucketName=env(CLUSTER_DATA_GCS_BUCKET)' $CONFIG_FILE
fi

if [[ "" != "$CLUSTER_DATA_GCS_BACKUP_BUCKET" ]]; then
  export CLUSTER_DATA_GCS_BACKUP_BUCKET; yq -i '.billingDataPipelineConfig.clusterDataGcsBackupBucketName=env(CLUSTER_DATA_GCS_BACKUP_BUCKET)' $CONFIG_FILE
fi

if [[ "" != "$AWS_ROLE_NAME" ]]; then
  export AWS_ROLE_NAME; yq -i '.billingDataPipelineConfig.awsRoleName=env(AWS_ROLE_NAME)' $CONFIG_FILE
fi

if [[ "" != "$BUFFER_SIZE_IN_MB" ]]; then
  export BUFFER_SIZE_IN_MB; yq -i '.billingDataPipelineConfig.bufferSizeInMB=env(BUFFER_SIZE_IN_MB)' $CONFIG_FILE
fi

if [[ "" != "$SMTP_HOST" ]]; then
  export SMTP_HOST; yq -i '.smtp.host=env(SMTP_HOST)' $CONFIG_FILE
fi

if [[ "" != "$SMTP_PORT" ]]; then
  export SMTP_PORT; yq -i '.smtp.port=env(SMTP_PORT)' $CONFIG_FILE
fi

if [[ "" != "$SMTP_USERNAME" ]]; then
  export SMTP_USERNAME; yq -i '.smtp.username=env(SMTP_USERNAME)' $CONFIG_FILE
fi

if [[ "" != "$SMTP_PASSWORD" ]]; then
  export SMTP_PASSWORD; yq -i '.smtp.password=env(SMTP_PASSWORD)' $CONFIG_FILE
fi

if [[ "" != "$SMTP_USE_SSL" ]]; then
  export SMTP_USE_SSL; yq -i '.smtp.useSSL=env(SMTP_USE_SSL)' $CONFIG_FILE
fi

if [[ "" != "$UI_SERVER_URL" ]]; then
  export UI_SERVER_URL; yq -i '.baseUrl=env(UI_SERVER_URL)' $CONFIG_FILE
fi

if [[ "" != "$SEGMENT_ENABLED" ]]; then
  export SEGMENT_ENABLED; yq -i '.segmentConfig.enabled=env(SEGMENT_ENABLED)' $CONFIG_FILE
fi

if [[ "" != "$SEGMENT_APIKEY" ]]; then
  export SEGMENT_APIKEY; yq -i '.segmentConfig.apiKey=env(SEGMENT_APIKEY)' $CONFIG_FILE
fi

if [[ "" != "$CF_API_KEY" ]]; then
  export CF_API_KEY; yq -i '.cfConfig.apiKey=env(CF_API_KEY)' $CONFIG_FILE
fi

if [[ "" != "$CF_BASE_URL" ]]; then
  export CF_BASE_URL; yq -i '.cfConfig.baseUrl=env(CF_BASE_URL)' $CONFIG_FILE
fi

if [[ "" != "$POD_NAME" ]]; then
  export POD_NAME; yq -i '.podInfo.name=env(POD_NAME)' $CONFIG_FILE
fi

if [[ "" != "$REPLICA" ]]; then
  export REPLICA; yq -i '.podInfo.replica=env(REPLICA)' $CONFIG_FILE
fi

if [[ "" != "$ISOLATED_REPLICA" ]]; then
  export ISOLATED_REPLICA; yq -i '.podInfo.isolatedReplica=env(ISOLATED_REPLICA)' $CONFIG_FILE
fi

if [[ "" != "$BUDGET_ALERTS_JOB_CRON" ]]; then
  export BUDGET_ALERTS_JOB_CRON; yq -i '.scheduler-jobs-config.budgetAlertsJobCron=env(BUDGET_ALERTS_JOB_CRON)' $CONFIG_FILE
fi

if [[ "" != "$DAILY_BUDGET_ALERTS_JOB_CRON" ]]; then
  export DAILY_BUDGET_ALERTS_JOB_CRON; yq -i '.scheduler-jobs-config.dailyBudgetAlertsJobCron=env(DAILY_BUDGET_ALERTS_JOB_CRON)' $CONFIG_FILE
fi

if [[ "" != "$BUDGET_COST_UPDATE_JOB_CRON" ]]; then
  export BUDGET_COST_UPDATE_JOB_CRON; yq -i '.scheduler-jobs-config.budgetCostUpdateJobCron=env(BUDGET_COST_UPDATE_JOB_CRON)' $CONFIG_FILE
fi

if [[ "" != "$DAILY_BUDGET_COST_UPDATE_JOB_CRON" ]]; then
  export DAILY_BUDGET_COST_UPDATE_JOB_CRON; yq -i '.scheduler-jobs-config.dailyBudgetCostUpdateJobCron=env(DAILY_BUDGET_COST_UPDATE_JOB_CRON)' $CONFIG_FILE
fi

if [[ "" != "$WEEKLY_REPORT_JOB_CRON" ]]; then
  export WEEKLY_REPORT_JOB_CRON; yq -i '.scheduler-jobs-config.weeklyReportsJobCron=env(WEEKLY_REPORT_JOB_CRON)' $CONFIG_FILE
fi

if [[ "" != "$CONNECTOR_HEALTH_UPDATE_CRON" ]]; then
  export CONNECTOR_HEALTH_UPDATE_CRON; yq -i '.scheduler-jobs-config.connectorHealthUpdateJobCron=env(CONNECTOR_HEALTH_UPDATE_CRON)' $CONFIG_FILE
fi

if [[ "" != "$AWS_ACCOUNT_TAGS_COLLECTION_CRON" ]]; then
  export AWS_ACCOUNT_TAGS_COLLECTION_CRON; yq -i '.scheduler-jobs-config.awsAccountTagsCollectionJobCron=env(AWS_ACCOUNT_TAGS_COLLECTION_CRON)' $CONFIG_FILE
fi

if [[ "" != "$GOVERNANCE_RECOMMENDATION_JOB_CRON" ]]; then
  export GOVERNANCE_RECOMMENDATION_JOB_CRON; yq -i '.scheduler-jobs-config.governanceRecommendationJobCronAws=env(GOVERNANCE_RECOMMENDATION_JOB_CRON)' $CONFIG_FILE
fi

if [[ "" != "$GOVERNANCE_RECOMMENDATION_JOB_CRON_AZURE" ]]; then
  export GOVERNANCE_RECOMMENDATION_JOB_CRON_AZURE; yq -i '.scheduler-jobs-config.governanceRecommendationJobCronAzure=env(GOVERNANCE_RECOMMENDATION_JOB_CRON_AZURE)' $CONFIG_FILE
fi

if [[ "" != "$GOVERNANCE_RECOMMENDATION_JOB_ENABLED" ]]; then
  export GOVERNANCE_RECOMMENDATION_JOB_ENABLED; yq -i '.recommendationConfig.governanceRecommendationEnabledAws=env(GOVERNANCE_RECOMMENDATION_JOB_ENABLED)' $CONFIG_FILE
fi

if [[ "" != "$GOVERNANCE_RECOMMENDATION_JOB_ENABLED_AZURE" ]]; then
  export GOVERNANCE_RECOMMENDATION_JOB_ENABLED_AZURE; yq -i '.recommendationConfig.governanceRecommendationEnabledAzure=env(GOVERNANCE_RECOMMENDATION_JOB_ENABLED_AZURE)' $CONFIG_FILE
fi

if [[ "" != "$HARNESS_CE_AZURE_CLIENTID" ]]; then
  export HARNESS_CE_AZURE_CLIENTID; yq -i '.azureStorageSyncConfig.azureAppClientId=env(HARNESS_CE_AZURE_CLIENTID)' $CONFIG_FILE
fi

if [[ "" != "$HARNESS_CE_AZURE_CLIENTSECRET" ]]; then
  export HARNESS_CE_AZURE_CLIENTSECRET; yq -i '.azureStorageSyncConfig.azureAppClientSecret=env(HARNESS_CE_AZURE_CLIENTSECRET)' $CONFIG_FILE
fi

if [[ "" != "$HARNESS_CE_AZURE_TENANTID" ]]; then
  export HARNESS_CE_AZURE_TENANTID; yq -i '.azureStorageSyncConfig.azureTenantId=env(HARNESS_CE_AZURE_TENANTID)' $CONFIG_FILE
fi

if [[ "" != "$HARNESS_CE_AZURE_CONTAINER_NAME" ]]; then
  export HARNESS_CE_AZURE_CONTAINER_NAME; yq -i '.azureStorageSyncConfig.azureStorageContainerName=env(HARNESS_CE_AZURE_CONTAINER_NAME)' $CONFIG_FILE
fi

if [[ "" != "$HARNESS_CE_AZURE_STORAGE_NAME" ]]; then
  export HARNESS_CE_AZURE_STORAGE_NAME; yq -i '.azureStorageSyncConfig.azureStorageAccountName=env(HARNESS_CE_AZURE_STORAGE_NAME)' $CONFIG_FILE
fi

if [[ "" != "$HARNESS_CE_AZURE_SAS" ]]; then
  export HARNESS_CE_AZURE_SAS; yq -i '.azureStorageSyncConfig.azureSasToken=env(HARNESS_CE_AZURE_SAS)' $CONFIG_FILE
fi

if [[ "" != "$HARNESS_CE_AZURE_IS_SYNC_JOB_DISABLED" ]]; then
  export HARNESS_CE_AZURE_IS_SYNC_JOB_DISABLED; yq -i '.azureStorageSyncConfig.syncJobDisabled=env(HARNESS_CE_AZURE_IS_SYNC_JOB_DISABLED)' $CONFIG_FILE
fi

if [[ "" != "$ANOMALY_DETECTION_PYTHON_SERVICE_URL" ]]; then
  export ANOMALY_DETECTION_PYTHON_SERVICE_URL; yq -i '.cePythonService.pythonServiceUrl=env(ANOMALY_DETECTION_PYTHON_SERVICE_URL)' $CONFIG_FILE
fi

if [[ "" != "$ANOMALY_DETECTION_USE_PROPHET" ]]; then
  export ANOMALY_DETECTION_USE_PROPHET; yq -i '.cePythonService.useProphet=env(ANOMALY_DETECTION_USE_PROPHET)' $CONFIG_FILE
fi

if [[ "" != "$BANZAI_CONFIG_HOST" ]]; then
  export BANZAI_CONFIG_HOST; yq -i '.banzaiConfig.host=env(BANZAI_CONFIG_HOST)' $CONFIG_FILE
fi

if [[ "" != "$BANZAI_CONFIG_PORT" ]]; then
  export BANZAI_CONFIG_PORT; yq -i '.banzaiConfig.port=env(BANZAI_CONFIG_PORT)' $CONFIG_FILE
fi

if [[ "" != "$NG_MANAGER_SERVICE_HTTP_CLIENT_CONFIG_BASE_URL" ]]; then
  export NG_MANAGER_SERVICE_HTTP_CLIENT_CONFIG_BASE_URL; yq -i '.ngManagerServiceHttpClientConfig.baseUrl=env(NG_MANAGER_SERVICE_HTTP_CLIENT_CONFIG_BASE_URL)' $CONFIG_FILE
fi

if [[ "" != "$NG_MANAGER_SERVICE_HTTP_CLIENT_CONFIG_CONNECT_TIMEOUT" ]]; then
  export NG_MANAGER_SERVICE_HTTP_CLIENT_CONFIG_CONNECT_TIMEOUT; yq -i '.ngManagerServiceHttpClientConfig.connectTimeOutSeconds=env(NG_MANAGER_SERVICE_HTTP_CLIENT_CONFIG_CONNECT_TIMEOUT)' $CONFIG_FILE
fi

if [[ "" != "$NG_MANAGER_SERVICE_HTTP_CLIENT_CONFIG_READ_TIMEOUT" ]]; then
  export NG_MANAGER_SERVICE_HTTP_CLIENT_CONFIG_READ_TIMEOUT; yq -i '.ngManagerServiceHttpClientConfig.readTimeOutSeconds=env(NG_MANAGER_SERVICE_HTTP_CLIENT_CONFIG_READ_TIMEOUT)' $CONFIG_FILE
fi

if [[ "" != "$NEXT_GEN_MANAGER_SECRET" ]]; then
  export NEXT_GEN_MANAGER_SECRET; yq -i '.ngManagerServiceSecret=env(NEXT_GEN_MANAGER_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$CE_NG_SERVICE_HTTP_CLIENT_CONFIG_BASE_URL" ]]; then
  export CE_NG_SERVICE_HTTP_CLIENT_CONFIG_BASE_URL; yq -i '.ceNgServiceHttpClientConfig.baseUrl=env(CE_NG_SERVICE_HTTP_CLIENT_CONFIG_BASE_URL)' $CONFIG_FILE
fi

if [[ "" != "$CE_NG_SERVICE_SECRET" ]]; then
  export CE_NG_SERVICE_SECRET; yq -i '.ceNgServiceSecret=env(CE_NG_SERVICE_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$CONNECTOR_HEALTH_UPDATE_JOB_ENABLED" ]]; then
  export CONNECTOR_HEALTH_UPDATE_JOB_ENABLED; yq -i '.connectorHealthUpdateJobConfig.enabled=env(CONNECTOR_HEALTH_UPDATE_JOB_ENABLED)' $CONFIG_FILE
fi

if [[ "" != "$AWS_ACCOUNT_TAGS_COLLECTION_JOB_ENABLED" ]]; then
  export AWS_ACCOUNT_TAGS_COLLECTION_JOB_ENABLED; yq -i '.awsAccountTagsCollectionJobConfig.enabled=env(AWS_ACCOUNT_TAGS_COLLECTION_JOB_ENABLED)' $CONFIG_FILE
fi

if [[ "" != "$GCP_BQ_UPDATE_BATCH_SUBSCRIPTION_NAME" ]]; then
  export GCP_BQ_UPDATE_BATCH_SUBSCRIPTION_NAME; yq -i '.gcpConfig.bigQueryUpdatePubSubTopic.subscriptionName=env(GCP_BQ_UPDATE_BATCH_SUBSCRIPTION_NAME)' $CONFIG_FILE
fi

if [[ "" != "$BATCH_JOB_REPOSITORY_TIMESCALE_ENABLE" ]]; then
  export BATCH_JOB_REPOSITORY_TIMESCALE_ENABLE; yq -i '.batchJobRepository.timescaleEnabled=env(BATCH_JOB_REPOSITORY_TIMESCALE_ENABLE)' $CONFIG_FILE
fi

replace_key_value cfClientConfig.apiKey "$CF_CLIENT_API_KEY"
replace_key_value cfClientConfig.configUrl "$CF_CLIENT_CONFIG_URL"
replace_key_value cfClientConfig.eventUrl "$CF_CLIENT_EVENT_URL"
replace_key_value cfClientConfig.analyticsEnabled "$CF_CLIENT_ANALYTICS_ENABLED"
replace_key_value cfClientConfig.connectionTimeout "$CF_CLIENT_CONNECTION_TIMEOUT"
replace_key_value cfClientConfig.readTimeout "$CF_CLIENT_READ_TIMEOUT"
replace_key_value cfClientConfig.bufferSize "$CF_CLIENT_BUFFER_SIZE"
replace_key_value cfClientConfig.retries "$CF_CLIENT_RETRIES"
replace_key_value cfClientConfig.sleepInterval "$CF_CLIENT_SLEEPINTERVAL"
replace_key_value featureFlagConfig.featureFlagSystem "$FEATURE_FLAG_SYSTEM"
replace_key_value featureFlagConfig.syncFeaturesToCF "$SYNC_FEATURES_TO_CF"

replace_key_value awsS3SyncConfig.awsS3SyncTimeoutMinutes "$AWS_S3_SYNC_TIMEOUT_MINUTES"

replace_key_value banzaiRecommenderConfig.baseUrl "$BANZAI_RECOMMENDER_BASEURL"
replace_key_value awsCurBilling "$AWS_CUR_BILLING"

replace_key_value gcpConfig.gcpProjectId "$GCP_PROJECT_ID"
replace_key_value gcpConfig.gcpAwsConnectorCrudPubSubTopic "$GCP_AWS_CONNECTOR_CRUD_PUBSUB_TOPIC"
replace_key_value gcpConfig.bigQueryUpdatePubSubTopic.enabled "$GCP_BQ_UPDATE_BATCH_ENABLED"

replace_key_value currencyPreferences.historicalUpdateMonthsCount "$CURRENCY_PREFERENCE_HISTORICAL_UPDATE_MONTHS_COUNT"
replace_key_value deployMode "$DEPLOY_MODE"

replace_key_value clickHouseConfig.url "$CLICKHOUSE_URL"
replace_key_value clickHouseConfig.username "$CLICKHOUSE_USERNAME"
replace_key_value clickHouseConfig.password "$CLICKHOUSE_PASSWORD"

replace_key_value isClickHouseEnabled "$CLICKHOUSE_ENABLED"
