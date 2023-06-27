/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.workers.background.critical.iterator;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.rule.OwnerRule.FERNANDOD;

import static software.wings.sm.StateExecutionInstance.Builder.aStateExecutionInstance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ExecutionInterruptType;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;

import software.wings.beans.WorkflowExecution;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.sm.ExecutionInterrupt;
import software.wings.sm.StateExecutionInstance;
import software.wings.sm.StateExecutionInstance.StateExecutionInstanceKeys;
import software.wings.sm.StateType;

import dev.morphia.query.FindOptions;
import dev.morphia.query.Query;
import dev.morphia.query.Sort;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(CDC)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class WorkflowExecutionZombieHandlerTest {
  private static final String WORKFLOW_ID = "workflowId";
  private static final String EXECUTION_UUID = "executionUuid";
  private static final String PARENT_UUID = "parentUuid";
  private static final String APP_ID = "APP_ID";

  @InjectMocks private WorkflowExecutionZombieHandler monitorHandler;

  @Mock private WingsPersistence wingsPersistence;
  @Mock private WorkflowExecutionService workflowExecutionService;
  @Mock private FeatureFlagService featureFlagService;

  private ArgumentCaptor<Sort> argSort;
  private ArgumentCaptor<FindOptions> argFindOptions;

  @Before
  public void setup() {
    when(featureFlagService.isNotEnabled(eq(FeatureName.WORKFLOW_EXECUTION_ZOMBIE_MONITOR), any())).thenReturn(false);
    argSort = ArgumentCaptor.forClass(Sort.class);
    argFindOptions = ArgumentCaptor.forClass(FindOptions.class);
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldHandleWorkflowExecutionWhenNotFoundStateExecutionInstances() {
    WorkflowExecution wfExecution = createValidWorkflowExecution();

    prepareWingsPersistence(Collections.emptyList(), 0);

    monitorHandler.handle(wfExecution);

    verify(workflowExecutionService, never()).triggerExecutionInterrupt(any());
    assertSortAndLimit();
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldHandleWorkflowExecutionWhenNotZombieState() {
    WorkflowExecution wfExecution = createValidWorkflowExecution();
    StateExecutionInstance seInstance = aStateExecutionInstance().stateType(StateType.SHELL_SCRIPT.name()).build();

    prepareWingsPersistence(seInstance);

    monitorHandler.handle(wfExecution);

    verify(workflowExecutionService, never()).triggerExecutionInterrupt(any());
    assertSortAndLimit();
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldHandleWorkflowExecutionWhenIsZombieStateButCreatedOutOfThreshold() {
    WorkflowExecution wfExecution = createValidWorkflowExecution();
    StateExecutionInstance seInstance = aStateExecutionInstance()
                                            .appId(APP_ID)
                                            .executionUuid(EXECUTION_UUID)
                                            .stateType(StateType.PHASE.name())
                                            .createdAt(System.currentTimeMillis())
                                            .build();

    prepareWingsPersistence(seInstance);

    monitorHandler.handle(wfExecution);

    verify(workflowExecutionService, never()).triggerExecutionInterrupt(any());
    assertSortAndLimit();
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldHandleWorkflowExecutionAndTriggerInterruptWhenZombieState() {
    WorkflowExecution wfExecution = createValidWorkflowExecution();
    StateExecutionInstance seInstance = aStateExecutionInstance()
                                            .appId(APP_ID)
                                            .executionUuid(EXECUTION_UUID)
                                            .stateType(StateType.PHASE.name())
                                            .createdAt(createThreshold(46))
                                            .build();

    prepareWingsPersistence(seInstance);

    monitorHandler.handle(wfExecution);

    ArgumentCaptor<ExecutionInterrupt> captor = ArgumentCaptor.forClass(ExecutionInterrupt.class);
    verify(workflowExecutionService).triggerExecutionInterrupt(captor.capture());
    assertSortAndLimit();

    ExecutionInterrupt value = captor.getValue();
    assertThat(value.getAppId()).isEqualTo(APP_ID);
    assertThat(value.getExecutionUuid()).isEqualTo(EXECUTION_UUID);
    assertThat(value.getExecutionInterruptType()).isEqualTo(ExecutionInterruptType.ABORT_ALL);
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldHandleWorkflowExecutionWhenSuccessZombieEndedOutOfThreshold() {
    WorkflowExecution wfExecution = createValidWorkflowExecution();
    StateExecutionInstance seInstance = aStateExecutionInstance()
                                            .appId(APP_ID)
                                            .executionUuid(EXECUTION_UUID)
                                            .stateType(StateType.ENV_STATE.name())
                                            .status(ExecutionStatus.SUCCESS)
                                            .endTs(System.currentTimeMillis())
                                            .build();

    prepareWingsPersistence(seInstance);

    monitorHandler.handle(wfExecution);

    verify(workflowExecutionService, never()).triggerExecutionInterrupt(any());
    assertSortAndLimit();
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldHandleWorkflowExecutionWhenStateExecutionIsRunningAndNotZombieState() {
    WorkflowExecution wfExecution = createValidWorkflowExecution();
    StateExecutionInstance seInstance = aStateExecutionInstance()
                                            .appId(APP_ID)
                                            .executionUuid(EXECUTION_UUID)
                                            .stateType(StateType.ENV_STATE.name())
                                            .status(ExecutionStatus.RUNNING)
                                            .createdAt(createThreshold(50))
                                            .build();

    prepareWingsPersistence(seInstance);

    monitorHandler.handle(wfExecution);

    verify(workflowExecutionService, never()).triggerExecutionInterrupt(any());
    assertSortAndLimit();
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldHandleWorkflowExecutionWhenStateExecutionIsPausedAndZombieState() {
    WorkflowExecution wfExecution = createValidWorkflowExecution();
    StateExecutionInstance seInstance = aStateExecutionInstance()
                                            .appId(APP_ID)
                                            .executionUuid(EXECUTION_UUID)
                                            .stateType(StateType.PHASE_STEP.name())
                                            .status(ExecutionStatus.PAUSED)
                                            .createdAt(createThreshold(50))
                                            .build();

    prepareWingsPersistence(seInstance);

    monitorHandler.handle(wfExecution);

    verify(workflowExecutionService, never()).triggerExecutionInterrupt(any());
    assertSortAndLimit();
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldHandleWorkflowExecutionWhenSuccessZombie() {
    WorkflowExecution wfExecution = createValidWorkflowExecution();
    StateExecutionInstance seInstance = aStateExecutionInstance()
                                            .appId(APP_ID)
                                            .executionUuid(EXECUTION_UUID)
                                            .parentInstanceId(PARENT_UUID)
                                            .stateType(StateType.ENV_STATE.name())
                                            .status(ExecutionStatus.SUCCESS)
                                            .endTs(createThreshold(46))
                                            .build();

    prepareWingsPersistence(seInstance);

    monitorHandler.handle(wfExecution);

    ArgumentCaptor<ExecutionInterrupt> captor = ArgumentCaptor.forClass(ExecutionInterrupt.class);
    verify(workflowExecutionService).triggerExecutionInterrupt(captor.capture());
    assertSortAndLimit();

    ExecutionInterrupt value = captor.getValue();
    assertThat(value.getAppId()).isEqualTo(APP_ID);
    assertThat(value.getExecutionUuid()).isEqualTo(EXECUTION_UUID);
    assertThat(value.getExecutionInterruptType()).isEqualTo(ExecutionInterruptType.ABORT_ALL);
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldHandleWorkflowExecutionWhenSuccessZombieAndParentIsFork() {
    WorkflowExecution wfExecution = createValidWorkflowExecution();
    StateExecutionInstance seInstance = aStateExecutionInstance()
                                            .appId(APP_ID)
                                            .executionUuid(EXECUTION_UUID)
                                            .parentInstanceId(PARENT_UUID)
                                            .stateType(StateType.ENV_STATE.name())
                                            .status(ExecutionStatus.SUCCESS)
                                            .endTs(createThreshold(46))
                                            .build();

    prepareWingsPersistence(Collections.singletonList(seInstance), 1);

    monitorHandler.handle(wfExecution);

    ArgumentCaptor<ExecutionInterrupt> captor = ArgumentCaptor.forClass(ExecutionInterrupt.class);
    verify(workflowExecutionService, never()).triggerExecutionInterrupt(captor.capture());
    assertSortAndLimit();
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldHandleWorkflowExecutionWhenSuccessZombieHasStatusSkipped() {
    WorkflowExecution wfExecution = createValidWorkflowExecution();
    StateExecutionInstance seInstance = aStateExecutionInstance()
                                            .appId(APP_ID)
                                            .executionUuid(EXECUTION_UUID)
                                            .parentInstanceId(PARENT_UUID)
                                            .stateType(StateType.ENV_STATE.name())
                                            .status(ExecutionStatus.SKIPPED)
                                            .endTs(createThreshold(46))
                                            .build();

    prepareWingsPersistence(seInstance);

    monitorHandler.handle(wfExecution);

    ArgumentCaptor<ExecutionInterrupt> captor = ArgumentCaptor.forClass(ExecutionInterrupt.class);
    verify(workflowExecutionService, never()).triggerExecutionInterrupt(captor.capture());
    assertSortAndLimit();
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldHandleWorkflowExecutionWhenSuccessZombieEndedRecently() {
    WorkflowExecution wfExecution = createValidWorkflowExecution();
    StateExecutionInstance seInstance = aStateExecutionInstance()
                                            .appId(APP_ID)
                                            .executionUuid(EXECUTION_UUID)
                                            .stateType(StateType.ENV_STATE.name())
                                            .status(ExecutionStatus.SKIPPED)
                                            .endTs(createThreshold(2))
                                            .build();

    prepareWingsPersistence(seInstance);

    monitorHandler.handle(wfExecution);

    ArgumentCaptor<ExecutionInterrupt> captor = ArgumentCaptor.forClass(ExecutionInterrupt.class);
    verify(workflowExecutionService, never()).triggerExecutionInterrupt(captor.capture());
    assertSortAndLimit();
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldHandleWorkflowExecutionWhenSuccessZombieEndedRecentlyAndParentIsFork() {
    WorkflowExecution wfExecution = createValidWorkflowExecution();
    StateExecutionInstance seInstance = aStateExecutionInstance()
                                            .appId(APP_ID)
                                            .executionUuid(EXECUTION_UUID)
                                            .stateType(StateType.ENV_STATE.name())
                                            .status(ExecutionStatus.SKIPPED)
                                            .endTs(createThreshold(2))
                                            .build();

    prepareWingsPersistence(Collections.singletonList(seInstance), 1);

    monitorHandler.handle(wfExecution);

    ArgumentCaptor<ExecutionInterrupt> captor = ArgumentCaptor.forClass(ExecutionInterrupt.class);
    verify(workflowExecutionService, never()).triggerExecutionInterrupt(captor.capture());
    assertSortAndLimit();
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldHandleWorkflowExecutionWhenSuccessZombieAndFFDisabled() {
    when(featureFlagService.isNotEnabled(eq(FeatureName.WORKFLOW_EXECUTION_ZOMBIE_MONITOR), any())).thenReturn(true);

    WorkflowExecution wfExecution = createValidWorkflowExecution();
    StateExecutionInstance seInstance = aStateExecutionInstance()
                                            .appId(APP_ID)
                                            .executionUuid(EXECUTION_UUID)
                                            .parentInstanceId(PARENT_UUID)
                                            .stateType(StateType.ENV_STATE.name())
                                            .status(ExecutionStatus.SUCCESS)
                                            .endTs(createThreshold(46))
                                            .build();

    prepareWingsPersistence(seInstance);

    monitorHandler.handle(wfExecution);

    ArgumentCaptor<ExecutionInterrupt> captor = ArgumentCaptor.forClass(ExecutionInterrupt.class);
    verify(workflowExecutionService, never()).triggerExecutionInterrupt(captor.capture());
    assertSortAndLimit();
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldVerifyNotZombieStateType() {
    Set<StateType> types = new HashSet<>(Arrays.asList(StateType.values()));

    // REMOVE VALID TYPES
    types.remove(StateType.REPEAT);
    types.remove(StateType.FORK);
    types.remove(StateType.PHASE_STEP);
    types.remove(StateType.PHASE);
    types.remove(StateType.SUB_WORKFLOW);

    types.forEach(item
        -> assertThat(monitorHandler.isZombieState(aStateExecutionInstance().stateType(item.name()).build()))
               .isFalse());
  }

  private WorkflowExecution createValidWorkflowExecution() {
    return WorkflowExecution.builder().workflowId(WORKFLOW_ID).uuid(EXECUTION_UUID).build();
  }

  private void prepareWingsPersistence(StateExecutionInstance seInstance) {
    prepareWingsPersistence(Collections.singletonList(seInstance), 0);
  }

  @SuppressWarnings("unchecked")
  private void prepareWingsPersistence(List<StateExecutionInstance> instances, long count) {
    Query<StateExecutionInstance> query = mock(Query.class);
    when(wingsPersistence.createQuery(StateExecutionInstance.class)).thenReturn(query);
    when(query.filter(eq(StateExecutionInstanceKeys.appId), any())).thenReturn(query);
    when(query.filter(eq(StateExecutionInstanceKeys.workflowId), any())).thenReturn(query);
    when(query.filter(eq(StateExecutionInstanceKeys.executionUuid), any())).thenReturn(query);
    when(query.order(argSort.capture())).thenReturn(query);
    when(query.asList(argFindOptions.capture())).thenReturn(instances);
    //
    when(query.filter(eq(StateExecutionInstanceKeys.uuid), any())).thenReturn(query);
    when(query.filter(eq(StateExecutionInstanceKeys.stateType), any())).thenReturn(query);
    when(query.count()).thenReturn(count);
  }

  private void assertSortAndLimit() {
    Sort sort = argSort.getValue();
    assertThat(sort.getField()).isEqualTo(StateExecutionInstanceKeys.createdAt);
    assertThat(sort.getOrder()).isEqualTo(-1);

    FindOptions fo = argFindOptions.getValue();
    assertThat(fo.getLimit()).isEqualTo(1);
  }

  private long createThreshold(int minutes) {
    return System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(minutes);
  }
}
