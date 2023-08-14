/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.data;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ALEXEI;
import static io.harness.rule.OwnerRule.ARCHIT;
import static io.harness.rule.OwnerRule.PRASHANT;
import static io.harness.rule.OwnerRule.PRASHANTSHARMA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.when;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.data.OutcomeInstance;
import io.harness.engine.expressions.ExpressionEvaluatorProvider;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.data.StepOutcomeRef;
import io.harness.pms.data.PmsOutcome;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.Outcome;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;
import io.harness.rule.Owner;
import io.harness.utils.AmbianceTestUtils;
import io.harness.utils.DummyOrchestrationOutcome;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.springframework.data.mongodb.core.MongoTemplate;

@OwnedBy(HarnessTeam.PIPELINE)
public class PmsOutcomeServiceImplTest extends OrchestrationTestBase {
  @Inject private MongoTemplate mongoTemplate;
  @Mock private ExpressionEvaluatorProvider expressionEvaluatorProvider;
  @Inject @InjectMocks @Spy private PmsOutcomeServiceImpl pmsOutcomeService;

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestSaveAndFind() {
    Ambiance ambiance = AmbianceTestUtils.buildAmbiance();
    String outcomeName = "outcomeName";
    Outcome outcome = DummyOrchestrationOutcome.builder().test("test").build();
    pmsOutcomeService.consume(ambiance, outcomeName, RecastOrchestrationUtils.toJson(outcome), "PHASE");

    // Resolve with producer id
    DummyOrchestrationOutcome savedOutcome = RecastOrchestrationUtils.fromJson(
        pmsOutcomeService.resolve(ambiance,
            RefObjectUtils.getOutcomeRefObject(outcomeName, AmbianceUtils.obtainCurrentSetupId(ambiance), null)),
        DummyOrchestrationOutcome.class);
    assertThat(savedOutcome).isNotNull();
    assertThat(savedOutcome.getTest()).isEqualTo("test");

    // Resolve with scope
    savedOutcome = RecastOrchestrationUtils.fromJson(
        pmsOutcomeService.resolve(ambiance, RefObjectUtils.getOutcomeRefObject(outcomeName)),
        DummyOrchestrationOutcome.class);
    assertThat(savedOutcome).isNotNull();
    assertThat(savedOutcome.getTest()).isEqualTo("test");
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestOutcomeInstancePopulation() {
    Ambiance ambiance = AmbianceTestUtils.buildAmbiance();
    String outcomeName = "outcomeName";
    Outcome outcome = DummyOrchestrationOutcome.builder().test("test").build();
    String outComeInstanceId =
        pmsOutcomeService.consume(ambiance, outcomeName, RecastOrchestrationUtils.toJson(outcome), "PHASE");

    assertThat(outComeInstanceId).isNotNull();
    OutcomeInstance instance = mongoTemplate.findById(outComeInstanceId, OutcomeInstance.class);
    assertThat(instance).isNotNull();
    assertThat(instance.getProducedBy()).isEqualTo(AmbianceUtils.obtainCurrentLevel(ambiance));
    assertThat(instance.getPlanExecutionId()).isEqualTo(AmbianceTestUtils.PLAN_EXECUTION_ID);
    assertThat(instance.getName()).isEqualTo(outcomeName);
    assertThat(instance.getGroupName()).isEqualTo("PHASE");
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestSaveAndFindForNull() {
    Ambiance ambiance = AmbianceTestUtils.buildAmbiance();
    String outcomeName = "outcomeName";
    pmsOutcomeService.consume(ambiance, outcomeName, null, null);

    Outcome outcome = RecastOrchestrationUtils.fromJson(
        pmsOutcomeService.resolve(ambiance, RefObjectUtils.getOutcomeRefObject(outcomeName)), Outcome.class);
    assertThat(outcome).isNull();
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void shouldTestDeleteAllOutcomesInstances() {
    Ambiance ambiance = AmbianceTestUtils.buildAmbiance();
    String outcomeName = "outcomeName";
    Outcome outcome1 = DummyOrchestrationOutcome.builder().test("test").build();
    pmsOutcomeService.consume(ambiance, outcomeName, RecastOrchestrationUtils.toJson(outcome1), null);
    Outcome outcome = RecastOrchestrationUtils.fromJson(
        pmsOutcomeService.resolve(ambiance, RefObjectUtils.getOutcomeRefObject(outcomeName)), Outcome.class);
    assertThat(outcome).isInstanceOf(DummyOrchestrationOutcome.class);

    String outcomeName2 = "outcomeName2";
    pmsOutcomeService.consume(ambiance, outcomeName2, RecastOrchestrationUtils.toJson(outcome1), null);
    Outcome outcome2 = RecastOrchestrationUtils.fromJson(
        pmsOutcomeService.resolve(ambiance, RefObjectUtils.getOutcomeRefObject(outcomeName2)), Outcome.class);
    assertThat(outcome2).isInstanceOf(DummyOrchestrationOutcome.class);

    pmsOutcomeService.deleteAllOutcomesInstances(Set.of(AmbianceTestUtils.PLAN_EXECUTION_ID));
    // As outcome don't exist anymore
    assertThatThrownBy(() -> pmsOutcomeService.resolve(ambiance, RefObjectUtils.getOutcomeRefObject(outcomeName)))
        .isInstanceOf(OutcomeException.class);
    assertThatThrownBy(() -> pmsOutcomeService.resolve(ambiance, RefObjectUtils.getOutcomeRefObject(outcomeName2)))
        .isInstanceOf(OutcomeException.class);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldFetchAllOutcomesByRuntimeId() {
    Ambiance ambiance = AmbianceTestUtils.buildAmbiance();
    String outcomeName = "outcome";
    Ambiance ambiance1 = AmbianceTestUtils.buildAmbiance();
    String outcomeName1 = "outcome1";

    pmsOutcomeService.consume(ambiance, outcomeName,
        RecastOrchestrationUtils.toJson(DummyOrchestrationOutcome.builder().test("test").build()), null);
    pmsOutcomeService.consume(ambiance1, outcomeName1,
        RecastOrchestrationUtils.toJson(DummyOrchestrationOutcome.builder().test("test1").build()), null);

    List<String> outcomes = pmsOutcomeService.findAllByRuntimeId(
        ambiance.getPlanExecutionId(), AmbianceUtils.obtainCurrentRuntimeId(ambiance));
    assertThat(outcomes.size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldFetchOutcomes() {
    Ambiance ambiance = AmbianceTestUtils.buildAmbiance();
    String outcomeName = "outcome";
    Ambiance ambiance1 = AmbianceTestUtils.buildAmbiance();
    String outcomeName1 = "outcome1";

    String instanceId1 = pmsOutcomeService.consume(ambiance, outcomeName,
        RecastOrchestrationUtils.toJson(DummyOrchestrationOutcome.builder().test("test1").build()), null);
    String instanceId2 = pmsOutcomeService.consume(ambiance1, outcomeName1,
        RecastOrchestrationUtils.toJson(DummyOrchestrationOutcome.builder().test("test2").build()), null);

    List<String> outcomes = pmsOutcomeService.fetchOutcomes(Arrays.asList(instanceId1, instanceId2));
    assertThat(outcomes.size()).isEqualTo(2);
    assertThat(outcomes.stream()
                   .map(oc -> (RecastOrchestrationUtils.fromJson(oc, DummyOrchestrationOutcome.class)).getTest())
                   .collect(Collectors.toList()))
        .containsExactlyInAnyOrder("test1", "test2");
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldFetchOutcome() {
    Ambiance ambiance = AmbianceTestUtils.buildAmbiance();
    String outcomeName = "outcome";

    String instanceId = pmsOutcomeService.consume(ambiance, outcomeName,
        RecastOrchestrationUtils.toJson(DummyOrchestrationOutcome.builder().test("test").build()), null);

    String outcomeJson = pmsOutcomeService.fetchOutcome(instanceId);
    Outcome outcome = RecastOrchestrationUtils.fromJson(outcomeJson, Outcome.class);
    assertThat(outcome).isInstanceOf(DummyOrchestrationOutcome.class);
    assertThat(((DummyOrchestrationOutcome) outcome).getTest()).isEqualTo("test");
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldResolveOptional() {
    Ambiance ambiance = AmbianceTestUtils.buildAmbiance();
    String outcomeName = "outcome";

    String outcomeJson = RecastOrchestrationUtils.toJson(DummyOrchestrationOutcome.builder().test("test").build());
    pmsOutcomeService.consume(ambiance, outcomeName, outcomeJson, null);

    // Resolve with producer id
    OptionalOutcome optionalOutcome = pmsOutcomeService.resolveOptional(
        ambiance, RefObjectUtils.getOutcomeRefObject(outcomeName, AmbianceUtils.obtainCurrentSetupId(ambiance), null));
    assertThat(optionalOutcome).isNotNull();
    assertThat(optionalOutcome.getOutcome()).isEqualTo(outcomeJson);
    assertThat(optionalOutcome.isFound()).isTrue();

    // Resolve with scope
    optionalOutcome = pmsOutcomeService.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(outcomeName));
    assertThat(optionalOutcome).isNotNull();
    assertThat(optionalOutcome.getOutcome()).isEqualTo(outcomeJson);
    assertThat(optionalOutcome.isFound()).isTrue();
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldResolveInternalWhenOutcomeIsNotFound() {
    Ambiance ambiance = AmbianceTestUtils.buildAmbiance();
    String outcomeName = "outcome";

    OptionalOutcome optionalOutcome = pmsOutcomeService.resolveOptional(
        ambiance, RefObjectUtils.getOutcomeRefObject(outcomeName, AmbianceUtils.obtainCurrentSetupId(ambiance), null));
    assertThat(optionalOutcome).isNotNull();
    assertThat(optionalOutcome.getOutcome()).isNull();
    assertThat(optionalOutcome.isFound()).isEqualTo(false);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldResolveOptionalWithDots() {
    Ambiance ambiance = AmbianceTestUtils.buildAmbiance();
    String outcomeName = "outcome.name";

    String outcomeJson = RecastOrchestrationUtils.toJson(DummyOrchestrationOutcome.builder().test("test").build());
    pmsOutcomeService.consume(ambiance, outcomeName, outcomeJson, null);

    when(expressionEvaluatorProvider.get(any(), any(Ambiance.class), anySet(), anyBoolean()))
        .thenReturn(prepareEngineExpressionEvaluator(
            ImmutableMap.of(outcomeName, DummyOrchestrationOutcome.builder().test("test").build())));

    // Resolve with producer id
    OptionalOutcome optionalOutcome = pmsOutcomeService.resolveOptional(
        ambiance, RefObjectUtils.getOutcomeRefObject(outcomeName, AmbianceUtils.obtainCurrentSetupId(ambiance), null));
    assertThat(optionalOutcome).isNotNull();
    assertThat(optionalOutcome.getOutcome()).isEqualTo(outcomeJson);
    assertThat(optionalOutcome.isFound()).isTrue();

    // Resolve with scope
    optionalOutcome = pmsOutcomeService.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(outcomeName));
    assertThat(optionalOutcome).isNotNull();
    assertThat(optionalOutcome.getOutcome()).isEqualTo(outcomeJson);
    assertThat(optionalOutcome.isFound()).isTrue();
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void fetchOutcomeRefs() {
    String planId = generateUuid();
    String planExecutionId = generateUuid();
    String nodeExecution1Id = generateUuid();
    String nodeExecution2Id = generateUuid();

    Ambiance.Builder ambianceBuilder = Ambiance.newBuilder().setPlanExecutionId(planExecutionId).setPlanId(planId);
    Ambiance ambiance1 = ambianceBuilder.addLevels(Level.newBuilder().setRuntimeId(nodeExecution1Id).build()).build();
    Ambiance ambiance2 = ambianceBuilder.addLevels(Level.newBuilder().setRuntimeId(nodeExecution2Id).build()).build();
    DummyOrchestrationOutcome outcome1 = DummyOrchestrationOutcome.builder().test("test1").build();
    DummyOrchestrationOutcome outcome2 = DummyOrchestrationOutcome.builder().test("test2").build();
    DummyOrchestrationOutcome outcome3 = DummyOrchestrationOutcome.builder().test("test3").build();

    pmsOutcomeService.consume(ambiance1, "outcome1", RecastOrchestrationUtils.toJson(outcome1), null);
    pmsOutcomeService.consume(ambiance1, "outcome2", RecastOrchestrationUtils.toJson(outcome2), null);
    pmsOutcomeService.consume(ambiance2, "outcome3", RecastOrchestrationUtils.toJson(outcome3), null);
    Map<String, List<StepOutcomeRef>> refMap =
        pmsOutcomeService.fetchOutcomeRefs(Arrays.asList(nodeExecution1Id, nodeExecution2Id));

    assertThat(refMap).containsKey(nodeExecution1Id);
    List<StepOutcomeRef> refs1 = refMap.get(nodeExecution1Id);
    assertThat(refs1).hasSize(2);
    assertThat(refs1.stream().map(StepOutcomeRef::getName)).containsExactlyInAnyOrder("outcome1", "outcome2");

    assertThat(refMap).containsKey(nodeExecution2Id);
    List<StepOutcomeRef> refs2 = refMap.get(nodeExecution2Id);
    assertThat(refs2).hasSize(1);
    assertThat(refs2.stream().map(StepOutcomeRef::getName)).containsExactlyInAnyOrder("outcome3");
  }

  public static class SampleEngineExpressionEvaluator extends EngineExpressionEvaluator {
    private final Map<String, Object> contextMap;

    public SampleEngineExpressionEvaluator(Map<String, Object> contextMap) {
      super(null);
      this.contextMap = contextMap;
    }

    @Override
    protected void initialize() {
      super.initialize();
      contextMap.forEach(this::addToContext);
    }
  }

  private static EngineExpressionEvaluator prepareEngineExpressionEvaluator(Map<String, Object> contextMap) {
    return new SampleEngineExpressionEvaluator(contextMap);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testFetchOutcomeInstanceByRuntimeId() {
    List<OutcomeInstance> result = pmsOutcomeService.fetchOutcomeInstanceByRuntimeId("abc");
    assertThat(result.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testCloneForRetryExecution() {
    Ambiance ambiance = AmbianceTestUtils.buildAmbiance();
    String outcomeName = "outcome.name";

    String outcomeJson = RecastOrchestrationUtils.toJson(DummyOrchestrationOutcome.builder().test("test").build());
    pmsOutcomeService.consume(ambiance, outcomeName, outcomeJson, null);

    when(pmsOutcomeService.fetchOutcomeInstanceByRuntimeId(any()))
        .thenReturn(Collections.singletonList(OutcomeInstance.builder()
                                                  .planExecutionId("plan")
                                                  .name(outcomeName)
                                                  .outcomeValue(PmsOutcome.parse(outcomeJson))
                                                  .build()));
    assertThat(pmsOutcomeService.cloneForRetryExecution(ambiance, "node").size()).isEqualTo(1);
  }
}
