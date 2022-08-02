/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.execution.strategy;

import static io.harness.rule.OwnerRule.SHALINI;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.pms.data.PmsOutcomeService;
import io.harness.engine.pms.execution.strategy.plannode.PlanNodeExecutionStrategy;
import io.harness.execution.NodeExecution;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.data.StepOutcomeRef;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.io.StepOutcomeProto;
import io.harness.pms.contracts.steps.io.StepResponseProto;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Consumer;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(HarnessTeam.PIPELINE)
@RunWith(MockitoJUnitRunner.class)
public class EndNodeExecutionHelperTest extends OrchestrationTestBase {
  @Mock private PmsOutcomeService pmsOutcomeService;
  @Mock private NodeExecutionService nodeExecutionService;
  @Mock private PlanNodeExecutionStrategy executionStrategy;
  @InjectMocks @Spy EndNodeExecutionHelper endNodeExecutionHelper;

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testEndNodeExecutionWithNoAdvisers() {
    doReturn(null)
        .when(endNodeExecutionHelper)
        .processStepResponseWithNoAdvisers(any(Ambiance.class), any(StepResponseProto.class));
    endNodeExecutionHelper.endNodeExecutionWithNoAdvisers(
        Ambiance.newBuilder().build(), StepResponseProto.newBuilder().build());
    verify(executionStrategy, times(0)).endNodeExecution(any(Ambiance.class));
    doReturn(NodeExecution.builder().ambiance(Ambiance.newBuilder().build()).build())
        .when(endNodeExecutionHelper)
        .processStepResponseWithNoAdvisers(any(Ambiance.class), any(StepResponseProto.class));
    endNodeExecutionHelper.endNodeExecutionWithNoAdvisers(
        Ambiance.newBuilder().build(), StepResponseProto.newBuilder().build());
    verify(executionStrategy, times(1)).endNodeExecution(any(Ambiance.class));
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testProcessStepResponseWithNoAdvisers() {
    NodeExecution nodeExecution = NodeExecution.builder().build();
    doReturn(nodeExecution)
        .when(nodeExecutionService)
        .updateStatusWithOps(anyString(), any(Status.class), any(Consumer.class), any(EnumSet.class));
    assertThat(endNodeExecutionHelper.processStepResponseWithNoAdvisers(
                   Ambiance.newBuilder().addLevels(Level.newBuilder().setRuntimeId("1").build()).build(),
                   StepResponseProto.newBuilder().setStatus(Status.valueOf("NO_OP")).build()))
        .isInstanceOf(NodeExecution.class);
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testHandleOutcomes() {
    assertEquals(new ArrayList<>(),
        endNodeExecutionHelper.handleOutcomes(Ambiance.newBuilder().build(), new ArrayList<>(), new ArrayList<>()));
    StepOutcomeProto stepOutcomeProto =
        StepOutcomeProto.newBuilder().setOutcome("1").setName("proto").setGroup("group1").build();
    doReturn("id1").when(pmsOutcomeService).consume(any(Ambiance.class), anyString(), anyString(), anyString());
    List<StepOutcomeProto> stepOutcomeProtoList = new ArrayList<>();
    stepOutcomeProtoList.add(stepOutcomeProto);
    List<StepOutcomeRef> stepOutcomeRefs =
        endNodeExecutionHelper.handleOutcomes(Ambiance.newBuilder().build(), stepOutcomeProtoList, new ArrayList<>());
    assertEquals(1, stepOutcomeRefs.size());
    assertEquals("id1", stepOutcomeRefs.get(0).getInstanceId());
    assertEquals("proto", stepOutcomeRefs.get(0).getName());
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testHandleStepResponsePreAdviser() {
    doReturn(null)
        .when(endNodeExecutionHelper)
        .processStepResponsePreAdvisers(any(Ambiance.class), any(StepResponseProto.class));
    assertNull(endNodeExecutionHelper.handleStepResponsePreAdviser(
        Ambiance.newBuilder().build(), StepResponseProto.newBuilder().build()));
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testProcessStepResponsePreAdvisers() {
    NodeExecution nodeExecution = NodeExecution.builder().build();
    doReturn(nodeExecution)
        .when(nodeExecutionService)
        .updateStatusWithOps(anyString(), any(Status.class), any(Consumer.class), any(EnumSet.class));
    assertThat(endNodeExecutionHelper.processStepResponseWithNoAdvisers(
                   Ambiance.newBuilder().addLevels(Level.newBuilder().setRuntimeId("1").build()).build(),
                   StepResponseProto.newBuilder().setStatus(Status.valueOf("NO_OP")).build()))
        .isInstanceOf(NodeExecution.class);
  }
}
