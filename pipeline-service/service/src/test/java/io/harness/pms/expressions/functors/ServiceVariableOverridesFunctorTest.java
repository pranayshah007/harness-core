/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.expressions.functors;

import static io.harness.rule.OwnerRule.HINGER;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.UUIDGenerator;
import io.harness.engine.pms.data.PmsEngineExpressionService;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.rule.Owner;

import java.util.LinkedList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ServiceVariableOverridesFunctorTest extends CategoryTest {
  @Mock private PmsEngineExpressionService engineExpressionService;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testGetServiceVariableValueWhenNoStepGroupsExist() {
    Ambiance ambiance = Ambiance.newBuilder()
                            .addAllLevels(getNormalStepLevels("stepRuntimeId", "stageRuntimeId", "stageSetupId"))
                            .build();

    ServiceVariableOverridesFunctor functor = new ServiceVariableOverridesFunctor(ambiance, engineExpressionService);
    functor.get("var1");
    verify(engineExpressionService, atLeastOnce()).renderExpression(any(), eq("<+serviceVariables.var1>"), any());
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testGetServiceVariableValueWhenStepGroupDoesNotDefineVar() {
    Ambiance ambiance = Ambiance.newBuilder()
                            .addAllLevels(getStepWithinTheStepGroup("stepRuntimeId", "stageRuntimeId", "stageSetupId"))
                            .build();

    ServiceVariableOverridesFunctor functor = new ServiceVariableOverridesFunctor(ambiance, engineExpressionService);
    functor.get("var1");
    verify(engineExpressionService, atLeastOnce()).renderExpression(any(), eq("<+serviceVariables.var1>"), any());
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testGetStepGroupVariableValueWhenStepGroupDefinesVar() {
    Ambiance ambiance = Ambiance.newBuilder()
                            .addAllLevels(getStepWithinTheStepGroup("stepRuntimeId", "stageRuntimeId", "stageSetupId"))
                            .build();
    when(engineExpressionService.renderExpression(
             any(), eq("<+pipeline.stages.stage1.stepGroup1.variables.var1>"), any()))
        .thenReturn("fromStepGroup");

    ServiceVariableOverridesFunctor functor = new ServiceVariableOverridesFunctor(ambiance, engineExpressionService);
    Object val = functor.get("var1");

    // get value from step group
    verify(engineExpressionService, atLeastOnce())
        .renderExpression(any(), eq("<+pipeline.stages.stage1.stepGroup1.variables.var1>"), any());

    // not from service variable
    verify(engineExpressionService, never()).renderExpression(any(), eq("<+serviceVariables.var1>"), any());
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

  private List<Level> getStepWithinTheStepGroup(String stepRuntimeId, String stageRuntimeId, String stageSetupId) {
    List<Level> levels = getNormalStageLevels(stageRuntimeId, stageSetupId);

    levels.add(
        Level.newBuilder()
            .setRuntimeId("stepGroupRuntimeId")
            .setIdentifier("stepGroup1")
            .setSetupId(UUIDGenerator.generateUuid())
            .setGroup("step_group")
            .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STEP_GROUP).setType("STEP_GROUP").build())
            .build());

    levels.add(Level.newBuilder()
                   .setRuntimeId(stepRuntimeId)
                   .setIdentifier("shell1")
                   .setSetupId(UUIDGenerator.generateUuid())
                   .setGroup("step")
                   .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STEP).setType("shellscript").build())
                   .build());
    return levels;
  }
}
