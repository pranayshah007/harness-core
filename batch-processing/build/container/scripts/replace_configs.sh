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
    yq -i '.$CONFIG_KEY="$CONFIG_VALUE"' $CONFIG_FILE
  fi
}

if [[ "" != "$MONGO_URI" ]]; then
  yq -i '.harness-mongo.uri="$MONGO_URI"' $CONFIG_FILE
fi

if [[ "" != "$MONGO_READ_PREF_NAME" ]]; then
  yq -i '.harness-mongo.readPref.name="$MONGO_READ_PREF_NAME"' $CONFIG_FILE
fi

if [[ "" != "$MONGO_READ_PREF_TAGS" ]]; then
  IFS=',' read -ra TAG_ITEMS <<< "$MONGO_READ_PREF_TAGS"
  for ITEM in "${TAG_ITEMS[@]}"; do
    TAG_NAME=$(echo $ITEM | awk -F= '{print $1}')
    TAG_VALUE=$(echo $ITEM | awk -F= '{print $2}')
    yq -i '.harness-mongo.readPref.tagSet.[$TAG_NAME]="$TAG_VALUE"' $CONFIG_FILE
  done
fi

if [[ "" != "$MONGO_INDEX_MANAGER_MODE" ]]; then
  yq -i '.harness-mongo.indexManagerMode="$MONGO_INDEX_MANAGER_MODE"' $CONFIG_FILE
fi

if [[ "" != "$EVEMTS_MONGO_INDEX_MANAGER_MODE" ]]; then
  yq -i '.events-mongo.indexManagerMode="$EVEMTS_MONGO_INDEX_MANAGER_MODE"' $CONFIG_FILE
fi

if [[ "" != "$EVENTS_MONGO_URI" ]]; then
  yq -i '.events-mongo.uri="$EVENTS_MONGO_URI"' $CONFIG_FILE
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

if [[ "" != "$S3_SYNC_CONFIG_BUCKET_NAME" ]]; then
  yq -i '.awsS3SyncConfig.awsS3BucketName="$S3_SYNC_CONFIG_BUCKET_NAME"' $CONFIG_FILE
fi

if [[ "" != "$QUERY_BATCH_SIZE" ]]; then
  yq -i '.batchQueryConfig.queryBatchSize="$QUERY_BATCH_SIZE"' $CONFIG_FILE
fi

if [[ "" != "$SYNC_JOB_DISABLED" ]]; then
  yq -i '.batchQueryConfig.syncJobDisabled="$SYNC_JOB_DISABLED"' $CONFIG_FILE
fi

if [[ "" != "$S3_SYNC_CONFIG_ACCESSKEY" ]]; then
  yq -i '.awsS3SyncConfig.awsAccessKey="$S3_SYNC_CONFIG_ACCESSKEY"' $CONFIG_FILE
fi

if [[ "" != "$S3_SYNC_CONFIG_SECRETKEY" ]]; then
  yq -i '.awsS3SyncConfig.awsSecretKey="$S3_SYNC_CONFIG_SECRETKEY"' $CONFIG_FILE
fi

if [[ "" != "$S3_SYNC_CONFIG_REGION" ]]; then
  yq -i '.awsS3SyncConfig.region="$S3_SYNC_CONFIG_REGION"' $CONFIG_FILE
fi

if [[ "" != "$DATA_PIPELINE_CONFIG_GCP_PROJECT_ID" ]]; then
  yq -i '.billingDataPipelineConfig.gcpProjectId="$DATA_PIPELINE_CONFIG_GCP_PROJECT_ID"' $CONFIG_FILE
fi

if [[ "" != "$DATA_PIPELINE_CONFIG_GCS_BASE_PATH" ]]; then
  yq -i '.billingDataPipelineConfig.gcsBasePath="$DATA_PIPELINE_CONFIG_GCS_BASE_PATH"' $CONFIG_FILE
fi

if [[ "" != "$GCP_PIPELINE_PUB_SUB_TOPIC" ]]; then
  yq -i '.billingDataPipelineConfig.gcpPipelinePubSubTopic="$GCP_PIPELINE_PUB_SUB_TOPIC"' $CONFIG_FILE
fi

