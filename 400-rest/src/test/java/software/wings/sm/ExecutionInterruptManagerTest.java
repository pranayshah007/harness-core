/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ANUBHAW;
import static io.harness.rule.OwnerRule.GARVIT;
import static io.harness.rule.OwnerRule.GEORGE;
import static io.harness.rule.OwnerRule.MILOS;
import static io.harness.rule.OwnerRule.RAFAEL;

import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.beans.Environment.Builder.anEnvironment;
import static software.wings.sm.ExecutionInterrupt.ExecutionInterruptBuilder.anExecutionInterrupt;
import static software.wings.sm.StateExecutionInstance.Builder.aStateExecutionInstance;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.beans.ExecutionInterruptType;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.WorkflowType;
import io.harness.category.element.UnitTests;
import io.harness.eraro.ErrorCode;
import io.harness.exception.WingsException;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.Application;
import software.wings.beans.Environment;
import software.wings.beans.WorkflowExecution;
import software.wings.dl.WingsPersistence;
import software.wings.service.impl.workflow.WorkflowNotificationHelper;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.StateExecutionService;
import software.wings.sm.states.WorkflowState;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * The type State machine execution event manager test.
 */
@Slf4j
@RunWith(MockitoJUnitRunner.class)
public class ExecutionInterruptManagerTest extends WingsBaseTest {
  @Inject private WingsPersistence wingsPersistence;
  @Mock private AppService appService;
  @Spy @InjectMocks @Inject private ExecutionInterruptManager executionInterruptManager;
  @Mock private StateMachineExecutor stateMachineExecutor;
  @Mock private WorkflowNotificationHelper workflowNotificationHelper;
  @Mock private StateExecutionService stateExecutionService;
  @Mock WorkflowState workflowState;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  /**
   * Should throw invalid argument for null state execution instance.
   */
  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldThrowInvalidArgumentForNullStateExecutionInstance() {
    Application app = anApplication().name("App1").build();
    wingsPersistence.save(app);
    Environment env = anEnvironment().appId(app.getUuid()).build();
    wingsPersistence.save(env);
    WorkflowExecution workflowExecution = WorkflowExecution.builder().appId(app.getAppId()).build();
    wingsPersistence.save(workflowExecution);
    ExecutionInterrupt executionInterrupt = anExecutionInterrupt()
                                                .appId(app.getUuid())
                                                .executionInterruptType(ExecutionInterruptType.PAUSE)
                                                .envId(env.getUuid())
                                                .executionUuid(workflowExecution.getUuid())
                                                .build();
    try {
      executionInterrupt = executionInterruptManager.registerExecutionInterrupt(executionInterrupt);
      failBecauseExceptionWasNotThrown(WingsException.class);
    } catch (WingsException exception) {
      assertThat(exception.getParams().values()).doesNotContainNull();
      assertThat(exception.getParams().values().iterator().next())
          .isInstanceOf(String.class)
          .asString()
          .startsWith("null stateExecutionInstanceId");
      assertThat(exception).hasMessage(ErrorCode.INVALID_ARGUMENT.name());
    }
  }

  /**
   * Should throw invalid argument for invalid state execution instance.
   */
  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldThrowInvalidArgumentForInvalidStateExecutionInstance() {
    Application app = anApplication().name("App1").build();
    wingsPersistence.save(app);
    Environment env = anEnvironment().appId(app.getUuid()).build();
    wingsPersistence.save(env);

    ExecutionInterrupt executionInterrupt = anExecutionInterrupt()
                                                .appId(app.getUuid())
                                                .executionInterruptType(ExecutionInterruptType.PAUSE)
                                                .envId(env.getUuid())
                                                .executionUuid(generateUuid())
                                                .stateExecutionInstanceId(generateUuid())
                                                .build();
    try {
      executionInterrupt = executionInterruptManager.registerExecutionInterrupt(executionInterrupt);
      failBecauseExceptionWasNotThrown(WingsException.class);
    } catch (WingsException exception) {
      assertThat(exception.getParams().values()).doesNotContainNull();
      assertThat(exception.getParams().values().iterator().next())
          .isInstanceOf(String.class)
          .asString()
          .startsWith("invalid stateExecutionInstanceId");
      assertThat(exception).hasMessage(ErrorCode.INVALID_ARGUMENT.name());
    }
  }

