/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.tasklet;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.batch.processing.billing.timeseries.service.impl.BillingDataServiceImpl;
import io.harness.batch.processing.ccm.BatchJobType;
import io.harness.batch.processing.ccm.CCMJobConstants;
import io.harness.batch.processing.service.intfc.BatchJobScheduledDataService;
import io.harness.beans.FeatureName;
import io.harness.ccm.commons.beans.JobConstants;
import io.harness.ccm.commons.dao.CEMetadataRecordDao;
import io.harness.ccm.commons.entities.batch.CEMetadataRecord;
import io.harness.ff.FeatureFlagService;

import com.google.common.collect.ImmutableList;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;

@OwnedBy(HarnessTeam.CE)
@Slf4j
public class RerunJobTasklet implements Tasklet {
  @Autowired private CEMetadataRecordDao ceMetadataRecordDao;
  @Autowired private BatchJobScheduledDataService batchJobScheduledDataService;
  @Autowired private BillingDataServiceImpl billingDataService;
  @Autowired private FeatureFlagService featureFlagService;

  @Override
  public RepeatStatus execute(StepContribution stepContribution, ChunkContext chunkContext) throws Exception {
    final JobConstants jobConstants = new CCMJobConstants(chunkContext);

    String accountId = jobConstants.getAccountId();
    Instant startInstant = Instant.ofEpochMilli(jobConstants.getJobStartTime()).minus(3, ChronoUnit.DAYS);
    CEMetadataRecord ceMetadataRecord = ceMetadataRecordDao.getByAccountId(accountId);
    if (null != ceMetadataRecord && isCloudDataPresent(ceMetadataRecord)) {
      log.info("invalidating ANOMALY_DETECTION_CLOUD jobs for {} for last 3 days", accountId);
      ImmutableList<String> batchJobs = ImmutableList.of(BatchJobType.ANOMALY_DETECTION_CLOUD.toString());
      batchJobScheduledDataService.invalidateJobs(accountId, batchJobs, startInstant);
    }

    if (null != ceMetadataRecord
        && ((null != ceMetadataRecord.getAwsDataPresent() && ceMetadataRecord.getAwsDataPresent())
            || (null != ceMetadataRecord.getAzureDataPresent() && ceMetadataRecord.getAzureDataPresent()))) {
      log.info("invalidate cluster jobs for {}", accountId);
      ImmutableList<String> batchJobs =
          ImmutableList.of(BatchJobType.INSTANCE_BILLING.toString(), BatchJobType.ACTUAL_IDLE_COST_BILLING.toString(),
              BatchJobType.INSTANCE_BILLING_AGGREGATION.toString(), BatchJobType.CLUSTER_DATA_TO_BIG_QUERY.toString());
      batchJobScheduledDataService.invalidateJobs(accountId, batchJobs, startInstant);
      boolean cleanBillingData = billingDataService.cleanBillingData(
          accountId, startInstant, Instant.ofEpochMilli(jobConstants.getJobEndTime()), BatchJobType.INSTANCE_BILLING);
      if (!cleanBillingData) {
        throw new Exception("Error Cleaning billing data");
      }
      boolean hourlyRerunEnabled = featureFlagService.isEnabled(FeatureName.CE_RERUN_HOURLY_JOBS, accountId);
      if (hourlyRerunEnabled || accountId.equals("aYXZz76ETU-_3LLQSzBt1Q")) {
        log.info("invalidate cluster data hourly jobs for {}", accountId);
        ImmutableList<String> hourlyBatchJobs = ImmutableList.of(BatchJobType.INSTANCE_BILLING_HOURLY.toString(),
            BatchJobType.ACTUAL_IDLE_COST_BILLING_HOURLY.toString(),
            BatchJobType.INSTANCE_BILLING_HOURLY_AGGREGATION.toString(),
            BatchJobType.CLUSTER_DATA_HOURLY_TO_BIG_QUERY.toString());
        Instant hourlyStartInstant = Instant.ofEpochMilli(jobConstants.getJobStartTime()).minus(1, ChronoUnit.DAYS);

        batchJobScheduledDataService.invalidateJobs(accountId, hourlyBatchJobs, hourlyStartInstant);
        boolean cleanHourlyBillingData = billingDataService.cleanBillingData(accountId, hourlyStartInstant,
            Instant.ofEpochMilli(jobConstants.getJobEndTime()), BatchJobType.INSTANCE_BILLING_HOURLY);
        if (!cleanHourlyBillingData) {
          throw new Exception("Error Cleaning billing hourly data");
        }
      }
    }
    return null;
  }

  private boolean isCloudDataPresent(CEMetadataRecord ceMetadataRecord) {
    return Boolean.TRUE.equals(ceMetadataRecord.getAwsDataPresent())
        || Boolean.TRUE.equals(ceMetadataRecord.getGcpDataPresent())
        || Boolean.TRUE.equals(ceMetadataRecord.getAzureDataPresent());
  }
}
