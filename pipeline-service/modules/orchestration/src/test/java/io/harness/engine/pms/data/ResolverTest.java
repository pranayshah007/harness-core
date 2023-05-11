/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.data;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.PRASHANT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import io.harness.OrchestrationTestBase;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.refobjects.RefObject;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;

public class ResolverTest extends OrchestrationTestBase {
  public static final String ACCOUNT_ID = generateUuid();
  public static final String APP_ID = generateUuid();
  public static final String PLAN_EXECUTION_ID = generateUuid();

  private static final String TEST_DATA = "TEST_DATA_RESOLVER";
  private static final String OUTPUT_NAME = "TEST_DATA_RESOLVER";

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestConsumeEmptyGroup() {
    NoopResolver resolver = spy(NoopResolver.class);
    Ambiance ambiance = buildAmbiance();
    resolver.consume(ambiance, OUTPUT_NAME, TEST_DATA, "");
    ArgumentCaptor<Ambiance> ambianceCaptor = ArgumentCaptor.forClass(Ambiance.class);
    ArgumentCaptor<Level> levelCaptor = ArgumentCaptor.forClass(Level.class);
    verify(resolver).consumeInternal(
        ambianceCaptor.capture(), levelCaptor.capture(), eq(OUTPUT_NAME), eq(TEST_DATA), eq(""));
    assertThat(ambianceCaptor.getValue().getLevelsCount()).isEqualTo(ambiance.getLevelsCount());
    Level producedBy = AmbianceUtils.obtainCurrentLevel(ambiance);
    assertThat(levelCaptor.getValue()).isEqualTo(producedBy);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestConsumeGlobal() {
    NoopResolver resolver = spy(NoopResolver.class);
    Ambiance ambiance = buildAmbiance();
    resolver.consume(ambiance, OUTPUT_NAME, TEST_DATA, ResolverUtils.GLOBAL_GROUP_SCOPE);
    ArgumentCaptor<Ambiance> ambianceCaptor = ArgumentCaptor.forClass(Ambiance.class);
    ArgumentCaptor<Level> levelCaptor = ArgumentCaptor.forClass(Level.class);
    verify(resolver).consumeInternal(ambianceCaptor.capture(), levelCaptor.capture(), eq(OUTPUT_NAME), eq(TEST_DATA),
        eq(ResolverUtils.GLOBAL_GROUP_SCOPE));
    assertThat(ambianceCaptor.getValue().getLevelsCount()).isEqualTo(0);
    Level producedBy = AmbianceUtils.obtainCurrentLevel(ambiance);
    assertThat(levelCaptor.getValue()).isEqualTo(producedBy);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestConsumeStage() {
    NoopResolver resolver = spy(NoopResolver.class);
    Ambiance ambiance = buildAmbiance();
    resolver.consume(ambiance, OUTPUT_NAME, TEST_DATA, "STAGE");
    ArgumentCaptor<Ambiance> ambianceCaptor = ArgumentCaptor.forClass(Ambiance.class);
    ArgumentCaptor<Level> levelCaptor = ArgumentCaptor.forClass(Level.class);
    verify(resolver).consumeInternal(
        ambianceCaptor.capture(), levelCaptor.capture(), eq(OUTPUT_NAME), eq(TEST_DATA), eq("STAGE"));
    assertThat(ambianceCaptor.getValue().getLevelsCount()).isEqualTo(3);
    assertThat(ambianceCaptor.getValue().getLevels(0).getGroup()).isEqualTo("PIPELINE");
    assertThat(ambianceCaptor.getValue().getLevels(1).getGroup()).isEqualTo("STAGES");
    assertThat(ambianceCaptor.getValue().getLevels(2).getGroup()).isEqualTo("STAGE");
    Level producedBy = AmbianceUtils.obtainCurrentLevel(ambiance);
    assertThat(levelCaptor.getValue()).isEqualTo(producedBy);
  }

  private Ambiance buildAmbiance() {
    Level pipelineLevel =
        Level.newBuilder()
            .setRuntimeId(generateUuid())
            .setSetupId(generateUuid())
            .setStepType(StepType.newBuilder().setType("PIPELINE_SETUP").setStepCategory(StepCategory.PIPELINE).build())
            .setGroup("PIPELINE")
            .build();
    Level stagesLevel =
        Level.newBuilder()
            .setRuntimeId(generateUuid())
            .setSetupId(generateUuid())
            .setStepType(StepType.newBuilder().setType("STAGES").setStepCategory(StepCategory.STAGES).build())
            .setGroup("STAGES")
            .build();

    Level stageLevel =
        Level.newBuilder()
            .setRuntimeId(generateUuid())
            .setSetupId(generateUuid())
            .setStepType(StepType.newBuilder().setType("STAGE").setStepCategory(StepCategory.STAGE).build())
            .setGroup("STAGE")
            .build();

    Level stepLevel = Level.newBuilder()
                          .setRuntimeId(generateUuid())
                          .setSetupId(generateUuid())
                          .setStepType(StepType.newBuilder().setType("STEP").setStepCategory(StepCategory.STEP).build())
                          .setGroup("STEP")
                          .build();

    List<Level> levels = new ArrayList<>();
    levels.add(pipelineLevel);
    levels.add(stagesLevel);
    levels.add(stageLevel);
    levels.add(stepLevel);
    return Ambiance.newBuilder()
        .setPlanExecutionId(PLAN_EXECUTION_ID)
        .putAllSetupAbstractions(ImmutableMap.of("accountId", ACCOUNT_ID, "appId", APP_ID))
        .addAllLevels(levels)
        .build();
  }

  private static class NoopResolver implements Resolver {
    NoopResolver() {}

    @Override
    public String resolve(Ambiance ambiance, RefObject refObject) {
      return null;
    }

    @Override
    public String consumeInternal(Ambiance ambiance, Level producedBy, String name, String value, String groupName) {
      return null;
    }
  }
}
