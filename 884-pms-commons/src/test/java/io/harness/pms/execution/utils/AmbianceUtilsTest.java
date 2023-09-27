/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.execution.utils;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.pms.contracts.execution.events.SdkResponseEventType.HANDLE_STEP_RESPONSE;
import static io.harness.pms.contracts.plan.TriggerType.MANUAL;
import static io.harness.pms.contracts.steps.StepCategory.PIPELINE;
import static io.harness.pms.contracts.steps.StepCategory.STAGE;
import static io.harness.pms.contracts.steps.StepCategory.STEP;
import static io.harness.pms.contracts.steps.StepCategory.STEP_GROUP;
import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.GARVIT;
import static io.harness.rule.OwnerRule.NAMAN;
import static io.harness.rule.OwnerRule.PRASHANT;
import static io.harness.rule.OwnerRule.SAHIL;
import static io.harness.rule.OwnerRule.VED;
import static io.harness.rule.OwnerRule.VIVEK_DIXIT;
import static io.harness.rule.OwnerRule.YUVRAJ;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.AutoLogContext;
import io.harness.ng.core.NGAccess;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.MatrixMetadata;
import io.harness.pms.contracts.execution.StrategyMetadata;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.contracts.plan.TriggeredBy;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.utils.NGPipelineSettingsConstant;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableMap;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class AmbianceUtilsTest extends CategoryTest {
  private static final String ACCOUNT_ID = generateUuid();
  private static final String ORG_ID = generateUuid();
  private static final String PROJECT_ID = generateUuid();
  private static final String APP_ID = generateUuid();
  private static final String PLAN_EXECUTION_ID = generateUuid();
  private static final String PLAN_ID = generateUuid();
  private static final String PHASE_RUNTIME_ID = generateUuid();
  private static final String PHASE_SETUP_ID = generateUuid();
  private static final String SECTION_RUNTIME_ID = generateUuid();
  private static final String SECTION_SETUP_ID = generateUuid();
  private static final String STEP_RUNTIME_ID = generateUuid();
  private static final String STEP_SETUP_ID = generateUuid();

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void cloneForFinish() {
    Ambiance ambiance = buildAmbiance();
    assertThat(ambiance.getLevelsList()).hasSize(4);

    Ambiance clonedAmbiance = AmbianceUtils.cloneForFinish(ambiance);
    assertThat(clonedAmbiance).isNotNull();
    assertThat(clonedAmbiance.getLevelsList()).hasSize(3);
    assertThat(clonedAmbiance.getPlanExecutionId()).isEqualTo(PLAN_EXECUTION_ID);
    assertThat(clonedAmbiance.getPlanId()).isEqualTo(PLAN_ID);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testCloneForChildAndLevel() {
    Ambiance ambiance = buildAmbiance();
    assertThat(ambiance.getLevelsList()).hasSize(4);

    Level stepLevel =
        Level.newBuilder()
            .setRuntimeId(STEP_RUNTIME_ID)
            .setSetupId(STEP_SETUP_ID)
            .setStepType(StepType.newBuilder().setType("HTTP_STEP").setStepCategory(StepCategory.STEP).build())
            .build();

    Ambiance clonedAmbiance = AmbianceUtils.cloneForChild(ambiance, stepLevel);
    assertThat(clonedAmbiance).isNotNull();
    assertThat(clonedAmbiance.getLevelsList()).hasSize(5);
    assertThat(clonedAmbiance.getPlanExecutionId()).isEqualTo(PLAN_EXECUTION_ID);
    assertThat(clonedAmbiance.getPlanId()).isEqualTo(PLAN_ID);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestClone() {
    Ambiance ambiance = buildAmbiance();
    assertThat(ambiance.getLevelsList()).hasSize(4);

    Ambiance clonedAmbiance = AmbianceUtils.clone(ambiance, 0);
    assertThat(clonedAmbiance).isNotNull();
    assertThat(clonedAmbiance.getLevelsList()).hasSize(0);
    assertThat(clonedAmbiance.getPlanExecutionId()).isEqualTo(PLAN_EXECUTION_ID);
    assertThat(clonedAmbiance.getPlanId()).isEqualTo(PLAN_ID);

    clonedAmbiance = AmbianceUtils.clone(ambiance, 1);
    assertThat(clonedAmbiance).isNotNull();
    assertThat(clonedAmbiance.getLevelsList()).hasSize(1);
    assertThat(clonedAmbiance.getPlanExecutionId()).isEqualTo(PLAN_EXECUTION_ID);
    assertThat(clonedAmbiance.getPlanId()).isEqualTo(PLAN_ID);

    clonedAmbiance = AmbianceUtils.clone(ambiance, 2);
    assertThat(clonedAmbiance).isNotNull();
    assertThat(clonedAmbiance.getLevelsList()).hasSize(2);
    assertThat(clonedAmbiance.getPlanExecutionId()).isEqualTo(PLAN_EXECUTION_ID);
    assertThat(clonedAmbiance.getPlanId()).isEqualTo(PLAN_ID);

    clonedAmbiance = AmbianceUtils.clone(ambiance, 5);
    assertThat(clonedAmbiance).isNotNull();
    assertThat(clonedAmbiance.getLevelsList()).hasSize(4);
    assertThat(clonedAmbiance.getPlanExecutionId()).isEqualTo(PLAN_EXECUTION_ID);
    assertThat(clonedAmbiance.getPlanId()).isEqualTo(PLAN_ID);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestDeepCopy() throws InvalidProtocolBufferException {
    Ambiance ambiance = buildAmbiance();
    Ambiance copy = AmbianceUtils.deepCopy(ambiance);

    assertThat(copy).isNotNull();
    assertThat(System.identityHashCode(copy.getLevelsList()))
        .isNotEqualTo(System.identityHashCode(ambiance.getLevelsList()));
    assertThat(copy.getSetupAbstractionsMap()).isEqualTo(ambiance.getSetupAbstractionsMap());
    assertThat(copy.getLevelsList()).isEqualTo(ambiance.getLevelsList());
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testAmbiancePropertyGetters() {
    Ambiance ambiance = buildAmbiance();
    assertThat(AmbianceUtils.getAccountId(ambiance)).isEqualTo(ACCOUNT_ID);
    assertThat(AmbianceUtils.getOrgIdentifier(ambiance)).isEqualTo(ORG_ID);
    assertThat(AmbianceUtils.getProjectIdentifier(ambiance)).isEqualTo(PROJECT_ID);

    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    assertThat(ngAccess.getAccountIdentifier()).isEqualTo(ACCOUNT_ID);
    assertThat(ngAccess.getOrgIdentifier()).isEqualTo(ORG_ID);
    assertThat(ngAccess.getProjectIdentifier()).isEqualTo(PROJECT_ID);

    assertThat(AmbianceUtils.obtainCurrentRuntimeId(ambiance)).isEqualTo(SECTION_RUNTIME_ID);
    assertThat(AmbianceUtils.obtainCurrentSetupId(ambiance)).isEqualTo(SECTION_SETUP_ID);
    assertThat(AmbianceUtils.obtainStepIdentifier(ambiance)).isEqualTo("i4");
    assertThat(AmbianceUtils.getCurrentStepType(ambiance))
        .isEqualTo(StepType.newBuilder().setType("SECTION").setStepCategory(StepCategory.STEP).build());
    assertThat(AmbianceUtils.getCurrentGroup(ambiance)).isEqualTo("SECTION");
    assertThat(AmbianceUtils.getCurrentLevelStartTs(ambiance)).isEqualTo(4);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testEmptyAmbiancePropertyGetters() {
    Ambiance ambiance = Ambiance.newBuilder().build();
    assertThat(AmbianceUtils.obtainCurrentRuntimeId(ambiance)).isNull();
    assertThat(AmbianceUtils.obtainCurrentSetupId(ambiance)).isNull();
    assertThat(AmbianceUtils.obtainStepIdentifier(ambiance)).isNull();
    assertThat(AmbianceUtils.getCurrentStepType(ambiance)).isNull();
    assertThat(AmbianceUtils.getCurrentGroup(ambiance)).isNull();
    assertThatThrownBy(() -> AmbianceUtils.getCurrentLevelStartTs(ambiance))
        .isInstanceOf(InvalidRequestException.class);
  }

  private Ambiance buildAmbiance() {
    Level phaseLevel =
        Level.newBuilder()
            .setRuntimeId(PHASE_RUNTIME_ID)
            .setSetupId(PHASE_SETUP_ID)
            .setStartTs(1)
            .setIdentifier("i1")
            .setStepType(StepType.newBuilder().setType("PHASE").setStepCategory(StepCategory.STEP).build())
            .build();
    Level sectionLevel = Level.newBuilder()
                             .setRuntimeId(SECTION_RUNTIME_ID)
                             .setSetupId(SECTION_SETUP_ID)
                             .setGroup("SECTION")
                             .setStartTs(2)
                             .setIdentifier("i2")
                             .setStepType(StepType.newBuilder().setType("SECTION").setStepCategory(STAGE).build())
                             .build();
    Level stageLevel = Level.newBuilder()
                           .setRuntimeId(SECTION_RUNTIME_ID)
                           .setSetupId(SECTION_SETUP_ID)
                           .setGroup("STAGE")
                           .setStartTs(3)
                           .setIdentifier("i3")
                           .setStepType(StepType.newBuilder().setType("SECTION").setStepCategory(STAGE).build())
                           .build();
    Level stepLevel =
        Level.newBuilder()
            .setRuntimeId(SECTION_RUNTIME_ID)
            .setSetupId(SECTION_SETUP_ID)
            .setGroup("SECTION")
            .setStartTs(4)
            .setIdentifier("i4")
            .setStepType(StepType.newBuilder().setType("SECTION").setStepCategory(StepCategory.STEP).build())
            .build();
    List<Level> levels = new ArrayList<>();
    levels.add(phaseLevel);
    levels.add(sectionLevel);
    levels.add(stageLevel);
    levels.add(stepLevel);
    return Ambiance.newBuilder()
        .setPlanExecutionId(PLAN_EXECUTION_ID)
        .setPlanId(PLAN_ID)
        .putAllSetupAbstractions(ImmutableMap.of(
            "accountId", ACCOUNT_ID, "orgIdentifier", ORG_ID, "projectIdentifier", PROJECT_ID, "appId", APP_ID))
        .addAllLevels(levels)
        .build();
  }

  private Ambiance buildAmbianceUsingStrategyMetadata() {
    Level phaseLevel =
        Level.newBuilder()
            .setRuntimeId(PHASE_RUNTIME_ID)
            .setSetupId(PHASE_SETUP_ID)
            .setStartTs(1)
            .setIdentifier("i1")
            .setStepType(StepType.newBuilder().setType("PHASE").setStepCategory(StepCategory.STEP).build())
            .build();
    Level sectionLevel = Level.newBuilder()
                             .setRuntimeId(SECTION_RUNTIME_ID)
                             .setSetupId(SECTION_SETUP_ID)
                             .setGroup("SECTION")
                             .setStartTs(2)
                             .setIdentifier("i2")
                             .setStepType(StepType.newBuilder().setType("SECTION").setStepCategory(STAGE).build())
                             .build();
    Level strategyLevel =
        Level.newBuilder()
            .setRuntimeId("STRATEGY_RUNTIME_ID")
            .setSetupId("STRATEGY_SETUP_ID")
            .setGroup("STRATEGY")
            .setStartTs(2)
            .setIdentifier("i2")
            .setStepType(StepType.newBuilder().setType("STRATEGY").setStepCategory(StepCategory.STRATEGY).build())
            .build();
    Level stageLevel =
        Level.newBuilder()
            .setRuntimeId(SECTION_RUNTIME_ID)
            .setSetupId(SECTION_SETUP_ID)
            .setGroup("STAGE")
            .setStartTs(3)
            .setIdentifier("i3")
            .setStepType(StepType.newBuilder().setType("SECTION").setStepCategory(STAGE).build())
            .setStrategyMetadata(StrategyMetadata.newBuilder()
                                     .setMatrixMetadata(MatrixMetadata.newBuilder().addMatrixCombination(1).build())
                                     .build())
            .build();
    List<Level> levels = new ArrayList<>();
    levels.add(phaseLevel);
    levels.add(sectionLevel);
    levels.add(strategyLevel);
    levels.add(stageLevel);
    return Ambiance.newBuilder()
        .setPlanExecutionId(PLAN_EXECUTION_ID)
        .setPlanId(PLAN_ID)
        .putAllSetupAbstractions(ImmutableMap.of(
            "accountId", ACCOUNT_ID, "orgIdentifier", ORG_ID, "projectIdentifier", PROJECT_ID, "appId", APP_ID))
        .addAllLevels(levels)
        .build();
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testGetStageLevelFromAmbiance() {
    Ambiance ambiance = buildAmbiance();
    Optional<Level> stage = AmbianceUtils.getStageLevelFromAmbiance(ambiance);
    assertThat(stage.isPresent()).isTrue();
    assertThat(stage.get())
        .isEqualTo(Level.newBuilder()
                       .setRuntimeId(SECTION_RUNTIME_ID)
                       .setSetupId(SECTION_SETUP_ID)
                       .setGroup("STAGE")
                       .setStartTs(3)
                       .setIdentifier("i3")
                       .setStepType(StepType.newBuilder().setType("SECTION").setStepCategory(STAGE).build())
                       .build());
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testObtainParentRuntimeId() {
    Ambiance ambiance = buildAmbiance();
    String parentRuntimeId = AmbianceUtils.obtainParentRuntimeId(ambiance);
    assertThat(parentRuntimeId).isNotNull();
    assertThat(parentRuntimeId).isEqualTo(SECTION_RUNTIME_ID);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testGetStrategyLevelFromAmbiance() {
    Ambiance ambiance = buildAmbianceUsingStrategyMetadata();
    Optional<Level> strategyLevel = AmbianceUtils.getStrategyLevelFromAmbiance(ambiance);
    assertThat(strategyLevel.isPresent()).isTrue();
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testIsCurrentStrategyLevelAtStage() {
    Ambiance ambiance = Ambiance.newBuilder().addLevels(Level.newBuilder().buildPartial()).build();
    assertFalse(AmbianceUtils.isCurrentStrategyLevelAtStage(ambiance));
    ambiance = Ambiance.newBuilder()
                   .addLevels(Level.newBuilder().setGroup("STEPS").buildPartial())
                   .addLevels(Level.newBuilder().buildPartial())
                   .build();
    assertFalse(AmbianceUtils.isCurrentStrategyLevelAtStage(ambiance));

    ambiance = Ambiance.newBuilder()
                   .addLevels(Level.newBuilder().setGroup("STAGES").buildPartial())
                   .addLevels(Level.newBuilder().buildPartial())
                   .build();
    assertTrue(AmbianceUtils.isCurrentStrategyLevelAtStage(ambiance));

    ambiance = Ambiance.newBuilder()
                   .addLevels(Level.newBuilder().setGroup("STEPS").buildPartial())
                   .addLevels(Level.newBuilder()
                                  .setStepType(StepType.newBuilder().setStepCategory(StepCategory.FORK).build())
                                  .buildPartial())
                   .addLevels(Level.newBuilder().buildPartial())
                   .build();
    assertFalse(AmbianceUtils.isCurrentStrategyLevelAtStage(ambiance));

    ambiance =
        Ambiance.newBuilder()
            .addLevels(Level.newBuilder().setGroup("STAGES").buildPartial())
            .addLevels(
                Level.newBuilder().setStepType(StepType.newBuilder().setStepCategory(STAGE).build()).buildPartial())
            .addLevels(Level.newBuilder().buildPartial())
            .build();
    assertFalse(AmbianceUtils.isCurrentStrategyLevelAtStage(ambiance));

    ambiance = Ambiance.newBuilder()
                   .addLevels(Level.newBuilder().setGroup("STAGES").buildPartial())
                   .addLevels(Level.newBuilder()
                                  .setStepType(StepType.newBuilder().setStepCategory(StepCategory.FORK).build())
                                  .buildPartial())
                   .addLevels(Level.newBuilder().buildPartial())
                   .build();
    assertTrue(AmbianceUtils.isCurrentStrategyLevelAtStage(ambiance));
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testGetEmail() {
    Ambiance ambiance =
        Ambiance.newBuilder()
            .setMetadata(ExecutionMetadata.newBuilder()
                             .setTriggerInfo(ExecutionTriggerInfo.newBuilder()
                                                 .setTriggeredBy(TriggeredBy.newBuilder()
                                                                     .putAllExtraInfo(Collections.singletonMap(
                                                                         "email", "expected@harness.io"))
                                                                     .build())
                                                 .build())
                             .build())
            .build();
    assertThat(AmbianceUtils.getEmail(ambiance)).isEqualTo("expected@harness.io");
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void getGetVersionWhenNotPresent() {
    Ambiance ambiance = Ambiance.newBuilder().build();
    String version = AmbianceUtils.getPipelineVersion(ambiance);
    assertThat(version).isEqualTo(HarnessYamlVersion.V0);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void getGetVersionWhenPresent() {
    Ambiance ambiance =
        Ambiance.newBuilder().setMetadata(ExecutionMetadata.newBuilder().setHarnessVersion("1").build()).build();
    String version = AmbianceUtils.getPipelineVersion(ambiance);
    assertThat(version).isEqualTo(HarnessYamlVersion.V1);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGetFQNFromAmbiance() {
    Ambiance ambiance = Ambiance.newBuilder().build();
    assertThat(AmbianceUtils.getFQNUsingLevels(ambiance.getLevelsList())).isEmpty();

    ambiance = Ambiance.newBuilder()
                   .addLevels(Level.newBuilder()
                                  .setStepType(StepType.newBuilder().setStepCategory(PIPELINE).build())
                                  .setIdentifier("pipeline")
                                  .build())
                   .build();
    assertThat(AmbianceUtils.getFQNUsingLevels(ambiance.getLevelsList())).isEqualTo("pipeline");

    ambiance = Ambiance.newBuilder()
                   .addLevels(Level.newBuilder()
                                  .setStepType(StepType.newBuilder().setStepCategory(PIPELINE).build())
                                  .setIdentifier("pipeline")
                                  .build())
                   .addLevels(Level.newBuilder()
                                  .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STEP).build())
                                  .setIdentifier("stages")
                                  .build())
                   .addLevels(Level.newBuilder()
                                  .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STEP).build())
                                  .setIdentifier("parallel")
                                  .build())
                   .addLevels(Level.newBuilder()
                                  .setStepType(StepType.newBuilder().setStepCategory(STAGE).build())
                                  .setIdentifier("stage1")
                                  .build())
                   .addLevels(Level.newBuilder()
                                  .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STEP).build())
                                  .setIdentifier("spec")
                                  .build())
                   .addLevels(Level.newBuilder()
                                  .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STEP).build())
                                  .setIdentifier("execution")
                                  .build())
                   .addLevels(Level.newBuilder()
                                  .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STEP).build())
                                  .setIdentifier("step1")
                                  .build())
                   .build();
    assertThat(AmbianceUtils.getFQNUsingLevels(ambiance.getLevelsList()))
        .isEqualTo("pipeline.stages.stage1.spec.execution.step1");

    ambiance = Ambiance.newBuilder()
                   .addLevels(Level.newBuilder()
                                  .setStepType(StepType.newBuilder().setStepCategory(PIPELINE).build())
                                  .setIdentifier("pipeline")
                                  .build())
                   .addLevels(Level.newBuilder()
                                  .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STEP).build())
                                  .setIdentifier("stages")
                                  .build())
                   .addLevels(Level.newBuilder()
                                  .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STRATEGY).build())
                                  .setSkipExpressionChain(true)
                                  .setIdentifier("stage1")
                                  .build())
                   .addLevels(Level.newBuilder()
                                  .setStepType(StepType.newBuilder().setStepCategory(STAGE).build())
                                  .setIdentifier("stage1_1")
                                  .build())
                   .addLevels(Level.newBuilder()
                                  .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STEP).build())
                                  .setIdentifier("spec")
                                  .build())
                   .addLevels(Level.newBuilder()
                                  .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STEP).build())
                                  .setIdentifier("execution")
                                  .build())
                   .addLevels(Level.newBuilder()
                                  .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STRATEGY).build())
                                  .setSkipExpressionChain(true)
                                  .setIdentifier("step1")
                                  .build())
                   .addLevels(Level.newBuilder()
                                  .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STEP).build())
                                  .setIdentifier("step1_0")
                                  .build())
                   .build();
    assertThat(AmbianceUtils.getFQNUsingLevels(ambiance.getLevelsList()))
        .isEqualTo("pipeline.stages.stage1_1.spec.execution.step1_0");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetStageExecutionIdForExecutionMode() {
    ExecutionMetadata normalMode = ExecutionMetadata.newBuilder().setExecutionMode(ExecutionMode.NORMAL).build();
    Ambiance normalModeAmbiance = Ambiance.newBuilder().setMetadata(normalMode).setStageExecutionId("currId").build();
    assertThat(AmbianceUtils.getStageExecutionIdForExecutionMode(normalModeAmbiance)).isEqualTo("currId");

    ExecutionMetadata rbMode = ExecutionMetadata.newBuilder().setExecutionMode(ExecutionMode.PIPELINE_ROLLBACK).build();
    Ambiance rbModeAmbiance = Ambiance.newBuilder()
                                  .setMetadata(rbMode)
                                  .setStageExecutionId("currId")
                                  .setOriginalStageExecutionIdForRollbackMode("origId")
                                  .build();
    assertThat(AmbianceUtils.getStageExecutionIdForExecutionMode(rbModeAmbiance)).isEqualTo("origId");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetPlanExecutionIdForExecutionMode() {
    ExecutionMetadata normalMode = ExecutionMetadata.newBuilder().setExecutionMode(ExecutionMode.NORMAL).build();
    Ambiance normalModeAmbiance = Ambiance.newBuilder().setMetadata(normalMode).setPlanExecutionId("currId").build();
    assertThat(AmbianceUtils.getPlanExecutionIdForExecutionMode(normalModeAmbiance)).isEqualTo("currId");

    ExecutionMetadata rbMode = ExecutionMetadata.newBuilder()
                                   .setExecutionMode(ExecutionMode.PIPELINE_ROLLBACK)
                                   .setOriginalPlanExecutionIdForRollbackMode("origId")
                                   .build();
    Ambiance rbModeAmbiance = Ambiance.newBuilder().setMetadata(rbMode).build();
    assertThat(AmbianceUtils.getPlanExecutionIdForExecutionMode(rbModeAmbiance)).isEqualTo("origId");
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void checkIfSettingEnabled() {
    Ambiance ambiance = Ambiance.newBuilder()
                            .setMetadata(ExecutionMetadata.newBuilder()
                                             .putSettingToValueMap("setting1", "true")
                                             .putSettingToValueMap("setting2", "false")
                                             .putSettingToValueMap("setting3", "some random string value")
                                             .build())
                            .build();
    assertThat(AmbianceUtils.checkIfSettingEnabled(ambiance, "setting1")).isTrue();
    assertThat(AmbianceUtils.checkIfSettingEnabled(ambiance, "setting2")).isFalse();
    assertThat(AmbianceUtils.checkIfSettingEnabled(ambiance, "setting3")).isFalse();
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testGetStrategyPostfix() {
    Map<String, String> values = new HashMap<>();
    values.put("a", "true");
    values.put("matrixIdentifierPostfixForDuplicates", "0");
    values.put("command", "hi");
    MatrixMetadata matrixMetadata = MatrixMetadata.newBuilder()
                                        .putAllMatrixValues(values)
                                        .addAllMatrixCombination(Collections.singletonList(1))
                                        .build();
    StrategyMetadata strategyMetadata = StrategyMetadata.newBuilder().setMatrixMetadata(matrixMetadata).build();
    Level level = Level.newBuilder().setStrategyMetadata(strategyMetadata).build();
    String identifier = AmbianceUtils.getStrategyPostfix(level, true);
    assertThat(identifier).isEqualTo("_true_hi_0");
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testGetStrategyPostfixWithNodeNamesAndBooleanSettingEnabled() {
    Map<String, String> values = new HashMap<>();
    values.put("go", "world");
    values.put("matrixIdentifierPostfixForDuplicates", "0");
    values.put("java", "a");
    MatrixMetadata matrixMetadata = MatrixMetadata.newBuilder()
                                        .putAllMatrixValues(values)
                                        .addAllMatrixCombination(Collections.singletonList(1))
                                        .setNodeName("a")
                                        .build();
    StrategyMetadata strategyMetadata = StrategyMetadata.newBuilder().setMatrixMetadata(matrixMetadata).build();
    Level level = Level.newBuilder().setStrategyMetadata(strategyMetadata).build();
    String identifier = AmbianceUtils.getStrategyPostfix(level, true);
    assertThat(identifier).isEqualTo("_a_0");
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testGetStrategyPostfixWithoutNodeNamesAndBooleanSettingEnabled() {
    Map<String, String> values = new HashMap<>();
    values.put("go", "world");
    values.put("matrixIdentifierPostfixForDuplicates", "0");
    values.put("java", "a");
    MatrixMetadata matrixMetadata = MatrixMetadata.newBuilder()
                                        .putAllMatrixValues(values)
                                        .addAllMatrixCombination(Collections.singletonList(1))
                                        .build();
    StrategyMetadata strategyMetadata = StrategyMetadata.newBuilder().setMatrixMetadata(matrixMetadata).build();
    Level level = Level.newBuilder().setStrategyMetadata(strategyMetadata).build();
    String identifier = AmbianceUtils.getStrategyPostfix(level, true);
    assertThat(identifier).isEqualTo("_world_a_0");
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testGetStrategyPostfixWithoutNodeNamesAndBooleanSettingDisabled() {
    Map<String, String> values = new HashMap<>();
    values.put("go", "world");
    values.put("matrixIdentifierPostfixForDuplicates", "0");
    values.put("java", "a");
    MatrixMetadata matrixMetadata = MatrixMetadata.newBuilder()
                                        .putAllMatrixValues(values)
                                        .addAllMatrixCombination(Collections.singletonList(0))
                                        .build();
    StrategyMetadata strategyMetadata = StrategyMetadata.newBuilder().setMatrixMetadata(matrixMetadata).build();
    Level level = Level.newBuilder().setStrategyMetadata(strategyMetadata).build();
    String identifier = AmbianceUtils.getStrategyPostfix(level, false);
    assertThat(identifier).isEqualTo("_0_0");
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testGetStrategyPostfixWithNodeNamesAndBooleanSettingDisabled() {
    Map<String, String> values = new HashMap<>();
    values.put("go", "world");
    values.put("matrixIdentifierPostfixForDuplicates", "0");
    values.put("java", "a");
    MatrixMetadata matrixMetadata = MatrixMetadata.newBuilder()
                                        .putAllMatrixValues(values)
                                        .addAllMatrixCombination(Collections.singletonList(0))
                                        .setNodeName("a")
                                        .build();
    StrategyMetadata strategyMetadata = StrategyMetadata.newBuilder().setMatrixMetadata(matrixMetadata).build();
    Level level = Level.newBuilder().setStrategyMetadata(strategyMetadata).build();
    String identifier = AmbianceUtils.getStrategyPostfix(level, false);
    assertThat(identifier).isEqualTo("_a_0");
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testGetEnabledFeatureFlags() {
    Ambiance ambiance =
        Ambiance.newBuilder()
            .setMetadata(ExecutionMetadata.newBuilder()
                             .putAllFeatureFlagToValueMap(Map.of("flag1", true, "flag2", false, "flag3", true))
                             .build())
            .build();

    List<String> enabledFeatureFlags = new ArrayList<>();
    enabledFeatureFlags.add("flag1");
    enabledFeatureFlags.add("flag3");
    List<String> ffListResponseFromAmbiance = AmbianceUtils.getEnabledFeatureFlags(ambiance);
    assertThat(ffListResponseFromAmbiance.size()).isEqualTo(enabledFeatureFlags.size());
    for (String ff : enabledFeatureFlags) {
      assertThat(ffListResponseFromAmbiance.contains(ff)).isTrue();
    }
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testGetSettingValue() {
    Map<String, String> settingValueMap = new HashMap<>();
    settingValueMap.put("id1", "v1");
    settingValueMap.put("id2", "v2");
    settingValueMap.put("id3", "v3");

    Ambiance ambiance =
        Ambiance.newBuilder()
            .setMetadata(ExecutionMetadata.newBuilder().putAllSettingToValueMap(settingValueMap).build())
            .build();

    assertThat("v2").isEqualTo(AmbianceUtils.getSettingValue(ambiance, "id2"));
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testShouldUseMatrixFieldName() {
    Map<String, String> settingValueMap = new HashMap<>();
    settingValueMap.put(NGPipelineSettingsConstant.ENABLE_MATRIX_FIELD_NAME_SETTING.getName(), "true");
    settingValueMap.put(NGPipelineSettingsConstant.ENABLE_NODE_EXECUTION_AUDIT_EVENTS.getName(), "false");
    settingValueMap.put(NGPipelineSettingsConstant.ENABLE_EXPRESSION_ENGINE_V2.getName(), "true");

    Ambiance ambiance =
        Ambiance.newBuilder()
            .setMetadata(ExecutionMetadata.newBuilder().putAllSettingToValueMap(settingValueMap).build())
            .build();

    assertThat(true).isEqualTo(AmbianceUtils.shouldUseMatrixFieldName(ambiance));
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testIsNodeExecutionAuditsEnabled() {
    Map<String, String> settingValueMap = new HashMap<>();
    settingValueMap.put(NGPipelineSettingsConstant.ENABLE_MATRIX_FIELD_NAME_SETTING.getName(), "true");
    settingValueMap.put(NGPipelineSettingsConstant.ENABLE_NODE_EXECUTION_AUDIT_EVENTS.getName(), "false");
    settingValueMap.put(NGPipelineSettingsConstant.ENABLE_EXPRESSION_ENGINE_V2.getName(), "true");

    Ambiance ambiance =
        Ambiance.newBuilder()
            .setMetadata(ExecutionMetadata.newBuilder().putAllSettingToValueMap(settingValueMap).build())
            .build();

    assertThat(false).isEqualTo(AmbianceUtils.isNodeExecutionAuditsEnabled(ambiance));
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testShouldUseExpressionEngineV2() {
    Map<String, String> settingValueMap = new HashMap<>();
    settingValueMap.put(NGPipelineSettingsConstant.ENABLE_MATRIX_FIELD_NAME_SETTING.getName(), "true");
    settingValueMap.put(NGPipelineSettingsConstant.ENABLE_NODE_EXECUTION_AUDIT_EVENTS.getName(), "false");
    settingValueMap.put(NGPipelineSettingsConstant.ENABLE_EXPRESSION_ENGINE_V2.getName(), "true");

    Ambiance ambiance =
        Ambiance.newBuilder()
            .setMetadata(ExecutionMetadata.newBuilder().putAllSettingToValueMap(settingValueMap).build())
            .build();

    assertThat(true).isEqualTo(AmbianceUtils.shouldUseExpressionEngineV2(ambiance));
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testIsUnderRollbackSteps() {
    List<Level> levels = new ArrayList<>();

    Level l1 = Level.newBuilder().setIdentifier("i1").build();

    Level l2 = Level.newBuilder().setIdentifier(YAMLFieldNameConstants.ROLLBACK_STEPS).build();

    Level l3 = Level.newBuilder().setIdentifier("i3").build();

    levels.add(l1);
    levels.add(l2);
    levels.add(l3);

    Ambiance ambiance = Ambiance.newBuilder().addAllLevels(levels).build();

    assertThat(true).isEqualTo(AmbianceUtils.isUnderRollbackSteps(ambiance));
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testAutoLogContext() {
    List<Level> levels = new ArrayList<>();

    Level l1 = Level.newBuilder()
                   .setIdentifier("i1")
                   .setRuntimeId("r1")
                   .setSetupId("s1")
                   .setStepType(StepType.newBuilder().setStepCategory(PIPELINE).setType("t1"))
                   .build();

    Level l2 = Level.newBuilder()
                   .setIdentifier("i2")
                   .setRuntimeId("r2")
                   .setSetupId("s2")
                   .setStepType(StepType.newBuilder().setStepCategory(PIPELINE).setType("t2"))
                   .build();

    Level l3 = Level.newBuilder()
                   .setIdentifier("i3")
                   .setRuntimeId("r3")
                   .setSetupId("s3")
                   .setStepType(StepType.newBuilder().setStepCategory(PIPELINE).setType("t3"))
                   .build();

    levels.add(l1);
    levels.add(l2);
    levels.add(l3);

    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanId("planId")
                            .addAllLevels(levels)
                            .setMetadata(ExecutionMetadata.newBuilder().setPipelineIdentifier("p1").build())
                            .build();

    AutoLogContext autoLogContext = AmbianceUtils.autoLogContext(ambiance, HANDLE_STEP_RESPONSE);
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testIsCurrentLevelChildOfStep() {
    List<Level> levels = new ArrayList<>();

    Level l1 = Level.newBuilder()
                   .setIdentifier("i1")
                   .setRuntimeId("r1")
                   .setSetupId("s1")
                   .setStepType(StepType.newBuilder().setStepCategory(PIPELINE).setType("stepType"))
                   .build();

    Level l2 = Level.newBuilder()
                   .setIdentifier("i2")
                   .setRuntimeId("r2")
                   .setSetupId("s2")
                   .setStepType(StepType.newBuilder().setStepCategory(PIPELINE).setType("stepType"))
                   .build();

    Level l3 = Level.newBuilder()
                   .setIdentifier("i3")
                   .setRuntimeId("r3")
                   .setSetupId("s3")
                   .setStepType(StepType.newBuilder().setStepCategory(PIPELINE).setType("stepType"))
                   .build();

    levels.add(l1);
    levels.add(l2);
    levels.add(l3);

    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanId("planId")
                            .addAllLevels(levels)
                            .setMetadata(ExecutionMetadata.newBuilder().setPipelineIdentifier("p1").build())
                            .build();

    assertThat(true).isEqualTo(AmbianceUtils.isCurrentLevelChildOfStep(ambiance, "stepType"));
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testIsCurrentLevelChildOfStepWithDifferentStepType() {
    List<Level> levels = new ArrayList<>();

    Level l1 = Level.newBuilder()
                   .setIdentifier("i1")
                   .setRuntimeId("r1")
                   .setSetupId("s1")
                   .setStepType(StepType.newBuilder().setStepCategory(PIPELINE).setType("stepType"))
                   .build();

    Level l2 = Level.newBuilder()
                   .setIdentifier("i2")
                   .setRuntimeId("r2")
                   .setSetupId("s2")
                   .setStepType(StepType.newBuilder().setStepCategory(PIPELINE).setType("stepType"))
                   .build();

    Level l3 = Level.newBuilder()
                   .setIdentifier("i3")
                   .setRuntimeId("r3")
                   .setSetupId("s3")
                   .setStepType(StepType.newBuilder().setStepCategory(PIPELINE).setType("stepType"))
                   .build();

    levels.add(l1);
    levels.add(l2);
    levels.add(l3);

    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanId("planId")
                            .addAllLevels(levels)
                            .setMetadata(ExecutionMetadata.newBuilder().setPipelineIdentifier("p1").build())
                            .build();

    assertThat(false).isEqualTo(AmbianceUtils.isCurrentLevelChildOfStep(ambiance, "diffStepType"));
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testIsCurrentLevelChildOfStepWithEmptyLevels() {
    List<Level> levels = new ArrayList<>();

    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanId("planId")
                            .addAllLevels(levels)
                            .setMetadata(ExecutionMetadata.newBuilder().setPipelineIdentifier("p1").build())
                            .build();

    assertThat(false).isEqualTo(AmbianceUtils.isCurrentLevelChildOfStep(ambiance, "stepType"));
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testGetPipelineExecutionIdentifier() {
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId("executionId")
                            .setMetadata(ExecutionMetadata.newBuilder().setExecutionUuid("executionId").build())
                            .build();

    assertThat("executionId").isEqualTo(AmbianceUtils.getPipelineExecutionIdentifier(ambiance));
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testGetPipelineExecutionIdentifier_2() {
    Ambiance ambiance = Ambiance.newBuilder()
                            .setMetadata(ExecutionMetadata.newBuilder().setExecutionUuid("executionId").build())
                            .build();

    assertThat("executionId").isEqualTo(AmbianceUtils.getPipelineExecutionIdentifier(ambiance));
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testGetPipelineExecutionIdentifier_3() {
    Ambiance ambiance = Ambiance.newBuilder().build();

    assertThat(true).isEqualTo(EmptyPredicate.isEmpty(AmbianceUtils.getPipelineExecutionIdentifier(ambiance)));
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testGetTriggerBy() {
    TriggeredBy triggeredBy =
        TriggeredBy.newBuilder().setIdentifier("i1").setTriggerName("n1").setTriggerIdentifier("t1").build();

    Ambiance ambiance =
        Ambiance.newBuilder()
            .setMetadata(
                ExecutionMetadata.newBuilder()
                    .setTriggerInfo(
                        ExecutionTriggerInfo.newBuilder().setTriggerType(MANUAL).setTriggeredBy(triggeredBy).build())
                    .build())
            .build();

    assertThat(triggeredBy).isEqualTo(AmbianceUtils.getTriggerBy(ambiance));
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testGetTriggerType() {
    TriggeredBy triggeredBy =
        TriggeredBy.newBuilder().setIdentifier("i1").setTriggerName("n1").setTriggerIdentifier("t1").build();

    Ambiance ambiance =
        Ambiance.newBuilder()
            .setMetadata(
                ExecutionMetadata.newBuilder()
                    .setTriggerInfo(
                        ExecutionTriggerInfo.newBuilder().setTriggerType(MANUAL).setTriggeredBy(triggeredBy).build())
                    .build())
            .build();

    assertThat(MANUAL).isEqualTo(AmbianceUtils.getTriggerType(ambiance));
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testGetTriggerIdentifier() {
    TriggeredBy triggeredBy =
        TriggeredBy.newBuilder().setIdentifier("i1").setTriggerName("n1").setTriggerIdentifier("t1").build();

    Ambiance ambiance =
        Ambiance.newBuilder()
            .setMetadata(
                ExecutionMetadata.newBuilder()
                    .setTriggerInfo(
                        ExecutionTriggerInfo.newBuilder().setTriggerType(MANUAL).setTriggeredBy(triggeredBy).build())
                    .build())
            .build();

    assertThat("i1").isEqualTo(AmbianceUtils.getTriggerIdentifier(ambiance));
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testGetPipelineIdentifier() {
    Ambiance ambiance =
        Ambiance.newBuilder().setMetadata(ExecutionMetadata.newBuilder().setPipelineIdentifier("p1").build()).build();

    assertThat("p1").isEqualTo(AmbianceUtils.getPipelineIdentifier(ambiance));
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testGetPipelineIdentifier_2() {
    Ambiance ambiance = Ambiance.newBuilder().build();

    assertThat(true).isEqualTo(EmptyPredicate.isEmpty(AmbianceUtils.getPipelineIdentifier(ambiance)));
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testIsCurrentLevelInsideStage() {
    List<Level> levels = new ArrayList<>();

    Level l1 = Level.newBuilder()
                   .setIdentifier("i1")
                   .setRuntimeId("r1")
                   .setSetupId("s1")
                   .setStepType(StepType.newBuilder().setStepCategory(STAGE).setType("stepType"))
                   .build();

    Level l2 = Level.newBuilder()
                   .setIdentifier("i2")
                   .setRuntimeId("r2")
                   .setSetupId("s2")
                   .setStepType(StepType.newBuilder().setStepCategory(PIPELINE).setType("stepType"))
                   .build();

    Level l3 = Level.newBuilder()
                   .setIdentifier("i3")
                   .setRuntimeId("r3")
                   .setSetupId("s3")
                   .setStepType(StepType.newBuilder().setStepCategory(PIPELINE).setType("stepType"))
                   .build();

    levels.add(l1);
    levels.add(l2);
    levels.add(l3);

    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanId("planId")
                            .addAllLevels(levels)
                            .setMetadata(ExecutionMetadata.newBuilder().setPipelineIdentifier("p1").build())
                            .build();

    assertThat(true).isEqualTo(AmbianceUtils.isCurrentLevelInsideStage(ambiance));
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testIsCurrentLevelInsideStage_2() {
    List<Level> levels = new ArrayList<>();

    Level l1 = Level.newBuilder()
                   .setIdentifier("i1")
                   .setRuntimeId("r1")
                   .setSetupId("s1")
                   .setStepType(StepType.newBuilder().setStepCategory(PIPELINE).setType("stepType"))
                   .build();

    Level l2 = Level.newBuilder()
                   .setIdentifier("i2")
                   .setRuntimeId("r2")
                   .setSetupId("s2")
                   .setStepType(StepType.newBuilder().setStepCategory(PIPELINE).setType("stepType"))
                   .build();

    Level l3 = Level.newBuilder()
                   .setIdentifier("i3")
                   .setRuntimeId("r3")
                   .setSetupId("s3")
                   .setStepType(StepType.newBuilder().setStepCategory(PIPELINE).setType("stepType"))
                   .build();

    levels.add(l1);
    levels.add(l2);
    levels.add(l3);

    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanId("planId")
                            .addAllLevels(levels)
                            .setMetadata(ExecutionMetadata.newBuilder().setPipelineIdentifier("p1").build())
                            .build();

    assertThat(false).isEqualTo(AmbianceUtils.isCurrentLevelInsideStage(ambiance));
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testIsCurrentLevelAtStep() {
    List<Level> levels = new ArrayList<>();

    Level l1 = Level.newBuilder()
                   .setIdentifier("i1")
                   .setRuntimeId("r1")
                   .setSetupId("s1")
                   .setStepType(StepType.newBuilder().setStepCategory(PIPELINE).setType("stepType"))
                   .build();

    Level l2 = Level.newBuilder()
                   .setIdentifier("i2")
                   .setRuntimeId("r2")
                   .setSetupId("s2")
                   .setStepType(StepType.newBuilder().setStepCategory(PIPELINE).setType("stepType"))
                   .build();

    Level l3 = Level.newBuilder()
                   .setIdentifier("i3")
                   .setRuntimeId("r3")
                   .setSetupId("s3")
                   .setStepType(StepType.newBuilder().setStepCategory(STEP).setType("stepType"))
                   .build();

    levels.add(l1);
    levels.add(l2);
    levels.add(l3);

    Ambiance ambiance = Ambiance.newBuilder().addAllLevels(levels).build();

    assertThat(true).isEqualTo(AmbianceUtils.isCurrentLevelAtStep(ambiance));
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testIsCurrentLevelAtStepWithEmptyLevels() {
    List<Level> levels = new ArrayList<>();

    Ambiance ambiance = Ambiance.newBuilder().addAllLevels(levels).build();

    assertThat(false).isEqualTo(AmbianceUtils.isCurrentLevelAtStep(ambiance));
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testIsCurrentNodeUnderStageStrategy() {
    List<Level> levels = new ArrayList<>();

    Level l1 = Level.newBuilder()
                   .setIdentifier("i1")
                   .setRuntimeId("r1")
                   .setSetupId("s1")
                   .setStepType(StepType.newBuilder().setStepCategory(PIPELINE).setType("stepType"))
                   .build();

    Level l2 = Level.newBuilder()
                   .setIdentifier("i2")
                   .setRuntimeId("r2")
                   .setSetupId("s2")
                   .setStepType(StepType.newBuilder().setStepCategory(PIPELINE).setType("stepType"))
                   .build();

    Level l3 =
        Level.newBuilder()
            .setIdentifier("i3")
            .setRuntimeId("r3")
            .setSetupId("s3")
            .setStepType(StepType.newBuilder().setStepCategory(STAGE).setType("stepType"))
            .setStrategyMetadata(StrategyMetadata.newBuilder()
                                     .setMatrixMetadata(MatrixMetadata.newBuilder().setNodeName("nodename").build())
                                     .build())
            .build();

    levels.add(l1);
    levels.add(l2);
    levels.add(l3);

    Ambiance ambiance = Ambiance.newBuilder().addAllLevels(levels).build();

    assertThat(true).isEqualTo(AmbianceUtils.isCurrentNodeUnderStageStrategy(ambiance));
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testIsCurrentNodeUnderStageStrategyWithoutStrategy() {
    List<Level> levels = new ArrayList<>();

    Level l1 = Level.newBuilder()
                   .setIdentifier("i1")
                   .setRuntimeId("r1")
                   .setSetupId("s1")
                   .setStepType(StepType.newBuilder().setStepCategory(PIPELINE).setType("stepType"))
                   .build();

    Level l2 = Level.newBuilder()
                   .setIdentifier("i2")
                   .setRuntimeId("r2")
                   .setSetupId("s2")
                   .setStepType(StepType.newBuilder().setStepCategory(PIPELINE).setType("stepType"))
                   .build();

    Level l3 = Level.newBuilder()
                   .setIdentifier("i3")
                   .setRuntimeId("r3")
                   .setSetupId("s3")
                   .setStepType(StepType.newBuilder().setStepCategory(STAGE).setType("stepType"))
                   .build();

    levels.add(l1);
    levels.add(l2);
    levels.add(l3);

    Ambiance ambiance = Ambiance.newBuilder().addAllLevels(levels).build();

    assertThat(false).isEqualTo(AmbianceUtils.isCurrentNodeUnderStageStrategy(ambiance));
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testObtainParentRuntimeIdWithSingleLevel() {
    List<Level> levels = new ArrayList<>();

    Level l1 = Level.newBuilder()
                   .setIdentifier("i1")
                   .setRuntimeId("r1")
                   .setSetupId("s1")
                   .setStepType(StepType.newBuilder().setStepCategory(PIPELINE).setType("stepType"))
                   .build();

    levels.add(l1);

    Ambiance ambiance = Ambiance.newBuilder().addAllLevels(levels).build();

    assertThat(true).isEqualTo(EmptyPredicate.isEmpty(AmbianceUtils.obtainParentRuntimeId(ambiance)));
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testIsRetry() {
    List<Level> levels = new ArrayList<>();

    Level l1 = Level.newBuilder()
                   .setIdentifier("i1")
                   .setRuntimeId("r1")
                   .setSetupId("s1")
                   .setStepType(StepType.newBuilder().setStepCategory(PIPELINE).setType("stepType"))
                   .setRetryIndex(2)
                   .build();

    levels.add(l1);

    Ambiance ambiance = Ambiance.newBuilder().addAllLevels(levels).build();

    assertThat(true).isEqualTo(AmbianceUtils.isRetry(ambiance));
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testGetStepGroupLevelFromAmbiance() {
    List<Level> levels = new ArrayList<>();

    Level l1 = Level.newBuilder()
                   .setIdentifier("i1")
                   .setRuntimeId("r1")
                   .setSetupId("s1")
                   .setStepType(StepType.newBuilder().setStepCategory(PIPELINE).setType("PIPELINE"))
                   .build();

    Level l2 = Level.newBuilder()
                   .setIdentifier("i2")
                   .setRuntimeId("r2")
                   .setSetupId("s2")
                   .setStepType(StepType.newBuilder().setStepCategory(STAGE).setType("STAGE"))
                   .build();

    Level l3 = Level.newBuilder()
                   .setIdentifier("i3")
                   .setRuntimeId("r3")
                   .setSetupId("s3")
                   .setStepType(StepType.newBuilder().setStepCategory(STEP_GROUP).setType("STEP_GROUP"))
                   .build();

    levels.add(l1);
    levels.add(l2);
    levels.add(l3);

    Ambiance ambiance = Ambiance.newBuilder().addAllLevels(levels).build();

    assertThat(Optional.of(l3)).isEqualTo(AmbianceUtils.getStepGroupLevelFromAmbiance(ambiance));
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testGetStageRuntimeIdAmbiance() {
    List<Level> levels = new ArrayList<>();

    Level l1 = Level.newBuilder()
                   .setIdentifier("i1")
                   .setRuntimeId("r1")
                   .setSetupId("s1")
                   .setStepType(StepType.newBuilder().setStepCategory(PIPELINE).setType("PIPELINE"))
                   .build();

    Level l2 = Level.newBuilder()
                   .setIdentifier("i2")
                   .setRuntimeId("r2")
                   .setSetupId("s2")
                   .setStepType(StepType.newBuilder().setStepCategory(STAGE).setType("STAGE"))
                   .build();

    Level l3 = Level.newBuilder()
                   .setIdentifier("i3")
                   .setRuntimeId("r3")
                   .setSetupId("s3")
                   .setStepType(StepType.newBuilder().setStepCategory(STEP_GROUP).setType("STEP_GROUP"))
                   .build();

    levels.add(l1);
    levels.add(l2);
    levels.add(l3);

    Ambiance ambiance = Ambiance.newBuilder().addAllLevels(levels).build();

    assertThat("r2").isEqualTo(AmbianceUtils.getStageRuntimeIdAmbiance(ambiance));
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testGetStageRuntimeIdAmbianceWithoutStageLevel() {
    List<Level> levels = new ArrayList<>();

    Level l1 = Level.newBuilder()
                   .setIdentifier("i1")
                   .setRuntimeId("r1")
                   .setSetupId("s1")
                   .setStepType(StepType.newBuilder().setStepCategory(PIPELINE).setType("PIPELINE"))
                   .build();

    Level l2 = Level.newBuilder()
                   .setIdentifier("i2")
                   .setRuntimeId("r2")
                   .setSetupId("s2")
                   .setStepType(StepType.newBuilder().setStepCategory(PIPELINE).setType("PIPELINE"))
                   .build();

    Level l3 = Level.newBuilder()
                   .setIdentifier("i3")
                   .setRuntimeId("r3")
                   .setSetupId("s3")
                   .setStepType(StepType.newBuilder().setStepCategory(STEP_GROUP).setType("STEP_GROUP"))
                   .build();

    levels.add(l1);
    levels.add(l2);
    levels.add(l3);

    Ambiance ambiance = Ambiance.newBuilder().addAllLevels(levels).build();

    assertThatThrownBy(() -> AmbianceUtils.getStageRuntimeIdAmbiance(ambiance))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Stage not present");
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testGetParentStepType() {
    List<Level> levels = new ArrayList<>();

    Level l1 = Level.newBuilder()
                   .setIdentifier("i1")
                   .setRuntimeId("r1")
                   .setSetupId("s1")
                   .setStepType(StepType.newBuilder().setStepCategory(PIPELINE).setType("PIPELINE"))
                   .build();

    Level l2 = Level.newBuilder()
                   .setIdentifier("i2")
                   .setRuntimeId("r2")
                   .setSetupId("s2")
                   .setStepType(StepType.newBuilder().setStepCategory(STAGE).setType("STAGE"))
                   .build();

    Level l3 = Level.newBuilder()
                   .setIdentifier("i3")
                   .setRuntimeId("r3")
                   .setSetupId("s3")
                   .setStepType(StepType.newBuilder().setStepCategory(STEP_GROUP).setType("STEP_GROUP"))
                   .build();

    levels.add(l1);
    levels.add(l2);
    levels.add(l3);

    Ambiance ambiance = Ambiance.newBuilder().addAllLevels(levels).build();

    assertThat(StepType.newBuilder().setStepCategory(STAGE).setType("STAGE").build())
        .isEqualTo(AmbianceUtils.getParentStepType(ambiance));
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testObtainStepGroupIdentifier() {
    List<Level> levels = new ArrayList<>();

    Level l1 = Level.newBuilder()
                   .setIdentifier("i1")
                   .setRuntimeId("r1")
                   .setSetupId("s1")
                   .setStepType(StepType.newBuilder().setStepCategory(PIPELINE).setType("PIPELINE"))
                   .build();

    Level l2 = Level.newBuilder()
                   .setIdentifier("i2")
                   .setRuntimeId("r2")
                   .setSetupId("s2")
                   .setStepType(StepType.newBuilder().setStepCategory(STAGE).setType("STAGE"))
                   .build();

    Level l3 = Level.newBuilder()
                   .setIdentifier("i3")
                   .setRuntimeId("r3")
                   .setSetupId("s3")
                   .setStepType(StepType.newBuilder().setStepCategory(STEP_GROUP).setType("STEP_GROUP"))
                   .build();

    levels.add(l1);
    levels.add(l2);
    levels.add(l3);

    Ambiance ambiance = Ambiance.newBuilder().addAllLevels(levels).build();

    assertThat("i3").isEqualTo(AmbianceUtils.obtainStepGroupIdentifier(ambiance));
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testObtainStepGroupIdentifierWithoutStepGroupLevel() {
    List<Level> levels = new ArrayList<>();

    Level l1 = Level.newBuilder()
                   .setIdentifier("i1")
                   .setRuntimeId("r1")
                   .setSetupId("s1")
                   .setStepType(StepType.newBuilder().setStepCategory(PIPELINE).setType("PIPELINE"))
                   .build();

    Level l2 = Level.newBuilder()
                   .setIdentifier("i2")
                   .setRuntimeId("r2")
                   .setSetupId("s2")
                   .setStepType(StepType.newBuilder().setStepCategory(STAGE).setType("STAGE"))
                   .build();

    Level l3 = Level.newBuilder()
                   .setIdentifier("i3")
                   .setRuntimeId("r3")
                   .setSetupId("s3")
                   .setStepType(StepType.newBuilder().setStepCategory(STAGE).setType("STAGE"))
                   .build();

    levels.add(l1);
    levels.add(l2);
    levels.add(l3);

    Ambiance ambiance = Ambiance.newBuilder().addAllLevels(levels).build();

    assertThat(true).isEqualTo(EmptyPredicate.isEmpty(AmbianceUtils.obtainStepGroupIdentifier(ambiance)));
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testObtainNodeType() {
    List<Level> levels = new ArrayList<>();

    Level l1 = Level.newBuilder()
                   .setIdentifier("i1")
                   .setRuntimeId("r1")
                   .setSetupId("s1")
                   .setStepType(StepType.newBuilder().setStepCategory(PIPELINE).setType("PIPELINE"))
                   .setNodeType("PIPELINE")
                   .build();

    Level l2 = Level.newBuilder()
                   .setIdentifier("i2")
                   .setRuntimeId("r2")
                   .setSetupId("s2")
                   .setStepType(StepType.newBuilder().setStepCategory(STAGE).setType("STAGE"))
                   .setNodeType("STAGE")
                   .build();

    Level l3 = Level.newBuilder()
                   .setIdentifier("i3")
                   .setRuntimeId("r3")
                   .setSetupId("s3")
                   .setStepType(StepType.newBuilder().setStepCategory(STEP).setType("STEP"))
                   .setNodeType("STEP")
                   .build();

    levels.add(l1);
    levels.add(l2);
    levels.add(l3);

    Ambiance ambiance = Ambiance.newBuilder().addAllLevels(levels).build();

    assertThat("STEP").isEqualTo(AmbianceUtils.obtainNodeType(ambiance));
  }
}
