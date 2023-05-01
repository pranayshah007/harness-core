/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.beans.ExecutionInterruptType.ABORT;
import static io.harness.beans.ExecutionInterruptType.END_EXECUTION;
import static io.harness.beans.ExecutionInterruptType.MARK_FAILED;
import static io.harness.beans.ExecutionInterruptType.MARK_SUCCESS;
import static io.harness.beans.ExecutionInterruptType.ROLLBACK_PREVIOUS_STAGES_ON_PIPELINE;
import static io.harness.beans.ExecutionInterruptType.WAITING_FOR_MANUAL_INTERVENTION;
import static io.harness.beans.ExecutionStatus.FAILED;
import static io.harness.beans.ExecutionStatus.NEW;
import static io.harness.beans.ExecutionStatus.RUNNING;
import static io.harness.beans.ExecutionStatus.SKIPPED;
import static io.harness.beans.ExecutionStatus.STARTING;
import static io.harness.beans.ExecutionStatus.SUCCESS;
import static io.harness.beans.ExecutionStatus.WAITING;
import static io.harness.beans.FeatureName.SPG_STATE_MACHINE_MAPPING_EXCEPTION_IGNORE;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.AGORODETKI;
import static io.harness.rule.OwnerRule.ANSHUL;
import static io.harness.rule.OwnerRule.FERNANDOD;
import static io.harness.rule.OwnerRule.GARVIT;
import static io.harness.rule.OwnerRule.GEORGE;
import static io.harness.rule.OwnerRule.LUCAS_SALES;
import static io.harness.rule.OwnerRule.MILOS;
import static io.harness.rule.OwnerRule.PRASHANT;
import static io.harness.rule.OwnerRule.RAFAEL;
import static io.harness.rule.OwnerRule.TMACARI;

import static software.wings.api.EnvStateExecutionData.Builder.anEnvStateExecutionData;
import static software.wings.api.InstanceElement.Builder.anInstanceElement;
import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.beans.CanaryOrchestrationWorkflow.CanaryOrchestrationWorkflowBuilder.aCanaryOrchestrationWorkflow;
import static software.wings.beans.NotificationGroup.NotificationGroupBuilder.aNotificationGroup;
import static software.wings.beans.NotificationRule.NotificationRuleBuilder.aNotificationRule;
import static software.wings.common.NotificationMessageResolver.NotificationMessageType.MANUAL_INTERVENTION_NEEDED_NOTIFICATION;
import static software.wings.common.NotificationMessageResolver.NotificationMessageType.NEEDS_RUNTIME_INPUTS;
import static software.wings.common.NotificationMessageResolver.NotificationMessageType.PIPELINE_NOTIFICATION;
import static software.wings.common.NotificationMessageResolver.NotificationMessageType.RUNTIME_INPUTS_PROVIDED;
import static software.wings.sm.ExecutionEventAdvice.ExecutionEventAdviceBuilder.anExecutionEventAdvice;
import static software.wings.sm.StateExecutionData.StateExecutionDataBuilder.aStateExecutionData;
import static software.wings.sm.StateExecutionInstance.Builder.aStateExecutionInstance;
import static software.wings.sm.StateMachine.StateMachineBuilder.aStateMachine;
import static software.wings.sm.StateMachineExecutor.TEMPLATE_VARIABLE_ENTRY;
import static software.wings.sm.StateMachineExecutor.VARIABLE_DESCRIPTION_FIELD;
import static software.wings.sm.StateMachineExecutor.VARIABLE_VALUE_FIELD;
import static software.wings.sm.StateType.ENV_ROLLBACK_STATE;
import static software.wings.sm.StateType.ENV_STATE;
import static software.wings.sm.StateType.PHASE;
import static software.wings.sm.Transition.Builder.aTransition;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.NOTIFICATION_GROUP_ID;
import static software.wings.utils.WingsTestConstants.PIPELINE_WORKFLOW_EXECUTION_ID;
import static software.wings.utils.WingsTestConstants.USER_NAME;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static org.apache.commons.lang3.RandomUtils.nextInt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.EmbeddedUser;
import io.harness.beans.EventType;
import io.harness.beans.WorkflowType;
import io.harness.category.element.UnitTests;
import io.harness.exception.FailureType;
import io.harness.ff.FeatureFlagService;
import io.harness.reflection.ReflectionUtils;
import io.harness.rule.Owner;
import io.harness.serializer.MapperUtils;
import io.harness.waiter.OrchestrationNotifyEventListener;

import software.wings.WingsBaseTest;
import software.wings.api.EnvStateExecutionData;
import software.wings.api.SkipStateExecutionData;
import software.wings.beans.CanaryOrchestrationWorkflow;
import software.wings.beans.Notification;
import software.wings.beans.NotificationRule;
import software.wings.beans.Pipeline;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowExecution;
import software.wings.common.NotificationMessageResolver;
import software.wings.dl.WingsPersistence;
import software.wings.rules.Listeners;
import software.wings.service.StaticMap;
import software.wings.service.impl.WorkflowExecutionUpdate;
import software.wings.service.impl.workflow.WorkflowNotificationDetails;
import software.wings.service.impl.workflow.WorkflowNotificationHelper;
import software.wings.service.intfc.AlertService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.NotificationService;
import software.wings.service.intfc.PipelineService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.service.intfc.WorkflowService;
import software.wings.sm.StateMachineTestBase.StateAsync;
import software.wings.sm.StateMachineTestBase.StateSync;
import software.wings.sm.states.EnvRollbackState;
import software.wings.sm.states.ForkState;
import software.wings.sm.states.ForkState.ForkStateExecutionData;
import software.wings.sm.states.ShellScriptState;

import com.google.inject.Inject;
import com.google.inject.Injector;
import java.lang.reflect.Field;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.modelmapper.MappingException;
import org.modelmapper.spi.ErrorMessage;

/**
 * Created by rishi on 2/25/17.
 */
