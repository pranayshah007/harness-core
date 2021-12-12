package io.harness.pms.expressions;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
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
import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.engine.expressions.OrchestrationConstants;
import io.harness.engine.pms.data.PmsOutcomeService;
import io.harness.execution.NodeExecution;
import io.harness.execution.PlanExecution;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.expression.EngineJexlContext;
import io.harness.expression.field.dummy.DummyOrchestrationField;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.expression.ExpressionResponse;
import io.harness.pms.contracts.plan.PlanNodeProto;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.expressions.functors.RemoteExpressionFunctor;
import io.harness.pms.sdk.PmsSdkInstance;
import io.harness.pms.sdk.PmsSdkInstanceService;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import org.joor.Reflect;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.PIPELINE)
public class PMSExpressionEvaluatorTest extends PipelineServiceTestBase {
  @Mock private PlanExecutionService planExecutionService;
  @Mock NodeExecutionService nodeExecutionService;
  @Mock PmsOutcomeService pmsOutcomeService;
  @Mock PmsSdkInstanceService pmsSdkInstanceService;
  @Mock RemoteExpressionFunctor remoteExpressionFunctor;

  private Ambiance ambiance;
  NodeExecution nodeExecution1;
  NodeExecution nodeExecution2;
  NodeExecution nodeExecution3;
  NodeExecution nodeExecution4;
  NodeExecution nodeExecution5;

