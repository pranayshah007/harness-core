/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.beans.ExecutionInterruptType.CONTINUE_PIPELINE_STAGE;
import static io.harness.beans.ExecutionInterruptType.PAUSE_ALL;
import static io.harness.beans.ExecutionInterruptType.PAUSE_FOR_INPUTS;
import static io.harness.beans.ExecutionInterruptType.RESUME_ALL;
import static io.harness.beans.ExecutionInterruptType.RETRY;
import static io.harness.beans.ExecutionStatus.ABORTED;
import static io.harness.beans.ExecutionStatus.DISCONTINUING;
import static io.harness.beans.ExecutionStatus.ERROR;
import static io.harness.beans.ExecutionStatus.EXPIRED;
import static io.harness.beans.ExecutionStatus.FAILED;
import static io.harness.beans.ExecutionStatus.NEW;
import static io.harness.beans.ExecutionStatus.PAUSED;
import static io.harness.beans.ExecutionStatus.PAUSING;
import static io.harness.beans.ExecutionStatus.QUEUED;
import static io.harness.beans.ExecutionStatus.RUNNING;
import static io.harness.beans.ExecutionStatus.SKIPPED;
import static io.harness.beans.ExecutionStatus.STARTING;
import static io.harness.beans.ExecutionStatus.SUCCESS;
import static io.harness.beans.ExecutionStatus.WAITING;
import static io.harness.beans.ExecutionStatus.activeStatuses;
import static io.harness.beans.ExecutionStatus.brokeStatuses;
import static io.harness.beans.ExecutionStatus.isBrokeStatus;
import static io.harness.beans.ExecutionStatus.isFinalStatus;
import static io.harness.beans.ExecutionStatus.isPositiveStatus;
import static io.harness.beans.FeatureName.SPG_FIX_APPROVAL_WAITING_FOR_INPUTS;
import static io.harness.beans.FeatureName.TIMEOUT_FAILURE_SUPPORT;
import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.beans.SearchFilter.Operator.EQ;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eraro.ErrorCode.INVALID_ARGUMENT;
import static io.harness.eraro.ErrorCode.STATE_NOT_FOR_TYPE;
import static io.harness.exception.WingsException.ExecutionContext.MANAGER;
import static io.harness.govern.Switch.unhandled;
import static io.harness.threading.Morpheus.quietSleep;
import static io.harness.validation.Validator.notNullCheck;
import static io.harness.waiter.OrchestrationNotifyEventListener.ORCHESTRATION;

import static software.wings.beans.ExecutionScope.WORKFLOW;
import static software.wings.beans.NotificationRule.NotificationRuleBuilder.aNotificationRule;
import static software.wings.beans.alert.AlertType.ManualInterventionNeeded;
import static software.wings.common.NotificationMessageResolver.NotificationMessageType.MANUAL_INTERVENTION_NEEDED_NOTIFICATION;
import static software.wings.common.NotificationMessageResolver.NotificationMessageType.NEEDS_RUNTIME_INPUTS;
import static software.wings.common.NotificationMessageResolver.NotificationMessageType.PIPELINE_NOTIFICATION;
import static software.wings.common.NotificationMessageResolver.NotificationMessageType.RUNTIME_INPUTS_PROVIDED;
import static software.wings.sm.ExecutionInterrupt.ExecutionInterruptBuilder.anExecutionInterrupt;
import static software.wings.sm.StateExecutionData.StateExecutionDataBuilder.aStateExecutionData;
import static software.wings.sm.StateExecutionInstance.Builder.aStateExecutionInstance;
import static software.wings.sm.StateType.ENV_LOOP_STATE;
import static software.wings.sm.StateType.ENV_ROLLBACK_STATE;
import static software.wings.sm.StateType.ENV_STATE;
import static software.wings.sm.StateType.FORK;
import static software.wings.sm.StateType.PHASE;

import static dev.morphia.mapping.Mapper.ID_KEY;
import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.alert.AlertData;
import io.harness.annotations.dev.BreakDependencyOn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.EventType;
import io.harness.beans.ExecutionInterruptType;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.FeatureName;
import io.harness.beans.PageResponse;
import io.harness.beans.WorkflowType;
import io.harness.config.PipelineConfig;
import io.harness.delay.DelayEventHelper;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskDetails;
import io.harness.delegate.beans.DelegateTaskNotifyResponseData;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.eraro.ErrorCode;
import io.harness.event.usagemetrics.UsageMetricsEventPublisher;
import io.harness.exception.ExceptionLogger;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.FailureType;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.logging.AutoLogContext;
import io.harness.observer.RemoteObserverInformer;
import io.harness.observer.Subject;
import io.harness.reflection.ReflectionUtils;
import io.harness.serializer.KryoSerializer;
import io.harness.serializer.MapperUtils;
import io.harness.state.inspection.ExpressionVariableUsage;
import io.harness.state.inspection.StateInspectionListener;
import io.harness.state.inspection.StateInspectionService;
import io.harness.tasks.ResponseData;
import io.harness.waiter.OldNotifyCallback;
import io.harness.waiter.WaitNotifyEngine;

import software.wings.api.ContinuePipelineResponseData;
import software.wings.api.EnvStateExecutionData;
import software.wings.api.SkipStateExecutionData;
import software.wings.beans.Account;
import software.wings.beans.Application;
import software.wings.beans.CanaryOrchestrationWorkflow;
import software.wings.beans.Environment;
import software.wings.beans.ErrorStrategy;
import software.wings.beans.InformationNotification;
import software.wings.beans.LoopEnvStateParams;
import software.wings.beans.NotificationRule;
import software.wings.beans.Pipeline;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.alert.AlertType;
import software.wings.beans.alert.ManualInterventionNeededAlert;
import software.wings.beans.alert.RuntimeInputsRequiredAlert;
import software.wings.common.NotificationMessageResolver;
import software.wings.dl.WingsPersistence;
import software.wings.exception.StateExecutionInstanceUpdateException;
import software.wings.exception.StateMachineIssueException;
import software.wings.expression.ManagerPreviewExpressionEvaluator;
import software.wings.service.impl.WorkflowExecutionUpdate;
import software.wings.service.impl.workflow.WorkflowNotificationDetails;
import software.wings.service.impl.workflow.WorkflowNotificationHelper;
import software.wings.service.intfc.AlertService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.NotificationService;
import software.wings.service.intfc.PipelineService;
import software.wings.service.intfc.StateExecutionService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.service.intfc.WorkflowService;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.sm.ElementNotifyResponseData.ElementNotifyResponseDataBuilder;
import software.wings.sm.StateExecutionInstance.StateExecutionInstanceKeys;
import software.wings.sm.states.ApprovalState;
import software.wings.sm.states.BarrierState;
import software.wings.sm.states.EnvLoopState;
import software.wings.sm.states.EnvState;
import software.wings.sm.states.ForkState;
import software.wings.sm.states.ForkState.ForkStateExecutionData;
import software.wings.sm.states.PhaseStepSubWorkflow;
import software.wings.sm.states.PhaseSubWorkflow;
import software.wings.sm.states.WorkflowState;
import software.wings.sm.status.StateStatusUpdateInfo;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import dev.morphia.FindAndModifyOptions;
import dev.morphia.query.FindOptions;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import dev.morphia.query.UpdateResults;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.modelmapper.MappingException;

/**
 * Class responsible for executing state machine.
 *
 * @author Rishi
 */
@OwnedBy(CDC)
@Singleton
@Slf4j
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
@BreakDependencyOn("software.wings.service.intfc.DelegateService")
public class StateMachineExecutor implements StateInspectionListener {
  public static final int DEFAULT_STATE_TIMEOUT_MILLIS = 4 * 60 * 60 * 1000; // 4 hours
  private static final int ABORT_EXPIRY_BUFFER_MILLIS = 10 * 60 * 1000; // 5 min
  public static final String PIPELINE_STEP_NAME = "PIPELINE_STEP_NAME";
  public static final String PIPELINE_STEP = "PIPELINE_STEP";
  public static final String PIPELINE_NAME = "PIPELINE_NAME";
  public static final String PIPELINE_URL = "PIPELINE_URL";
  public static final String PIPELINE = "PIPELINE";
  public static final String APPLICATION = "APPLICATION";
  public static final String APPLICATION_NAME = "APPLICATION_NAME";
  public static final String APPLICATION_URL = "APPLICATION_URL";
  public static final String DEBUG_LINE = "stateMachine processor: ";
  private static final String STATE_PARAMS = "stateParams";
  private static final String STATE_MACHINE_EXECUTOR_DEBUG_LINE = "STATE_MACHINE_EXECUTOR_DEBUG_LOG: ";
  private static final List<String> POSSIBLE_ROLLBACK_STATE_TYPES =
      asList(ENV_ROLLBACK_STATE.getType(), ENV_LOOP_STATE.getType(), FORK.getType());
  static final String TEMPLATE_VARIABLE_ENTRY = "templateVariables";
  private static final String VARIABLE_DESCRIPTION_FIELD = "description";

  @Getter private Subject<StateStatusUpdate> statusUpdateSubject = new Subject<>();

  @Inject private AlertService alertService;
  @Inject private AppService appService;
  @Inject private DelayEventHelper delayEventHelper;
  @Inject private DelegateService delegateService;
  @Inject private ExecutionInterruptManager executionInterruptManager;
  @Inject @Named("stateMachineExecutor-handler") private ExecutorService stateMachineExecutor;
  @Inject private ExecutorService executorService;
  @Inject private FeatureFlagService featureFlagService;
  @Inject private Injector injector;
  @Inject private NotificationMessageResolver notificationMessageResolver;
  @Inject private NotificationService notificationService;
  @Inject private StateExecutionService stateExecutionService;
  @Inject private StateInspectionService stateInspectionService;
  @Inject private WaitNotifyEngine waitNotifyEngine;
  @Inject private WingsPersistence wingsPersistence;
  @Inject private WorkflowExecutionService workflowExecutionService;
  @Inject private WorkflowNotificationHelper workflowNotificationHelper;
  @Inject private WorkflowService workflowService;
  @Inject private PipelineService pipelineService;
  @Inject private SweepingOutputService sweepingOutputService;
  @Inject private PipelineConfig pipelineConfig;
  @Inject private KryoSerializer kryoSerializer;
  @Inject private RemoteObserverInformer remoteObserverInformer;
  @Inject private WorkflowExecutionUpdate workflowExecutionUpdate;
  @Inject private UsageMetricsEventPublisher usageMetricsEventPublisher;
  /**
   * Execute.
   *
   * @param appId         the app id
   * @param executionUuid the execution uuid
   * @param executionName the execution name
   * @param contextParams the context params
   * @param callback      the callback
   * @return the state execution instance
   */
  public StateExecutionInstance execute(String appId, String executionUuid, String executionName,
      List<ContextElement> contextParams, StateMachineExecutionCallback callback) {
    WorkflowExecution workflowExecution = workflowExecutionService.getWorkflowExecution(appId, executionUuid);
    StateMachine stateMachine = workflowExecutionService.obtainStateMachine(workflowExecution);
    return execute(stateMachine, executionUuid, executionName, contextParams, callback, null);
  }

  /**
   * Execute.
   *
   * @param appId         the app id
   * @param executionUuid the execution uuid
   * @param executionName the execution name
   * @param contextParams the context params
   * @param callback      the callback
   * @param executionEventAdvisor      the executionEventAdvisor
   * @return the state execution instance
   */
  public StateExecutionInstance execute(String appId, String executionUuid, String executionName,
      List<ContextElement> contextParams, StateMachineExecutionCallback callback,
      ExecutionEventAdvisor executionEventAdvisor) {
    WorkflowExecution workflowExecution = workflowExecutionService.getWorkflowExecution(appId, executionUuid);
    StateMachine stateMachine = workflowExecutionService.obtainStateMachine(workflowExecution);
    return execute(stateMachine, executionUuid, executionName, contextParams, callback, executionEventAdvisor);
  }

  /**
   * Execute.
   *
   * @param sm            the sm
   * @param executionUuid the execution uuid
   * @param executionName the execution name
   * @param contextParams the context params
   * @param callback      the callback
   * @return the state execution instance
   */
  public StateExecutionInstance execute(StateMachine sm, String executionUuid, String executionName,
      List<ContextElement> contextParams, StateMachineExecutionCallback callback,
      ExecutionEventAdvisor executionEventAdvisor) {
    if (sm == null) {
      log.error("StateMachine passed for execution is null");
      throw new WingsException(INVALID_ARGUMENT).addParam("args", "State machine is null");
    }

    StateExecutionInstance.Builder stateExecutionInstanceBuilder =
        aStateExecutionInstance().executionName(executionName).executionUuid(executionUuid);

    LinkedList<ContextElement> contextElements = new LinkedList<>();
    if (contextParams != null) {
      contextElements.addAll(contextParams);
    }
    stateExecutionInstanceBuilder.contextElements(contextElements)
        .callback(callback)
        .stateName(sm.getInitialStateName())
        .displayName(sm.getInitialStateName());

    if (executionEventAdvisor != null) {
      stateExecutionInstanceBuilder.executionEventAdvisors(asList(executionEventAdvisor));
    }
    return triggerExecution(sm, stateExecutionInstanceBuilder.build());
  }

  /**
   * Execute.
   *
   * @param stateMachine           the state machine
   * @param stateExecutionInstance the state execution instance
   * @return the state execution instance
   */
  public StateExecutionInstance execute(StateMachine stateMachine, StateExecutionInstance stateExecutionInstance) {
    stateExecutionInstance = queue(stateMachine, stateExecutionInstance);
    return triggerExecution(stateMachine, stateExecutionInstance);
  }

  /**
   * Execute.
   *
   * @param stateMachine           the state machine
   * @param stateExecutionInstance the state execution instance
   * @return the state execution instance
   */
  public StateExecutionInstance queue(
      @NotNull StateMachine stateMachine, @NotNull StateExecutionInstance stateExecutionInstance) {
    if (stateExecutionInstance.getChildStateMachineId() != null
        && !stateExecutionInstance.getChildStateMachineId().equals(stateMachine.getUuid())
        && stateMachine.getChildStateMachines().get(stateExecutionInstance.getChildStateMachineId()) == null) {
      throw new InvalidRequestException(format("State instance %s child machine does not exist in the state machine %s",
          stateExecutionInstance.getUuid(), stateMachine.getUuid()));
    }
    StateMachine sm;
    if (stateExecutionInstance.getChildStateMachineId() == null) {
      sm = stateMachine;
    } else {
      sm = stateMachine.getChildStateMachines().get(stateExecutionInstance.getChildStateMachineId());
    }
    if (stateExecutionInstance.getDisplayName() == null) {
      stateExecutionInstance.setStateName(sm.getInitialStateName());
      stateExecutionInstance.setDisplayName(sm.getInitialStateName());
    }
    return saveStateExecutionInstance(stateMachine, stateExecutionInstance);
  }

  private StateExecutionInstance saveStateExecutionInstance(
      StateMachine stateMachine, StateExecutionInstance stateExecutionInstance) {
    notNullCheck("displayName", stateExecutionInstance.getDisplayName());
    notNullCheck("stateName", stateExecutionInstance.getStateName());

    stateExecutionInstance.setAppId(stateMachine.getAppId());
    stateExecutionInstance.setAccountId(stateMachine.getAccountId());
    State state =
        stateMachine.getState(stateExecutionInstance.getChildStateMachineId(), stateExecutionInstance.getStateName());
    if (state == null && stateExecutionInstance.isParentLoopedState()) {
      if (stateExecutionInstance.getLoopedStateParams() == null) {
        throw new InvalidRequestException("Looped Params cant be null when state is looped");
      }
      state = stateExecutionInstance.getLoopedStateParams().getEnvStateInstanceFromParams(
          workflowService, stateMachine.getAppId());
      stateMachine.addState(state);
    }

    notNullCheck("state", state);
    stateExecutionInstance.setRollback(state.isRollback());
    stateExecutionInstance.setStateType(state.getStateType());

    if (state instanceof EnvState || state instanceof EnvLoopState) {
      WorkflowState envState = (WorkflowState) state;
      stateExecutionInstance.setPipelineStageElementId(envState.getPipelineStageElementId());
      stateExecutionInstance.setPipelineStageParallelIndex(envState.getPipelineStageParallelIndex());
      stateExecutionInstance.setStageName(envState.getStageName());
    } else if (state instanceof ApprovalState) {
      ApprovalState approvalState = (ApprovalState) state;
      stateExecutionInstance.setStageName(approvalState.getStageName());
    }

    if (state instanceof PhaseStepSubWorkflow) {
      stateExecutionInstance.setPhaseSubWorkflowId(((PhaseStepSubWorkflow) state).getSubWorkflowId());
    } else if (state instanceof PhaseSubWorkflow) {
      stateExecutionInstance.setPhaseSubWorkflowId(((PhaseSubWorkflow) state).getSubWorkflowId());
    } else {
      stateExecutionInstance.setPhaseSubWorkflowId(null);
    }

    stateExecutionInstance.setStepId(state instanceof BarrierState ? state.getId() : null);

    if (stateExecutionInstance.getUuid() != null) {
      throw new InvalidRequestException("StateExecutionInstance was already created");
    }

    stateExecutionInstance.setExpiryTs(Long.MAX_VALUE);
    wingsPersistence.save(stateExecutionInstance);
    try {
      usageMetricsEventPublisher.publishDeploymentStepTimeSeriesEvent(
          stateExecutionInstance.getAccountId(), stateExecutionInstance);
    } catch (Exception e) {
      log.error("Failed to publish deployment step event [{}] , [{}]", stateExecutionInstance, e);
    }
    return stateExecutionInstance;
  }

