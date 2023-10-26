/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.barriers.event;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.distribution.barrier.Barrier.State.STANDING;
import static io.harness.rule.OwnerRule.VINICIUS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.OrchestrationStepsTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.observers.BarrierExpandRequest;
import io.harness.lock.PersistentLocker;
import io.harness.lock.noop.AcquiredNoopLock;
import io.harness.rule.Owner;
import io.harness.steps.barriers.beans.BarrierExecutionInstance;
import io.harness.steps.barriers.beans.BarrierPositionInfo;
import io.harness.steps.barriers.beans.BarrierPositionInfo.BarrierPosition;
import io.harness.steps.barriers.beans.BarrierPositionInfo.BarrierPosition.BarrierPositionType;
import io.harness.steps.barriers.beans.BarrierSetupInfo;
import io.harness.steps.barriers.service.BarrierService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.PIPELINE)
public class BarrierWithinStrategyExpanderTest extends OrchestrationStepsTestBase {
  private static final String planExecutionId = "planExecutionId";
  private static final String barrierIdentifier = "barrierId";
  private static final String stage1SetupId = "stage1SetupId";
  private static final String stage1ExecutionId = "stage1ExecutionId";
  private static final String stage2ExecutionId = "stage2ExecutionId";
  private static final String stage2SetupId = "stage2SetupId";
  private static final String stepGroup1SetupId = "stepGroup1SetupId";
  private static final String stepGroup2SetupId = "stepGroup2SetupId";
  private static final String step1SetupId = "step1SetupId";
  private static final String step2SetupId = "step2SetupId";
  private static final String step3SetupId = "step3SetupId";
  private static final List<String> stage1SetupIds = List.of(stage1SetupId, stage1SetupId);
  private static final List<String> stepGroup1SetupIds =
      List.of(stepGroup1SetupId, stepGroup1SetupId, stepGroup1SetupId);
  private static final List<String> stage1RuntimeIds = List.of("stage1RuntimeId1", "stage1RuntimeId2");
  private static final List<String> stepGroup1RuntimeIds =
      List.of("stepGroup1RuntimeId1", "stepGroup1RuntimeId2", "stepGroup1RuntimeId3");

