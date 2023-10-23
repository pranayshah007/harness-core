/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.expressions.functors;

import static io.harness.rule.OwnerRule.ARCHIT;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.UUIDGenerator;
import io.harness.engine.expressions.NodeExecutionsCache;
import io.harness.engine.expressions.OrchestrationConstants;
import io.harness.execution.NodeExecution;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.StrategyMetadata;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.rule.Owner;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class NodeExecutionMapTest extends CategoryTest {
  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testFetchExecutionUrl() {
    // Ambiance of stage level without matrix

    String stageSetupId = UUIDGenerator.generateUuid();
    String stageRuntimeId = UUIDGenerator.generateUuid();

    Ambiance ambiance = Ambiance.newBuilder().addAllLevels(getNormalStageLevels(stageRuntimeId, stageSetupId)).build();

    NodeExecutionsCache nodeExecutionsCache = NodeExecutionsCache.builder().build();
    String nodeExecutionId = UUIDGenerator.generateUuid();
    nodeExecutionsCache.getAmbianceMap().put(nodeExecutionId, ambiance);
    NodeExecutionMap nodeExecutionMap =
        NodeExecutionMap.builder()
            .nodeExecution(NodeExecution.builder().uuid(nodeExecutionId).ambiance(ambiance).build())
            .nodeExecutionsCache(nodeExecutionsCache)
            .ambiance(ambiance)
            .build();
    Optional<Object> executionUrl = nodeExecutionMap.fetchExecutionUrl(OrchestrationConstants.EXECUTION_URL);
    assertThat(executionUrl).isPresent();
    assertThat((String) executionUrl.get())
        .isEqualTo(String.format("<+<+pipeline.executionUrl>+'?stage=%s'>", stageSetupId));

    // Stage inside parallel block
    ambiance = Ambiance.newBuilder().addAllLevels(getStageLevelInsideParallel(stageRuntimeId, stageSetupId)).build();
    nodeExecutionsCache.getAmbianceMap().put(nodeExecutionId, ambiance);
    nodeExecutionMap = NodeExecutionMap.builder()
                           .nodeExecution(NodeExecution.builder().uuid(nodeExecutionId).ambiance(ambiance).build())
                           .nodeExecutionsCache(nodeExecutionsCache)
                           .ambiance(ambiance)
                           .build();
    executionUrl = nodeExecutionMap.fetchExecutionUrl(OrchestrationConstants.EXECUTION_URL);
    assertThat(executionUrl).isPresent();
    assertThat((String) executionUrl.get())
        .isEqualTo(String.format("<+<+pipeline.executionUrl>+'?stage=%s'>", stageSetupId));

    String stepRuntimeId = UUIDGenerator.generateUuid();
    Ambiance stepAmbiance =
        Ambiance.newBuilder().addAllLevels(getNormalStepLevels(stepRuntimeId, stageRuntimeId, stageSetupId)).build();
    nodeExecutionsCache.getAmbianceMap().put(nodeExecutionId, stepAmbiance);
    nodeExecutionMap = NodeExecutionMap.builder()
                           .nodeExecution(NodeExecution.builder().uuid(nodeExecutionId).ambiance(stepAmbiance).build())
                           .nodeExecutionsCache(nodeExecutionsCache)
                           .ambiance(stepAmbiance)
                           .build();
    executionUrl = nodeExecutionMap.fetchExecutionUrl(OrchestrationConstants.EXECUTION_URL);
    assertThat(executionUrl).isPresent();
    assertThat((String) executionUrl.get())
        .isEqualTo(String.format("<+<+pipeline.executionUrl>+'?stage=%s\\&step=%s'>", stageSetupId, stepRuntimeId));

    // step inside parallel block
    stepAmbiance = Ambiance.newBuilder()
                       .addAllLevels(getStepLevelInsideParallel(stepRuntimeId, stageRuntimeId, stageSetupId))
                       .build();
    nodeExecutionsCache.getAmbianceMap().put(nodeExecutionId, stepAmbiance);

    nodeExecutionMap = NodeExecutionMap.builder()
                           .nodeExecution(NodeExecution.builder().uuid(nodeExecutionId).ambiance(stepAmbiance).build())
                           .nodeExecutionsCache(nodeExecutionsCache)
                           .ambiance(stepAmbiance)
                           .build();
    executionUrl = nodeExecutionMap.fetchExecutionUrl(OrchestrationConstants.EXECUTION_URL);
    assertThat(executionUrl).isPresent();
    assertThat((String) executionUrl.get())
        .isEqualTo(String.format("<+<+pipeline.executionUrl>+'?stage=%s\\&step=%s'>", stageSetupId, stepRuntimeId));
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testFetchExecutionUrlForStrategy() {
    // Ambiance of stage level without matrix

    String stageSetupId = UUIDGenerator.generateUuid();
    String stageRuntimeId = UUIDGenerator.generateUuid();

    NodeExecutionsCache nodeExecutionsCache = NodeExecutionsCache.builder().build();
    String nodeExecutionId = UUIDGenerator.generateUuid();

    Ambiance ambiance = Ambiance.newBuilder()
                            .addAllLevels(getStageLevelInsideStrategy(stageRuntimeId, stageSetupId))
                            .setStageExecutionId(stageRuntimeId)
                            .build();
    nodeExecutionsCache.getAmbianceMap().put(nodeExecutionId, ambiance);

    NodeExecutionMap nodeExecutionMap =
        NodeExecutionMap.builder()
            .nodeExecution(NodeExecution.builder().uuid(nodeExecutionId).ambiance(ambiance).build())
            .ambiance(ambiance)
            .nodeExecutionsCache(nodeExecutionsCache)
            .build();
    Optional<Object> executionUrl = nodeExecutionMap.fetchExecutionUrl(OrchestrationConstants.EXECUTION_URL);
    assertThat(executionUrl).isPresent();
    assertThat((String) executionUrl.get())
        .isEqualTo(
            String.format("<+<+pipeline.executionUrl>+'?stage=%s\\&stageExecId=%s'>", stageSetupId, stageRuntimeId));

    // Stage having strategy inside parallel block
    ambiance = Ambiance.newBuilder()
                   .addAllLevels(getStageLevelInsideStrategyInParallel(stageRuntimeId, stageSetupId))
                   .setStageExecutionId(stageRuntimeId)
                   .build();
    nodeExecutionsCache.getAmbianceMap().put(nodeExecutionId, ambiance);

    nodeExecutionMap = NodeExecutionMap.builder()
                           .nodeExecution(NodeExecution.builder().uuid(nodeExecutionId).ambiance(ambiance).build())
                           .ambiance(ambiance)
                           .nodeExecutionsCache(nodeExecutionsCache)
                           .build();
    executionUrl = nodeExecutionMap.fetchExecutionUrl(OrchestrationConstants.EXECUTION_URL);
    assertThat(executionUrl).isPresent();
    assertThat((String) executionUrl.get())
        .isEqualTo(
            String.format("<+<+pipeline.executionUrl>+'?stage=%s\\&stageExecId=%s'>", stageSetupId, stageRuntimeId));

    // Only step is inside strategy
    String stepRuntimeId = UUIDGenerator.generateUuid();
    Ambiance stepAmbiance = Ambiance.newBuilder()
                                .addAllLevels(getStepLevelInsideStrategy(stepRuntimeId, stageRuntimeId, stageSetupId))
                                .setStageExecutionId(stageRuntimeId)
                                .build();
    nodeExecutionsCache.getAmbianceMap().put(nodeExecutionId, stepAmbiance);

    nodeExecutionMap = NodeExecutionMap.builder()
                           .nodeExecution(NodeExecution.builder().uuid(nodeExecutionId).ambiance(stepAmbiance).build())
                           .ambiance(stepAmbiance)
                           .nodeExecutionsCache(nodeExecutionsCache)
                           .build();
    executionUrl = nodeExecutionMap.fetchExecutionUrl(OrchestrationConstants.EXECUTION_URL);
    assertThat(executionUrl).isPresent();
    assertThat((String) executionUrl.get())
        .isEqualTo(String.format("<+<+pipeline.executionUrl>+'?stage=%s\\&step=%s'>", stageSetupId, stepRuntimeId));

    // step having strategy inside parallel block and not stage
    stepAmbiance = Ambiance.newBuilder()
                       .addAllLevels(getStepLevelInsideStrategyInParallel(stepRuntimeId, stageRuntimeId, stageSetupId))
                       .setStageExecutionId(stageRuntimeId)
                       .build();
    nodeExecutionsCache.getAmbianceMap().put(nodeExecutionId, stepAmbiance);

    nodeExecutionMap = NodeExecutionMap.builder()
                           .nodeExecution(NodeExecution.builder().uuid(nodeExecutionId).ambiance(stepAmbiance).build())
                           .ambiance(stepAmbiance)
                           .nodeExecutionsCache(nodeExecutionsCache)
                           .build();
    executionUrl = nodeExecutionMap.fetchExecutionUrl(OrchestrationConstants.EXECUTION_URL);
    assertThat(executionUrl).isPresent();
    assertThat((String) executionUrl.get())
        .isEqualTo(String.format("<+<+pipeline.executionUrl>+'?stage=%s\\&step=%s'>", stageSetupId, stepRuntimeId));

    // step having stage strategy
    stepAmbiance = Ambiance.newBuilder()
                       .addAllLevels(getStepLevelInsideStageStrategy(stepRuntimeId, stageRuntimeId, stageSetupId))
                       .setStageExecutionId(stageRuntimeId)
                       .build();
    nodeExecutionsCache.getAmbianceMap().put(nodeExecutionId, stepAmbiance);

    nodeExecutionMap = NodeExecutionMap.builder()
                           .nodeExecution(NodeExecution.builder().uuid(nodeExecutionId).ambiance(stepAmbiance).build())
                           .ambiance(stepAmbiance)
                           .nodeExecutionsCache(nodeExecutionsCache)
                           .build();
    executionUrl = nodeExecutionMap.fetchExecutionUrl(OrchestrationConstants.EXECUTION_URL);
    assertThat(executionUrl).isPresent();
    assertThat((String) executionUrl.get())
        .isEqualTo(String.format("<+<+pipeline.executionUrl>+'?stage=%s\\&stageExecId=%s\\&step=%s'>", stageSetupId,
            stageRuntimeId, stepRuntimeId));
  }

  private List<Level> getNormalStageLevels(String stageRuntimeId, String stageSetupId) {
    List<Level> levelList = new LinkedList<>();
    levelList.add(
        Level.newBuilder()
            .setRuntimeId(UUIDGenerator.generateUuid())
            .setSetupId(UUIDGenerator.generateUuid())
            .setStepType(StepType.newBuilder().setStepCategory(StepCategory.PIPELINE).setType("pipeline").build())
            .build());
    levelList.add(Level.newBuilder()
                      .setRuntimeId(UUIDGenerator.generateUuid())
                      .setSetupId(UUIDGenerator.generateUuid())
                      .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STAGES).setType("stages").build())
                      .build());
    levelList.add(Level.newBuilder()
                      .setRuntimeId(stageRuntimeId)
                      .setSetupId(stageSetupId)
                      .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).setType("stage1").build())
                      .build());
    return levelList;
  }

  private List<Level> getNormalStepLevels(String stepRuntimeId, String stageRuntimeId, String stageSetupId) {
    List<Level> levels = getNormalStageLevels(stageRuntimeId, stageSetupId);
    levels.add(Level.newBuilder()
                   .setRuntimeId(stepRuntimeId)
                   .setSetupId(UUIDGenerator.generateUuid())
                   .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STEP).setType("shellscript").build())
                   .build());
    return levels;
  }

  private List<Level> getStageLevelInsideParallel(String stageRuntimeId, String stageSetupId) {
    List<Level> stageLevels = getNormalStageLevels(stageRuntimeId, stageSetupId);
    // Add parallel block to it
    stageLevels.add(2,
        Level.newBuilder()
            .setRuntimeId(UUIDGenerator.generateUuid())
            .setSetupId(UUIDGenerator.generateUuid())
            .setStepType(StepType.newBuilder().setStepCategory(StepCategory.FORK).setType("section").build())
            .build());
    return stageLevels;
  }

  private List<Level> getStepLevelInsideParallel(String stepRuntimeId, String stageRuntimeId, String stageSetupId) {
    List<Level> stepLevels = getNormalStepLevels(stepRuntimeId, stageRuntimeId, stageSetupId);
    // Add parallel block to it
    stepLevels.add(3,
        Level.newBuilder()
            .setRuntimeId(UUIDGenerator.generateUuid())
            .setSetupId(UUIDGenerator.generateUuid())
            .setStepType(StepType.newBuilder().setStepCategory(StepCategory.FORK).setType("section").build())
            .build());
    return stepLevels;
  }

  private List<Level> getStageLevelInsideStrategy(String stageRuntimeId, String stageSetupId) {
    List<Level> stageLevels = getNormalStageLevels(stageRuntimeId, stageSetupId);
    // Add strategy block to it
    stageLevels.add(2,
        Level.newBuilder()
            .setRuntimeId(UUIDGenerator.generateUuid())
            .setSetupId(UUIDGenerator.generateUuid())
            .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STRATEGY).setType("strategy").build())
            .build());
    stageLevels.set(3,
        Level.newBuilder()
            .setRuntimeId(stageRuntimeId)
            .setSetupId(stageSetupId)
            .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).setType("stage1").build())
            .setStrategyMetadata(StrategyMetadata.newBuilder().build())
            .build());
    return stageLevels;
  }

  private List<Level> getStepLevelInsideStrategy(String stepRuntimeId, String stageRuntimeId, String stageSetupId) {
    List<Level> stepLevels = getNormalStepLevels(stepRuntimeId, stageRuntimeId, stageSetupId);
    // Add strategy block to it
    stepLevels.add(3,
        Level.newBuilder()
            .setRuntimeId(UUIDGenerator.generateUuid())
            .setSetupId(UUIDGenerator.generateUuid())
            .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STRATEGY).setType("strategy").build())
            .build());

    stepLevels.add(4,
        Level.newBuilder()
            .setRuntimeId(UUIDGenerator.generateUuid())
            .setSetupId(UUIDGenerator.generateUuid())
            .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STEP).setType("shellscript").build())
            .setStrategyMetadata(StrategyMetadata.newBuilder().build())
            .build());
    return stepLevels;
  }

  private List<Level> getStageLevelInsideStrategyInParallel(String stageRuntimeId, String stageSetupId) {
    List<Level> stageLevels = getStageLevelInsideParallel(stageRuntimeId, stageSetupId);
    // Add strategy block to it
    stageLevels.add(3,
        Level.newBuilder()
            .setRuntimeId(UUIDGenerator.generateUuid())
            .setSetupId(UUIDGenerator.generateUuid())
            .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STRATEGY).setType("strategy").build())
            .build());
    stageLevels.set(4,
        Level.newBuilder()
            .setRuntimeId(stageRuntimeId)
            .setSetupId(stageSetupId)
            .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).setType("stage1").build())
            .setStrategyMetadata(StrategyMetadata.newBuilder().build())
            .build());
    return stageLevels;
  }

  private List<Level> getStepLevelInsideStrategyInParallel(
      String stepRuntimeId, String stageRuntimeId, String stageSetupId) {
    List<Level> stepLevels = getStepLevelInsideParallel(stepRuntimeId, stageRuntimeId, stageSetupId);
    // Add strategy block to it
    stepLevels.add(4,
        Level.newBuilder()
            .setRuntimeId(UUIDGenerator.generateUuid())
            .setSetupId(UUIDGenerator.generateUuid())
            .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STRATEGY).setType("strategy").build())
            .setStrategyMetadata(StrategyMetadata.newBuilder().build())
            .build());
    stepLevels.add(5,
        Level.newBuilder()
            .setRuntimeId(UUIDGenerator.generateUuid())
            .setSetupId(UUIDGenerator.generateUuid())
            .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STEP).setType("shellscript").build())
            .setStrategyMetadata(StrategyMetadata.newBuilder().build())
            .build());
    return stepLevels;
  }

  private List<Level> getStepLevelInsideStageStrategy(
      String stepRuntimeId, String stageRuntimeId, String stageSetupId) {
    List<Level> stepLevels = getNormalStepLevels(stepRuntimeId, stageRuntimeId, stageSetupId);

    stepLevels.add(2,
        Level.newBuilder()
            .setRuntimeId(UUIDGenerator.generateUuid())
            .setSetupId(UUIDGenerator.generateUuid())
            .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STRATEGY).setType("strategy").build())
            .build());
    stepLevels.set(3,
        Level.newBuilder()
            .setRuntimeId(stageRuntimeId)
            .setSetupId(stageSetupId)
            .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).setType("stage1").build())
            .setStrategyMetadata(StrategyMetadata.newBuilder().build())
            .build());
    return stepLevels;
  }
}
