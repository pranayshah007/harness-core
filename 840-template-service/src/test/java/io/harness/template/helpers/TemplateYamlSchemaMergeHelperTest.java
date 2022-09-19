/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.helpers;

import static io.harness.rule.OwnerRule.ABHINAV_MITTAL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.EntityType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.template.TemplateEntityType;
import io.harness.rule.Owner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDC)
public class TemplateYamlSchemaMergeHelperTest {
  @Inject TemplateYamlSchemaMergeHelper templateYamlSchemaMergeHelper;

  private JsonNode readJsonFile(String filename) {
    ClassLoader classLoader = getClass().getClassLoader();
    ObjectMapper objectMapper = new ObjectMapper();
    try {
      return objectMapper.readTree(
          Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new InvalidRequestException("Could not read resource file: " + filename);
    }
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void mergePipelineTemplateYamlSchema() {
    JsonNode templateSchema = readJsonFile("template-schema.json");
    JsonNode pipelineSchema = readJsonFile("pipeline-schema.json");
    JsonNode pipelineTemplateSchema = readJsonFile("pipeline-template-schema.json");
    templateYamlSchemaMergeHelper.mergeYamlSchema(
        templateSchema, pipelineSchema, EntityType.PIPELINES, TemplateEntityType.PIPELINE_TEMPLATE);
    assertThat(templateSchema).isEqualTo(pipelineTemplateSchema);
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void mergeStepTemplateYamlSchema() {
    JsonNode templateSchema = readJsonFile("template-schema.json");
    JsonNode stepSchema = readJsonFile("step-schema.json");
    JsonNode stepTemplateSchema = readJsonFile("step-template-schema.json");
    templateYamlSchemaMergeHelper.mergeYamlSchema(
        templateSchema, stepSchema, EntityType.HTTP_STEP, TemplateEntityType.STEP_TEMPLATE);
    assertThat(templateSchema).isEqualTo(stepTemplateSchema);
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void mergeStageTemplateYamlSchema() {
    JsonNode templateSchema = readJsonFile("template-schema.json");
    JsonNode stageSchema = readJsonFile("stage-schema.json");
    JsonNode stageTemplateSchema = readJsonFile("stage-template-schema.json");
    templateYamlSchemaMergeHelper.mergeYamlSchema(
        templateSchema, stageSchema, EntityType.HTTP_STEP, TemplateEntityType.STEP_TEMPLATE);
    assertThat(templateSchema).isEqualTo(stageTemplateSchema);
  }
}