  @NotNull
  private Integer getDefaultTimeout(State state, ExecutionContext context) {
    injector.injectMembers(state);
    Integer timeout = state.getTimeoutMillis(context);
    if (timeout != null) {
      return timeout;
    }
    return DEFAULT_STATE_TIMEOUT_MILLIS;
  }

  private Long evaluateExpiryTs(State state, ExecutionContext context) {
    Integer timeout = getDefaultTimeout(state, context);
    if (pipelineConfig != null && pipelineConfig.isEnabled()) {
      if (StateType.ENV_STATE.name().equals(state.getStateType())) {
        timeout = pipelineConfig.getEnvStateTimeout();
      }
    }
    if (State.INFINITE_TIMEOUT.equals(timeout)) {
      return Long.MAX_VALUE;
    } else {
      if (timeout == null) {
        timeout = DEFAULT_STATE_TIMEOUT_MILLIS;
      }
      if (state.getWaitInterval() != null) {
        timeout += state.getWaitInterval() * 1000;
      }
      return System.currentTimeMillis() + timeout;
    }
  }

  /**
   * Trigger execution state execution instance.
   *
   * @param stateMachine           the state machine
   * @param stateExecutionInstance the state execution instance
   * @return the state execution instance
   */
  StateExecutionInstance triggerExecution(StateMachine stateMachine, StateExecutionInstance stateExecutionInstance) {
    stateExecutionInstance = saveAndUpdateStateExecutionInstance(stateMachine, stateExecutionInstance);
    submitToSMDispatcher(stateExecutionInstance, stateMachine);
    return stateExecutionInstance;
  }

  private void submitToSMDispatcher(StateExecutionInstance stateExecutionInstance, StateMachine stateMachine) {
    ExecutionContextImpl context = new ExecutionContextImpl(stateExecutionInstance, stateMachine, injector);
    log.info(DEBUG_LINE + "about to start stateExecutionInstance for {}", stateExecutionInstance.getUuid());
    if (featureFlagService.isEnabled(FeatureName.REFACTOR_STATEMACHINEXECUTOR, context.getAccountId())) {
      stateMachineExecutor.execute(new SmExecutionDispatcher(context, this));
    } else {
      executorService.execute(new SmExecutionDispatcher(context, this));
    }
  }

  private StateExecutionInstance saveAndUpdateStateExecutionInstance(
      StateMachine stateMachine, StateExecutionInstance stateExecutionInstance) {
    log.info(DEBUG_LINE + "save and update stateMachineInstance");
    stateExecutionInstance = saveStateExecutionInstance(stateMachine, stateExecutionInstance);
    ExecutionContextImpl context = new ExecutionContextImpl(stateExecutionInstance, stateMachine, injector);
    injector.injectMembers(context);
    return updateStateExecutionInstanceTimeout(stateMachine, stateExecutionInstance, context);
  }

  /**
   * Start execution.
   *
   * @param appId                    the app id
   * @param executionUuid the executionUuid
   */
  public boolean startQueuedExecution(String appId, String executionUuid) {
    PageResponse<StateExecutionInstance> pageResponse = wingsPersistence.query(StateExecutionInstance.class,
        aPageRequest().addFilter("appId", EQ, appId).addFilter("executionUuid", EQ, executionUuid).build());

    if (pageResponse == null || pageResponse.size() != 1 || pageResponse.get(0) == null
        || !(pageResponse.get(0).getStatus() == QUEUED || pageResponse.get(0).getStatus() == NEW)) {
      return false;
    }
    StateExecutionInstance stateExecutionInstance = pageResponse.get(0);
    StateMachine stateMachine = stateExecutionService.obtainStateMachine(stateExecutionInstance);
    log.info("Starting execution of StateMachine {} with initialState {}", stateMachine.getName(),
        stateMachine.getInitialStateName());
    startExecution(stateMachine, stateExecutionInstance);
    return true;
  }

  /**
   * Start execution.
   *
   * @param appId                    the app id
   * @param executionUuid the execution Uuid
   * @param stateExecutionInstanceId the state execution instance id
   */
  void startExecution(String appId, String executionUuid, String stateExecutionInstanceId) {
    StateExecutionInstance stateExecutionInstance =
        getStateExecutionInstance(appId, executionUuid, stateExecutionInstanceId);
    StateMachine sm = stateExecutionService.obtainStateMachine(stateExecutionInstance);
    startExecution(sm, stateExecutionInstance);
  }

  void startExecutionRuntime(String appId, String executionUuid, String stateExecutionInstanceId, StateMachine sm) {
    StateExecutionInstance stateExecutionInstance =
        getStateExecutionInstance(appId, executionUuid, stateExecutionInstanceId);
    startExecution(sm, stateExecutionInstance);
  }

  /**
   * Start execution.
   *
   * @param stateMachine                    the stateMachine
   * @param stateExecutionInstance the stateExecutionInstance
   */
  public void startExecution(StateMachine stateMachine, StateExecutionInstance stateExecutionInstance) {
    ExecutionContextImpl context = new ExecutionContextImpl(stateExecutionInstance, stateMachine, injector);
    injector.injectMembers(context);
    StateExecutionInstance instance =
        updateStateExecutionInstanceTimeout(stateMachine, stateExecutionInstance, context);
    context = new ExecutionContextImpl(instance, stateMachine, injector);
    startExecution(context);
  }

  /**
   * Start execution.
   *
   * @param context the context
   */
  void startExecution(ExecutionContextImpl context) {
    StateExecutionInstance stateExecutionInstance = context.getStateExecutionInstance();

    ExecutionInterrupt reason = null;
    List<ExecutionInterrupt> executionInterrupts = executionInterruptManager.checkForExecutionInterrupt(
        stateExecutionInstance.getAppId(), stateExecutionInstance.getExecutionUuid());
    if (executionInterrupts != null) {
      Optional<ExecutionInterrupt> pauseAll =
          executionInterrupts.stream()
              .filter(ei -> ei != null && ei.getExecutionInterruptType() == PAUSE_ALL)
              .findFirst();
      if (pauseAll.isPresent()) {
        updateStatusPauseAndWaiting(stateExecutionInstance, PAUSED, Lists.newArrayList(NEW, QUEUED), pauseAll.get());
        waitNotifyEngine.waitForAllOn(ORCHESTRATION,
            new ExecutionResumeAllCallback(stateExecutionInstance.getAppId(), stateExecutionInstance.getExecutionUuid(),
                stateExecutionInstance.getUuid()),
            pauseAll.get().getUuid());
        return;
      }

      if (stateExecutionInstance.getStatus() == PAUSED) {
        Optional<ExecutionInterrupt> resumeAll =
            executionInterrupts.stream()
                .filter(ei -> ei != null && ei.getExecutionInterruptType() == RESUME_ALL)
                .findFirst();

        if (resumeAll.isPresent()) {
          reason = resumeAll.get();
        }

        Optional<ExecutionInterrupt> continueFromUI =
            executionInterrupts.stream()
                .filter(ei -> ei != null && ei.getExecutionInterruptType() == CONTINUE_PIPELINE_STAGE)
                .findFirst();

        if (continueFromUI.isPresent()) {
          reason = continueFromUI.get();
        }
      }
    }

    StateMachine stateMachine = context.getStateMachine();
    boolean updated =
        updateStartStatus(stateExecutionInstance, STARTING, Lists.newArrayList(NEW, QUEUED, PAUSED, WAITING), reason);
    if (!updated) {
      throw new WingsException("stateExecutionInstance: " + stateExecutionInstance.getUuid() + " could not be started");
    }
    State currentState =
        stateMachine.getState(stateExecutionInstance.getChildStateMachineId(), stateExecutionInstance.getStateName());
    if (currentState == null && stateExecutionInstance.isParentLoopedState()) {
      log.info(
          DEBUG_LINE + "Parent Looped State is set, call startStateExecution for {}", stateExecutionInstance.getUuid());
      startStateExecution(context, stateExecutionInstance);
      return;
    }
    notNullCheck("currentState", currentState);
    if (currentState.getWaitInterval() != null && currentState.getWaitInterval() > 0) {
      if (skipDelayedStepIfRequired(context, currentState)) {
        return;
      }
      StateExecutionData stateExecutionData =
          aStateExecutionData()
              .withWaitInterval(currentState.getWaitInterval())
              .withErrorMsg("Waiting " + currentState.getWaitInterval() + " seconds before execution")
              .build();
      updated = updateStateExecutionData(stateExecutionInstance, stateExecutionData, STARTING, null, null, null, null,
          null, evaluateExpiryTs(currentState, context));
      if (!updated) {
        throw new WingsException("updateStateExecutionData failed");
      }
      String resumeId = delayEventHelper.delay(currentState.getWaitInterval(), Collections.emptyMap());
      waitNotifyEngine.waitForAllOn(ORCHESTRATION,
          new ExecutionWaitCallback(stateExecutionInstance.getAppId(), stateExecutionInstance.getExecutionUuid(),
              stateExecutionInstance.getUuid()),
          resumeId);
      return;
    }

    startStateExecution(context, stateExecutionInstance);
  }

  boolean skipDelayedStepIfRequired(ExecutionContextImpl context, State currentState) {
    ExecutionEventAdvice executionEventAdvice = invokeAdvisors(ExecutionEvent.builder()
                                                                   .failureTypes(EnumSet.noneOf(FailureType.class))
                                                                   .context(context)
                                                                   .state(currentState)
                                                                   .build());
    if (executionEventAdvice != null && executionEventAdvice.isSkipState()) {
      handleResponse(context, skipStateExecutionResponse(executionEventAdvice));
      return true;
    }
    return false;
  }

  void startStateExecution(String appId, String executionUuid, String stateExecutionInstanceId) {
    log.info("startStateExecution called after wait");

    StateExecutionInstance stateExecutionInstance =
        getStateExecutionInstance(appId, executionUuid, stateExecutionInstanceId);
    StateMachine sm = stateExecutionService.obtainStateMachine(stateExecutionInstance);
    ExecutionContextImpl context = new ExecutionContextImpl(stateExecutionInstance, sm, injector);
    injector.injectMembers(context);
    startStateExecution(context, stateExecutionInstance);
  }

  private void startStateExecution(ExecutionContextImpl context, StateExecutionInstance stateExecutionInstance) {
    ExecutionResponse executionResponse = null;
    WingsException ex = null;
    try (AutoLogContext ignore = context.autoLogContext()) {
      State currentState = getStateForExecution(context, stateExecutionInstance);
      executionResponse = getExecutionResponseWithAdvise(context, executionResponse, currentState);

      handleResponse(context, executionResponse);
    } catch (StateExecutionInstanceUpdateException exception) {
      log.error(
          String.format("Exception occurred while updating state execution instance : %s", stateExecutionInstance),
          exception);
    } catch (WingsException exception) {
      ex = exception;
      log.error(
          String.format("Exception occurred while starting state execution : %s", stateExecutionInstance), exception);
    } catch (Exception exception) {
      ex = new WingsException(exception);
      log.error(String.format("Exception occurred while starting state execution : %s", stateExecutionInstance), ex);
    }

    if (ex != null) {
      handleExecuteResponseException(context, ex);
    }
  }

  @VisibleForTesting
  protected void handleResponse(ExecutionContextImpl context, ExecutionResponse executionResponse) {
    Map<String, Map<Object, Integer>> usage = context.getVariableResolverTracker().getUsage();
    ManagerPreviewExpressionEvaluator expressionEvaluator = new ManagerPreviewExpressionEvaluator();

    if (isNotEmpty(usage)) {
      List<ExpressionVariableUsage.Item> items = new ArrayList<>();
      usage.forEach(
          (expression, values)
              -> values.forEach(
                  (expressionValue, count)
                      -> items.add(ExpressionVariableUsage.Item.builder()
                                       .expression(expression)
                                       .value(expressionEvaluator.substitute(expressionValue.toString(), emptyMap()))
                                       .count(count)
                                       .build())));

      stateInspectionService.append(
          context.getStateExecutionInstance().getUuid(), ExpressionVariableUsage.builder().variables(items).build());
    }

    handleExecuteResponse(context, executionResponse);
  }

  private ExecutionResponse getExecutionResponseWithAdvise(
      ExecutionContextImpl context, ExecutionResponse executionResponse, State currentState) {
    ExecutionEventAdvice executionEventAdvice = invokeAdvisors(ExecutionEvent.builder()
                                                                   .failureTypes(EnumSet.noneOf(FailureType.class))
                                                                   .context(context)
                                                                   .state(currentState)
                                                                   .build());
    if (executionEventAdvice != null) {
      if (executionEventAdvice.isSkipState()) {
        executionResponse = skipStateExecutionResponse(executionEventAdvice);
      } else if (executionEventAdvice.getExecutionInterruptType() == PAUSE_FOR_INPUTS) {
        executionResponse = executionEventAdvice.getExecutionResponse();
      } else if (executionEventAdvice.getExecutionResponse() != null) {
        executionResponse = executionEventAdvice.getExecutionResponse();
      }
    } else {
      executionResponse = currentState.execute(context);
    }
    return executionResponse;
  }

  public State getStateForExecution(ExecutionContextImpl context, StateExecutionInstance stateExecutionInstance) {
    StateMachine stateMachine = context.getStateMachine();
    State currentState =
        stateMachine.getState(stateExecutionInstance.getChildStateMachineId(), stateExecutionInstance.getStateName());
    notNullCheck("currentState", currentState);

    log.info("startStateExecution for State {} of type {}", currentState.getName(), currentState.getStateType());

    if (stateExecutionInstance.getStateParams() != null) {
      mapObject(stateExecutionInstance.getStateParams(), currentState, context.getAccountId());
    }
    injector.injectMembers(currentState);
    return currentState;
  }

  @VisibleForTesting
  public static ExecutionResponse skipStateExecutionResponse(ExecutionEventAdvice executionEventAdvice) {
    if (isNotEmpty(executionEventAdvice.getSkipError())) {
      return ExecutionResponse.builder()
          .executionStatus(FAILED)
          .errorMessage(executionEventAdvice.getSkipError())
          .stateExecutionData(SkipStateExecutionData.builder().build())
          .build();
    }

    return ExecutionResponse.builder()
        .executionStatus(SKIPPED)
        .errorMessage("Skip condition: " + executionEventAdvice.getSkipExpression())
        .stateExecutionData(
            SkipStateExecutionData.builder().skipAssertionExpression(executionEventAdvice.getSkipExpression()).build())
        .build();
  }

  private ExecutionEventAdvice invokeAdvisors(ExecutionEvent executionEvent) {
    List<ExecutionEventAdvisor> advisors =
        executionEvent.getContext().getStateExecutionInstance().getExecutionEventAdvisors();
    if (isEmpty(advisors)) {
      return null;
    }

    log.info("Calculating advice from advisors : {}", advisors);
    ExecutionEventAdvice executionEventAdvice = null;
    for (ExecutionEventAdvisor advisor : advisors) {
      executionEventAdvice = advisor.onExecutionEvent(executionEvent);
    }
    log.info("Issued Execution event advice : {}", executionEventAdvice);
    return executionEventAdvice;
  }

