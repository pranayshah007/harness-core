/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.merger.helpers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.HINGER;
import static io.harness.rule.OwnerRule.NAMAN;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class RuntimeInputFormHelperTest extends CategoryTest {
  private String readFile(String filename) {
    ClassLoader classLoader = getClass().getClassLoader();
    try {
      return Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new InvalidRequestException("Could not read resource file: " + filename);
    }
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testCreateTemplateFromPipeline() {
    String filename = "pipeline-extensive.yml";
    String yaml = readFile(filename);
    String templateYaml = RuntimeInputFormHelper.createRuntimeInputForm(yaml, true);

    String resFile = "pipeline-extensive-template.yml";
    String resTemplate = readFile(resFile);
    assertThat(templateYaml).isEqualTo(resTemplate);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testCreateExecutionInputFormAndUpdateYamlField() throws JsonProcessingException {
    String filename = "execution-input-pipeline.yaml";
    String inputTemplateFileName = "execution-input-pipeline-template.yaml";
    String expectedTemplateYaml = readFile(inputTemplateFileName);
    String yaml = readFile(filename);
    ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    JsonNode jsonNode = mapper.readTree(yaml);
    String templateYaml = RuntimeInputFormHelper.createExecutionInputFormAndUpdateYamlField(jsonNode);
    assertNotNull(templateYaml);
    assertEquals(templateYaml, expectedTemplateYaml);
    assertFalse(jsonNode.toString().contains("<+input>.executionInput()()"));
    assertTrue(yaml.contains("<+input>.executionInput()"));
    assertEquals(jsonNode.get("pipeline")
                     .get("stages")
                     .get(0)
                     .get("stage")
                     .get("spec")
                     .get("execution")
                     .get("steps")
                     .get(0)
                     .get("step")
                     .get("type")
                     .asText(),
        "<+executionInput.pipeline.stages.sd.spec.execution.steps.ss.type>");
    assertEquals(jsonNode.get("pipeline")
                     .get("stages")
                     .get(0)
                     .get("stage")
                     .get("spec")
                     .get("execution")
                     .get("steps")
                     .get(0)
                     .get("step")
                     .get("spec")
                     .get("source")
                     .get("spec")
                     .get("script")
                     .asText(),
        "<+executionInput.pipeline.stages.sd.spec.execution.steps.ss.spec.source.spec.script>");
    assertEquals(jsonNode.get("pipeline").get("stages").get(0).get("stage").get("description").asText(),
        "<+executionInput.pipeline.stages.sd.description>");
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testCreateExecutionInputFormAndUpdateYamlFieldForStage() throws JsonProcessingException {
    String filename = "execution-input-pipeline.yaml";
    String expectedTemplateYaml = "stage:\n"
        + "  identifier: \"sd\"\n"
        + "  type: \"Approval\"\n"
        + "  description: \"<+input>.executionInput()\"\n";
    String yaml = readFile(filename);
    ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    JsonNode jsonNode = mapper.readTree(yaml);
    String templateYaml = RuntimeInputFormHelper.createExecutionInputFormAndUpdateYamlFieldForStage(
        jsonNode.get("pipeline").get("stages").get(0));
    assertNotNull(templateYaml);
    assertEquals(templateYaml, expectedTemplateYaml);
    assertFalse(jsonNode.toString().contains("<+input>.executionInput()()"));
    assertTrue(yaml.contains("<+input>.executionInput()"));
    assertEquals(jsonNode.get("pipeline")
                     .get("stages")
                     .get(0)
                     .get("stage")
                     .get("spec")
                     .get("execution")
                     .get("steps")
                     .get(0)
                     .get("step")
                     .get("type")
                     .asText(),
        "<+input>.executionInput()");
    assertEquals(jsonNode.get("pipeline")
                     .get("stages")
                     .get(0)
                     .get("stage")
                     .get("spec")
                     .get("execution")
                     .get("steps")
                     .get(0)
                     .get("step")
                     .get("spec")
                     .get("source")
                     .get("spec")
                     .get("script")
                     .asText(),
        "<+input>.executionInput()");
    assertEquals(jsonNode.get("pipeline").get("stages").get(0).get("stage").get("description").asText(),
        "<+executionInput.stage.description>");
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testJsonNodeMergeDoesNotChangeOrderInList() throws JsonProcessingException {
    String filename = "jsonNodeWithList.yaml";
    String yaml = readFile(filename);
    ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    JsonNode jsonNode = mapper.readTree(yaml);

    RuntimeInputFormHelper.createExecutionInputFormAndUpdateYamlFieldForStage(jsonNode);
    assertThat(jsonNode).isEqualTo(mapper.readTree(yaml));
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testCreateTemplateFromPipelineTemplateWithDefaults() {
    String filename = "pipeline-template-defaults.yml";
    String yaml = readFile(filename);
    String templateYaml = RuntimeInputFormHelper.createRuntimeInputFormWithDefaultValues(yaml);

    String resFile = "pipeline-template-defaults-runtime.yml";
    String resTemplate = readFile(resFile);
    assertThat(templateYaml).isEqualTo(resTemplate);
  }
}
