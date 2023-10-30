/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.engine.pms.execution.modifier.ambiance;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ARCHIT;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class AmbianceModifierFactoryTest extends CategoryTest {
  @Mock StageLevelAmbianceModifier stageLevelAmbianceModifier;
  @InjectMocks AmbianceModifierFactory ambianceModifierFactory;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testObtainModifier() {
    Level stageLevelNoStrategy = Level.newBuilder()
                                     .setSetupId("stageSetupId")
                                     .setRuntimeId("runtimeId")
                                     .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).build())
                                     .build();
    Ambiance ambiance =
        Ambiance.newBuilder()
            .addLevels(Level.newBuilder()
                           .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STAGES).build())
                           .build())
            .addLevels(stageLevelNoStrategy)
            .setMetadata(ExecutionMetadata.newBuilder().build())
            .setPlanExecutionId(generateUuid())
            .build();

    AmbianceModifier ambianceModifier = ambianceModifierFactory.obtainModifier(
        AmbianceUtils.obtainCurrentLevel(ambiance).getStepType().getStepCategory());
    assertThat(ambianceModifier).isEqualTo(stageLevelAmbianceModifier);

    Ambiance ambiance1 =
        ambiance.toBuilder()
            .addLevels(Level.newBuilder()
                           .setSetupId("stepSetupId")
                           .setRuntimeId("runtimeId2")
                           .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STEP).build())
                           .build())
            .build();
    AmbianceModifier ambianceModifier1 = ambianceModifierFactory.obtainModifier(
        AmbianceUtils.obtainCurrentLevel(ambiance1).getStepType().getStepCategory());
    assertThat(ambianceModifier1).isNull();
  }
}