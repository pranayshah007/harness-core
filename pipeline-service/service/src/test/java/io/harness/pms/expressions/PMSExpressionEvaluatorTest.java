/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.expressions;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ARCHIT;
import static io.harness.rule.OwnerRule.BRIJESH;

import static java.util.Arrays.asList;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.PipelineServiceApplication;
import io.harness.PipelineServiceTestBase;
import io.harness.PipelineServiceTestHelper;
import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.engine.expressions.OrchestrationConstants;
import io.harness.engine.pms.data.PmsOutcomeService;
import io.harness.engine.utils.PmsLevelUtils;
import io.harness.execution.NodeExecution;
import io.harness.execution.PlanExecution;
import io.harness.execution.expansion.PlanExpansionService;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.expression.EngineJexlContext;
import io.harness.expression.field.dummy.DummyOrchestrationField;
import io.harness.plan.PlanNode;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.expression.ExpressionResponse;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.data.stepparameters.PmsStepParameters;
import io.harness.pms.execution.utils.NodeProjectionUtils;
import io.harness.pms.expressions.functors.RemoteExpressionFunctor;
import io.harness.pms.sdk.PmsSdkInstance;
import io.harness.pms.sdk.PmsSdkInstanceService;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import org.joor.Reflect;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.data.util.CloseableIterator;

@OwnedBy(HarnessTeam.PIPELINE)
public class PMSExpressionEvaluatorTest extends PipelineServiceTestBase {
  @Mock private PlanExecutionService planExecutionService;
  @Mock NodeExecutionService nodeExecutionService;
  @Mock PmsOutcomeService pmsOutcomeService;
  @Mock PmsSdkInstanceService pmsSdkInstanceService;
  @Mock RemoteExpressionFunctor remoteExpressionFunctor;
  @Mock PlanExpansionService planExpansionService;

  private final String planExecutionId = generateUuid();
  NodeExecution nodeExecution1;
  PlanNode planNode1;

  NodeExecution nodeExecution2;
  PlanNode planNode2;

  NodeExecution nodeExecution3;
  PlanNode planNode3;

  NodeExecution nodeExecution4;
  PlanNode planNode4;

  NodeExecution nodeExecution5;
  PlanNode planNode5;