if [[ "" != "$GCP_USE_NEW_PIPELINE" ]]; then
  yq -i '.billingDataPipelineConfig.gcpUseNewPipeline="$GCP_USE_NEW_PIPELINE"' $CONFIG_FILE
fi

if [[ "" != "$AWS_USE_NEW_PIPELINE" ]]; then
  yq -i '.billingDataPipelineConfig.awsUseNewPipeline="$AWS_USE_NEW_PIPELINE"' $CONFIG_FILE
fi

if [[ "" != "$GCP_SYNC_ENABLED" ]]; then
  yq -i '.billingDataPipelineConfig.isGcpSyncEnabled="$GCP_SYNC_ENABLED"' $CONFIG_FILE
fi

if [[ "" != "$CLUSTER_DATA_GCS_BUCKET" ]]; then
  yq -i '.billingDataPipelineConfig.clusterDataGcsBucketName="$CLUSTER_DATA_GCS_BUCKET"' $CONFIG_FILE
fi

if [[ "" != "$CLUSTER_DATA_GCS_BACKUP_BUCKET" ]]; then
  yq -i '.billingDataPipelineConfig.clusterDataGcsBackupBucketName="$CLUSTER_DATA_GCS_BACKUP_BUCKET"' $CONFIG_FILE
fi

if [[ "" != "$AWS_ROLE_NAME" ]]; then
  yq -i '.billingDataPipelineConfig.awsRoleName="$AWS_ROLE_NAME"' $CONFIG_FILE
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

if [[ "" != "$UI_SERVER_URL" ]]; then
  yq -i '.baseUrl="$UI_SERVER_URL"' $CONFIG_FILE
fi

if [[ "" != "$SEGMENT_ENABLED" ]]; then
  yq -i '.segmentConfig.enabled="$SEGMENT_ENABLED"' $CONFIG_FILE
fi

if [[ "" != "$SEGMENT_APIKEY" ]]; then
  yq -i '.segmentConfig.apiKey="$SEGMENT_APIKEY"' $CONFIG_FILE
fi

if [[ "" != "$CF_API_KEY" ]]; then
  yq -i '.cfConfig.apiKey="$CF_API_KEY"' $CONFIG_FILE
fi

if [[ "" != "$CF_BASE_URL" ]]; then
  yq -i '.cfConfig.baseUrl="$CF_BASE_URL"' $CONFIG_FILE
fi

if [[ "" != "$POD_NAME" ]]; then
  yq -i '.podInfo.name="$POD_NAME"' $CONFIG_FILE
fi

if [[ "" != "$REPLICA" ]]; then
  yq -i '.podInfo.replica="$REPLICA"' $CONFIG_FILE
fi

if [[ "" != "$ISOLATED_REPLICA" ]]; then
  yq -i '.podInfo.isolatedReplica="$ISOLATED_REPLICA"' $CONFIG_FILE
fi

if [[ "" != "$BUDGET_ALERTS_JOB_CRON" ]]; then
  yq -i '.scheduler-jobs-config.budgetAlertsJobCron="$BUDGET_ALERTS_JOB_CRON"' $CONFIG_FILE
fi

if [[ "" != "$WEEKLY_REPORT_JOB_CRON" ]]; then
  yq -i '.scheduler-jobs-config.weeklyReportsJobCron="$WEEKLY_REPORT_JOB_CRON"' $CONFIG_FILE
fi

if [[ "" != "$CONNECTOR_HEALTH_UPDATE_CRON" ]]; then
  yq -i '.scheduler-jobs-config.connectorHealthUpdateJobCron="$CONNECTOR_HEALTH_UPDATE_CRON"' $CONFIG_FILE
fi

if [[ "" != "$AWS_ACCOUNT_TAGS_COLLECTION_CRON" ]]; then
  yq -i '.scheduler-jobs-config.awsAccountTagsCollectionJobCron="$AWS_ACCOUNT_TAGS_COLLECTION_CRON"' $CONFIG_FILE
fi

if [[ "" != "$HARNESS_CE_AZURE_CLIENTID" ]]; then
  yq -i '.azureStorageSyncConfig.azureAppClientId="$HARNESS_CE_AZURE_CLIENTID"' $CONFIG_FILE
fi