  /**
   * Should throw state not for resume.
   */
  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldThrowStateNotForResume() {
    Application app = anApplication().name("App1").build();
    wingsPersistence.save(app);
    Environment env = anEnvironment().appId(app.getUuid()).build();
    wingsPersistence.save(env);

    StateExecutionInstance stateExecutionInstance =
        aStateExecutionInstance().appId(app.getUuid()).displayName("state1").build();
    wingsPersistence.save(stateExecutionInstance);
    ExecutionInterrupt executionInterrupt = anExecutionInterrupt()
                                                .appId(app.getUuid())
                                                .executionInterruptType(ExecutionInterruptType.RESUME)
                                                .envId(env.getUuid())
                                                .executionUuid(generateUuid())
                                                .stateExecutionInstanceId(stateExecutionInstance.getUuid())
                                                .build();
    try {
      executionInterrupt = executionInterruptManager.registerExecutionInterrupt(executionInterrupt);
      failBecauseExceptionWasNotThrown(WingsException.class);
    } catch (WingsException exception) {
      assertThat(exception).hasMessage(ErrorCode.STATE_NOT_FOR_TYPE.name());
    }
  }

  /**
   * Should throw state not for retry.
   */
  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldThrowStateNotForRetry() {
    Application app = anApplication().name("App1").build();
    wingsPersistence.save(app);
    Environment env = anEnvironment().appId(app.getUuid()).build();
    wingsPersistence.save(env);

    StateExecutionInstance stateExecutionInstance =
        aStateExecutionInstance().appId(app.getUuid()).displayName("state1").build();
    wingsPersistence.save(stateExecutionInstance);
    ExecutionInterrupt executionInterrupt = anExecutionInterrupt()
                                                .appId(app.getUuid())
                                                .executionInterruptType(ExecutionInterruptType.RETRY)
                                                .envId(env.getUuid())
                                                .executionUuid(generateUuid())
                                                .stateExecutionInstanceId(stateExecutionInstance.getUuid())
                                                .build();
    try {
      executionInterrupt = executionInterruptManager.registerExecutionInterrupt(executionInterrupt);
      failBecauseExceptionWasNotThrown(WingsException.class);
    } catch (WingsException exception) {
      assertThat(exception).hasMessage(ErrorCode.STATE_NOT_FOR_TYPE.name());
    }
  }

  /**
   * Should throw state not for pause.
   */
  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldThrowStateNotForPause() {
    Application app = anApplication().name("App1").build();
    wingsPersistence.save(app);
    Environment env = anEnvironment().appId(app.getUuid()).build();
    wingsPersistence.save(env);

    StateExecutionInstance stateExecutionInstance =
        aStateExecutionInstance().appId(app.getUuid()).displayName("state1").status(ExecutionStatus.SUCCESS).build();
    wingsPersistence.save(stateExecutionInstance);
    ExecutionInterrupt executionInterrupt = anExecutionInterrupt()
                                                .appId(app.getUuid())
                                                .executionInterruptType(ExecutionInterruptType.PAUSE)
                                                .envId(env.getUuid())
                                                .executionUuid(generateUuid())
                                                .stateExecutionInstanceId(stateExecutionInstance.getUuid())
                                                .build();
    try {
      executionInterrupt = executionInterruptManager.registerExecutionInterrupt(executionInterrupt);
      failBecauseExceptionWasNotThrown(WingsException.class);
    } catch (WingsException exception) {
      assertThat(exception).hasMessage(ErrorCode.STATE_NOT_FOR_TYPE.name());
    }
  }