  /**
   * Handle execute response state execution instance.
   *
   * @param context           the context
   * @param executionResponse the execution response
   * @return the state execution instance
   */
  @SuppressWarnings("PMD")
  StateExecutionInstance handleExecuteResponse(ExecutionContextImpl context, ExecutionResponse executionResponse) {
    StateExecutionInstance stateExecutionInstance = context.getStateExecutionInstance();
    StateMachine sm = context.getStateMachine();
    try {
      State currentState =
          sm.getState(stateExecutionInstance.getChildStateMachineId(), stateExecutionInstance.getStateName());
      Long expiryTs = stateExecutionInstance.getExpiryTs();
      ExecutionStatus status = executionResponse.getExecutionStatus();
      if (executionResponse.isAsync()) {
        log.info("Got an async response");
        if (isEmpty(executionResponse.getCorrelationIds())) {
          log.error("executionResponse is null, but no correlationId - currentState : " + currentState.getName()
              + ", stateExecutionInstanceId: " + stateExecutionInstance.getUuid());
          status = ERROR;
        } else {
          if (status != PAUSED) {
            status = RUNNING;
            expiryTs = stateExecutionInstance.getStatus() == status ? stateExecutionInstance.getExpiryTs()
                                                                    : evaluateExpiryTs(currentState, context);
          } else if (StateType.APPROVAL.name().equals(stateExecutionInstance.getStateType())) {
            expiryTs = evaluateExpiryTs(currentState, context);
          }
          OldNotifyCallback callback = new StateMachineResumeCallback(stateExecutionInstance.getAppId(),
              stateExecutionInstance.getExecutionUuid(), stateExecutionInstance.getUuid());
          waitNotifyEngine.waitForAllOn(
              ORCHESTRATION, callback, executionResponse.getCorrelationIds().toArray(new String[0]));
          log.info("Created a state machine resume callback and updated to wait notify engine");
        }

        boolean updated = updateStateExecutionData(stateExecutionInstance, executionResponse.getStateExecutionData(),
            status, executionResponse.getErrorMessage(), executionResponse.getContextElements(),
            executionResponse.getNotifyElements(), executionResponse.getDelegateTaskId(), expiryTs);
        log.info("Updated state execution data successfully? : {}", updated);
        if (!updated) {
          // Currently, it is by design that handle execute response can be in race with some other ways to update the
          // state. Say it can be aborted.
          StateExecutionInstance dbStateExecutionInstance =
              wingsPersistence.get(StateExecutionInstance.class, stateExecutionInstance.getUuid());
          if (ExecutionStatus.isFinalStatus(dbStateExecutionInstance.getStatus())) {
            log.warn("updateStateExecutionData failed. StateExecutionInstance is in status: "
                + stateExecutionInstance.getStatus());
            throw new StateExecutionInstanceUpdateException("updateStateExecutionData failed");
          } else {
            throw new WingsException("updateStateExecutionData failed", WingsException.NOBODY);
          }
        }
        invokeAdvisors(ExecutionEvent.builder()
                           .failureTypes(executionResponse.getFailureTypes())
                           .context(context)
                           .state(currentState)
                           .build());

        log.info("Invoked advisors with execution event ");
        if (status == RUNNING) {
          handleSpawningStateExecutionInstances(sm, stateExecutionInstance, executionResponse);
        }

      } else {
        boolean updated = updateStateExecutionData(stateExecutionInstance, executionResponse.getStateExecutionData(),
            status, executionResponse.getErrorMessage(), executionResponse.getContextElements(),
            executionResponse.getNotifyElements(),
            RUNNING == status ? evaluateExpiryTs(currentState, context) : expiryTs);
        if (!updated) {
          log.info("State Execution Instance {} update failed Retrying", stateExecutionInstance.getUuid());
          return reloadStateExecutionInstanceAndCheckStatus(stateExecutionInstance);
        }

        log.info("Updated state execution data successfully? : {}", updated);

        ExecutionEventAdvice executionEventAdvice =
            invokeAdvisors(ExecutionEvent.builder()
                               .failureTypes(executionResponse.getFailureTypes())
                               .context(context)
                               .state(currentState)
                               .build());

        log.info("Invoked advisors with execution event ");

        if (executionEventAdvice != null && !executionEventAdvice.isSkipState() && SKIPPED != status) {
          log.info(
              "Execution Advise is not null. Handling Advise : {}", executionEventAdvice.getExecutionInterruptType());
          return handleExecutionEventAdvice(context, stateExecutionInstance, status, executionEventAdvice);
        } else if (isPositiveStatus(status)) {
          log.info("Execution Advise is null. Starting Positive Transition : {}", status);
          return successTransition(context);
        } else if (isBrokeStatus(status)) {
          log.info("Execution Advise is null. Starting Failed Transition : {}", status);
          return failedTransition(context, null);
        } else if (ExecutionStatus.isDiscontinueStatus(status)) {
          log.info("Execution Advise is null. Starting Discontinue Transition : {}", status);
          endTransition(context, stateExecutionInstance, status, null);
        } else {
          log.info("Execution Advise is null. Execution status is {}", status);
        }
      }
      return stateExecutionInstance;
    } catch (Exception ex) {
      log.error("Error Occurred while handling the execution response: {}", ex.getStackTrace());
      throw ex;
    } catch (Throwable t) {
      log.error("Encountered a throwable while handling the execution response: {}", t.getStackTrace());
      throw t;
    }
  }

  private StateExecutionInstance updateStateExecutionInstanceTimeout(
      StateMachine stateMachine, StateExecutionInstance stateExecutionInstance, ExecutionContext context) {
    State state =
        stateMachine.getState(stateExecutionInstance.getChildStateMachineId(), stateExecutionInstance.getStateName());
    if (state == null) {
      log.warn(STATE_MACHINE_EXECUTOR_DEBUG_LINE + "State fetched from state machine is null. This is very bad");
      stateMachine.clearCache();
      state =
          stateMachine.getState(stateExecutionInstance.getChildStateMachineId(), stateExecutionInstance.getStateName());
      if (state == null) {
        log.error(STATE_MACHINE_EXECUTOR_DEBUG_LINE
            + "State fetched is still null. The states in the state machine are:" + stateMachine.getStates());
      }
    }
    Integer executionInstanceTimeout = getDefaultTimeout(state, context);
    final Query<StateExecutionInstance> stateExecutionInstanceQuery =
        wingsPersistence.createQuery(StateExecutionInstance.class)
            .field(StateExecutionInstanceKeys.uuid)
            .equal(stateExecutionInstance.getUuid());
    final UpdateOperations<StateExecutionInstance> updateOperations =
        wingsPersistence.createUpdateOperations(StateExecutionInstance.class)
            .set(StateExecutionInstanceKeys.stateTimeout, executionInstanceTimeout);

    StateExecutionInstance updated = wingsPersistence.findAndModify(
        stateExecutionInstanceQuery, updateOperations, new FindAndModifyOptions().returnNew(true));
    if (updated == null) {
      log.error("[TimeOut Op] StateExecutionInstance stateTimeout update Failed");
    }
    return updated;
  }

  private StateExecutionInstance reloadStateExecutionInstanceAndCheckStatus(
      StateExecutionInstance stateExecutionInstance) {
    String stateExecutionInstanceId = stateExecutionInstance.getUuid();
    stateExecutionInstance = getStateExecutionInstance(
        stateExecutionInstance.getAppId(), stateExecutionInstance.getExecutionUuid(), stateExecutionInstanceId);
    if (isFinalStatus(stateExecutionInstance.getStatus())) {
      if (log.isDebugEnabled()) {
        log.debug("StateExecutionInstance already reached the final status. Skipping the update for "
            + stateExecutionInstanceId);
      }
      return stateExecutionInstance;
    } else {
      throw new WingsException("updateStateExecutionData failed");
    }
  }

  @VisibleForTesting
  protected StateExecutionInstance handleExecutionEventAdvice(ExecutionContextImpl context,
      StateExecutionInstance stateExecutionInstance, ExecutionStatus status,
      ExecutionEventAdvice executionEventAdvice) {
    // NOTE: Pre-requisites for calling this function:
    // - executionEventAdvice != null
    // - !executionEventAdvice.isSkipState()
    if (executionEventAdvice == null || executionEventAdvice.isSkipState()
        || executionEventAdvice.getExecutionInterruptType() == null) {
      return stateExecutionInstance;
    }

    switch (executionEventAdvice.getExecutionInterruptType()) {
      case MARK_FAILED: {
        return failedTransition(context, null);
      }
      case MARK_SUCCESS: {
        if (status != SUCCESS) {
          updateEndStatus(stateExecutionInstance, SUCCESS, asList(status));
        }
        return successTransition(context);
      }
      case IGNORE: {
        return successTransition(context);
      }
      case ABORT: {
        endTransition(context, stateExecutionInstance, ABORTED, null);
        break;
      }
      case PAUSE: {
        log.info(
            "[TimeOut Op]: Updating expiryTs to Long.MAX_VALUE on PAUSE Advice for stateExecutionInstance id: {}, name: {}",
            stateExecutionInstance.getUuid(), stateExecutionInstance.getDisplayName());
        updateStatus(stateExecutionInstance, WAITING, brokeStatuses(), null, ops -> {
          ops.set(StateExecutionInstanceKeys.expiryTs, Long.MAX_VALUE);
          if (executionEventAdvice.getStateParams() != null) {
            ops.set(STATE_PARAMS, executionEventAdvice.getStateParams());
          }
        });

        // Open an alert
        Environment environment = context.getEnv();
        String pipelineExecutionId = workflowExecutionService.getPipelineExecutionId(
            stateExecutionInstance.getAppId(), context.getWorkflowExecutionId());
        WorkflowType workflowType = pipelineExecutionId != null ? WorkflowType.PIPELINE : WorkflowType.ORCHESTRATION;
        ManualInterventionNeededAlert manualInterventionNeededAlert =
            ManualInterventionNeededAlert.builder()
                .envId(environment != null ? environment.getUuid() : null)
                .stateExecutionInstanceId(stateExecutionInstance.getUuid())
                .executionId(context.getWorkflowExecutionId())
                .pipelineExecutionId(pipelineExecutionId)
                .workflowType(workflowType)
                .name(context.getWorkflowExecutionName())
                .build();
        openAnAlert(context, manualInterventionNeededAlert);
        sendManualInterventionNeededNotification(context, Long.MAX_VALUE);
        break;
      }
      case WAITING_FOR_MANUAL_INTERVENTION: {
        log.info(
            "[TimeOut Op]: Updating expiryTs considering manualInterventionTimeout on WAITING_FOR_MANUAL_INTERVENTION Advice for stateExecutionInstance id: {}, name: {}",
            stateExecutionInstance.getUuid(), stateExecutionInstance.getDisplayName());
        updateStateExecutionInstanceForManualInterventions(stateExecutionInstance, status, executionEventAdvice);

        // Open an alert
        WorkflowExecution workflowExecution =
            workflowExecutionService.getWorkflowExecution(context.getAppId(), context.getWorkflowExecutionId());
        Environment environment = context.getEnv();
        String pipelineExecutionId = workflowExecution.getPipelineExecutionId();
        WorkflowType workflowType = pipelineExecutionId != null ? WorkflowType.PIPELINE : WorkflowType.ORCHESTRATION;
        ManualInterventionNeededAlert manualInterventionNeededAlert =
            ManualInterventionNeededAlert.builder()
                .envId(environment != null ? environment.getUuid() : null)
                .stateExecutionInstanceId(stateExecutionInstance.getUuid())
                .executionId(context.getWorkflowExecutionId())
                .pipelineExecutionId(pipelineExecutionId)
                .workflowType(workflowType)
                .name(context.getWorkflowExecutionName())
                .build();
        openAnAlert(context, manualInterventionNeededAlert);
        StateStatusUpdateInfo arg =
            StateStatusUpdateInfo.buildFromStateExecutionInstance(stateExecutionInstance, false);

        // Change this to WF_PAUSE once it is merged
        workflowExecutionUpdate.publish(workflowExecution, arg, EventType.PIPELINE_PAUSE);
        sendManualInterventionNeededNotification(context, stateExecutionInstance.getExpiryTs());
        break;
      }
      case PAUSE_FOR_INPUTS: {
        log.info(
            "Updating expiryTs to Timeout value so that state is paused until timeout. stateExecutionInstance id: {}, name: {}",
            stateExecutionInstance.getUuid(), stateExecutionInstance.getDisplayName());
        // update expiry and status to paused/waiting
        updateStateExecutionInstanceForRuntimeInputs(stateExecutionInstance, status, executionEventAdvice);
        OldNotifyCallback callback = new PipelineContinueWithInputsCallback(stateExecutionInstance.getAppId(),
            stateExecutionInstance.getExecutionUuid(), stateExecutionInstance.getUuid(),
            stateExecutionInstance.getPipelineStageElementId());
        waitNotifyEngine.waitForAllOn(ORCHESTRATION, callback,
            getContinuePipelineWaitId(
                stateExecutionInstance.getPipelineStageElementId(), stateExecutionInstance.getExecutionUuid()));

        // Open an alert
        Environment environment = context.getEnv();
        RuntimeInputsRequiredAlert runtimeInputsRequiredAlert =
            RuntimeInputsRequiredAlert.builder()
                .envId(environment != null ? environment.getUuid() : null)
                .stateExecutionInstanceId(stateExecutionInstance.getUuid())
                .executionId(context.getWorkflowExecutionId())
                .name(context.getWorkflowExecutionName())
                .build();
        openAnAlert(context, runtimeInputsRequiredAlert);
        sendRuntimeInputNeededNotification(context, executionEventAdvice, stateExecutionInstance);
        break;
      }
      case NEXT_STEP:
      case ROLLBACK_PROVISIONER_AFTER_PHASES:
      case ROLLBACK:
      case ROLLBACK_ON_APPROVAL:
      case ROLLBACK_PROVISIONER_AFTER_PHASES_ON_APPROVAL: {
        if (executionEventAdvice.getNextChildStateMachineId() != null
            || executionEventAdvice.getNextStateName() != null) {
          executionEventAdviceTransition(context, executionEventAdvice);
          break;
        }
        throw new WingsException(INVALID_ARGUMENT).addParam("args", "rollbackStateMachineId or rollbackStateName");
      }
      case ROLLBACK_PREVIOUS_STAGES_ON_PIPELINE: {
        return rollbackPreviousPipelineStages(context, executionEventAdvice);
      }
      case ROLLBACK_DONE: {
        if (checkIfOnDemand(context.getAppId(), context.getWorkflowExecutionId())) {
          updateEndStatus(stateExecutionInstance, SUCCESS, asList(status));
          return successTransition(context);
        }
        endTransition(context, stateExecutionInstance, FAILED, null);
        break;
      }
      case RETRY: {
        if (executionEventAdvice.getWaitInterval() != null && executionEventAdvice.getWaitInterval() > 0) {
          log.info("Retry Wait Interval : {}", executionEventAdvice.getWaitInterval());
          String resumeId = delayEventHelper.delay(executionEventAdvice.getWaitInterval(), Collections.emptyMap());
          waitNotifyEngine.waitForAllOn(ORCHESTRATION,
              new ExecutionWaitRetryCallback(stateExecutionInstance.getAppId(),
                  stateExecutionInstance.getExecutionUuid(), stateExecutionInstance.getUuid()),
              resumeId);
        } else {
          executionInterruptManager.registerExecutionInterrupt(
              anExecutionInterrupt()
                  .appId(stateExecutionInstance.getAppId())
                  .executionUuid(stateExecutionInstance.getExecutionUuid())
                  .stateExecutionInstanceId(stateExecutionInstance.getUuid())
                  .executionInterruptType(RETRY)
                  .build());
        }
        break;
      }
      case END_EXECUTION: {
        if (!isFinalStatus(status)) {
          status = ABORTED;
        }
        endTransition(context, stateExecutionInstance, status, null);
        break;
      }
      default: {
        throw new WingsException(INVALID_ARGUMENT)
            .addParam("args",
                "executionEventAdvice.getExecutionInterruptType: " + executionEventAdvice.getExecutionInterruptType());
      }
    }

    return stateExecutionInstance;
  }

  private boolean hasRollbackInstancesOnExecution(String workflowExecutionId) {
    List<StateExecutionInstance> rollbackInstances =
        wingsPersistence.createQuery(StateExecutionInstance.class)
            .filter(StateExecutionInstanceKeys.executionUuid, workflowExecutionId)
            .filter(StateExecutionInstanceKeys.rollback, true)
            .asList(new FindOptions().limit(1));

    return !rollbackInstances.isEmpty();
  }

