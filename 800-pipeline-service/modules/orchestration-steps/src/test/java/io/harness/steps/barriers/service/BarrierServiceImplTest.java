/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.barriers.service;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.distribution.barrier.Barrier.State.DOWN;
import static io.harness.distribution.barrier.Barrier.State.ENDURE;
import static io.harness.distribution.barrier.Barrier.State.STANDING;
import static io.harness.distribution.barrier.Barrier.State.TIMED_OUT;
import static io.harness.rule.OwnerRule.ALEXEI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;

import io.harness.OrchestrationStepsTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.NodeExecution;
import io.harness.execution.PlanExecution;
import io.harness.pms.contracts.execution.Status;
import io.harness.repositories.BarrierNodeRepository;
import io.harness.rule.Owner;
import io.harness.steps.barriers.beans.BarrierExecutionInstance;
import io.harness.steps.barriers.beans.BarrierPositionInfo;
import io.harness.steps.barriers.beans.BarrierPositionInfo.BarrierPosition;
import io.harness.steps.barriers.beans.BarrierPositionInfo.BarrierPosition.BarrierPositionType;
import io.harness.steps.barriers.beans.BarrierSetupInfo;
import io.harness.steps.barriers.beans.StageDetail;
import io.harness.testlib.RealMongo;
import io.harness.waiter.WaitNotifyEngine;

import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.assertj.core.util.Lists;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.internal.util.collections.Sets;
import org.springframework.data.mongodb.core.MongoTemplate;

