/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.status.handlers;

import static io.harness.beans.ExecutionStatus.ABORTED;
import static io.harness.beans.ExecutionStatus.DISCONTINUING;
import static io.harness.beans.ExecutionStatus.ERROR;
import static io.harness.beans.ExecutionStatus.EXPIRED;
import static io.harness.beans.ExecutionStatus.FAILED;
import static io.harness.beans.ExecutionStatus.NEW;
import static io.harness.beans.ExecutionStatus.PAUSED;
import static io.harness.beans.ExecutionStatus.REJECTED;
import static io.harness.beans.ExecutionStatus.RUNNING;
import static io.harness.beans.ExecutionStatus.WAITING;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.validation.Validator.notNullCheck;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ExecutionStatus;
import io.harness.persistence.HPersistence;

import software.wings.beans.PipelineStageExecution;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.WorkflowExecution.WorkflowExecutionKeys;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.WorkflowExecutionService;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.util.List;
import javax.validation.constraints.NotNull;

@OwnedBy(HarnessTeam.CDC)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class WorkflowStatusPropagatorHelper {
  @Inject WorkflowExecutionService workflowExecutionService;
  @Inject WingsPersistence wingsPersistence;

  WorkflowExecution updateStatus(
      String appId, String uuid, List<ExecutionStatus> allowedFromStatuses, ExecutionStatus toStatus) {
    Query<WorkflowExecution> query = wingsPersistence.createQuery(WorkflowExecution.class)
                                         .filter(WorkflowExecutionKeys.appId, appId)
                                         .filter(WorkflowExecutionKeys.uuid, uuid)
                                         .field(WorkflowExecutionKeys.status)
                                         .in(allowedFromStatuses);
    UpdateOperations<WorkflowExecution> ops =
        wingsPersistence.createUpdateOperations(WorkflowExecution.class).set(WorkflowExecutionKeys.status, toStatus);
    return wingsPersistence.findAndModify(query, ops, HPersistence.returnNewOptions);
  }

  @NotNull
  public WorkflowExecution obtainExecution(@NotNull String appId, @NotNull String uuid) {
    // ONLY TWO FIELDS ARE READ FROM THE CALLER
    WorkflowExecution execution = workflowExecutionService.getWorkflowExecution(
        appId, uuid, WorkflowExecutionKeys.pipelineExecutionId, WorkflowExecutionKeys.pipelineExecution);
    notNullCheck("Workflow Execution null for executionId: " + uuid, execution);
    return execution;
  }

  @NotNull
  public boolean shouldResumePipeline(String appId, String pipelineExecutionId) {
    if (pipelineExecutionId == null) {
      return false;
    }
    List<PipelineStageExecution> stageExecutions = getPipelineStageExecutions(appId, pipelineExecutionId);
    return stageExecutions.stream().noneMatch(se
        -> PAUSED == se.getStatus() || ABORTED == se.getStatus() || ERROR == se.getStatus() || FAILED == se.getStatus()
            || REJECTED == se.getStatus() || EXPIRED == se.getStatus());
  }

  public boolean shouldPausePipeline(String appId, String pipelineExecutionId) {
    if (pipelineExecutionId == null) {
      return false;
    }
    List<PipelineStageExecution> stageExecutions = getPipelineStageExecutions(appId, pipelineExecutionId);
    return stageExecutions.stream().noneMatch(se
        -> RUNNING == se.getStatus() || WAITING == se.getStatus() || NEW == se.getStatus()
            || DISCONTINUING == se.getStatus());
  }

  private List<PipelineStageExecution> getPipelineStageExecutions(String appId, String pipelineExecutionId) {
    WorkflowExecution execution = wingsPersistence.createQuery(WorkflowExecution.class)
                                      .filter(WorkflowExecutionKeys.appId, appId)
                                      .filter(WorkflowExecutionKeys.uuid, pipelineExecutionId)
                                      .project(WorkflowExecutionKeys.pipelineExecution, true)
                                      .get();
    notNullCheck("Workflow Execution null for executionId: " + pipelineExecutionId, execution);
    return execution.getPipelineExecution().getPipelineStageExecutions();
  }

  public void refreshPipelineExecution(String appId, String pipelineExecutionId) {
    if (isNotEmpty(pipelineExecutionId)) {
      String[] fields = {WorkflowExecutionKeys.appId, WorkflowExecutionKeys.createdAt,
          WorkflowExecutionKeys.pipelineExecution, WorkflowExecutionKeys.status, WorkflowExecutionKeys.triggeredBy,
          WorkflowExecutionKeys.uuid};
      workflowExecutionService.refreshPipelineExecution(
          workflowExecutionService.getWorkflowExecution(appId, pipelineExecutionId, fields));
    }
  }

  public void refreshPipelineExecution(WorkflowExecution workflowExecution) {
    workflowExecutionService.refreshPipelineExecution(workflowExecution);
  }
}