  private StateExecutionInstance rollbackPreviousPipelineStages(
      ExecutionContextImpl context, ExecutionEventAdvice executionEventAdvice) {
    StateMachine sm = context.getStateMachine();
    State nextState = sm.getState(null, executionEventAdvice.getNextStateName());

    StateExecutionData stateExecutionData = context.getStateExecutionData();
    if (stateExecutionData instanceof EnvStateExecutionData) {
      EnvStateExecutionData envStateExecutionData = (EnvStateExecutionData) context.getStateExecutionData();
      if (hasRollbackInstancesOnExecution(envStateExecutionData.getWorkflowExecutionId())) {
        nextState = sm.getNextState(nextState.getName(), TransitionType.SUCCESS);
      }
    } else if (stateExecutionData instanceof ForkStateExecutionData) {
      Map<String, StateExecutionInstance> executionToInstanceMap = new HashMap<>();
      StateExecutionInstance parentInstance = context.getStateExecutionInstance();
      List<StateExecutionInstance> childFailedEnvInstances =
          wingsPersistence.createQuery(StateExecutionInstance.class)
              .filter(StateExecutionInstanceKeys.appId, context.getAppId())
              .filter(StateExecutionInstanceKeys.parentInstanceId, parentInstance.getUuid())
              .filter(StateExecutionInstanceKeys.status, FAILED)
              .filter(StateExecutionInstanceKeys.stateType, ENV_STATE.getType())
              .asList();

      Set<String> failedWorkflowExecutionIdsSet =
          childFailedEnvInstances.stream()
              .map(instance -> {
                EnvStateExecutionData envStateExecutionData =
                    (EnvStateExecutionData) instance.fetchStateExecutionData();
                executionToInstanceMap.put(envStateExecutionData.getWorkflowExecutionId(), instance);
                return envStateExecutionData.getWorkflowExecutionId();
              })
              .collect(Collectors.toSet());

      List<StateExecutionInstance> rollbackInstances =
          wingsPersistence.createQuery(StateExecutionInstance.class)
              .filter(StateExecutionInstanceKeys.appId, context.getAppId())
              .field(StateExecutionInstanceKeys.executionUuid)
              .in(failedWorkflowExecutionIdsSet)
              .filter(StateExecutionInstanceKeys.rollback, true)
              .filter(StateExecutionInstanceKeys.stateType, PHASE.getType())
              .project(StateExecutionInstanceKeys.executionUuid, true)
              .asList();

      Set<String> executionsWithRollback =
          rollbackInstances.stream().map(StateExecutionInstance::getExecutionUuid).collect(toSet());

      List<StateExecutionInstance> instancesToBeSkipped =
          executionsWithRollback.stream().map(executionToInstanceMap::get).collect(toList());

      if (nextState instanceof ForkState) {
        ForkState forkState = (ForkState) nextState;
        instancesToBeSkipped.forEach(
            instance -> forkState.getForkStateNames().remove("Rollback-" + instance.getStateName()));
        if (forkState.getForkStateNames().isEmpty()) {
          nextState = sm.getNextState(nextState.getName(), TransitionType.SUCCESS);
        }
      } else if (nextState instanceof EnvLoopState) {
        EnvLoopState envLoopState = (EnvLoopState) nextState;
        instancesToBeSkipped.forEach(instance -> {
          LoopEnvStateParams loopEnvStateParams = (LoopEnvStateParams) instance.getLoopedStateParams();
          loopEnvStateParams.getWorkflowVariables().values().forEach(
              variableValue -> envLoopState.getLoopedValues().remove(variableValue));
        });

        if (envLoopState.getLoopedValues().isEmpty()) {
          nextState = sm.getNextState(nextState.getName(), TransitionType.SUCCESS);
        }
      }

      if (nextState == null) {
        throw new StateMachineIssueException("No stages to rollback", ErrorCode.STATE_MACHINE_ISSUE);
      }
    }

    if (nextState == null) {
      throw new StateMachineIssueException(
          String.format("The advice suggests as next state %s, that is not in state machine: %s.",
              executionEventAdvice.getNextStateName(), executionEventAdvice.getNextChildStateMachineId()),
          ErrorCode.STATE_MACHINE_ISSUE);
    }

    StateExecutionInstance cloned = clone(context.getStateExecutionInstance(), null, nextState);
    if (executionEventAdvice.getNextStateDisplayName() != null) {
      cloned.setDisplayName(executionEventAdvice.getNextStateDisplayName());
    }
    if (executionEventAdvice.getRollbackPhaseName() != null) {
      cloned.setRollbackPhaseName(executionEventAdvice.getRollbackPhaseName());
    }
    return triggerExecution(sm, cloned);
  }

  public static String getContinuePipelineWaitId(String pipelineStageElementId, String pipelineExecutionId) {
    return pipelineStageElementId + "_" + pipelineExecutionId;
  }

  private void updateStateExecutionInstanceForRuntimeInputs(StateExecutionInstance stateExecutionInstance,
      ExecutionStatus status, ExecutionEventAdvice executionEventAdvice) {
    UpdateOperations<StateExecutionInstance> ops =
        wingsPersistence.createUpdateOperations(StateExecutionInstance.class);
    stateExecutionInstance.setStatus(PAUSED);
    stateExecutionInstance.setWaitingForInputs(true);
    stateExecutionInstance.setActionOnTimeout(executionEventAdvice.getActionOnTimeout());
    ops.set(StateExecutionInstanceKeys.status, PAUSED);
    ops.set(StateExecutionInstanceKeys.waitingForInputs, true);
    ops.set(StateExecutionInstanceKeys.actionOnTimeout, executionEventAdvice.getActionOnTimeout());
    ops.set(StateExecutionInstanceKeys.expiryTs, System.currentTimeMillis() + executionEventAdvice.getTimeout());

    Query<StateExecutionInstance> query =
        wingsPersistence.createQuery(StateExecutionInstance.class)
            .filter(StateExecutionInstanceKeys.appId, stateExecutionInstance.getAppId())
            .filter(StateExecutionInstanceKeys.uuid, stateExecutionInstance.getUuid());
    UpdateResults updateResult = wingsPersistence.update(query, ops);
    if (updateResult == null || updateResult.getWriteResult() == null || updateResult.getWriteResult().getN() != 1) {
      log.error("StateExecutionInstance status could not be updated - "
              + "stateExecutionInstance: {},  status: {}",
          stateExecutionInstance.getUuid(), status);
    }

    if (updateResult != null) {
      try {
        usageMetricsEventPublisher.publishDeploymentStepTimeSeriesEvent(
            stateExecutionInstance.getAccountId(), stateExecutionInstance);
      } catch (Exception e) {
        log.error("Failed to publish deployment step event [{}] , [{}]", stateExecutionInstance, e);
      }
    }

    final StateStatusUpdateInfo arg =
        StateStatusUpdateInfo.buildFromStateExecutionInstance(stateExecutionInstance, false);
    statusUpdateSubject.fireInform(StateStatusUpdate::stateExecutionStatusUpdated, arg);
    remoteObserverInformer.sendEvent(
        ReflectionUtils.getMethod(StateStatusUpdate.class, "stateExecutionStatusUpdated", StateStatusUpdateInfo.class),
        StateMachineExecutor.class, arg);
  }

  private void updateStateExecutionInstanceForManualInterventions(StateExecutionInstance stateExecutionInstance,
      ExecutionStatus status, ExecutionEventAdvice executionEventAdvice) {
    Long expiryTs = System.currentTimeMillis() + executionEventAdvice.getTimeout();
    UpdateOperations<StateExecutionInstance> ops =
        wingsPersistence.createUpdateOperations(StateExecutionInstance.class);
    stateExecutionInstance.setStatus(WAITING);
    stateExecutionInstance.setWaitingForManualIntervention(true);
    stateExecutionInstance.setActionAfterManualInterventionTimeout(
        executionEventAdvice.getActionAfterManualInterventionTimeout());
    stateExecutionInstance.setExpiryTs(expiryTs);
    if (executionEventAdvice.getStateParams() != null) {
      ops.set(STATE_PARAMS, executionEventAdvice.getStateParams());
      stateExecutionInstance.setStateParams(executionEventAdvice.getStateParams());
    }
    ops.set(StateExecutionInstanceKeys.status, WAITING);
    ops.set(StateExecutionInstanceKeys.waitingForManualIntervention, true);
    ops.set(StateExecutionInstanceKeys.actionAfterManualInterventionTimeout,
        executionEventAdvice.getActionAfterManualInterventionTimeout());
    ops.set(StateExecutionInstanceKeys.expiryTs, expiryTs);

    Query<StateExecutionInstance> query =
        wingsPersistence.createQuery(StateExecutionInstance.class)
            .filter(StateExecutionInstanceKeys.appId, stateExecutionInstance.getAppId())
            .filter(StateExecutionInstanceKeys.uuid, stateExecutionInstance.getUuid());
    UpdateResults updateResult = wingsPersistence.update(query, ops);
    if (updateResult == null || updateResult.getWriteResult() == null || updateResult.getWriteResult().getN() != 1) {
      log.error("StateExecutionInstance status could not be updated - "
              + "stateExecutionInstance: {},  status: {}",
          stateExecutionInstance.getUuid(), status);
    }

    if (updateResult != null) {
      try {
        usageMetricsEventPublisher.publishDeploymentStepTimeSeriesEvent(
            stateExecutionInstance.getAccountId(), stateExecutionInstance);
      } catch (Exception e) {
        log.error("Failed to publish deployment step event [{}] , [{}]", stateExecutionInstance, e);
      }
    }

    final StateStatusUpdateInfo arg =
        StateStatusUpdateInfo.buildFromStateExecutionInstance(stateExecutionInstance, false);
    statusUpdateSubject.fireInform(StateStatusUpdate::stateExecutionStatusUpdated, arg);
    remoteObserverInformer.sendEvent(
        ReflectionUtils.getMethod(StateStatusUpdate.class, "stateExecutionStatusUpdated", StateStatusUpdateInfo.class),
        StateMachineExecutor.class, arg);
  }

  private boolean checkIfOnDemand(String appId, String executionUuid) {
    return workflowExecutionService.checkIfOnDemand(appId, executionUuid);
  }

  private Map<String, String> getManualInterventionPlaceholderValues(ExecutionContextImpl context) {
    notNullCheck("context.getApp()", context.getApp());
    WorkflowExecution workflowExecution = workflowExecutionService.getExecutionDetails(
        context.getApp().getUuid(), context.getWorkflowExecutionId(), false, false);
    String artifactsMessage =
        workflowNotificationHelper.getArtifactsDetails(context, workflowExecution, WORKFLOW, null).getMessage();

    return notificationMessageResolver.getPlaceholderValues(context, workflowExecution.getTriggeredBy().getName(),
        workflowExecution.getStartTs(), System.currentTimeMillis(), "", "", artifactsMessage, ExecutionStatus.PAUSED,
        AlertType.ManualInterventionNeeded);
  }

  protected void sendManualInterventionNeededNotification(ExecutionContextImpl context, long expiryTs) {
    Application app = context.getApp();
    notNullCheck("app", app);
    Workflow workflow = workflowService.readWorkflow(app.getAppId(), context.getWorkflowId());
    CanaryOrchestrationWorkflow orchestrationWorkflow =
        (CanaryOrchestrationWorkflow) workflow.getOrchestrationWorkflow();
    List<NotificationRule> notificationRules = orchestrationWorkflow.getNotificationRules();
    for (NotificationRule notificationRule : notificationRules) {
      workflowNotificationHelper.renderExpressions(context, notificationRule);
    }

    Map<String, String> placeholderValues =
        workflowNotificationHelper.getPlaceholderValues(context, app, context.getEnv(), PAUSED, null);
    placeholderValues.put("EXPIRES_TS_SECS", String.valueOf(expiryTs / 1000L));
    placeholderValues.put("EXPIRES_DATE", notificationMessageResolver.getFormattedExpiresTime(expiryTs));

    notificationService.sendNotificationAsync(
        InformationNotification.builder()
            .appId(app.getName())
            .accountId(app.getAccountId())
            .notificationTemplateId(MANUAL_INTERVENTION_NEEDED_NOTIFICATION.name())
            .notificationTemplateVariables(placeholderValues)
            .build(),
        notificationRules);
  }

  protected void sendRuntimeInputNeededNotification(ExecutionContextImpl context,
      ExecutionEventAdvice executionEventAdvice, StateExecutionInstance stateExecutionInstance) {
    Application app = context.getApp();
    notNullCheck("app", app);

    Pipeline pipeline = pipelineService.readPipeline(app.getAppId(), context.getWorkflowId(), false);
    notNullCheck("Pipeline does not exist for given Id: " + context.getWorkflowId(), pipeline);

    Map<String, String> placeholderValues =
        getRuntimeInputsPlaceholderValues(context, stateExecutionInstance, executionEventAdvice.getTimeout());

    List<String> userGroupIds = executionEventAdvice.getUserGroupIdsToNotify();
    NotificationRule rule = aNotificationRule().withUserGroupIds(userGroupIds).build();

    notificationService.sendNotificationAsync(InformationNotification.builder()
                                                  .appId(app.getName())
                                                  .accountId(app.getAccountId())
                                                  .notificationTemplateId(NEEDS_RUNTIME_INPUTS.name())
                                                  .notificationTemplateVariables(placeholderValues)
                                                  .build(),
        Collections.singletonList(rule));
  }

  protected void sendRuntimeInputsProvidedNotification(
      ExecutionContextImpl context, List<String> userGroupIds, StateExecutionInstance stateExecutionInstance) {
    Application app = context.getApp();
    notNullCheck("app", app);
    Pipeline pipeline = pipelineService.readPipeline(app.getAppId(), context.getWorkflowId(), false);
    notNullCheck("Pipeline does not exist for given Id: " + context.getWorkflowId(), pipeline);

    Map<String, String> placeholderValues = getInputsProvidedPlaceholderValues(context, stateExecutionInstance);
    NotificationRule rule = aNotificationRule().withUserGroupIds(userGroupIds).build();

    notificationService.sendNotificationAsync(InformationNotification.builder()
                                                  .appId(app.getName())
                                                  .accountId(app.getAccountId())
                                                  .notificationTemplateId(RUNTIME_INPUTS_PROVIDED.name())
                                                  .notificationTemplateVariables(placeholderValues)
                                                  .build(),
        Collections.singletonList(rule));
  }

  protected void sendPipelineNotification(ExecutionContextImpl context, List<String> userGroupIds,
      StateExecutionInstance stateExecutionInstance, ExecutionStatus status) {
    Application app = context.getApp();
    notNullCheck("app", app);
    Pipeline pipeline = pipelineService.readPipeline(app.getAppId(), context.getWorkflowId(), false);
    notNullCheck("Pipeline does not exist for given Id: " + context.getWorkflowId(), pipeline);

    Map<String, String> placeholderValues = getPipelinePlaceholderValues(context, stateExecutionInstance, status);
    NotificationRule rule = aNotificationRule().withUserGroupIds(userGroupIds).build();

    notificationService.sendNotificationAsync(InformationNotification.builder()
                                                  .appId(app.getName())
                                                  .accountId(app.getAccountId())
                                                  .notificationTemplateId(PIPELINE_NOTIFICATION.name())
                                                  .notificationTemplateVariables(placeholderValues)
                                                  .build(),
        Collections.singletonList(rule));
  }

  private Map<String, String> getRuntimeInputsPlaceholderValues(
      ExecutionContextImpl context, StateExecutionInstance stateExecutionInstance, long timeout) {
    notNullCheck("context.getApp()", context.getApp());

    WorkflowExecution pipelineExecution = workflowExecutionService.getExecutionDetails(
        context.getApp().getUuid(), context.getWorkflowExecutionId(), false, false);

    WorkflowNotificationDetails pipelineDetails = workflowNotificationHelper.calculatePipelineDetailsPipelineExecution(
        context.getApp(), pipelineExecution, context);

    final WorkflowNotificationDetails applicationDetails = workflowNotificationHelper.calculateApplicationDetails(
        context.getAccountId(), context.getAppId(), context.getApp());

    Map<String, String> placeholderValues = notificationMessageResolver.getPlaceholderValues(context,
        pipelineExecution.getTriggeredBy().getName(), pipelineExecution.getCreatedAt(), pipelineExecution.getStartTs(),
        String.valueOf(timeout), "", "", ExecutionStatus.PAUSED, AlertType.ManualInterventionNeeded);
    String pipelineStepMessage = format("*Pipeline Step:* %s", stateExecutionInstance.getStateName());

    placeholderValues.put(PIPELINE_STEP_NAME, stateExecutionInstance.getStateName());
    placeholderValues.put(PIPELINE_STEP, pipelineStepMessage);
    placeholderValues.put(PIPELINE_NAME, pipelineDetails.getName());
    placeholderValues.put(PIPELINE_URL, pipelineDetails.getUrl());
    placeholderValues.put(PIPELINE, pipelineDetails.getMessage());
    placeholderValues.put(APPLICATION, applicationDetails.getMessage());
    placeholderValues.put(APPLICATION_NAME, applicationDetails.getName());
    placeholderValues.put(APPLICATION_URL, applicationDetails.getUrl());

    return placeholderValues;
  }

  private Map<String, String> getInputsProvidedPlaceholderValues(
      ExecutionContextImpl context, StateExecutionInstance stateExecutionInstance) {
    notNullCheck("context.getApp()", context.getApp());

    WorkflowExecution pipelineExecution = workflowExecutionService.getExecutionDetails(
        context.getApp().getUuid(), context.getWorkflowExecutionId(), false, false);

    WorkflowNotificationDetails pipelineDetails = workflowNotificationHelper.calculatePipelineDetailsPipelineExecution(
        context.getApp(), pipelineExecution, context);

    final WorkflowNotificationDetails applicationDetails = workflowNotificationHelper.calculateApplicationDetails(
        context.getAccountId(), context.getAppId(), context.getApp());

    Map<String, String> placeholderValues = notificationMessageResolver.getPlaceholderValues(context,
        pipelineExecution.getTriggeredBy().getName(), pipelineExecution.getCreatedAt(), System.currentTimeMillis(), "",
        "", "", ExecutionStatus.RESUMED, AlertType.ManualInterventionNeeded);
    String pipelineStepMessage = format("*Pipeline Step:* %s", stateExecutionInstance.getStateName());

    placeholderValues.put(PIPELINE_STEP_NAME, stateExecutionInstance.getStateName());
    placeholderValues.put(PIPELINE_STEP, pipelineStepMessage);
    placeholderValues.put(PIPELINE_NAME, pipelineDetails.getName());
    placeholderValues.put(PIPELINE_URL, pipelineDetails.getUrl());
    placeholderValues.put(PIPELINE, pipelineDetails.getMessage());
    placeholderValues.put(APPLICATION, applicationDetails.getMessage());
    placeholderValues.put(APPLICATION_NAME, applicationDetails.getName());
    placeholderValues.put(APPLICATION_URL, applicationDetails.getUrl());

    return placeholderValues;
  }

