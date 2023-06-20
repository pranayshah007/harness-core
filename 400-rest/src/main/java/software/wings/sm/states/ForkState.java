/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.beans.FeatureName.SPG_CG_REJECT_PRIORITY_WHEN_FORK_STATE;

import static java.lang.String.join;
import static org.apache.commons.lang3.StringUtils.abbreviate;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.ExecutionStatusResponseData;
import io.harness.ff.FeatureFlagService;
import io.harness.serializer.KryoSerializer;
import io.harness.tasks.ResponseData;

import software.wings.api.ExecutionDataValue;
import software.wings.api.ForkElement;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.ExecutionResponse.ExecutionResponseBuilder;
import software.wings.sm.State;
import software.wings.sm.StateExecutionData;
import software.wings.sm.StateExecutionInstance;
import software.wings.sm.StateType;

import com.github.reinert.jjschema.SchemaIgnore;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Describes a ForkState by which we can fork execution to multiple threads in state machine.
 *
 * @author Rishi
 */
@OwnedBy(CDC)
@Slf4j
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class ForkState extends State {
  @Inject transient KryoSerializer kryoSerializer;
  @Inject private transient FeatureFlagService featureFlagService;
  @SchemaIgnore private List<String> forkStateNames = new ArrayList<>();

  /**
   * Instantiates a new fork state.
   *
   * @param name the name
   */
  public ForkState(String name) {
    super(name, StateType.FORK.name());
  }

  /*
   * (non-Javadoc)
   *
   * @see software.wings.sm.State#execute(software.wings.sm.ExecutionContext)
   */
  @Override
  public ExecutionResponse execute(ExecutionContext contextIntf) {
    ExecutionContextImpl context = (ExecutionContextImpl) contextIntf;
    StateExecutionInstance stateExecutionInstance = context.getStateExecutionInstance();
    List<String> correlationIds = new ArrayList<>();

    ExecutionResponseBuilder executionResponseBuilder = ExecutionResponse.builder();
    ForkStateExecutionData forkStateExecutionData = new ForkStateExecutionData();
    forkStateExecutionData.setForkStateNames(forkStateNames);
    forkStateExecutionData.setElements(new ArrayList<>());
    for (String state : forkStateNames) {
      ForkElement element = ForkElement.builder().stateName(state).parentId(stateExecutionInstance.getUuid()).build();
      StateExecutionInstance childStateExecutionInstance = kryoSerializer.clone(stateExecutionInstance);
      childStateExecutionInstance.setStateParams(null);

      childStateExecutionInstance.setContextElement(element);
      childStateExecutionInstance.setDisplayName(state);
      childStateExecutionInstance.setStateName(state);
      childStateExecutionInstance.setNotifyId(element.getUuid());
      childStateExecutionInstance.setPrevInstanceId(null);
      childStateExecutionInstance.setDelegateTaskId(null);
      childStateExecutionInstance.setContextTransition(true);
      childStateExecutionInstance.setStatus(ExecutionStatus.NEW);
      childStateExecutionInstance.setStartTs(null);
      childStateExecutionInstance.setEndTs(null);
      childStateExecutionInstance.setCreatedAt(0);
      childStateExecutionInstance.setLastUpdatedAt(0);
      childStateExecutionInstance.setHasInspection(false);
      executionResponseBuilder.stateExecutionInstance(childStateExecutionInstance);
      correlationIds.add(element.getUuid());
      forkStateExecutionData.getElements().add(childStateExecutionInstance.getContextElement().getName());
    }

    return executionResponseBuilder.stateExecutionData(forkStateExecutionData)
        .async(true)
        .correlationIds(correlationIds)
        .build();
  }

  /**
   * Handle abort event.
   *
   * @param context the context
   */
  @Override
  public void handleAbortEvent(ExecutionContext context) {
    // nothing to handle
  }

  /*
   * (non-Javadoc)
   *
   * @see software.wings.sm.State#handleAsyncResponse(software.wings.sm.ExecutionContextImpl, java.util.Map)
   */
  @Override
  public ExecutionResponse handleAsyncResponse(ExecutionContext context, Map<String, ResponseData> response) {
    ExecutionResponseBuilder executionResponseBuilder = ExecutionResponse.builder();
    for (ResponseData status : response.values()) {
      ExecutionStatus executionStatus = ((ExecutionStatusResponseData) status).getExecutionStatus();
      if (executionStatus != ExecutionStatus.SUCCESS) {
        if (isRejectStatusPriority(context, executionStatus)) {
          executionResponseBuilder.executionStatus(executionStatus);
          break;
        }
        executionResponseBuilder.executionStatus(executionStatus);
      }
    }
    final ExecutionResponse executionResponse = executionResponseBuilder.build();
    log.info("Fork state execution completed - displayName:{}, executionStatus:{}", getName(),
        executionResponse.getExecutionStatus());
    return executionResponse;
  }

  private boolean isRejectStatusPriority(ExecutionContext context, ExecutionStatus executionStatus) {
    return executionStatus == ExecutionStatus.REJECTED
        && featureFlagService.isEnabled(SPG_CG_REJECT_PRIORITY_WHEN_FORK_STATE, context.getAccountId());
  }

  @Override
  @SchemaIgnore
  public Integer getTimeoutMillis() {
    return INFINITE_TIMEOUT;
  }

  /**
   * Gets fork state names.
   *
   * @return the fork state names
   */
  @SchemaIgnore
  public List<String> getForkStateNames() {
    return forkStateNames;
  }

  /**
   * Sets fork state names.
   *
   * @param forkStateNames the fork state names
   */
  @SchemaIgnore
  public void setForkStateNames(List<String> forkStateNames) {
    this.forkStateNames = forkStateNames;
  }

  /**
   * Gets fork element name.
   *
   * @param state the state
   * @return the fork element name
   */
  @SchemaIgnore
  public String getForkElementName(String state) {
    return "Fork-" + state;
  }

  /**
   * Adds the fork state.
   *
   * @param state the state
   */
  public void addForkState(State state) {
    this.forkStateNames.add(state.getName());
  }

  /**
   * The type Fork state execution data.
   */
  public static class ForkStateExecutionData extends ElementStateExecutionData {
    private List<String> elements;
    private List<String> forkStateNames;

    /**
     * Gets elements.
     *
     * @return the elements
     */
    public List<String> getElements() {
      return elements;
    }

    /**
     * Sets elements.
     *
     * @param elements the elements
     */
    public void setElements(List<String> elements) {
      this.elements = elements;
    }

    /**
     * Gets fork state names.
     *
     * @return the fork state names
     */
    public List<String> getForkStateNames() {
      return forkStateNames;
    }

    /**
     * Sets fork state names.
     *
     * @param forkStateNames the fork state names
     */
    public void setForkStateNames(List<String> forkStateNames) {
      this.forkStateNames = forkStateNames;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, ExecutionDataValue> getExecutionDetails() {
      Map<String, ExecutionDataValue> executionDetails = super.getExecutionDetails();
      putNotNull(executionDetails, "forkStateNames",
          ExecutionDataValue.builder().displayName("Forking to").value(join(", ", forkStateNames)).build());
      return executionDetails;
    }

    @Override
    public Map<String, ExecutionDataValue> getExecutionSummary() {
      Map<String, ExecutionDataValue> executionDetails = super.getExecutionSummary();
      putNotNull(executionDetails, "forkStateNames",
          ExecutionDataValue.builder()
              .displayName("Forking to")
              .value(abbreviate(join(", ", forkStateNames), StateExecutionData.SUMMARY_PAYLOAD_LIMIT))
              .build());
      return executionDetails;
    }
  }
}
