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

import software.wings.beans.Application;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.WorkflowExecution.WorkflowExecutionKeys;
import software.wings.dl.WingsPersistence;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Sort;
import org.mongodb.morphia.query.UpdateOperations;

@Slf4j
public class UpdateWorkflowExecutionAccountId implements Migration {
  @Inject private WingsPersistence wingsPersistence;

  @Override
  public void migrate() {
    int count = 0;
    try (HIterator<WorkflowExecution> iterator =
             new HIterator<>(wingsPersistence.createQuery(WorkflowExecution.class, excludeAuthority)
                                 .order(Sort.descending(WorkflowExecutionKeys.createdAt))
                                 .field(WorkflowExecutionKeys.accountId)
                                 .doesNotExist()
                                 .project(WorkflowExecutionKeys.appId, true)
                                 .fetch())) {
      for (WorkflowExecution workflowExecution : iterator) {
        migrate(workflowExecution);
        count++;
      }
      if (count % 1000 == 0) {
        log.info("Completed migrating {} records", count);
      }
    } catch (Exception e) {
      log.error("Failed to complete migration", e);
    }
  }

  public void migrate(WorkflowExecution workflowExecution) {
    try {
      Application application = wingsPersistence.get(Application.class, workflowExecution.getAppId());
      if (application != null) {
        UpdateOperations<WorkflowExecution> updateOps =
            wingsPersistence.createUpdateOperations(WorkflowExecution.class)
                .set(WorkflowExecutionKeys.accountId, application.getAccountId());
        wingsPersistence.update(workflowExecution, updateOps);
      } else {
        log.info("No app found with appID:[{}]", workflowExecution.getAppId());
      }

    } catch (WingsException exception) {
      exception.addContext(WorkflowExecution.class, workflowExecution.getUuid());
      ExceptionLogger.logProcessedMessages(exception, ExecutionContext.MANAGER, log);
    } catch (Exception e) {
      log.error("Exception occurred while processing workflowExecution:[{}]", workflowExecution.getUuid(), e);
    }
  }
}
