/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.datadeletion.lightwing;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.ccm.commons.entities.datadeletion.DataDeletionBucket.AUTOSTOPPING_DELETION;
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
public class AutoStoppingDeletionHandler extends DataDeletionHandler {
  private static final String AUTOSTOPPING_DELETION_JOB_TYPE = "AutoStoppingDataDeletionJob";
  private static final String JOB_QUEUE = "services_bulk";

  AutoStoppingDeletionHandler() {
    super(AUTOSTOPPING_DELETION);
  }

  @Override
  public void executeSteps(DataDeletionRecord dataDeletionRecord) {
    try {
      String accountId = dataDeletionRecord.getAccountId();
      DataDeletionStatus status = dataDeletionRecord.getAutoStoppingStatus();
      Boolean dryRun = dataDeletionRecord.getDryRun();
      if (status != null && status.equals(COMPLETE)) {
        log.info("Can't push job to Faktory, AutoStopping deletion job status: {}", status);
      } else {
        Object[] args = new Object[] {accountId, dryRun};
        String jobId = FaktoryProducer.push(AUTOSTOPPING_DELETION_JOB_TYPE, JOB_QUEUE, args, 0);
        log.info("Pushed job to faktory with Job id: {}", jobId);
        dataDeletionRecord.setAutoStoppingRetryCount(dataDeletionRecord.getAutoStoppingRetryCount() + 1);
        dataDeletionRecord.setRetryCount(
            Math.max(dataDeletionRecord.getRetryCount(), dataDeletionRecord.getAutoStoppingRetryCount()));
      }
    } catch (Exception e) {
      log.error("Encountered an error in AutoStoppingDeletionHandler", e);
    }
  }
}
