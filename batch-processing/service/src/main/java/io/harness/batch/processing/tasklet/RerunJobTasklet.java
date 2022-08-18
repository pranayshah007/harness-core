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
import io.harness.ccm.commons.beans.JobConstants;
import io.harness.ccm.commons.dao.CEMetadataRecordDao;
import io.harness.ccm.commons.entities.batch.CEMetadataRecord;

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

  @Override
  public RepeatStatus execute(StepContribution stepContribution, ChunkContext chunkContext) throws Exception {
    final JobConstants jobConstants = new CCMJobConstants(chunkContext);

    Instant startInstant = Instant.ofEpochMilli(jobConstants.getJobStartTime()).minus(3, ChronoUnit.DAYS);
    CEMetadataRecord ceMetadataRecord = ceMetadataRecordDao.getByAccountId(jobConstants.getAccountId());
    if (null != ceMetadataRecord && isCloudDataPresent(ceMetadataRecord)) {
      log.info("invalidate jobs for {}", jobConstants.getAccountId());
      ImmutableList<String> batchJobs = ImmutableList.of(BatchJobType.ANOMALY_DETECTION_CLOUD.toString());
      batchJobScheduledDataService.invalidateJobs(jobConstants.getAccountId(), batchJobs, startInstant);
    }

    if (null != ceMetadataRecord
        && ((null != ceMetadataRecord.getAwsDataPresent() && ceMetadataRecord.getAwsDataPresent())
            || (null != ceMetadataRecord.getAzureDataPresent() && ceMetadataRecord.getAzureDataPresent()))) {
      log.info("invalidate cluster jobs for {}", jobConstants.getAccountId());
      ImmutableList<String> batchJobs =
          ImmutableList.of(BatchJobType.INSTANCE_BILLING.toString(), BatchJobType.ACTUAL_IDLE_COST_BILLING.toString(),
              BatchJobType.INSTANCE_BILLING_AGGREGATION.toString(), BatchJobType.CLUSTER_DATA_TO_BIG_QUERY.toString());
      batchJobScheduledDataService.invalidateJobs(jobConstants.getAccountId(), batchJobs, startInstant);
      boolean cleanBillingData = billingDataService.cleanBillingData(jobConstants.getAccountId(), startInstant,
          Instant.ofEpochMilli(jobConstants.getJobEndTime()), BatchJobType.INSTANCE_BILLING);
      if (!cleanBillingData) {
        throw new Exception("Error Cleaning billing data");
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
