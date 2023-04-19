/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.barriers.service;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.distribution.barrier.Barrier.State.STANDING;
import static io.harness.rule.OwnerRule.ALEXEI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.harness.PipelineServiceTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.utils.PmsLevelUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.NodeExecution;
import io.harness.plan.PlanNode;
import io.harness.pms.barriers.beans.BarrierExecutionInfo;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.repositories.TimeoutInstanceRepository;
import io.harness.rule.Owner;
import io.harness.steps.barriers.beans.BarrierExecutionInstance;
import io.harness.steps.barriers.beans.BarrierPositionInfo;
import io.harness.steps.barriers.beans.BarrierPositionInfo.BarrierPosition;
import io.harness.steps.barriers.beans.BarrierSetupInfo;
import io.harness.steps.barriers.beans.StageDetail;
import io.harness.steps.barriers.service.BarrierService;
import io.harness.timeout.TimeoutInstance;
import io.harness.timeout.trackers.absolute.AbsoluteTimeoutTracker;

import io.fabric8.utils.Lists;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.internal.util.collections.Sets;

@OwnedBy(HarnessTeam.PIPELINE)
public class PMSBarrierServiceTest extends PipelineServiceTestBase {
  @Mock private BarrierService barrierService;
  @Mock private NodeExecutionService nodeExecutionService;
  @Mock private TimeoutInstanceRepository timeoutInstanceRepository;
  private PMSBarrierServiceImpl pmsBarrierService;

  @Before
  public void setUp() {
    pmsBarrierService = new PMSBarrierServiceImpl(nodeExecutionService, barrierService, timeoutInstanceRepository);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestGetBarrierExecutionInfoList() {
    String nodeRuntimeId = generateUuid();
    PlanNode planNode =
        PlanNode.builder()
            .uuid(generateUuid())
            .identifier(generateUuid())
            .name("stage")
            .stepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).setType("STAGE").build())
            .build();
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(generateUuid())
                            .addLevels(PmsLevelUtils.buildLevelFromNode(nodeRuntimeId, planNode))
                            .build();
    NodeExecution stageNode = NodeExecution.builder()
                                  .uuid("stageNode")
                                  .status(Status.RUNNING)
                                  .parentId(generateUuid())
                                  .planNode(planNode)
                                  .ambiance(ambiance)
                                  .version(1L)
                                  .build();

    BarrierExecutionInstance instance1 =
        BarrierExecutionInstance.builder()
            .uuid(generateUuid())
            .name(generateUuid())
            .barrierState(STANDING)
            .identifier(generateUuid())
            .planExecutionId(ambiance.getPlanExecutionId())
            .setupInfo(
                BarrierSetupInfo.builder()
                    .stages(Sets.newSet(
                        StageDetail.builder().name(planNode.getName()).identifier(planNode.getIdentifier()).build()))
                    .build())
            .positionInfo(BarrierPositionInfo.builder()
                              .barrierPositionList(
                                  Lists.newArrayList(BarrierPosition.builder().stepRuntimeId(nodeRuntimeId).build()))
                              .build())
            .build();

    when(nodeExecutionService.getByPlanNodeUuid(stageNode.getUuid(), ambiance.getPlanExecutionId()))
        .thenReturn(stageNode);

    when(barrierService.findByStageIdentifierAndPlanExecutionIdAnsStateIn(anyString(), anyString(), anySet()))
        .thenReturn(Lists.newArrayList(instance1));

    when(nodeExecutionService.get(nodeRuntimeId)).thenThrow(new InvalidRequestException("Exception"));

    List<BarrierExecutionInfo> barrierExecutionInfoList =
        pmsBarrierService.getBarrierExecutionInfoList(stageNode.getUuid(), ambiance.getPlanExecutionId());

    assertThat(barrierExecutionInfoList).isNotNull();
    assertThat(barrierExecutionInfoList.size()).isEqualTo(1);

    BarrierExecutionInfo barrierExecutionInfo = barrierExecutionInfoList.get(0);
    assertThat(barrierExecutionInfo).isNotNull();
    assertThat(barrierExecutionInfo.getName()).isEqualTo(instance1.getName());
    assertThat(barrierExecutionInfo.getIdentifier()).isEqualTo(instance1.getIdentifier());
    assertThat(barrierExecutionInfo.getStartedAt()).isEqualTo(0);
    assertThat(barrierExecutionInfo.isStarted()).isFalse();
    assertThat(barrierExecutionInfo.getStages())
        .containsExactlyInAnyOrder(
            StageDetail.builder().identifier(planNode.getIdentifier()).name(planNode.getName()).build());
    assertThat(barrierExecutionInfo.getTimeoutIn()).isEqualTo(0);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestGetBarrierExecutionInfo() {
    Ambiance ambiance = Ambiance.newBuilder().setPlanExecutionId(generateUuid()).build();
    String planNodeId = generateUuid();
    String timeoutId = generateUuid();
    long currentTimeMillis = System.currentTimeMillis();
    long timeoutIn = TimeUnit.MINUTES.toMillis(2);

    NodeExecution nodeExecution = NodeExecution.builder()
                                      .startTs(currentTimeMillis)
                                      .timeoutInstanceIds(Collections.singletonList(timeoutId))
                                      .build();

    TimeoutInstance timeoutInstance = TimeoutInstance.builder().tracker(new AbsoluteTimeoutTracker(timeoutIn)).build();

    BarrierExecutionInstance instance1 =
        BarrierExecutionInstance.builder()
            .uuid(generateUuid())
            .name(generateUuid())
            .barrierState(STANDING)
            .identifier(generateUuid())
            .planExecutionId(ambiance.getPlanExecutionId())
            .setupInfo(BarrierSetupInfo.builder()
                           .stages(Sets.newSet(
                               StageDetail.builder().name("stage-name").identifier("stage-identifier").build()))
                           .build())
            .positionInfo(
                BarrierPositionInfo.builder()
                    .barrierPositionList(Lists.newArrayList(BarrierPosition.builder().stepSetupId(planNodeId).build()))
                    .build())
            .build();

    when(barrierService.findByPlanNodeIdAndPlanExecutionId(planNodeId, ambiance.getPlanExecutionId()))
        .thenReturn(instance1);
    when(nodeExecutionService.getByPlanNodeUuid(planNodeId, ambiance.getPlanExecutionId())).thenReturn(nodeExecution);
    when(timeoutInstanceRepository.findAllById(nodeExecution.getTimeoutInstanceIds()))
        .thenReturn(Lists.newArrayList(timeoutInstance));

    BarrierExecutionInfo barrierExecutionInfo =
        pmsBarrierService.getBarrierExecutionInfo(planNodeId, ambiance.getPlanExecutionId());

    assertThat(barrierExecutionInfo).isNotNull();
    assertThat(barrierExecutionInfo.getName()).isEqualTo(instance1.getName());
    assertThat(barrierExecutionInfo.getIdentifier()).isEqualTo(instance1.getIdentifier());
    assertThat(barrierExecutionInfo.getStartedAt()).isEqualTo(0);
    assertThat(barrierExecutionInfo.isStarted()).isFalse();
    assertThat(barrierExecutionInfo.getStages())
        .containsExactlyInAnyOrder(StageDetail.builder().identifier("stage-identifier").name("stage-name").build());
    assertThat(barrierExecutionInfo.getTimeoutIn()).isCloseTo(timeoutIn, offset(100L));
  }
}
