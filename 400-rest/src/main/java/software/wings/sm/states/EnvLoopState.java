/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.ExecutionStatusResponseData;
import io.harness.beans.RepairActionCode;
import io.harness.expression.ExpressionEvaluator;
import io.harness.ff.FeatureFlagService;
import io.harness.tasks.ResponseData;

import software.wings.api.ForkElement;
import software.wings.beans.LoopEnvStateParams;
import software.wings.beans.LoopEnvStateParams.LoopEnvStateParamsBuilder;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.ExecutionResponse.ExecutionResponseBuilder;
import software.wings.sm.State;
import software.wings.sm.StateExecutionInstance;
import software.wings.sm.StateExecutionInstanceHelper;
import software.wings.sm.StateType;
import software.wings.sm.states.ForkState.ForkStateExecutionData;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldNameConstants;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.annotations.Transient;

@OwnedBy(CDC)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
@Slf4j
@FieldNameConstants(innerTypeName = "EnvLoopStateKeys")
public class EnvLoopState extends State implements WorkflowState {
  @Getter @Setter private String workflowId;

  @Getter @Setter private String pipelineId;
  @Getter @Setter private String pipelineStageElementId;
  @Getter @Setter private int pipelineStageParallelIndex;
  @Getter @Setter private String stageName;
  @Getter @Setter private String loopedVarName;
  @Getter @Setter private List<String> loopedValues;

  @JsonIgnore @SchemaIgnore private Map<String, String> workflowVariables;

  @Setter @SchemaIgnore List<String> runtimeInputVariables;
  @Setter @SchemaIgnore long timeout;
  @Setter @SchemaIgnore List<String> userGroupIds;
  @Setter @SchemaIgnore RepairActionCode timeoutAction;

  @Getter @Setter @JsonIgnore private boolean disable;

  @Getter @Setter private String disableAssertion;
  @Getter @Setter private boolean continued;

  @Transient @Inject private StateExecutionInstanceHelper instanceHelper;
  @Transient @Inject private FeatureFlagService featureFlagService;

  public EnvLoopState(String name) {
    super(name, StateType.ENV_LOOP_STATE.name());
  }

  @Override
  public ExecutionResponse execute(ExecutionContext context) {
    return executeInternal((ExecutionContextImpl) context);
  }

  private ExecutionResponse executeInternal(ExecutionContextImpl context) {
    StateExecutionInstance stateExecutionInstance = context.getStateExecutionInstance();
    List<String> correlationIds = new ArrayList<>();

    ExecutionResponseBuilder executionResponseBuilder = ExecutionResponse.builder();
    ForkStateExecutionData forkStateExecutionData = new ForkStateExecutionData();
    List<String> forkStateNames = new ArrayList<>();
    forkStateExecutionData.setElements(new ArrayList<>());
    if (loopedValues.size() == 1 && ExpressionEvaluator.matchesVariablePattern(loopedValues.get(0))
        && loopedValues.get(0).contains(".")) {
      String resolvedValue = context.renderExpression(loopedValues.get(0));
      if (resolvedValue == null || resolvedValue.equals(loopedValues.get(0))) {
        forkStateExecutionData.setStateType(StateType.ENV_LOOP_STATE.name());
        forkStateExecutionData.setForkStateNames(Collections.singletonList(getName()));
        return executionResponseBuilder.stateExecutionData(forkStateExecutionData)
            .async(false)
            .executionStatus(ExecutionStatus.FAILED)
            .errorMessage(
                "The expression " + loopedValues.get(0) + " provided for the variable doesn't resolve to a valid value")
            .build();
      }
      loopedValues = new ArrayList<>();
      loopedValues.addAll(Arrays.asList(resolvedValue.trim().split("\\s*,\\s*")));
    }

    int i = 1;
    for (String loopedValue : loopedValues) {
      String state = getName() + "_" + i;
      forkStateNames.add(state);
      ForkElement element = ForkElement.builder().stateName(state).parentId(stateExecutionInstance.getUuid()).build();
      StateExecutionInstance childStateExecutionInstance = instanceHelper.clone(stateExecutionInstance);
      childStateExecutionInstance.setStateParams(null);

      childStateExecutionInstance.setContextElement(element);
      childStateExecutionInstance.setDisplayName(state);
      childStateExecutionInstance.setStateName(state);
      childStateExecutionInstance.setParentLoopedState(true);
      childStateExecutionInstance.setLoopedStateParams(getLoopStateParams(loopedValue, state));
      childStateExecutionInstance.setStateType(StateType.ENV_STATE.getName());
      childStateExecutionInstance.setNotifyId(element.getUuid());
      executionResponseBuilder.stateExecutionInstance(childStateExecutionInstance);
      correlationIds.add(element.getUuid());
      forkStateExecutionData.getElements().add(childStateExecutionInstance.getContextElement().getName());
      i++;
    }
    forkStateExecutionData.setForkStateNames(forkStateNames);

    trackDisableAssertionExpression(context);

    return executionResponseBuilder.stateExecutionData(forkStateExecutionData)
        .async(true)
        .correlationIds(correlationIds)
        .build();
  }