  private Map<String, String> getPipelinePlaceholderValues(
      ExecutionContextImpl context, StateExecutionInstance stateExecutionInstance, ExecutionStatus status) {
    notNullCheck("context.getApp()", context.getApp());

    WorkflowExecution pipelineExecution = workflowExecutionService.getExecutionDetails(
        context.getApp().getUuid(), context.getWorkflowExecutionId(), false, false);

    WorkflowNotificationDetails pipelineDetails = workflowNotificationHelper.calculatePipelineDetailsPipelineExecution(
        context.getApp(), pipelineExecution, context);

    final WorkflowNotificationDetails applicationDetails = workflowNotificationHelper.calculateApplicationDetails(
        context.getAccountId(), context.getAppId(), context.getApp());

    Map<String, String> placeholderValues = notificationMessageResolver.getPlaceholderValues(context,
        pipelineExecution.getTriggeredBy().getName(), pipelineExecution.getCreatedAt(), System.currentTimeMillis(), "",
        "", "", status, AlertType.ManualInterventionNeeded);
    String pipelineStepMessage = format("*Pipeline Step:* %s", stateExecutionInstance.getStateName());

    placeholderValues.put(PIPELINE_STEP_NAME, stateExecutionInstance.getStateName());
    placeholderValues.put(PIPELINE_STEP, pipelineStepMessage);
    placeholderValues.put(PIPELINE_NAME, pipelineDetails.getName());
    placeholderValues.put(PIPELINE_URL, pipelineDetails.getUrl());
    placeholderValues.put(PIPELINE, pipelineDetails.getMessage());
    placeholderValues.put(APPLICATION, applicationDetails.getMessage());
    placeholderValues.put(APPLICATION_NAME, applicationDetails.getName());
    placeholderValues.put(APPLICATION_URL, applicationDetails.getUrl());

    return placeholderValues;
  }

  private void openAnAlert(ExecutionContextImpl context, AlertData alertData) {
    try {
      Application app = context.getApp();
      notNullCheck("app", app);
      Environment environment = context.getEnv();

      alertService.openAlert(app.getAccountId(), app.getUuid(), ManualInterventionNeeded, alertData);
    } catch (Exception e) {
      log.warn("Failed to open ManualInterventionNeeded alarm for executionId {} and name {}",
          context.getWorkflowExecutionId(), context.getWorkflowExecutionName(), e);
    }
  }

  private StateExecutionInstance executionEventAdviceTransition(
      ExecutionContextImpl context, ExecutionEventAdvice executionEventAdvice) {
    StateMachine sm = context.getStateMachine();
    State nextState =
        sm.getState(executionEventAdvice.getNextChildStateMachineId(), executionEventAdvice.getNextStateName());

    if (nextState == null) {
      throw new StateMachineIssueException(
          String.format("The advice suggests as next state %s, that is not in state machine: %s.",
              executionEventAdvice.getNextStateName(), executionEventAdvice.getNextChildStateMachineId()),
          ErrorCode.STATE_MACHINE_ISSUE);
    }

    StateExecutionInstance cloned =
        clone(context.getStateExecutionInstance(), executionEventAdvice.getNextChildStateMachineId(), nextState);
    if (executionEventAdvice.getNextStateDisplayName() != null) {
      cloned.setDisplayName(executionEventAdvice.getNextStateDisplayName());
    }
    if (executionEventAdvice.getRollbackPhaseName() != null) {
      cloned.setRollbackPhaseName(executionEventAdvice.getRollbackPhaseName());
    }
    return triggerExecution(sm, cloned);
  }

  /**
   * Handle execute response exception state execution instance.
   *
   * @param context   the context
   * @param exception the exception
   * @return the state execution instance
   */
  StateExecutionInstance handleExecuteResponseException(ExecutionContextImpl context, WingsException exception) {
    StateExecutionInstance stateExecutionInstance = null;
    State currentState = null;
    try (AutoLogContext ignore = context.autoLogContext()) {
      stateExecutionInstance = context.getStateExecutionInstance();
      StateMachine sm = context.getStateMachine();
      currentState =
          sm.getState(stateExecutionInstance.getChildStateMachineId(), stateExecutionInstance.getStateName());
      addContext(context, exception);
      ExceptionLogger.logProcessedMessages(exception, MANAGER, log);
    } catch (RuntimeException ex) {
      WingsException wingsException = new WingsException(ex);
      addContext(context, wingsException);
      log.error("Error when processing exception", wingsException);
    }

    if (stateExecutionInstance != null) {
      updateStateExecutionData(stateExecutionInstance, null, FAILED, ExceptionUtils.getMessage(exception), null, null,
          stateExecutionInstance.getExpiryTs());
    }

    try (AutoLogContext ignore = context.autoLogContext()) {
      ExecutionEventAdvice executionEventAdvice =
          invokeAdvisors(ExecutionEvent.builder()
                             .failureTypes(ExceptionUtils.getFailureTypes(exception))
                             .context(context)
                             .state(currentState)
                             .build());
      if (executionEventAdvice != null && !executionEventAdvice.isSkipState()) {
        return handleExecutionEventAdvice(context, stateExecutionInstance, FAILED, executionEventAdvice);
      }
    } catch (RuntimeException ex) {
      log.error("Error when trying to obtain the advice ", ex);
    }

    try (AutoLogContext ignore = context.autoLogContext()) {
      return failedTransition(context, exception);
    } catch (RuntimeException ex) {
      log.error("Error in transitioning to failure state", ex);
    }
    return null;
  }

  private boolean isPipelineRollback(StateExecutionInstance stateExecutionInstance, StateMachine sm) {
    State state = sm.getState(null, stateExecutionInstance.getStateName());
    if (state == null) {
      return false;
    }
    return POSSIBLE_ROLLBACK_STATE_TYPES.contains(stateExecutionInstance.getStateType()) && state.isRollback()
        && state.getParentId() == null;
  }

  private StateExecutionInstance successTransition(ExecutionContextImpl context) {
    StateExecutionInstance stateExecutionInstance = context.getStateExecutionInstance();
    StateMachine sm = context.getStateMachine();

    State nextState =
        sm.getSuccessTransition(stateExecutionInstance.getChildStateMachineId(), stateExecutionInstance.getStateName());
    if (nextState == null) {
      log.info(
          "nextSuccessState is null.. ending execution  - currentState : {}", stateExecutionInstance.getStateName());

      log.info("State Machine execution ended for the stateMachine: {}", sm.getName());

      if (isPipelineRollback(stateExecutionInstance, sm)) {
        endTransition(context, stateExecutionInstance, FAILED, null);
      }

      endTransition(context, stateExecutionInstance, SUCCESS, null);
    } else {
      StateExecutionInstance cloned = clone(stateExecutionInstance, nextState);
      return triggerExecution(sm, cloned);
    }

    return null;
  }

  private StateExecutionInstance failedTransition(ExecutionContextImpl context, Exception exception) {
    StateExecutionInstance stateExecutionInstance = context.getStateExecutionInstance();
    StateMachine sm = context.getStateMachine();

    State nextState =
        sm.getFailureTransition(stateExecutionInstance.getChildStateMachineId(), stateExecutionInstance.getStateName());
    if (nextState == null) {
      log.info("nextFailureState is null.. for the currentState : {}, stateExecutionInstanceId: {}",
          stateExecutionInstance.getStateName(), stateExecutionInstance.getUuid());

      ErrorStrategy errorStrategy = context.getErrorStrategy();
      if (errorStrategy == null || errorStrategy == ErrorStrategy.FAIL) {
        log.info("Ending execution  - currentState : {}, stateExecutionInstanceId: {}",
            stateExecutionInstance.getStateName(), stateExecutionInstance.getUuid());
        endTransition(context, stateExecutionInstance, FAILED, exception);
      } else if (errorStrategy == ErrorStrategy.PAUSE) {
        log.info("Pausing execution  - currentState : {}, stateExecutionInstanceId: {}",
            stateExecutionInstance.getStateName(), stateExecutionInstance.getUuid());
        updateStatusPauseAndWaiting(stateExecutionInstance, WAITING, Lists.newArrayList(FAILED), null);
      } else {
        // TODO: handle more strategy
        log.info("Unhandled error strategy for the state: {}, stateExecutionInstanceId: {}, errorStrategy: {}",
            stateExecutionInstance.getStateName(), stateExecutionInstance.getUuid(), errorStrategy);
      }
    } else {
      StateExecutionInstance cloned = clone(stateExecutionInstance, nextState);
      return triggerExecution(sm, cloned);
    }
    return null;
  }

  private void endTransition(ExecutionContextImpl context, StateExecutionInstance stateExecutionInstance,
      ExecutionStatus status, Exception exception) {
    if (stateExecutionInstance.getNotifyId() != null) {
      notify(stateExecutionInstance, status);
    } else {
      executeCallback(context, stateExecutionInstance, status, exception);
    }
  }

  public void executeCallback(ExecutionContextImpl context, StateExecutionInstance stateExecutionInstance,
      ExecutionStatus status, Exception exception) {
    StateMachineExecutionCallback callback = stateExecutionInstance.getCallback();
    if (callback == null) {
      return;
    }

    injector.injectMembers(callback);
    callback.callback(context, status, exception);
  }

  private void discontinueExecution(ExecutionContextImpl context, ExecutionInterruptType interruptType) {
    StateExecutionInstance stateExecutionInstance = context.getStateExecutionInstance();
    ExecutionStatus finalStatus = getFinalStatus(interruptType);

    if (stateExecutionInstance.getStatus() == DISCONTINUING) {
      boolean terminated = terminateAndTransition(
          context, stateExecutionInstance, finalStatus, "Stuck Discontinuing Instance..Terminating");
      if (!terminated) {
        throw new IllegalStateException("State Execution Instance Stuck in Discontinuing State");
      }
      return;
    }

    List<ExecutionStatus> executionStatuses = asList(NEW, QUEUED, STARTING, RUNNING, PAUSED, WAITING);
    boolean updated = updateStatus(stateExecutionInstance, DISCONTINUING, executionStatuses, null,
        ops -> ops.set("expiryTs", System.currentTimeMillis() + ABORT_EXPIRY_BUFFER_MILLIS));
    if (!updated) {
      throw new WingsException(STATE_NOT_FOR_TYPE)
          .addParam("displayName", stateExecutionInstance.getDisplayName())
          .addParam("type", DISCONTINUING.name())
          .addParam(StateExecutionInstanceKeys.status, stateExecutionInstance.getStatus().name())
          .addParam("statuses", executionStatuses);
    }

    discontinueMarkedInstance(context, stateExecutionInstance, finalStatus);
  }

  private ExecutionStatus getFinalStatus(ExecutionInterruptType interruptType) {
    switch (interruptType) {
      case MARK_EXPIRED:
        return EXPIRED;
      case ABORT:
        return ABORTED;
      default:
        unhandled(interruptType);
    }
    return ERROR;
  }

  private void discontinueMarkedInstance(
      ExecutionContextImpl context, StateExecutionInstance stateExecutionInstance, ExecutionStatus finalStatus) {
    boolean updated = false;
    StateMachine sm = context.getStateMachine();
    try {
      log.info(
          "[AbortInstance] Aborting StateExecution Instance with Id : {}", stateExecutionInstance.getExecutionUuid());
      State currentState =
          sm.getState(stateExecutionInstance.getChildStateMachineId(), stateExecutionInstance.getStateName());
      notNullCheck("currentState", currentState);
      injector.injectMembers(currentState);

      Set<String> delegateTaskIds = new HashSet();
      if (isNotBlank(stateExecutionInstance.getDelegateTaskId())) {
        delegateTaskIds.add(stateExecutionInstance.getDelegateTaskId());
      }

      if (isNotEmpty(stateExecutionInstance.getDelegateTasksDetails())) {
        delegateTaskIds.addAll(stateExecutionInstance.getDelegateTasksDetails()
                                   .stream()
                                   .map(DelegateTaskDetails::getDelegateTaskId)
                                   .collect(Collectors.toSet()));
      }

      StringBuilder errorMsgBuilder = new StringBuilder();
      log.info("[AbortInstance] Found {} Delegate Task Id for StateExecutionInstance {}", delegateTaskIds.size(),
          stateExecutionInstance.getUuid());
      for (String delegateTaskId : delegateTaskIds) {
        notNullCheck("context.getApp()", context.getApp());
        if (finalStatus == ABORTED) {
          try {
            delegateService.abortTaskV2(context.getApp().getAccountId(), delegateTaskId);
          } catch (Exception e) {
            log.error(
                "[AbortInstance] Error in ABORTING WorkflowExecution {}. Error in aborting delegate task : {}. Reason : {}",
                stateExecutionInstance.getExecutionUuid(), delegateTaskId, e.getMessage());
          }
        } else {
          try {
            String errorMsg = delegateService.expireTaskV2(context.getApp().getAccountId(), delegateTaskId);
            if (isNotBlank(errorMsg)) {
              errorMsgBuilder.append(errorMsg);
            }
          } catch (Exception e) {
            log.error(
                "[AbortInstance] Error in ABORTING WorkflowExecution {}. Error in expiring delegate task : {}. Reason : {}",
                stateExecutionInstance.getExecutionUuid(), delegateTaskId, e.getMessage());
          }
        }
      }
      log.info(
          "[AbortInstance] All DelegateTaskHandled for StateExecutionInstance {}", stateExecutionInstance.getUuid());

      String errorMessage =
          (context.getStateExecutionData() != null && context.getStateExecutionData().getErrorMsg() != null
              && isBlank(errorMsgBuilder.toString()))
          ? context.getStateExecutionData().getErrorMsg()
          : errorMsgBuilder.toString();

      if (stateExecutionInstance.getStateParams() != null) {
        mapObject(stateExecutionInstance.getStateParams(), currentState, context.getAccountId());
      }

      currentState.handleAbortEvent(context);
      if (!(isStepSupportingTimeout(stateExecutionInstance)
              && featureFlagService.isEnabled(TIMEOUT_FAILURE_SUPPORT, context.getAccountId())
              && finalStatus == EXPIRED)) {
        updated = terminateAndTransition(context, stateExecutionInstance, finalStatus, errorMessage);
        invokeAdvisors(ExecutionEvent.builder()
                           .failureTypes(EnumSet.<FailureType>of(FailureType.EXPIRED))
                           .context(context)
                           .state(currentState)
                           .build());
      }

    } catch (Exception e) {
      log.error("[AbortInstance] Error in discontinuing", e);
    }
    if (!updated) {
      throw new WingsException(ErrorCode.STATE_DISCONTINUE_FAILED)
          .addParam("displayName", stateExecutionInstance.getDisplayName());
    }
  }

  private boolean isStepSupportingTimeout(StateExecutionInstance stateExecutionInstance) {
    return StateType.SHELL_SCRIPT.name().equals(stateExecutionInstance.getStateType())
        || StateType.HTTP.name().equals(stateExecutionInstance.getStateType());
  }

  private boolean terminateAndTransition(ExecutionContextImpl context, StateExecutionInstance stateExecutionInstance,
      ExecutionStatus finalStatus, String errorMessage) {
    boolean updated = false;
    try {
      updated = updateStateExecutionData(
          stateExecutionInstance, null, finalStatus, errorMessage, singletonList(DISCONTINUING), null, null, null);
      log.info("[AbortInstance] UpdateStateExecutionData Finished with response :{} ", updated);
      endTransition(context, stateExecutionInstance, finalStatus, null);
    } catch (Exception ex) {
      log.error("[AbortInstance] Error Occurred while UpdateStateExecutionData", ex);
    }
    return updated;
  }

  private void notify(StateExecutionInstance stateExecutionInstance, ExecutionStatus status) {
    ElementNotifyResponseDataBuilder elementNotifyResponseDataBuilder =
        ElementNotifyResponseData.builder().executionStatus(status);
    if (isNotEmpty(stateExecutionInstance.getNotifyElements())) {
      elementNotifyResponseDataBuilder.contextElements(stateExecutionInstance.getNotifyElements());
    }
    waitNotifyEngine.doneWith(stateExecutionInstance.getNotifyId(), elementNotifyResponseDataBuilder.build());
  }