if [[ "" != "$HARNESS_CE_AZURE_CLIENTSECRET" ]]; then
  yq -i '.azureStorageSyncConfig.azureAppClientSecret="$HARNESS_CE_AZURE_CLIENTSECRET"' $CONFIG_FILE
fi

if [[ "" != "$HARNESS_CE_AZURE_TENANTID" ]]; then
  yq -i '.azureStorageSyncConfig.azureTenantId="$HARNESS_CE_AZURE_TENANTID"' $CONFIG_FILE
fi

if [[ "" != "$HARNESS_CE_AZURE_CONTAINER_NAME" ]]; then
  yq -i '.azureStorageSyncConfig.azureStorageContainerName="$HARNESS_CE_AZURE_CONTAINER_NAME"' $CONFIG_FILE
fi

if [[ "" != "$HARNESS_CE_AZURE_STORAGE_NAME" ]]; then
  yq -i '.azureStorageSyncConfig.azureStorageAccountName="$HARNESS_CE_AZURE_STORAGE_NAME"' $CONFIG_FILE
fi

if [[ "" != "$HARNESS_CE_AZURE_SAS" ]]; then
  yq -i '.azureStorageSyncConfig.azureSasToken="$HARNESS_CE_AZURE_SAS"' $CONFIG_FILE
fi

if [[ "" != "$HARNESS_CE_AZURE_IS_SYNC_JOB_DISABLED" ]]; then
  yq -i '.azureStorageSyncConfig.syncJobDisabled="$HARNESS_CE_AZURE_IS_SYNC_JOB_DISABLED"' $CONFIG_FILE
fi

if [[ "" != "$ANOMALY_DETECTION_PYTHON_SERVICE_URL" ]]; then
  yq -i '.cePythonService.pythonServiceUrl="$ANOMALY_DETECTION_PYTHON_SERVICE_URL"' $CONFIG_FILE
fi

if [[ "" != "$ANOMALY_DETECTION_USE_PROPHET" ]]; then
  yq -i '.cePythonService.useProphet="$ANOMALY_DETECTION_USE_PROPHET"' $CONFIG_FILE
fi

if [[ "" != "$BANZAI_CONFIG_HOST" ]]; then
  yq -i '.banzaiConfig.host="$BANZAI_CONFIG_HOST"' $CONFIG_FILE
fi

if [[ "" != "$BANZAI_CONFIG_PORT" ]]; then
  yq -i '.banzaiConfig.port="$BANZAI_CONFIG_PORT"' $CONFIG_FILE
fi

if [[ "" != "$NG_MANAGER_SERVICE_HTTP_CLIENT_CONFIG_BASE_URL" ]]; then
  yq -i '.ngManagerServiceHttpClientConfig.baseUrl="$NG_MANAGER_SERVICE_HTTP_CLIENT_CONFIG_BASE_URL"' $CONFIG_FILE
fi

if [[ "" != "$NEXT_GEN_MANAGER_SECRET" ]]; then
  yq -i '.ngManagerServiceSecret="$NEXT_GEN_MANAGER_SECRET"' $CONFIG_FILE
fi

if [[ "" != "$CE_NG_SERVICE_HTTP_CLIENT_CONFIG_BASE_URL" ]]; then
  yq -i '.ceNgServiceHttpClientConfig.baseUrl="$CE_NG_SERVICE_HTTP_CLIENT_CONFIG_BASE_URL"' $CONFIG_FILE
fi

if [[ "" != "$CE_NG_SERVICE_SECRET" ]]; then
  yq -i '.ceNgServiceSecret="$CE_NG_SERVICE_SECRET"' $CONFIG_FILE
fi

if [[ "" != "$CONNECTOR_HEALTH_UPDATE_JOB_ENABLED" ]]; then
  yq -i '.connectorHealthUpdateJobConfig.enabled="$CONNECTOR_HEALTH_UPDATE_JOB_ENABLED"' $CONFIG_FILE
fi

if [[ "" != "$AWS_ACCOUNT_TAGS_COLLECTION_JOB_ENABLED" ]]; then
  yq -i '.awsAccountTagsCollectionJobConfig.enabled="$AWS_ACCOUNT_TAGS_COLLECTION_JOB_ENABLED"' $CONFIG_FILE
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
