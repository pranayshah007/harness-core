/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.ngpipeline.inputs.service;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.PipelineServiceTestBase;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.ngpipeline.inputs.beans.entity.InputEntity;
import io.harness.pms.ngpipeline.inputs.beans.entity.OptionsInput;
import io.harness.rule.Owner;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class PMSInputsServiceImplTest extends PipelineServiceTestBase {
  @Inject PMSInputsServiceImpl pmsInputsService;
  @Inject ObjectMapper objectMapper;
  String pipelineYaml;

  private String readFile(String filename) {
    ClassLoader classLoader = this.getClass().getClassLoader();
    try {
      return Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new InvalidRequestException("Could not read file " + filename, e);
    }
  }

  @Before
  public void setUp() throws IOException {
    String pipelineYamlFileName = "pipeline-v1.yaml";
    pipelineYaml = readFile(pipelineYamlFileName);
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetInputs() throws JsonProcessingException {
    Optional<Map<String, InputEntity>> optionalInputEntityMap = pmsInputsService.get(pipelineYaml);
    assertThat(optionalInputEntityMap.isEmpty()).isFalse();
    Map<String, InputEntity> inputEntityMap = optionalInputEntityMap.get();
    String expectedResponse = readFile("get-inputs-expected-response.json");
    JsonNode jsonNode = objectMapper.readTree(expectedResponse);
    assertThat(objectMapper.readTree(objectMapper.writeValueAsString(inputEntityMap)))
        .isEqualTo(jsonNode.get("inputs"));
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetOptionsInput() throws JsonProcessingException {
    Optional<OptionsInput> optionalOptionsInput = pmsInputsService.getOptions(pipelineYaml);
    assertThat(optionalOptionsInput.isEmpty()).isFalse();
    OptionsInput optionsInput = optionalOptionsInput.get();
    assertThat(optionsInput.getClone()).isNotNull();
    String expectedResponse = readFile("get-inputs-expected-response.json");
    JsonNode jsonNode = objectMapper.readTree(expectedResponse);
    assertThat(objectMapper.readTree(objectMapper.writeValueAsString(optionsInput))).isEqualTo(jsonNode.get("options"));
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetOptionsInputIfCloneDisabled() {
    String yaml = readFile("pipeline-v1-disabled-clone.yaml");
    Optional<OptionsInput> optionalOptionsInput = pmsInputsService.getOptions(yaml);
    assertThat(optionalOptionsInput.isEmpty()).isTrue();
  }
}
