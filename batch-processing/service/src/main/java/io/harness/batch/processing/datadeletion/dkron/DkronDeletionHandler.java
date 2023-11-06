/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.datadeletion.dkron;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.ccm.commons.entities.datadeletion.DataDeletionBucket.DKRON_DELETION;

import io.harness.annotations.dev.OwnedBy;
import io.harness.batch.processing.datadeletion.DataDeletionHandler;
import io.harness.ccm.commons.entities.datadeletion.DataDeletionRecord;
import io.harness.ccm.commons.entities.datadeletion.DataDeletionStep;
import io.harness.ccm.scheduler.SchedulerClient;
import io.harness.ccm.views.dao.RuleEnforcementDAO;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import retrofit2.Response;

@Slf4j
@Component
@OwnedBy(CE)
public class DkronDeletionHandler extends DataDeletionHandler {
  @Autowired RuleEnforcementDAO ruleEnforcementDAO;
  @Autowired SchedulerClient schedulerClient;

  DkronDeletionHandler() {
    super(DKRON_DELETION);
  }

  @Override
  public boolean executeStep(DataDeletionRecord dataDeletionRecord, DataDeletionStep dataDeletionStep) {
    String accountId = dataDeletionRecord.getAccountId();
    boolean dryRun = dataDeletionRecord.getDryRun();
    log.info("Executing step: {} for accountId: {}", dataDeletionStep, accountId);
    try {
      long recordsCount = 0L;
      switch (dataDeletionStep) {
        case DKRON_GOVERNANCE_RULE_EXECUTION_JOBS:
          List<String> schedulerNames = ruleEnforcementDAO.fetchSchedulerNamesByAccountId(accountId);
          recordsCount = schedulerNames.size();
          log.info("Found these rule enforcements to be deleted: {}", schedulerNames);
          if (!dryRun) {
            for (String schedulerName : schedulerNames) {
              Response res = schedulerClient.deleteJob(schedulerName).execute();
              log.info("code: {}, message: {}, body: {}", res.code(), res.message(), res.body());
              log.info("Deleted governance rule enforcement: {}", schedulerName);
            }
          }
          break;
        default:
          log.warn("Unknown step: {} for accountId: {}", dataDeletionStep, accountId);
      }
      dataDeletionRecord.getRecords().get(dataDeletionStep.name()).setRecordsCount(recordsCount);
      return true;
    } catch (Exception e) {
      log.error("Caught an exception while executing step: {}, accountId: {}", dataDeletionStep, accountId, e);
      return false;
    }
  }
}
