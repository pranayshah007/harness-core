/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.beans.ExecutionInterruptType.CONTINUE_PIPELINE_STAGE;
import static io.harness.beans.ExecutionInterruptType.CONTINUE_WITH_DEFAULTS;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static software.wings.sm.ExecutionInterrupt.ExecutionInterruptBuilder.anExecutionInterrupt;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.exception.InvalidRequestException;
import io.harness.persistence.HPersistence;
import io.harness.tasks.ResponseData;
import io.harness.waiter.OldNotifyCallback;

import software.wings.api.ContinuePipelineResponseData;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.sm.StateExecutionInstance.StateExecutionInstanceKeys;
import software.wings.sm.states.EnvLoopState;
import software.wings.sm.states.WorkflowState;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import dev.morphia.query.UpdateOperations;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Callback method for handling notify callback from wait notify engine.
 *
 * @author Rishi
 */
@OwnedBy(CDC)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
@Data
@NoArgsConstructor
public class PipelineContinueWithInputsCallback implements OldNotifyCallback {
  @Inject private StateMachineExecutor stateMachineExecutor;
  @Inject private WorkflowExecutionService workflowExecutionService;
  @Inject private WingsPersistence wingsPersistence;
  @Inject private ExecutionInterruptManager executionInterruptManager;

  private String appId;
  private String executionUuid;
  private String stateExecutionInstanceId;
  private String pipelineStageElementId;

  /**
   * Instantiates a new state machine resume callback.
   *
   * @param appId                    the app id
   * @param stateExecutionInstanceId the state execution instance id
   */
  public PipelineContinueWithInputsCallback(
      String appId, String executionUuid, String stateExecutionInstanceId, String pipelineStageElementId) {
    this.appId = appId;
    this.executionUuid = executionUuid;
    this.stateExecutionInstanceId = stateExecutionInstanceId;
    this.pipelineStageElementId = pipelineStageElementId;
  }

  @Override
  public void notify(Map<String, ResponseData> response) {
    String waitId = StateMachineExecutor.getContinuePipelineWaitId(pipelineStageElementId, executionUuid);
    ContinuePipelineResponseData responseData = null;
    if (isNotEmpty(response) && response.get(waitId) != null) {
      responseData = (ContinuePipelineResponseData) response.get(waitId);
    }

    if (responseData == null) {
      throw new InvalidRequestException("Error in handle notify for PipelineContinueCallback, no responseData present");
    }

    ExecutionContextImpl context =
        stateMachineExecutor.getExecutionContext(appId, executionUuid, stateExecutionInstanceId);
    StateExecutionInstance stateExecutionInstance =
        workflowExecutionService.getStateExecutionInstancePipelineStage(appId, executionUuid, pipelineStageElementId);
    StateMachine stateMachine = context.getStateMachine();
    software.wings.sm.State currentState = stateMachineExecutor.getStateForExecution(context, stateExecutionInstance);
    WorkflowState workflowState = (WorkflowState) currentState;

    if (responseData.getInterrupt() != null
        && responseData.getInterrupt().getExecutionInterruptType() == CONTINUE_WITH_DEFAULTS) {
      executionInterruptManager.seize(responseData.getInterrupt());
    } else if (isNotEmpty(responseData.getWorkflowVariables())) {
      Map<String, String> currentVariableValues = workflowState.getWorkflowVariables();
      for (Map.Entry<String, String> variableValue : responseData.getWorkflowVariables().entrySet()) {
        currentVariableValues.put(variableValue.getKey(), variableValue.getValue());
        if (workflowState instanceof EnvLoopState) {
          EnvLoopState envState = (EnvLoopState) workflowState;
          if (variableValue.getKey().equals(envState.getLoopedVarName())) {
            envState.setLoopedValues(Lists.newArrayList(variableValue.getValue().trim().split("\\s*,\\s*")));
          }
        }
      }
    }

    UpdateOperations<StateExecutionInstance> ops =
        wingsPersistence.createUpdateOperations(StateExecutionInstance.class);
    ops.set(StateExecutionInstanceKeys.continued, true);
    wingsPersistence.findAndModify(
        wingsPersistence.createQuery(StateExecutionInstance.class).filter("_id", stateExecutionInstance.getUuid()), ops,
        HPersistence.returnNewOptions);

    ExecutionInterrupt executionInterrupt = anExecutionInterrupt()
                                                .appId(appId)
                                                .accountId(stateExecutionInstance.getAccountId())
                                                .executionInterruptType(CONTINUE_PIPELINE_STAGE)
                                                .executionUuid(executionUuid)
                                                .stateExecutionInstanceId(stateExecutionInstanceId)
                                                .build();
    wingsPersistence.save(executionInterrupt);

    try {
      stateMachineExecutor.sendRuntimeInputsProvidedNotification(
          context, workflowState.getUserGroupIds(), stateExecutionInstance);
      stateMachineExecutor.startExecutionRuntime(appId, executionUuid, stateExecutionInstanceId, stateMachine);
    } finally {
      executionInterruptManager.closeAlertsIfOpened(stateExecutionInstance, executionInterrupt);
      executionInterruptManager.seize(executionInterrupt);
    }
  }

  @Override
  public void notifyError(Map<String, ResponseData> response) {
    // Do nothing.
  }
}