@OwnedBy(CDC)
@Listeners(OrchestrationNotifyEventListener.class)
@Slf4j
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class StateMachineExecutorTest extends WingsBaseTest {
  private final String APP_ID = generateUuid();
  private final DateFormat dateFormat = new SimpleDateFormat("MMM d");
  private final DateFormat timeFormat = new SimpleDateFormat("HH:mm z");
  @Inject WingsPersistence wingsPersistence;
  @InjectMocks @Inject StateMachineExecutor stateMachineExecutor;

  @Inject private WorkflowService workflowService;

  @Mock private Workflow workflow;
  @Mock private Pipeline pipeline;
  @Mock private ExecutionContextImpl context;
  @Mock private WorkflowService mockWorkflowService;
  @Mock private PipelineService pipelineService;
  @Mock private NotificationService notificationService;
  @Mock private WorkflowExecutionService workflowExecutionService;
  @Mock private NotificationMessageResolver notificationMessageResolver;
  @Mock private WorkflowNotificationHelper workflowNotificationHelper;
  @Mock private WorkflowExecutionUpdate workflowExecutionUpdate;
  @Mock private FeatureFlagService featureFlagService;
  @Mock private AppService appService;
  @Mock private ExecutionEventAdvice executionEventAdvice;
  @Mock private StateExecutionInstance stateExecutionInstance;
  @Mock private AlertService alertService;
  @InjectMocks private StateMachineExecutor injectStateMachineExecutor;
  @InjectMocks private StateMachineExecutor spyExecutor = spy(new StateMachineExecutor());

  @Captor private ArgumentCaptor<List<NotificationRule>> notificationRuleArgumentCaptor;

  /**
   * Should trigger.
   *
   * @throws InterruptedException the interrupted exception
   */
  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldTrigger() throws InterruptedException {
    String appId = generateUuid();
    StateMachine sm = new StateMachine();
    sm.setAppId(appId);
    State stateA = new StateSync("stateA" + StaticMap.getUnique());
    sm.addState(stateA);
    StateSync stateB = new StateSync("stateB" + StaticMap.getUnique());
    sm.addState(stateB);
    StateSync stateC = new StateSync("stateC" + StaticMap.getUnique());
    sm.addState(stateC);
    sm.setInitialStateName(stateA.getName());

    sm.addTransition(
        aTransition().withFromState(stateA).withTransitionType(TransitionType.SUCCESS).withToState(stateB).build());
    sm.addTransition(
        aTransition().withFromState(stateB).withTransitionType(TransitionType.SUCCESS).withToState(stateC).build());

    sm = workflowService.createStateMachine(sm);
    assertThat(sm).isNotNull().extracting(StateMachine::getUuid).isNotNull();

    String smId = sm.getUuid();
    log.info("Going to trigger state machine");
    String executionUuid = generateUuid();

    StateMachineExecutionCallbackMock callback = new StateMachineExecutionCallbackMock();

    stateMachineExecutor.execute(sm, executionUuid, executionUuid, null, callback, null);
    callback.await();

    assertThat(StaticMap.getValue(stateA.getName())).isNotNull();
    assertThat(StaticMap.getValue(stateB.getName())).isNotNull();
    assertThat(StaticMap.getValue(stateC.getName())).isNotNull();

    assertThat((long) StaticMap.getValue(stateA.getName()) < (long) StaticMap.getValue(stateB.getName()))
        .as("StateA executed before StateB")
        .isEqualTo(true);
    assertThat((long) StaticMap.getValue(stateB.getName()) < (long) StaticMap.getValue(stateC.getName()))
        .as("StateB executed before StateC")
        .isEqualTo(true);
  }

  /**
   * Should trigger failed transition.
   *
   * @throws InterruptedException the interrupted exception
   */
  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldTriggerFailedTransition() throws InterruptedException {
    String appId = generateUuid();
    StateMachine sm = new StateMachine();
    sm.setAppId(appId);
    State stateA = new StateSync("stateA" + StaticMap.getUnique());
    sm.addState(stateA);
    StateSync stateB = new StateSync("stateB" + StaticMap.getUnique(), true);
    sm.addState(stateB);
    StateSync stateC = new StateSync("stateC" + StaticMap.getUnique());
    sm.addState(stateC);
    StateSync stateD = new StateSync("stateD" + StaticMap.getUnique());
    sm.addState(stateD);
    sm.setInitialStateName(stateA.getName());

    sm.addTransition(
        aTransition().withFromState(stateA).withTransitionType(TransitionType.SUCCESS).withToState(stateB).build());
    sm.addTransition(
        aTransition().withFromState(stateB).withTransitionType(TransitionType.SUCCESS).withToState(stateC).build());
    sm.addTransition(
        aTransition().withFromState(stateB).withTransitionType(TransitionType.FAILURE).withToState(stateD).build());

    sm = workflowService.createStateMachine(sm);
    assertThat(sm).isNotNull().extracting(StateMachine::getUuid).isNotNull();

    String smId = sm.getUuid();
    log.info("Going to trigger state machine");
    String executionUuid = generateUuid();

    StateMachineExecutionCallbackMock callback = new StateMachineExecutionCallbackMock();

    stateMachineExecutor.execute(sm, executionUuid, executionUuid, null, callback, null);
    callback.await();

    assertThat(StaticMap.getValue(stateA.getName())).isNotNull();
    assertThat(StaticMap.getValue(stateB.getName())).isNotNull();
    assertThat(StaticMap.getValue(stateC.getName())).isNull();
    assertThat(StaticMap.getValue(stateD.getName())).isNotNull();

    assertThat((long) StaticMap.getValue(stateA.getName()) < (long) StaticMap.getValue(stateB.getName()))
        .as("StateA executed before StateB")
        .isEqualTo(true);
    assertThat(StaticMap.getValue(stateC.getName())).isNull();
    assertThat((long) StaticMap.getValue(stateB.getName()) < (long) StaticMap.getValue(stateD.getName()))
        .as("StateB executed before StateD")
        .isEqualTo(true);
  }

  /**
   * Should trigger and fail.
   *
   * @throws InterruptedException the interrupted exception
   */
  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldTriggerAndFail() throws InterruptedException {
    String appId = generateUuid();
    StateMachine sm = new StateMachine();
    sm.setAppId(appId);
    State stateA = new StateSync("stateA" + StaticMap.getUnique());
    sm.addState(stateA);
    StateSync stateB = new StateSync("stateB" + StaticMap.getUnique(), true);
    sm.addState(stateB);
    StateSync stateC = new StateSync("stateC" + StaticMap.getUnique());
    sm.addState(stateC);
    StateSync stateD = new StateSync("stateD" + StaticMap.getUnique());
    sm.addState(stateD);
    sm.setInitialStateName(stateA.getName());

    sm.addTransition(
        aTransition().withFromState(stateA).withTransitionType(TransitionType.SUCCESS).withToState(stateB).build());
    sm.addTransition(
        aTransition().withFromState(stateB).withTransitionType(TransitionType.SUCCESS).withToState(stateC).build());
    sm.addTransition(
        aTransition().withFromState(stateC).withTransitionType(TransitionType.SUCCESS).withToState(stateD).build());

    sm = workflowService.createStateMachine(sm);
    assertThat(sm).isNotNull().extracting(StateMachine::getUuid).isNotNull();

    String smId = sm.getUuid();
    log.info("Going to trigger state machine");
    String executionUuid = generateUuid();

    StateMachineExecutionCallbackMock callback = new StateMachineExecutionCallbackMock();

    stateMachineExecutor.execute(sm, executionUuid, executionUuid, null, callback, null);
    callback.await();

    assertThat(StaticMap.getValue(stateA.getName())).isNotNull();
    assertThat(StaticMap.getValue(stateB.getName())).isNotNull();
    assertThat(StaticMap.getValue(stateC.getName())).isNull();
    assertThat(StaticMap.getValue(stateD.getName())).isNull();

    assertThat((long) StaticMap.getValue(stateA.getName()) < (long) StaticMap.getValue(stateB.getName()))
        .as("StateA executed before StateB")
        .isEqualTo(true);
    assertThat(StaticMap.getValue(stateC.getName())).isNull();
    assertThat(StaticMap.getValue(stateD.getName())).isNull();
  }

  /**
   * Should trigger asynch.
   *
   * @throws InterruptedException the interrupted exception
   */
  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTriggerAsync() throws InterruptedException {
    String appId = generateUuid();
    StateMachine sm = new StateMachine();
    sm.setAppId(appId);
    State stateA = new StateSync("stateA" + nextInt(0, 10000));
    sm.addState(stateA);
    StateSync stateB = new StateSync("stateB" + nextInt(0, 10000));
    sm.addState(stateB);
    StateSync stateC = new StateSync("stateC" + nextInt(0, 10000));
    sm.addState(stateC);

    State stateAB = new StateAsync("StateAB" + nextInt(0, 10000), 2000);
    sm.addState(stateAB);
    State stateBC = new StateAsync("StateBC" + nextInt(0, 10000), 1000);
    sm.addState(stateBC);

    sm.setInitialStateName(stateA.getName());

    sm.addTransition(
        aTransition().withFromState(stateA).withTransitionType(TransitionType.SUCCESS).withToState(stateAB).build());
    sm.addTransition(
        aTransition().withFromState(stateAB).withTransitionType(TransitionType.SUCCESS).withToState(stateB).build());
    sm.addTransition(
        aTransition().withFromState(stateB).withTransitionType(TransitionType.SUCCESS).withToState(stateBC).build());
    sm.addTransition(
        aTransition().withFromState(stateBC).withTransitionType(TransitionType.SUCCESS).withToState(stateC).build());

    sm = workflowService.createStateMachine(sm);
    assertThat(sm).isNotNull().extracting(StateMachine::getUuid).isNotNull();

    log.info("Going to trigger state machine");
    String executionUuid = createWorkflowExecution(sm);

    StateMachineExecutionCallbackMock callback = new StateMachineExecutionCallbackMock();
    stateMachineExecutor.execute(sm, executionUuid, executionUuid, null, callback, null);
    callback.await();

    assertThat((long) StaticMap.getValue(stateA.getName()) < (long) StaticMap.getValue(stateAB.getName()))
        .as("StateA executed before StateAB")
        .isEqualTo(true);
    assertThat((long) StaticMap.getValue(stateAB.getName()) < (long) StaticMap.getValue(stateB.getName()))
        .as("StateAB executed before StateB")
        .isEqualTo(true);
    assertThat((long) StaticMap.getValue(stateB.getName()) < (long) StaticMap.getValue(stateBC.getName()))
        .as("StateB executed before StateBC")
        .isEqualTo(true);
    assertThat((long) StaticMap.getValue(stateBC.getName()) < (long) StaticMap.getValue(stateC.getName()))
        .as("StateBC executed before StateC")
        .isEqualTo(true);
  }

  /**
   * Should trigger failed asynch.
   *
   * @throws InterruptedException the interrupted exception
   */
  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldTriggerFailedAsync() throws InterruptedException {
    String appId = generateUuid();
    StateMachine sm = new StateMachine();
    sm.setAppId(appId);
    State stateA = new StateSync("stateA" + StaticMap.getUnique());
    sm.addState(stateA);
    StateSync stateB = new StateSync("stateB" + StaticMap.getUnique());
    sm.addState(stateB);
    StateSync stateC = new StateSync("stateC" + StaticMap.getUnique());
    sm.addState(stateC);

    State stateAB = new StateAsync("StateAB" + StaticMap.getUnique(), 600, true);
    sm.addState(stateAB);

    sm.setInitialStateName(stateA.getName());

    sm.addTransition(
        aTransition().withFromState(stateA).withTransitionType(TransitionType.SUCCESS).withToState(stateAB).build());
    sm.addTransition(
        aTransition().withFromState(stateAB).withTransitionType(TransitionType.SUCCESS).withToState(stateB).build());
    sm.addTransition(
        aTransition().withFromState(stateAB).withTransitionType(TransitionType.FAILURE).withToState(stateC).build());

    sm = workflowService.createStateMachine(sm);
    assertThat(sm).isNotNull().extracting(StateMachine::getUuid).isNotNull();

    log.info("Going to trigger state machine");
    String executionUuid = createWorkflowExecution(sm);

    StateMachineExecutionCallbackMock callback = new StateMachineExecutionCallbackMock();
    stateMachineExecutor.execute(sm, executionUuid, executionUuid, null, callback, null);
    callback.await();

    assertThat(StaticMap.getValue(stateA.getName())).isNotNull();
    assertThat(StaticMap.getValue(stateAB.getName())).isNotNull();
    assertThat(StaticMap.getValue(stateB.getName())).isNull();
    assertThat(StaticMap.getValue(stateC.getName())).isNotNull();

    assertThat((long) StaticMap.getValue(stateA.getName()) < (long) StaticMap.getValue(stateAB.getName()))
        .as("StateA executed before StateAB")
        .isEqualTo(true);
    assertThat((long) StaticMap.getValue(stateAB.getName()) < (long) StaticMap.getValue(stateC.getName()))
        .as("StateAB executed before StateC")
        .isEqualTo(true);
  }

  private String createWorkflowExecution(StateMachine sm) {
    String executionUuid = generateUuid();

    WorkflowExecution workflowExecution = WorkflowExecution.builder().build();
    workflowExecution.setUuid(executionUuid);
    workflowExecution.setAppId(sm.getAppId());
    workflowExecution.setStateMachine(sm);
    wingsPersistence.save(workflowExecution);
    return executionUuid;
  }

  /**
   * Should mark success .
   *
   * @throws InterruptedException the interrupted exception
   */
  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldAdviceToMarkSuccess() throws InterruptedException {
    String appId = generateUuid();
    StateMachine sm = new StateMachine();
    sm.setAppId(appId);
    State stateA = new StateSync("stateA" + StaticMap.getUnique());
    sm.addState(stateA);
    StateSync stateB = new StateSync("stateB" + StaticMap.getUnique());
    sm.addState(stateB);
    StateSync stateC = new StateSync("stateC" + StaticMap.getUnique());
    sm.addState(stateC);

    State stateAB = new StateAsync("StateAB" + StaticMap.getUnique(), 100, true);
    sm.addState(stateAB);

    sm.setInitialStateName(stateA.getName());

    sm.addTransition(
        aTransition().withFromState(stateA).withTransitionType(TransitionType.SUCCESS).withToState(stateAB).build());
    sm.addTransition(
        aTransition().withFromState(stateAB).withTransitionType(TransitionType.SUCCESS).withToState(stateB).build());
    sm.addTransition(
        aTransition().withFromState(stateAB).withTransitionType(TransitionType.FAILURE).withToState(stateC).build());

    sm = workflowService.createStateMachine(sm);
    assertThat(sm).isNotNull().extracting(StateMachine::getUuid).isNotNull();

    log.info("Going to trigger state machine");
    String executionUuid = createWorkflowExecution(sm);

    StateMachineExecutionCallbackMock callback = new StateMachineExecutionCallbackMock();
    CustomExecutionEventAdvisor advisor = new CustomExecutionEventAdvisor(MARK_SUCCESS);
    stateMachineExecutor.execute(sm, executionUuid, executionUuid, null, callback, advisor);
    callback.await();

    assertThat(StaticMap.getValue(stateA.getName())).isNotNull();
    assertThat(StaticMap.getValue(stateAB.getName())).isNotNull();
    assertThat(StaticMap.getValue(stateB.getName())).isNotNull();
    assertThat(StaticMap.getValue(stateC.getName())).isNull();

    assertThat((long) StaticMap.getValue(stateA.getName()) < (long) StaticMap.getValue(stateAB.getName()))
        .as("StateA executed before StateAB")
        .isEqualTo(true);
    assertThat((long) StaticMap.getValue(stateAB.getName()) < (long) StaticMap.getValue(stateB.getName()))
        .as("StateAB executed before StateB")
        .isEqualTo(true);
  }

  /**
   * Should mark failed
   *
   * @throws InterruptedException the interrupted exception
   */
  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldAdviceToMarkFailed() throws InterruptedException {
    String appId = generateUuid();
    StateMachine sm = new StateMachine();
    sm.setAppId(appId);
    State stateA = new StateSync("stateA" + StaticMap.getUnique());
    sm.addState(stateA);
    StateSync stateB = new StateSync("stateB" + StaticMap.getUnique());
    sm.addState(stateB);
    StateSync stateC = new StateSync("stateC" + StaticMap.getUnique());
    sm.addState(stateC);

    State stateAB = new StateAsync("StateAB" + StaticMap.getUnique(), 100, true);
    sm.addState(stateAB);

    sm.setInitialStateName(stateA.getName());

    sm.addTransition(
        aTransition().withFromState(stateA).withTransitionType(TransitionType.SUCCESS).withToState(stateAB).build());
    sm.addTransition(
        aTransition().withFromState(stateAB).withTransitionType(TransitionType.SUCCESS).withToState(stateB).build());
    sm.addTransition(
        aTransition().withFromState(stateAB).withTransitionType(TransitionType.FAILURE).withToState(stateC).build());

    sm = workflowService.createStateMachine(sm);
    assertThat(sm).isNotNull().extracting(StateMachine::getUuid).isNotNull();

    log.info("Going to trigger state machine");
    String executionUuid = createWorkflowExecution(sm);

    StateMachineExecutionCallbackMock callback = new StateMachineExecutionCallbackMock();
    CustomExecutionEventAdvisor advisor = new CustomExecutionEventAdvisor(MARK_FAILED);
    stateMachineExecutor.execute(sm, executionUuid, executionUuid, null, callback, advisor);
    callback.await();

    assertThat(StaticMap.getValue(stateA.getName())).isNotNull();
    assertThat(StaticMap.getValue(stateAB.getName())).isNotNull();
    assertThat(StaticMap.getValue(stateB.getName())).isNull();
    assertThat(StaticMap.getValue(stateC.getName())).isNotNull();

    assertThat((long) StaticMap.getValue(stateA.getName()) < (long) StaticMap.getValue(stateAB.getName()))
        .as("StateA executed before StateAB")
        .isEqualTo(true);
    assertThat((long) StaticMap.getValue(stateAB.getName()) < (long) StaticMap.getValue(stateC.getName()))
        .as("StateAB executed before StateC")
        .isEqualTo(true);
  }

  /**
   * Should mark aborted
   *
   * @throws InterruptedException the interrupted exception
   */
  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldAdviceToMarkAborted() throws InterruptedException {
    String appId = generateUuid();
    StateMachine sm = new StateMachine();
    sm.setAppId(appId);
    State stateA = new StateSync("stateA" + StaticMap.getUnique());
    sm.addState(stateA);
    StateSync stateB = new StateSync("stateB" + StaticMap.getUnique());
    sm.addState(stateB);
    StateSync stateC = new StateSync("stateC" + StaticMap.getUnique());
    sm.addState(stateC);

    State stateAB = new StateAsync("StateAB" + StaticMap.getUnique(), 100, true);
    sm.addState(stateAB);

    sm.setInitialStateName(stateA.getName());

    sm.addTransition(
        aTransition().withFromState(stateA).withTransitionType(TransitionType.SUCCESS).withToState(stateAB).build());
    sm.addTransition(
        aTransition().withFromState(stateAB).withTransitionType(TransitionType.SUCCESS).withToState(stateB).build());
    sm.addTransition(
        aTransition().withFromState(stateAB).withTransitionType(TransitionType.FAILURE).withToState(stateC).build());

    sm = workflowService.createStateMachine(sm);
    assertThat(sm).isNotNull().extracting(StateMachine::getUuid).isNotNull();

    log.info("Going to trigger state machine");
    String executionUuid = createWorkflowExecution(sm);

    StateMachineExecutionCallbackMock callback = new StateMachineExecutionCallbackMock();
    CustomExecutionEventAdvisor advisor = new CustomExecutionEventAdvisor(ABORT);
    stateMachineExecutor.execute(sm, executionUuid, executionUuid, null, callback, advisor);
    callback.await();

    assertThat(StaticMap.getValue(stateA.getName())).isNotNull();
    assertThat(StaticMap.getValue(stateAB.getName())).isNotNull();
    assertThat(StaticMap.getValue(stateB.getName())).isNull();
    assertThat(StaticMap.getValue(stateC.getName())).isNull();

    assertThat((long) StaticMap.getValue(stateA.getName()) < (long) StaticMap.getValue(stateAB.getName()))
        .as("StateA executed before StateAB")
        .isEqualTo(true);
  }

  /**
   * Should trigger and fail asynch.
   *
   * @throws InterruptedException the interrupted exception
   */
  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldTriggerAndFailAsync() throws InterruptedException {
    String appId = generateUuid();
    StateMachine sm = new StateMachine();
    sm.setAppId(appId);
    State stateA = new StateSync("stateA" + StaticMap.getUnique());
    sm.addState(stateA);
    StateSync stateB = new StateSync("stateB" + StaticMap.getUnique());
    sm.addState(stateB);
    StateSync stateC = new StateSync("stateC" + StaticMap.getUnique());
    sm.addState(stateC);

    State stateAB = new StateAsync("StateAB" + StaticMap.getUnique(), 500, true);
    sm.addState(stateAB);

    sm.setInitialStateName(stateA.getName());

    sm.addTransition(
        aTransition().withFromState(stateA).withTransitionType(TransitionType.SUCCESS).withToState(stateAB).build());
    sm.addTransition(
        aTransition().withFromState(stateAB).withTransitionType(TransitionType.SUCCESS).withToState(stateB).build());
    sm.addTransition(
        aTransition().withFromState(stateB).withTransitionType(TransitionType.SUCCESS).withToState(stateC).build());

    sm = workflowService.createStateMachine(sm);
    assertThat(sm).isNotNull().extracting(StateMachine::getUuid).isNotNull();

    log.info("Going to trigger state machine");
    String executionUuid = createWorkflowExecution(sm);

    StateMachineExecutionCallbackMock callback = new StateMachineExecutionCallbackMock();
    stateMachineExecutor.execute(sm, executionUuid, executionUuid, null, callback, null);
    callback.await();

    assertThat(StaticMap.getValue(stateA.getName())).isNotNull();
    assertThat(StaticMap.getValue(stateAB.getName())).isNotNull();
    assertThat(StaticMap.getValue(stateB.getName())).isNull();
    assertThat(StaticMap.getValue(stateC.getName())).isNull();

    assertThat((long) StaticMap.getValue(stateA.getName()) < (long) StaticMap.getValue(stateAB.getName()))
        .as("StateA executed before StateAB")
        .isEqualTo(true);
  }

  /**
   * Should fail after exception
   *
   * @throws InterruptedException the interrupted exception
   */
  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldFailAfterException() throws InterruptedException {
    String appId = generateUuid();
    StateMachine sm = new StateMachine();
    sm.setAppId(appId);
    State stateA = new StateSync("stateA" + StaticMap.getUnique());
    sm.addState(stateA);
    StateSync stateB = new StateSync("stateB" + StaticMap.getUnique());
    sm.addState(stateB);
    StateSync stateC = new StateSync("stateC" + StaticMap.getUnique());
    sm.addState(stateC);

    State stateAB = new StateAsync("StateAB" + StaticMap.getUnique(), 500, false, true);
    sm.addState(stateAB);

    sm.setInitialStateName(stateA.getName());

    sm.addTransition(
        aTransition().withFromState(stateA).withTransitionType(TransitionType.SUCCESS).withToState(stateAB).build());
    sm.addTransition(
        aTransition().withFromState(stateAB).withTransitionType(TransitionType.SUCCESS).withToState(stateB).build());
    sm.addTransition(
        aTransition().withFromState(stateAB).withTransitionType(TransitionType.FAILURE).withToState(stateC).build());

    sm = workflowService.createStateMachine(sm);
    assertThat(sm).isNotNull().extracting(StateMachine::getUuid).isNotNull();

    log.info("Going to trigger state machine");
    String executionUuid = generateUuid();

    StateMachineExecutionCallbackMock callback = new StateMachineExecutionCallbackMock();
    stateMachineExecutor.execute(sm, executionUuid, executionUuid, null, callback, null);
    callback.await();

    assertThat(StaticMap.getValue(stateA.getName())).isNotNull();
    assertThat(StaticMap.getValue(stateAB.getName())).isNull();
    assertThat(StaticMap.getValue(stateB.getName())).isNull();
    assertThat(StaticMap.getValue(stateC.getName())).isNotNull();

    assertThat((long) StaticMap.getValue(stateA.getName()) < (long) StaticMap.getValue(stateC.getName()))
        .as("StateA executed before StateC")
        .isEqualTo(true);
  }

  /**
   * Should trigger simple fork.
   *
   * @throws InterruptedException the interrupted exception
   */
  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldTriggerSimpleFork() throws InterruptedException {
    String appId = generateUuid();
    StateMachine sm = new StateMachine();
    sm.setAppId(appId);
    State stateA = new StateSync("stateA" + StaticMap.getUnique());
    sm.addState(stateA);
    StateSync stateB = new StateSync("stateB" + StaticMap.getUnique());
    sm.addState(stateB);
    StateSync stateC = new StateSync("stateC" + StaticMap.getUnique());
    sm.addState(stateC);

    ForkState fork1 = new ForkState("fork1");
    List<String> forkStates = new ArrayList<>();
    forkStates.add(stateB.getName());
    forkStates.add(stateC.getName());
    fork1.setForkStateNames(forkStates);
    sm.addState(fork1);

    sm.setInitialStateName(stateA.getName());

    sm.addTransition(
        aTransition().withFromState(stateA).withTransitionType(TransitionType.SUCCESS).withToState(fork1).build());

    sm = workflowService.createStateMachine(sm);
    assertThat(sm).isNotNull().extracting(StateMachine::getUuid).isNotNull();

    log.info("Going to trigger state machine");
    String executionUuid = createWorkflowExecution(sm);

    StateMachineExecutionCallbackMock callback = new StateMachineExecutionCallbackMock();
    stateMachineExecutor.execute(sm, executionUuid, executionUuid, null, callback, null);
    callback.await();
  }

  /**
   * Should trigger mixed fork.
   *
   * @throws InterruptedException the interrupted exception
   */
  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldTriggerMixedFork() throws InterruptedException {
    String appId = generateUuid();
    StateMachine sm = new StateMachine();
    sm.setAppId(appId);
    State stateA = new StateSync("stateA" + StaticMap.getUnique());
    sm.addState(stateA);
    StateSync stateB = new StateSync("stateB" + StaticMap.getUnique());
    sm.addState(stateB);
    StateSync stateC = new StateSync("stateC" + StaticMap.getUnique());
    sm.addState(stateC);

    State stateAB = new StateAsync("StateAB", 1000);
    sm.addState(stateAB);
    State stateBC = new StateAsync("StateBC", 100);
    sm.addState(stateBC);

    ForkState fork1 = new ForkState("fork1");
    List<String> forkStates = new ArrayList<>();
    forkStates.add(stateB.getName());
    forkStates.add(stateBC.getName());
    fork1.setForkStateNames(forkStates);
    sm.addState(fork1);

    sm.setInitialStateName(stateA.getName());

    sm.addTransition(
        aTransition().withFromState(stateA).withTransitionType(TransitionType.SUCCESS).withToState(stateAB).build());
    sm.addTransition(
        aTransition().withFromState(stateAB).withTransitionType(TransitionType.SUCCESS).withToState(fork1).build());
    sm.addTransition(
        aTransition().withFromState(fork1).withTransitionType(TransitionType.SUCCESS).withToState(stateC).build());

    sm = workflowService.createStateMachine(sm);
    assertThat(sm).isNotNull().extracting(StateMachine::getUuid).isNotNull();

    String smId = sm.getUuid();
    log.info("Going to trigger state machine");
    String executionUuid = createWorkflowExecution(sm);

    StateMachineExecutionCallbackMock callback = new StateMachineExecutionCallbackMock();
    stateMachineExecutor.execute(sm, executionUuid, executionUuid, null, callback, null);
    callback.await();
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldCleanForRetry() {
    List<ContextElement> originalNotifyElements = asList(anInstanceElement().displayName("foo").build());

    String prevStateExecutionInstanceId = wingsPersistence.save(
        aStateExecutionInstance().appId("appId").displayName("state0").notifyElements(originalNotifyElements).build());

    HashMap<String, StateExecutionData> stateExecutionMap = new HashMap<>();
    stateExecutionMap.put("state0", aStateExecutionData().withStateName("state0").build());
    stateExecutionMap.put("state1", aStateExecutionData().withStateName("state1").build());

    List<ContextElement> notifyElements =
        asList(anInstanceElement().displayName("bar").build(), originalNotifyElements.get(0));

    StateExecutionInstance stateExecutionInstance = aStateExecutionInstance()
                                                        .appId("appId")
                                                        .displayName("state1")
                                                        .stateExecutionMap(stateExecutionMap)
                                                        .prevInstanceId(prevStateExecutionInstanceId)
                                                        .stateTimeout(60000L)
                                                        .status(FAILED)
                                                        .build();

    wingsPersistence.save(stateExecutionInstance);
    stateMachineExecutor.clearStateExecutionData(stateExecutionInstance, null);

    stateExecutionInstance = wingsPersistence.get(StateExecutionInstance.class, stateExecutionInstance.getUuid());

    // TODO: add more checks
    assertThat(stateExecutionInstance.getStatus()).isEqualTo(NEW);
    assertThat(stateExecutionInstance.getNotifyElements()).isEqualTo(originalNotifyElements);
    assertThat(stateExecutionInstance.isRetry()).isEqualTo(true);
    assertThat(stateExecutionInstance.getRetryCount()).isEqualTo(1);
    assertThat(stateExecutionInstance.isWaitingForManualIntervention()).isEqualTo(false);
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testSendManualInterventionNeededNotification() {
    long expiryTs = 1610998940219L;
    NotificationRule notificationRule = aNotificationRule()
                                            .withNotificationGroups(Arrays.asList(aNotificationGroup()
                                                                                      .withName(USER_NAME)
                                                                                      .withUuid(NOTIFICATION_GROUP_ID)
                                                                                      .withAccountId(ACCOUNT_ID)
                                                                                      .build()))
                                            .build();

    CanaryOrchestrationWorkflow canaryOrchestrationWorkflow =
        aCanaryOrchestrationWorkflow().withNotificationRules(singletonList(notificationRule)).build();

    when(context.getApp()).thenReturn(anApplication().accountId(ACCOUNT_ID).uuid(APP_ID).build());
    when(context.getWorkflowExecutionId()).thenReturn(PIPELINE_WORKFLOW_EXECUTION_ID);
    when(mockWorkflowService.readWorkflow(any(), any())).thenReturn(workflow);
    when(workflow.getOrchestrationWorkflow()).thenReturn(canaryOrchestrationWorkflow);
    when(workflowExecutionService.getExecutionDetails(
             eq(APP_ID), eq(PIPELINE_WORKFLOW_EXECUTION_ID), anyBoolean(), anyBoolean()))
        .thenReturn(WorkflowExecution.builder()
                        .triggeredBy(EmbeddedUser.builder().name(USER_NAME).uuid(USER_NAME).build())
                        .startTs(70L)
                        .build());

    Map<String, String> placeholders = new HashMap<>();
    when(notificationMessageResolver.getPlaceholderValues(
             any(), any(), any(Long.class), any(Long.class), any(), any(), any(), any(), any()))
        .thenReturn(placeholders);
    when(notificationMessageResolver.getFormattedExpiresTime(expiryTs))
        .thenReturn(format("%s at %s", dateFormat.format(new Date(expiryTs)), timeFormat.format(new Date(expiryTs))));
    when(workflowNotificationHelper.getArtifactsDetails(any(), any(), any(), any()))
        .thenReturn(WorkflowNotificationDetails.builder().build());

    injectStateMachineExecutor.sendManualInterventionNeededNotification(context, expiryTs);
    verify(notificationService).sendNotificationAsync(any(Notification.class), singletonList(any()));

    ArgumentCaptor<Notification> notificationArgumentCaptor = ArgumentCaptor.forClass(Notification.class);
    verify(notificationService)
        .sendNotificationAsync(notificationArgumentCaptor.capture(), notificationRuleArgumentCaptor.capture());
    Notification notification = notificationArgumentCaptor.getAllValues().get(0);
    assertThat(notification.getNotificationTemplateId()).isEqualTo(MANUAL_INTERVENTION_NEEDED_NOTIFICATION.name());
    assertThat(notification.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(notification.getNotificationTemplateVariables().get("EXPIRES_TS_SECS"))
        .isEqualTo(String.valueOf(expiryTs / 1000));
    assertThat(notification.getNotificationTemplateVariables().get("EXPIRES_DATE"))
        .isEqualTo(format("%s at %s", dateFormat.format(new Date(expiryTs)), timeFormat.format(new Date(expiryTs))));

    notificationRule = notificationRuleArgumentCaptor.getValue().get(0);
    assertThat(notificationRule.getNotificationGroups().get(0).getName()).isEqualTo(USER_NAME);
    assertThat(notificationRule.getNotificationGroups().get(0).getUuid()).isEqualTo(NOTIFICATION_GROUP_ID);
    assertThat(notificationRule.getNotificationGroups().get(0).getAccountId()).isEqualTo(ACCOUNT_ID);
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void testSendRuntimeinputsNeededNotification() {
    NotificationRule notificationRule = aNotificationRule()
                                            .withNotificationGroups(Arrays.asList(aNotificationGroup()
                                                                                      .withName(USER_NAME)
                                                                                      .withUuid(NOTIFICATION_GROUP_ID)
                                                                                      .withAccountId(ACCOUNT_ID)
                                                                                      .build()))
                                            .build();

    CanaryOrchestrationWorkflow canaryOrchestrationWorkflow =
        aCanaryOrchestrationWorkflow().withNotificationRules(singletonList(notificationRule)).build();

    when(context.getApp()).thenReturn(anApplication().accountId(ACCOUNT_ID).uuid(APP_ID).build());
    when(context.getWorkflowExecutionId()).thenReturn(PIPELINE_WORKFLOW_EXECUTION_ID);
    when(pipelineService.readPipeline(any(), any(), anyBoolean())).thenReturn(pipeline);
    when(workflow.getOrchestrationWorkflow()).thenReturn(canaryOrchestrationWorkflow);
    when(workflowExecutionService.getExecutionDetails(
             eq(APP_ID), eq(PIPELINE_WORKFLOW_EXECUTION_ID), anyBoolean(), anyBoolean()))
        .thenReturn(WorkflowExecution.builder()
                        .triggeredBy(EmbeddedUser.builder().name(USER_NAME).uuid(USER_NAME).build())
                        .startTs(70L)
                        .build());

    Map<String, String> placeholders = new HashMap<>();
    when(notificationMessageResolver.getPlaceholderValues(
             any(), any(), any(Long.class), any(Long.class), any(), any(), any(), any(), any()))
        .thenReturn(placeholders);
    when(workflowNotificationHelper.getArtifactsDetails(any(), any(), any(), any()))
        .thenReturn(WorkflowNotificationDetails.builder().build());
    when(workflowNotificationHelper.calculatePipelineDetailsPipelineExecution(any(), any(), any()))
        .thenReturn(WorkflowNotificationDetails.builder().build());
    when(workflowNotificationHelper.calculateApplicationDetails(any(), any(), any()))
        .thenReturn(WorkflowNotificationDetails.builder().build());

    injectStateMachineExecutor.sendRuntimeInputNeededNotification(
        context, executionEventAdvice, stateExecutionInstance);
    verify(notificationService).sendNotificationAsync(any(Notification.class), singletonList(any()));

    ArgumentCaptor<Notification> notificationArgumentCaptor = ArgumentCaptor.forClass(Notification.class);
    verify(notificationService)
        .sendNotificationAsync(notificationArgumentCaptor.capture(), notificationRuleArgumentCaptor.capture());
    Notification notification = notificationArgumentCaptor.getAllValues().get(0);
    assertThat(notification.getNotificationTemplateId()).isEqualTo(NEEDS_RUNTIME_INPUTS.name());
    assertThat(notification.getAccountId()).isEqualTo(ACCOUNT_ID);
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void testSendRuntimeinputsProvidedNotification() {
    NotificationRule notificationRule = aNotificationRule()
                                            .withNotificationGroups(Arrays.asList(aNotificationGroup()
                                                                                      .withName(USER_NAME)
                                                                                      .withUuid(NOTIFICATION_GROUP_ID)
                                                                                      .withAccountId(ACCOUNT_ID)
                                                                                      .build()))
                                            .build();

    CanaryOrchestrationWorkflow canaryOrchestrationWorkflow =
        aCanaryOrchestrationWorkflow().withNotificationRules(singletonList(notificationRule)).build();

    when(context.getApp()).thenReturn(anApplication().accountId(ACCOUNT_ID).uuid(APP_ID).build());
    when(context.getWorkflowExecutionId()).thenReturn(PIPELINE_WORKFLOW_EXECUTION_ID);
    when(pipelineService.readPipeline(any(), any(), anyBoolean())).thenReturn(pipeline);
    when(workflow.getOrchestrationWorkflow()).thenReturn(canaryOrchestrationWorkflow);
    when(workflowExecutionService.getExecutionDetails(
             eq(APP_ID), eq(PIPELINE_WORKFLOW_EXECUTION_ID), anyBoolean(), anyBoolean()))
        .thenReturn(WorkflowExecution.builder()
                        .triggeredBy(EmbeddedUser.builder().name(USER_NAME).uuid(USER_NAME).build())
                        .startTs(70L)
                        .build());

    Map<String, String> placeholders = new HashMap<>();
    when(notificationMessageResolver.getPlaceholderValues(
             any(), any(), any(Long.class), any(Long.class), any(), any(), any(), any(), any()))
        .thenReturn(placeholders);
    when(workflowNotificationHelper.getArtifactsDetails(any(), any(), any(), any()))
        .thenReturn(WorkflowNotificationDetails.builder().build());
    when(workflowNotificationHelper.calculatePipelineDetailsPipelineExecution(any(), any(), any()))
        .thenReturn(WorkflowNotificationDetails.builder().build());
    when(workflowNotificationHelper.calculateApplicationDetails(any(), any(), any()))
        .thenReturn(WorkflowNotificationDetails.builder().build());

    injectStateMachineExecutor.sendRuntimeInputsProvidedNotification(
        context, executionEventAdvice.getUserGroupIdsToNotify(), stateExecutionInstance);
    verify(notificationService).sendNotificationAsync(any(Notification.class), singletonList(any()));

    ArgumentCaptor<Notification> notificationArgumentCaptor = ArgumentCaptor.forClass(Notification.class);
    verify(notificationService)
        .sendNotificationAsync(notificationArgumentCaptor.capture(), notificationRuleArgumentCaptor.capture());
    Notification notification = notificationArgumentCaptor.getAllValues().get(0);
    assertThat(notification.getNotificationTemplateId()).isEqualTo(RUNTIME_INPUTS_PROVIDED.name());
    assertThat(notification.getAccountId()).isEqualTo(ACCOUNT_ID);
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void testSendPipelineNotification() {
    NotificationRule notificationRule = aNotificationRule()
                                            .withNotificationGroups(Arrays.asList(aNotificationGroup()
                                                                                      .withName(USER_NAME)
                                                                                      .withUuid(NOTIFICATION_GROUP_ID)
                                                                                      .withAccountId(ACCOUNT_ID)
                                                                                      .build()))
                                            .build();

    CanaryOrchestrationWorkflow canaryOrchestrationWorkflow =
        aCanaryOrchestrationWorkflow().withNotificationRules(singletonList(notificationRule)).build();

    when(context.getApp()).thenReturn(anApplication().accountId(ACCOUNT_ID).uuid(APP_ID).build());
    when(context.getWorkflowExecutionId()).thenReturn(PIPELINE_WORKFLOW_EXECUTION_ID);
    when(pipelineService.readPipeline(any(), any(), anyBoolean())).thenReturn(pipeline);
    when(workflow.getOrchestrationWorkflow()).thenReturn(canaryOrchestrationWorkflow);
    when(workflowExecutionService.getExecutionDetails(
             eq(APP_ID), eq(PIPELINE_WORKFLOW_EXECUTION_ID), anyBoolean(), anyBoolean()))
        .thenReturn(WorkflowExecution.builder()
                        .triggeredBy(EmbeddedUser.builder().name(USER_NAME).uuid(USER_NAME).build())
                        .startTs(70L)
                        .build());

    Map<String, String> placeholders = new HashMap<>();
    when(notificationMessageResolver.getPlaceholderValues(
             any(), any(), any(Long.class), any(Long.class), any(), any(), any(), any(), any()))
        .thenReturn(placeholders);
    when(workflowNotificationHelper.getArtifactsDetails(any(), any(), any(), any()))
        .thenReturn(WorkflowNotificationDetails.builder().build());
    when(workflowNotificationHelper.calculatePipelineDetailsPipelineExecution(any(), any(), any()))
        .thenReturn(WorkflowNotificationDetails.builder().build());
    when(workflowNotificationHelper.calculateApplicationDetails(any(), any(), any()))
        .thenReturn(WorkflowNotificationDetails.builder().build());

    injectStateMachineExecutor.sendPipelineNotification(
        context, executionEventAdvice.getUserGroupIdsToNotify(), stateExecutionInstance, SUCCESS);
    verify(notificationService).sendNotificationAsync(any(Notification.class), singletonList(any()));

    ArgumentCaptor<Notification> notificationArgumentCaptor = ArgumentCaptor.forClass(Notification.class);
    verify(notificationService)
        .sendNotificationAsync(notificationArgumentCaptor.capture(), notificationRuleArgumentCaptor.capture());
    Notification notification = notificationArgumentCaptor.getAllValues().get(0);
    assertThat(notification.getNotificationTemplateId()).isEqualTo(PIPELINE_NOTIFICATION.name());
    assertThat(notification.getAccountId()).isEqualTo(ACCOUNT_ID);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testSkipStateExecutionResponse() {
    String expr = "${env.name} == \"qa\"";
    String errorMsg = "skip error msg";
    ExecutionEventAdvice advice =
        anExecutionEventAdvice().withSkipState(true).withSkipExpression(expr).withSkipError(errorMsg).build();
    ExecutionResponse response = StateMachineExecutor.skipStateExecutionResponse(advice);
    assertThat(response).isNotNull();
    assertThat(response.getExecutionStatus()).isEqualTo(FAILED);
    assertThat(((SkipStateExecutionData) response.getStateExecutionData()).getSkipAssertionExpression()).isNull();

    advice.setSkipError(null);
    response = StateMachineExecutor.skipStateExecutionResponse(advice);
    assertThat(response).isNotNull();
    assertThat(response.getExecutionStatus()).isEqualTo(SKIPPED);
    assertThat(((SkipStateExecutionData) response.getStateExecutionData()).getSkipAssertionExpression())
        .isEqualTo(expr);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testUpdateExecutionInstanceTimeoutWhenTriggerExecution() {
    String appId = generateUuid();
    StateMachine sm = new StateMachine();
    sm.setAppId(appId);
    State stateA = new StateSync("stateA");
    stateA.setTimeoutMillis(10);
    sm.addState(stateA);
    sm.setInitialStateName(stateA.getName());
    sm = workflowService.createStateMachine(sm);
    StateExecutionInstance stateExecutionInstance =
        aStateExecutionInstance().appId(appId).appId("appId").displayName("state1").stateName("stateA").build();
    assertThat(stateExecutionInstance.getStateTimeout()).isEqualTo(null);
    stateMachineExecutor.triggerExecution(sm, stateExecutionInstance);
    StateExecutionInstance updatedStateExecutionInstance =
        wingsPersistence.get(StateExecutionInstance.class, stateExecutionInstance.getUuid());
    assertThat(updatedStateExecutionInstance.getStateTimeout()).isEqualTo(10);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testUpdateExecutionInstanceTimeoutWhenStartExecution() {
    String appId = generateUuid();
    StateMachine sm = new StateMachine();
    sm.setAppId(appId);
    State stateA = new StateSync("stateA");
    stateA.setTimeoutMillis(10);
    sm.addState(stateA);
    sm.setInitialStateName(stateA.getName());
    sm = workflowService.createStateMachine(sm);
    StateExecutionInstance stateExecutionInstance =
        aStateExecutionInstance().appId(appId).appId("appId").displayName("state1").stateName("stateA").build();

    assertThat(stateExecutionInstance.getStateTimeout()).isEqualTo(null);
    wingsPersistence.save(stateExecutionInstance);
    stateMachineExecutor.startExecution(sm, stateExecutionInstance);
    StateExecutionInstance updatedStateExecutionInstance =
        wingsPersistence.get(StateExecutionInstance.class, stateExecutionInstance.getUuid());
    assertThat(updatedStateExecutionInstance.getStateTimeout()).isEqualTo(10);
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void testUpdateExecutionInstanceWhenWaitingForIntervention() {
    when(context.getApp()).thenReturn(anApplication().accountId(ACCOUNT_ID).uuid(APP_ID).build());
    when(alertService.openAlert(any(), any(), any(), any())).thenReturn(null);
    when(context.getWorkflowExecutionId()).thenReturn(PIPELINE_WORKFLOW_EXECUTION_ID);
    when(pipelineService.readPipeline(any(), any(), anyBoolean())).thenReturn(pipeline);
    when(workflow.getOrchestrationWorkflow()).thenReturn(new CanaryOrchestrationWorkflow());
    when(workflowExecutionService.getExecutionDetails(
             eq(APP_ID), eq(PIPELINE_WORKFLOW_EXECUTION_ID), anyBoolean(), anyBoolean()))
        .thenReturn(WorkflowExecution.builder()
                        .triggeredBy(EmbeddedUser.builder().name(USER_NAME).uuid(USER_NAME).build())
                        .startTs(70L)
                        .build());
    when(mockWorkflowService.readWorkflow(any(), any())).thenReturn(workflow);
    when(notificationMessageResolver.getPlaceholderValues(
             any(), any(), any(Long.class), any(Long.class), any(), any(), any(), any(), any()))
        .thenReturn(emptyMap());
    when(workflowNotificationHelper.getArtifactsDetails(any(), any(), any(), any()))
        .thenReturn(WorkflowNotificationDetails.builder().build());

    String uuid = generateUuid();
    ExecutionEventAdvice executionEventAdvice = anExecutionEventAdvice()
                                                    .withTimeout(60000L)
                                                    .withActionAfterManualInterventionTimeout(END_EXECUTION)
                                                    .withExecutionInterruptType(WAITING_FOR_MANUAL_INTERVENTION)
                                                    .build();

    StateExecutionInstance initialStateExecutionInstance =
        aStateExecutionInstance().uuid(uuid).appId("appId").displayName("state1").stateName("stateA").build();
    wingsPersistence.save(initialStateExecutionInstance);
    ArgumentCaptor<WorkflowExecution> workflowExecutionArgumentCaptor =
        ArgumentCaptor.forClass(WorkflowExecution.class);
    WorkflowExecution workflowExecution =
        WorkflowExecution.builder().workflowType(WorkflowType.ORCHESTRATION).uuid("id1").build();
    when(workflowExecutionService.getWorkflowExecution(any(), any())).thenReturn(workflowExecution);
    StateExecutionInstance updatedStateExecutionInstance = stateMachineExecutor.handleExecutionEventAdvice(
        context, initialStateExecutionInstance, RUNNING, executionEventAdvice);
    // Change this to WF_Pause once that is merged.
    verify(workflowExecutionUpdate)
        .publish(workflowExecutionArgumentCaptor.capture(), any(), eq(EventType.PIPELINE_PAUSE));
    assertThat(workflowExecutionArgumentCaptor.getValue().getUuid().equals("id1"));
    assertThat(workflowExecutionArgumentCaptor.getValue().getWorkflowType().equals(WorkflowType.ORCHESTRATION));
    StateExecutionInstance persistedStateExecutionInstance =
        wingsPersistence.get(StateExecutionInstance.class, initialStateExecutionInstance.getUuid());
    assertThat(persistedStateExecutionInstance)
        .isEqualToIgnoringGivenFields(updatedStateExecutionInstance, "lastUpdatedAt");
    assertThat(persistedStateExecutionInstance.getStatus()).isEqualTo(WAITING);
    assertThat(persistedStateExecutionInstance.getExpiryTs()).isEqualTo(updatedStateExecutionInstance.getExpiryTs());
    assertThat(persistedStateExecutionInstance.getActionAfterManualInterventionTimeout()).isEqualTo(END_EXECUTION);
    wingsPersistence.delete(updatedStateExecutionInstance);
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldSkipDelayedStepIfRequired() {
    StateMachineExecutor executor = mock(StateMachineExecutor.class);
    State state = mock(State.class);
    when(executor.skipDelayedStepIfRequired(context, state)).thenCallRealMethod();
    ExecutionEvent executionEvent =
        ExecutionEvent.builder().failureTypes(EnumSet.noneOf(FailureType.class)).context(context).state(state).build();
    ExecutionEventAdvisor executionEventAdvisor = mock(ExecutionEventAdvisor.class);
    when(context.getStateExecutionInstance())
        .thenReturn(aStateExecutionInstance().executionEventAdvisors(singletonList(executionEventAdvisor)).build());
    when(executionEventAdvisor.onExecutionEvent(executionEvent))
        .thenReturn(anExecutionEventAdvice().withSkipState(true).build());
    executor.skipDelayedStepIfRequired(context, state);

    verify(executor).handleResponse(any(), any());
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldNotSkipDelayedStep() {
    StateMachineExecutor executor = mock(StateMachineExecutor.class);
    State state = mock(State.class);
    when(executor.skipDelayedStepIfRequired(context, state)).thenCallRealMethod();
    ExecutionEvent executionEvent =
        ExecutionEvent.builder().failureTypes(EnumSet.noneOf(FailureType.class)).context(context).state(state).build();
    ExecutionEventAdvisor executionEventAdvisor = mock(ExecutionEventAdvisor.class);
    when(context.getStateExecutionInstance())
        .thenReturn(aStateExecutionInstance().executionEventAdvisors(singletonList(executionEventAdvisor)).build());
    when(executionEventAdvisor.onExecutionEvent(executionEvent))
        .thenReturn(anExecutionEventAdvice().withSkipState(false).build());
    executor.skipDelayedStepIfRequired(context, state);

    verify(executor, never()).handleResponse(any(), any());
  }

  @Test
  @Owner(developers = RAFAEL)
  @Category(UnitTests.class)
  public void shouldCleanForRetryWhenStateExecutionMapIsEmpty() {
    List<ContextElement> originalNotifyElements = asList(anInstanceElement().displayName("foo").build());

    String prevStateExecutionInstanceId = wingsPersistence.save(
        aStateExecutionInstance().appId("appId").displayName("state0").notifyElements(originalNotifyElements).build());

    HashMap<String, StateExecutionData> stateExecutionMap = new HashMap<>();

    StateExecutionInstance stateExecutionInstance = aStateExecutionInstance()
                                                        .appId("appId")
                                                        .displayName("state1")
                                                        .status(STARTING)
                                                        .stateExecutionMap(stateExecutionMap)
                                                        .prevInstanceId(prevStateExecutionInstanceId)
                                                        .stateTimeout(60000L)
                                                        .build();

    wingsPersistence.save(stateExecutionInstance);
    stateMachineExecutor.clearStateExecutionData(stateExecutionInstance, null);

    stateExecutionInstance = wingsPersistence.get(StateExecutionInstance.class, stateExecutionInstance.getUuid());

    assertThat(stateExecutionInstance.getStatus()).isEqualTo(NEW);
    assertThat(stateExecutionInstance.isRetry()).isEqualTo(true);
    assertThat(stateExecutionInstance.getRetryCount()).isEqualTo(1);
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void testRollbackPreviousStagesAdvice() {
    EnvRollbackState envRollbackState = new EnvRollbackState("Rollback-stage 1");
    StateMachine sm = aStateMachine().addState(envRollbackState).build();
    StateExecutionInstance initialInstance = aStateExecutionInstance()
                                                 .uuid("uuid")
                                                 .stateType(ENV_ROLLBACK_STATE.getType())
                                                 .executionUuid("executionUuid")
                                                 .build();
    initialInstance.setUuid("uuid");
    ExecutionEventAdvice executionEventAdvice = anExecutionEventAdvice()
                                                    .withNextStateName(envRollbackState.getName())
                                                    .withExecutionInterruptType(ROLLBACK_PREVIOUS_STAGES_ON_PIPELINE)
                                                    .build();
    EnvStateExecutionData envStateExecutionData = anEnvStateExecutionData().build();

    doReturn(sm).when(context).getStateMachine();
    doReturn(initialInstance).when(context).getStateExecutionInstance();
    doReturn(envStateExecutionData).when(context).getStateExecutionData();

    StateExecutionInstance newInstance =
        stateMachineExecutor.handleExecutionEventAdvice(context, initialInstance, RUNNING, executionEventAdvice);
    assertThat(newInstance).isNotNull();
    assertThat(newInstance.getStateName()).isEqualTo(envRollbackState.getName());
    assertThat(newInstance.getStateType()).isEqualTo(ENV_ROLLBACK_STATE.getType());
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void testRollbackPreviousStagesAdvice_ShouldSkipExecutionWithRollbackInstances() {
    StateExecutionInstance rollbackInstance =
        aStateExecutionInstance().rollback(true).executionUuid("executionUuid").build();
    wingsPersistence.save(rollbackInstance);
    EnvRollbackState envRollbackState = new EnvRollbackState("Rollback-stage 1");
    EnvRollbackState envRollbackState2 = new EnvRollbackState("Rollback-stage 2");
    StateMachine sm = aStateMachine()
                          .addState(envRollbackState)
                          .addState(envRollbackState2)
                          .addTransition(aTransition()
                                             .withTransitionType(TransitionType.SUCCESS)
                                             .withToState(envRollbackState2)
                                             .withFromState(envRollbackState)
                                             .build())
                          .build();

    StateExecutionInstance initialInstance =
        aStateExecutionInstance().uuid("uuid").executionUuid("executionUuid").build();
    initialInstance.setUuid("uuid");
    ExecutionEventAdvice executionEventAdvice = anExecutionEventAdvice()
                                                    .withNextStateName(envRollbackState.getName())
                                                    .withExecutionInterruptType(ROLLBACK_PREVIOUS_STAGES_ON_PIPELINE)
                                                    .build();
    EnvStateExecutionData envStateExecutionData =
        anEnvStateExecutionData().withWorkflowExecutionId("executionUuid").build();

    doReturn(sm).when(context).getStateMachine();
    doReturn(initialInstance).when(context).getStateExecutionInstance();
    doReturn(envStateExecutionData).when(context).getStateExecutionData();

    StateExecutionInstance newInstance =
        stateMachineExecutor.handleExecutionEventAdvice(context, initialInstance, RUNNING, executionEventAdvice);
    assertThat(newInstance).isNotNull();
    assertThat(newInstance.getStateType()).isEqualTo(ENV_ROLLBACK_STATE.getType());
    assertThat(newInstance.getStateName()).isEqualTo(envRollbackState2.getName());
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void testRollbackPreviousStagesAdvice_withForkInstance() {
    StateExecutionInstance rollbackInstance =
        aStateExecutionInstance().rollback(true).stateType(PHASE.getType()).executionUuid("executionUuid1").build();
    StateExecutionInstance failedEnvInstance1 = aStateExecutionInstance()
                                                    .displayName("1")
                                                    .uuid("uuid1")
                                                    .stateName("stage 1")
                                                    .status(FAILED)
                                                    .stateType(ENV_STATE.getType())
                                                    .parentInstanceId("parentId")
                                                    .executionUuid("executionUuid1")
                                                    .build();
    StateExecutionInstance failedEnvInstance2 = aStateExecutionInstance()
                                                    .displayName("2")
                                                    .uuid("uuid2")
                                                    .status(FAILED)
                                                    .stateType(ENV_STATE.getType())
                                                    .stateName("stage 2")
                                                    .parentInstanceId("parentId")
                                                    .executionUuid("executionUuid2")
                                                    .build();
    EnvStateExecutionData envStateExecutionData1 =
        anEnvStateExecutionData().withWorkflowExecutionId("executionUuid1").build();
    EnvStateExecutionData envStateExecutionData2 =
        anEnvStateExecutionData().withWorkflowExecutionId("executionUuid2").build();
    Map<String, StateExecutionData> stateExecutionMap = new HashMap<>();
    stateExecutionMap.put("1", envStateExecutionData1);
    stateExecutionMap.put("2", envStateExecutionData2);
    failedEnvInstance1.setStateExecutionMap(stateExecutionMap);
    failedEnvInstance2.setStateExecutionMap(stateExecutionMap);
    wingsPersistence.save(rollbackInstance);
    wingsPersistence.save(failedEnvInstance1);
    wingsPersistence.save(failedEnvInstance2);

    EnvRollbackState envRollbackState = new EnvRollbackState("Rollback-stage 1");
    EnvRollbackState envRollbackState2 = new EnvRollbackState("Rollback-stage 2");
    ForkState forkState = new ForkState("Rollback-fork");
    forkState.addForkState(envRollbackState);
    forkState.addForkState(envRollbackState2);

    StateMachine sm =
        aStateMachine().addState(envRollbackState).addState(envRollbackState2).addState(forkState).build();

    StateExecutionInstance initialInstance =
        aStateExecutionInstance().uuid("parentId").executionUuid("executionUuid").build();
    ExecutionEventAdvice executionEventAdvice = anExecutionEventAdvice()
                                                    .withNextStateName(forkState.getName())
                                                    .withExecutionInterruptType(ROLLBACK_PREVIOUS_STAGES_ON_PIPELINE)
                                                    .build();
    ForkStateExecutionData forkStateExecutionData = mock(ForkStateExecutionData.class);

    doReturn(sm).when(context).getStateMachine();
    doReturn(initialInstance).when(context).getStateExecutionInstance();
    doReturn(forkStateExecutionData).when(context).getStateExecutionData();

    StateExecutionInstance newInstance =
        stateMachineExecutor.handleExecutionEventAdvice(context, initialInstance, RUNNING, executionEventAdvice);
    assertThat(newInstance).isNotNull();
    assertThat(forkState.getForkStateNames().size()).isEqualTo(1);
    assertThat(forkState.getForkStateNames().get(0)).isEqualTo(envRollbackState2.getName());
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldIgnoreMappingExceptionWhenGetStateForExecution() throws IllegalAccessException {
    final StateMachine stateMachine = mock(StateMachine.class);
    when(context.getStateMachine()).thenReturn(stateMachine);

    final State currentState = mock(State.class);
    when(stateMachine.getState(null, null)).thenReturn(currentState);

    final HashMap<String, Object> stateParams = new HashMap<>();
    stateParams.put("paramsA", "valueA");
    stateParams.put("paramsB", "valueB");
    when(stateExecutionInstance.getStateParams()).thenReturn(stateParams);

    // DEPENDENCY MANUALLY INJECTED TO AVOID SIDE EFFECTS ON OTHER TESTS. WHEN DECLARED AT CLASS LEVEL
    // AT LEAST 6 TEST CASES FAILED WITHOUT ADDITIONAL EXPLANATION.
    Injector injector = mock(Injector.class);
    final Field injectorField = ReflectionUtils.getFieldByName(StateMachineExecutor.class, "injector");
    ReflectionUtils.setObjectField(injectorField, stateMachineExecutor, injector);

    try (MockedStatic<MapperUtils> mapper = Mockito.mockStatic(MapperUtils.class)) {
      List<ErrorMessage> messages = Collections.singletonList(new ErrorMessage(""));
      mapper.when(() -> MapperUtils.mapObject(stateParams, currentState)).thenThrow(new MappingException(messages));

      final State result = stateMachineExecutor.getStateForExecution(context, stateExecutionInstance);

      verify(injector).injectMembers(currentState);
      assertThat(result).isNotNull();
    } finally {
      ReflectionUtils.setObjectField(injectorField, stateMachineExecutor, null);
    }
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldMapEntries() {
    when(featureFlagService.isNotEnabled(SPG_STATE_MACHINE_MAPPING_EXCEPTION_IGNORE, ACCOUNT_ID)).thenReturn(true);

    List<Map<String, String>> templateVariables = new ArrayList<>();
    templateVariables.add(Map.of("className", "software.wings.beans.Variable", "name", "BENDER_BRANCH_NAME",
        "description", "any-value", "value", "master"));
    templateVariables.add(
        Map.of("className", "software.wings.beans.Variable", "name", "SWITCH_CLOUD", "value", "true"));
    templateVariables.add(
        Map.of("className", "software.wings.beans.Variable", "name", "NO_VALUE", "description", "description-text"));

    final State target = new ShellScriptState("TestState");
    final Map<String, Object> source = new HashMap<>();
    source.put("templateVariables", templateVariables);
    source.put("parentId", generateUuid());
    source.put("rollback", true);

    stateMachineExecutor.mapEntries(source, target, ACCOUNT_ID);

    assertThat(target).isNotNull();
    assertThat(target.getParentId()).isEqualTo(source.get("parentId"));
    assertThat(target.isRollback()).isTrue();
    assertThat(target.getTemplateVariables()).hasSize(3);
    assertThat(target.getTemplateVariables().get(0).getName()).isEqualTo("BENDER_BRANCH_NAME");
    assertThat(target.getTemplateVariables().get(0).getValue()).isEqualTo("master");
    assertThat(target.getTemplateVariables().get(0).getDescription()).isEqualTo("any-value");
    assertThat(target.getTemplateVariables().get(1).getName()).isEqualTo("SWITCH_CLOUD");
    assertThat(target.getTemplateVariables().get(1).getValue()).isEqualTo("true");
    assertThat(target.getTemplateVariables().get(1).getDescription()).isEqualTo("");
    assertThat(target.getTemplateVariables().get(2).getName()).isEqualTo("NO_VALUE");
    assertThat(target.getTemplateVariables().get(2).getValue()).isEqualTo("");
    assertThat(target.getTemplateVariables().get(2).getDescription()).isEqualTo("description-text");
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldMapEntriesMissingValue() {
    when(featureFlagService.isNotEnabled(SPG_STATE_MACHINE_MAPPING_EXCEPTION_IGNORE, ACCOUNT_ID)).thenReturn(true);

    List<Map<String, String>> templateVariables = new ArrayList<>();
    templateVariables.add(Map.of("name", "BENDER_BRANCH_NAME", "value", "master"));
    templateVariables.add(Map.of("name", "NO_VALUE"));

    final State target = new ShellScriptState("TestState");
    final Map<String, Object> source = new HashMap<>();
    source.put("templateVariables", templateVariables);

    stateMachineExecutor.mapEntries(source, target, ACCOUNT_ID);

    assertThat(target).isNotNull();
    assertThat(target.getTemplateVariables()).hasSize(2);
    assertThat(target.getTemplateVariables().get(0).getName()).isEqualTo("BENDER_BRANCH_NAME");
    assertThat(target.getTemplateVariables().get(0).getValue()).isEqualTo("master");
    assertThat(target.getTemplateVariables().get(1).getName()).isEqualTo("NO_VALUE");
    assertThat(target.getTemplateVariables().get(1).getValue()).isEqualTo("");
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldMapEntriesMissingDescription() {
    when(featureFlagService.isNotEnabled(SPG_STATE_MACHINE_MAPPING_EXCEPTION_IGNORE, ACCOUNT_ID)).thenReturn(true);

    List<Map<String, String>> templateVariables = new ArrayList<>();
    templateVariables.add(Map.of("name", "BENDER_BRANCH_NAME", "description", "master"));
    templateVariables.add(Map.of("name", "MISSING"));

    final State target = new ShellScriptState("TestState");
    final Map<String, Object> source = new HashMap<>();
    source.put("templateVariables", templateVariables);

    stateMachineExecutor.mapEntries(source, target, ACCOUNT_ID);

    assertThat(target).isNotNull();
    assertThat(target.getTemplateVariables()).hasSize(2);
    assertThat(target.getTemplateVariables().get(0).getName()).isEqualTo("BENDER_BRANCH_NAME");
    assertThat(target.getTemplateVariables().get(0).getDescription()).isEqualTo("master");
    assertThat(target.getTemplateVariables().get(1).getName()).isEqualTo("MISSING");
    assertThat(target.getTemplateVariables().get(1).getDescription()).isEqualTo("");
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldMapEntriesThrowException() {
    when(featureFlagService.isNotEnabled(SPG_STATE_MACHINE_MAPPING_EXCEPTION_IGNORE, ACCOUNT_ID)).thenReturn(true);

    final State target = new ShellScriptState("TestState");
    final Map<String, Object> source = new HashMap<>();
    source.put("parentId", generateUuid());
    source.put("rollback", true);

    try (MockedStatic<MapperUtils> mapper = mockStatic(MapperUtils.class)) {
      List<ErrorMessage> messages = Collections.singletonList(new ErrorMessage(""));
      mapper.when(() -> MapperUtils.mapObject(Mockito.anyMap(), eq(target))).thenThrow(new MappingException(messages));

      assertThrows(MappingException.class, () -> stateMachineExecutor.mapEntries(source, target, ACCOUNT_ID));
    }
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldMapEntriesDoNotThrowException() {
    when(featureFlagService.isNotEnabled(SPG_STATE_MACHINE_MAPPING_EXCEPTION_IGNORE, ACCOUNT_ID)).thenReturn(false);

    final State target = new ShellScriptState("TestState");
    final Map<String, Object> source = new HashMap<>();
    source.put("parentId", generateUuid());
    source.put("rollback", true);

    try (MockedStatic<MapperUtils> mapper = mockStatic(MapperUtils.class)) {
      List<ErrorMessage> messages = Collections.singletonList(new ErrorMessage(""));
      mapper.when(() -> MapperUtils.mapObject(Mockito.anyMap(), eq(target))).thenThrow(new MappingException(messages));

      stateMachineExecutor.mapEntries(source, target, ACCOUNT_ID);
    }
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldSanitizeEntryNotRequiredField() {
    Map<String, Object> result = stateMachineExecutor.sanitizeEntry(Map.entry("fieldName", 1410));
    assertThat(result.size()).isEqualTo(1);
    assertThat(result.get("fieldName")).isEqualTo(1410);
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldSanitizeEntryTemplateVariables() {
    Map<String, Object> result = stateMachineExecutor.sanitizeEntry(Map.entry(TEMPLATE_VARIABLE_ENTRY, 1410));
    assertThat(result.size()).isEqualTo(1);
    assertThat(result.get(TEMPLATE_VARIABLE_ENTRY)).isEqualTo(1410);
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldSanitizeTemplateVariablesHandleClassCast() {
    final Map<String, Object> result =
        stateMachineExecutor.sanitizeTemplateVariables(Map.entry(TEMPLATE_VARIABLE_ENTRY, List.of("A", "B")));
    assertThat(result).isNotNull();
    assertThat(result).containsOnlyKeys(TEMPLATE_VARIABLE_ENTRY);
    assertThat(result.get(TEMPLATE_VARIABLE_ENTRY)).isInstanceOf(List.class);
    assertThat((List<String>) result.get(TEMPLATE_VARIABLE_ENTRY)).containsOnly("A", "B");
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldSanitizeTemplateVariablesHandleNullValue() {
    Map<String, Object> source = Collections.singletonMap(TEMPLATE_VARIABLE_ENTRY, null);
    final Map<String, Object> result =
        stateMachineExecutor.sanitizeTemplateVariables(source.entrySet().iterator().next());
    assertThat(result).isNotNull();
    assertThat(result).containsOnlyKeys(TEMPLATE_VARIABLE_ENTRY);
    assertThat(result.get(TEMPLATE_VARIABLE_ENTRY)).isNull();
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldSanitizeTemplateVariablesHandleNoDescriptionField() {
    List<Map<String, String>> templateVariables = new ArrayList<>();
    templateVariables.add(
        Map.of("className", "software.wings.beans.Variable", "name", "BENDER_BRANCH_NAME", "value", "master"));
    templateVariables.add(
        Map.of("className", "software.wings.beans.Variable", "name", "SWITCH_CLOUD", "value", "true"));

    final Map<String, Object> result =
        stateMachineExecutor.sanitizeTemplateVariables(Map.entry(TEMPLATE_VARIABLE_ENTRY, templateVariables));

    assertThat(result).isNotNull();
    assertThat(result).containsOnlyKeys(TEMPLATE_VARIABLE_ENTRY);
    assertThat(((List<Map<String, String>>) result.get(TEMPLATE_VARIABLE_ENTRY))
                   .stream()
                   .anyMatch(e -> e.containsKey(VARIABLE_DESCRIPTION_FIELD)))
        .isFalse();
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldSanitizeTemplateVariablesHandleNoValueField() {
    List<Map<String, String>> templateVariables = new ArrayList<>();
    templateVariables.add(
        Map.of("className", "software.wings.beans.Variable", "name", "BENDER_BRANCH_NAME", "description", "master"));
    templateVariables.add(
        Map.of("className", "software.wings.beans.Variable", "name", "SWITCH_CLOUD", "description", "true"));

    final Map<String, Object> result =
        stateMachineExecutor.sanitizeTemplateVariables(Map.entry(TEMPLATE_VARIABLE_ENTRY, templateVariables));

    assertThat(result).isNotNull();
    assertThat(result).containsOnlyKeys(TEMPLATE_VARIABLE_ENTRY);
    assertThat(((List<Map<String, String>>) result.get(TEMPLATE_VARIABLE_ENTRY))
                   .stream()
                   .anyMatch(e -> e.containsKey(VARIABLE_VALUE_FIELD)))
        .isFalse();
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldSanitizeTemplateVariablesHandleNotRequiredSanitization() {
    List<Map<String, String>> templateVariables = new ArrayList<>();
    templateVariables.add(Map.of("className", "software.wings.beans.Variable", "name", "BENDER_BRANCH_NAME"));
    templateVariables.add(Map.of("className", "software.wings.beans.Variable", "name", "SWITCH_CLOUD"));

    final Map<String, Object> result =
        stateMachineExecutor.sanitizeTemplateVariables(Map.entry(TEMPLATE_VARIABLE_ENTRY, templateVariables));

    assertThat(result).isNotNull();
    assertThat(result).containsOnlyKeys(TEMPLATE_VARIABLE_ENTRY);
    assertThat(((List<Map<String, String>>) result.get(TEMPLATE_VARIABLE_ENTRY))
                   .stream()
                   .anyMatch(e -> e.containsKey(VARIABLE_VALUE_FIELD) || e.containsKey(VARIABLE_DESCRIPTION_FIELD)))
        .isFalse();
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldSanitizeTemplateVariablesWhenAtLeastOneDescriptionFieldIsFound() {
    List<Map<String, String>> templateVariables = new ArrayList<>();
    templateVariables.add(Map.of("className", "software.wings.beans.Variable", "name", "BENDER_BRANCH_NAME",
        "description", "any-content", "value", "master"));
    templateVariables.add(
        Map.of("className", "software.wings.beans.Variable", "name", "SWITCH_CLOUD", "value", "true"));
    final Map<String, Object> result =
        stateMachineExecutor.sanitizeTemplateVariables(Map.entry(TEMPLATE_VARIABLE_ENTRY, templateVariables));
    assertThat(result).hasSize(1);
    assertThat(result.get(TEMPLATE_VARIABLE_ENTRY)).isInstanceOf(List.class);

    @SuppressWarnings("unchecked")
    final List<Map<String, String>> content = (List<Map<String, String>>) result.get(TEMPLATE_VARIABLE_ENTRY);
    assertThat(content).hasSize(2);
    //
    assertThat(content.get(0).get("className")).isEqualTo("software.wings.beans.Variable");
    assertThat(content.get(0).get("name")).isEqualTo("BENDER_BRANCH_NAME");
    assertThat(content.get(0).get("value")).isEqualTo("master");
    assertThat(content.get(0).get("description")).isEqualTo("any-content");
    //
    assertThat(content.get(1).get("className")).isEqualTo("software.wings.beans.Variable");
    assertThat(content.get(1).get("name")).isEqualTo("SWITCH_CLOUD");
    assertThat(content.get(1).get("value")).isEqualTo("true");
    assertThat(content.get(1).get("description")).isEqualTo("");
  }
}