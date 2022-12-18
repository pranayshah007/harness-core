/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.exception.ExceptionLogger;
import io.harness.exception.WingsException;
import io.harness.exception.WingsException.ExecutionContext;
import io.harness.migrations.Migration;
import io.harness.persistence.HIterator;

import software.wings.beans.WorkflowExecution;
import software.wings.beans.WorkflowExecution.WorkflowExecutionKeys;
import software.wings.dl.WingsPersistence;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.UpdateOperations;

@Slf4j
public class UpdateWorkflowExecutionDuration implements Migration {
  @Inject private WingsPersistence wingsPersistence;

  @Override
  public void migrate() {
    try (HIterator<WorkflowExecution> iterator =
             new HIterator<>(wingsPersistence.createQuery(WorkflowExecution.class, excludeAuthority)
                                 .project(WorkflowExecutionKeys.uuid, true)
                                 .project(WorkflowExecutionKeys.startTs, true)
                                 .project(WorkflowExecutionKeys.endTs, true)
                                 .fetch())) {
      for (WorkflowExecution workflowExecution : iterator) {
        migrate(workflowExecution);
      }
    }
  }

  public void migrate(WorkflowExecution workflowExecution) {
    if (workflowExecution.getStartTs() == null || workflowExecution.getEndTs() == null) {
      return;
    }
    try {
      UpdateOperations<WorkflowExecution> updateOps =
          wingsPersistence.createUpdateOperations(WorkflowExecution.class)
              .set(WorkflowExecutionKeys.duration, workflowExecution.getEndTs() - workflowExecution.getStartTs());

      wingsPersistence.update(workflowExecution, updateOps);
    } catch (WingsException exception) {
      exception.addContext(WorkflowExecution.class, workflowExecution.getUuid());
      ExceptionLogger.logProcessedMessages(exception, ExecutionContext.MANAGER, log);
    } catch (RuntimeException exception) {
      log.error("", exception);
    }
  }
}
