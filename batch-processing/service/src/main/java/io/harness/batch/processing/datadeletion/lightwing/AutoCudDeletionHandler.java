/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.datadeletion.lightwing;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.ccm.commons.entities.datadeletion.DataDeletionBucket.AUTOCUD_DELETION;
import static io.harness.ccm.commons.entities.datadeletion.DataDeletionStatus.COMPLETE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.batch.processing.datadeletion.DataDeletionHandler;
import io.harness.ccm.commons.entities.datadeletion.DataDeletionRecord;
import io.harness.ccm.commons.entities.datadeletion.DataDeletionStatus;
import io.harness.faktory.FaktoryProducer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@OwnedBy(CE)
public class AutoCudDeletionHandler extends DataDeletionHandler {
  private static final String AUTOCUD_DELETION_JOB_TYPE = "AutoCudDataDeletionJob";
  private static final String JOB_QUEUE = "services_bulk";

  AutoCudDeletionHandler() {
    super(AUTOCUD_DELETION);
  }

  @Override
  public void executeSteps(DataDeletionRecord dataDeletionRecord) {
    try {
      String accountId = dataDeletionRecord.getAccountId();
      DataDeletionStatus status = dataDeletionRecord.getAutoCudStatus();
      Boolean dryRun = dataDeletionRecord.getDryRun();
      if (status != null && status.equals(COMPLETE)) {
        log.info("Can't push job to Faktory, AutoCud deletion job status: {}", status);
      } else {
        String jobId = FaktoryProducer.push(AUTOCUD_DELETION_JOB_TYPE, JOB_QUEUE, new Object[] {accountId, dryRun}, 0);
        log.info("Pushed job to faktory with Job id: {}", jobId);
        dataDeletionRecord.setAutoCudRetryCount(dataDeletionRecord.getAutoCudRetryCount() + 1);
        dataDeletionRecord.setRetryCount(
            Math.max(dataDeletionRecord.getRetryCount(), dataDeletionRecord.getAutoCudRetryCount()));
      }
    } catch (Exception e) {
      log.error("Encountered an error in AutoCudDeletionHandler", e);
    }
  }
}