  @Mock BarrierService barrierService;
  @Mock PersistentLocker persistentLocker;
  @InjectMocks BarrierWithinStrategyExpander barrierWithinStrategyExpander;

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testOnInitializeRequest() {
    when(persistentLocker.waitToAcquireLock(any(), any(), any())).thenReturn(AcquiredNoopLock.builder().build());
    when(barrierService.existsByPlanExecutionIdAndStrategySetupId(any(), any())).thenReturn(true);
    when(barrierService.findManyByPlanExecutionIdAndStrategySetupId(any(), any()))
        .thenReturn(List.of(obtainBarrierExecutionInstance()));
    doNothing()
        .when(barrierService)
        .updateBarrierPositionInfoListAndStrategyConcurrency(any(), any(), any(), any(), anyInt());

    BarrierExpandRequest barrierExpandRequest1 = BarrierExpandRequest.builder()
                                                     .planExecutionId(planExecutionId)
                                                     .stageExecutionId(stage1ExecutionId)
                                                     .strategySetupId(stage1SetupId)
                                                     .childrenSetupIds(stage1SetupIds)
                                                     .childrenRuntimeIds(stage1RuntimeIds)
                                                     .maxConcurrency(10)
                                                     .build();
    ArgumentCaptor<List<BarrierPosition>> updatedPositionsCaptor1 = ArgumentCaptor.forClass(List.class);
    barrierWithinStrategyExpander.onInitializeRequest(barrierExpandRequest1);
    verify(barrierService, times(1))
        .updateBarrierPositionInfoListAndStrategyConcurrency(
            eq(barrierIdentifier), eq(planExecutionId), updatedPositionsCaptor1.capture(), eq(stage1SetupId), eq(2));
    List<BarrierPosition> updatedPositions1 = updatedPositionsCaptor1.getValue();
    assertThat(updatedPositions1.size()).isEqualTo(4);
    assertThat(updatedPositions1.stream().filter(position -> position.getStepSetupId().equals(step1SetupId)).count())
        .isEqualTo(2);
    assertThat(updatedPositions1.stream()
                   .filter(position -> position.getStepSetupId().equals(step1SetupId))
                   .map(BarrierPosition::getStageRuntimeId)
                   .collect(Collectors.toSet()))
        .containsExactlyInAnyOrder(stage1RuntimeIds.get(0), stage1RuntimeIds.get(1));
    assertThat(updatedPositions1.stream().filter(position -> !position.getStepSetupId().equals(step1SetupId)).count())
        .isEqualTo(2);
    assertThat(updatedPositions1.stream()
                   .filter(position -> !position.getStepSetupId().equals(step1SetupId))
                   .map(BarrierPosition::getStageRuntimeId)
                   .collect(Collectors.toSet()))
        .containsOnlyNulls();

    BarrierExpandRequest barrierExpandRequest2 = BarrierExpandRequest.builder()
                                                     .planExecutionId(planExecutionId)
                                                     .stageExecutionId(stage2ExecutionId)
                                                     .strategySetupId(stepGroup1SetupId)
                                                     .childrenSetupIds(stepGroup1SetupIds)
                                                     .childrenRuntimeIds(stepGroup1RuntimeIds)
                                                     .maxConcurrency(2)
                                                     .build();
    ArgumentCaptor<List<BarrierPosition>> updatedPositionsCaptor2 = ArgumentCaptor.forClass(List.class);
    barrierWithinStrategyExpander.onInitializeRequest(barrierExpandRequest2);
    verify(barrierService, times(1))
        .updateBarrierPositionInfoListAndStrategyConcurrency(eq(barrierIdentifier), eq(planExecutionId),
            updatedPositionsCaptor2.capture(), eq(stepGroup1SetupId), eq(2));
    List<BarrierPosition> updatedPositions2 = updatedPositionsCaptor2.getValue();
    assertThat(updatedPositions2.size()).isEqualTo(4);
    assertThat(updatedPositions2.stream().filter(position -> position.getStepSetupId().equals(step2SetupId)).count())
        .isEqualTo(2);
    assertThat(updatedPositions2.stream()
                   .filter(position -> position.getStepSetupId().equals(step2SetupId))
                   .map(BarrierPosition::getStageRuntimeId)
                   .collect(Collectors.toSet()))
        .containsOnly(stage2ExecutionId);
    assertThat(updatedPositions2.stream()
                   .filter(position -> position.getStepSetupId().equals(step2SetupId))
                   .map(BarrierPosition::getStepGroupRuntimeId)
                   .collect(Collectors.toSet()))
        .containsExactlyInAnyOrder(stepGroup1RuntimeIds.get(0), stepGroup1RuntimeIds.get(1));
    assertThat(updatedPositions2.stream().filter(position -> !position.getStepSetupId().equals(step2SetupId)).count())
        .isEqualTo(2);
    assertThat(updatedPositions2.stream()
                   .filter(position -> !position.getStepSetupId().equals(step2SetupId))
                   .map(BarrierPosition::getStageRuntimeId)
                   .collect(Collectors.toSet()))
        .containsOnlyNulls();
    assertThat(updatedPositions2.stream()
                   .filter(position -> !position.getStepSetupId().equals(step2SetupId))
                   .map(BarrierPosition::getStepGroupRuntimeId)
                   .collect(Collectors.toSet()))
        .containsOnlyNulls();
  }

