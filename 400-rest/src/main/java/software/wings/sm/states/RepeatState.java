/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.join;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.abbreviate;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ExecutionStatus;
import io.harness.context.ContextElementType;
import io.harness.exception.WingsException;
import io.harness.serializer.KryoSerializer;
import io.harness.tasks.ResponseData;

import software.wings.api.ExecutionDataValue;
import software.wings.beans.ExecutionStrategy;
import software.wings.common.WingsExpressionProcessorFactory;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.sm.ContextElement;
import software.wings.sm.ElementNotifyResponseData;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.ExecutionResponse.ExecutionResponseBuilder;
import software.wings.sm.ExpressionProcessor;
import software.wings.sm.State;
import software.wings.sm.StateExecutionData;
import software.wings.sm.StateExecutionInstance;
import software.wings.sm.StateType;
import software.wings.stencils.DefaultValue;

import com.github.reinert.jjschema.Attributes;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.inject.Inject;
import dev.morphia.annotations.Transient;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

/**
 * The Class RepeatState.
 *
 * @author Rishi
 */
@OwnedBy(CDC)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
@Attributes
@Slf4j
public class RepeatState extends State {
  @SchemaIgnore private ContextElementType repeatElementType;
  @DefaultValue("")
  @Attributes(required = true, title = "Repeat Element Expression")
  private String repeatElementExpression;
  @Attributes(required = true, title = "Execution Strategy") private ExecutionStrategy executionStrategy;
  @SchemaIgnore private String executionStrategyExpression;

  @SchemaIgnore private String repeatTransitionStateName;

  @Transient @Inject private WorkflowExecutionService workflowExecutionService;
  @Transient @Inject private KryoSerializer kryoSerializer;
  @Transient @Inject private RepeatStateHelper repeatStateHelper;

  /**
   * Instantiates a new repeat state.
   *
   * @param name the name
   */
  public RepeatState(String name) {
    super(name, StateType.REPEAT.name());
  }

  public void setWorkflowExecutionService(WorkflowExecutionService workflowExecutionService) {
    this.workflowExecutionService = workflowExecutionService;
  }

