/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.merger.helpers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.SANDESH_SALUNKHE;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class InputSetTemplateHelperTest extends CategoryTest {
  private String readFile(String filename) {
    ClassLoader classLoader = getClass().getClassLoader();
    try {
      return Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new InvalidRequestException("Could not read resource file: " + filename);
    }
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testCreateTemplateFromPipelineForGivenStagesStage1() {
    String filename = "inputSet-default-variables-selective-stage1-exec-test.yaml";
    String pipelineYaml = readFile(filename);
    List<String> stageIdentifiers = Collections.singletonList("stg1");
    String result = InputSetTemplateHelper.createTemplateFromWithDefaultValuesPipelineForGivenStages(
        pipelineYaml, stageIdentifiers);
    String expectedYaml = "pipeline:\n"
        + "  identifier: test_1\n"
        + "  variables:\n"
        + "    - name: stack_id\n"
        + "      type: String\n"
        + "      value: <+input>\n"
        + "    - name: harness_org_id\n"
        + "      type: String\n"
        + "      default: Applications\n"
        + "      value: \"<+input>.allowedValues(Operations,Applications)\"\n"
        + "    - name: log_level\n"
        + "      type: String\n"
        + "      default: \"2\"\n"
        + "      value: \"<+input>.allowedValues(0,1,2,3)\"\n"
        + "    - name: aws_resources\n"
        + "      type: String\n"
        + "      value: \"<+input>.allowedValues(true,false)\"\n";
    assertThat(result).isEqualTo(expectedYaml);
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testCreateTemplateFromPipelineForGivenStagesStage2() {
    String filename = "inputSet-default-variables-selective-stage2-exec-test.yaml";
    String pipelineYaml = readFile(filename);
    List<String> stageIdentifiers = Collections.singletonList("stg2");
    String result = InputSetTemplateHelper.createTemplateFromWithDefaultValuesPipelineForGivenStages(
        pipelineYaml, stageIdentifiers);
    String expectedYaml = "pipeline:\n"
        + "  identifier: test_1\n"
        + "  variables:\n"
        + "    - name: log_level\n"
        + "      type: String\n"
        + "      default: \"2\"\n"
        + "      value: \"<+input>.allowedValues(0,1,2,3)\"\n"
        + "    - name: aws_resources\n"
        + "      type: String\n"
        + "      value: \"<+input>.allowedValues(true,false)\"\n";
    assertThat(result).isEqualTo(expectedYaml);
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testCreateTemplateFromPipelineForGivenStagesNullTemplate() {
    String pipelineYaml = "";
    List<String> stageIdentifiers = Collections.singletonList("stg2");
    mockStatic(RuntimeInputFormHelper.class);
    String result = InputSetTemplateHelper.createTemplateFromWithDefaultValuesPipelineForGivenStages(
        pipelineYaml, stageIdentifiers);
    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testRemoveRuntimeInputFromYaml() {
    String filename = "inputset-with-execution-input.yaml";
    String yaml = readFile(filename);
    String pipelineYaml = readFile("pipeline-with-execution-input.yaml");
    String templateYaml = InputSetTemplateHelper.removeRuntimeInputFromYaml(pipelineYaml, yaml);

    String resFile = "inputset-after-removing-execution-input.yaml";
    String resTemplate = readFile(resFile);
    assertThat(templateYaml).isEqualTo(resTemplate);
  }
}
