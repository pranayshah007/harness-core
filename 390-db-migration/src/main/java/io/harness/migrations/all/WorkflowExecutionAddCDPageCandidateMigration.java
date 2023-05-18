/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.persistence.HQuery.excludeAuthority;

import static software.wings.service.impl.WorkflowExecutionServiceHelper.calculateCdPageCandidate;

import io.harness.migrations.Migration;
import io.harness.persistence.HIterator;

import software.wings.beans.WorkflowExecution;
import software.wings.beans.WorkflowExecution.WorkflowExecutionKeys;
import software.wings.dl.WingsPersistence;

import com.google.inject.Inject;
import dev.morphia.query.Sort;
import dev.morphia.query.UpdateOperations;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WorkflowExecutionAddCDPageCandidateMigration implements Migration {
  private final String DEBUG_LINE = "WORKFLOW_EXECUTION_MIGRATION: ";

  @Inject private WingsPersistence wingsPersistence;

  @Override
  public void migrate() {
    int count = 0;
    log.info(DEBUG_LINE + "Starting migration");
    try (HIterator<WorkflowExecution> iterator =
             new HIterator<>(wingsPersistence.createQuery(WorkflowExecution.class, excludeAuthority)
                                 .field(WorkflowExecutionKeys.cdPageCandidate)
                                 .doesNotExist()
                                 .order(Sort.descending(WorkflowExecutionKeys.createdAt))
                                 .fetch())) {
      for (WorkflowExecution workflowExecution : iterator) {
        migrate(workflowExecution);
        count++;
      }
      log.info(DEBUG_LINE + "Completed migrating {} records", count);
    } catch (Exception e) {
      log.error(DEBUG_LINE + "Failed to complete migration", e);
    }
  }

  private void migrate(WorkflowExecution workflowExecution) {
    try {
      String pipelineExecutionId = workflowExecution.getPipelineExecutionId(); // isEmpty --> Candidate else not
      String pipelineResumeId =
          workflowExecution.getPipelineResumeId(); // if pipelineResumeId is empty--> then pipeline was never resumed.
                                                   // Latest pipeline resume is still false in this case
      boolean latestPipelineResume =
          workflowExecution
              .isLatestPipelineResume(); // if pipelineResumeId is NotEmpty--> then latestPipelineResume must be true
      String accountId = workflowExecution.getAccountId();

      boolean cdPageCandidate =
          calculateCdPageCandidate(pipelineExecutionId, pipelineResumeId, latestPipelineResume, accountId);
      UpdateOperations<WorkflowExecution> updateOps = wingsPersistence.createUpdateOperations(WorkflowExecution.class)
                                                          .set(WorkflowExecutionKeys.cdPageCandidate, cdPageCandidate);
      wingsPersistence.update(workflowExecution, updateOps);

    } catch (Exception e) {
      log.error(
          DEBUG_LINE + "Exception occurred while processing workflowExecution:[{}]", workflowExecution.getUuid(), e);
    }
  }
}
