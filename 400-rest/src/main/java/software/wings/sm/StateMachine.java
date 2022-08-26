/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.beans.OrchestrationWorkflowType.BUILD;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static software.wings.sm.ExpressionProcessor.EXPRESSION_PREFIX;
import static software.wings.sm.ExpressionProcessor.EXPRESSION_SUFFIX;
import static software.wings.sm.ExpressionProcessor.SUBFIELD_ACCESS;
import static software.wings.sm.StateType.REPEAT;
import static software.wings.sm.Transition.Builder.aTransition;
import static software.wings.sm.states.RepeatState.Builder.aRepeatState;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.EmbeddedUser;
import io.harness.context.ContextElementType;
import io.harness.data.structure.ListUtils;
import io.harness.data.structure.MapUtils;
import io.harness.eraro.ErrorCode;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UuidAware;

import software.wings.beans.ExecutionStrategy;
import software.wings.beans.Graph;
import software.wings.beans.GraphLink;
import software.wings.beans.GraphNode;
import software.wings.beans.OrchestrationWorkflow;
import software.wings.beans.Pipeline;
import software.wings.beans.PipelineStage;
import software.wings.beans.PipelineStage.PipelineStageElement;
import software.wings.beans.Workflow;
import software.wings.beans.entityinterface.ApplicationAccess;
import software.wings.common.WingsExpressionProcessorFactory;
import software.wings.common.WorkflowConstants;
import software.wings.exception.DuplicateStateNameException;
import software.wings.exception.StateMachineIssueException;
import software.wings.sm.states.EnvState.EnvStateKeys;
import software.wings.sm.states.ForkState;
import software.wings.sm.states.RepeatState;
import software.wings.sm.states.SubWorkflowState;
import software.wings.sm.states.mixin.SweepingOutputStateMixin;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;
import org.mongodb.morphia.annotations.PostLoad;
import org.mongodb.morphia.annotations.Transient;

/**
 * Describes a StateMachine.
 *
 * @author Rishi
 */
