/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.service;

import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.FERNANDOD;
import static io.harness.rule.OwnerRule.PRASHANTSHARMA;
import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;
import static io.harness.yaml.schema.beans.SchemaConstants.PIPELINE_NODE;
import static io.harness.yaml.schema.beans.SchemaConstants.PROPERTIES_NODE;
import static io.harness.yaml.schema.beans.SchemaConstants.TRIGGER_NODE;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.EntityType;
import io.harness.ModuleType;
import io.harness.PipelineServiceConfiguration;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.InvalidYamlException;
import io.harness.pms.merger.helpers.FQNMapGenerator;
import io.harness.pms.pipeline.service.yamlschema.PmsYamlSchemaHelper;
import io.harness.pms.pipeline.service.yamlschema.SchemaFetcher;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.serializer.JsonUtils;
import io.harness.yaml.schema.beans.YamlGroup;
import io.harness.yaml.schema.beans.YamlSchemaDetailsWrapper;
import io.harness.yaml.schema.beans.YamlSchemaMetadata;
import io.harness.yaml.schema.beans.YamlSchemaWithDetails;
import io.harness.yaml.utils.JsonPipelineUtils;
import io.harness.yaml.validator.YamlSchemaValidator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeoutException;
import org.apache.commons.io.IOUtils;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class PMSYamlSchemaServiceImplTest {
  @Mock private SchemaFetcher schemaFetcher;
  @Mock PmsYamlSchemaHelper pmsYamlSchemaHelper;
  @Mock YamlSchemaValidator yamlSchemaValidator;

  @InjectMocks private PMSYamlSchemaServiceImpl pmsYamlSchemaService;
  @Mock private ExecutorService yamlSchemaExecutor;

  PipelineServiceConfiguration pipelineServiceConfiguration;

  private static final String ACC_ID = "accountId";
  private static final String ORG_ID = "orgId";
  private static final String PRJ_ID = "projectId";

  @Before
  public void setUp() throws ExecutionException, InterruptedException, TimeoutException {
    MockitoAnnotations.initMocks(this);
    pmsYamlSchemaService = new PMSYamlSchemaServiceImpl(
        yamlSchemaValidator, pmsYamlSchemaHelper, schemaFetcher, 25, yamlSchemaExecutor, null);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testFetchSchemaWithDetailsFromModules() {
    ModuleType moduleType = ModuleType.CD;
    YamlSchemaMetadata yamlSchemaMetadata =
        YamlSchemaMetadata.builder().yamlGroup(YamlGroup.builder().group("step").build()).build();
    doReturn(
        YamlSchemaDetailsWrapper.builder()
            .yamlSchemaWithDetailsList(Collections.singletonList(
                YamlSchemaWithDetails.builder().moduleType(moduleType).yamlSchemaMetadata(yamlSchemaMetadata).build()))
            .build())
        .when(schemaFetcher)
        .fetchSchemaDetail(any(), any());
    List<YamlSchemaWithDetails> yamlSchemaWithDetailsList =
        pmsYamlSchemaService.fetchSchemaWithDetailsFromModules("accountId", Collections.singletonList(moduleType));

    assertEquals(yamlSchemaWithDetailsList.get(0).getYamlSchemaMetadata(), yamlSchemaMetadata);
    assertEquals(yamlSchemaWithDetailsList.get(0).getModuleType(), moduleType);
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldValidateUniqueFqn() {
    String yaml = "pipeline:\n"
        + "  identifier: cipipeline\n"
        + "  name: Integration Pipeline\n";
    try (MockedStatic<FQNMapGenerator> aMock = mockStatic(FQNMapGenerator.class)) {
      pmsYamlSchemaService.validateUniqueFqn(yaml);
      aMock.verify(() -> FQNMapGenerator.generateFQNMap(any(JsonNode.class)));
    }
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldValidateUniqueFqnInvalidYaml() {
    try (MockedStatic<YamlUtils> yamlUtils = mockStatic(YamlUtils.class)) {
      yamlUtils.when(() -> YamlUtils.getErrorNodePartialFQN(any())).thenReturn("DUMMY");
      yamlUtils.when(() -> YamlUtils.readTree(any())).thenThrow(new IOException());
      Assertions.assertThatCode(() -> pmsYamlSchemaService.validateUniqueFqn(""))
          .isInstanceOf(InvalidYamlException.class)
          .hasMessage("Invalid yaml in node [DUMMY]");
    }
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void staticSchemaForTrigger() throws IOException {
    JsonNode expected = readJsonNode("trigger-short.json");
    when(schemaFetcher.fetchTriggerStaticYamlSchema()).thenReturn(expected);

    final JsonNode result = pmsYamlSchemaService.getStaticSchemaForAllEntities("trigger", null, null, "v0");

    assertThat(result).isNotNull();
    assertThat(result.get(PROPERTIES_NODE).get(TRIGGER_NODE).get("$ref").asText())
        .isEqualTo("#/definitions/trigger/trigger");
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void staticSchemaForPipeline() throws IOException {
    JsonNode expected = readJsonNode("pipeline-short.json");
    when(schemaFetcher.fetchPipelineStaticYamlSchema("v0")).thenReturn(expected);

    final JsonNode result = pmsYamlSchemaService.getStaticSchemaForAllEntities("pipeline", null, null, "v0");

    assertThat(result).isNotNull();
    assertThat(result.get(PROPERTIES_NODE).get(PIPELINE_NODE).get("$ref").asText())
        .isEqualTo("#/definitions/pipeline/pipeline");
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void staticSchemaForPipelineV1() throws IOException {
    JsonNode expected = readJsonNode("pipeline-short.json");
    when(schemaFetcher.fetchPipelineStaticYamlSchema("v1")).thenReturn(expected);

    final JsonNode result = pmsYamlSchemaService.getStaticSchemaForAllEntities("pipeline", null, null, "v1");

    assertThat(result).isNotNull();
    assertThat(result.get(PROPERTIES_NODE).get(PIPELINE_NODE).get("$ref").asText())
        .isEqualTo("#/definitions/pipeline/pipeline");
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void staticSchemaForPipelineInvalidVersion() {
    when(schemaFetcher.fetchPipelineStaticYamlSchema("v2x"))
        .thenThrow(new InvalidRequestException(
            "[PMS] Incorrect version [v2x] of Pipeline Schema passed, Valid values are [v0, v1]"));
    assertThatThrownBy(() -> pmsYamlSchemaService.getStaticSchemaForAllEntities("pipeline", null, null, "v2x"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("[PMS] Incorrect version [v2x] of Pipeline Schema passed, Valid values are [v0, v1]");
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldRemoveDuplicateFromStageElementConfig() throws IOException {
    final JsonNode jsonNode = readJsonNode("remove-duplicate-yaml-schema.json");
    assertThat(jsonNode.get("oneOf").get(1).get("allOf").size()).isEqualTo(3);

    pmsYamlSchemaService.removeDuplicateIfThenFromStageElementConfig((ObjectNode) jsonNode);

    // AFTER REMOVE WE SHOULD HAVE ONLY "#/definitions/CustomStageConfig"
    assertThat(jsonNode.get("oneOf").get(1).get("allOf").size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testCalculateFileURL() {
    pipelineServiceConfiguration = mock(PipelineServiceConfiguration.class);
    pmsYamlSchemaService.pipelineServiceConfiguration = pipelineServiceConfiguration;

    doReturn("https://raw.githubusercontent.com/harness/harness-schema/main/%s/%s")
        .when(pipelineServiceConfiguration)
        .getStaticSchemaFileURL();
    String fileUrL = pmsYamlSchemaService.calculateFileURL(EntityType.PIPELINES, "v0");
    assertThat(fileUrL).isEqualTo("https://raw.githubusercontent.com/harness/harness-schema/main/v0/pipeline.json");
  }

  public JsonNode fetchFile(String filePath) throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    String staticJson =
        Resources.toString(Objects.requireNonNull(classLoader.getResource(filePath)), StandardCharsets.UTF_8);
    return JsonUtils.asObject(staticJson, JsonNode.class);
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldInvalidateCaches() {
    pmsYamlSchemaService.invalidateAllCache();
    verify(schemaFetcher).invalidateAllCache();
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldNotValidateYamlSchema() throws IOException {
    when(pmsYamlSchemaHelper.isFeatureFlagEnabled(FeatureName.DISABLE_PIPELINE_SCHEMA_VALIDATION, ACC_ID))
        .thenReturn(true);
    pmsYamlSchemaService.validateYamlSchemaInternal(ACC_ID, ORG_ID, PRJ_ID, null);
    verify(yamlSchemaValidator, never()).validate(anyString(), anyString(), anyBoolean(), anyInt(), anyString());
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldValidateYamlSchema() throws Throwable {
    final String yaml = "yamlContent";
    final String schemaString = "schemaContent";
    pmsYamlSchemaService.allowedParallelStages = 0;

    when(pmsYamlSchemaHelper.isFeatureFlagEnabled(FeatureName.DISABLE_PIPELINE_SCHEMA_VALIDATION, ACC_ID))
        .thenReturn(false);
    when(pmsYamlSchemaHelper.isFeatureFlagEnabled(FeatureName.DONT_RESTRICT_PARALLEL_STAGE_COUNT, ACC_ID))
        .thenReturn(false);

    when(pmsYamlSchemaHelper.isFeatureFlagEnabled(FeatureName.PIE_STATIC_YAML_SCHEMA, ACC_ID)).thenReturn(false);

    MockedStatic<JsonPipelineUtils> pipelineUtils = mockStatic(JsonPipelineUtils.class);
    pipelineUtils.when(() -> JsonPipelineUtils.writeJsonString(any())).thenReturn(schemaString);

    pmsYamlSchemaService.validateYamlSchemaInternal(ACC_ID, ORG_ID, PRJ_ID, YamlUtils.readAsJsonNode(yaml));

    verify(yamlSchemaValidator)
        .validate(eq(YamlUtils.readAsJsonNode(yaml)), eq(schemaString), anyBoolean(), anyInt(), anyString());
  }

  private JsonNode readJsonNode(String resourceName) throws IOException {
    final String resource = IOUtils.resourceToString(resourceName, StandardCharsets.UTF_8, getClass().getClassLoader());
    ObjectMapper objectMapper = new ObjectMapper();
    return objectMapper.readTree(resource);
  }
}
