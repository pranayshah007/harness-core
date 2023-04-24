/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.service.yamlschema;

import static io.harness.rule.OwnerRule.BRIJESH;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import io.harness.EntityType;
import io.harness.ModuleType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.pipeline.service.yamlschema.approval.ApprovalYamlSchemaService;
import io.harness.pms.pipeline.service.yamlschema.customstage.CustomStageYamlSchemaService;
import io.harness.pms.pipeline.service.yamlschema.featureflag.FeatureFlagYamlService;
import io.harness.pms.pipeline.service.yamlschema.pipelinestage.PipelineStageYamlSchemaService;
import io.harness.rule.Owner;
import io.harness.yaml.schema.YamlSchemaProvider;
import io.harness.yaml.schema.beans.PartialSchemaDTO;
import io.harness.yaml.schema.beans.YamlGroup;
import io.harness.yaml.schema.beans.YamlSchemaDetailsWrapper;
import io.harness.yaml.schema.beans.YamlSchemaMetadata;
import io.harness.yaml.schema.beans.YamlSchemaWithDetails;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class LocalSchemaGetterTest {
  @Mock private YamlSchemaProvider yamlSchemaProvider;
  @Mock private ApprovalYamlSchemaService approvalYamlSchemaService;
  @Mock private FeatureFlagYamlService featureFlagYamlService;
  @Mock private CustomStageYamlSchemaService customStageYamlSchemaService;
  @Mock private PipelineStageYamlSchemaService pipelineStageYamlSchemaService;
  @Mock private PmsYamlSchemaHelper pmsYamlSchemaHelper;
  @InjectMocks LocalSchemaGetter localSchemaGetter;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGetSchema() {
    PartialSchemaDTO approvalPartialSchemaDTO =
        PartialSchemaDTO.builder().namespace("approval").moduleType(ModuleType.PMS).nodeName("approval").build();
    PartialSchemaDTO cfPartialSchemaDTO =
        PartialSchemaDTO.builder().namespace("cf").moduleType(ModuleType.PMS).nodeName("cf").build();
    PartialSchemaDTO customStagePartialSchemaDTO =
        PartialSchemaDTO.builder().namespace("custom").moduleType(ModuleType.PMS).nodeName("custom").build();
    PartialSchemaDTO pipelineStagePartialSchemaDTO =
        PartialSchemaDTO.builder().namespace("pipeline").moduleType(ModuleType.PMS).nodeName("pipeline").build();

    doReturn(approvalPartialSchemaDTO)
        .when(approvalYamlSchemaService)
        .getApprovalYamlSchema(any(), any(), any(), any(), any());
    doReturn(cfPartialSchemaDTO)
        .when(featureFlagYamlService)
        .getFeatureFlagYamlSchema(any(), any(), any(), any(), any());
    doReturn(customStagePartialSchemaDTO)
        .when(customStageYamlSchemaService)
        .getCustomStageYamlSchema(any(), any(), any(), any(), any());
    doReturn(pipelineStagePartialSchemaDTO)
        .when(pipelineStageYamlSchemaService)
        .getPipelineStageYamlSchema(any(), any(), any(), any(), any());

    List<PartialSchemaDTO> partialSchemaDTOList = localSchemaGetter.getSchema(Collections.emptyList());
    assertEquals(partialSchemaDTOList.size(), 4);
    assertEquals(partialSchemaDTOList.get(1), cfPartialSchemaDTO);
    assertEquals(partialSchemaDTOList.get(2), customStagePartialSchemaDTO);
    assertEquals(partialSchemaDTOList.get(0), approvalPartialSchemaDTO);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGetSchemaDetails() {
    YamlSchemaMetadata yamlSchemaMetadata = YamlSchemaMetadata.builder()
                                                .yamlGroup(YamlGroup.builder().group("step").build())
                                                .modulesSupported(Collections.singletonList(ModuleType.CD))
                                                .build();
    YamlSchemaWithDetails yamlSchemaWithDetails = YamlSchemaWithDetails.builder()
                                                      .yamlSchemaMetadata(yamlSchemaMetadata)
                                                      .moduleType(ModuleType.PMS)
                                                      .schemaClassName("HttpStepNode")
                                                      .build();
    List<YamlSchemaWithDetails> yamlSchemaWithDetailsList = new ArrayList<>();
    yamlSchemaWithDetailsList.add(yamlSchemaWithDetails);
    doReturn(yamlSchemaWithDetailsList)
        .when(yamlSchemaProvider)
        .getCrossFunctionalStepsSchemaDetails(any(), any(), any(), any(), any());
    YamlSchemaDetailsWrapper yamlSchemaDetailsWrapper = localSchemaGetter.getSchemaDetails();

    assertEquals(yamlSchemaDetailsWrapper.getYamlSchemaWithDetailsList().size(), 2);
    assertEquals(yamlSchemaDetailsWrapper.getYamlSchemaWithDetailsList().get(0).getModuleType(), ModuleType.PMS);
    assertEquals(yamlSchemaDetailsWrapper.getYamlSchemaWithDetailsList().get(0).getSchemaClassName(), "HttpStepNode");
    assertEquals(
        yamlSchemaDetailsWrapper.getYamlSchemaWithDetailsList().get(0).getYamlSchemaMetadata(), yamlSchemaMetadata);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testFetchStepYamlSchema() throws IOException {
    YamlSchemaMetadata yamlSchemaMetadata = YamlSchemaMetadata.builder()
                                                .yamlGroup(YamlGroup.builder().group("step").build())
                                                .modulesSupported(Collections.singletonList(ModuleType.CD))
                                                .build();
    YamlSchemaWithDetails yamlSchemaWithDetails = YamlSchemaWithDetails.builder()
                                                      .yamlSchemaMetadata(yamlSchemaMetadata)
                                                      .moduleType(ModuleType.PMS)
                                                      .schemaClassName("HttpStepNode")
                                                      .build();
    List<YamlSchemaWithDetails> yamlSchemaWithDetailsList = new ArrayList<>();
    yamlSchemaWithDetailsList.add(yamlSchemaWithDetails);

    ObjectMapper objectMapper = new ObjectMapper();
    JsonNode approvalSchema = objectMapper.readTree(getResource("approvalStageSchema.json"));

    PartialSchemaDTO approvalPartialSchemaDTO = PartialSchemaDTO.builder()
                                                    .namespace("approval")
                                                    .moduleType(ModuleType.PMS)
                                                    .nodeName("approval")
                                                    .schema(approvalSchema)
                                                    .build();
    doReturn(approvalPartialSchemaDTO)
        .when(approvalYamlSchemaService)
        .getApprovalYamlSchema(any(), any(), any(), any(), any());

    JsonNode response = localSchemaGetter.fetchStepYamlSchema(
        "orgId", "projectId", null, EntityType.APPROVAL_STAGE, "STAGE", yamlSchemaWithDetailsList);
    assertEquals(response.get("properties").get("type").get("enum").get(0).asText(), "Approval");

    JsonNode cfSchema = objectMapper.readTree(getResource("cfStageSchema.json"));
    PartialSchemaDTO cfPartialSchemaDTO =
        PartialSchemaDTO.builder().namespace("cf").moduleType(ModuleType.PMS).nodeName("cf").schema(cfSchema).build();

    doReturn(cfPartialSchemaDTO)
        .when(featureFlagYamlService)
        .getFeatureFlagYamlSchema(any(), any(), any(), any(), any());

    response = localSchemaGetter.fetchStepYamlSchema(
        "orgId", "projectId", null, EntityType.FEATURE_FLAG_STAGE, "STAGE", yamlSchemaWithDetailsList);
    assertEquals(response.get("properties").get("type").get("enum").get(0).asText(), "Cf");

    assertThatThrownBy(()
                           -> localSchemaGetter.fetchStepYamlSchema("orgId", "projectId", null,
                               EntityType.DEPLOYMENT_STAGE, "STAGE", yamlSchemaWithDetailsList))
        .isInstanceOf(InvalidRequestException.class);

    JsonNode httpStepSchema = objectMapper.readTree(getResource("stepSchema.json"));
    doReturn(httpStepSchema).when(yamlSchemaProvider).getYamlSchema(EntityType.HTTP_STEP, "orgId", "projectId", null);
    response = localSchemaGetter.fetchStepYamlSchema(
        "orgId", "projectId", null, EntityType.HTTP_STEP, "STEP", yamlSchemaWithDetailsList);
    assertEquals(response.get("properties").get("type").get("enum").get(0).asText(), "Http");
  }

  private String getResource(String path) throws IOException {
    return IOUtils.resourceToString(path, StandardCharsets.UTF_8, this.getClass().getClassLoader());
  }
}
