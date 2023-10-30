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
import io.harness.serializer.KryoSerializer;
import io.harness.tasks.ResponseData;

import software.wings.beans.NameValuePair;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.ExecutionResponse.ExecutionResponseBuilder;
import software.wings.sm.State;
import software.wings.sm.StateExecutionInstance;
import software.wings.sm.StateType;

import com.github.reinert.jjschema.SchemaIgnore;
import com.google.inject.Inject;
import dev.morphia.annotations.Transient;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by rishi on 12/16/16.
 */
@OwnedBy(CDC)
@Slf4j
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class SubWorkflowState extends State {
  private List<NameValuePair> variableOverrides;

  @Transient @Inject private WorkflowExecutionService workflowExecutionService;
  @Transient @Inject private KryoSerializer kryoSerializer;

  /**
   * Instantiates a new repeat state.
   *
   * @param name the name
   */
  public SubWorkflowState(String name) {
    super(name, StateType.SUB_WORKFLOW.name());
  }

  protected SubWorkflowState(String name, String stateType) {
    super(name, stateType);
  }

  private String subWorkflowId;

  @Override
  public ExecutionResponse execute(ExecutionContext contextIntf) {
    ExecutionContextImpl context = (ExecutionContextImpl) contextIntf;
    StateExecutionInstance stateExecutionInstance = context.getStateExecutionInstance();
    List<String> correlationIds = new ArrayList<>();

    StateExecutionInstance childStateExecutionInstance = getSpawningInstance(stateExecutionInstance);
    correlationIds.add(stateExecutionInstance.getUuid());

    return ExecutionResponse.builder()
        .stateExecutionInstance(childStateExecutionInstance)
        .async(true)
        .correlationIds(correlationIds)
        .build();
  }

  protected StateExecutionInstance getSpawningInstance(StateExecutionInstance stateExecutionInstance) {
    StateExecutionInstance childStateExecutionInstance = kryoSerializer.clone(stateExecutionInstance);
    childStateExecutionInstance.setStateParams(null);

    childStateExecutionInstance.setChildStateMachineId(subWorkflowId);
    childStateExecutionInstance.setDisplayName(null);
    childStateExecutionInstance.setStateName(null);
    childStateExecutionInstance.setStateType(null);
    childStateExecutionInstance.setNotifyId(stateExecutionInstance.getUuid());
    childStateExecutionInstance.setPrevInstanceId(null);
    childStateExecutionInstance.setDelegateTaskId(null);
    childStateExecutionInstance.setContextTransition(true);
    childStateExecutionInstance.setStatus(ExecutionStatus.NEW);
    childStateExecutionInstance.setContextElement(null);
    childStateExecutionInstance.setStartTs(null);
    childStateExecutionInstance.setEndTs(null);
    childStateExecutionInstance.setCreatedAt(0);
    childStateExecutionInstance.setLastUpdatedAt(0);
    if (childStateExecutionInstance.getStateExecutionMap() != null) {
      childStateExecutionInstance.getStateExecutionMap().clear();
    }
    return childStateExecutionInstance;
  }

  /*
   * (non-Javadoc)
   *
   * @see software.wings.sm.State#handleAsyncResponse(software.wings.sm.ExecutionContextImpl, java.util.Map)
   */
  @Override
  public ExecutionResponse handleAsyncResponse(ExecutionContext context, Map<String, ResponseData> response) {
    ExecutionResponseBuilder executionResponseBuilder = ExecutionResponse.builder();
    handleStatusSummary(workflowExecutionService, context, response, executionResponseBuilder);
    return executionResponseBuilder.build();
  }

  protected void handleStatusSummary(WorkflowExecutionService workflowExecutionService, ExecutionContext context,
      Map<String, ResponseData> response, ExecutionResponseBuilder executionResponseBuilder) {
    ExecutionStatus executionStatus =
        ((ExecutionStatusResponseData) response.values().iterator().next()).getExecutionStatus();
    if (executionStatus != ExecutionStatus.SUCCESS) {
      executionResponseBuilder.executionStatus(executionStatus);
    }
    log.debug("Subworkflow state execution completed - displayName:{}", getName());

    ElementStateExecutionData stateExecutionData = (ElementStateExecutionData) context.getStateExecutionData();
    stateExecutionData.setElementStatusSummary(workflowExecutionService.getElementsSummary(
        context.getAppId(), context.getWorkflowExecutionId(), context.getStateExecutionInstanceId()));
  }

  @Override
  public void handleAbortEvent(ExecutionContext context) {
    // nothing to handle
  }

  @Override
  @SchemaIgnore
  public Integer getTimeoutMillis() {
    return INFINITE_TIMEOUT;
  }

  public String getSubWorkflowId() {
    return subWorkflowId;
  }

  public void setSubWorkflowId(String subWorkflowId) {
    this.subWorkflowId = subWorkflowId;
  }

  public List<NameValuePair> getVariableOverrides() {
    return variableOverrides;
  }

  public void setVariableOverrides(List<NameValuePair> variableOverrides) {
    this.variableOverrides = variableOverrides;
  }
}