  /**
   * Should throw state not for abort.
   */
  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldThrowStateNotForAbort() {
    Application app = anApplication().name("App1").build();
    wingsPersistence.save(app);
    Environment env = anEnvironment().appId(app.getUuid()).build();
    wingsPersistence.save(env);

    StateExecutionInstance stateExecutionInstance =
        aStateExecutionInstance().appId(app.getUuid()).displayName("state1").status(ExecutionStatus.SUCCESS).build();
    wingsPersistence.save(stateExecutionInstance);
    ExecutionInterrupt executionInterrupt = anExecutionInterrupt()
                                                .appId(app.getUuid())
                                                .executionInterruptType(ExecutionInterruptType.ABORT)
                                                .envId(env.getUuid())
                                                .executionUuid(generateUuid())
                                                .stateExecutionInstanceId(stateExecutionInstance.getUuid())
                                                .build();
    try {
      executionInterrupt = executionInterruptManager.registerExecutionInterrupt(executionInterrupt);
      failBecauseExceptionWasNotThrown(WingsException.class);
    } catch (WingsException exception) {
      assertThat(exception).hasMessage(ErrorCode.STATE_NOT_FOR_TYPE.name());
    }
  }

  /**
   * Should throw abort all already.
   */
  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  @Ignore("TODO: please provide clear motivation why this test is ignored")
  public void shouldThrowAbortAllAlready() {
    Application app = anApplication().name("App1").build();
    wingsPersistence.save(app);
    Environment env = anEnvironment().appId(app.getUuid()).build();
    wingsPersistence.save(env);

    String executionUuid = generateUuid();
    ExecutionInterrupt executionInterrupt = anExecutionInterrupt()
                                                .appId(app.getUuid())
                                                .executionInterruptType(ExecutionInterruptType.ABORT_ALL)
                                                .envId(env.getUuid())
                                                .executionUuid(executionUuid)
                                                .build();
    executionInterrupt = executionInterruptManager.registerExecutionInterrupt(executionInterrupt);

    executionInterrupt = anExecutionInterrupt()
                             .appId(app.getUuid())
                             .executionInterruptType(ExecutionInterruptType.PAUSE_ALL)
                             .envId(env.getUuid())
                             .executionUuid(executionUuid)
                             .build();
    try {
      executionInterrupt = executionInterruptManager.registerExecutionInterrupt(executionInterrupt);
      failBecauseExceptionWasNotThrown(WingsException.class);
    } catch (WingsException exception) {
      assertThat(exception).hasMessage(ErrorCode.ABORT_ALL_ALREADY.name());
    }
  }

  /**
   * Should throw pause all already.
   */
  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldThrowPauseAllAlready() {
    Application app = anApplication().name("App1").build();
    wingsPersistence.save(app);
    Environment env = anEnvironment().appId(app.getUuid()).build();
    wingsPersistence.save(env);

    WorkflowExecution workflowExecution = WorkflowExecution.builder().appId(app.getAppId()).build();
    wingsPersistence.save(workflowExecution);

    ExecutionInterrupt executionInterrupt = anExecutionInterrupt()
                                                .appId(app.getUuid())
                                                .executionInterruptType(ExecutionInterruptType.PAUSE_ALL)
                                                .envId(env.getUuid())
                                                .executionUuid(workflowExecution.getUuid())
                                                .build();
    executionInterrupt = executionInterruptManager.registerExecutionInterrupt(executionInterrupt);

    executionInterrupt = anExecutionInterrupt()
                             .appId(app.getUuid())
                             .executionInterruptType(ExecutionInterruptType.PAUSE_ALL)
                             .envId(env.getUuid())
                             .executionUuid(workflowExecution.getUuid())
                             .build();
    try {
      executionInterrupt = executionInterruptManager.registerExecutionInterrupt(executionInterrupt);
      failBecauseExceptionWasNotThrown(WingsException.class);
    } catch (WingsException exception) {
      assertThat(exception).hasMessage(ErrorCode.PAUSE_ALL_ALREADY.name());
    }
  }

