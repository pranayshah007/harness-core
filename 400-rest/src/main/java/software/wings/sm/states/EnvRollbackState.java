/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.beans.ExecutionStatus.SKIPPED;

import static software.wings.api.EnvStateExecutionData.Builder.anEnvStateExecutionData;

import static java.util.Arrays.asList;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.RepairActionCode;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.WingsException;
import io.harness.tasks.ResponseData;

import software.wings.api.EnvStateExecutionData;
import software.wings.api.SkipStateExecutionData;
import software.wings.beans.PipelineExecution;
import software.wings.beans.PipelineStageExecution;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.WorkflowExecution.WorkflowExecutionKeys;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.service.intfc.WorkflowService;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.ExecutionResponse.ExecutionResponseBuilder;
import software.wings.sm.State;
import software.wings.sm.StateType;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.github.reinert.jjschema.Attributes;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.inject.Inject;
import dev.morphia.annotations.Transient;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldNameConstants;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;

@OwnedBy(CDC)
@Attributes(title = "EnvRollback")
@Slf4j
@FieldNameConstants(innerTypeName = "EnvRollbackStateKeys")
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class EnvRollbackState extends State implements WorkflowState {
  @Setter @SchemaIgnore private String pipelineStageElementId;
  @Setter @SchemaIgnore private int pipelineStageParallelIndex;
  @Setter @SchemaIgnore private String stageName;
  @Getter @Setter private boolean continued;
  @Getter @Setter private String disableAssertion;
  @Attributes(required = true, title = "Workflow") @Getter @Setter private String workflowId;

  @JsonIgnore @SchemaIgnore private Map<String, String> workflowVariables;

  @Setter @SchemaIgnore List<String> runtimeInputVariables;
  @Setter @SchemaIgnore long timeout;
  @Setter @SchemaIgnore List<String> userGroupIds;
  @Setter @SchemaIgnore RepairActionCode timeoutAction;

  @Transient @Inject private WorkflowExecutionService executionService;
  private static String ROLLBACK_PREFIX = "Rollback-";

  public EnvRollbackState(String name) {
    super(name, StateType.ENV_ROLLBACK_STATE.name());
  }

  @Override
  public ExecutionResponse execute(ExecutionContext context) {
    String workflowExecutionId;
    ExecutionContextImpl executionContext = (ExecutionContextImpl) context;
    String workflowId = context.getWorkflowId();

    EnvStateExecutionData envStateExecutionData = anEnvStateExecutionData()
                                                      .withWorkflowId(workflowId)
                                                      .withPipelineStageElementId(pipelineStageElementId)
                                                      .withPipelineStageParallelIndex(pipelineStageParallelIndex)
                                                      .build();

    EnvStateExecutionData stateExecutionData =
        (EnvStateExecutionData) executionContext.getStateExecutionInstance().getStateExecutionMap().get(
            this.getName().replace(ROLLBACK_PREFIX, ""));
    if (stateExecutionData == null) {
      String pipelineExecutionId = executionContext.getStateExecutionInstance().getExecutionUuid();
      PipelineExecution pipelineExecution =
          executionService
              .getWorkflowExecution(context.getAppId(), pipelineExecutionId, WorkflowExecutionKeys.pipelineExecution)
              .getPipelineExecution();
      PipelineStageExecution pipelineStageExecution =
          pipelineExecution.getPipelineStageExecutions()
              .stream()
              .filter(it -> it.getStateName().equals(this.getName().replace(ROLLBACK_PREFIX, "")))
              .findFirst()
              .orElseThrow(() -> { throw new InvalidArgumentsException("Pipeline stage not found to rollback"); });
      workflowExecutionId = pipelineStageExecution.getWorkflowExecutions().get(0).getUuid();
    } else {
      workflowExecutionId = stateExecutionData.getWorkflowExecutionId();
    }

    String[] wfeFields = {WorkflowExecutionKeys.accountId, WorkflowExecutionKeys.appId, WorkflowExecutionKeys.artifacts,
        WorkflowExecutionKeys.envId, WorkflowExecutionKeys.executionArgs, WorkflowExecutionKeys.infraDefinitionIds,
        WorkflowExecutionKeys.infraMappingIds, WorkflowExecutionKeys.name, WorkflowExecutionKeys.pipelineExecutionId,
        WorkflowExecutionKeys.startTs, WorkflowExecutionKeys.status, WorkflowExecutionKeys.workflowType};
    WorkflowExecution workflowExecution =
        executionService.getWorkflowExecution(executionContext.getAppId(), workflowExecutionId, wfeFields);
    WorkflowExecution rollbackExecution = null;
    try {
      rollbackExecution =
          executionService.triggerRollbackExecutionWorkflow(executionContext.getAppId(), workflowExecution, true);
    } catch (WingsException ex) {
      SkipStateExecutionData skipStateExecutionData = SkipStateExecutionData.builder()
                                                          .pipelineStageParallelIndex(pipelineStageParallelIndex)
                                                          .workflowId(workflowId)
                                                          .stateName(getName())
                                                          .stateType("ENV_STATE")
                                                          .pipelineStageElementId(pipelineStageElementId)
                                                          .build();
      skipStateExecutionData.setErrorMsg(ex.getMessage());
      return ExecutionResponse.builder()
          .executionStatus(SKIPPED)
          .errorMessage(ex.getMessage())
          .stateExecutionData(skipStateExecutionData)
          .build();
    }

    envStateExecutionData.setWorkflowExecutionId(rollbackExecution.getUuid());
    envStateExecutionData.setOrchestrationWorkflowType(rollbackExecution.getOrchestrationType());
    return ExecutionResponse.builder()
        .async(true)
        .correlationIds(asList(rollbackExecution.getUuid()))
        .stateExecutionData(envStateExecutionData)
        .build();
  }

  @Override
  public ExecutionResponse handleAsyncResponse(ExecutionContext context, Map<String, ResponseData> response) {
    // EnvExecutionResponseData is inner class, maybe we should outter it
    EnvState.EnvExecutionResponseData responseData =
        (EnvState.EnvExecutionResponseData) response.values().iterator().next();
    ExecutionResponseBuilder executionResponseBuilder =
        ExecutionResponse.builder().executionStatus(responseData.getStatus());

    return executionResponseBuilder.build();
  }

  @Override
  public void handleAbortEvent(ExecutionContext context) {}

  @Override
  public List<String> getRuntimeInputVariables() {
    return runtimeInputVariables;
  }

  @Override
  public long getTimeout() {
    return timeout;
  }

  @Override
  public List<String> getUserGroupIds() {
    return userGroupIds;
  }

  @Override
  public RepairActionCode getTimeoutAction() {
    return timeoutAction;
  }

  @Override
  public Map<String, String> getWorkflowVariables() {
    return workflowVariables;
  }

  @Override
  public String getPipelineStageElementId() {
    return pipelineStageElementId;
  }

  @Override
  public int getPipelineStageParallelIndex() {
    return pipelineStageParallelIndex;
  }

  @Override
  public String getStageName() {
    return stageName;
  }

  @Override
  public ExecutionResponse checkDisableAssertion(
      ExecutionContextImpl context, WorkflowService workflowService, Logger log) {
    return WorkflowState.super.checkDisableAssertion(context, workflowService, log);
  }
}
