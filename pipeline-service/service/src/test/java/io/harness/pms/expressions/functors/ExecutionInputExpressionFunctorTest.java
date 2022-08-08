/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.expressions.functors;

import static io.harness.rule.OwnerRule.BRIJESH;

import static junit.framework.TestCase.assertEquals;
import static org.joor.Reflect.on;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.execution.ExecutionInputService;
import io.harness.execution.ExecutionInputInstance;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;
import io.harness.rule.Owner;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class ExecutionInputExpressionFunctorTest extends CategoryTest {
  @Mock private ExecutionInputService executionInputService;
  @InjectMocks private ExecutionInputExpressionFunctor inputExpressionFunctor;
  ObjectMapper objectMapper = new YAMLMapper();
  String template1 = "step:\n"
      + "  identifier: \"ss\"\n"
      + "  type: \"ShellScript\"\n"
      + "  spec:\n"
      + "    source:\n"
      + "      type: \"Inline\"\n"
      + "      spec:\n"
      + "        script: \"echo Hi\"\n";
  String template2 = "stage:\n"
      + "  execution:\n"
      + "    step:\n"
      + "      identifier: \"ss\"\n"
      + "      type: \"ShellScript\"\n"
      + "      spec:\n"
      + "        source:\n"
      + "          type: \"Inline\"\n"
      + "          spec:\n"
      + "            script: \"echo Hello\"";
  Set<String> nodeExecutionIds = new HashSet<String>() {
    {
      add("nodeExecutionId1");
      add("nodeExecutionId2");
    }
  };
  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testBind() throws JsonProcessingException {
    Ambiance.Builder builder = Ambiance.newBuilder();
    for (String nodeExecutionId : nodeExecutionIds) {
      builder.addLevels(Level.newBuilder().setRuntimeId(nodeExecutionId).build());
    }
    Ambiance ambiance = builder.build();
    on(inputExpressionFunctor).set("ambiance", ambiance);

    Map<String, Object> template1Map = RecastOrchestrationUtils.fromJson(objectMapper.readTree(template1).toString());
    Map<String, Object> template2Map = RecastOrchestrationUtils.fromJson(objectMapper.readTree(template2).toString());
    doReturn(Arrays.asList(ExecutionInputInstance.builder().mergedInputTemplate(template1Map).build(),
                 ExecutionInputInstance.builder().mergedInputTemplate(template2Map).build()))
        .when(executionInputService)
        .getExecutionInputInstances(nodeExecutionIds);

    Map<String, Object> responseMap = (Map<String, Object>) inputExpressionFunctor.bind();

    template1Map.putAll(template2Map);
    assertEquals(responseMap, template1Map);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testExpression() throws JsonProcessingException {
    Ambiance.Builder builder = Ambiance.newBuilder();
    for (String nodeExecutionId : nodeExecutionIds) {
      builder.addLevels(Level.newBuilder().setRuntimeId(nodeExecutionId).build());
    }
    Ambiance ambiance = builder.build();
    on(inputExpressionFunctor).set("ambiance", ambiance);

    Map<String, Object> template1Map = RecastOrchestrationUtils.fromJson(objectMapper.readTree(template1).toString());
    Map<String, Object> template2Map = RecastOrchestrationUtils.fromJson(objectMapper.readTree(template2).toString());
    doReturn(Arrays.asList(ExecutionInputInstance.builder().mergedInputTemplate(template1Map).build(),
                 ExecutionInputInstance.builder().mergedInputTemplate(template2Map).build()))
        .when(executionInputService)
        .getExecutionInputInstances(nodeExecutionIds);

    SampleEvaluator evaluator = new SampleEvaluator(inputExpressionFunctor);
    assertEquals(evaluator.evaluateExpression("<+expressionInput.step.spec.source.spec.script>"), "echo Hi");
    assertEquals(
        evaluator.evaluateExpression("<+expressionInput.stage.execution.step.spec.source.spec.script>"), "echo Hello");
  }

  private static class SampleEvaluator extends EngineExpressionEvaluator {
    ExecutionInputExpressionFunctor executionInputExpressionFunctor;

    SampleEvaluator(ExecutionInputExpressionFunctor executionInputExpressionFunctor) {
      super(null);
      this.executionInputExpressionFunctor = executionInputExpressionFunctor;
    }

    @Override
    protected void initialize() {
      addToContext("expressionInput", executionInputExpressionFunctor);
      super.initialize();
    }
  }
}