  private void handleSpawningStateExecutionInstances(
      StateMachine sm, StateExecutionInstance stateExecutionInstance, ExecutionResponse executionResponse) {
    if (isEmpty(executionResponse.getStateExecutionInstances())) {
      return;
    }

    for (StateExecutionInstance childStateExecutionInstance : executionResponse.getStateExecutionInstances()) {
      childStateExecutionInstance.setUuid(null);
      childStateExecutionInstance.setStateParams(null);
      childStateExecutionInstance.setParentInstanceId(stateExecutionInstance.getUuid());
      childStateExecutionInstance.setAppId(stateExecutionInstance.getAppId());
      childStateExecutionInstance.setNotifyElements(null);

      if (childStateExecutionInstance.getStateName() == null
          && childStateExecutionInstance.getChildStateMachineId() != null) {
        if (sm.getChildStateMachines().get(childStateExecutionInstance.getChildStateMachineId()) == null) {
          notify(childStateExecutionInstance, SUCCESS);
          return;
        }
        String initialStateName =
            sm.getChildStateMachines().get(childStateExecutionInstance.getChildStateMachineId()).getInitialStateName();
        childStateExecutionInstance.setDisplayName(initialStateName);
        childStateExecutionInstance.setStateName(initialStateName);
      }

      if (childStateExecutionInstance.getChildStateMachineId() != null) {
        StateMachine stateMachine =
            sm.getChildStateMachines().get(childStateExecutionInstance.getChildStateMachineId());
        if (stateMachine != null) {
          State state = stateMachine.getStatesMap().get(childStateExecutionInstance.getStateName());
          childStateExecutionInstance.setSelectionLogsTrackingForTasksEnabled(
              state.isSelectionLogsTrackingForTasksEnabled());
        }
      }

      triggerExecution(sm, childStateExecutionInstance);
    }
  }

  /**
   * @param stateExecutionInstance
   * @param childStateMachineId
   * @param nextState              @return
   */
  private StateExecutionInstance clone(
      StateExecutionInstance stateExecutionInstance, String childStateMachineId, State nextState) {
    StateExecutionInstance cloned = clone(stateExecutionInstance, nextState);
    cloned.setChildStateMachineId(childStateMachineId);
    return cloned;
  }

  /**
   * @param stateExecutionInstance
   * @param nextState              @return
   */
  private StateExecutionInstance clone(StateExecutionInstance stateExecutionInstance, State nextState) {
    StateExecutionInstance cloned = kryoSerializer.clone(stateExecutionInstance);
    if (featureFlagService.isEnabled(SPG_FIX_APPROVAL_WAITING_FOR_INPUTS, stateExecutionInstance.getAccountId())) {
      cloned.setWaitingForInputs(false);
      cloned.setActionOnTimeout(null);
    }

    cloned.setContinued(false);
    cloned.setInterruptHistory(null);
    cloned.setStateExecutionDataHistory(null);
    cloned.setDedicatedInterruptCount(null);
    cloned.setUuid(null);
    cloned.setStateParams(null);
    cloned.setDisplayName(nextState.getName());
    cloned.setStateName(nextState.getName());
    cloned.setPrevInstanceId(stateExecutionInstance.getUuid());
    cloned.setDelegateTasksDetails(null);
    cloned.setSelectionLogsTrackingForTasksEnabled(nextState.isSelectionLogsTrackingForTasksEnabled());
    cloned.setContextTransition(false);
    cloned.setStatus(NEW);
    cloned.setStartTs(null);
    cloned.setEndTs(null);
    cloned.setCreatedAt(0);
    cloned.setLastUpdatedAt(0);
    cloned.setHasInspection(false);
    cloned.setExpiryTs(Long.MAX_VALUE);
    cloned.setStateTimeout(null);
    cloned.setDelegateTaskId(null);
    return cloned;
  }

  private boolean updateStartStatus(StateExecutionInstance stateExecutionInstance, ExecutionStatus status,
      Collection<ExecutionStatus> existingExecutionStatus, ExecutionInterrupt reason) {
    stateExecutionInstance.setStartTs(System.currentTimeMillis());
    Long timeout = stateExecutionInstance.getStateTimeout() != null && stateExecutionInstance.getStateTimeout() > 0
        ? System.currentTimeMillis() + stateExecutionInstance.getStateTimeout()
        : Long.MAX_VALUE;
    stateExecutionInstance.setExpiryTs(timeout);
    return updateStatus(stateExecutionInstance, status, existingExecutionStatus, reason, ops -> {
      ops.set(StateExecutionInstanceKeys.startTs, stateExecutionInstance.getStartTs())
          .set(StateExecutionInstanceKeys.expiryTs, timeout);
    });
  }

  private boolean updateEndStatus(StateExecutionInstance stateExecutionInstance, ExecutionStatus status,
      Collection<ExecutionStatus> existingExecutionStatus) {
    stateExecutionInstance.setEndTs(System.currentTimeMillis());
    return updateStatus(stateExecutionInstance, status, existingExecutionStatus, null,
        ops -> { ops.set("endTs", stateExecutionInstance.getEndTs()); });
  }

  private boolean updateStatus(StateExecutionInstance stateExecutionInstance, ExecutionStatus status,
      Collection<ExecutionStatus> existingExecutionStatus, ExecutionInterrupt reason) {
    return updateStatus(stateExecutionInstance, status, existingExecutionStatus, reason, null);
  }

  private boolean updateStatusPauseAndWaiting(StateExecutionInstance stateExecutionInstance, ExecutionStatus status,
      Collection<ExecutionStatus> existingExecutionStatus, ExecutionInterrupt reason) {
    log.info(
        "[TimeOut Op]: Updating expiryTs to Long.MAX_VALUE on PAUSE_ALL interrupt or pause error strategy on failure for stateExecutionInstance id: {}, name: {}",
        stateExecutionInstance.getUuid(), stateExecutionInstance.getDisplayName());
    return updateStatus(stateExecutionInstance, status, existingExecutionStatus, reason,
        ops -> ops.set(StateExecutionInstanceKeys.expiryTs, Long.MAX_VALUE));
  }

  private boolean updateStatus(StateExecutionInstance stateExecutionInstance, ExecutionStatus status,
      Collection<ExecutionStatus> existingExecutionStatus, ExecutionInterrupt reason,
      Consumer<UpdateOperations<StateExecutionInstance>> more) {
    UpdateOperations<StateExecutionInstance> ops =
        wingsPersistence.createUpdateOperations(StateExecutionInstance.class);

    statusUpdateOperation(stateExecutionInstance, status, ops);

    if (more != null) {
      more.accept(ops);
    }

    if (reason != null) {
      if (stateExecutionInstance.getUuid().equals(reason.getStateExecutionInstanceId())) {
        log.error(format("The reason execution interrupt with type %s is already assigned to this execution instance.",
                      reason.getExecutionInterruptType().name()),
            new Exception(""));
      } else {
        ops.addToSet("interruptHistory",
            ExecutionInterruptEffect.builder().interruptId(reason.getUuid()).tookEffectAt(new Date()).build());
      }
    }

    Query<StateExecutionInstance> query =
        wingsPersistence.createQuery(StateExecutionInstance.class)
            .filter(StateExecutionInstanceKeys.appId, stateExecutionInstance.getAppId())
            .filter(StateExecutionInstanceKeys.uuid, stateExecutionInstance.getUuid())
            .field(StateExecutionInstanceKeys.status)
            .in(existingExecutionStatus);
    UpdateResults updateResult = wingsPersistence.update(query, ops);
    if (updateResult == null || updateResult.getWriteResult() == null || updateResult.getWriteResult().getN() != 1) {
      log.error("StateExecutionInstance status could not be updated - "
              + "stateExecutionInstance: {},  status: {}, existingExecutionStatus: {}, ",
          stateExecutionInstance.getUuid(), status, existingExecutionStatus);
      return false;
    }
    try {
      usageMetricsEventPublisher.publishDeploymentStepTimeSeriesEvent(
          stateExecutionInstance.getAccountId(), stateExecutionInstance);
    } catch (Exception e) {
      log.error("Failed to publish deployment step event [{}] , [{}]", stateExecutionInstance, e);
    }

    final StateStatusUpdateInfo arg = StateStatusUpdateInfo.buildFromStateExecutionInstance(stateExecutionInstance,
        reason != null
            && (RESUME_ALL == reason.getExecutionInterruptType()
                || CONTINUE_PIPELINE_STAGE == reason.getExecutionInterruptType()));
    statusUpdateSubject.fireInform(StateStatusUpdate::stateExecutionStatusUpdated, arg);
    remoteObserverInformer.sendEvent(
        ReflectionUtils.getMethod(StateStatusUpdate.class, "stateExecutionStatusUpdated", StateStatusUpdateInfo.class),
        StateMachineExecutor.class, arg);
    return true;
  }

  private void statusUpdateOperation(StateExecutionInstance stateExecutionInstance, ExecutionStatus status,
      UpdateOperations<StateExecutionInstance> ops) {
    stateExecutionInstance.setStatus(status);
    ops.set(StateExecutionInstanceKeys.status, stateExecutionInstance.getStatus());

    stateExecutionInstance.setDedicatedInterruptCount(
        workflowExecutionService.getExecutionInterruptCount(stateExecutionInstance.getUuid()));
    ops.set(StateExecutionInstanceKeys.dedicatedInterruptCount, stateExecutionInstance.getDedicatedInterruptCount());

    if (isFinalStatus(status)) {
      stateExecutionInstance.setEndTs(System.currentTimeMillis());
      ops.set("endTs", stateExecutionInstance.getEndTs());
    }
  }

  private boolean updateStateExecutionData(StateExecutionInstance stateExecutionInstance,
      StateExecutionData stateExecutionData, ExecutionStatus status, String errorMsg, List<ContextElement> elements,
      List<ContextElement> notifyElements, @NotNull Long expiryTs) {
    log.info("[TimeOut Op]: Updating expiryTs to: {} on {} status set for stateExecutionInstance id: {}, name: {}",
        expiryTs, status, stateExecutionInstance.getUuid(), stateExecutionInstance.getDisplayName());
    stateExecutionInstance.setExpiryTs(expiryTs);
    return updateStateExecutionData(
        stateExecutionInstance, stateExecutionData, status, errorMsg, null, elements, notifyElements, null);
  }

  private boolean updateStateExecutionData(StateExecutionInstance stateExecutionInstance,
      StateExecutionData stateExecutionData, ExecutionStatus status, String errorMsg, List<ContextElement> elements,
      List<ContextElement> notifyElements, String delegateTaskId, @NotNull Long expiryTs) {
    log.info("[TimeOut Op]: Updating expiryTs to: {} on {} status set for stateExecutionInstance id: {}, name: {}",
        expiryTs, status, stateExecutionInstance.getUuid(), stateExecutionInstance.getDisplayName());
    stateExecutionInstance.setExpiryTs(expiryTs);
    return updateStateExecutionData(
        stateExecutionInstance, stateExecutionData, status, errorMsg, null, elements, notifyElements, delegateTaskId);
  }

  private boolean updateStateExecutionData(StateExecutionInstance stateExecutionInstance,
      StateExecutionData stateExecutionData, ExecutionStatus status, String errorMsg,
      Collection<ExecutionStatus> runningStatusLists, List<ContextElement> contextElements,
      List<ContextElement> notifyElements, String delegateTaskId, @NotNull Long expiryTs) {
    log.info("[TimeOut Op]: Updating expiryTs to {} on {} status set for stateExecutionInstance id: {}, name: {}",
        expiryTs, status, stateExecutionInstance.getUuid(), stateExecutionInstance.getDisplayName());
    stateExecutionInstance.setExpiryTs(expiryTs);
    return updateStateExecutionData(stateExecutionInstance, stateExecutionData, status, errorMsg, runningStatusLists,
        contextElements, notifyElements, delegateTaskId);
  }

  private boolean updateStateExecutionData(StateExecutionInstance stateExecutionInstance,
      StateExecutionData stateExecutionData, ExecutionStatus status, String errorMsg,
      Collection<ExecutionStatus> runningStatusLists, List<ContextElement> contextElements,
      List<ContextElement> notifyElements, String delegateTaskId) {
    Map<String, StateExecutionData> stateExecutionMap = stateExecutionInstance.getStateExecutionMap();
    if (stateExecutionMap == null) {
      stateExecutionMap = new HashMap<>();
      stateExecutionInstance.setStateExecutionMap(stateExecutionMap);
    }

    if (stateExecutionData == null) {
      stateExecutionData = stateExecutionMap.get(stateExecutionInstance.getDisplayName());
      if (stateExecutionData == null) {
        stateExecutionData = new StateExecutionData();
      }
    }

    stateExecutionData.setStateName(stateExecutionInstance.getDisplayName());
    stateExecutionData.setStateType(stateExecutionInstance.getStateType());
    stateExecutionMap.put(stateExecutionInstance.getDisplayName(), stateExecutionData);

    UpdateOperations<StateExecutionInstance> ops =
        wingsPersistence.createUpdateOperations(StateExecutionInstance.class);

    statusUpdateOperation(stateExecutionInstance, status, ops);

    if (isNotEmpty(contextElements)) {
      contextElements.forEach(contextElement -> stateExecutionInstance.getContextElements().push(contextElement));
      ops.set("contextElements", stateExecutionInstance.getContextElements());
    }

    if (isNotEmpty(notifyElements)) {
      if (stateExecutionInstance.getNotifyElements() == null) {
        stateExecutionInstance.setNotifyElements(new ArrayList<>());
      }
      stateExecutionInstance.getNotifyElements().addAll(notifyElements);
      ops.set("notifyElements", stateExecutionInstance.getNotifyElements());
    }

    stateExecutionData.setElement(stateExecutionInstance.getContextElement());
    stateExecutionData.setStartTs(stateExecutionInstance.getStartTs());
    if (stateExecutionInstance.getEndTs() != null) {
      stateExecutionData.setEndTs(stateExecutionInstance.getEndTs());
    }
    stateExecutionData.setStatus(stateExecutionInstance.getStatus());
    if (errorMsg != null) {
      stateExecutionData.setErrorMsg(errorMsg);
    }
    stateExecutionData.setStateParams(stateExecutionInstance.getStateParams());

    if (isEmpty(runningStatusLists)) {
      runningStatusLists = activeStatuses();
    }

    if (isNotBlank(delegateTaskId)) {
      ops.set(StateExecutionInstanceKeys.delegateTaskId, delegateTaskId);
    }

    Query<StateExecutionInstance> query =
        wingsPersistence.createQuery(StateExecutionInstance.class)
            .filter(StateExecutionInstanceKeys.appId, stateExecutionInstance.getAppId())
            .filter(ID_KEY, stateExecutionInstance.getUuid())
            .field(StateExecutionInstanceKeys.status)
            .in(runningStatusLists);

    ops.set("stateExecutionMap", stateExecutionInstance.getStateExecutionMap());
    ops.set("expiryTs", stateExecutionInstance.getExpiryTs());

    UpdateResults updateResult = wingsPersistence.update(query, ops);
    if (updateResult == null || updateResult.getWriteResult() == null || updateResult.getWriteResult().getN() != 1) {
      log.warn("StateExecutionInstance status could not be updated -"
              + " stateExecutionInstance: {}, stateExecutionData: {}, status: {}, errorMsg: {}, ",
          stateExecutionInstance.getUuid(), stateExecutionData.toString(), status, errorMsg);

      return false;
    }

    try {
      usageMetricsEventPublisher.publishDeploymentStepTimeSeriesEvent(
          stateExecutionInstance.getAccountId(), stateExecutionInstance);
    } catch (Exception e) {
      log.error("Failed to publish deployment step event [{}] , [{}]", stateExecutionInstance, e);
    }

    final StateStatusUpdateInfo arg = StateStatusUpdateInfo.buildFromStateExecutionInstance(stateExecutionInstance,
        isApprovalResumed(stateExecutionInstance.getStateType(), stateExecutionInstance.getStatus()));
    statusUpdateSubject.fireInform(StateStatusUpdate::stateExecutionStatusUpdated, arg);
    remoteObserverInformer.sendEvent(
        ReflectionUtils.getMethod(StateStatusUpdate.class, "stateExecutionStatusUpdated", StateStatusUpdateInfo.class),
        StateMachineExecutor.class, arg);
    log.info("State Execution data updated successfully");
    return true;
  }

  private boolean isApprovalResumed(String stateType, ExecutionStatus status) {
    return StateType.APPROVAL.name().equals(stateType) && SUCCESS == status;
  }