  private void trackDisableAssertionExpression(ExecutionContext context) {
    try {
      context.renderExpression(this.disableAssertion);
    } catch (Exception e) {
      log.error("Failed to render disableAssertion: accountId: {}, executionId: {}", context.getAccountId(),
          context.getWorkflowExecutionId());
    }
  }

  private LoopEnvStateParams getLoopStateParams(String loopedValue, String name) {
    LoopEnvStateParamsBuilder loopEnvStateParams = LoopEnvStateParams.builder()
                                                       .pipelineId(pipelineId)
                                                       .pipelineStageElementId(pipelineStageElementId)
                                                       .pipelineStageParallelIndex(pipelineStageParallelIndex)
                                                       .stageName(stageName)
                                                       .disable(disable)
                                                       .disableAssertion(disableAssertion)
                                                       .stepName(name)
                                                       .workflowId(workflowId);

    if (workflowVariables != null) {
      Map<String, String> newWV = new HashMap<>(workflowVariables);
      newWV.put(loopedVarName, loopedValue);
      loopEnvStateParams.workflowVariables(newWV);
    }
    return loopEnvStateParams.build();
  }

  @Override
  public ExecutionResponse handleAsyncResponse(ExecutionContext context, Map<String, ResponseData> response) {
    ExecutionResponseBuilder executionResponseBuilder = ExecutionResponse.builder();
    for (ResponseData status : response.values()) {
      ExecutionStatus executionStatus = ((ExecutionStatusResponseData) status).getExecutionStatus();
      if (executionStatus != ExecutionStatus.SUCCESS) {
        executionResponseBuilder.executionStatus(executionStatus);
      }
    }
    return executionResponseBuilder.build();
  }

  @Override
  public void parseProperties(Map<String, Object> properties) {
    boolean isDisabled =
        properties.get(EnvLoopStateKeys.disable) != null && (boolean) properties.get(EnvLoopStateKeys.disable);
    if (isDisabled && properties.get(EnvLoopStateKeys.disableAssertion) == null) {
      properties.put(EnvLoopStateKeys.disableAssertion, "true");
    }
    super.parseProperties(properties);
  }

  public Map<String, String> getWorkflowVariables() {
    return workflowVariables;
  }

  public List<String> getRuntimeInputVariables() {
    return runtimeInputVariables;
  }

  public long getTimeout() {
    return timeout;
  }

  public List<String> getUserGroupIds() {
    return userGroupIds;
  }

  public RepairActionCode getTimeoutAction() {
    return timeoutAction;
  }

  @SchemaIgnore
  @Override
  public Integer getTimeoutMillis() {
    if (super.getTimeoutMillis() == null) {
      return EnvState.ENV_STATE_TIMEOUT_MILLIS;
    }
    return super.getTimeoutMillis();
  }

  public void setWorkflowVariables(Map<String, String> workflowVariables) {
    this.workflowVariables = workflowVariables;
  }

  @Override
  public void handleAbortEvent(ExecutionContext context) {
    // nothing to handle
  }
}