  @Before
  public void setup() {
    String nodeExecution1Id = generateUuid();
    String nodeExecution2Id = generateUuid();
    String nodeExecution3Id = generateUuid();
    String nodeExecution4Id = generateUuid();
    String nodeExecution5Id = generateUuid();

    Ambiance.Builder ambianceBuilder = Ambiance.newBuilder().setPlanExecutionId(planExecutionId);
    planNode1 = preparePlanNode(false, "pipeline", "pipelineValue", "PIPELINE");

    nodeExecution1 =
        NodeExecution.builder()
            .uuid(nodeExecution1Id)
            .ambiance(ambianceBuilder.addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecution1Id, planNode1)).build())
            .planNode(planNode1)
            .resolvedStepParameters(prepareStepParameters("pipelineResolvedValue"))
            .build();

    planNode2 = preparePlanNode(false, "stages", "stagesValue", null);
    nodeExecution2 =
        NodeExecution.builder()
            .uuid(nodeExecution2Id)
            .ambiance(ambianceBuilder.addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecution1Id, planNode1))
                          .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecution2Id, planNode2))
                          .build())
            .planNode(planNode2)
            .resolvedStepParameters(prepareStepParameters("stagesResolvedValue"))
            .parentId(nodeExecution1Id)
            .build();

    planNode3 = preparePlanNode(false, "stage", "stageValue", "STAGE");
    nodeExecution3 =
        NodeExecution.builder()
            .uuid(nodeExecution3Id)
            .ambiance(ambianceBuilder.addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecution1Id, planNode1))
                          .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecution2Id, planNode2))
                          .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecution3Id, planNode3))
                          .build())
            .planNode(planNode3)
            .resolvedStepParameters(prepareStepParameters("stageResolvedValue"))
            .parentId(nodeExecution2Id)
            .build();

    planNode4 = preparePlanNode(false, "d", "di1", null);
    nodeExecution4 =
        NodeExecution.builder()
            .uuid(nodeExecution4Id)
            .ambiance(ambianceBuilder.addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecution1Id, planNode1))
                          .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecution2Id, planNode2))
                          .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecution3Id, planNode3))
                          .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecution4Id, planNode4))
                          .build())
            .planNode(planNode4)
            .resolvedStepParameters(prepareStepParameters("dResolvedValue"))
            .parentId(nodeExecution3Id)
            .nextId(nodeExecution5Id)
            .build();

    planNode5 = preparePlanNode(false, "e", "ei1", null);
    nodeExecution5 =
        NodeExecution.builder()
            .uuid(nodeExecution5Id)
            .ambiance(ambianceBuilder.addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecution1Id, planNode1))
                          .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecution2Id, planNode2))
                          .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecution3Id, planNode3))
                          .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecution5Id, planNode5))
                          .build())
            .planNode(planNode5)
            .resolvedStepParameters(prepareStepParameters("eResolvedValue"))
            .previousId(nodeExecution4Id)
            .parentId(nodeExecution3Id)
            .build();

    when(nodeExecutionService.getWithFieldsIncluded(
             nodeExecution1.getUuid(), NodeProjectionUtils.fieldsForExpressionEngine))
        .thenReturn(nodeExecution1);
    when(nodeExecutionService.getWithFieldsIncluded(
             nodeExecution2.getUuid(), NodeProjectionUtils.fieldsForExpressionEngine))
        .thenReturn(nodeExecution2);
    when(nodeExecutionService.getWithFieldsIncluded(
             nodeExecution3.getUuid(), NodeProjectionUtils.fieldsForExpressionEngine))
        .thenReturn(nodeExecution3);
    when(nodeExecutionService.getWithFieldsIncluded(
             nodeExecution4.getUuid(), NodeProjectionUtils.fieldsForExpressionEngine))
        .thenReturn(nodeExecution4);
    when(nodeExecutionService.getWithFieldsIncluded(
             nodeExecution5.getUuid(), NodeProjectionUtils.fieldsForExpressionEngine))
        .thenReturn(nodeExecution5);

    List<NodeExecution> nodeExecutionsList1 = Collections.singletonList(nodeExecution1);
    CloseableIterator<NodeExecution> iterator1 =
        PipelineServiceTestHelper.createCloseableIterator(nodeExecutionsList1.iterator());
    when(nodeExecutionService.fetchChildrenNodeExecutionsIterator(
             planExecutionId, null, NodeProjectionUtils.fieldsForExpressionEngine))
        .thenReturn(iterator1);

    List<NodeExecution> nodeExecutionsList2 = Collections.singletonList(nodeExecution2);
    CloseableIterator<NodeExecution> iterator2 =
        PipelineServiceTestHelper.createCloseableIterator(nodeExecutionsList2.iterator());
    when(nodeExecutionService.fetchChildrenNodeExecutionsIterator(
             planExecutionId, nodeExecution1.getUuid(), NodeProjectionUtils.fieldsForExpressionEngine))
        .thenReturn(iterator2);

    List<NodeExecution> nodeExecutionList3 = Collections.singletonList(nodeExecution3);
    CloseableIterator<NodeExecution> iterator3 =
        PipelineServiceTestHelper.createCloseableIterator(nodeExecutionList3.iterator());
    when(nodeExecutionService.fetchChildrenNodeExecutionsIterator(
             planExecutionId, nodeExecution2.getUuid(), NodeProjectionUtils.fieldsForExpressionEngine))
        .thenReturn(iterator3);

    List<NodeExecution> nodeExecutionList4 = asList(nodeExecution4, nodeExecution5);
    CloseableIterator<NodeExecution> iterator4 =
        PipelineServiceTestHelper.createCloseableIterator(nodeExecutionList4.iterator());
    when(nodeExecutionService.fetchChildrenNodeExecutionsIterator(
             planExecutionId, nodeExecution3.getUuid(), NodeProjectionUtils.fieldsForExpressionEngine))
        .thenReturn(iterator4);

    CloseableIterator<NodeExecution> emptyIterator =
        PipelineServiceTestHelper.createCloseableIterator(Collections.emptyListIterator());
    when(nodeExecutionService.fetchChildrenNodeExecutionsIterator(
             planExecutionId, nodeExecution4.getUuid(), NodeProjectionUtils.fieldsForExpressionEngine))
        .thenReturn(emptyIterator);
    when(nodeExecutionService.fetchChildrenNodeExecutionsIterator(
             planExecutionId, nodeExecution5.getUuid(), NodeProjectionUtils.fieldsForExpressionEngine))
        .thenReturn(emptyIterator);
    when(planExecutionService.getPlanExecutionMetadata(planExecutionId)).thenReturn(PlanExecution.builder().build());
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testNodeExecutionCurrentStatusWhenIgnoredFailure() {
    Ambiance newAmbiance = Ambiance.newBuilder()
                               .setPlanExecutionId(planExecutionId)
                               .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecution1.getUuid(), planNode1))
                               .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecution2.getUuid(), planNode2))
                               .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecution3.getUuid(), planNode3))
                               .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecution5.getUuid(), planNode5))
                               .build();

    Reflect.on(nodeExecution5).set("status", Status.IGNORE_FAILED);
    Reflect.on(nodeExecution4).set("status", Status.SUCCEEDED);

    // pipeline children
    when(nodeExecutionService.findAllChildrenWithStatusInAndWithoutOldRetries(
             planExecutionId, nodeExecution1.getUuid(), null, false, Collections.emptySet(), false))
        .thenReturn(Arrays.asList(nodeExecution4, nodeExecution5));

    EngineExpressionEvaluator engineExpressionEvaluator = prepareEngineExpressionEvaluator(newAmbiance);
    PmsSdkInstance pmsSdkInstance =
        PmsSdkInstance.builder().staticAliases(new PipelineServiceApplication().getStaticAliases()).build();
    doReturn(ImmutableMap.of("cd", pmsSdkInstance)).when(pmsSdkInstanceService).getSdkInstanceCacheValue();
    Object pipelineCurrentStatus = engineExpressionEvaluator.evaluateExpression("<+pipeline.currentStatus>");
    assertThat((String) pipelineCurrentStatus).isEqualTo("IGNORE_FAILED");
    Object stageCurrentStatus = engineExpressionEvaluator.evaluateExpression("<+pipeline.currentStatus>");
    assertThat((String) stageCurrentStatus).isEqualTo("IGNORE_FAILED");
    Object pipelineSuccess =
        engineExpressionEvaluator.evaluateExpression("<+" + OrchestrationConstants.PIPELINE_SUCCESS + ">");
    assertThat(pipelineSuccess).isInstanceOf(Boolean.class);
    assertThat((Boolean) pipelineSuccess).isEqualTo(true);

    Object stageSuccess =
        engineExpressionEvaluator.evaluateExpression("<+" + OrchestrationConstants.STAGE_SUCCESS + ">");
    assertThat(stageSuccess).isInstanceOf(Boolean.class);
    assertThat((Boolean) stageSuccess).isEqualTo(true);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testNodeExecutionLiveStatusWhenIgnoredFailure() {
    Ambiance newAmbiance = Ambiance.newBuilder()
                               .setPlanExecutionId(planExecutionId)
                               .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecution1.getUuid(), planNode1))
                               .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecution2.getUuid(), planNode2))
                               .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecution3.getUuid(), planNode3))
                               .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecution5.getUuid(), planNode5))
                               .build();

    Reflect.on(nodeExecution5).set("status", Status.IGNORE_FAILED);
    Reflect.on(nodeExecution4).set("status", Status.SUCCEEDED);

    // pipeline children
    when(nodeExecutionService.findAllChildrenWithStatusInAndWithoutOldRetries(
             planExecutionId, nodeExecution1.getUuid(), null, false, Collections.emptySet(), true))
        .thenReturn(Arrays.asList(nodeExecution4, nodeExecution5));

    EngineExpressionEvaluator engineExpressionEvaluator = prepareEngineExpressionEvaluator(newAmbiance);
    PmsSdkInstance pmsSdkInstance =
        PmsSdkInstance.builder().staticAliases(new PipelineServiceApplication().getStaticAliases()).build();
    doReturn(ImmutableMap.of("cd", pmsSdkInstance)).when(pmsSdkInstanceService).getSdkInstanceCacheValue();
    Object pipelineCurrentStatus = engineExpressionEvaluator.evaluateExpression("<+pipeline.liveStatus>");
    assertThat((String) pipelineCurrentStatus).isEqualTo("IGNORE_FAILED");
    Object stageCurrentStatus = engineExpressionEvaluator.evaluateExpression("<+pipeline.liveStatus>");
    assertThat((String) stageCurrentStatus).isEqualTo("IGNORE_FAILED");
    Object pipelineSuccess =
        engineExpressionEvaluator.evaluateExpression("<+" + OrchestrationConstants.PIPELINE_SUCCESS + ">");
    assertThat(pipelineSuccess).isInstanceOf(Boolean.class);
    assertThat((Boolean) pipelineSuccess).isEqualTo(true);

    Object stageSuccess =
        engineExpressionEvaluator.evaluateExpression("<+" + OrchestrationConstants.STAGE_SUCCESS + ">");
    assertThat(stageSuccess).isInstanceOf(Boolean.class);
    assertThat((Boolean) stageSuccess).isEqualTo(true);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testRemoteFunctor() {
    Ambiance newAmbiance = Ambiance.newBuilder()
                               .setPlanExecutionId(planExecutionId)
                               .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecution1.getUuid(), planNode1))
                               .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecution2.getUuid(), planNode2))
                               .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecution3.getUuid(), planNode3))
                               .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecution5.getUuid(), planNode5))
                               .build();
    EngineExpressionEvaluator engineExpressionEvaluator = prepareEngineExpressionEvaluator(newAmbiance);
    ExpressionResponse expressionResponse = ExpressionResponse.newBuilder().build();
    ArgumentCaptor<String> argumentCaptor = ArgumentCaptor.forClass(String.class);
    doReturn(expressionResponse).when(remoteExpressionFunctor).get(any());

    // testing that remoteFunctor is registered correctly
    assertTrue(engineExpressionEvaluator.evaluateExpression("<+dummy>") instanceof RemoteExpressionFunctor);

    // testing simple string argument
    assertEquals(engineExpressionEvaluator.evaluateExpression("<+dummy.abc>"), expressionResponse);
    verify(remoteExpressionFunctor, times(1)).get(argumentCaptor.capture());
    assertEquals(argumentCaptor.getValue(), "abc");
    assertEquals(engineExpressionEvaluator.evaluateExpression("<+dummy.get(\"arg1\")>"), expressionResponse);
    verify(remoteExpressionFunctor, times(2)).get(argumentCaptor.capture());
    assertEquals(argumentCaptor.getValue(), "arg1");

    // testing array of strings as argument
    assertEquals(engineExpressionEvaluator.evaluateExpression("<+dummy.get([\"arg1\",\"arg2\"])>"), expressionResponse);
    ArgumentCaptor<String[]> arrayArgumentCaptor = ArgumentCaptor.forClass(String[].class);
    verify(remoteExpressionFunctor, times(3)).get(arrayArgumentCaptor.capture());
    String[] argsArray = arrayArgumentCaptor.getValue();
    assertEquals(argsArray.length, 2);
    assertEquals(argsArray[0], "arg1");
    assertEquals(argsArray[1], "arg2");
  }

  private PlanNode preparePlanNode(
      boolean skipExpressionChain, String identifier, String paramValue, String groupName) {
    return PlanNode.builder()
        .uuid(generateUuid())
        .name(identifier)
        .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
        .identifier(identifier)
        .skipExpressionChain(skipExpressionChain)
        .stepParameters(PmsStepParameters.parse(RecastOrchestrationUtils.toJson(prepareStepParameters(paramValue))))
        .group(groupName)
        .build();
  }

  private Map<String, Object> prepareStepParameters(String paramValue) {
    return RecastOrchestrationUtils.toMap(TestStepParameters.builder().param(paramValue).build());
  }

  private EngineExpressionEvaluator prepareEngineExpressionEvaluator(Ambiance ambiance) {
    SampleEngineExpressionEvaluator evaluator = new SampleEngineExpressionEvaluator(ambiance, pmsSdkInstanceService);
    on(evaluator).set("planExecutionService", planExecutionService);
    on(evaluator).set("nodeExecutionService", nodeExecutionService);
    on(evaluator).set("planExpansionService", planExpansionService);

    evaluator.addToContextMap("dummy", remoteExpressionFunctor);

    return evaluator;
  }

  public static class SampleEngineExpressionEvaluator extends PMSExpressionEvaluator {
    public SampleEngineExpressionEvaluator(Ambiance ambiance, PmsSdkInstanceService pmsSdkInstanceService) {
      super(null, ambiance, null, false);
      this.pmsSdkInstanceService = pmsSdkInstanceService;
    }

    @Override
    protected void initialize() {
      super.initialize();
    }

    public void addToContextMap(String a, Object b) {
      super.addToContext(a, b);
    }

    @Override
    protected Object evaluateInternal(String expression, EngineJexlContext ctx) {
      Object value = super.evaluateInternal(expression, ctx);
      if (value instanceof DummyOrchestrationField) {
        return ((DummyOrchestrationField) value).fetchFinalValue();
      }
      return value;
    }
  }

  @Data
  @Builder
  @RecasterAlias("io.harness.pms.expressions.PMSExpressionEvaluatorTest$TestStepParameters")
  public static class TestStepParameters implements StepParameters {
    String param;
  }
}
