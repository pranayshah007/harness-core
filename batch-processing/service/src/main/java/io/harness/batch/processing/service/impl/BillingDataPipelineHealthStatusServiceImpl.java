/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.service.impl;

import static io.harness.batch.processing.service.impl.BillingDataPipelineServiceImpl.PARENT_TEMPLATE;

import io.harness.batch.processing.ccm.BatchJobType;
import io.harness.batch.processing.config.BatchMainConfig;
import io.harness.batch.processing.dao.intfc.BatchJobScheduledDataDao;
import io.harness.batch.processing.dao.intfc.BillingDataPipelineRecordDao;
import io.harness.batch.processing.service.intfc.BillingDataPipelineHealthStatusService;
import io.harness.batch.processing.service.intfc.BillingDataPipelineService;
import io.harness.ccm.bigQuery.BigQueryService;
import io.harness.ccm.commons.constants.CloudProvider;
import io.harness.ccm.commons.entities.batch.BatchJobScheduledData;
import io.harness.ccm.commons.entities.billing.BillingDataPipelineRecord;
import io.harness.ccm.commons.entities.billing.BillingDataPipelineRecord.BillingDataPipelineRecordBuilder;

import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.FieldValueList;
import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.QueryParameterValue;
import com.google.cloud.bigquery.TableResult;
import com.google.cloud.bigquery.datatransfer.v1.DataTransferServiceClient;
import com.google.cloud.bigquery.datatransfer.v1.ListTransferConfigsRequest;
import com.google.cloud.bigquery.datatransfer.v1.TransferConfig;
import com.google.cloud.bigquery.datatransfer.v1.TransferState;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.inject.Singleton;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Singleton
@Slf4j
public class BillingDataPipelineHealthStatusServiceImpl implements BillingDataPipelineHealthStatusService {
  private BatchMainConfig mainConfig;
  private BillingDataPipelineRecordDao billingDataPipelineRecordDao;
  private BillingDataPipelineService billingDataPipelineService;
  private BatchJobScheduledDataDao batchJobScheduledDataDao;
  @Autowired private BigQueryService bigQueryService;

  private static final String BQ_PRE_AGG_TABLE_DATACHECK_TEMPLATE =
      "SELECT count(*) as count FROM `%s.preAggregated` WHERE DATE(startTime) "
      + ">= DATE_SUB(@run_date , INTERVAL 3 DAY) AND cloudProvider = \"%s\";%n";

  @Autowired
  public BillingDataPipelineHealthStatusServiceImpl(BatchMainConfig mainConfig,
      BillingDataPipelineRecordDao billingDataPipelineRecordDao, BillingDataPipelineService billingDataPipelineService,
      BatchJobScheduledDataDao batchJobScheduledDataDao) {
    this.mainConfig = mainConfig;
    this.billingDataPipelineRecordDao = billingDataPipelineRecordDao;
    this.billingDataPipelineService = billingDataPipelineService;
    this.batchJobScheduledDataDao = batchJobScheduledDataDao;
  }

  @Override
  public void processAndUpdateHealthStatus() throws IOException {
    String gcpProjectId = mainConfig.getBillingDataPipelineConfig().getGcpProjectId();
    String parent = String.format(PARENT_TEMPLATE, gcpProjectId);
    DataTransferServiceClient dataTransferServiceClient = billingDataPipelineService.getDataTransferClient();
    updateBillingPipelineRecordsStatus(computeTransferConfigStatusMap(dataTransferServiceClient, parent));
  }

  Map<String, TransferState> computeTransferConfigStatusMap(
      DataTransferServiceClient dataTransferServiceClient, String parent) {
    List<TransferConfig> transferConfigList = new ArrayList<>();
    String nextToken = "";
    ListTransferConfigsRequest.Builder transferConfigsRequest =
        ListTransferConfigsRequest.newBuilder().setParent(parent);

    do {
      transferConfigsRequest.setPageToken(nextToken);
      DataTransferServiceClient.ListTransferConfigsPagedResponse transferConfigsPagedResponse =
          dataTransferServiceClient.listTransferConfigs(transferConfigsRequest.build());
      transferConfigList.addAll(Lists.newArrayList(transferConfigsPagedResponse.iterateAll()));
      nextToken = transferConfigsPagedResponse.getNextPageToken();
    } while (!nextToken.equals(""));

    logFailedTransfers(transferConfigList);

    return transferConfigList.stream().collect(ImmutableMap.toImmutableMap(
        TransferConfig::getDisplayName, TransferConfig::getState, (existing, replacement) -> existing));
  }

