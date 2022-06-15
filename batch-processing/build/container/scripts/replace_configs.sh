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
    yq write -i "$CONFIG_FILE" "$CONFIG_KEY" "$CONFIG_VALUE"
  fi
}

if [[ "" != "$MONGO_URI" ]]; then
  yq write -i $CONFIG_FILE harness-mongo.uri "$MONGO_URI"
fi

if [[ "" != "$MONGO_READ_PREF_NAME" ]]; then
  yq write -i $CONFIG_FILE harness-mongo.readPref.name "$MONGO_READ_PREF_NAME"
fi

if [[ "" != "$MONGO_READ_PREF_TAGS" ]]; then
  IFS=',' read -ra TAG_ITEMS <<< "$MONGO_READ_PREF_TAGS"
  for ITEM in "${TAG_ITEMS[@]}"; do
    TAG_NAME=$(echo $ITEM | awk -F= '{print $1}')
    TAG_VALUE=$(echo $ITEM | awk -F= '{print $2}')
    yq write -i $CONFIG_FILE "harness-mongo.readPref.tagSet.[$TAG_NAME]" "$TAG_VALUE"
  done
fi

if [[ "" != "$MONGO_INDEX_MANAGER_MODE" ]]; then
  yq write -i $CONFIG_FILE harness-mongo.indexManagerMode $MONGO_INDEX_MANAGER_MODE
fi

if [[ "" != "$EVEMTS_MONGO_INDEX_MANAGER_MODE" ]]; then
  yq write -i $CONFIG_FILE events-mongo.indexManagerMode $EVEMTS_MONGO_INDEX_MANAGER_MODE
fi

if [[ "" != "$EVENTS_MONGO_URI" ]]; then
  yq write -i $CONFIG_FILE events-mongo.uri "$EVENTS_MONGO_URI"
fi

if [[ "" != "$TIMESCALEDB_URI" ]]; then
  yq write -i $CONFIG_FILE timescaledb.timescaledbUrl "$TIMESCALEDB_URI"
fi

if [[ "" != "$TIMESCALEDB_USERNAME" ]]; then
  yq write -i $CONFIG_FILE timescaledb.timescaledbUsername "$TIMESCALEDB_USERNAME"
fi

if [[ "" != "$TIMESCALEDB_PASSWORD" ]]; then
  yq write -i $CONFIG_FILE timescaledb.timescaledbPassword "$TIMESCALEDB_PASSWORD"
fi

if [[ "" != "$S3_SYNC_CONFIG_BUCKET_NAME" ]]; then
  yq write -i $CONFIG_FILE awsS3SyncConfig.awsS3BucketName "$S3_SYNC_CONFIG_BUCKET_NAME"
fi

if [[ "" != "$QUERY_BATCH_SIZE" ]]; then
  yq write -i $CONFIG_FILE batchQueryConfig.queryBatchSize "$QUERY_BATCH_SIZE"
fi

if [[ "" != "$SYNC_JOB_DISABLED" ]]; then
  yq write -i $CONFIG_FILE batchQueryConfig.syncJobDisabled "$SYNC_JOB_DISABLED"
fi

if [[ "" != "$S3_SYNC_CONFIG_ACCESSKEY" ]]; then
  yq write -i $CONFIG_FILE awsS3SyncConfig.awsAccessKey "$S3_SYNC_CONFIG_ACCESSKEY"
fi

if [[ "" != "$S3_SYNC_CONFIG_SECRETKEY" ]]; then
  yq write -i $CONFIG_FILE awsS3SyncConfig.awsSecretKey "$S3_SYNC_CONFIG_SECRETKEY"
fi

if [[ "" != "$S3_SYNC_CONFIG_REGION" ]]; then
  yq write -i $CONFIG_FILE awsS3SyncConfig.region "$S3_SYNC_CONFIG_REGION"
fi

if [[ "" != "$DATA_PIPELINE_CONFIG_GCP_PROJECT_ID" ]]; then
  yq write -i $CONFIG_FILE billingDataPipelineConfig.gcpProjectId "$DATA_PIPELINE_CONFIG_GCP_PROJECT_ID"
fi

if [[ "" != "$DATA_PIPELINE_CONFIG_GCS_BASE_PATH" ]]; then
  yq write -i $CONFIG_FILE billingDataPipelineConfig.gcsBasePath "$DATA_PIPELINE_CONFIG_GCS_BASE_PATH"
fi