  private BarrierExecutionInstance obtainBarrierExecutionInstance() {
    return BarrierExecutionInstance.builder()
        .uuid(generateUuid())
        .identifier(barrierIdentifier)
        .planExecutionId(planExecutionId)
        .barrierState(STANDING)
        .setupInfo(BarrierSetupInfo.builder().build())
        .positionInfo(BarrierPositionInfo.builder()
                          .planExecutionId(planExecutionId)
                          .barrierPositionList(List.of(BarrierPositionInfo.BarrierPosition.builder()
                                                           .stageSetupId(stage1SetupId)
                                                           .stepSetupId(step1SetupId)
                                                           .strategySetupId(stage1SetupId)
                                                           .allStrategySetupIds(List.of(stage1SetupId))
                                                           .isDummyPosition(true)
                                                           .strategyNodeType(BarrierPositionType.STAGE)
                                                           .build(),
                              BarrierPositionInfo.BarrierPosition.builder()
                                  .stageSetupId(stage2SetupId)
                                  .stepSetupId(step2SetupId)
                                  .stepGroupSetupId(stepGroup1SetupId)
                                  .strategySetupId(stepGroup1SetupId)
                                  .allStrategySetupIds(List.of(stepGroup1SetupId))
                                  .isDummyPosition(true)
                                  .strategyNodeType(BarrierPositionType.STEP_GROUP)
                                  .build(),
                              BarrierPositionInfo.BarrierPosition.builder()
                                  .stageSetupId(stage2SetupId)
                                  .stepSetupId(step3SetupId)
                                  .stepGroupSetupId(stepGroup2SetupId)
                                  .isDummyPosition(false)
                                  .build()))
                          .build())
        .build();
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testOnInitializeRequestWithNestedStrategy() {
    when(persistentLocker.waitToAcquireLock(any(), any(), any())).thenReturn(AcquiredNoopLock.builder().build());
    when(barrierService.existsByPlanExecutionIdAndStrategySetupId(any(), any())).thenReturn(true);
    when(barrierService.findManyByPlanExecutionIdAndStrategySetupId(any(), any()))
        .thenReturn(List.of(obtainBarrierExecutionInstanceWithNestedStrategy()));
    doNothing()
        .when(barrierService)
        .updateBarrierPositionInfoListAndStrategyConcurrency(any(), any(), any(), any(), anyInt());

    BarrierExpandRequest barrierExpandRequest1 = BarrierExpandRequest.builder()
                                                     .planExecutionId(planExecutionId)
                                                     .stageExecutionId(stage1ExecutionId)
                                                     .strategySetupId(stage1SetupId)
                                                     .childrenSetupIds(stage1SetupIds)
                                                     .childrenRuntimeIds(stage1RuntimeIds)
                                                     .maxConcurrency(10)
                                                     .build();
    ArgumentCaptor<List<BarrierPosition>> updatedPositionsCaptor1 = ArgumentCaptor.forClass(List.class);
    barrierWithinStrategyExpander.onInitializeRequest(barrierExpandRequest1);
    verify(barrierService, times(1))
        .updateBarrierPositionInfoListAndStrategyConcurrency(
            eq(barrierIdentifier), eq(planExecutionId), updatedPositionsCaptor1.capture(), eq(stage1SetupId), eq(2));
    List<BarrierPosition> updatedPositions1 = updatedPositionsCaptor1.getValue();
    assertThat(updatedPositions1.size()).isEqualTo(2);
    assertThat(updatedPositions1.stream().filter(position -> position.getStepSetupId().equals(step2SetupId)).count())
        .isEqualTo(1);
    assertThat(updatedPositions1.stream()
                   .filter(position -> position.getStepSetupId().equals(step2SetupId))
                   .map(BarrierPosition::getStageRuntimeId)
                   .collect(Collectors.toSet()))
        .containsOnlyNulls();
    assertThat(updatedPositions1.stream().filter(position -> !position.getStepSetupId().equals(step2SetupId)).count())
        .isEqualTo(1);
    assertThat(updatedPositions1.stream()
                   .filter(position -> !position.getStepSetupId().equals(step2SetupId))
                   .map(BarrierPosition::getStageRuntimeId)
                   .collect(Collectors.toSet()))
        .containsOnlyNulls();

    BarrierExecutionInstance barrierExecutionInstanceWithUpdatedConcurrencyMap =
        obtainBarrierExecutionInstanceWithNestedStrategy();
    barrierExecutionInstanceWithUpdatedConcurrencyMap.getSetupInfo().setStrategyConcurrencyMap(
        new HashMap<>(Map.of(stage1SetupId, 2)));
    when(barrierService.findManyByPlanExecutionIdAndStrategySetupId(any(), any()))
        .thenReturn(List.of(barrierExecutionInstanceWithUpdatedConcurrencyMap));
    BarrierExpandRequest barrierExpandRequest2 = BarrierExpandRequest.builder()
                                                     .planExecutionId(planExecutionId)
                                                     .stageExecutionId(stage2ExecutionId)
                                                     .strategySetupId(stepGroup1SetupId)
                                                     .childrenSetupIds(stepGroup1SetupIds)
                                                     .childrenRuntimeIds(stepGroup1RuntimeIds)
                                                     .maxConcurrency(2)
                                                     .build();
    ArgumentCaptor<List<BarrierPosition>> updatedPositionsCaptor2 = ArgumentCaptor.forClass(List.class);
    barrierWithinStrategyExpander.onInitializeRequest(barrierExpandRequest2);
    verify(barrierService, times(1))
        .updateBarrierPositionInfoListAndStrategyConcurrency(eq(barrierIdentifier), eq(planExecutionId),
            updatedPositionsCaptor2.capture(), eq(stepGroup1SetupId), eq(2));
    List<BarrierPosition> updatedPositions2 = updatedPositionsCaptor2.getValue();
    assertThat(updatedPositions2.size()).isEqualTo(4);
    assertThat(updatedPositions2.stream().filter(position -> position.getStepSetupId().equals(step2SetupId)).count())
        .isEqualTo(3);
    assertThat(updatedPositions2.stream()
                   .filter(position -> position.getStepSetupId().equals(step2SetupId))
                   .map(BarrierPosition::getStageRuntimeId)
                   .collect(Collectors.toSet()))
        .containsExactlyInAnyOrder(stage2ExecutionId, null);
    assertThat(updatedPositions2.stream()
                   .filter(position -> position.getStepSetupId().equals(step2SetupId))
                   .map(BarrierPosition::getStepGroupRuntimeId)
                   .collect(Collectors.toSet()))
        .containsExactlyInAnyOrder(stepGroup1RuntimeIds.get(0), stepGroup1RuntimeIds.get(1), null);
    assertThat(updatedPositions2.stream().filter(BarrierPosition::getIsDummyPosition).count()).isEqualTo(1);
    assertThat(updatedPositions2.stream().filter(position -> !position.getStepSetupId().equals(step2SetupId)).count())
        .isEqualTo(1);
    assertThat(updatedPositions2.stream()
                   .filter(position -> !position.getStepSetupId().equals(step2SetupId))
                   .map(BarrierPosition::getStageRuntimeId)
                   .collect(Collectors.toSet()))
        .containsOnlyNulls();
    assertThat(updatedPositions2.stream()
                   .filter(position -> !position.getStepSetupId().equals(step2SetupId))
                   .map(BarrierPosition::getStepGroupRuntimeId)
                   .collect(Collectors.toSet()))
        .containsOnlyNulls();

    BarrierExecutionInstance barrierExecutionInstanceBeforeFinalUpdate =
        barrierExecutionInstanceWithUpdatedConcurrencyMap;
    barrierExecutionInstanceBeforeFinalUpdate.getPositionInfo().setBarrierPositionList(updatedPositions2);
    barrierExecutionInstanceWithUpdatedConcurrencyMap.getSetupInfo().setStrategyConcurrencyMap(
        new HashMap<>(Map.of(stage1SetupId, 2, stepGroup1SetupId, 2)));
    when(barrierService.findManyByPlanExecutionIdAndStrategySetupId(any(), any()))
        .thenReturn(List.of(barrierExecutionInstanceBeforeFinalUpdate));
    ArgumentCaptor<List<BarrierPosition>> updatedPositionsCaptor3 = ArgumentCaptor.forClass(List.class);
    barrierWithinStrategyExpander.onInitializeRequest(barrierExpandRequest2);
    verify(barrierService, times(2))
        .updateBarrierPositionInfoListAndStrategyConcurrency(eq(barrierIdentifier), eq(planExecutionId),
            updatedPositionsCaptor3.capture(), eq(stepGroup1SetupId), eq(2));
    List<BarrierPosition> updatedPositions3 = updatedPositionsCaptor3.getValue();
    assertThat(updatedPositions3.size()).isEqualTo(5);
    assertThat(updatedPositions3.stream().filter(position -> position.getStepSetupId().equals(step2SetupId)).count())
        .isEqualTo(4);
    assertThat(updatedPositions3.stream()
                   .filter(position -> position.getStepSetupId().equals(step2SetupId))
                   .map(BarrierPosition::getStageRuntimeId)
                   .collect(Collectors.toSet()))
        .containsExactlyInAnyOrder(stage2ExecutionId);
    assertThat(updatedPositions3.stream()
                   .filter(position -> position.getStepSetupId().equals(step2SetupId))
                   .map(BarrierPosition::getStepGroupRuntimeId)
                   .collect(Collectors.toSet()))
        .containsExactlyInAnyOrder(stepGroup1RuntimeIds.get(0), stepGroup1RuntimeIds.get(1));
    assertThat(updatedPositions3.stream().filter(BarrierPosition::getIsDummyPosition).count()).isEqualTo(0);
    assertThat(updatedPositions3.stream().filter(position -> !position.getStepSetupId().equals(step2SetupId)).count())
        .isEqualTo(1);
    assertThat(updatedPositions3.stream()
                   .filter(position -> !position.getStepSetupId().equals(step2SetupId))
                   .map(BarrierPosition::getStageRuntimeId)
                   .collect(Collectors.toSet()))
        .containsOnlyNulls();
    assertThat(updatedPositions3.stream()
                   .filter(position -> !position.getStepSetupId().equals(step2SetupId))
                   .map(BarrierPosition::getStepGroupRuntimeId)
                   .collect(Collectors.toSet()))
        .containsOnlyNulls();
  }

  private BarrierExecutionInstance obtainBarrierExecutionInstanceWithNestedStrategy() {
    return BarrierExecutionInstance.builder()
        .uuid(generateUuid())
        .identifier(barrierIdentifier)
        .planExecutionId(planExecutionId)
        .barrierState(STANDING)
        .setupInfo(BarrierSetupInfo.builder().build())
        .positionInfo(
            BarrierPositionInfo.builder()
                .planExecutionId(planExecutionId)
                .barrierPositionList(List.of(BarrierPositionInfo.BarrierPosition.builder()
                                                 .stageSetupId(stage2SetupId)
                                                 .stepSetupId(step2SetupId)
                                                 .stepGroupSetupId(stepGroup1SetupId)
                                                 .strategySetupId(stepGroup1SetupId)
                                                 .allStrategySetupIds(List.of(stage1SetupId, stepGroup1SetupId))
                                                 .isDummyPosition(true)
                                                 .strategyNodeType(BarrierPositionType.STEP_GROUP)
                                                 .build(),
                    BarrierPositionInfo.BarrierPosition.builder()
                        .stageSetupId(stage2SetupId)
                        .stepSetupId(step3SetupId)
                        .stepGroupSetupId(stepGroup2SetupId)
                        .isDummyPosition(false)
                        .build()))
                .build())
        .build();
  }
}
