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
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ExecutionInterruptType;
import io.harness.beans.FeatureName;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.beans.SearchFilter;
import io.harness.beans.SortOrder;
import io.harness.category.element.UnitTests;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;

import software.wings.beans.WorkflowExecution;
import software.wings.service.intfc.StateExecutionService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.sm.ExecutionInterrupt;
import software.wings.sm.StateExecutionInstance;
import software.wings.sm.StateType;

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
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(CDC)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class WorkflowExecutionZombieMonitorHandlerTest {
  private static final String WORKFLOW_ID = "workflowId";
  private static final String EXECUTION_UUID = "executionUuid";

  @InjectMocks private WorkflowExecutionZombieMonitorHandler monitorHandler;

  @Mock private WorkflowExecutionService workflowExecutionService;
  @Mock private StateExecutionService stateExecutionService;
  @Mock private FeatureFlagService featureFlagService;

  @Before
  public void setup() {
    when(featureFlagService.isNotEnabled(eq(FeatureName.WORKFLOW_EXECUTION_ZOMBIE_MONITOR), any())).thenReturn(false);
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldIgnoreExecutionWhenFeatureFlagDisable() {
    when(featureFlagService.isNotEnabled(eq(FeatureName.WORKFLOW_EXECUTION_ZOMBIE_MONITOR), any())).thenReturn(true);

    monitorHandler.handle(createValidWorkflowExecution());

    verify(stateExecutionService, never()).list(any());
    verify(workflowExecutionService, never()).triggerExecutionInterrupt(any());
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldVerifyMinutesAgo() {
    assertThat(monitorHandler.minutesAgo()).isLessThan(System.currentTimeMillis());
    assertThat(monitorHandler.minutesAgo()).isGreaterThan(System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(20));
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldHandleWorkflowExecutionWhenNotFoundStateExecutionInstances() {
    WorkflowExecution wfExecution = createValidWorkflowExecution();

    ArgumentCaptor<PageRequest> arg = ArgumentCaptor.forClass(PageRequest.class);
    when(stateExecutionService.list(arg.capture())).thenReturn(new PageResponse<>());

    monitorHandler.handle(wfExecution);

    verify(workflowExecutionService, never()).triggerExecutionInterrupt(any());
    assertPageRequest(arg.getValue(), wfExecution);
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldHandleWorkflowExecutionWhenNotNotZombieState() {
    WorkflowExecution wfExecution = createValidWorkflowExecution();
    StateExecutionInstance seInstance = aStateExecutionInstance().stateType(StateType.SHELL_SCRIPT.name()).build();

    ArgumentCaptor<PageRequest> arg = ArgumentCaptor.forClass(PageRequest.class);
    PageResponse<StateExecutionInstance> response = new PageResponse<>();
    response.setResponse(Collections.singletonList(seInstance));
    when(stateExecutionService.list(arg.capture())).thenReturn(response);

    monitorHandler.handle(wfExecution);

    verify(workflowExecutionService, never()).triggerExecutionInterrupt(any());
    assertPageRequest(arg.getValue(), wfExecution);
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldHandleWorkflowExecutionAndTriggerInterruptWhenZombieState() {
    WorkflowExecution wfExecution = createValidWorkflowExecution();
    StateExecutionInstance seInstance = aStateExecutionInstance()
                                            .appId("APP_ID")
                                            .executionUuid(EXECUTION_UUID)
                                            .stateType(StateType.PHASE.name())
                                            .build();

    ArgumentCaptor<PageRequest> arg = ArgumentCaptor.forClass(PageRequest.class);
    PageResponse<StateExecutionInstance> response = new PageResponse<>();
    response.setResponse(Collections.singletonList(seInstance));
    when(stateExecutionService.list(arg.capture())).thenReturn(response);

    monitorHandler.handle(wfExecution);

    ArgumentCaptor<ExecutionInterrupt> captor = ArgumentCaptor.forClass(ExecutionInterrupt.class);
    verify(workflowExecutionService).triggerExecutionInterrupt(captor.capture());
    assertPageRequest(arg.getValue(), wfExecution);

    ExecutionInterrupt value = captor.getValue();
    assertThat(value.getAppId()).isEqualTo("APP_ID");
    assertThat(value.getExecutionUuid()).isEqualTo(EXECUTION_UUID);
    assertThat(value.getExecutionInterruptType()).isEqualTo(ExecutionInterruptType.ABORT_ALL);
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldVerifyNotZombieStateType() {
    Set<StateType> types = new HashSet(Arrays.asList(StateType.values()));

    // REMOVE VALID TYPES
    types.remove(StateType.REPEAT);
    types.remove(StateType.FORK);
    types.remove(StateType.PHASE_STEP);
    types.remove(StateType.PHASE);
    types.remove(StateType.SUB_WORKFLOW);

    types.forEach(item -> {
      assertThat(monitorHandler.isZombieState(aStateExecutionInstance().stateType(item.name()).build())).isFalse();
    });
  }

  private WorkflowExecution createValidWorkflowExecution() {
    return WorkflowExecution.builder().workflowId(WORKFLOW_ID).uuid(EXECUTION_UUID).build();
  }

  private void assertPageRequest(PageRequest req, WorkflowExecution wfExecution) {
    List<SortOrder> orders = req.getOrders();
    assertThat(orders.size()).isEqualTo(1);
    assertThat(orders.get(0).getFieldName()).isEqualTo("createdAt");
    assertThat(orders.get(0).getOrderType()).isEqualTo(SortOrder.OrderType.ASC);

    List<SearchFilter> filters = req.getFilters();
    assertThat(filters.size()).isEqualTo(2);
    //
    assertThat(filters.get(0).getFieldName()).isEqualTo("workflowId");
    assertThat(filters.get(0).getOp()).isEqualTo(SearchFilter.Operator.EQ);
    assertThat(filters.get(0).getFieldValues()[0]).isEqualTo(wfExecution.getWorkflowId());
    //
    assertThat(filters.get(1).getFieldName()).isEqualTo("executionUuid");
    assertThat(filters.get(1).getOp()).isEqualTo(SearchFilter.Operator.EQ);
    assertThat(filters.get(1).getFieldValues()[0]).isEqualTo(wfExecution.getUuid());
  }
}
