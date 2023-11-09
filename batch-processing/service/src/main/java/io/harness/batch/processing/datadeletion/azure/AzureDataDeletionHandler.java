/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.datadeletion.azure;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.ccm.commons.entities.datadeletion.DataDeletionBucket.AZURE_DELETION;

import io.harness.annotations.dev.OwnedBy;
import io.harness.batch.processing.datadeletion.DataDeletionHandler;
import io.harness.batch.processing.datadeletion.azure.step.AzureStorageCleanup;
import io.harness.ccm.commons.entities.datadeletion.DataDeletionRecord;
import io.harness.ccm.commons.entities.datadeletion.DataDeletionStep;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@OwnedBy(CE)
public class AzureDataDeletionHandler extends DataDeletionHandler {
  @Autowired AzureStorageCleanup azureStorageCleanup;

  AzureDataDeletionHandler() {
    super(AZURE_DELETION);
  }

  @Override
  public boolean executeStep(DataDeletionRecord dataDeletionRecord, DataDeletionStep dataDeletionStep) {
    String accountId = dataDeletionRecord.getAccountId();
    boolean dryRun = dataDeletionRecord.getDryRun();
    log.info("Executing step: {} for accountId: {}", dataDeletionStep, accountId);
    try {
      boolean deleted;
      switch (dataDeletionStep) {
        case AZURE_STORAGE:
          deleted = azureStorageCleanup.delete(accountId, dataDeletionRecord, dryRun);
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