  /**
   * Resumes execution of a StateMachineInstance.
   *
   * @param appId                    the app id
   * @param stateExecutionInstanceId stateMachineInstance to resume.
   * @param response                 map of responses from state machine instances this state was waiting on.
   */
  public void resume(String appId, String executionUuid, String stateExecutionInstanceId,
      Map<String, ResponseData> response, boolean asyncError) {
    log.info(DEBUG_LINE + "resuming stateExecution for stateExecutionId {}", stateExecutionInstanceId);
    StateExecutionInstance stateExecutionInstance =
        getStateExecutionInstance(appId, executionUuid, stateExecutionInstanceId);
    StateMachine sm = stateExecutionService.obtainStateMachine(stateExecutionInstance);
    State currentState =
        sm.getState(stateExecutionInstance.getChildStateMachineId(), stateExecutionInstance.getStateName());
    if (currentState == null && stateExecutionInstance.isParentLoopedState()) {
      currentState =
          stateExecutionInstance.getLoopedStateParams().getEnvStateInstanceFromParams(workflowService, appId);
      sm.addState(currentState);
    }
    injector.injectMembers(currentState);

    while (stateExecutionInstance.getStatus() == NEW || stateExecutionInstance.getStatus() == QUEUED
        || stateExecutionInstance.getStatus() == STARTING) {
      log.warn("stateExecutionInstance: {} status {} is not in RUNNING state yet", stateExecutionInstance.getUuid(),
          stateExecutionInstance.getStatus());
      // TODO - more elegant way
      quietSleep(Duration.ofMillis(500));
      stateExecutionInstance = getStateExecutionInstance(appId, executionUuid, stateExecutionInstanceId);
    }
    if (stateExecutionInstance.getStatus() != RUNNING && stateExecutionInstance.getStatus() != PAUSED
        && stateExecutionInstance.getStatus() != DISCONTINUING) {
      log.warn("stateExecutionInstance: {} status {} is no longer in RUNNING/PAUSED/DISCONTINUING state",
          stateExecutionInstance.getUuid(), stateExecutionInstance.getStatus());
      return;
    }
    ExecutionContextImpl context = new ExecutionContextImpl(stateExecutionInstance, sm, injector);
    injector.injectMembers(context);
    log.info(DEBUG_LINE + "kick off stateExecution from resume for state {}", currentState);
    // TODO: Should this changed to stateMachineExecutor or Not?
    executorService.execute(
        new SmExecutionAsyncResumer(context, currentState, response, this, asyncError, featureFlagService));
  }

  /**
   * Handle event.
   *
   * @param workflowExecutionInterrupt the workflow execution event
   */
  public void handleInterrupt(ExecutionInterrupt workflowExecutionInterrupt) {
    WorkflowExecution workflowExecution = wingsPersistence.getWithAppId(
        WorkflowExecution.class, workflowExecutionInterrupt.getAppId(), workflowExecutionInterrupt.getExecutionUuid());

    ExecutionInterruptType type = workflowExecutionInterrupt.getExecutionInterruptType();
    try (AutoLogContext ignore = workflowExecutionInterrupt.autoLogContext()) {
      log.info("State To handle interrupt of Type : {}", type);
      switch (type) {
        case IGNORE: {
          StateExecutionInstance stateExecutionInstance = getStateExecutionInstance(
              workflowExecutionInterrupt.getAppId(), workflowExecutionInterrupt.getExecutionUuid(),
              workflowExecutionInterrupt.getStateExecutionInstanceId());

          updateStatus(stateExecutionInstance, FAILED, Lists.newArrayList(WAITING), null);

          StateMachine sm = stateExecutionService.obtainStateMachine(stateExecutionInstance);

          State currentState =
              sm.getState(stateExecutionInstance.getChildStateMachineId(), stateExecutionInstance.getStateName());
          injector.injectMembers(currentState);

          ExecutionContextImpl context = new ExecutionContextImpl(stateExecutionInstance, sm, injector);
          injector.injectMembers(context);
          successTransition(context);
          break;
        }

        case RESUME:
        case MARK_SUCCESS: {
          ExecutionContextImpl context = getExecutionContext(workflowExecutionInterrupt);
          executorService.execute(new SmExecutionResumer(context, this, SUCCESS));
          break;
        }

        case CONTINUE_WITH_DEFAULTS: {
          StateExecutionInstance stateExecutionInstance = getStateExecutionInstance(
              workflowExecutionInterrupt.getAppId(), workflowExecutionInterrupt.getExecutionUuid(),
              workflowExecutionInterrupt.getStateExecutionInstanceId());
          ContinuePipelineResponseData responseData =
              new ContinuePipelineResponseData(null, workflowExecutionInterrupt);
          waitNotifyEngine.doneWith(getContinuePipelineWaitId(stateExecutionInstance.getPipelineStageElementId(),
                                        stateExecutionInstance.getExecutionUuid()),
              responseData);
          break;
        }

        case RETRY: {
          StateExecutionInstance stateExecutionInstance = getStateExecutionInstance(
              workflowExecutionInterrupt.getAppId(), workflowExecutionInterrupt.getExecutionUuid(),
              workflowExecutionInterrupt.getStateExecutionInstanceId());

          retryStateExecutionInstance(stateExecutionInstance, workflowExecutionInterrupt.getProperties());
          break;
        }
        case MARK_EXPIRED:
        case ABORT: {
          ExecutionContextImpl context = getExecutionContext(workflowExecutionInterrupt);
          discontinueExecution(context, type);
          break;
        }
        case ABORT_ALL: {
          abortInstancesByStatus(workflowExecutionInterrupt, workflowExecution,
              EnumSet.<ExecutionStatus>of(NEW, QUEUED, RUNNING, STARTING, PAUSED, PAUSING, WAITING));
          break;
        }
        case END_EXECUTION:
        case ROLLBACK_PROVISIONER_AFTER_PHASES:
        case ROLLBACK:
        case ROLLBACK_ON_APPROVAL:
        case ROLLBACK_PROVISIONER_AFTER_PHASES_ON_APPROVAL: {
          endExecution(workflowExecutionInterrupt, workflowExecution);
          break;
        }
        case PAUSE_ALL: {
          break;
        }
        case RESUME_ALL: {
          break;
        }
        default:
          unhandled(type);
      }
    }
  }

  private ExecutionContextImpl getExecutionContext(ExecutionInterrupt workflowExecutionInterrupt) {
    return getExecutionContext(workflowExecutionInterrupt.getAppId(), workflowExecutionInterrupt.getExecutionUuid(),
        workflowExecutionInterrupt.getStateExecutionInstanceId());
  }

  public ExecutionContextImpl getExecutionContext(String appId, String executionUuid, String stateExecutionInstanceId) {
    StateExecutionInstance stateExecutionInstance =
        getStateExecutionInstance(appId, executionUuid, stateExecutionInstanceId);
    if (stateExecutionInstance == null) {
      log.info("could not find state execution for app {}, workflow execution {}, uuid {}", appId, executionUuid,
          stateExecutionInstanceId);
      return null;
    }
    StateMachine sm = stateExecutionService.obtainStateMachine(stateExecutionInstance);

    return new ExecutionContextImpl(stateExecutionInstance, sm, injector);
  }

  private void endExecution(ExecutionInterrupt workflowExecutionInterrupt, WorkflowExecution workflowExecution) {
    abortInstancesByStatus(workflowExecutionInterrupt, workflowExecution,
        EnumSet.<ExecutionStatus>of(NEW, QUEUED, RUNNING, STARTING, PAUSED, PAUSING));

    List<StateExecutionInstance> allStateExecutionInstances =
        wingsPersistence.createQuery(StateExecutionInstance.class)
            .filter(StateExecutionInstanceKeys.appId, workflowExecutionInterrupt.getAppId())
            .filter(StateExecutionInstanceKeys.executionUuid, workflowExecutionInterrupt.getExecutionUuid())
            .filter(StateExecutionInstanceKeys.status, WAITING)
            .asList();

    for (StateExecutionInstance stateExecutionInstance : allStateExecutionInstances) {
      StateMachine sm = stateExecutionService.obtainStateMachine(stateExecutionInstance);
      ExecutionContextImpl context = new ExecutionContextImpl(stateExecutionInstance, sm, injector);
      injector.injectMembers(context);
      updateStateExecutionData(stateExecutionInstance, null, FAILED, null, asList(WAITING), null, null, null);
      endTransition(context, stateExecutionInstance, FAILED, null);
    }
  }

  private void abortInstancesByStatus(ExecutionInterrupt workflowExecutionInterrupt,
      WorkflowExecution workflowExecution, Collection<ExecutionStatus> statuses) {
    if (!markAbortingState(workflowExecutionInterrupt, workflowExecution, statuses)) {
      log.warn(
          "ABORT_ALL workflowExecutionInterrupt: {} being ignored as could not mark aborting states for all instances: {}",
          workflowExecutionInterrupt.getUuid(), workflowExecutionInterrupt.getExecutionUuid());
      return;
    }

    List<StateExecutionInstance> allStateExecutionInstances =
        wingsPersistence.createQuery(StateExecutionInstance.class)
            .filter(StateExecutionInstanceKeys.appId, workflowExecutionInterrupt.getAppId())
            .filter(StateExecutionInstanceKeys.executionUuid, workflowExecutionInterrupt.getExecutionUuid())
            .filter(StateExecutionInstanceKeys.status, DISCONTINUING)
            .asList();

    if (isEmpty(allStateExecutionInstances)) {
      log.warn(
          "ABORT_ALL workflowExecutionInterrupt: {} being ignored as no running instance found for executionUuid: {}",
          workflowExecutionInterrupt.getUuid(), workflowExecutionInterrupt.getExecutionUuid());
      return;
    }

    for (StateExecutionInstance stateExecutionInstance : allStateExecutionInstances) {
      StateMachine sm = stateExecutionService.obtainStateMachine(stateExecutionInstance);
      ExecutionContextImpl context = new ExecutionContextImpl(stateExecutionInstance, sm, injector);
      injector.injectMembers(context);
      if (workflowExecutionInterrupt.getExecutionInterruptType().equals(
              ExecutionInterruptType.ROLLBACK_PROVISIONER_AFTER_PHASES_ON_APPROVAL)
          || workflowExecutionInterrupt.getExecutionInterruptType().equals(
              ExecutionInterruptType.ROLLBACK_ON_APPROVAL)) {
        continue;
      }
      discontinueMarkedInstance(context, stateExecutionInstance, ABORTED);
    }
  }

  private void retryStateExecutionInstance(
      StateExecutionInstance stateExecutionInstance, Map<String, Object> stateParams) {
    clearStateExecutionData(stateExecutionInstance, stateParams);
    stateExecutionInstance = getStateExecutionInstance(
        stateExecutionInstance.getAppId(), stateExecutionInstance.getExecutionUuid(), stateExecutionInstance.getUuid());
    StateMachine sm = stateExecutionService.obtainStateMachine(stateExecutionInstance);

    State currentState =
        sm.getState(stateExecutionInstance.getChildStateMachineId(), stateExecutionInstance.getStateName());
    injector.injectMembers(currentState);
    sweepingOutputService.cleanForStateExecutionInstance(stateExecutionInstance);
    ExecutionContextImpl context = new ExecutionContextImpl(stateExecutionInstance, sm, injector);
    injector.injectMembers(context);
    log.info(DEBUG_LINE + "retrying stateExecutionInstance {}", stateExecutionInstance.getUuid());
    executorService.execute(new SmExecutionDispatcher(context, this));
  }

  protected void clearStateExecutionData(
      StateExecutionInstance stateExecutionInstance, Map<String, Object> stateParams) {
    Map<String, StateExecutionData> stateExecutionMap = stateExecutionInstance.getStateExecutionMap();
    if (stateExecutionMap == null) {
      return;
    }

    UpdateOperations<StateExecutionInstance> ops =
        wingsPersistence.createUpdateOperations(StateExecutionInstance.class);

    StateExecutionData stateExecutionData = stateExecutionMap.get(stateExecutionInstance.getDisplayName());
    if (stateExecutionData != null) {
      ops.addToSet("stateExecutionDataHistory", stateExecutionData);
      stateExecutionInstance.getStateExecutionDataHistory().add(stateExecutionData);

      stateExecutionMap.remove(stateExecutionInstance.getDisplayName());
      ops.set("stateExecutionMap", stateExecutionMap);
    }

    List<ContextElement> notifyElements = new ArrayList<>();
    String prevInstanceId = stateExecutionInstance.getPrevInstanceId();
    if (prevInstanceId != null) {
      StateExecutionInstance prevStateExecutionInstance =
          wingsPersistence.get(StateExecutionInstance.class, prevInstanceId);
      Preconditions.checkNotNull(prevStateExecutionInstance);
      if (prevStateExecutionInstance.getNotifyElements() != null) {
        notifyElements = prevStateExecutionInstance.getNotifyElements();
      }
    }
    ops.set("notifyElements", notifyElements);

    if (stateParams != null) {
      ops.set(STATE_PARAMS, stateParams);
    }

    if (stateExecutionInstance.getEndTs() != null) {
      stateExecutionInstance.setEndTs(null);
      ops.unset("endTs");
    }
    Long expiryTs = stateExecutionInstance.getStateTimeout() != null && stateExecutionInstance.getStateTimeout() > 0
        ? System.currentTimeMillis() + stateExecutionInstance.getStateTimeout()
        : Long.MAX_VALUE;
    ops.set(StateExecutionInstanceKeys.expiryTs, expiryTs);
    ops.set(StateExecutionInstanceKeys.status, NEW);
    ops.set(StateExecutionInstanceKeys.retry, Boolean.TRUE);
    ops.set(StateExecutionInstanceKeys.retryCount, stateExecutionInstance.getRetryCount() + 1);
    ops.set(StateExecutionInstanceKeys.waitingForManualIntervention, false);

    Query<StateExecutionInstance> query;
    if (featureFlagService.isEnabled(TIMEOUT_FAILURE_SUPPORT, stateExecutionInstance.getAccountId())) {
      query = wingsPersistence.createQuery(StateExecutionInstance.class)
                  .filter(StateExecutionInstanceKeys.appId, stateExecutionInstance.getAppId())
                  .filter(ID_KEY, stateExecutionInstance.getUuid())
                  .field(StateExecutionInstanceKeys.status)
                  .in(asList(WAITING, FAILED, ERROR, EXPIRED, STARTING));
    } else {
      query = wingsPersistence.createQuery(StateExecutionInstance.class)
                  .filter(StateExecutionInstanceKeys.appId, stateExecutionInstance.getAppId())
                  .filter(ID_KEY, stateExecutionInstance.getUuid())
                  .field(StateExecutionInstanceKeys.status)
                  .in(asList(WAITING, FAILED, ERROR, STARTING));
    }

    UpdateResults updateResult = wingsPersistence.update(query, ops);
    if (updateResult == null || updateResult.getWriteResult() == null || updateResult.getWriteResult().getN() != 1) {
      throw new WingsException(ErrorCode.RETRY_FAILED).addParam("displayName", stateExecutionInstance.getDisplayName());
    }

    try {
      usageMetricsEventPublisher.publishDeploymentStepTimeSeriesEvent(
          stateExecutionInstance.getAccountId(), stateExecutionInstance);
    } catch (Exception e) {
      log.error("Failed to publish deployment step event [{}] , [{}]", stateExecutionInstance, e);
    }

    final StateStatusUpdateInfo arg =
        StateStatusUpdateInfo.buildFromStateExecutionInstance(stateExecutionInstance, false);
    statusUpdateSubject.fireInform(StateStatusUpdate::stateExecutionStatusUpdated, arg);
    remoteObserverInformer.sendEvent(
        ReflectionUtils.getMethod(StateStatusUpdate.class, "stateExecutionStatusUpdated", StateStatusUpdateInfo.class),
        StateMachineExecutor.class, arg);
  }

