/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.barriers.event;

import static io.harness.rule.OwnerRule.VINICIUS;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_MOCKS;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.OrchestrationStepsTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.engine.observers.NodeUpdateInfo;
import io.harness.execution.NodeExecution;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.rule.Owner;
import io.harness.steps.barriers.beans.BarrierPositionInfo.BarrierPosition.BarrierPositionType;
import io.harness.steps.barriers.service.BarrierService;

import java.util.List;
import java.util.Optional;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;

@OwnedBy(HarnessTeam.PIPELINE)
public class BarrierPositionHelperEventHandlerTest extends OrchestrationStepsTestBase {
  @Mock BarrierService barrierService;
  @InjectMocks BarrierPositionHelperEventHandler barrierPositionHelperEventHandler;

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testOnNodeStatusUpdateBarrierFFEnabled() {
    String accountId = "accountId";
    String executionId = "executionId";
    String planExecutionId = "planExecutionId";
    NodeUpdateInfo nodeUpdateInfo =
        NodeUpdateInfo.builder()
            .nodeExecution(NodeExecution.builder()
                               .uuid(executionId)
                               .ambiance(Ambiance.newBuilder().setPlanExecutionId(planExecutionId).build())
                               .build())
            .build();
    when(barrierService.updatePosition(
             planExecutionId, BarrierPositionType.STEP, "setupId", executionId, "stageId", "stepGroupId", true))
        .thenReturn(List.of());
    try (MockedStatic<AmbianceUtils> mockAmbianceUtils = mockStatic(AmbianceUtils.class, RETURNS_MOCKS)) {
      when(AmbianceUtils.getAccountId(any())).thenReturn(accountId);
      when(AmbianceUtils.obtainCurrentLevel(any()))
          .thenReturn(Level.newBuilder().setGroup(BarrierPositionType.STEP.name()).setSetupId("setupId").build());
      when(AmbianceUtils.getStageLevelFromAmbiance(any()))
          .thenReturn(Optional.of(Level.newBuilder().setRuntimeId("stageId").build()));
      when(AmbianceUtils.getStepGroupLevelFromAmbiance(any()))
          .thenReturn(Optional.of(Level.newBuilder().setRuntimeId("stepGroupId").build()));
      when(AmbianceUtils.checkIfFeatureFlagEnabled(
               any(), eq(FeatureName.CDS_NG_BARRIER_STEPS_WITHIN_LOOPING_STRATEGIES.name())))
          .thenReturn(true);
      barrierPositionHelperEventHandler.onNodeStatusUpdate(nodeUpdateInfo);
      verify(barrierService, times(1))
          .updatePosition(
              planExecutionId, BarrierPositionType.STEP, "setupId", executionId, "stageId", "stepGroupId", true);
    }
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testOnNodeStatusUpdateBarrierFFDisabled() {
    String accountId = "accountId";
    String executionId = "executionId";
    String planExecutionId = "planExecutionId";
    NodeUpdateInfo nodeUpdateInfo =
        NodeUpdateInfo.builder()
            .nodeExecution(NodeExecution.builder()
                               .uuid(executionId)
                               .ambiance(Ambiance.newBuilder().setPlanExecutionId(planExecutionId).build())
                               .build())
            .build();
    when(barrierService.updatePosition(
             planExecutionId, BarrierPositionType.STEP, "setupId", executionId, null, null, false))
        .thenReturn(List.of());
    try (MockedStatic<AmbianceUtils> mockAmbianceUtils = mockStatic(AmbianceUtils.class, RETURNS_MOCKS)) {
      when(AmbianceUtils.getAccountId(any())).thenReturn(accountId);
      when(AmbianceUtils.obtainCurrentLevel(any()))
          .thenReturn(Level.newBuilder().setGroup(BarrierPositionType.STEP.name()).setSetupId("setupId").build());
      when(AmbianceUtils.checkIfFeatureFlagEnabled(
               any(), eq(FeatureName.CDS_NG_BARRIER_STEPS_WITHIN_LOOPING_STRATEGIES.name())))
          .thenReturn(false);
      barrierPositionHelperEventHandler.onNodeStatusUpdate(nodeUpdateInfo);
      verify(barrierService, times(1))
          .updatePosition(planExecutionId, BarrierPositionType.STEP, "setupId", executionId, null, null, false);
    }
  }
}
