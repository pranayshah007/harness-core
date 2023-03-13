/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.expressions.functors;

import static io.harness.rule.OwnerRule.SAHIL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.UUIDGenerator;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.StrategyMetadata;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.rule.Owner;

import com.google.common.collect.Lists;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ExpandedJsonFunctorUtilsTest extends CategoryTest {
  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testGetExpressionsForStage() {
    Ambiance ambiance = Ambiance.newBuilder()
                            .addAllLevels(getNormalStepLevels("stepRuntimeId", "stageRuntimeId", "stageSetupId"))
                            .build();
    String expression = "stage.identifier";
    assertThat(ExpandedJsonFunctorUtils.getExpressions(ambiance, Map.of("stage", "stage"), expression))
        .isEqualTo(Lists.newArrayList("expandedJson.pipeline.stages.stage1.identifier",
            "outcome.expandedJson.pipeline.stages.stage1.identifier",
            "stepInputs.expandedJson.pipeline.stages.stage1.identifier",
            "expandedJson.outcome.pipeline.stages.stage1.identifier",
            "expandedJson.stepInputs.pipeline.stages.stage1.identifier",
            "expandedJson.pipeline.outcome.stages.stage1.identifier",
            "expandedJson.pipeline.stepInputs.stages.stage1.identifier",
            "expandedJson.pipeline.stages.outcome.stage1.identifier",
            "expandedJson.pipeline.stages.stepInputs.stage1.identifier",
            "expandedJson.pipeline.stages.stage1.outcome.identifier",
            "expandedJson.pipeline.stages.stage1.stepInputs.identifier"));
    expression = "stage1.identifier";
    assertThat(ExpandedJsonFunctorUtils.getExpressions(ambiance, Map.of("stage", "stage"), expression))
        .isEqualTo(Lists.newArrayList("expandedJson.pipeline.stages.stage1.identifier",
            "outcome.expandedJson.pipeline.stages.stage1.identifier",
            "stepInputs.expandedJson.pipeline.stages.stage1.identifier",
            "expandedJson.outcome.pipeline.stages.stage1.identifier",
            "expandedJson.stepInputs.pipeline.stages.stage1.identifier",
            "expandedJson.pipeline.outcome.stages.stage1.identifier",
            "expandedJson.pipeline.stepInputs.stages.stage1.identifier",
            "expandedJson.pipeline.stages.outcome.stage1.identifier",
            "expandedJson.pipeline.stages.stepInputs.stage1.identifier",
            "expandedJson.pipeline.stages.stage1.outcome.identifier",
            "expandedJson.pipeline.stages.stage1.stepInputs.identifier"));
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testGetExpressionsForStep() {
    Ambiance ambiance = Ambiance.newBuilder()
                            .addAllLevels(getNormalStepLevels("stepRuntimeId", "stageRuntimeId", "stageSetupId"))
                            .build();
    String expression = "step.identifier";
    assertThat(ExpandedJsonFunctorUtils.getExpressions(ambiance, Map.of("step", "step"), expression))
        .isEqualTo(Lists.newArrayList("expandedJson.pipeline.stages.stage1.shell1.identifier",
            "outcome.expandedJson.pipeline.stages.stage1.shell1.identifier",
            "stepInputs.expandedJson.pipeline.stages.stage1.shell1.identifier",
            "expandedJson.outcome.pipeline.stages.stage1.shell1.identifier",
            "expandedJson.stepInputs.pipeline.stages.stage1.shell1.identifier",
            "expandedJson.pipeline.outcome.stages.stage1.shell1.identifier",
            "expandedJson.pipeline.stepInputs.stages.stage1.shell1.identifier",
            "expandedJson.pipeline.stages.outcome.stage1.shell1.identifier",
            "expandedJson.pipeline.stages.stepInputs.stage1.shell1.identifier",
            "expandedJson.pipeline.stages.stage1.outcome.shell1.identifier",
            "expandedJson.pipeline.stages.stage1.stepInputs.shell1.identifier",
            "expandedJson.pipeline.stages.stage1.shell1.outcome.identifier",
            "expandedJson.pipeline.stages.stage1.shell1.stepInputs.identifier"));
    expression = "shell1.identifier";
    assertThat(ExpandedJsonFunctorUtils.getExpressions(ambiance, Map.of("stage", "stage"), expression))
        .isEqualTo(Lists.newArrayList("expandedJson.pipeline.stages.stage1.shell1.identifier",
            "outcome.expandedJson.pipeline.stages.stage1.shell1.identifier",
            "stepInputs.expandedJson.pipeline.stages.stage1.shell1.identifier",
            "expandedJson.outcome.pipeline.stages.stage1.shell1.identifier",
            "expandedJson.stepInputs.pipeline.stages.stage1.shell1.identifier",
            "expandedJson.pipeline.outcome.stages.stage1.shell1.identifier",
            "expandedJson.pipeline.stepInputs.stages.stage1.shell1.identifier",
            "expandedJson.pipeline.stages.outcome.stage1.shell1.identifier",
            "expandedJson.pipeline.stages.stepInputs.stage1.shell1.identifier",
            "expandedJson.pipeline.stages.stage1.outcome.shell1.identifier",
            "expandedJson.pipeline.stages.stage1.stepInputs.shell1.identifier",
            "expandedJson.pipeline.stages.stage1.shell1.outcome.identifier",
            "expandedJson.pipeline.stages.stage1.shell1.stepInputs.identifier"));
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testGetExpressionsForStepInsideParallel() {
    Ambiance ambiance = Ambiance.newBuilder()
                            .addAllLevels(getStepLevelInsideParallel("stepRuntimeId", "stageRuntimeId", "stageSetupId"))
                            .build();
    String expression = "step.identifier";
    assertThat(ExpandedJsonFunctorUtils.getExpressions(ambiance, Map.of("step", "step"), expression))
        .isEqualTo(Lists.newArrayList("expandedJson.pipeline.stages.stage1.shell1.identifier",
            "outcome.expandedJson.pipeline.stages.stage1.shell1.identifier",
            "stepInputs.expandedJson.pipeline.stages.stage1.shell1.identifier",
            "expandedJson.outcome.pipeline.stages.stage1.shell1.identifier",
            "expandedJson.stepInputs.pipeline.stages.stage1.shell1.identifier",
            "expandedJson.pipeline.outcome.stages.stage1.shell1.identifier",
            "expandedJson.pipeline.stepInputs.stages.stage1.shell1.identifier",
            "expandedJson.pipeline.stages.outcome.stage1.shell1.identifier",
            "expandedJson.pipeline.stages.stepInputs.stage1.shell1.identifier",
            "expandedJson.pipeline.stages.stage1.outcome.shell1.identifier",
            "expandedJson.pipeline.stages.stage1.stepInputs.shell1.identifier",
            "expandedJson.pipeline.stages.stage1.shell1.outcome.identifier",
            "expandedJson.pipeline.stages.stage1.shell1.stepInputs.identifier"));
    expression = "shell1.identifier";
    assertThat(ExpandedJsonFunctorUtils.getExpressions(ambiance, Map.of("stage", "stage"), expression))
        .isEqualTo(Lists.newArrayList("expandedJson.pipeline.stages.stage1.shell1.identifier",
            "outcome.expandedJson.pipeline.stages.stage1.shell1.identifier",
            "stepInputs.expandedJson.pipeline.stages.stage1.shell1.identifier",
            "expandedJson.outcome.pipeline.stages.stage1.shell1.identifier",
            "expandedJson.stepInputs.pipeline.stages.stage1.shell1.identifier",
            "expandedJson.pipeline.outcome.stages.stage1.shell1.identifier",
            "expandedJson.pipeline.stepInputs.stages.stage1.shell1.identifier",
            "expandedJson.pipeline.stages.outcome.stage1.shell1.identifier",
            "expandedJson.pipeline.stages.stepInputs.stage1.shell1.identifier",
            "expandedJson.pipeline.stages.stage1.outcome.shell1.identifier",
            "expandedJson.pipeline.stages.stage1.stepInputs.shell1.identifier",
            "expandedJson.pipeline.stages.stage1.shell1.outcome.identifier",
            "expandedJson.pipeline.stages.stage1.shell1.stepInputs.identifier"));
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testGetExpressionsForStepInsideStrategy() {
    Ambiance ambiance = Ambiance.newBuilder()
                            .addAllLevels(getStepLevelInsideStrategy("stepRuntimeId", "stageRuntimeId", "stageSetupId"))
                            .build();
    String expression = "step.identifier";
    assertThat(ExpandedJsonFunctorUtils.getExpressions(ambiance, Map.of("step", "step"), expression))
        .isEqualTo(Lists.newArrayList("expandedJson.pipeline.stages.stage1.strategy.shell1.shell1.identifier",
            "outcome.expandedJson.pipeline.stages.stage1.strategy.shell1.shell1.identifier",
            "stepInputs.expandedJson.pipeline.stages.stage1.strategy.shell1.shell1.identifier",
            "expandedJson.outcome.pipeline.stages.stage1.strategy.shell1.shell1.identifier",
            "expandedJson.stepInputs.pipeline.stages.stage1.strategy.shell1.shell1.identifier",
            "expandedJson.pipeline.outcome.stages.stage1.strategy.shell1.shell1.identifier",
            "expandedJson.pipeline.stepInputs.stages.stage1.strategy.shell1.shell1.identifier",
            "expandedJson.pipeline.stages.outcome.stage1.strategy.shell1.shell1.identifier",
            "expandedJson.pipeline.stages.stepInputs.stage1.strategy.shell1.shell1.identifier",
            "expandedJson.pipeline.stages.stage1.outcome.strategy.shell1.shell1.identifier",
            "expandedJson.pipeline.stages.stage1.stepInputs.strategy.shell1.shell1.identifier",
            "expandedJson.pipeline.stages.stage1.strategy.outcome.shell1.shell1.identifier",
            "expandedJson.pipeline.stages.stage1.strategy.stepInputs.shell1.shell1.identifier",
            "expandedJson.pipeline.stages.stage1.strategy.shell1.outcome.shell1.identifier",
            "expandedJson.pipeline.stages.stage1.strategy.shell1.stepInputs.shell1.identifier",
            "expandedJson.pipeline.stages.stage1.strategy.shell1.shell1.outcome.identifier",
            "expandedJson.pipeline.stages.stage1.strategy.shell1.shell1.stepInputs.identifier"));
    expression = "shell1.identifier";
    assertThat(ExpandedJsonFunctorUtils.getExpressions(ambiance, Map.of("stage", "stage"), expression))
        .isEqualTo(Lists.newArrayList("expandedJson.pipeline.stages.stage1.strategy.shell1.identifier",
            "outcome.expandedJson.pipeline.stages.stage1.strategy.shell1.identifier",
            "stepInputs.expandedJson.pipeline.stages.stage1.strategy.shell1.identifier",
            "expandedJson.outcome.pipeline.stages.stage1.strategy.shell1.identifier",
            "expandedJson.stepInputs.pipeline.stages.stage1.strategy.shell1.identifier",
            "expandedJson.pipeline.outcome.stages.stage1.strategy.shell1.identifier",
            "expandedJson.pipeline.stepInputs.stages.stage1.strategy.shell1.identifier",
            "expandedJson.pipeline.stages.outcome.stage1.strategy.shell1.identifier",
            "expandedJson.pipeline.stages.stepInputs.stage1.strategy.shell1.identifier",
            "expandedJson.pipeline.stages.stage1.outcome.strategy.shell1.identifier",
            "expandedJson.pipeline.stages.stage1.stepInputs.strategy.shell1.identifier",
            "expandedJson.pipeline.stages.stage1.strategy.outcome.shell1.identifier",
            "expandedJson.pipeline.stages.stage1.strategy.stepInputs.shell1.identifier",
            "expandedJson.pipeline.stages.stage1.strategy.shell1.outcome.identifier",
            "expandedJson.pipeline.stages.stage1.strategy.shell1.stepInputs.identifier"));
  }

  private List<Level> getNormalStageLevels(String stageRuntimeId, String stageSetupId) {
    List<Level> levelList = new LinkedList<>();
    levelList.add(
        Level.newBuilder()
            .setIdentifier("pipeline")
            .setRuntimeId(UUIDGenerator.generateUuid())
            .setSetupId(UUIDGenerator.generateUuid())
            .setStepType(StepType.newBuilder().setStepCategory(StepCategory.PIPELINE).setType("pipeline").build())
            .build());
    levelList.add(Level.newBuilder()
                      .setIdentifier("stages")
                      .setRuntimeId(UUIDGenerator.generateUuid())
                      .setSetupId(UUIDGenerator.generateUuid())
                      .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STAGES).setType("stages").build())
                      .build());
    levelList.add(Level.newBuilder()
                      .setIdentifier("stage1")
                      .setRuntimeId(stageRuntimeId)
                      .setSetupId(stageSetupId)
                      .setGroup("stage")
                      .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).setType("stage1").build())
                      .build());
    return levelList;
  }

  private List<Level> getNormalStepLevels(String stepRuntimeId, String stageRuntimeId, String stageSetupId) {
    List<Level> levels = getNormalStageLevels(stageRuntimeId, stageSetupId);
    levels.add(Level.newBuilder()
                   .setRuntimeId(stepRuntimeId)
                   .setIdentifier("shell1")
                   .setSetupId(UUIDGenerator.generateUuid())
                   .setGroup("step")
                   .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STEP).setType("shellscript").build())
                   .build());
    return levels;
  }

  private List<Level> getStepLevelInsideParallel(String stepRuntimeId, String stageRuntimeId, String stageSetupId) {
    List<Level> stepLevels = getNormalStepLevels(stepRuntimeId, stageRuntimeId, stageSetupId);
    // Add parallel block to it
    stepLevels.add(3,
        Level.newBuilder()
            .setRuntimeId(UUIDGenerator.generateUuid())
            .setIdentifier("step1")
            .setSkipExpressionChain(true)
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
            .setIdentifier("strategy")
            .setSetupId(UUIDGenerator.generateUuid())
            .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STRATEGY).setType("strategy").build())
            .build());

    stepLevels.add(4,
        Level.newBuilder()
            .setRuntimeId(UUIDGenerator.generateUuid())
            .setSetupId(UUIDGenerator.generateUuid())
            .setIdentifier("shell1")
            .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STEP).setType("shellscript").build())
            .setStrategyMetadata(StrategyMetadata.newBuilder().build())
            .build());
    return stepLevels;
  }
}