  private boolean markAbortingState(@NotNull ExecutionInterrupt workflowExecutionInterrupt,
      WorkflowExecution workflowExecution, Collection<ExecutionStatus> statuses) {
    // Get all that are eligible for discontinuing

    List<StateExecutionInstance> allStateExecutionInstances =
        wingsPersistence.createQuery(StateExecutionInstance.class)
            .filter(StateExecutionInstanceKeys.appId, workflowExecutionInterrupt.getAppId())
            .filter(StateExecutionInstanceKeys.executionUuid, workflowExecutionInterrupt.getExecutionUuid())
            .field(StateExecutionInstanceKeys.status)
            .in(statuses)
            .field(StateExecutionInstanceKeys.createdAt)
            .greaterThanOrEq(workflowExecution.getCreatedAt())
            .project(StateExecutionInstanceKeys.uuid, true)
            .project(StateExecutionInstanceKeys.stateType, true)
            .asList();

    if (isEmpty(allStateExecutionInstances)) {
      log.warn("No stateExecutionInstance could be marked as DISCONTINUING - appId: {}, executionUuid: {}",
          workflowExecutionInterrupt.getAppId(), workflowExecutionInterrupt.getExecutionUuid());
      return false;
    }
    List<String> leafInstanceIds =
        getAllLeafInstanceIds(workflowExecutionInterrupt, workflowExecution, allStateExecutionInstances);

    UpdateOperations<StateExecutionInstance> ops =
        wingsPersistence.createUpdateOperations(StateExecutionInstance.class);

    ops.set(StateExecutionInstanceKeys.status, DISCONTINUING);
    ops.set(StateExecutionInstanceKeys.expiryTs, System.currentTimeMillis() + ABORT_EXPIRY_BUFFER_MILLIS);
    ops.addToSet(StateExecutionInstanceKeys.interruptHistory,
        ExecutionInterruptEffect.builder()
            .interruptId(workflowExecutionInterrupt.getUuid())
            .tookEffectAt(new Date())
            .build());

    Query<StateExecutionInstance> query =
        wingsPersistence.createQuery(StateExecutionInstance.class)
            .filter(StateExecutionInstanceKeys.appId, workflowExecutionInterrupt.getAppId())
            .filter(StateExecutionInstanceKeys.executionUuid, workflowExecutionInterrupt.getExecutionUuid())
            .field(StateExecutionInstanceKeys.uuid)
            .in(leafInstanceIds);
    // Set the status to DISCONTINUING
    UpdateResults updateResult = wingsPersistence.update(query, ops);
    if (updateResult == null || updateResult.getWriteResult() == null || updateResult.getWriteResult().getN() == 0) {
      log.warn("No stateExecutionInstance could be marked as DISCONTINUING - appId: {}, executionUuid: {}",
          workflowExecutionInterrupt.getAppId(), workflowExecutionInterrupt.getExecutionUuid());
      return false;
    }

    if (updateResult != null) {
      List<StateExecutionInstance> stateExecutionInstances =
          getAllStateExecutionInstancesHavingTheseIds(leafInstanceIds);
      for (StateExecutionInstance stateExecutionInstance : stateExecutionInstances) {
        try {
          usageMetricsEventPublisher.publishDeploymentStepTimeSeriesEvent(
              stateExecutionInstance.getAccountId(), stateExecutionInstance);
        } catch (Exception e) {
          log.error("Failed to publish deployment step event [{}] , [{}]", stateExecutionInstance, e);
        }
      }
    }

    return true;
  }

  private List<String> getAllLeafInstanceIds(ExecutionInterrupt workflowExecutionInterrupt,
      WorkflowExecution workflowExecution, List<StateExecutionInstance> stateExecutionInstances) {
    List<String> allInstanceIds =
        stateExecutionInstances.stream().map(StateExecutionInstance::getUuid).collect(toList());

    // Get Parent Ids
    List<String> parentInstanceIds =
        stateExecutionInstances.stream()
            .filter(stateExecutionInstance
                -> stateExecutionInstance.getStateType().equals(StateType.REPEAT.name())
                    || stateExecutionInstance.getStateType().equals(StateType.FORK.name())
                    || stateExecutionInstance.getStateType().equals(StateType.PHASE_STEP.name())
                    || stateExecutionInstance.getStateType().equals(StateType.PHASE.name())
                    || stateExecutionInstance.getStateType().equals(StateType.SUB_WORKFLOW.name()))
            .map(StateExecutionInstance::getUuid)
            .collect(toList());

    if (isEmpty(parentInstanceIds)) {
      return allInstanceIds;
    }

    List<StateExecutionInstance> childInstances =
        wingsPersistence.createQuery(StateExecutionInstance.class)
            .filter(StateExecutionInstanceKeys.appId, workflowExecutionInterrupt.getAppId())
            .filter(StateExecutionInstanceKeys.executionUuid, workflowExecutionInterrupt.getExecutionUuid())
            .field(StateExecutionInstanceKeys.parentInstanceId)
            .in(parentInstanceIds)
            .field(StateExecutionInstanceKeys.status)
            .in(EnumSet.<ExecutionStatus>of(NEW, QUEUED, STARTING, RUNNING, PAUSED, PAUSING, WAITING))
            .asList();

    // get distinct parent Ids
    List<String> parentIdsHavingChildren =
        childInstances.stream().map(StateExecutionInstance::getParentInstanceId).distinct().collect(toList());

    // parent with no children
    allInstanceIds.removeAll(parentIdsHavingChildren);

    // Mark aborting
    return allInstanceIds;
  }

  private List<StateExecutionInstance> getAllStateExecutionInstancesHavingTheseIds(
      List<String> stateExecutionInstanceIds) {
    return wingsPersistence.createQuery(StateExecutionInstance.class)
        .field(ID_KEY)
        .exists()
        .field(ID_KEY)
        .in(stateExecutionInstanceIds)
        .asList();
  }

  private StateExecutionInstance getStateExecutionInstance(
      String appId, String executionUuid, String stateExecutionInstanceId) {
    // TODO: convert this not to use Query directly after default createdAt sorting is taken off
    Query<StateExecutionInstance> query = wingsPersistence.createQuery(StateExecutionInstance.class)
                                              .filter(ID_KEY, stateExecutionInstanceId)
                                              .filter(StateExecutionInstanceKeys.appId, appId);
    if (executionUuid != null) {
      query.filter("executionUuid", executionUuid);
    }
    return query.get();
  }

  public void addContext(ExecutionContextImpl context, WingsException exception) {
    if (context == null) {
      return;
    }
    try {
      if (context.getStateExecutionInstance() != null) {
        exception.addContext(StateExecutionInstance.class, context.getStateExecutionInstance().getUuid());
      }
      exception.addContext(WorkflowExecution.class, context.getWorkflowExecutionId());
      if (context.getEnv() != null) {
        exception.addContext(Environment.class, context.getEnv().getUuid());
      }
      if (context.getAppId() != null) {
        String accountId = appService.getAccountIdByAppId(context.getAppId());
        exception.addContext(Account.class, accountId);
        exception.addContext(Application.class, context.getAppId());
      }
    } catch (RuntimeException ignore) {
      // Suppress any exception during filling the context. We would like to see the original more.
    }
  }

  @Override
  public void appendedDataFor(String stateExecutionInstanceId) {
    Query<StateExecutionInstance> query = wingsPersistence.createQuery(StateExecutionInstance.class)
                                              .filter(StateExecutionInstanceKeys.uuid, stateExecutionInstanceId);

    UpdateOperations<StateExecutionInstance> updateOperations =
        wingsPersistence.createUpdateOperations(StateExecutionInstance.class)
            .set(StateExecutionInstanceKeys.hasInspection, true);

    wingsPersistence.update(query, updateOperations);
  }

  private static class SmExecutionDispatcher implements Runnable {
    private ExecutionContextImpl context;
    private StateMachineExecutor stateMachineExecutor;

    /**
     * Instantiates a new Sm execution dispatcher.
     *
     * @param context              the context
     * @param stateMachineExecutor the state machine executor
     */
    SmExecutionDispatcher(ExecutionContextImpl context, StateMachineExecutor stateMachineExecutor) {
      this.context = context;
      this.stateMachineExecutor = stateMachineExecutor;
    }

    /* (non-Javadoc)
     * @see java.lang.Runnable#run()
     */
    @Override
    @SuppressWarnings("PMD")
    public void run() {
      try (AutoLogContext ignore = context.autoLogContext()) {
        log.info(DEBUG_LINE + "inside run of SmExecutionDispatcher");
        stateMachineExecutor.startExecution(context);
      } catch (WingsException exception) {
        stateMachineExecutor.addContext(context, exception);
        ExceptionLogger.logProcessedMessages(exception, MANAGER, log);
      } catch (Exception exception) {
        log.error("Unhandled exception", exception);
      } catch (Throwable throwable) {
        log.error("Catching a throwable here, this shouldn't happen", throwable);
      }
    }
  }

  private static class SmExecutionResumer implements Runnable {
    private ExecutionContextImpl context;
    private StateMachineExecutor stateMachineExecutor;
    private ExecutionStatus status;

    /**
     * Instantiates a new Sm execution dispatcher.
     *
     * @param context              the context
     * @param stateMachineExecutor the state machine executor
     */
    SmExecutionResumer(
        ExecutionContextImpl context, StateMachineExecutor stateMachineExecutor, ExecutionStatus status) {
      this.context = context;
      this.stateMachineExecutor = stateMachineExecutor;
      this.status = status;
    }

    /* (non-Javadoc)
     * @see java.lang.Runnable#run()
     */
    @Override
    @SuppressWarnings("PMD")
    public void run() {
      try (AutoLogContext ignore = context.autoLogContext()) {
        stateMachineExecutor.handleExecuteResponse(
            context, ExecutionResponse.builder().executionStatus(status).build());
      } catch (WingsException ex) {
        stateMachineExecutor.addContext(context, ex);
        stateMachineExecutor.handleExecuteResponseException(context, ex);
      } catch (Exception ex) {
        stateMachineExecutor.addContext(context, new WingsException(ex));
        stateMachineExecutor.handleExecuteResponseException(context, new WingsException(ex));
      } catch (Throwable throwable) {
        log.error("Catching a throwable here, this shouldn't happen", throwable);
      }
    }
  }

  private class SmExecutionAsyncResumer implements Runnable {
    private ExecutionContextImpl context;
    private StateMachineExecutor stateMachineExecutor;
    private State state;
    private Map<String, ResponseData> response;
    private boolean asyncError;
    private FeatureFlagService featureFlagService;

    /**
     * Instantiates a new Sm execution dispatcher.
     *
     * @param context              the context
     * @param state                the state
     * @param response             the response
     * @param stateMachineExecutor the state machine executor
     */
    SmExecutionAsyncResumer(ExecutionContextImpl context, State state, Map<String, ResponseData> response,
        StateMachineExecutor stateMachineExecutor, boolean asyncError, FeatureFlagService featureFlagService) {
      this.context = context;
      this.state = state;
      this.response = response;
      this.stateMachineExecutor = stateMachineExecutor;
      this.asyncError = asyncError;
      this.featureFlagService = featureFlagService;
    }

    /* (non-Javadoc)
     * @see java.lang.Runnable#run()
     */
    @Override
    public void run() {
      try (AutoLogContext ignore = context.autoLogContext()) {
        populateDelegateMetaData();
        if (asyncError) {
          StateExecutionData stateExecutionData = context.getStateExecutionInstance().fetchStateExecutionData();
          ErrorNotifyResponseData errorNotifyResponseData =
              (ErrorNotifyResponseData) response.values().iterator().next();
          stateExecutionData.setErrorMsg(errorNotifyResponseData.getErrorMessage());
          if (featureFlagService.isEnabled(TIMEOUT_FAILURE_SUPPORT, context.getAccountId())
              && errorNotifyResponseData.isExpired()) {
            stateExecutionData.setStatus(EXPIRED);
            EnumSet<FailureType> failureTypes;
            if (errorNotifyResponseData.getFailureTypes() == null) {
              failureTypes = EnumSet.of(FailureType.EXPIRED, FailureType.TIMEOUT_ERROR);
            } else {
              failureTypes = errorNotifyResponseData.getFailureTypes();
              failureTypes.addAll(EnumSet.of(FailureType.EXPIRED, FailureType.TIMEOUT_ERROR));
            }

            stateMachineExecutor.handleExecuteResponse(context,
                ExecutionResponse.builder()
                    .executionStatus(EXPIRED)
                    .stateExecutionData(stateExecutionData)
                    .failureTypes(failureTypes)
                    .errorMessage(errorNotifyResponseData.getErrorMessage())
                    .build());
          } else {
            stateExecutionData.setStatus(ERROR);
            stateMachineExecutor.handleExecuteResponse(context,
                ExecutionResponse.builder()
                    .executionStatus(ERROR)
                    .stateExecutionData(stateExecutionData)
                    .failureTypes(errorNotifyResponseData.getFailureTypes())
                    .errorMessage(errorNotifyResponseData.getErrorMessage())
                    .build());
          }

          return;
        }

        StateExecutionInstance stateExecutionInstance = context.getStateExecutionInstance();
        if (stateExecutionInstance.getStateParams() != null) {
          mapObject(stateExecutionInstance.getStateParams(), state, context.getAccountId());
        }
        log.info("Execution is processing for workflowExecutionId: {}, stateExecutionId: {}, appId: {}, accountId: {}",
            context.getWorkflowExecutionId(), context.getStateExecutionInstanceId(), context.getAppId(),
            context.getAccountId());
        ExecutionResponse executionResponse = state.handleAsyncResponse(context, response);
        stateMachineExecutor.handleResponse(context, executionResponse);
      } catch (WingsException ex) {
        stateMachineExecutor.handleExecuteResponseException(context, ex);
      } catch (Exception ex) {
        stateMachineExecutor.handleExecuteResponseException(context, new WingsException(ex));
      }
    }

    private void populateDelegateMetaData() {
      try {
        if (response != null) {
          DelegateResponseData responseData = (DelegateResponseData) response.values().iterator().next();
          if (responseData instanceof DelegateTaskNotifyResponseData) {
            context.getStateExecutionData().setDelegateMetaInfo(
                ((DelegateTaskNotifyResponseData) responseData).getDelegateMetaInfo());
          } else if (responseData instanceof StateExecutionData) {
            context.getStateExecutionData().setDelegateMetaInfo(
                ((StateExecutionData) responseData).getDelegateMetaInfo());
          }
        }
      } catch (Exception ex) {
        log.warn("Failed to extract delegate metadata", ex);
      }
    }
  }

  /**
   * Maps source to target.
   */
  private void mapObject(Map<String, Object> source, State target, String accountId) {
    try {
      MapperUtils.mapObject(source, target);
    } catch (MappingException e) {
      // CHANGE LOG PRIORITY TO A LOWER VALUE AS WE FOUND THE ROOT CAUSE
      log.warn(String.format("Got model mapping exception during map the stateParams <%s> to state <%s>", source,
                   ToStringBuilder.reflectionToString(target)),
          e);

      // ITERATE THE SOURCE ELEMENTS AND MAP TO THE SAME TARGET
      mapEntries(source, target, accountId);
    }
  }

  @VisibleForTesting
  void mapEntries(Map<String, Object> source, State target, String accountId) {
    for (Map.Entry<String, Object> entry : source.entrySet()) {
      try {
        MapperUtils.mapObject(sanitizeEntry(entry), target);

      } catch (MappingException e1) {
        log.error(String.format("Failure on entry <%s> to the same target", entry), e1);

        // IF IGNORE FF IS ENABLED, LOG AND KEEP THE EXECUTION.
        if (featureFlagService.isNotEnabled(FeatureName.SPG_STATE_MACHINE_MAPPING_EXCEPTION_IGNORE, accountId)) {
          throw e1;
        }
      }
    }
  }

  @VisibleForTesting
  Map<String, Object> sanitizeEntry(Map.Entry<String, Object> entry) {
    //
    // ONLY SOME FIELDS NEED SANITIZATION, WHEN NOT REQUIRED THE SAME ENTRY IS RETURNED.
    //
    Map<String, Object> result = null;

    if (TEMPLATE_VARIABLE_ENTRY.equals(entry.getKey())) {
      result = sanitizeTemplateVariables(entry);
    }

    // ADVICE: USING singletonMap BECAUSE ENTRY VALUE CAN BE NULL
    return Optional.ofNullable(result).orElseGet(() -> Collections.singletonMap(entry.getKey(), entry.getValue()));
  }

  @VisibleForTesting
  Map<String, Object> sanitizeTemplateVariables(final Map.Entry<String, Object> entry) {
    //
    // THE ROOT CAUSE OF MAPPING EXCEPTION IS AN ISSUE IN ModelMapper LIBRARY WHERE IS EXPECTED TO EVERY MAP
    // ELEMENT OF THE SAME FIELD HAS THE SAME KEYS, NOT THE COUNT, BUT THE KEYS. MORE DETAILS AT CDS-54824.
    //
    if (entry.getValue() instanceof List) {
      try {
        @SuppressWarnings("unchecked")
        List<Map<String, String>> elements = (List<Map<String, String>>) entry.getValue();

        if (elements.stream().anyMatch(e -> e.containsKey(VARIABLE_DESCRIPTION_FIELD))) {
          final List<Map<String, String>> result = new ArrayList<>();

          elements.forEach(e -> {
            if (e.containsKey(VARIABLE_DESCRIPTION_FIELD)) {
              result.add(e);
            } else {
              Map<String, String> content = new HashMap<>(e);
              content.put(VARIABLE_DESCRIPTION_FIELD, StringUtils.EMPTY);
              result.add(content);
            }
          });

          return Collections.singletonMap(TEMPLATE_VARIABLE_ENTRY, result);
        }

      } catch (ClassCastException e) {
        log.warn(
            String.format("Unable to cast [%s] to expected type [java.util.List]", entry.getValue().getClass()), e);
      } catch (RuntimeException e) {
        log.warn(String.format("Unable to sanitize field [%s]", TEMPLATE_VARIABLE_ENTRY), e);
      }
    }
    return null;
  }
}