@OwnedBy(HarnessTeam.PIPELINE)
public class BarrierServiceImplTest extends OrchestrationStepsTestBase {
  @Inject private BarrierNodeRepository barrierNodeRepository;
  @Inject private MongoTemplate mongoTemplate;
  @Mock private NodeExecutionService nodeExecutionService;
  @Mock private PlanExecutionService planExecutionService;
  @Mock private WaitNotifyEngine waitNotifyEngine;
  @InjectMocks @Inject BarrierServiceImpl barrierService;

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  @RealMongo
  public void shouldSaveBarrierNode() {
    String uuid = generateUuid();
    String planExecutionId = generateUuid();
    BarrierExecutionInstance barrierExecutionInstance =
        BarrierExecutionInstance.builder().uuid(uuid).identifier("identifier").planExecutionId(planExecutionId).build();
    BarrierExecutionInstance savedBarrierExecutionInstance = barrierService.save(barrierExecutionInstance);

    assertThat(savedBarrierExecutionInstance).isNotNull();
    assertThat(savedBarrierExecutionInstance.getUuid()).isEqualTo(uuid);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  @RealMongo
  public void shouldGetSavedBarrierNode() {
    String uuid = generateUuid();
    String planExecutionId = generateUuid();
    BarrierExecutionInstance barrierExecutionInstance =
        BarrierExecutionInstance.builder().uuid(uuid).identifier("identifier").planExecutionId(planExecutionId).build();
    barrierService.save(barrierExecutionInstance);

    BarrierExecutionInstance savedBarrierExecutionInstance = barrierService.get(barrierExecutionInstance.getUuid());

    assertThat(savedBarrierExecutionInstance).isNotNull();
    assertThat(savedBarrierExecutionInstance.getUuid()).isEqualTo(uuid);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  @RealMongo
  public void shouldSaveAllBarrierNode() {
    String identifier = "identifier";
    String planExecutionId = generateUuid();
    BarrierExecutionInstance barrierExecutionInstance = BarrierExecutionInstance.builder()
                                                            .uuid(generateUuid())
                                                            .identifier(identifier)
                                                            .planExecutionId(planExecutionId)
                                                            .build();
    BarrierExecutionInstance barrierExecutionInstance1 = BarrierExecutionInstance.builder()
                                                             .uuid(generateUuid())
                                                             .identifier(identifier)
                                                             .planExecutionId(planExecutionId)
                                                             .build();
    List<BarrierExecutionInstance> savedBarrierExecutionInstances =
        barrierService.saveAll(ImmutableList.of(barrierExecutionInstance, barrierExecutionInstance1));

    assertThat(savedBarrierExecutionInstances).isNotNull();
    assertThat(savedBarrierExecutionInstances).isNotEmpty();
    assertThat(savedBarrierExecutionInstances)
        .containsExactlyInAnyOrder(barrierExecutionInstance, barrierExecutionInstance1);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  @RealMongo
  public void shouldThrowInvalidRequestException() {
    String uuid = generateUuid();
    assertThatThrownBy(() -> barrierService.get(uuid))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Barrier not found for id: " + uuid);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  @RealMongo
  public void shouldFindByIdentifier() {
    String identifier = generateUuid();
    String planExecutionId = generateUuid();

    BarrierExecutionInstance bar = BarrierExecutionInstance.builder()
                                       .uuid(generateUuid())
                                       .identifier(identifier)
                                       .planExecutionId(planExecutionId)
                                       .build();
    barrierNodeRepository.save(bar);

    BarrierExecutionInstance barrierExecutionInstance =
        barrierService.findByIdentifierAndPlanExecutionId(identifier, planExecutionId);

    assertThat(barrierExecutionInstance).isNotNull();
    assertThat(barrierExecutionInstance).isEqualTo(bar);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  @RealMongo
  public void shouldUpdateState() {
    String uuid = generateUuid();
    String planExecutionId = generateUuid();
    BarrierExecutionInstance barrierExecutionInstance =
        BarrierExecutionInstance.builder().uuid(uuid).identifier("identifier").planExecutionId(planExecutionId).build();
    barrierService.save(barrierExecutionInstance);

    BarrierExecutionInstance savedBarrierExecutionInstance = barrierService.get(barrierExecutionInstance.getUuid());
    assertThat(savedBarrierExecutionInstance).isNotNull();

    barrierService.updateState(savedBarrierExecutionInstance.getUuid(), DOWN);
    BarrierExecutionInstance savedBarrier = barrierService.get(savedBarrierExecutionInstance.getUuid());
    assertThat(savedBarrier).isNotNull();
    assertThat(savedBarrier.getBarrierState()).isEqualTo(DOWN);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  @RealMongo
  public void shouldFindByStageIdentifierAndPlanExecutionIdAnsStateIn() {
    String planExecutionId = generateUuid();
    String stageIdentifier = generateUuid();

    List<BarrierExecutionInstance> barrierExecutionInstances = Lists.newArrayList(
        BarrierExecutionInstance.builder()
            .uuid(generateUuid())
            .name(generateUuid())
            .barrierState(STANDING)
            .identifier(generateUuid())
            .planExecutionId(planExecutionId)
            .setupInfo(BarrierSetupInfo.builder()
                           .stages(Sets.newSet(StageDetail.builder().identifier(stageIdentifier).build()))
                           .build())
            .build(),
        BarrierExecutionInstance.builder()
            .uuid(generateUuid())
            .name(generateUuid())
            .barrierState(DOWN)
            .identifier(generateUuid())
            .planExecutionId(planExecutionId)
            .setupInfo(BarrierSetupInfo.builder()
                           .stages(Sets.newSet(StageDetail.builder().identifier(stageIdentifier).build()))
                           .build())
            .build());
    mongoTemplate.insertAll(barrierExecutionInstances);

    List<BarrierExecutionInstance> barrierNodeExecutions =
        barrierService.findByStageIdentifierAndPlanExecutionIdAnsStateIn(
            stageIdentifier, planExecutionId, Sets.newSet(STANDING));

    assertThat(barrierNodeExecutions).isNotNull();
    assertThat(barrierNodeExecutions.size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestGetBarrierSetupInfoList() throws IOException {
    ClassLoader classLoader = getClass().getClassLoader();
    String yamlFile = "barriers.yaml";
    String yaml = Resources.toString(Objects.requireNonNull(classLoader.getResource(yamlFile)), StandardCharsets.UTF_8);

    List<BarrierSetupInfo> barrierSetupInfoList = barrierService.getBarrierSetupInfoList(yaml);

    assertThat(barrierSetupInfoList.size()).isEqualTo(3);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldThrowIOExceptionWhenGetBarrierSetupInfoList() {
    String incorrectYaml = "pipeline: stages: stage";
    assertThatThrownBy(() -> barrierService.getBarrierSetupInfoList(incorrectYaml))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Error while extracting yaml");
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldThrowInvalidRequestExceptionWhenGetBarrierSetupInfoList() throws IOException {
    ClassLoader classLoader = getClass().getClassLoader();
    String yamlFile = "barriers-incorrect.yaml";
    String yaml = Resources.toString(Objects.requireNonNull(classLoader.getResource(yamlFile)), StandardCharsets.UTF_8);

    assertThatThrownBy(() -> barrierService.getBarrierSetupInfoList(yaml))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Barrier Identifier myBarrierId7 was not present in flowControl");
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestGetBarrierPositionInfoMap() throws IOException {
    ClassLoader classLoader = getClass().getClassLoader();
    String yamlFile = "barriers.yaml";
    String yaml = Resources.toString(Objects.requireNonNull(classLoader.getResource(yamlFile)), StandardCharsets.UTF_8);

    Map<String, List<BarrierPosition>> barrierPositionInfoMap = barrierService.getBarrierPositionInfoList(yaml);

    assertThat(barrierPositionInfoMap.size()).isEqualTo(3);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldThrowIOExceptionWhenGetBarrierPositionInfoMap() {
    String incorrectYaml = "pipeline: stages: stage";
    assertThatThrownBy(() -> barrierService.getBarrierPositionInfoList(incorrectYaml))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Error while extracting yaml");
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldThrowInvalidRequestExceptionWhenGetBarrierPositionInfo() throws IOException {
    ClassLoader classLoader = getClass().getClassLoader();
    String yamlFile = "barriers-incorrect.yaml";
    String yaml = Resources.toString(Objects.requireNonNull(classLoader.getResource(yamlFile)), StandardCharsets.UTF_8);

    assertThatThrownBy(() -> barrierService.getBarrierPositionInfoList(yaml))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Barrier Identifier myBarrierId7 was not present in flowControl");
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  @RealMongo
  public void shouldFindByPlanNodeIdAndPlanExecutionId() {
    String identifier = "identifier";
    String planExecutionId = generateUuid();
    String planNodeId = generateUuid();
    BarrierExecutionInstance barrierExecutionInstance =
        BarrierExecutionInstance.builder()
            .uuid(generateUuid())
            .identifier(identifier)
            .planExecutionId(planExecutionId)
            .positionInfo(
                BarrierPositionInfo.builder()
                    .planExecutionId(planExecutionId)
                    .barrierPositionList(ImmutableList.of(BarrierPosition.builder().stepSetupId(planNodeId).build()))
                    .build())
            .build();
    barrierService.save(barrierExecutionInstance);

    BarrierExecutionInstance result = barrierService.findByPlanNodeIdAndPlanExecutionId(planNodeId, planExecutionId);

    assertThat(result).isNotNull();
    assertThat(result).isEqualTo(barrierExecutionInstance);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  @RealMongo
  public void shouldUpdatePositionForStep() {
    String identifier = generateUuid();
    String planExecutionId = generateUuid();
    String planNodeId = generateUuid();
    String executionId = generateUuid();
    BarrierExecutionInstance barrierExecutionInstance =
        BarrierExecutionInstance.builder()
            .uuid(generateUuid())
            .identifier(identifier)
            .planExecutionId(planExecutionId)
            .positionInfo(
                BarrierPositionInfo.builder()
                    .planExecutionId(planExecutionId)
                    .barrierPositionList(ImmutableList.of(BarrierPosition.builder().stepSetupId(planNodeId).build()))
                    .build())
            .build();
    barrierService.save(barrierExecutionInstance);

    barrierService.updatePosition(planExecutionId, BarrierPositionType.STEP, planNodeId, executionId);

    List<BarrierExecutionInstance> result =
        barrierService.findByPosition(planExecutionId, BarrierPositionType.STEP, planNodeId);

    assertThat(result).isNotNull();
    assertThat(result).isNotEmpty();
    assertThat(result.size()).isEqualTo(1);
    assertThat(result.get(0).getPositionInfo().getBarrierPositionList()).isNotEmpty();
    assertThat(result.get(0).getPositionInfo().getBarrierPositionList().get(0).getStepRuntimeId())
        .isEqualTo(executionId);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  @RealMongo
  public void shouldUpdatePositionForStepGroup() {
    String identifier = generateUuid();
    String planExecutionId = generateUuid();
    String planNodeId = generateUuid();
    String executionId = generateUuid();
    BarrierExecutionInstance barrierExecutionInstance =
        BarrierExecutionInstance.builder()
            .uuid(generateUuid())
            .identifier(identifier)
            .planExecutionId(planExecutionId)
            .positionInfo(BarrierPositionInfo.builder()
                              .planExecutionId(planExecutionId)
                              .barrierPositionList(
                                  ImmutableList.of(BarrierPosition.builder().stepGroupSetupId(planNodeId).build()))
                              .build())
            .build();
    barrierService.save(barrierExecutionInstance);

    barrierService.updatePosition(planExecutionId, BarrierPositionType.STEP_GROUP, planNodeId, executionId);

    List<BarrierExecutionInstance> result =
        barrierService.findByPosition(planExecutionId, BarrierPositionType.STEP_GROUP, planNodeId);

    assertThat(result).isNotNull();
    assertThat(result).isNotEmpty();
    assertThat(result.size()).isEqualTo(1);
    assertThat(result.get(0).getPositionInfo().getBarrierPositionList()).isNotEmpty();
    assertThat(result.get(0).getPositionInfo().getBarrierPositionList().get(0).getStepGroupRuntimeId())
        .isEqualTo(executionId);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  @RealMongo
  public void shouldUpdatePositionForStage() {
    String identifier = generateUuid();
    String planExecutionId = generateUuid();
    String planNodeId = generateUuid();
    String executionId = generateUuid();
    BarrierExecutionInstance barrierExecutionInstance =
        BarrierExecutionInstance.builder()
            .uuid(generateUuid())
            .identifier(identifier)
            .planExecutionId(planExecutionId)
            .positionInfo(
                BarrierPositionInfo.builder()
                    .planExecutionId(planExecutionId)
                    .barrierPositionList(ImmutableList.of(BarrierPosition.builder().stageSetupId(planNodeId).build()))
                    .build())
            .build();
    barrierService.save(barrierExecutionInstance);

    barrierService.updatePosition(planExecutionId, BarrierPositionType.STAGE, planNodeId, executionId);

    List<BarrierExecutionInstance> result =
        barrierService.findByPosition(planExecutionId, BarrierPositionType.STAGE, planNodeId);

    assertThat(result).isNotNull();
    assertThat(result).isNotEmpty();
    assertThat(result.size()).isEqualTo(1);
    assertThat(result.get(0).getPositionInfo().getBarrierPositionList()).isNotEmpty();
    assertThat(result.get(0).getPositionInfo().getBarrierPositionList().get(0).getStageRuntimeId())
        .isEqualTo(executionId);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  @RealMongo
  public void shouldTestUpdateStanding() {
    BarrierExecutionInstance barrierExecutionInstance = obtainBarrierExecutionInstance();
    barrierService.save(barrierExecutionInstance);

    when(nodeExecutionService.get(anyString())).thenReturn(NodeExecution.builder().status(Status.SUCCEEDED).build());
    when(planExecutionService.get(anyString())).thenReturn(PlanExecution.builder().status(Status.RUNNING).build());

    barrierService.update(barrierExecutionInstance);
    BarrierExecutionInstance updated = barrierService.get(barrierExecutionInstance.getUuid());

    assertThat(updated).isNotNull();
    assertThat(updated.getBarrierState()).isEqualTo(DOWN);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  @RealMongo
  public void shouldTestUpdateEndure() {
    BarrierExecutionInstance barrierExecutionInstance = obtainBarrierExecutionInstance();
    barrierService.save(barrierExecutionInstance);

    when(waitNotifyEngine.doneWith(anyString(), any())).thenReturn("");
    when(planExecutionService.get(anyString())).thenReturn(PlanExecution.builder().status(Status.FAILED).build());

    barrierService.update(barrierExecutionInstance);
    BarrierExecutionInstance updated = barrierService.get(barrierExecutionInstance.getUuid());

    assertThat(updated).isNotNull();
    assertThat(updated.getBarrierState()).isEqualTo(ENDURE);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  @RealMongo
  public void shouldTestUpdateTimedOut() {
    BarrierExecutionInstance barrierExecutionInstance = obtainBarrierExecutionInstance();
    barrierService.save(barrierExecutionInstance);

    when(waitNotifyEngine.doneWith(anyString(), any())).thenReturn("");
    when(nodeExecutionService.get(anyString())).thenReturn(NodeExecution.builder().status(Status.EXPIRED).build());
    when(planExecutionService.get(anyString())).thenReturn(PlanExecution.builder().status(Status.RUNNING).build());

    barrierService.update(barrierExecutionInstance);
    BarrierExecutionInstance updated = barrierService.get(barrierExecutionInstance.getUuid());

    assertThat(updated).isNotNull();
    assertThat(updated.getBarrierState()).isEqualTo(TIMED_OUT);
  }

  private BarrierExecutionInstance obtainBarrierExecutionInstance() {
    String identifier = generateUuid();
    String planExecutionId = generateUuid();
    String stageSetupId = generateUuid();
    String stepSetupId = generateUuid();
    String stageExecutionId = generateUuid();
    String stepExecutionId = generateUuid();
    return BarrierExecutionInstance.builder()
        .uuid(generateUuid())
        .identifier(identifier)
        .planExecutionId(planExecutionId)
        .barrierState(STANDING)
        .positionInfo(BarrierPositionInfo.builder()
                          .planExecutionId(planExecutionId)
                          .barrierPositionList(ImmutableList.of(BarrierPosition.builder()
                                                                    .stageSetupId(stageSetupId)
                                                                    .stageRuntimeId(stageExecutionId)
                                                                    .stepSetupId(stepSetupId)
                                                                    .stepRuntimeId(stepExecutionId)
                                                                    .build()))
                          .build())
        .build();
  }
}
