/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.workers.background.critical.iterator;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.beans.ExecutionStatus.EXPIRED;
import static io.harness.beans.ExecutionStatus.PREPARING;
import static io.harness.rule.OwnerRule.AADITI;
import static io.harness.rule.OwnerRule.AGORODETKI;
import static io.harness.rule.OwnerRule.PRABU;
import static io.harness.rule.OwnerRule.YOGESH;

import static software.wings.sm.StateExecutionInstance.Builder.aStateExecutionInstance;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.WORKFLOW_EXECUTION_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ExecutionInterruptType;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.RepairActionCode;
import io.harness.category.element.UnitTests;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.MongoPersistenceIterator.MongoPersistenceIteratorBuilder;
import io.harness.mongo.iterator.filter.MorphiaFilterExpander;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.WorkflowExecution;
import software.wings.sm.ExecutionInterrupt;
import software.wings.sm.ExecutionInterruptManager;
import software.wings.sm.StateExecutionInstance;
import software.wings.sm.StateMachineExecutionCallbackMock;
import software.wings.sm.StateMachineExecutor;

import com.google.inject.Inject;
import java.time.Duration;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PrepareForTest({WorkflowExecutionMonitorHandler.class, PersistenceIteratorFactory.class})
@PowerMockIgnore({"javax.security.*", "javax.net.*"})
@OwnedBy(CDC)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class WorkflowExecutionMonitorHandlerTest extends WingsBaseTest {
  @Mock PersistenceIteratorFactory persistenceIteratorFactory;
  @Mock private ExecutionInterruptManager executionInterruptManager;
  @Mock private StateMachineExecutor stateMachineExecutor;
  @Mock private WorkflowExecutionZombieHandler zombieHandler;

  @Inject private HPersistence persistence;
  @Inject @InjectMocks private WorkflowExecutionMonitorHandler workflowExecutionMonitorHandler;
  private ArgumentCaptor<MongoPersistenceIteratorBuilder> captor =
      ArgumentCaptor.forClass(MongoPersistenceIteratorBuilder.class);
  private WorkflowExecution workflowExecution;
  private static final Duration EXPIRE_THRESHOLD = Duration.ofMinutes(11);

  @Before
  public void setUp() throws Exception {
    when(persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(any(), any(), any()))
        .thenAnswer(invocationOnMock -> invocationOnMock.getArgument(2, MongoPersistenceIteratorBuilder.class).build());

    workflowExecution = WorkflowExecution.builder().appId(APP_ID).uuid(WORKFLOW_EXECUTION_ID).build();
    persistence.save(workflowExecution);
  }

  private StateExecutionInstance createExpiredStateExecutionInstance() {
    StateExecutionInstance expiredStateExecutionInstance = aStateExecutionInstance()
                                                               .appId(APP_ID)
                                                               .executionUuid(WORKFLOW_EXECUTION_ID)
                                                               .status(ExecutionStatus.WAITING)
                                                               .build();
    expiredStateExecutionInstance.setExpiryTs(System.currentTimeMillis() - 1);
    persistence.save(expiredStateExecutionInstance);
    return expiredStateExecutionInstance;
  }

  private StateExecutionInstance createExpiredButWaitingForManualInterventionStateExecutionInstance() {
    StateExecutionInstance expiredStateExecutionInstance = aStateExecutionInstance()
                                                               .appId(APP_ID)
                                                               .executionUuid(WORKFLOW_EXECUTION_ID)
                                                               .status(ExecutionStatus.WAITING)
                                                               .build();
    expiredStateExecutionInstance.setActionAfterManualInterventionTimeout(ExecutionInterruptType.END_EXECUTION);
    expiredStateExecutionInstance.setWaitingForManualIntervention(true);
    expiredStateExecutionInstance.setExpiryTs(System.currentTimeMillis() - 1);
    persistence.save(expiredStateExecutionInstance);
    return expiredStateExecutionInstance;
  }

  private StateExecutionInstance createExpiredButWaitingForInputsStateExecutionInstance() {
    StateExecutionInstance expiredStateExecutionInstance = aStateExecutionInstance()
                                                               .appId(APP_ID)
                                                               .executionUuid(WORKFLOW_EXECUTION_ID)
                                                               .status(ExecutionStatus.PAUSED)
                                                               .build();
    expiredStateExecutionInstance.setActionOnTimeout(RepairActionCode.CONTINUE_WITH_DEFAULTS);
    expiredStateExecutionInstance.setWaitingForInputs(true);
    expiredStateExecutionInstance.setExpiryTs(System.currentTimeMillis() - 1);
    persistence.save(expiredStateExecutionInstance);
    return expiredStateExecutionInstance;
  }

  private StateExecutionInstance createSuccessStateExecutionInstance() {
    StateExecutionInstance successStateExecutionInstance = aStateExecutionInstance()
                                                               .appId(APP_ID)
                                                               .executionUuid(WORKFLOW_EXECUTION_ID)
                                                               .status(ExecutionStatus.FAILED)
                                                               .callback(new StateMachineExecutionCallbackMock())
                                                               .build();
    persistence.save(successStateExecutionInstance);
    return successStateExecutionInstance;
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testRegisterIterators() {
    workflowExecutionMonitorHandler.registerIterators(5);
    Mockito.verify(persistenceIteratorFactory, times(1))
        .createPumpIteratorWithDedicatedThreadPool(any(), eq(WorkflowExecution.class), captor.capture());
    MongoPersistenceIterator<WorkflowExecution, MorphiaFilterExpander<WorkflowExecution>> persistenceIterator =
        (MongoPersistenceIterator<WorkflowExecution, MorphiaFilterExpander<WorkflowExecution>>) captor.getValue()
            .build();
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testHandleExpiredWorkflow() {
    StateExecutionInstance expiredStateExecutionInstance = createExpiredStateExecutionInstance();
    ArgumentCaptor<ExecutionInterrupt> executionInterruptArgumentCaptor =
        ArgumentCaptor.forClass(ExecutionInterrupt.class);
    workflowExecutionMonitorHandler.handle(workflowExecution);
    verify(executionInterruptManager, times(1)).registerExecutionInterrupt(executionInterruptArgumentCaptor.capture());
    ExecutionInterrupt executionInterrupt = executionInterruptArgumentCaptor.getValue();
    assertThat(executionInterrupt.getExecutionInterruptType()).isEqualTo(ExecutionInterruptType.MARK_EXPIRED);
    persistence.delete(expiredStateExecutionInstance);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testHandleRecentSuccessfulStateExecution() {
    StateExecutionInstance failedStateExecutionInstance = createSuccessStateExecutionInstance();
    workflowExecutionMonitorHandler.handle(workflowExecution);
    verify(stateMachineExecutor, never()).executeCallback(any(), any(), any(), any());
    persistence.delete(failedStateExecutionInstance);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testExpireWorkflowInPreparingState() {
    WorkflowExecution execution = WorkflowExecution.builder()
                                      .appId(APP_ID)
                                      .accountId(ACCOUNT_ID)
                                      .uuid(WORKFLOW_EXECUTION_ID)
                                      .startTs(System.currentTimeMillis() - EXPIRE_THRESHOLD.toMillis())
                                      .status(PREPARING)
                                      .message("Starting artifact collection")
                                      .build();
    persistence.save(execution);
    workflowExecutionMonitorHandler.handle(execution);
    WorkflowExecution updatedWorkflowExecution = persistence.get(WorkflowExecution.class, WORKFLOW_EXECUTION_ID);
    assertThat(updatedWorkflowExecution.getStatus()).isEqualTo(EXPIRED);
    assertThat(updatedWorkflowExecution.getMessage()).isNull();
    persistence.delete(updatedWorkflowExecution);
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldInterruptWaitingForManualInterventionExecution() {
    StateExecutionInstance expiredStateExecutionInstance =
        createExpiredButWaitingForManualInterventionStateExecutionInstance();
    ArgumentCaptor<ExecutionInterrupt> executionInterruptArgumentCaptor =
        ArgumentCaptor.forClass(ExecutionInterrupt.class);
    workflowExecutionMonitorHandler.handle(workflowExecution);
    verify(executionInterruptManager, times(1)).registerExecutionInterrupt(executionInterruptArgumentCaptor.capture());
    ExecutionInterrupt executionInterrupt = executionInterruptArgumentCaptor.getValue();
    assertThat(executionInterrupt.getExecutionInterruptType()).isEqualTo(ExecutionInterruptType.END_EXECUTION);
    persistence.delete(expiredStateExecutionInstance);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldInterruptByContinuingWithDefaultValues() {
    StateExecutionInstance expiredStateExecutionInstance = createExpiredButWaitingForInputsStateExecutionInstance();
    ArgumentCaptor<ExecutionInterrupt> executionInterruptArgumentCaptor =
        ArgumentCaptor.forClass(ExecutionInterrupt.class);
    workflowExecutionMonitorHandler.handle(workflowExecution);
    verify(executionInterruptManager, times(1)).registerExecutionInterrupt(executionInterruptArgumentCaptor.capture());
    ExecutionInterrupt executionInterrupt = executionInterruptArgumentCaptor.getValue();
    assertThat(executionInterrupt.getExecutionInterruptType()).isEqualTo(ExecutionInterruptType.CONTINUE_WITH_DEFAULTS);
    persistence.delete(expiredStateExecutionInstance);
  }
}
