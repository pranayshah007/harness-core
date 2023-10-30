/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.datadeletion.gcp;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.ccm.commons.entities.datadeletion.DataDeletionBucket.GCP_DELETION;

import io.harness.annotations.dev.OwnedBy;
import io.harness.batch.processing.datadeletion.DataDeletionHandler;
import io.harness.batch.processing.datadeletion.gcp.step.GCPBigQueryDatasetCleanup;
import io.harness.batch.processing.datadeletion.gcp.step.GCPBigQueryInternalTablesCleanup;
import io.harness.batch.processing.datadeletion.gcp.step.GCPBigQueryTransferJobsCleanup;
import io.harness.batch.processing.datadeletion.gcp.step.GCPCloudStorageBucketsCleanup;
import io.harness.batch.processing.datadeletion.gcp.step.GCPServiceAccountsCleanup;
import io.harness.ccm.commons.entities.datadeletion.DataDeletionRecord;
import io.harness.ccm.commons.entities.datadeletion.DataDeletionStep;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@OwnedBy(CE)
public class GCPDataDeletionHandler extends DataDeletionHandler {
  private static final String COST_AGGREGATED = "costAggregated";
  private static final String GCP_CONNECTOR_INFO = "gcpConnectorInfo";
  private static final String CURRENCY_CONVERSION_FACTOR_DEFAULT = "currencyConversionFactorDefault";
  private static final String MSP_MARKUP = "mspMarkup";
  private static final String AWS_TRUTH_TABLE = "awstruthtable";

  @Autowired GCPBigQueryDatasetCleanup gcpBigQueryDatasetCleanup;
  @Autowired GCPBigQueryTransferJobsCleanup gcpBigQueryTransferJobsCleanup;
  @Autowired GCPBigQueryInternalTablesCleanup gcpBigQueryInternalTablesCleanup;
  @Autowired GCPCloudStorageBucketsCleanup gcpCloudStorageBucketsCleanup;
  @Autowired GCPServiceAccountsCleanup gcpServiceAccountsCleanup;

  GCPDataDeletionHandler() {
    super(GCP_DELETION);
  }

  @Override
  public boolean executeStep(DataDeletionRecord dataDeletionRecord, DataDeletionStep dataDeletionStep) {
    String accountId = dataDeletionRecord.getAccountId();
    boolean dryRun = dataDeletionRecord.getDryRun();
    log.info("Executing step: {} for accountId: {}", dataDeletionStep, accountId);
    try {
      boolean deleted;
      switch (dataDeletionStep) {
        case GCP_BQ_DATASET:
          deleted = gcpBigQueryDatasetCleanup.delete(accountId, dataDeletionRecord, dryRun);
          break;
        case GCP_BQ_TRANSFER_JOBS:
          deleted = gcpBigQueryTransferJobsCleanup.delete(accountId, dataDeletionRecord, dryRun);
          break;
        case GCP_BQ_CE_INTERNAL_COST_AGGREGATED:
          deleted = gcpBigQueryInternalTablesCleanup.delete(
              accountId, COST_AGGREGATED, dataDeletionStep, dataDeletionRecord, dryRun);
          break;
        case GCP_BQ_CE_INTERNAL_GCP_CONNECTOR_INFO:
          deleted = gcpBigQueryInternalTablesCleanup.delete(
              accountId, GCP_CONNECTOR_INFO, dataDeletionStep, dataDeletionRecord, dryRun);
          break;
        case GCP_BQ_CE_INTERNAL_CURRENCY_CONVERSION_FACTOR_DEFAULT:
          deleted = gcpBigQueryInternalTablesCleanup.delete(
              accountId, CURRENCY_CONVERSION_FACTOR_DEFAULT, dataDeletionStep, dataDeletionRecord, dryRun);
          break;
        case GCP_BQ_CE_INTERNAL_MSP_MARKUP:
          deleted = gcpBigQueryInternalTablesCleanup.delete(
              accountId, MSP_MARKUP, dataDeletionStep, dataDeletionRecord, dryRun);
          break;
        case GCP_BQ_HARNESS_AWS_TRUTH_TABLE:
          deleted = gcpBigQueryInternalTablesCleanup.delete(
              accountId, AWS_TRUTH_TABLE, dataDeletionStep, dataDeletionRecord, dryRun);
          break;
        case GCP_GCS_BUCKET:
          deleted = gcpCloudStorageBucketsCleanup.delete(accountId, dataDeletionRecord, dryRun);
          break;
        case GCP_SERVICE_ACCOUNTS:
          deleted = gcpServiceAccountsCleanup.delete(accountId, dataDeletionRecord, dryRun);
          break;
        default:
          log.warn("Unknown step: {} for accountId: {}", dataDeletionStep, accountId);
          return true;
      }
      if (!deleted) {
        log.info("Entities have already been deleted for step: {}, accountId: {}", dataDeletionStep, accountId);
      } else {
        log.info("Entities deletion successful for step: {}, accountId: {}", dataDeletionStep, accountId);
      }
    } catch (Exception e) {
      log.error("Caught an exception while executing step: {}, accountId: {}", dataDeletionStep, accountId, e);
      return false;
    }
    return true;
  }
}