  @Before
  public void setup() {
    String nodeExecution1Id = generateUuid();
    String nodeExecution2Id = generateUuid();
    String nodeExecution3Id = generateUuid();
    String nodeExecution4Id = generateUuid();
    String nodeExecution5Id = generateUuid();

    nodeExecution1 = NodeExecution.builder()
                         .uuid(nodeExecution1Id)
                         .node(preparePlanNode(false, "pipeline", "pipelineValue", "PIPELINE"))
                         .resolvedStepParameters(prepareStepParameters("pipelineResolvedValue"))
                         .build();
    nodeExecution2 = NodeExecution.builder()
                         .uuid(nodeExecution2Id)
                         .node(preparePlanNode(false, "stages", "stagesValue", null))
                         .resolvedStepParameters(prepareStepParameters("stagesResolvedValue"))
                         .parentId(nodeExecution1Id)
                         .build();
    nodeExecution3 = NodeExecution.builder()
                         .uuid(nodeExecution3Id)
                         .node(preparePlanNode(false, "stage", "stageValue", "STAGE"))
                         .resolvedStepParameters(prepareStepParameters("stageResolvedValue"))
                         .parentId(nodeExecution2Id)
                         .build();
    nodeExecution4 = NodeExecution.builder()
                         .uuid(nodeExecution4Id)
                         .node(preparePlanNode(false, "d", "di1", null))
                         .resolvedStepParameters(prepareStepParameters("dResolvedValue"))
                         .parentId(nodeExecution3Id)
                         .nextId(nodeExecution5Id)
                         .build();

    nodeExecution5 = NodeExecution.builder()
                         .uuid(nodeExecution4Id)
                         .node(preparePlanNode(false, "e", "ei1", null))
                         .resolvedStepParameters(prepareStepParameters("eResolvedValue"))
                         .previousId(nodeExecution4Id)
                         .parentId(nodeExecution3Id)
                         .build();

    Level pipelineLevel = Level.newBuilder().setRuntimeId(nodeExecution1Id).build();
    Level stagesLevel = Level.newBuilder().setRuntimeId(nodeExecution2Id).build();
    Level stageLevel = Level.newBuilder().setRuntimeId(nodeExecution3Id).build();
    List<Level> levels = new ArrayList<>();
    levels.add(pipelineLevel);
    levels.add(stagesLevel);
    levels.add(stageLevel);
    ambiance = Ambiance.newBuilder()
                   .setPlanExecutionId(generateUuid())
                   .putAllSetupAbstractions(ImmutableMap.of("accountId", generateUuid(), "appId", generateUuid()))
                   .setExpressionFunctorToken(1234)
                   .build();

    when(nodeExecutionService.get(nodeExecution1.getUuid())).thenReturn(nodeExecution1);
    when(nodeExecutionService.get(nodeExecution2.getUuid())).thenReturn(nodeExecution2);
    when(nodeExecutionService.get(nodeExecution3.getUuid())).thenReturn(nodeExecution3);
    when(nodeExecutionService.get(nodeExecution4.getUuid())).thenReturn(nodeExecution4);
    when(nodeExecutionService.get(nodeExecution5.getUuid())).thenReturn(nodeExecution5);

    String planExecutionId = ambiance.getPlanExecutionId();
    when(nodeExecutionService.fetchChildrenNodeExecutions(planExecutionId, null))
        .thenReturn(Collections.singletonList(nodeExecution1));
    when(nodeExecutionService.fetchChildrenNodeExecutions(planExecutionId, nodeExecution1.getUuid()))
        .thenReturn(Collections.singletonList(nodeExecution2));
    when(nodeExecutionService.fetchChildrenNodeExecutions(planExecutionId, nodeExecution2.getUuid()))
        .thenReturn(Collections.singletonList(nodeExecution3));
    when(nodeExecutionService.fetchChildrenNodeExecutions(planExecutionId, nodeExecution3.getUuid()))
        .thenReturn(asList(nodeExecution4, nodeExecution5));

    when(planExecutionService.get(planExecutionId)).thenReturn(PlanExecution.builder().build());

    // pipeline children
    when(nodeExecutionService.findAllChildren(ambiance.getPlanExecutionId(), nodeExecution1.getUuid(), false))
        .thenReturn(Arrays.asList(nodeExecution2, nodeExecution3, nodeExecution4, nodeExecution5));

    // stage children
    when(nodeExecutionService.findAllChildren(ambiance.getPlanExecutionId(), nodeExecution3.getUuid(), false))
        .thenReturn(Arrays.asList(nodeExecution4, nodeExecution5));
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testNodeExecutionCurrentStatusWhenIgnoredFailure() {
    Ambiance newAmbiance =
        AmbianceUtils.cloneForChild(ambiance, Level.newBuilder().setRuntimeId(nodeExecution5.getUuid()).build());

    Reflect.on(nodeExecution5).set("status", Status.IGNORE_FAILED);
    Reflect.on(nodeExecution4).set("status", Status.SUCCEEDED);

    EngineExpressionEvaluator engineExpressionEvaluator = prepareEngineExpressionEvaluator(newAmbiance);
    PmsSdkInstance pmsSdkInstance =
        PmsSdkInstance.builder().staticAliases(new PipelineServiceApplication().getStaticAliases()).build();
    doReturn(ImmutableMap.of("cd", pmsSdkInstance)).when(pmsSdkInstanceService).getSdkInstanceCacheValue();
    Object pipelineSuccess =
        engineExpressionEvaluator.evaluateExpression("<+" + OrchestrationConstants.PIPELINE_SUCCESS + ">");
    assertThat(pipelineSuccess).isInstanceOf(Boolean.class);
    assertThat((Boolean) pipelineSuccess).isEqualTo(true);
    Object pipelineCurrentStatus = engineExpressionEvaluator.evaluateExpression("<+pipeline.currentStatus>");
    assertThat((String) pipelineCurrentStatus).isEqualTo("IGNORE_FAILED");
    Object stageCurrentStatus = engineExpressionEvaluator.evaluateExpression("<+pipeline.currentStatus>");
    assertThat((String) stageCurrentStatus).isEqualTo("IGNORE_FAILED");

    Object stageSuccess =
        engineExpressionEvaluator.evaluateExpression("<+" + OrchestrationConstants.STAGE_SUCCESS + ">");
    assertThat(stageSuccess).isInstanceOf(Boolean.class);
    assertThat((Boolean) stageSuccess).isEqualTo(true);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testRemoteFunctor() {
    Ambiance newAmbiance =
        AmbianceUtils.cloneForChild(ambiance, Level.newBuilder().setRuntimeId(nodeExecution5.getUuid()).build());
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

  private PlanNodeProto preparePlanNode(
      boolean skipExpressionChain, String identifier, String paramValue, String groupName) {
    PlanNodeProto.Builder builder =
        PlanNodeProto.newBuilder()
            .setUuid(generateUuid())
            .setName(identifier)
            .setStepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .setIdentifier(identifier)
            .setSkipExpressionChain(skipExpressionChain)
            .setStepParameters(RecastOrchestrationUtils.toJson(prepareStepParameters(paramValue)));

    if (!isEmpty(groupName)) {
      builder.setGroup(groupName);
    }
    return builder.build();
  }

  private StepParameters prepareStepParameters(String paramValue) {
    return TestStepParameters.builder().param(paramValue).build();
  }

  private EngineExpressionEvaluator prepareEngineExpressionEvaluator(Ambiance ambiance) {
    SampleEngineExpressionEvaluator evaluator = new SampleEngineExpressionEvaluator(ambiance, pmsSdkInstanceService);
    on(evaluator).set("planExecutionService", planExecutionService);
    on(evaluator).set("nodeExecutionService", nodeExecutionService);

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