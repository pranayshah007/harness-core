/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.yaml;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.BRIJESH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.yaml.preprocess.YamlV1PreProcessor;
import io.harness.rule.Owner;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class YamlV1PreprocessorTest extends CategoryTest {
  YamlV1PreProcessor preprocessor = new YamlV1PreProcessor();
  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testPreprocess() {
    String yaml = readFile();
    String processedYaml = YamlUtils.writeYamlString(preprocessor.preProcess(yaml).getPreprocessedJsonNode());
    JsonNode jsonNode = YamlUtils.readAsJsonNode(processedYaml);
    assertThat(processedYaml).isNotEqualTo(yaml);

    assertThat(jsonNode.get(YAMLFieldNameConstants.SPEC)
                   .get(YAMLFieldNameConstants.STAGES)
                   .get(0)
                   .get(YAMLFieldNameConstants.ID)
                   .asText())
        .isEqualTo("stage1_1");
    assertThat(jsonNode.get(YAMLFieldNameConstants.SPEC)
                   .get(YAMLFieldNameConstants.STAGES)
                   .get(0)
                   .get(YAMLFieldNameConstants.SPEC)
                   .get(YAMLFieldNameConstants.STEPS)
                   .get(0)
                   .get(YAMLFieldNameConstants.ID)
                   .asText())
        .isEqualTo("Http_1");
    assertThat(jsonNode.get(YAMLFieldNameConstants.SPEC)
                   .get(YAMLFieldNameConstants.STAGES)
                   .get(0)
                   .get(YAMLFieldNameConstants.SPEC)
                   .get(YAMLFieldNameConstants.STEPS)
                   .get(1)
                   .get(YAMLFieldNameConstants.ID)
                   .asText())
        .isEqualTo("Http_3");

    assertThat(jsonNode.get(YAMLFieldNameConstants.SPEC)
                   .get(YAMLFieldNameConstants.STAGES)
                   .get(1)
                   .get(YAMLFieldNameConstants.ID)
                   .asText())
        .isEqualTo("custom_1");
    assertThat(jsonNode.get(YAMLFieldNameConstants.SPEC)
                   .get(YAMLFieldNameConstants.STAGES)
                   .get(1)
                   .get(YAMLFieldNameConstants.SPEC)
                   .get(YAMLFieldNameConstants.STEPS)
                   .get(0)
                   .get(YAMLFieldNameConstants.ID)
                   .asText())
        .isEqualTo("Http_2");
  }

  private String readFile() {
    ClassLoader classLoader = getClass().getClassLoader();
    try {
      return Resources.toString(
          Objects.requireNonNull(classLoader.getResource("pipeline-v1-with-ids.yaml")), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new InvalidRequestException("Could not read resource file: "
          + "pipeline-v1-with-ids.yaml");
    }
  }
}