  /**
   * Should pause all clear previous resume all.
   */
  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldPauseAllClearPreviousResumeAll() {
    Application app = anApplication().name("App1").build();
    wingsPersistence.save(app);
    Environment env = anEnvironment().appId(app.getUuid()).build();
    wingsPersistence.save(env);

    WorkflowExecution workflowExecution =
        WorkflowExecution.builder().appId(app.getAppId()).workflowType(WorkflowType.ORCHESTRATION).build();
    wingsPersistence.save(workflowExecution);
    StateExecutionInstance stateExecutionInstance = aStateExecutionInstance()
                                                        .appId(app.getAppId())
                                                        .executionUuid(workflowExecution.getUuid())
                                                        .createdAt(workflowExecution.getCreatedAt())
                                                        .build();
    wingsPersistence.save(stateExecutionInstance);

    doNothing().when(workflowNotificationHelper).sendWorkflowStatusChangeNotification(any(), any());

    ExecutionInterrupt executionInterrupt = anExecutionInterrupt()
                                                .appId(app.getUuid())
                                                .executionInterruptType(ExecutionInterruptType.PAUSE_ALL)
                                                .envId(env.getUuid())
                                                .executionUuid(workflowExecution.getUuid())
                                                .build();
    executionInterruptManager.registerExecutionInterrupt(executionInterrupt);

    ExecutionInterrupt resumeAll = anExecutionInterrupt()
                                       .appId(app.getUuid())
                                       .executionInterruptType(ExecutionInterruptType.RESUME_ALL)
                                       .envId(env.getUuid())
                                       .executionUuid(workflowExecution.getUuid())
                                       .build();
    resumeAll = executionInterruptManager.registerExecutionInterrupt(resumeAll);

    executionInterrupt = anExecutionInterrupt()
                             .appId(app.getUuid())
                             .executionInterruptType(ExecutionInterruptType.PAUSE_ALL)
                             .envId(env.getUuid())
                             .executionUuid(workflowExecution.getUuid())
                             .build();
    executionInterruptManager.registerExecutionInterrupt(executionInterrupt);
    final ExecutionInterrupt interrupt =
        wingsPersistence.getWithAppId(ExecutionInterrupt.class, resumeAll.getAppId(), resumeAll.getUuid());
    assertThat(interrupt.isSeized()).isTrue();
  }

  /**
   * Should throw resume all already.
   */
  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldThrowResumeAllAlready() {
    Application app = anApplication().name("App1").build();
    wingsPersistence.save(app);
    Environment env = anEnvironment().appId(app.getUuid()).build();
    wingsPersistence.save(env);

    String executionUuid = generateUuid();
    ExecutionInterrupt executionInterrupt = anExecutionInterrupt()
                                                .appId(app.getUuid())
                                                .executionInterruptType(ExecutionInterruptType.RESUME_ALL)
                                                .envId(env.getUuid())
                                                .executionUuid(executionUuid)
                                                .build();
    try {
      executionInterrupt = executionInterruptManager.registerExecutionInterrupt(executionInterrupt);
      failBecauseExceptionWasNotThrown(WingsException.class);
    } catch (WingsException exception) {
      assertThat(exception).hasMessage(ErrorCode.RESUME_ALL_ALREADY.name());
    }
  }

