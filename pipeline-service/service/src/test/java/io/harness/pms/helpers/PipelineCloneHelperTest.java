/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.helpers;

import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;
import static io.harness.rule.OwnerRule.SOUMYAJIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.pipeline.ClonePipelineDTO;
import io.harness.pms.pipeline.DestinationPipelineConfig;
import io.harness.pms.pipeline.SourceIdentifierConfig;
import io.harness.pms.rbac.PipelineRbacPermissions;
import io.harness.rule.Owner;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class PipelineCloneHelperTest {
  @Mock AccessControlClient accessControlClient;
  @InjectMocks PipelineCloneHelper pipelineCloneHelper;
  ClonePipelineDTO clonePipelineDTO = null;
  private final String accountId = RandomStringUtils.randomAlphanumeric(6);
  private final String SOURCE_ORG_IDENTIFIER = "orgId_s";
  private final String SOURCE_PROJ_IDENTIFIER = "projId_s";
  private final String SOURCE_PIPELINE_IDENTIFIER = "myPipeline_s";
  private final String DEST_ORG_IDENTIFIER = "orgId_d";
  private final String DEST_PROJ_IDENTIFIER = "projId_d";
  private final String DEST_PIPELINE_IDENTIFIER = "myPipeline_d";
  private final String DEST_PIPELINE_DESCRIPTION = "test description_d";
  private String SOURCE_PIPELINE_YAML;
  private String SOURCE_PIPELINE_YAML_V1;
  private String SOURCE_PIPELINE_YAML_WITHOUT_NAME_V1;

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);

    SourceIdentifierConfig sourceIdentifierConfig = SourceIdentifierConfig.builder()
                                                        .orgIdentifier(SOURCE_ORG_IDENTIFIER)
                                                        .projectIdentifier(SOURCE_PROJ_IDENTIFIER)
                                                        .pipelineIdentifier(SOURCE_PIPELINE_IDENTIFIER)
                                                        .build();
    DestinationPipelineConfig destinationPipelineConfig = DestinationPipelineConfig.builder()
                                                              .pipelineIdentifier(DEST_PIPELINE_IDENTIFIER)
                                                              .orgIdentifier(DEST_ORG_IDENTIFIER)
                                                              .pipelineName(DEST_PIPELINE_IDENTIFIER)
                                                              .projectIdentifier(DEST_PROJ_IDENTIFIER)
                                                              .description(DEST_PIPELINE_DESCRIPTION)
                                                              .build();
    clonePipelineDTO = ClonePipelineDTO.builder()
                           .sourceConfig(sourceIdentifierConfig)
                           .destinationConfig(destinationPipelineConfig)
                           .build();

    ClassLoader classLoader = this.getClass().getClassLoader();
    String pipeline_yaml_filename = "clonePipelineInput.yaml";
    SOURCE_PIPELINE_YAML = Resources.toString(
        Objects.requireNonNull(classLoader.getResource(pipeline_yaml_filename)), StandardCharsets.UTF_8);
    SOURCE_PIPELINE_YAML_V1 =
        Resources.toString(Objects.requireNonNull(classLoader.getResource("pipeline-v1.yaml")), StandardCharsets.UTF_8);

    SOURCE_PIPELINE_YAML_WITHOUT_NAME_V1 = Resources.toString(
        Objects.requireNonNull(classLoader.getResource("pipeline-without-name-v1.yaml")), StandardCharsets.UTF_8);
  }

  @Test
  @Owner(developers = SOUMYAJIT)
  @Category(UnitTests.class)
  public void testUpdateSourceYaml() throws IOException {
    String updatedYaml =
        pipelineCloneHelper.updatePipelineMetadataInSourceYaml(clonePipelineDTO, SOURCE_PIPELINE_YAML, accountId);

    ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());

    JsonNode jsonNode = null;
    try {
      jsonNode = objectMapper.readTree(updatedYaml);
    } catch (JsonProcessingException e) {
      log.error(String.format("Error while processing source yaml to json for pipeline [%s]",
                    clonePipelineDTO.getSourceConfig().getPipelineIdentifier()),
          e);
    }

    assertThat(updatedYaml).isNotEqualTo(null);
    assertThat(jsonNode.get("pipeline").get("identifier").asText()).isEqualTo(DEST_PIPELINE_IDENTIFIER);
    assertThat(jsonNode.get("pipeline").get("name").asText()).isEqualTo(DEST_PIPELINE_IDENTIFIER);
    assertThat(jsonNode.get("pipeline").get("description").asText()).isEqualTo(DEST_PIPELINE_DESCRIPTION);
    assertThat(jsonNode.get("pipeline").get("projectIdentifier").asText()).isEqualTo(DEST_PROJ_IDENTIFIER);
    assertThat(jsonNode.get("pipeline").get("orgIdentifier").asText()).isEqualTo(DEST_ORG_IDENTIFIER);
    assertThat(jsonNode.get("pipeline").get("tags")).isNotEqualTo(null);
  }

  @Test
  @Owner(developers = SOUMYAJIT)
  @Category(UnitTests.class)
  public void testUpdateSourceYamlWithoutName() throws IOException {
    clonePipelineDTO.getDestinationConfig().setPipelineName(null);
    assertThatThrownBy(
        () -> pipelineCloneHelper.updatePipelineMetadataInSourceYaml(clonePipelineDTO, SOURCE_PIPELINE_YAML, accountId))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(String.format("Destination Pipeline Name should not be null for pipeline [%s]",
            clonePipelineDTO.getDestinationConfig().getPipelineIdentifier()));
  }

  @Test
  @Owner(developers = SOUMYAJIT)
  @Category(UnitTests.class)
  public void testUpdateSourceYamlWithTags() throws IOException {
    Map<String, String> tags = new HashMap<>();
    tags.put("key1", "value1");
    tags.put("key2", "value2");

    clonePipelineDTO.getDestinationConfig().setTags(tags);
    String updatedYaml =
        pipelineCloneHelper.updatePipelineMetadataInSourceYaml(clonePipelineDTO, SOURCE_PIPELINE_YAML, accountId);

    ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());

    JsonNode jsonNode = null;
    try {
      jsonNode = objectMapper.readTree(updatedYaml);
    } catch (JsonProcessingException e) {
      log.error(String.format("Error while processing source yaml to json for pipeline [%s]",
                    clonePipelineDTO.getSourceConfig().getPipelineIdentifier()),
          e);
    }
    Map<String, String> updatedTags =
        objectMapper.convertValue(jsonNode.get("pipeline").get("tags"), new TypeReference<Map<String, String>>() {});
    assertThat(tags).isEqualTo(updatedTags);
  }

  @Test
  @Owner(developers = SOUMYAJIT)
  @Category(UnitTests.class)
  public void testCheckAccess() {
    pipelineCloneHelper.checkAccess(clonePipelineDTO, accountId);
    verify(accessControlClient, times(1))
        .checkForAccessOrThrow(any(), any(), eq(PipelineRbacPermissions.PIPELINE_CREATE_AND_EDIT));
    verify(accessControlClient, times(1))
        .checkForAccessOrThrow(any(), any(), eq(PipelineRbacPermissions.PIPELINE_VIEW));
  }

  @Test
  @Owner(developers = SOUMYAJIT)
  @Category(UnitTests.class)
  public void testCheckInvalidYaml() {
    String invalidYaml = ":";

    assertThatThrownBy(
        () -> pipelineCloneHelper.updatePipelineMetadataInSourceYaml(clonePipelineDTO, invalidYaml, accountId))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(String.format("Generic Backend Error occurred for pipeline [%s] org [%s] project [%s]",
            clonePipelineDTO.getSourceConfig().getPipelineIdentifier(),
            clonePipelineDTO.getSourceConfig().getOrgIdentifier(),
            clonePipelineDTO.getSourceConfig().getProjectIdentifier()));
  }

  @Test
  @Owner(developers = SOUMYAJIT)
  @Category(UnitTests.class)
  public void testCheckSameDestConfig() {
    clonePipelineDTO.setDestinationConfig(DestinationPipelineConfig.builder()
                                              .pipelineName("temp")
                                              .pipelineIdentifier(SOURCE_PIPELINE_IDENTIFIER)
                                              .projectIdentifier(SOURCE_PROJ_IDENTIFIER)
                                              .orgIdentifier(SOURCE_ORG_IDENTIFIER)
                                              .build());
    String yaml =
        pipelineCloneHelper.updatePipelineMetadataInSourceYaml(clonePipelineDTO, SOURCE_PIPELINE_YAML, accountId);
    assertThat(yaml).isNotEqualTo(null);
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testUpdateSourceV1Yaml() throws IOException {
    String updatedYaml =
        pipelineCloneHelper.updatePipelineMetadataInSourceYamlV1(clonePipelineDTO, SOURCE_PIPELINE_YAML_V1);
    ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
    JsonNode jsonNode = objectMapper.readTree(updatedYaml);
    assertThat(updatedYaml).isNotNull();
    assertThat(jsonNode.get("name").asText()).isEqualTo(clonePipelineDTO.getDestinationConfig().getPipelineName());
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testUpdateSourceV1YamlWithoutName() throws IOException {
    String updatedYaml = pipelineCloneHelper.updatePipelineMetadataInSourceYamlV1(
        clonePipelineDTO, SOURCE_PIPELINE_YAML_WITHOUT_NAME_V1);
    ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
    JsonNode jsonNode = objectMapper.readTree(updatedYaml);
    assertThat(updatedYaml).isNotNull();
    assertThat(jsonNode.get("name")).isNull();
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testUpdateSourceV1YamlWithoutCloneName() {
    clonePipelineDTO.getDestinationConfig().setPipelineName(null);
    assertThatThrownBy(
        () -> pipelineCloneHelper.updatePipelineMetadataInSourceYamlV1(clonePipelineDTO, SOURCE_PIPELINE_YAML_V1))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(String.format("Destination pipeline name should not be null for pipeline [%s]",
            clonePipelineDTO.getDestinationConfig().getPipelineIdentifier()));
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testCheckInvalidYamlV1() {
    String invalidYaml = ":";
    assertThatThrownBy(() -> pipelineCloneHelper.updatePipelineMetadataInSourceYamlV1(clonePipelineDTO, invalidYaml))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(String.format("Generic Backend Error occurred for pipeline [%s] org [%s] project [%s]",
            clonePipelineDTO.getSourceConfig().getPipelineIdentifier(),
            clonePipelineDTO.getSourceConfig().getOrgIdentifier(),
            clonePipelineDTO.getSourceConfig().getProjectIdentifier()));
  }
}