  private void logFailedTransfers(List<TransferConfig> transferConfigList) {
    for (TransferConfig transferConfig : transferConfigList) {
      if (transferConfig.getState().getNumber() == 5) {
        log.error("Transfer Failed: {} ", transferConfig.getDisplayName());
      }
    }
  }

  void updateBillingPipelineRecordsStatus(Map<String, TransferState> transferToStatusMap) {
    log.info("running updateBillingPipelineRecordsStatus");
    billingDataPipelineRecordDao.listAllBillingDataPipelineRecords().forEach(billingDataPipelineRecord -> {
      try {
        BillingDataPipelineRecordBuilder billingDataPipelineRecordBuilder = BillingDataPipelineRecord.builder();
        if (billingDataPipelineRecord.getCloudProvider().equals(CloudProvider.AZURE.name())) {
          billingDataPipelineRecordBuilder.accountId(billingDataPipelineRecord.getAccountId())
              .settingId(billingDataPipelineRecord.getSettingId());
          // Check for data in last 3 days in preAgg table. Only when time has elapsed more than 24 hours.
          long now = Instant.now().toEpochMilli() - 1 * 24 * 60 * 60 * 1000;
          if (billingDataPipelineRecord.getCreatedAt() >= now
              || isDataPresentPreAgg(billingDataPipelineRecord.getDataSetId(), CloudProvider.AZURE.name())) {
            // Set SUCCEEDED in first 24 hours
            log.info("Updating data transfer and preagg status..");
            billingDataPipelineRecordBuilder.dataTransferJobStatus(TransferState.SUCCEEDED.toString());
            // This is for compatibility with health status api in manager
            billingDataPipelineRecordBuilder.preAggregatedScheduledQueryStatus(TransferState.SUCCEEDED.toString());
          } else {
            log.info("Updating data transfer and preagg status...");
            billingDataPipelineRecordBuilder.dataTransferJobStatus(TransferState.FAILED.toString());
            // This is for compatibility with health status api in manager
            billingDataPipelineRecordBuilder.preAggregatedScheduledQueryStatus(TransferState.SUCCEEDED.toString());
          }
          billingDataPipelineRecordBuilder.lastSuccessfulStorageSync(fetchLastSuccessfulS3RunInstant(
              billingDataPipelineRecord.getAccountId(), BatchJobType.SYNC_BILLING_REPORT_AZURE));
        } else if (billingDataPipelineRecord.getCloudProvider().equals(CloudProvider.GCP.name())) {
          billingDataPipelineRecordBuilder.accountId(billingDataPipelineRecord.getAccountId())
              .settingId(billingDataPipelineRecord.getSettingId())
              .dataTransferJobStatus(
                  getTransferStateStringValue(transferToStatusMap, billingDataPipelineRecord.getDataTransferJobName()));
          if (mainConfig.getBillingDataPipelineConfig().isGcpUseNewPipeline()) {
            // For GCP only + in new pipeline, we dont need to compute preagg status anymore.
            // This is for compatibility with health status api in manager
            log.info("Setting status for preagg query in new pipeline for GCP");
            billingDataPipelineRecordBuilder.preAggregatedScheduledQueryStatus(TransferState.SUCCEEDED.toString());
          } else {
            // Regular flow
            log.info("Setting status for preagg query for GCP");
            billingDataPipelineRecordBuilder.preAggregatedScheduledQueryStatus(getTransferStateStringValue(
                transferToStatusMap, billingDataPipelineRecord.getPreAggregatedScheduledQueryName()));
          }
        } else if (billingDataPipelineRecord.getCloudProvider().equals(CloudProvider.AWS.name())) {
          billingDataPipelineRecordBuilder.accountId(billingDataPipelineRecord.getAccountId())
              .settingId(billingDataPipelineRecord.getSettingId());
          if (mainConfig.getBillingDataPipelineConfig().isAwsUseNewPipeline()) {
            // Check for data in last 3 days in preAgg table. Only when time has elapsed more than 24 hours.
            log.info("Setting status for preagg query in new pipeline for AWS");
            long now = Instant.now().toEpochMilli() - 1 * 24 * 60 * 60 * 1000;
            if (billingDataPipelineRecord.getCreatedAt() >= now
                || isDataPresentPreAgg(billingDataPipelineRecord.getDataSetId(), CloudProvider.AWS.name())) {
              // Set SUCCEEDED in first 24 hours
              billingDataPipelineRecordBuilder.dataTransferJobStatus(TransferState.SUCCEEDED.toString())
                  .preAggregatedScheduledQueryStatus(TransferState.SUCCEEDED.toString())
                  .awsFallbackTableScheduledQueryStatus(TransferState.SUCCEEDED.toString());
            } else {
              billingDataPipelineRecordBuilder.dataTransferJobStatus(TransferState.FAILED.toString())
                  .preAggregatedScheduledQueryStatus(TransferState.FAILED.toString())
                  .awsFallbackTableScheduledQueryStatus(TransferState.FAILED.toString());
            }
          } else {
            // Regular flow
            log.info("Setting status for preagg query for AWS");
            billingDataPipelineRecordBuilder
                .dataTransferJobStatus(getTransferStateStringValue(
                    transferToStatusMap, billingDataPipelineRecord.getDataTransferJobName()))
                .preAggregatedScheduledQueryStatus(getTransferStateStringValue(
                    transferToStatusMap, billingDataPipelineRecord.getPreAggregatedScheduledQueryName()))
                .awsFallbackTableScheduledQueryStatus(getTransferStateStringValue(
                    transferToStatusMap, billingDataPipelineRecord.getAwsFallbackTableScheduledQueryName()));
          }
          billingDataPipelineRecordBuilder.lastSuccessfulS3Sync(fetchLastSuccessfulS3RunInstant(
              billingDataPipelineRecord.getAccountId(), BatchJobType.SYNC_BILLING_REPORT_S3));
        }
        billingDataPipelineRecordDao.upsert(billingDataPipelineRecordBuilder.build());
        log.info("Updated record");
      } catch (Exception e) {
        log.error("Failed to update the health status for the account {}", billingDataPipelineRecord.getAccountId(), e);
      }
    });
  }

