/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.ngpipeline.inputs.api;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import io.harness.PipelineServiceTestBase;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.ngpipeline.inputs.service.PMSInputsServiceImpl;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.service.PMSPipelineService;
import io.harness.pms.yaml.PipelineVersion;
import io.harness.rule.Owner;
import io.harness.spec.server.pipeline.v1.model.InputsResponseBody;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import javax.ws.rs.core.Response;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(PIPELINE)
public class InputsApiImplTest extends PipelineServiceTestBase {
  InputsApiImpl inputsApiImpl;
  @Inject PMSInputsServiceImpl pmsInputsService;
  @Inject ObjectMapper objectMapper;
  @Mock PMSPipelineService pipelineService;
  private static final String ACCOUNT_ID = "accountId";
  private static final String ORG_IDENTIFIER = "orgId";
  private static final String PROJ_IDENTIFIER = "projId";
  private static final String PIPELINE_IDENTIFIER = "pipeId";
  String pipelineYaml;
  PipelineEntity pipelineEntity;

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
    MockitoAnnotations.initMocks(this);
    inputsApiImpl = new InputsApiImpl(pmsInputsService, pipelineService);
    String pipelineYamlFileName = "pipeline-v1.yaml";
    pipelineYaml = readFile(pipelineYamlFileName);
    pipelineEntity = PipelineEntity.builder()
                         .accountId(ACCOUNT_ID)
                         .orgIdentifier(ORG_IDENTIFIER)
                         .projectIdentifier(PROJ_IDENTIFIER)
                         .identifier(PIPELINE_IDENTIFIER)
                         .yaml(pipelineYaml)
                         .harnessVersion(PipelineVersion.V1)
                         .version(1L)
                         .build();
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetInputs() throws JsonProcessingException {
    doReturn(Optional.of(pipelineEntity))
        .when(pipelineService)
        .getPipeline(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, false, false);
    Response response = inputsApiImpl.getPipelineInputs(
        ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, ACCOUNT_ID, null, null, null);
    String expectedResponse = readFile("get-inputs-expected-response.json");
    assertThat(response).isNotNull();
    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.OK);
    assertThat(response.getEntity()).isNotNull();
    InputsResponseBody responseBody = (InputsResponseBody) response.getEntity();
    assertThat(objectMapper.readTree(objectMapper.writeValueAsString(responseBody)))
        .isEqualTo(objectMapper.readTree(expectedResponse));
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetInputsWithCloneDisabled() throws JsonProcessingException {
    String yaml = readFile("pipeline-v1-disabled-clone.yaml");
    pipelineEntity.setYaml(yaml);
    doReturn(Optional.of(pipelineEntity))
        .when(pipelineService)
        .getPipeline(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, false, false);
    Response response = inputsApiImpl.getPipelineInputs(
        ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, ACCOUNT_ID, null, null, null);
    String expectedResponse = readFile("get-inputs-clone-disabled-expected-response.json");
    assertThat(response).isNotNull();
    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.OK);
    assertThat(response.getEntity()).isNotNull();
    InputsResponseBody responseBody = (InputsResponseBody) response.getEntity();
    assertThat(objectMapper.readTree(objectMapper.writeValueAsString(responseBody)))
        .isEqualTo(objectMapper.readTree(expectedResponse));
  }
}
