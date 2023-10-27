/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.data;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ARCHIT;
import static io.harness.rule.OwnerRule.NAMANG;
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

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testSaveAtScopeAndFindAtScope() {
    Ambiance ambianceSection = AmbianceTestUtils.buildAmbiance();
    Ambiance ambiancePhase = AmbianceUtils.cloneForFinish(ambianceSection);
    Ambiance ambianceStep = prepareStepAmbiance(ambianceSection);

    String outputName = "outputName";
    String testValueSection = "testSection";
    String testValueStep = "testStep";

    pmsSweepingOutputService.consume(ambianceSection, outputName,
        RecastOrchestrationUtils.toJson(DummySweepingOutput.builder().test(testValueSection).build()), "SECTION");

    validateResult(resolveForScope(ambianceSection, outputName, "SECTION"), testValueSection);
    validateResult(resolveForScope(ambianceSection, outputName, ""), testValueSection);
    assertThatThrownBy(() -> resolveForScope(ambianceSection, outputName, "PHASE"))
        .isInstanceOf(SweepingOutputException.class);

    validateResult(resolveForScope(ambianceStep, outputName, "SECTION"), testValueSection);
    validateResult(resolveForScope(ambianceStep, outputName, ""), testValueSection);
    assertThatThrownBy(() -> resolveForScope(ambianceStep, outputName, "PHASE"))
        .isInstanceOf(SweepingOutputException.class);

    assertThatThrownBy(() -> resolveForScope(ambiancePhase, outputName, "SECTION"))
        .isInstanceOf(SweepingOutputException.class);

    pmsSweepingOutputService.consume(ambianceStep, outputName,
        RecastOrchestrationUtils.toJson(DummySweepingOutput.builder().test(testValueStep).build()),
        ResolverUtils.GLOBAL_GROUP_SCOPE);
    validateResult(resolveForScope(ambiancePhase, outputName, ResolverUtils.GLOBAL_GROUP_SCOPE), testValueStep);
    validateResult(resolveForScope(ambiancePhase, outputName, ""), testValueStep);
    validateResult(resolveForScope(ambianceSection, outputName, ResolverUtils.GLOBAL_GROUP_SCOPE), testValueStep);
    validateResult(resolveForScope(ambianceStep, outputName, ResolverUtils.GLOBAL_GROUP_SCOPE), testValueStep);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testSaveAtScopeAndFindAtScopeOptional() {
    Ambiance ambianceSection = AmbianceTestUtils.buildAmbiance();
    Ambiance ambiancePhase = AmbianceUtils.cloneForFinish(ambianceSection);
    Ambiance ambianceStep = prepareStepAmbiance(ambianceSection);

    String outputName = "outputName";
    String testValueSection = "testSection";
    String testValueStep = "testStep";

    pmsSweepingOutputService.consume(ambianceSection, outputName,
        RecastOrchestrationUtils.toJson(DummySweepingOutput.builder().test(testValueSection).build()), "SECTION");

    validateResult(resolveForScopeOptional(ambianceSection, outputName, "SECTION"), testValueSection);
    validateResult(resolveForScopeOptional(ambianceSection, outputName, ""), testValueSection);
    assertThat(resolveForScopeOptional(ambianceSection, outputName, "PHASE")).isNull();

    validateResult(resolveForScopeOptional(ambianceStep, outputName, "SECTION"), testValueSection);
    validateResult(resolveForScopeOptional(ambianceStep, outputName, ""), testValueSection);
    assertThat(resolveForScopeOptional(ambianceStep, outputName, "PHASE")).isNull();

    assertThat(resolveForScopeOptional(ambiancePhase, outputName, "SECTION")).isNull();

    pmsSweepingOutputService.consume(ambianceStep, outputName,
        RecastOrchestrationUtils.toJson(DummySweepingOutput.builder().test(testValueStep).build()),
        ResolverUtils.GLOBAL_GROUP_SCOPE);
    validateResult(resolveForScopeOptional(ambiancePhase, outputName, ResolverUtils.GLOBAL_GROUP_SCOPE), testValueStep);
    validateResult(resolveForScopeOptional(ambiancePhase, outputName, ""), testValueStep);
    validateResult(
        resolveForScopeOptional(ambianceSection, outputName, ResolverUtils.GLOBAL_GROUP_SCOPE), testValueStep);
    validateResult(resolveForScopeOptional(ambianceStep, outputName, ResolverUtils.GLOBAL_GROUP_SCOPE), testValueStep);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testSaveAtScopeAndFindAtScopeWithStepGroupNestingShouldReturnChild() {
    Ambiance ambianceSection = AmbianceTestUtils.buildAmbiance();
    Ambiance ambianceParentStepGroup = addStepGroupAmbiance(ambianceSection, generateUuid());
    Ambiance ambianceChildStepGroup = addStepGroupAmbiance(ambianceParentStepGroup, generateUuid());
    Ambiance ambianceStep = prepareStepAmbiance(ambianceChildStepGroup);

    String outputName = "outputName";
    String testValueStepParent = "testStepP";
    String testValueStepChild = "testStepC";

    // published to inner step group
    pmsSweepingOutputService.consume(ambianceStep, outputName,
        RecastOrchestrationUtils.toJson(DummySweepingOutput.builder().test(testValueStepChild).build()), "StepGroup");
    // published to outer step group
    pmsSweepingOutputService.consume(ambianceParentStepGroup, outputName,
        RecastOrchestrationUtils.toJson(DummySweepingOutput.builder().test(testValueStepParent).build()), "StepGroup");

    // at step level will return nearest stepGroup output
    validateResult(resolveForScopeOptional(ambianceStep, outputName, "StepGroup"), testValueStepChild);
    // at inner step group level will return child stepGroup output
    validateResult(resolveForScopeOptional(ambianceChildStepGroup, outputName, "StepGroup"), testValueStepChild);
    // at outer step group level will return parent stepGroup output
    validateResult(resolveForScopeOptional(ambianceParentStepGroup, outputName, "StepGroup"), testValueStepParent);
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

  private Ambiance addStepGroupAmbiance(Ambiance ambianceSection, String runtimeId) {
    return AmbianceUtils.cloneForChild(ambianceSection,
        Level.newBuilder().setRuntimeId(runtimeId).setSetupId(runtimeId).setGroup("StepGroup").build());
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
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void shouldTestUpdateTTLSweepingOutputInstances() {
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

  private Document resolveForScope(Ambiance ambiance, String outputName, String groupName) {
    String resolvedVal = pmsSweepingOutputService.resolve(
        ambiance, RefObjectUtils.getSweepingOutputRefObjectUsingGroup(outputName, groupName));
    if (resolvedVal == null) {
      return null;
    }
    return Document.parse(resolvedVal);
  }

  private Document resolveForScopeOptional(Ambiance ambiance, String outputName, String groupName) {
    RawOptionalSweepingOutput resolvedVal = pmsSweepingOutputService.resolveOptional(
        ambiance, RefObjectUtils.getSweepingOutputRefObjectUsingGroup(outputName, groupName));
    if (!resolvedVal.found) {
      return null;
    }
    return Document.parse(resolvedVal.output);
  }
}