  /**
   * Should resume all clear prev pause all.
   */
  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldResumeAllClearPrevPauseAll() {
    Application app = anApplication().name("App1").build();
    wingsPersistence.save(app);
    Environment env = anEnvironment().appId(app.getUuid()).build();
    wingsPersistence.save(env);

    WorkflowExecution workflowExecution =
        WorkflowExecution.builder().appId(app.getAppId()).workflowType(WorkflowType.ORCHESTRATION).build();
    wingsPersistence.save(workflowExecution);
    StateExecutionInstance stateExecutionInstance = aStateExecutionInstance()
                                                        .appId(app.getAppId())
                                                        .executionUuid(workflowExecution.getUuid())
                                                        .createdAt(workflowExecution.getCreatedAt())
                                                        .build();
    wingsPersistence.save(stateExecutionInstance);

    ExecutionInterrupt pauseAll = anExecutionInterrupt()
                                      .appId(app.getUuid())
                                      .executionInterruptType(ExecutionInterruptType.PAUSE_ALL)
                                      .envId(env.getUuid())
                                      .executionUuid(workflowExecution.getUuid())
                                      .build();
    pauseAll = executionInterruptManager.registerExecutionInterrupt(pauseAll);

    ExecutionInterrupt executionInterrupt = anExecutionInterrupt()
                                                .appId(app.getUuid())
                                                .executionInterruptType(ExecutionInterruptType.RESUME_ALL)
                                                .envId(env.getUuid())
                                                .executionUuid(workflowExecution.getUuid())
                                                .build();
    executionInterruptManager.registerExecutionInterrupt(executionInterrupt);

    final ExecutionInterrupt interrupt =
        wingsPersistence.getWithAppId(ExecutionInterrupt.class, pauseAll.getAppId(), pauseAll.getUuid());
    assertThat(interrupt.isSeized()).isTrue();
  }

