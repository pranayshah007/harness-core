/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.data;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ARCHIT;
import static io.harness.rule.OwnerRule.PRASHANT;
import static io.harness.rule.OwnerRule.PRASHANTSHARMA;
import static io.harness.rule.OwnerRule.VIVEK_DIXIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.data.ExecutionSweepingOutputInstance;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;
import io.harness.rule.Owner;
import io.harness.utils.AmbianceTestUtils;
import io.harness.utils.DummySweepingOutput;

import com.google.inject.Inject;
import java.util.List;
import java.util.Set;
import org.bson.Document;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockedStatic;
import org.springframework.data.mongodb.core.MongoTemplate;

@OwnedBy(HarnessTeam.PIPELINE)
public class PmsSweepingOutputServiceImplTest extends OrchestrationTestBase {
  private static final String STEP_RUNTIME_ID = generateUuid();
  private static final String STEP_SETUP_ID = generateUuid();

  @Inject private PmsSweepingOutputService pmsSweepingOutputService;
  @Inject private MongoTemplate mongoTemplate;

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void testConsumeAndFind() {
    Ambiance ambianceSection = AmbianceTestUtils.buildAmbiance();
    Ambiance ambiancePhase = AmbianceUtils.cloneForFinish(ambianceSection);
    Ambiance ambianceStep = prepareStepAmbiance(ambianceSection);

    String outputName = "outputName";
    String testValueSection = "testSection";
    String testValueStep = "testStep";

    pmsSweepingOutputService.consume(ambianceSection, outputName,
        RecastOrchestrationUtils.toJson(DummySweepingOutput.builder().test(testValueSection).build()), null);
    validateResult(resolve(ambianceSection, outputName), testValueSection);
    validateResult(resolve(ambianceStep, outputName), testValueSection);
    assertThatThrownBy(() -> resolve(ambiancePhase, outputName)).isInstanceOf(SweepingOutputException.class);

    pmsSweepingOutputService.consume(ambianceStep, outputName,
        RecastOrchestrationUtils.toJson(DummySweepingOutput.builder().test(testValueStep).build()), null);
    validateResult(resolve(ambianceSection, outputName), testValueSection);
    validateResult(resolve(ambianceStep, outputName), testValueStep);
    assertThatThrownBy(() -> resolve(ambiancePhase, outputName)).isInstanceOf(SweepingOutputException.class);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void testSaveWithLevelsToKeepAndFind() {
    Ambiance ambianceSection = AmbianceTestUtils.buildAmbiance();
    Ambiance ambiancePhase = AmbianceUtils.cloneForFinish(ambianceSection);
    Ambiance ambianceStep = prepareStepAmbiance(ambianceSection);

    String outputName = "outputName";
    String testValueSection = "testSection";
    String testValueStep = "testStep";

    pmsSweepingOutputService.consumeInternal(ambianceSection, AmbianceUtils.obtainCurrentLevel(ambianceSection),
        outputName, RecastOrchestrationUtils.toJson(DummySweepingOutput.builder().test(testValueSection).build()),
        null);
    validateResult(resolve(ambianceSection, outputName), testValueSection);
    validateResult(resolve(ambianceStep, outputName), testValueSection);
    assertThatThrownBy(() -> resolve(ambiancePhase, outputName)).isInstanceOf(SweepingOutputException.class);

    pmsSweepingOutputService.consumeInternal(AmbianceUtils.clone(ambianceStep, 0),
        AmbianceUtils.obtainCurrentLevel(ambianceSection), outputName,
        RecastOrchestrationUtils.toJson(DummySweepingOutput.builder().test(testValueStep).build()), null);
    validateResult(resolve(ambiancePhase, outputName), testValueStep);
    validateResult(resolve(ambianceSection, outputName), testValueSection);
    validateResult(resolve(ambianceStep, outputName), testValueSection);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void testSaveAtScopeAndFind() {
    Ambiance ambianceSection = AmbianceTestUtils.buildAmbiance();
    Ambiance ambiancePhase = AmbianceUtils.cloneForFinish(ambianceSection);
    Ambiance ambianceStep = prepareStepAmbiance(ambianceSection);

    String outputName = "outputName";
    String testValueSection = "testSection";
    String testValueStep = "testStep";

    pmsSweepingOutputService.consume(ambianceSection, outputName,
        RecastOrchestrationUtils.toJson(DummySweepingOutput.builder().test(testValueSection).build()), "SECTION");
    validateResult(resolve(ambianceSection, outputName), testValueSection);
    validateResult(resolve(ambianceStep, outputName), testValueSection);
    assertThatThrownBy(() -> resolve(ambiancePhase, outputName)).isInstanceOf(SweepingOutputException.class);

    pmsSweepingOutputService.consume(ambianceStep, outputName,
        RecastOrchestrationUtils.toJson(DummySweepingOutput.builder().test(testValueStep).build()),
        ResolverUtils.GLOBAL_GROUP_SCOPE);
    validateResult(resolve(ambiancePhase, outputName), testValueStep);
    validateResult(resolve(ambianceSection, outputName), testValueSection);
    validateResult(resolve(ambianceStep, outputName), testValueSection);

    assertThatThrownBy(
        ()
            -> pmsSweepingOutputService.consume(ambianceSection, "randomOutputName",
                RecastOrchestrationUtils.toJson(DummySweepingOutput.builder().test("randomTestValue").build()),
                "RANDOM"))
        .isInstanceOf(GroupNotFoundException.class);
  }

  private void validateResult(Document foundOutput, String testValue) {
    assertThat(foundOutput).isNotNull();
    assertThat(foundOutput.getString("test")).isEqualTo(testValue);
  }

  private Ambiance prepareStepAmbiance(Ambiance ambianceSection) {
    return AmbianceUtils.cloneForChild(ambianceSection,
        Level.newBuilder()
            .setRuntimeId(STEP_RUNTIME_ID)
            .setSetupId(STEP_SETUP_ID)
            .setStepType(StepType.newBuilder().setType("SHELL_SCRIPT").setStepCategory(StepCategory.STEP).build())
            .build());
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestSaveAndFindForNull() {
    Ambiance ambiance = AmbianceTestUtils.buildAmbiance();
    String outputName = "outcomeName";
    pmsSweepingOutputService.consume(ambiance, outputName, null, null);

    Document output = resolve(ambiance, outputName);
    assertThat(output).isNull();
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void shouldTestDeleteAllSweepingOutputInstances() {
    Ambiance ambiance = AmbianceTestUtils.buildAmbiance();
    String outputName = "outcomeName";
    DummySweepingOutput output1 = DummySweepingOutput.builder().test("test").build();
    pmsSweepingOutputService.consume(ambiance, outputName, RecastOrchestrationUtils.toJson(output1), null);
    validateResult(resolve(ambiance, outputName), "test");

    String outputName2 = "outcomeName2";
    pmsSweepingOutputService.consume(ambiance, outputName2, RecastOrchestrationUtils.toJson(output1), null);
    validateResult(resolve(ambiance, outputName2), "test");

    pmsSweepingOutputService.deleteAllSweepingOutputInstances(Set.of(AmbianceTestUtils.PLAN_EXECUTION_ID));
    // As output don't exist anymore
    assertThatThrownBy(() -> resolve(ambiance, outputName)).isInstanceOf(SweepingOutputException.class);
    assertThatThrownBy(() -> resolve(ambiance, outputName2)).isInstanceOf(SweepingOutputException.class);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testFetchOutcomeInstanceByRuntimeId() {
    List<ExecutionSweepingOutputInstance> result = pmsSweepingOutputService.fetchOutcomeInstanceByRuntimeId("abc");
    assertThat(result.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestOutputInstancePopulation() {
    Ambiance ambiance = AmbianceTestUtils.buildAmbiance();
    String outcomeName = "outcomeName";
    DummySweepingOutput output = DummySweepingOutput.builder().test("test").build();
    String outputInstanceId =
        pmsSweepingOutputService.consume(ambiance, outcomeName, RecastOrchestrationUtils.toJson(output), "PHASE");

    assertThat(outputInstanceId).isNotNull();
    ExecutionSweepingOutputInstance instance =
        mongoTemplate.findById(outputInstanceId, ExecutionSweepingOutputInstance.class);
    assertThat(instance).isNotNull();
    assertThat(instance.getProducedBy()).isEqualTo(AmbianceUtils.obtainCurrentLevel(ambiance));
    assertThat(instance.getPlanExecutionId()).isEqualTo(AmbianceTestUtils.PLAN_EXECUTION_ID);
    assertThat(instance.getName()).isEqualTo(outcomeName);
    assertThat(instance.getGroupName()).isEqualTo("PHASE");
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testResolveForFQNCreation() {
    Ambiance ambiance = AmbianceTestUtils.buildAmbiance();
    String outputName = ".outcomeName";
    pmsSweepingOutputService.consume(ambiance, outputName, null, null);
    MockedStatic<EngineExpressionEvaluator> evaluatorMockedStatic = mockStatic(EngineExpressionEvaluator.class);
    evaluatorMockedStatic.when(() -> EngineExpressionEvaluator.createExpression(any())).thenReturn(null);

    Document output = resolve(ambiance, outputName);
    assertThat(output).isNull();
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testResolveOptional() {
    Ambiance ambiance = AmbianceTestUtils.buildAmbiance();
    String outputName = ".outcomeName";
    pmsSweepingOutputService.consume(ambiance, outputName, null, null);
    MockedStatic<EngineExpressionEvaluator> evaluatorMockedStatic = mockStatic(EngineExpressionEvaluator.class);
    evaluatorMockedStatic.when(() -> EngineExpressionEvaluator.createExpression(any())).thenReturn(null);

    assertThatCode(
        () -> pmsSweepingOutputService.resolveOptional(ambiance, RefObjectUtils.getSweepingOutputRefObject(outputName)))
        .doesNotThrowAnyException();
  }

  private Document resolve(Ambiance ambiance, String outputName) {
    String resolvedVal =
        pmsSweepingOutputService.resolve(ambiance, RefObjectUtils.getSweepingOutputRefObject(outputName));
    if (resolvedVal == null) {
      return null;
    }
    return Document.parse(resolvedVal);
  }
}