  public void setKryoSerializer(KryoSerializer kryoSerializer) {
    this.kryoSerializer = kryoSerializer;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ExecutionResponse execute(ExecutionContext context) {
    RepeatStateExecutionData repeatStateExecutionData = (RepeatStateExecutionData) context.getStateExecutionData();
    return execute((ExecutionContextImpl) context, repeatStateExecutionData);
  }

  /**
   * Execute.
   *
   * @param context                  the context
   * @param repeatStateExecutionData the repeat state execution data
   * @return the execution response
   */
  ExecutionResponse execute(ExecutionContextImpl context, RepeatStateExecutionData repeatStateExecutionData) {
    if (repeatStateExecutionData == null) {
      repeatStateExecutionData = new RepeatStateExecutionData();
    }
    List<ContextElement> repeatElements = repeatStateExecutionData.getRepeatElements();
    try {
      if (isEmpty(repeatElements)) {
        if (repeatElementExpression != null) {
          repeatElements = (List<ContextElement>) context.evaluateExpression(repeatElementExpression);
          repeatStateExecutionData.setRepeatElements(repeatElements);
          if (isNotEmpty(repeatElements)) {
            repeatStateExecutionData.setRepeatElementType(repeatElements.get(0).getElementType());
          }
        }
      }
    } catch (Exception ex) {
      throw new WingsException(ex);
    }

    if (repeatTransitionStateName == null) {
      return ExecutionResponse.builder().executionStatus(ExecutionStatus.FAILED).build();
    }

    if (executionStrategyExpression != null) {
      try {
        executionStrategy = (ExecutionStrategy) context.evaluateExpression(executionStrategyExpression);
      } catch (Exception ex) {
        log.error("Error in evaluating executionStrategy... default to SERIAL", ex);
      }
    }

    if (executionStrategy == null) {
      log.info("RepeatState: {} falling to default  executionStrategy : SERIAL", getName());
      executionStrategy = ExecutionStrategy.SERIAL;
    }

    repeatStateExecutionData.setExecutionStrategy(executionStrategy);

    StateExecutionInstance stateExecutionInstance = context.getStateExecutionInstance();

    ExecutionResponseBuilder executionResponseBuilder = ExecutionResponse.builder();

    if (isNotEmpty(repeatElements)) {
      List<String> correlationIds = new ArrayList<>();
      if (executionStrategy == ExecutionStrategy.PARALLEL) {
        for (ContextElement repeatElement : repeatElements) {
          processChildState(stateExecutionInstance, correlationIds, executionResponseBuilder, repeatElement);
        }
      } else {
        Integer repeatElementIndex = 0;
        repeatStateExecutionData.setRepeatElementIndex(0);
        ContextElement repeatElement = repeatElements.get(repeatElementIndex);
        processChildState(stateExecutionInstance, correlationIds, executionResponseBuilder, repeatElement);
      }

      executionResponseBuilder.async(true);
      executionResponseBuilder.correlationIds(correlationIds);
    }

    executionResponseBuilder.stateExecutionData(repeatStateExecutionData);
    return executionResponseBuilder.build();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ExecutionResponse handleAsyncResponse(ExecutionContext context, Map<String, ResponseData> response) {
    ExecutionStatus executionStatus = ExecutionStatus.SUCCESS;
    for (ResponseData status : response.values()) {
      ExecutionStatus responseStatus = ((ElementNotifyResponseData) status).getExecutionStatus();
      if (responseStatus != ExecutionStatus.SUCCESS) {
        executionStatus = responseStatus;
      }
    }

    RepeatStateExecutionData repeatStateExecutionData = (RepeatStateExecutionData) context.getStateExecutionData();
    List<ContextElement> repeatElements = repeatStateExecutionData.getRepeatElements();

    executionStrategy = repeatStateExecutionData.getExecutionStrategy();
    if (executionStrategy == ExecutionStrategy.PARALLEL || executionStatus != ExecutionStatus.SUCCESS
        || repeatStateExecutionData.indexReachedMax()) {
      ExecutionResponseBuilder executionResponseBuilder = ExecutionResponse.builder();
      executionResponseBuilder.executionStatus(executionStatus);

      repeatStateExecutionData.setElementStatusSummary(workflowExecutionService.getElementsSummary(
          context.getAppId(), context.getWorkflowExecutionId(), context.getStateExecutionInstanceId()));
      executionResponseBuilder.stateExecutionData(repeatStateExecutionData);
      return executionResponseBuilder.build();
    } else {
      ExecutionResponseBuilder executionResponseBuilder = ExecutionResponse.builder();

      Integer repeatElementIndex = repeatStateExecutionData.getRepeatElementIndex();
      if (repeatElementIndex == null) {
        repeatElementIndex = 0;
      }
      repeatElementIndex++;
      repeatStateExecutionData.setRepeatElementIndex(repeatElementIndex);
      ContextElement repeatElement = repeatElements.get(repeatElementIndex);

      StateExecutionInstance stateExecutionInstance = ((ExecutionContextImpl) context).getStateExecutionInstance();
      List<String> correlationIds = new ArrayList<>();
      processChildState(stateExecutionInstance, correlationIds, executionResponseBuilder, repeatElement);

      executionResponseBuilder.async(true);
      executionResponseBuilder.correlationIds(correlationIds);

      executionResponseBuilder.stateExecutionData(repeatStateExecutionData);
      return executionResponseBuilder.build();
    }
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

  /**
   * {@inheritDoc}
   */
  @Override
  public void resolveProperties() {
    ExpressionProcessor expressionProcessor =
        WingsExpressionProcessorFactory.getMatchingExpressionProcessor(repeatElementExpression, null);
    repeatElementType =
        expressionProcessor == null ? ContextElementType.OTHER : expressionProcessor.getContextElementType();
  }

  private void processChildState(StateExecutionInstance stateExecutionInstance, List<String> correlationIds,
      ExecutionResponseBuilder executionResponseBuilder, ContextElement repeatElement) {
    String notifyId = stateExecutionInstance.getUuid() + "-repeat-" + repeatElement.getUuid();
    StateExecutionInstance childStateExecutionInstance = kryoSerializer.clone(stateExecutionInstance);
    childStateExecutionInstance.setStateParams(null);
    childStateExecutionInstance.setDisplayName(repeatTransitionStateName);
    childStateExecutionInstance.setStateName(repeatTransitionStateName);
    childStateExecutionInstance.setNotifyId(notifyId);
    childStateExecutionInstance.setPrevInstanceId(null);
    childStateExecutionInstance.setDelegateTaskId(null);
    childStateExecutionInstance.setSubGraphFilterId(repeatElement.getUuid());
    childStateExecutionInstance.getContextElements().push(repeatElement);
    childStateExecutionInstance.setContextElement(repeatElement);
    childStateExecutionInstance.setContextTransition(true);
    childStateExecutionInstance.setStatus(ExecutionStatus.NEW);
    childStateExecutionInstance.setStartTs(null);
    childStateExecutionInstance.setEndTs(null);
    childStateExecutionInstance.setCreatedAt(0);
    childStateExecutionInstance.setLastUpdatedAt(0);

    executionResponseBuilder.stateExecutionInstance(childStateExecutionInstance);
    correlationIds.add(notifyId);
  }

  /**
   * Gets repeat element type.
   *
   * @return the repeat element type
   */
  @SchemaIgnore
  public ContextElementType getRepeatElementType() {
    return repeatElementType;
  }

  /**
   * Sets repeat element type.
   *
   * @param repeatElementType the repeat element type
   */
  @SchemaIgnore
  public void setRepeatElementType(ContextElementType repeatElementType) {
    this.repeatElementType = repeatElementType;
  }

  /**
   * Gets repeat element expression.
   *
   * @return the repeat element expression
   */
  public String getRepeatElementExpression() {
    return repeatElementExpression;
  }

  /**
   * Sets repeat element expression.
   *
   * @param repeatElementExpression the repeat element expression
   */
  public void setRepeatElementExpression(String repeatElementExpression) {
    this.repeatElementExpression = repeatElementExpression;
  }

  /**
   * Gets execution strategy.
   *
   * @return the execution strategy
   */
  public ExecutionStrategy getExecutionStrategy() {
    return executionStrategy;
  }

  /**
   * Sets execution strategy.
   *
   * @param executionStrategy the execution strategy
   */
  public void setExecutionStrategy(ExecutionStrategy executionStrategy) {
    this.executionStrategy = executionStrategy;
  }

  /**
   * Gets execution strategy expression.
   *
   * @return the execution strategy expression
   */
  public String getExecutionStrategyExpression() {
    return executionStrategyExpression;
  }

  /**
   * Sets execution strategy expression.
   *
   * @param executionStrategyExpression the execution strategy expression
   */
  public void setExecutionStrategyExpression(String executionStrategyExpression) {
    this.executionStrategyExpression = executionStrategyExpression;
  }

  /**
   * Gets repeat transition state name.
   *
   * @return the repeat transition state name
   */
  @SchemaIgnore
  public String getRepeatTransitionStateName() {
    return repeatTransitionStateName;
  }

  /**
   * Sets repeat transition state name.
   *
   * @param repeatTransitionStateName the repeat transition state name
   */
  @SchemaIgnore
  public void setRepeatTransitionStateName(String repeatTransitionStateName) {
    this.repeatTransitionStateName = repeatTransitionStateName;
  }

  @Override
  @SchemaIgnore
  public Integer getTimeoutMillis() {
    return INFINITE_TIMEOUT;
  }

  @Override
  public int hashCode() {
    return Objects.hash(log, repeatElementType, repeatElementExpression, executionStrategy, executionStrategyExpression,
        repeatTransitionStateName);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    final RepeatState other = (RepeatState) obj;
    return this.repeatElementType == other.repeatElementType
        && Objects.equals(this.repeatElementExpression, other.repeatElementExpression)
        && this.executionStrategy == other.executionStrategy
        && Objects.equals(this.executionStrategyExpression, other.executionStrategyExpression)
        && Objects.equals(this.repeatTransitionStateName, other.repeatTransitionStateName);
  }

  /**
   * The Class RepeatStateExecutionData.
   */
  public static class RepeatStateExecutionData extends ElementStateExecutionData {
    private ContextElementType repeatElementType;
    private List<ContextElement> repeatElements = new ArrayList<>();
    private Integer repeatElementIndex;
    private ExecutionStrategy executionStrategy;

    /**
     * Gets repeat element type.
     *
     * @return the repeat element type
     */
    public ContextElementType getRepeatElementType() {
      return repeatElementType;
    }

    /**
     * Sets repeat element type.
     *
     * @param repeatElementType the repeat element type
     */
    public void setRepeatElementType(ContextElementType repeatElementType) {
      this.repeatElementType = repeatElementType;
    }

    /**
     * Gets repeat elements.
     *
     * @return the repeat elements
     */
    public List<ContextElement> getRepeatElements() {
      return repeatElements;
    }

    /**
     * Sets repeat elements.
     *
     * @param repeatElements the repeat elements
     */
    public void setRepeatElements(List<ContextElement> repeatElements) {
      this.repeatElements = repeatElements;
    }

    /**
     * Gets repeat element index.
     *
     * @return the repeat element index
     */
    public Integer getRepeatElementIndex() {
      return repeatElementIndex;
    }

    /**
     * Sets repeat element index.
     *
     * @param repeatElementIndex the repeat element index
     */
    public void setRepeatElementIndex(Integer repeatElementIndex) {
      this.repeatElementIndex = repeatElementIndex;
    }

    /**
     * Index reached max.
     *
     * @return true, if successful
     */
    boolean indexReachedMax() {
      return repeatElements != null && repeatElementIndex != null && repeatElementIndex == (repeatElements.size() - 1);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, ExecutionDataValue> getExecutionDetails() {
      Map<String, ExecutionDataValue> executionDetails = super.getExecutionDetails();
      putNotNull(executionDetails, "repeatElements",
          ExecutionDataValue.builder()
              .displayName("Repeating Over")
              .value(join(", ", repeatElements.stream().map(ContextElement::getName).collect(toList())))
              .build());
      putNotNull(executionDetails, "executionStrategy",
          ExecutionDataValue.builder().displayName("Execution Strategy").value(executionStrategy).build());
      return executionDetails;
    }

    @Override
    public Map<String, ExecutionDataValue> getExecutionSummary() {
      Map<String, ExecutionDataValue> executionDetails = super.getExecutionSummary();
      putNotNull(executionDetails, "repeatElements",
          ExecutionDataValue.builder()
              .displayName("Repeating Over")
              .value(abbreviate(join(", ", repeatElements.stream().map(ContextElement::getName).collect(toList())),
                  StateExecutionData.SUMMARY_PAYLOAD_LIMIT))
              .build());
      putNotNull(executionDetails, "executionStrategy",
          ExecutionDataValue.builder().displayName("Execution Strategy").value(executionStrategy).build());
      return executionDetails;
    }

    /**
     * Getter for property 'executionStrategy'.
     *
     * @return Value for property 'executionStrategy'.
     */
    public ExecutionStrategy getExecutionStrategy() {
      return executionStrategy;
    }

    /**
     * Setter for property 'executionStrategy'.
     *
     * @param executionStrategy Value to set for property 'executionStrategy'.
     */
    public void setExecutionStrategy(ExecutionStrategy executionStrategy) {
      this.executionStrategy = executionStrategy;
    }
  }

  /**
   * The type Builder.
   */
  public static final class Builder {
    private String name;
    private ContextElementType repeatElementType;
    private String repeatElementExpression;
    private ExecutionStrategy executionStrategy;
    private String executionStrategyExpression;
    private String repeatTransitionStateName;
    private String parentId;

    private Builder() {}

    /**
     * A repeat state builder.
     *
     * @return the builder
     */
    public static Builder aRepeatState() {
      return new Builder();
    }

    /**
     * With name builder.
     *
     * @param name the name
     * @return the builder
     */
    public Builder withName(String name) {
      this.name = name;
      return this;
    }

    /**
     * With parentId builder.
     *
     * @param parentId the parentId
     * @return the builder
     */
    public Builder withParentId(String parentId) {
      this.parentId = parentId;
      return this;
    }

    /**
     * With repeat element type builder.
     *
     * @param repeatElementType the repeat element type
     * @return the builder
     */
    public Builder withRepeatElementType(ContextElementType repeatElementType) {
      this.repeatElementType = repeatElementType;
      return this;
    }

    /**
     * With repeat element expression builder.
     *
     * @param repeatElementExpression the repeat element expression
     * @return the builder
     */
    public Builder withRepeatElementExpression(String repeatElementExpression) {
      this.repeatElementExpression = repeatElementExpression;
      return this;
    }

    /**
     * With execution strategy builder.
     *
     * @param executionStrategy the execution strategy
     * @return the builder
     */
    public Builder withExecutionStrategy(ExecutionStrategy executionStrategy) {
      this.executionStrategy = executionStrategy;
      return this;
    }

    /**
     * With execution strategy expression builder.
     *
     * @param executionStrategyExpression the execution strategy expression
     * @return the builder
     */
    public Builder withExecutionStrategyExpression(String executionStrategyExpression) {
      this.executionStrategyExpression = executionStrategyExpression;
      return this;
    }

    /**
     * With repeat transition state name builder.
     *
     * @param repeatTransitionStateName the repeat transition state name
     * @return the builder
     */
    public Builder withRepeatTransitionStateName(String repeatTransitionStateName) {
      this.repeatTransitionStateName = repeatTransitionStateName;
      return this;
    }

    /**
     * But builder.
     *
     * @return the builder
     */
    public Builder but() {
      return aRepeatState()
          .withName(name)
          .withRepeatElementType(repeatElementType)
          .withRepeatElementExpression(repeatElementExpression)
          .withExecutionStrategy(executionStrategy)
          .withExecutionStrategyExpression(executionStrategyExpression)
          .withRepeatTransitionStateName(repeatTransitionStateName)
          .withParentId(parentId);
    }

    /**
     * Build repeat state.
     *
     * @return the repeat state
     */
    public RepeatState build() {
      RepeatState repeatState = new RepeatState(name);
      repeatState.setRepeatElementType(repeatElementType);
      repeatState.setRepeatElementExpression(repeatElementExpression);
      repeatState.setExecutionStrategy(executionStrategy);
      repeatState.setExecutionStrategyExpression(executionStrategyExpression);
      repeatState.setRepeatTransitionStateName(repeatTransitionStateName);
      repeatState.setParentId(parentId);
      return repeatState;
    }
  }
}
