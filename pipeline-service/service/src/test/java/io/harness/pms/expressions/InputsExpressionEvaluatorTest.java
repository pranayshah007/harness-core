/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.expressions;

import static io.harness.rule.OwnerRule.BRIJESH;

import static junit.framework.TestCase.assertEquals;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.expression.common.ExpressionMode;
import io.harness.pms.expressions.functors.InputsFunctor;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class InputsExpressionEvaluatorTest extends CategoryTest {
  @InjectMocks private InputsFunctor inputsFunctor;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testBind() throws IOException {
    String inputsYaml =
        "{\"repo\":\"harness-core\",\"image\":\"nginx\",\"password\":\"dockerPass\",\"count\":5,\"version\":\"V1\"}";

    String pipelineYaml = readFile();

    EngineExpressionEvaluator evaluator = new InputsExpressionEvaluator(inputsYaml, pipelineYaml);

    String resolvedPipelineYaml =
        (String) evaluator.resolve(pipelineYaml, ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED);
    YamlField stagesField = YamlUtils.readTree(resolvedPipelineYaml).getNode().getField("stages");

    JsonNode specNode = stagesField.getNode().getCurrJsonNode().get(0).get("steps").get(0).get("spec");

    assertEquals(specNode.get("image").asText(), "nginx");
    assertEquals(specNode.get("settings").get("repo").asText(), "harness-core");
    assertEquals(specNode.get("settings").get("password").asText(), "<+secrets.getValue(\"dockerPass\")>");
  }

  private String readFile() {
    ClassLoader classLoader = getClass().getClassLoader();
    try {
      return Resources.toString(
          Objects.requireNonNull(classLoader.getResource("simplified-yaml-v1-pipeline-with-inputs.yaml")),
          StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new InvalidRequestException("Could not read resource file: simplified-yaml-v1-pipeline-with-inputs.yaml");
    }
  }
}
