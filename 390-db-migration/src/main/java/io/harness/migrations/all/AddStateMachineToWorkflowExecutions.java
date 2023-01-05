/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.migrations.Migration;
import io.harness.persistence.HIterator;
import io.harness.threading.Morpheus;

import software.wings.beans.WorkflowExecution;
import software.wings.beans.WorkflowExecution.WorkflowExecutionKeys;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.sm.StateMachine;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AddStateMachineToWorkflowExecutions implements Migration {
  @Inject private WingsPersistence wingsPersistence;
  @Inject private WorkflowExecutionService workflowExecutionService;

  @Override
  @SuppressWarnings("PMD")
  public void migrate() {
    try (HIterator<WorkflowExecution> workflowExecutionIterator =
             new HIterator<>(wingsPersistence.createQuery(WorkflowExecution.class, excludeAuthority)
                                 .field(WorkflowExecutionKeys.stateMachine)
                                 .doesNotExist()
                                 .field(WorkflowExecutionKeys.stateMachineId)
                                 .exists()
                                 .project(WorkflowExecutionKeys.uuid, true)
                                 .project(WorkflowExecutionKeys.appId, true)
                                 .project(WorkflowExecutionKeys.stateMachineId, true)
                                 .fetch())) {
      while (workflowExecutionIterator.hasNext()) {
        WorkflowExecution workflowExecution = workflowExecutionIterator.next();
        try {
          StateMachine stateMachine = workflowExecutionService.obtainStateMachine(workflowExecution);
          if (stateMachine != null) {
            final Query<WorkflowExecution> query = wingsPersistence.createQuery(WorkflowExecution.class)
                                                       .filter(WorkflowExecutionKeys.uuid, workflowExecution.getUuid());

            final UpdateOperations<WorkflowExecution> updateOperations =
                wingsPersistence.createUpdateOperations(WorkflowExecution.class)
                    .set(WorkflowExecutionKeys.stateMachine, stateMachine)
                    .unset(WorkflowExecutionKeys.stateMachineId);

            wingsPersistence.update(query, updateOperations);
          }

        } catch (Throwable exception) {
          log.error("Exception while migrating workflowExecution for {}", workflowExecution.getUuid(), exception);
        }

        Morpheus.sleep(Duration.ofMillis(10));
      }
    }
  }
}