  /**
   * Should throw resume all already 2.
   */
  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldThrowResumeAllAlready2() {
    Application app = anApplication().name("App1").build();
    wingsPersistence.save(app);
    Environment env = anEnvironment().appId(app.getUuid()).build();
    wingsPersistence.save(env);

    WorkflowExecution workflowExecution = WorkflowExecution.builder().appId(app.getAppId()).build();
    wingsPersistence.save(workflowExecution);

    ExecutionInterrupt pauseAll = anExecutionInterrupt()
                                      .appId(app.getUuid())
                                      .executionInterruptType(ExecutionInterruptType.PAUSE_ALL)
                                      .envId(env.getUuid())
                                      .executionUuid(workflowExecution.getUuid())
                                      .build();
    pauseAll = executionInterruptManager.registerExecutionInterrupt(pauseAll);

    ExecutionInterrupt executionInterrupt = anExecutionInterrupt()
                                                .appId(app.getUuid())
                                                .executionInterruptType(ExecutionInterruptType.RESUME_ALL)
                                                .envId(env.getUuid())
                                                .executionUuid(workflowExecution.getUuid())
                                                .build();
    executionInterrupt = executionInterruptManager.registerExecutionInterrupt(executionInterrupt);

    executionInterrupt = anExecutionInterrupt()
                             .appId(app.getUuid())
                             .executionInterruptType(ExecutionInterruptType.RESUME_ALL)
                             .envId(env.getUuid())
                             .executionUuid(workflowExecution.getUuid())
                             .build();
    try {
      executionInterrupt = executionInterruptManager.registerExecutionInterrupt(executionInterrupt);
      failBecauseExceptionWasNotThrown(WingsException.class);
    } catch (WingsException exception) {
      assertThat(exception).hasMessage(ErrorCode.RESUME_ALL_ALREADY.name());
    }
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testListByIdsUsingSecondary() {
    List<ExecutionInterrupt> executionInterrupts =
        executionInterruptManager.listByIdsUsingSecondary(Collections.emptyList());
    assertThat(executionInterrupts).isNotNull();
    assertThat(executionInterrupts).isEmpty();

    ExecutionInterrupt executionInterrupt = anExecutionInterrupt().executionUuid("id").build();
    String id = wingsPersistence.save(executionInterrupt);

    executionInterrupts = executionInterruptManager.listByIdsUsingSecondary(asList(id, "random"));
    assertThat(executionInterrupts).isNotNull();
    assertThat(executionInterrupts.size()).isEqualTo(1);
    assertThat(executionInterrupts.get(0).getUuid()).isEqualTo(id);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testListByStateExecutionIdsUsingSecondary() {
    List<ExecutionInterrupt> executionInterrupts =
        executionInterruptManager.listByStateExecutionIdsUsingSecondary(Collections.emptyList());
    assertThat(executionInterrupts).isNotNull();
    assertThat(executionInterrupts).isEmpty();

    ExecutionInterrupt executionInterrupt =
        anExecutionInterrupt().stateExecutionInstanceId("seid").executionUuid("id").build();
    String id = wingsPersistence.save(executionInterrupt);

    executionInterrupts = executionInterruptManager.listByStateExecutionIdsUsingSecondary(asList("seid", "random"));
    assertThat(executionInterrupts).isNotNull();
    assertThat(executionInterrupts.size()).isEqualTo(1);
    assertThat(executionInterrupts.get(0).getUuid()).isEqualTo(id);
  }

  /**
   * Tries to resume all clear prev pause all without StateExecutionInstance
   */
  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void resumeAllWithoutStateExecutionInstanceTest() {
    Application app = anApplication().name("App1").build();
    wingsPersistence.save(app);
    Environment env = anEnvironment().appId(app.getUuid()).build();
    wingsPersistence.save(env);

    WorkflowExecution workflowExecution =
        WorkflowExecution.builder().appId(app.getAppId()).workflowType(WorkflowType.ORCHESTRATION).build();
    wingsPersistence.save(workflowExecution);

    ExecutionInterrupt pauseAll = anExecutionInterrupt()
                                      .appId(app.getUuid())
                                      .executionInterruptType(ExecutionInterruptType.PAUSE_ALL)
                                      .envId(env.getUuid())
                                      .executionUuid(workflowExecution.getUuid())
                                      .build();
    pauseAll = executionInterruptManager.registerExecutionInterrupt(pauseAll);

    ExecutionInterrupt executionInterrupt = anExecutionInterrupt()
                                                .appId(app.getUuid())
                                                .executionInterruptType(ExecutionInterruptType.RESUME_ALL)
                                                .envId(env.getUuid())
                                                .executionUuid(workflowExecution.getUuid())
                                                .build();
    executionInterruptManager.registerExecutionInterrupt(executionInterrupt);

    final ExecutionInterrupt interrupt =
        wingsPersistence.getWithAppId(ExecutionInterrupt.class, pauseAll.getAppId(), pauseAll.getUuid());
    assertThat(interrupt.isSeized()).isTrue();
  }

  /**
   * Should mark expired prev pause all.
   */
  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void markExpiredPrevPauseAllTest() {
    Application app = anApplication().name("App1").build();
    wingsPersistence.save(app);
    Environment env = anEnvironment().appId(app.getUuid()).build();
    wingsPersistence.save(env);

    WorkflowExecution workflowExecution =
        WorkflowExecution.builder().appId(app.getAppId()).workflowType(WorkflowType.PIPELINE).build();
    wingsPersistence.save(workflowExecution);
    StateExecutionInstance stateExecutionInstance = aStateExecutionInstance()
                                                        .appId(app.getAppId())
                                                        .executionUuid(workflowExecution.getUuid())
                                                        .status(ExecutionStatus.PAUSED)
                                                        .createdAt(workflowExecution.getCreatedAt())
                                                        .build();
    wingsPersistence.save(stateExecutionInstance);

    doReturn(workflowState).when(executionInterruptManager).getWorkflowState(any(), any());
    doNothing().when(stateMachineExecutor).sendPipelineNotification(any(), any(), any(), any());

    ExecutionInterrupt pauseAll = anExecutionInterrupt()
                                      .appId(app.getUuid())
                                      .executionInterruptType(ExecutionInterruptType.PAUSE_ALL)
                                      .envId(env.getUuid())
                                      .executionUuid(workflowExecution.getUuid())
                                      .build();
    executionInterruptManager.registerExecutionInterrupt(pauseAll);

    ExecutionInterrupt executionInterrupt = anExecutionInterrupt()
                                                .appId(app.getUuid())
                                                .executionInterruptType(ExecutionInterruptType.MARK_EXPIRED)
                                                .envId(env.getUuid())
                                                .stateExecutionInstanceId(stateExecutionInstance.getUuid())
                                                .executionUuid(workflowExecution.getUuid())
                                                .build();
    executionInterruptManager.registerExecutionInterrupt(executionInterrupt);

    verify(stateMachineExecutor, times(1)).sendPipelineNotification(any(), any(), any(), any());
  }

  /**
   * Should abort previously paused all.
   */
  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void abortPrevPauseAllTest() {
    Application app = anApplication().name("App1").build();
    wingsPersistence.save(app);
    Environment env = anEnvironment().appId(app.getUuid()).build();
    wingsPersistence.save(env);

    WorkflowExecution workflowExecution =
        WorkflowExecution.builder().appId(app.getAppId()).workflowType(WorkflowType.PIPELINE).build();
    wingsPersistence.save(workflowExecution);
    StateExecutionInstance stateExecutionInstance = aStateExecutionInstance()
                                                        .appId(app.getAppId())
                                                        .executionUuid(workflowExecution.getUuid())
                                                        .status(ExecutionStatus.PAUSED)
                                                        .createdAt(workflowExecution.getCreatedAt())
                                                        .build();
    wingsPersistence.save(stateExecutionInstance);

    doReturn(workflowState).when(executionInterruptManager).getWorkflowState(any(), any());
    doNothing().when(stateMachineExecutor).sendPipelineNotification(any(), any(), any(), any());

    ExecutionInterrupt pauseAll = anExecutionInterrupt()
                                      .appId(app.getUuid())
                                      .executionInterruptType(ExecutionInterruptType.PAUSE_ALL)
                                      .envId(env.getUuid())
                                      .executionUuid(workflowExecution.getUuid())
                                      .build();
    executionInterruptManager.registerExecutionInterrupt(pauseAll);

    ExecutionInterrupt executionInterrupt = anExecutionInterrupt()
                                                .appId(app.getUuid())
                                                .executionInterruptType(ExecutionInterruptType.ABORT_ALL)
                                                .envId(env.getUuid())
                                                .stateExecutionInstanceId(stateExecutionInstance.getUuid())
                                                .executionUuid(workflowExecution.getUuid())
                                                .build();
    executionInterruptManager.registerExecutionInterrupt(executionInterrupt);

    verify(stateMachineExecutor, times(1)).sendPipelineNotification(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = RAFAEL)
  @Category(UnitTests.class)
  public void shouldThrowStateStartingForRetry() {
    Application app = anApplication().name("App1").build();
    wingsPersistence.save(app);
    Environment env = anEnvironment().appId(app.getUuid()).build();
    wingsPersistence.save(env);

    StateExecutionInstance stateExecutionInstance =
        aStateExecutionInstance().appId(app.getUuid()).displayName("state1").status(ExecutionStatus.STARTING).build();
    wingsPersistence.save(stateExecutionInstance);
    ExecutionInterrupt executionInterrupt = anExecutionInterrupt()
                                                .appId(app.getUuid())
                                                .executionInterruptType(ExecutionInterruptType.RETRY)
                                                .envId(env.getUuid())
                                                .executionUuid(generateUuid())
                                                .stateExecutionInstanceId(stateExecutionInstance.getUuid())
                                                .build();
    executionInterrupt = executionInterruptManager.registerExecutionInterrupt(executionInterrupt);
    assertThat(executionInterrupt.getExecutionInterruptType()).isEqualTo(ExecutionInterruptType.RETRY);
  }
}