@OwnedBy(CDC)
@TargetModule(HarnessModule._957_CG_BEANS)
@Data
@Entity(value = "stateMachines", noClassnameStored = true)
@HarnessEntity(exportable = true)
@FieldNameConstants(innerTypeName = "StateMachineKeys")
@Slf4j
public class StateMachine implements PersistentEntity, UuidAware, CreatedAtAware, ApplicationAccess {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("appId_origin")
                 .field(StateMachineKeys.appId)
                 .field(StateMachineKeys.originId)
                 .field(StateMachineKeys.originVersion)
                 .build())
        .build();
  }
  public static final String MAPPING_ERROR_MESSAGE_PREFIX = "Error mapping properties for state: ";
  public static final String MAPPING_ERROR_MESSAGE_SUFFIX = "[%s] of type: [%s]";
  public static final String MAPPING_ERROR_MESSAGE = MAPPING_ERROR_MESSAGE_PREFIX + MAPPING_ERROR_MESSAGE_SUFFIX;

  @Id private String uuid;
  @FdIndex @NotNull protected String appId;
  @FdIndex private long createdAt;

  @FdIndex private String accountId;

  @FdIndex private String originId;

  private Integer originVersion;

  private String name;

  private OrchestrationWorkflow orchestrationWorkflow;

  private boolean valid;

  private List<State> states = Lists.newArrayList();

  private List<Transition> transitions = Lists.newArrayList();

  private Map<String, StateMachine> childStateMachines = new HashMap<>();

  private String initialStateName;

  @Transient private transient Map<String, State> cachedStatesMap;

  @Transient private transient Map<String, Map<TransitionType, List<State>>> cachedTransitionFlowMap;

  /**
   * Instantiates a new state machine.
   */
  public StateMachine() {}

  /**
   * Instantiates a new state machine.
   *  @param workflow   the workflow
   * @param graph      the graph
   * @param stencilMap the stencil map
   */

  public StateMachine(Workflow workflow, Integer originVersion, Graph graph,
      Map<String, StateTypeDescriptor> stencilMap, boolean migration) {
    orchestrationWorkflow = workflow.getOrchestrationWorkflow();
    setAppId(workflow.getAppId());
    setAccountId(workflow.getAccountId());
    this.originId = workflow.getUuid();
    this.originVersion = originVersion;
    String errorMsg = null;
    try {
      deepTransform(graph, stencilMap, orchestrationWorkflow);
      valid = true;
    } catch (WingsException wingsException) {
      log.error("Error in State Machine transform", wingsException);
      errorMsg = ExceptionUtils.getMessage(wingsException);
    }
    if (!migration) {
      orchestrationWorkflow.validate();
    }
    if (orchestrationWorkflow.isValid() && !valid) {
      orchestrationWorkflow.setValid(false);
      orchestrationWorkflow.setValidationMessage(errorMsg);
    }

    if (!workflow.envValid()) {
      if (BUILD != orchestrationWorkflow.getOrchestrationWorkflowType()) {
        orchestrationWorkflow.setValid(false);
        orchestrationWorkflow.setValidationMessage(WorkflowConstants.WORKFLOW_ENV_VALIDATION_MESSAGE);
      }
    } else if (orchestrationWorkflow.isValid()) {
      orchestrationWorkflow.setValid(true);
      orchestrationWorkflow.setValidationMessage(null);
    }

    // Update Orchestration Workflow user variables
    orchestrationWorkflow.updateUserVariables();
  }

  public StateMachine(
      Graph graph, Map<String, StateTypeDescriptor> stencilMap, OrchestrationWorkflow orchestrationWorkflow) {
    deepTransform(graph, stencilMap, orchestrationWorkflow);
  }

  public StateMachine(Pipeline pipeline, Map<String, StateTypeDescriptor> stencilMap) {
    log.debug("Pipeline received for transformation {}", pipeline.toString());
    setAppId(pipeline.getAppId());
    setAccountId(pipeline.getAccountId());
    this.originId = pipeline.getUuid();
    try {
      transformPipeline(pipeline, stencilMap);
    } catch (WingsException e) {
      throw e;
    } catch (Exception e) {
      log.error(e.getLocalizedMessage(), e);
      throw new InvalidRequestException("StateMachine transformation error");
    }
  }

  private void transformPipeline(Pipeline pipeline, Map<String, StateTypeDescriptor> stencilMap) {
    String originStateName = null;
    State prevState = null;

    if (isNotEmpty(pipeline.getPipelineStages())) {
      for (int i = 0; i < pipeline.getPipelineStages().size(); i++) {
        PipelineStage pipelineStage = pipeline.getPipelineStages().get(i);
        State state = convertToState(pipelineStage, pipeline, stencilMap);

        if (i > 0 && pipelineStage.isParallel()) {
          // part of fork - 2nd, 3rd stage in parallel
          ((ForkState) prevState).addForkState(state);
        } else if (i < pipeline.getPipelineStages().size() - 1
            && pipeline.getPipelineStages().get(i + 1).isParallel()) {
          // start of a fork - not a parallel, but following stage has parallel flag
          String forkName = getForkStateName(pipelineStage);
          forkName += "-" + i;
          ForkState forkState = new ForkState(forkName);
          forkState.addForkState(state);
          addState(forkState);
          if (prevState != null) {
            addTransition(aTransition()
                              .withTransitionType(TransitionType.SUCCESS)
                              .withFromState(prevState)
                              .withToState(forkState)
                              .build());
          }
          prevState = forkState;
          if (originStateName == null) {
            originStateName = forkState.getName();
          }
        } else {
          if (prevState != null) {
            addTransition(aTransition()
                              .withTransitionType(TransitionType.SUCCESS)
                              .withFromState(prevState)
                              .withToState(state)
                              .build());
          }
          prevState = state;
          if (originStateName == null) {
            originStateName = state.getName();
          }
        }
      }
    }

    setInitialStateName(originStateName);
    validate();
    clearCache();
  }

  private State convertToState(
      PipelineStage pipelineStage, Pipeline pipeline, Map<String, StateTypeDescriptor> stencilMap) {
    if (pipelineStage == null || isEmpty(pipelineStage.getPipelineStageElements())) {
      throw new InvalidArgumentsException(Pair.of("args", "Pipeline Stage: pipelineStage"));
    }
    if (pipelineStage.getPipelineStageElements().size() == 1) {
      return convertToState(
          pipelineStage.getPipelineStageElements().get(0), pipeline, stencilMap, pipelineStage.getName());
    } else {
      String forkName = getForkStateName(pipelineStage);
      ForkState forkState = new ForkState(forkName);
      for (PipelineStageElement pipelineStageElement : pipelineStage.getPipelineStageElements()) {
        State state = convertToState(pipelineStageElement, pipeline, stencilMap, pipelineStage.getName());
        forkState.addForkState(state);
      }
      addState(forkState);
      return forkState;
    }
  }

  private String getForkStateName(PipelineStage pipelineStage) {
    String forkName;
    if (pipelineStage.getName() == null) {
      forkName = "fork-pipeline-stage-" + System.currentTimeMillis();
    } else {
      forkName = pipelineStage.getName() + "-fork";
    }
    return forkName;
  }

  private State convertToState(PipelineStageElement pipelineStageElement, Pipeline pipeline,
      Map<String, StateTypeDescriptor> stencilMap, String stageName) {
    StateTypeDescriptor stateTypeDesc = stencilMap.get(pipelineStageElement.getType());

    State state = stateTypeDesc.newInstance(pipelineStageElement.getName());

    Map<String, Object> properties = pipelineStageElement.getProperties();

    properties = MapUtils.putToImmutable(EnvStateKeys.pipelineId, pipeline.getUuid(), properties);
    properties.put(EnvStateKeys.pipelineStageElementId, pipelineStageElement.getUuid());
    properties.put(EnvStateKeys.pipelineStageParallelIndex, pipelineStageElement.getParallelIndex());
    properties.put(EnvStateKeys.stageName, stageName);
    properties.put(EnvStateKeys.disableAssertion, pipelineStageElement.getDisableAssertion());
    properties.put(EnvStateKeys.disable, pipelineStageElement.isDisable());

    if (pipelineStageElement.getWorkflowVariables() != null) {
      properties.put(EnvStateKeys.workflowVariables, pipelineStageElement.getWorkflowVariables());
    }
    if (pipelineStageElement.getRuntimeInputsConfig() != null) {
      properties.put(
          EnvStateKeys.runtimeInputVariables, pipelineStageElement.getRuntimeInputsConfig().getRuntimeInputVariables());
      properties.put(EnvStateKeys.timeout, pipelineStageElement.getRuntimeInputsConfig().getTimeout());
      properties.put(EnvStateKeys.timeoutAction, pipelineStageElement.getRuntimeInputsConfig().getTimeoutAction());
      properties.put(EnvStateKeys.userGroupIds, pipelineStageElement.getRuntimeInputsConfig().getUserGroupIds());
    }
    state.parseProperties(properties);
    state.resolveProperties();
    addState(state);
    return state;
  }

  private void deepTransform(
      Graph graph, Map<String, StateTypeDescriptor> stencilMap, OrchestrationWorkflow orchestrationWorkflow) {
    transform(graph, stencilMap, orchestrationWorkflow);
    Map<String, Graph> subworkflows = graph.getSubworkflows();
    if (subworkflows != null) {
      for (Map.Entry<String, Graph> entry : subworkflows.entrySet()) {
        Graph childGraph = entry.getValue();
        if (childGraph == null || isEmpty(childGraph.getNodes())) {
          continue;
        }
        childStateMachines.put(entry.getKey(), new StateMachine(childGraph, stencilMap, orchestrationWorkflow));
      }
    }
  }

  private void transform(
      Graph graph, Map<String, StateTypeDescriptor> stencilMap, OrchestrationWorkflow orchestrationWorkflow) {
    String originStateName = null;
    for (GraphNode node : graph.getNodes()) {
      log.debug("node : {}", node);

      if (node.getType() == null || stencilMap.get(node.getType()) == null) {
        throw new InvalidRequestException("Unknown stencil type");
      }

      if (node.getName() == null) {
        throw new InvalidRequestException("Node name null");
      }

      if (node.isOrigin()) {
        if (originStateName != null) {
          throw new InvalidRequestException("Duplicate origin state: " + originStateName + " and " + node.getName());
        }

        originStateName = node.getName();
      }

      StateTypeDescriptor stateTypeDesc = stencilMap.get(node.getType());

      State state = stateTypeDesc.newInstance(node.getName());

      // This is temporary until this field is removed
      state.setTemplateExpressions(node.getTemplateExpressions());

      // populate properties
      if (node.getProperties() != null) {
        try {
          state.parseProperties(node.getProperties());
        } catch (WingsException e) {
          throw e;
        } catch (Exception e) {
          String errorMessage = format(MAPPING_ERROR_MESSAGE, state.getName(), state.getStateType());
          log.warn(errorMessage, e);
          log.warn("Properties: " + StringUtils.join(node.getProperties()));
          throw new InvalidRequestException(errorMessage, e);
        }
      }

      state.setId(node.getId());
      state.setRollback(node.isRollback());

      state.setTemplateVariables(node.getTemplateVariables());
      state.setTemplateUuid(node.getTemplateUuid());
      state.setTemplateVersion(node.getTemplateVersion());

      state.resolveProperties();

      if (isNotEmpty(node.getVariableOverrides()) && state instanceof SubWorkflowState) {
        ((SubWorkflowState) state).setVariableOverrides(node.getVariableOverrides());
      }
      Map<String, String> stateValidateMessages = state.validateFields();
      node.setInValidFieldMessages(stateValidateMessages);

      if (orchestrationWorkflow != null) {
        if (state.getTemplateExpressions() != null) {
          orchestrationWorkflow.addToUserVariables(state);
        }
        if (isNotEmpty(state.getTemplateUuid())) {
          orchestrationWorkflow.addTemplateUuid(state.getTemplateUuid());
        }
      }
      addState(state);
    }

    if (originStateName == null) {
      throw new InvalidRequestException("Origin state missing");
    }

    try {
      Map<String, GraphNode> nodeIdMap = graph.getNodesMap();
      Map<String, State> statesMap = getStatesMap();

      if (graph.getLinks() != null) {
        for (GraphLink link : graph.getLinks()) {
          GraphNode nodeFrom = nodeIdMap.get(link.getFrom());
          GraphNode nodeTo = nodeIdMap.get(link.getTo());

          State stateFrom = null;
          try {
            stateFrom = statesMap.get(nodeFrom.getName());
          } catch (Exception e) {
            log.error("", e);
          }
          State stateTo = statesMap.get(nodeTo.getName());

          TransitionType transitionType = TransitionType.valueOf(link.getType().toUpperCase());

          if (transitionType == TransitionType.FORK) {
            ((ForkState) stateFrom).addForkState(stateTo);
          } else if (transitionType == TransitionType.REPEAT) {
            ((RepeatState) stateFrom).setRepeatTransitionStateName(stateTo.getName());
          }

          addTransition(
              aTransition().withFromState(stateFrom).withTransitionType(transitionType).withToState(stateTo).build());
        }
      }
      setInitialStateName(originStateName);
      validate();

      addRepeatersForRequiredContextElement();
      clearCache();
    } catch (IllegalArgumentException e) {
      throw new InvalidArgumentsException(
          Pair.of("transform", "Exception Occurred While StateMachine Transformation"), e);
    }
  }

  /**
   * Clear cache.
   */
  void clearCache() {
    cachedStatesMap = null;
    cachedTransitionFlowMap = null;
  }

  /**
   * Gets initial state name.
   *
   * @return the initial state name
   */
  public String getInitialStateName() {
    return initialStateName;
  }

  /**
   * Sets initial state name.
   *
   * @param initialStateName the initial state name
   */
  public void setInitialStateName(String initialStateName) {
    this.initialStateName = initialStateName;
  }

  /**
   * Gets states.
   *
   * @return the states
   */
  public synchronized List<State> getStates() {
    return states;
  }

  /**
   * Sets states.
   *
   * @param states the states
   */
  public synchronized void setStates(List<State> states) {
    this.states = states;
  }

  /**
   * Adds a state to state machine.
   *
   * @param state state to be added.
   * @return state after saving.
   */
  public synchronized State addState(State state) {
    if (states == null) {
      states = new ArrayList<>();
    }
    states.add(state);
    cachedStatesMap = null;
    return state;
  }

  /**
   * Gets transitions.
   *
   * @return the transitions
   */
  public List<Transition> getTransitions() {
    return transitions;
  }

  /**
   * Sets transitions.
   *
   * @param transitions the transitions
   */
  public void setTransitions(List<Transition> transitions) {
    this.transitions = transitions;
  }

  /**
   * Adds transition to state machine.
   *
   * @param transition transition to be added.
   * @return transition after add.
   */
  public Transition addTransition(Transition transition) {
    if (transitions == null) {
      transitions = new ArrayList<>();
    }
    transitions.add(transition);
    cachedTransitionFlowMap = null;
    return transition;
  }

  /**
   * Gets initial state.
   *
   * @return the initial state
   */
  public State getInitialState() {
    Map<String, State> statesMap = getStatesMap();
    return statesMap.get(initialStateName);
  }

  /**
   * Gets states map.
   *
   * @return map to state to stateNames.
   */
  public synchronized Map<String, State> getStatesMap() {
    if (isNotEmpty(cachedStatesMap)) {
      return cachedStatesMap;
    }
    Map<String, State> statesMap = new HashMap<>();
    HashSet<String> dupNames = new HashSet<>();

    String dupName = null;
    if (states != null) {
      for (State state : states) {
        if (statesMap.containsKey(state.getName())) {
          dupNames.add(state.getName());
          if (!state.isRollback()) {
            dupName = state.getName();
          } else {
            if (dupName == null) {
              dupName = state.getName();
            }
          }
        } else {
          statesMap.put(state.getName(), state);
        }
      }
    }

    if (dupName != null) {
      throw new DuplicateStateNameException(dupName);
    }

    cachedStatesMap = statesMap;
    return statesMap;
  }

  /**
   * Gets the success transition.
   *
   * @param fromStateName the from state name
   * @return the success transition
   */
  public State getSuccessTransition(String fromStateName) {
    return getNextState(fromStateName, TransitionType.SUCCESS);
  }

  public State getSuccessTransition(String childStateMachineId, String fromStateName) {
    return getNextState(childStateMachineId, fromStateName, TransitionType.SUCCESS);
  }

  /**
   * Returns next state given start state and transition type.
   *
   * @param fromStateName  start state.
   * @param transitionType transition type to look state from.
   * @return first next state if any or null.
   */
  public State getNextState(String fromStateName, TransitionType transitionType) {
    List<State> nextStates = getNextStates(fromStateName, transitionType);
    if (isEmpty(nextStates)) {
      return null;
    }
    return nextStates.get(0);
  }

  public State getNextState(String childStateMachineId, String fromStateName, TransitionType transitionType) {
    if (childStateMachineId == null) {
      return getNextState(fromStateName, transitionType);
    } else {
      StateMachine sm = childStateMachines.get(childStateMachineId);
      if (sm == null) {
        return null;
      }
      return sm.getNextState(fromStateName, transitionType);
    }
  }

  /**
   * Returns list of next states given start state and transition type.
   *
   * @param fromStateName  start state.
   * @param transitionType transition type to look state from.
   * @return list of next states or null.
   */
  public List<State> getNextStates(String fromStateName, TransitionType transitionType) {
    Map<String, Map<TransitionType, List<State>>> transitionFlowMap = getTransitionFlowMap();
    if (transitionFlowMap == null || transitionFlowMap.get(fromStateName) == null) {
      return null;
    }
    return transitionFlowMap.get(fromStateName).get(transitionType);
  }

  /**
   * Gets next states.
   *
   * @param fromStateName the from state name
   * @return the next states
   */
  public List<State> getNextStates(String fromStateName) {
    Map<String, Map<TransitionType, List<State>>> transitionFlowMap = getTransitionFlowMap();
    if (transitionFlowMap == null || transitionFlowMap.get(fromStateName) == null) {
      return null;
    }
    return transitionFlowMap.get(fromStateName).values().stream().flatMap(Collection::stream).collect(toList());
  }

  /**
   * Gets transition flow map.
   *
   * @return a transition flow map describing transition types to list of states.
   */
  public Map<String, Map<TransitionType, List<State>>> getTransitionFlowMap() {
    if (isNotEmpty(cachedTransitionFlowMap)) {
      return cachedTransitionFlowMap;
    }

    Map<String, State> statesMap = getStatesMap();
    Set<String> invalidStateNames = new HashSet<>();
    Set<String> statesWithDupTransitions = new HashSet<>();
    Set<String> nonForkStates = new HashSet<>();
    Set<String> nonRepeatStates = new HashSet<>();
    Map<String, Map<TransitionType, List<State>>> flowMap = new HashMap<>();

    Map<String, List<String>> forkStateNamesMap = new HashMap<>();
    if (transitions != null) {
      for (Transition transition : transitions) {
        if (transition.getTransitionType() == null) {
          throw new StateMachineIssueException("TransitionType Cannot be null", ErrorCode.TRANSITION_TYPE_NULL);
        }
        State fromState = transition.getFromState();
        State toState = transition.getToState();
        if (fromState == null || toState == null) {
          throw new StateMachineIssueException(
              "Transition Should be linked. Both from and to states are null", ErrorCode.TRANSITION_NOT_LINKED);
        }
        boolean invalidState = false;
        if (statesMap.get(fromState.getName()) == null) {
          invalidStateNames.add(fromState.getName());
          invalidState = true;
        }
        if (statesMap.get(toState.getName()) == null) {
          invalidStateNames.add(toState.getName());
          invalidState = true;
        }
        if (!invalidState) {
          if (transition.getTransitionType() == TransitionType.FORK
              && !StateType.FORK.name().equals(fromState.getStateType())) {
            nonForkStates.add(fromState.getName());
            continue;
          }

          if (transition.getTransitionType() == TransitionType.REPEAT
              && !REPEAT.name().equals(fromState.getStateType())) {
            nonRepeatStates.add(fromState.getName());
            continue;
          }

          Map<TransitionType, List<State>> transitionMap = flowMap.get(fromState.getName());
          if (transitionMap == null) {
            transitionMap = new HashMap<>();
            flowMap.put(fromState.getName(), transitionMap);
            List<State> toStates = new ArrayList<>();
            toStates.add(toState);
            transitionMap.put(transition.getTransitionType(), toStates);
          } else {
            List<State> toStates = transitionMap.get(transition.getTransitionType());
            if (toStates == null) {
              toStates = new ArrayList<>();
              toStates.add(toState);
              transitionMap.put(transition.getTransitionType(), toStates);
            } else {
              if (transition.getTransitionType() != TransitionType.FORK
                  && transition.getTransitionType() != TransitionType.CONDITIONAL) {
                statesWithDupTransitions.add(fromState.getName());
              } else {
                toStates.add(toState);
              }
            }
          }

          if (transition.getTransitionType() == TransitionType.FORK) {
            List<String> forkStateNames =
                forkStateNamesMap.computeIfAbsent(fromState.getName(), k -> new ArrayList<>());
            forkStateNames.add(toState.getName());
          }
        }
      }
    }
    if (!invalidStateNames.isEmpty()) {
      throw new StateMachineIssueException(
          String.format("Invalid State Names Detected: %s", invalidStateNames.toString()),
          ErrorCode.TRANSITION_TO_INCORRECT_STATE);
    }
    if (!nonForkStates.isEmpty()) {
      throw new StateMachineIssueException(
          String.format("NonForkStates: %s", nonForkStates.toString()), ErrorCode.NON_FORK_STATES);
    }
    if (!nonRepeatStates.isEmpty()) {
      throw new StateMachineIssueException(
          String.format("NonRepeatStates: %s", nonRepeatStates.toString()), ErrorCode.NON_REPEAT_STATES);
    }
    if (!statesWithDupTransitions.isEmpty()) {
      throw new StateMachineIssueException(
          String.format("StatesWithDupTransitions: %s", statesWithDupTransitions.toString()),
          ErrorCode.STATES_WITH_DUP_TRANSITIONS);
    }
    for (Entry<String, List<String>> forkStateEntry : forkStateNamesMap.entrySet()) {
      ForkState forkFromState = (ForkState) statesMap.get(forkStateEntry.getKey());
      forkFromState.setForkStateNames(forkStateEntry.getValue());
    }

    cachedTransitionFlowMap = flowMap;
    return flowMap;
  }

  /**
   * Gets the failure transition.
   *
   * @param fromStateName the from state name
   * @return the failure transition
   */
  public State getFailureTransition(String fromStateName) {
    return getNextState(fromStateName, TransitionType.FAILURE);
  }

  public State getFailureTransition(String childStateMachineId, String fromStateName) {
    return getNextState(childStateMachineId, fromStateName, TransitionType.FAILURE);
  }

  /**
   * Get state based on name.
   *
   * @param stateName name of state to lookup for.
   * @return state object if found or null.
   */
  private State getState(String stateName) {
    Map<String, State> statesMap = getStatesMap();
    if (statesMap == null) {
      return null;
    }
    if (stateName == null) {
      return statesMap.get(initialStateName);
    }
    return statesMap.get(stateName);
  }

  /**
   * Get state based on name.
   *
   * @param childStateMachineId childStateMachineId.
   * @param stateName           name of state to lookup for.
   * @return state object if found or null.
   */
  public State getState(String childStateMachineId, String stateName) {
    if (childStateMachineId == null) {
      return getState(stateName);
    } else {
      StateMachine sm = childStateMachines.get(childStateMachineId);
      if (sm == null) {
        return null;
      }
      return sm.getState(stateName);
    }
  }

  /**
   * Gets transition from.
   *
   * @param stateFrom the state from
   * @return the transition from
   */
  public List<Transition> getTransitionFrom(State stateFrom) {
    return transitions.stream().filter(transition -> transition.getFromState() == stateFrom).collect(toList());
  }

  /**
   * After load.
   */
  @PostLoad
  public void afterLoad() {
    validate();
  }

  /**
   * Validates a state machine.
   *
   * @return true if valid.
   */
  public boolean validate() {
    getTransitionFlowMap();
    return true;
  }

  /**
   * Add repeaters based on state required context element.
   */
  void addRepeatersForRequiredContextElement() {
    List<String> stateNamesInOrder = new ArrayList<>();
    buildStateNamesInOrderByAppearance(getInitialState(), stateNamesInOrder);

    for (String stateName : stateNamesInOrder) {
      State state = getState(stateName);
      if (state == null || state instanceof RepeatState) {
        continue;
      }
      ContextElementType requiredContextElementType = getRequiredContextElementType(state);

      if (requiredContextElementType == null) {
        continue;
      }
      List<Transition> transitionsToOldState = getTransitionsTo(state);
      if (isEmpty(transitionsToOldState)) {
        if (!initialStateName.equals(stateName)) {
          throw new StateMachineIssueException("Inconsistent state", ErrorCode.STATE_MACHINE_ISSUE);
        }
        State newRepeatState = createRepeatState(state, requiredContextElementType);
        setInitialStateName(newRepeatState.getName());
        addTransition(aTransition()
                          .withTransitionType(TransitionType.REPEAT)
                          .withFromState(newRepeatState)
                          .withToState(state)
                          .build());
        postRepeatStateCheck(state, requiredContextElementType, newRepeatState);
        continue;
      } else {
        Map<Transition, Set<ContextElementType>> availableContextsByTransition = new HashMap<>();
        buildAvailableContextsByTransition(getInitialState(), new HashSet<>(), availableContextsByTransition);
        ContextElementType finalRequiredContextElementType = requiredContextElementType;
        List<Transition> transitionsForRequired =
            transitionsToOldState.stream()
                .filter(transition
                    -> availableContextsByTransition.get(transition) == null
                        || !availableContextsByTransition.get(transition).contains(finalRequiredContextElementType))
                .collect(toList());
        if (transitionsForRequired.isEmpty()) {
          continue;
        }
        State newRepeatState = createRepeatState(state, requiredContextElementType);
        transitionsForRequired.forEach(transition -> { transition.setToState(newRepeatState); });
        addTransition(aTransition()
                          .withTransitionType(TransitionType.REPEAT)
                          .withFromState(newRepeatState)
                          .withToState(state)
                          .build());

        postRepeatStateCheck(state, requiredContextElementType, newRepeatState);
      }
    }
  }

  private void postRepeatStateCheck(State state, ContextElementType requiredContextElementType, State newRepeatState) {
    State fromState = state;
    for (;;) {
      State nextState = getNextState(fromState.getName(), TransitionType.SUCCESS);
      if (nextState == null) {
        break;
      }

      final ContextElementType nextRequiredContextElementType = getRequiredContextElementType(nextState);
      if (nextRequiredContextElementType != requiredContextElementType) {
        final List<Transition> transitionsTo = getTransitionsTo(nextState);
        final String name = fromState.getName();
        transitionsTo.stream()
            .filter(transition -> transition.getFromState().getName().equals(name))
            .forEach(transition -> transition.setFromState(newRepeatState));
        break;
      }
      fromState = nextState;
    }
  }

  ContextElementType getRequiredContextElementType(State state) {
    ContextElementType requiredContextElementType = state.getRequiredContextElementType();
    if (requiredContextElementType != null) {
      return requiredContextElementType;
    }
    List<String> patternsForRequiredContextElementType = state.getPatternsForRequiredContextElementType();
    if (state instanceof SweepingOutputStateMixin) {
      patternsForRequiredContextElementType = ListUtils.addSafely(
          ((SweepingOutputStateMixin) state).getSweepingOutputName(), patternsForRequiredContextElementType);
    }

    if (patternsForRequiredContextElementType != null) {
      return scanRequiredContextElementType(patternsForRequiredContextElementType);
    }

    return null;
  }

  private State createRepeatState(State state, ContextElementType requiredContextElementType) {
    return addState(aRepeatState()
                        .withName("Repeat " + state.getName())
                        .withRepeatElementType(requiredContextElementType)
                        .withExecutionStrategy(ExecutionStrategy.PARALLEL)
                        .withParentId(state.getParentId())
                        .withRepeatElementExpression(
                            WingsExpressionProcessorFactory.getDefaultExpression(requiredContextElementType))
                        .withRepeatTransitionStateName(state.getName())
                        .build());
  }

  private void buildAvailableContextsByTransition(State state, Set<ContextElementType> previousContexts,
      Map<Transition, Set<ContextElementType>> availableContextsByTransition) {
    List<Transition> transitionFrom = getTransitionFrom(state);
    if (isEmpty(transitionFrom)) {
      return;
    }
    transitionFrom.forEach(transition -> {
      Set<ContextElementType> newContexts = previousContexts;
      if (transition.getTransitionType() == TransitionType.REPEAT) {
        newContexts = new HashSet<>(previousContexts);
        newContexts.add(((RepeatState) state).getRepeatElementType());
      }
      availableContextsByTransition.put(transition, newContexts);
      buildAvailableContextsByTransition(transition.getToState(), newContexts, availableContextsByTransition);
    });
  }

  private void buildStateNamesInOrderByAppearance(State state, List<String> stateNamesInOrder) {
    List<State> nextStates = getNextStates(state.getName());
    if (isEmpty(nextStates)) {
      stateNamesInOrder.add(state.getName());
      return;
    }

    int ind = getLeastIndexPresentNextStates(stateNamesInOrder, nextStates);
    if (ind == Integer.MAX_VALUE) {
      stateNamesInOrder.add(state.getName());
    } else {
      stateNamesInOrder.add(ind, state.getName());
    }
    nextStates.forEach(nextState -> {
      if (!stateNamesInOrder.contains(nextState.getName())) {
        buildStateNamesInOrderByAppearance(nextState, stateNamesInOrder);
      }
    });
  }

  private int getLeastIndexPresentNextStates(List<String> stateNamesInOrder, List<State> nextStates) {
    int ind = Integer.MAX_VALUE;
    for (State nextState : nextStates) {
      int ind2 = stateNamesInOrder.indexOf(nextState.getName());
      ind = (ind2 < 0 || ind2 > ind) ? ind : ind2;
    }
    return ind;
  }

  private ContextElementType scanRequiredContextElementType(List<String> patternsForRequiredContextElementType) {
    if (patternsForRequiredContextElementType.stream().anyMatch(pattern
            -> pattern != null
                && (pattern.contains(EXPRESSION_PREFIX + ContextElement.INSTANCE + EXPRESSION_SUFFIX)
                    || pattern.contains(EXPRESSION_PREFIX + ContextElement.INSTANCE + SUBFIELD_ACCESS)
                    || pattern.contains(EXPRESSION_PREFIX + ContextElement.HOST + SUBFIELD_ACCESS)))) {
      return ContextElementType.INSTANCE;
    }

    return null;
  }

  private List<Transition> getTransitionsTo(State state) {
    return transitions.stream().filter(transition -> transition.getToState() == state).collect(toList());
  }

  /**
   * Gets origin id.
   *
   * @return the origin id
   */
  public String getOriginId() {
    return originId;
  }

  /**
   * Sets origin id.
   *
   * @param originId the origin id
   */
  public void setOriginId(String originId) {
    this.originId = originId;
  }

  /**
   * Gets name.
   *
   * @return the name
   */
  public String getName() {
    return name;
  }

  /**
   * Sets name.
   *
   * @param name the name
   */
  public void setName(String name) {
    this.name = name;
  }

  public Integer getOriginVersion() {
    return originVersion;
  }

  public void setOriginVersion(Integer originVersion) {
    this.originVersion = originVersion;
  }

  public Map<String, StateMachine> getChildStateMachines() {
    return childStateMachines;
  }

  public void setChildStateMachines(Map<String, StateMachine> childStateMachines) {
    this.childStateMachines = childStateMachines;
  }

  public OrchestrationWorkflow getOrchestrationWorkflow() {
    return orchestrationWorkflow;
  }

  public void setOrchestrationWorkflow(OrchestrationWorkflow orchestrationWorkflow) {
    this.orchestrationWorkflow = orchestrationWorkflow;
  }

  public boolean isValid() {
    return valid;
  }

  public void setValid(boolean valid) {
    this.valid = valid;
  }

  /**
   * {@inheritDoc}
   */ /* (non-Javadoc)
   * @see software.wings.beans.Base#toString()
   */
  @Override
  public String toString() {
    return "StateMachine [initialStateName=" + initialStateName + ", states=" + states + ", transitions=" + transitions
        + "]";
  }

  public static final class StateMachineBuilder {
    private String originId;
    private Integer originVersion;
    private String name;
    private List<State> states = Lists.newArrayList();
    private List<Transition> transitions = Lists.newArrayList();
    private String initialStateName;
    private String uuid;
    private String appId;
    private EmbeddedUser createdBy;
    private long createdAt;
    private EmbeddedUser lastUpdatedBy;
    private long lastUpdatedAt;
    private String accountId;

    private StateMachineBuilder() {}

    public static StateMachineBuilder aStateMachine() {
      return new StateMachineBuilder();
    }

    public StateMachineBuilder withOriginId(String originId) {
      this.originId = originId;
      return this;
    }

    public StateMachineBuilder withOriginVersion(Integer originVersion) {
      this.originVersion = originVersion;
      return this;
    }

    public StateMachineBuilder withName(String name) {
      this.name = name;
      return this;
    }

    public StateMachineBuilder addState(State state) {
      this.states.add(state);
      return this;
    }

    public StateMachineBuilder addTransition(Transition transition) {
      this.transitions.add(transition);
      return this;
    }

    public StateMachineBuilder withInitialStateName(String initialStateName) {
      this.initialStateName = initialStateName;
      return this;
    }

    public StateMachineBuilder withUuid(String uuid) {
      this.uuid = uuid;
      return this;
    }

    public StateMachineBuilder withAppId(String appId) {
      this.appId = appId;
      return this;
    }

    public StateMachineBuilder withCreatedBy(EmbeddedUser createdBy) {
      this.createdBy = createdBy;
      return this;
    }

    public StateMachineBuilder withCreatedAt(long createdAt) {
      this.createdAt = createdAt;
      return this;
    }

    public StateMachineBuilder withLastUpdatedBy(EmbeddedUser lastUpdatedBy) {
      this.lastUpdatedBy = lastUpdatedBy;
      return this;
    }

    public StateMachineBuilder withLastUpdatedAt(long lastUpdatedAt) {
      this.lastUpdatedAt = lastUpdatedAt;
      return this;
    }

    public StateMachineBuilder withAccountId(String accountId) {
      this.accountId = accountId;
      return this;
    }

    public StateMachine build() {
      StateMachine stateMachine = new StateMachine();
      stateMachine.setOriginId(originId);
      stateMachine.setOriginVersion(originVersion);
      stateMachine.setName(name);
      stateMachine.setStates(states);
      stateMachine.setTransitions(transitions);
      stateMachine.setInitialStateName(initialStateName);
      stateMachine.setUuid(uuid);
      stateMachine.setAppId(appId);
      stateMachine.setCreatedAt(createdAt);
      stateMachine.setAccountId(accountId);
      return stateMachine;
    }
  }
}