if [[ "" != "$GCP_PIPELINE_PUB_SUB_TOPIC" ]]; then
  yq write -i $CONFIG_FILE billingDataPipelineConfig.gcpPipelinePubSubTopic "$GCP_PIPELINE_PUB_SUB_TOPIC"
fi

if [[ "" != "$GCP_USE_NEW_PIPELINE" ]]; then
  yq write -i $CONFIG_FILE billingDataPipelineConfig.gcpUseNewPipeline "$GCP_USE_NEW_PIPELINE"
fi

if [[ "" != "$AWS_USE_NEW_PIPELINE" ]]; then
  yq write -i $CONFIG_FILE billingDataPipelineConfig.awsUseNewPipeline "$AWS_USE_NEW_PIPELINE"
fi

if [[ "" != "$GCP_SYNC_ENABLED" ]]; then
  yq write -i $CONFIG_FILE billingDataPipelineConfig.isGcpSyncEnabled "$GCP_SYNC_ENABLED"
fi

if [[ "" != "$CLUSTER_DATA_GCS_BUCKET" ]]; then
  yq write -i $CONFIG_FILE billingDataPipelineConfig.clusterDataGcsBucketName "$CLUSTER_DATA_GCS_BUCKET"
fi

if [[ "" != "$CLUSTER_DATA_GCS_BACKUP_BUCKET" ]]; then
  yq write -i $CONFIG_FILE billingDataPipelineConfig.clusterDataGcsBackupBucketName "$CLUSTER_DATA_GCS_BACKUP_BUCKET"
fi

if [[ "" != "$AWS_ROLE_NAME" ]]; then
  yq write -i $CONFIG_FILE billingDataPipelineConfig.awsRoleName "$AWS_ROLE_NAME"
fi


if [[ "" != "$SMTP_HOST" ]]; then
  yq write -i $CONFIG_FILE smtp.host "$SMTP_HOST"
fi

if [[ "" != "$SMTP_USERNAME" ]]; then
  yq write -i $CONFIG_FILE smtp.username "$SMTP_USERNAME"
fi

if [[ "" != "$SMTP_PASSWORD" ]]; then
  yq write -i $CONFIG_FILE smtp.password "$SMTP_PASSWORD"
fi

if [[ "" != "$SMTP_USE_SSL" ]]; then
  yq write -i $CONFIG_FILE smtp.useSSL "$SMTP_USE_SSL"
fi

if [[ "" != "$UI_SERVER_URL" ]]; then
  yq write -i $CONFIG_FILE baseUrl "$UI_SERVER_URL"
fi

if [[ "" != "$SEGMENT_ENABLED" ]]; then
  yq write -i $CONFIG_FILE segmentConfig.enabled "$SEGMENT_ENABLED"
fi

if [[ "" != "$SEGMENT_APIKEY" ]]; then
  yq write -i $CONFIG_FILE segmentConfig.apiKey "$SEGMENT_APIKEY"
fi

if [[ "" != "$CF_API_KEY" ]]; then
  yq write -i $CONFIG_FILE cfConfig.apiKey "$CF_API_KEY"
fi

if [[ "" != "$CF_BASE_URL" ]]; then
  yq write -i $CONFIG_FILE cfConfig.baseUrl "$CF_BASE_URL"
fi

if [[ "" != "$POD_NAME" ]]; then
  yq write -i $CONFIG_FILE podInfo.name "$POD_NAME"
fi

if [[ "" != "$REPLICA" ]]; then
  yq write -i $CONFIG_FILE podInfo.replica "$REPLICA"
fi

if [[ "" != "$ISOLATED_REPLICA" ]]; then
  yq write -i $CONFIG_FILE podInfo.isolatedReplica "$ISOLATED_REPLICA"
fi

if [[ "" != "$BUDGET_ALERTS_JOB_CRON" ]]; then
  yq write -i $CONFIG_FILE scheduler-jobs-config.budgetAlertsJobCron "$BUDGET_ALERTS_JOB_CRON"
fi

if [[ "" != "$WEEKLY_REPORT_JOB_CRON" ]]; then
  yq write -i $CONFIG_FILE scheduler-jobs-config.weeklyReportsJobCron "$WEEKLY_REPORT_JOB_CRON"
fi

if [[ "" != "$CONNECTOR_HEALTH_UPDATE_CRON" ]]; then
  yq write -i $CONFIG_FILE scheduler-jobs-config.connectorHealthUpdateJobCron "$CONNECTOR_HEALTH_UPDATE_CRON"
fi