  private Instant fetchLastSuccessfulS3RunInstant(String accountId, BatchJobType batchJobType) {
    BatchJobScheduledData batchJobScheduledData =
        batchJobScheduledDataDao.fetchLastBatchJobScheduledData(accountId, batchJobType);
    if (null != batchJobScheduledData) {
      return Instant.ofEpochMilli(batchJobScheduledData.getCreatedAt());
    }
    return Instant.MIN;
  }

  protected String getTransferStateStringValue(Map<String, TransferState> transferToStatusMap, String key) {
    return transferToStatusMap.containsKey(key) ? transferToStatusMap.get(key).toString()
                                                : TransferState.UNRECOGNIZED.toString();
  }

  private boolean isDataPresentPreAgg(String datasetId, String cloudProvider) {
    BigQuery bigquery = bigQueryService.get();
    String gcpProjectId = mainConfig.getBillingDataPipelineConfig().getGcpProjectId();
    String tablePrefix = gcpProjectId + "." + datasetId;
    String query = String.format(BQ_PRE_AGG_TABLE_DATACHECK_TEMPLATE, tablePrefix, cloudProvider);
    QueryJobConfiguration queryConfig =
        QueryJobConfiguration.newBuilder(query)
            .addNamedParameter("run_date", QueryParameterValue.date(String.valueOf(java.time.LocalDate.now())))
            .build();

    // Get the results.
    TableResult result;
    try {
      result = bigquery.query(queryConfig);
    } catch (InterruptedException e) {
      log.error("Failed to check for data. {}", e);
      Thread.currentThread().interrupt();
      return false;
    }
    // Print all pages of the results.
    for (FieldValueList row : result.iterateAll()) {
      long count = row.get("count").getLongValue();
      if (count > 0) {
        return true;
      }
    }
    return false;
  }
}