if [[ "" != "$AWS_ACCOUNT_TAGS_COLLECTION_CRON" ]]; then
  yq write -i $CONFIG_FILE scheduler-jobs-config.awsAccountTagsCollectionJobCron "$AWS_ACCOUNT_TAGS_COLLECTION_CRON"
fi

if [[ "" != "$HARNESS_CE_AZURE_CLIENTID" ]]; then
  yq write -i $CONFIG_FILE azureStorageSyncConfig.azureAppClientId "$HARNESS_CE_AZURE_CLIENTID"
fi

if [[ "" != "$HARNESS_CE_AZURE_CLIENTSECRET" ]]; then
  yq write -i $CONFIG_FILE azureStorageSyncConfig.azureAppClientSecret "$HARNESS_CE_AZURE_CLIENTSECRET"
fi

if [[ "" != "$HARNESS_CE_AZURE_TENANTID" ]]; then
  yq write -i $CONFIG_FILE azureStorageSyncConfig.azureTenantId "$HARNESS_CE_AZURE_TENANTID"
fi

if [[ "" != "$HARNESS_CE_AZURE_CONTAINER_NAME" ]]; then
  yq write -i $CONFIG_FILE azureStorageSyncConfig.azureStorageContainerName "$HARNESS_CE_AZURE_CONTAINER_NAME"
fi

if [[ "" != "$HARNESS_CE_AZURE_STORAGE_NAME" ]]; then
  yq write -i $CONFIG_FILE azureStorageSyncConfig.azureStorageAccountName "$HARNESS_CE_AZURE_STORAGE_NAME"
fi

if [[ "" != "$HARNESS_CE_AZURE_SAS" ]]; then
  yq write -i $CONFIG_FILE azureStorageSyncConfig.azureSasToken "$HARNESS_CE_AZURE_SAS"
fi

if [[ "" != "$HARNESS_CE_AZURE_IS_SYNC_JOB_DISABLED" ]]; then
  yq write -i $CONFIG_FILE azureStorageSyncConfig.syncJobDisabled "$HARNESS_CE_AZURE_IS_SYNC_JOB_DISABLED"
fi

if [[ "" != "$ANOMALY_DETECTION_PYTHON_SERVICE_URL" ]]; then
  yq write -i $CONFIG_FILE cePythonService.pythonServiceUrl "$ANOMALY_DETECTION_PYTHON_SERVICE_URL"
fi

if [[ "" != "$ANOMALY_DETECTION_USE_PROPHET" ]]; then
  yq write -i $CONFIG_FILE cePythonService.useProphet "$ANOMALY_DETECTION_USE_PROPHET"
fi

if [[ "" != "$BANZAI_CONFIG_HOST" ]]; then
  yq write -i $CONFIG_FILE banzaiConfig.host "$BANZAI_CONFIG_HOST"
fi

if [[ "" != "$BANZAI_CONFIG_PORT" ]]; then
  yq write -i $CONFIG_FILE banzaiConfig.port "$BANZAI_CONFIG_PORT"
fi

if [[ "" != "$NG_MANAGER_SERVICE_HTTP_CLIENT_CONFIG_BASE_URL" ]]; then
  yq write -i $CONFIG_FILE ngManagerServiceHttpClientConfig.baseUrl "$NG_MANAGER_SERVICE_HTTP_CLIENT_CONFIG_BASE_URL"
fi

if [[ "" != "$NEXT_GEN_MANAGER_SECRET" ]]; then
  yq write -i $CONFIG_FILE ngManagerServiceSecret "$NEXT_GEN_MANAGER_SECRET"
fi

if [[ "" != "$CONNECTOR_HEALTH_UPDATE_JOB_ENABLED" ]]; then
  yq write -i $CONFIG_FILE connectorHealthUpdateJobConfig.enabled "$CONNECTOR_HEALTH_UPDATE_JOB_ENABLED"
fi

if [[ "" != "$AWS_ACCOUNT_TAGS_COLLECTION_JOB_ENABLED" ]]; then
  yq write -i $CONFIG_FILE awsAccountTagsCollectionJobConfig.enabled "$AWS_ACCOUNT_TAGS_COLLECTION_JOB_ENABLED"
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

replace_key_value banzaiRecommenderConfig.baseUrl "$BANZAI_RECOMMENDER_BASEURL"
replace_key_value awsCurBilling "$AWS_CUR_BILLING"

replace_key_value gcpConfig.gcpProjectId "$GCP_PROJECT_ID"
replace_key_value gcpConfig.gcpAwsConnectorCrudPubSubTopic "$GCP_AWS_CONNECTOR_CRUD_PUBSUB_TOPIC"
