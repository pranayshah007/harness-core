/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.service;

import static io.harness.pms.pipeline.service.PMSYamlSchemaServiceImpl.STAGE_ELEMENT_CONFIG;
import static io.harness.pms.pipeline.service.yamlschema.PmsYamlSchemaHelper.STEP_ELEMENT_CONFIG;
import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.FERNANDOD;
import static io.harness.rule.OwnerRule.PRASHANTSHARMA;
import static io.harness.yaml.schema.beans.SchemaConstants.DEFINITIONS_NODE;
import static io.harness.yaml.schema.beans.SchemaConstants.ONE_OF_NODE;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.EntityType;
import io.harness.ModuleType;
import io.harness.PipelineServiceConfiguration;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.encryption.Scope;
import io.harness.exception.InvalidYamlException;
import io.harness.jackson.JsonNodeUtils;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.merger.helpers.FQNMapGenerator;
import io.harness.pms.pipeline.service.yamlschema.PmsYamlSchemaHelper;
import io.harness.pms.pipeline.service.yamlschema.SchemaFetcher;
import io.harness.pms.sdk.PmsSdkInstanceService;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.yaml.schema.YamlSchemaProvider;
import io.harness.yaml.schema.YamlSchemaTransientHelper;
import io.harness.yaml.schema.beans.YamlGroup;
import io.harness.yaml.schema.beans.YamlSchemaDetailsWrapper;
import io.harness.yaml.schema.beans.YamlSchemaMetadata;
import io.harness.yaml.schema.beans.YamlSchemaWithDetails;
import io.harness.yaml.utils.JsonPipelineUtils;
import io.harness.yaml.validator.YamlSchemaValidator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
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
  @Mock private YamlSchemaProvider yamlSchemaProvider;
  @Mock PmsYamlSchemaHelper pmsYamlSchemaHelper;
  @Mock PmsSdkInstanceService pmsSdkInstanceService;
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
    pmsYamlSchemaService = new PMSYamlSchemaServiceImpl(yamlSchemaProvider, yamlSchemaValidator, pmsSdkInstanceService,
        pmsYamlSchemaHelper, schemaFetcher, 25, yamlSchemaExecutor);
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
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldAddStepSpecTypeWhenGetIndividualYamlSchema() throws IOException {
    String yamlGroup = "";
    JsonNode expected = readJsonNode("individual-yaml-schema.json");

    when(
        schemaFetcher.fetchStepYamlSchema(ACC_ID, PRJ_ID, ORG_ID, Scope.ACCOUNT, EntityType.PIPELINES, yamlGroup, null))
        .thenReturn(expected);

    final JsonNode result = pmsYamlSchemaService.getIndividualYamlSchema(
        ACC_ID, ORG_ID, PRJ_ID, Scope.ACCOUNT, EntityType.PIPELINES, yamlGroup);

    assertThat(result).isNotNull();
    assertThat(result.get(DEFINITIONS_NODE).get("StepSpecType")).isNotNull();
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

    fileUrL = pmsYamlSchemaService.calculateFileURL(EntityType.TEMPLATE, "v1");
    assertThat(fileUrL).isEqualTo("https://raw.githubusercontent.com/harness/harness-schema/main/v1/template.json");

    doReturn("https://raw.githubusercontent.com/harness/harness-schema/quality-assurance/%s/%s")
        .when(pipelineServiceConfiguration)
        .getStaticSchemaFileURL();
    fileUrL = pmsYamlSchemaService.calculateFileURL(EntityType.TEMPLATE, "v1");
    assertThat(fileUrL).isEqualTo(
        "https://raw.githubusercontent.com/harness/harness-schema/quality-assurance/v1/template.json");
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void verifyGetPipelineYamlSchema() throws Throwable {
    final Scope scope = Scope.ORG;
    prepareAndAssertGetPipelineYamlSchemaInternal(
        scope, () -> pmsYamlSchemaService.getPipelineYamlSchema(ACC_ID, PRJ_ID, ORG_ID, scope));
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
    pmsYamlSchemaService.validateYamlSchemaInternal(ACC_ID, ORG_ID, PRJ_ID, "");
    verify(yamlSchemaValidator, never()).validate(anyString(), anyString(), anyBoolean(), anyInt(), anyString());
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldValidateYamlSchema() throws Throwable {
    final String yaml = "yamlContent";
    final String schemaString = "schemaContent";
    final Scope scope = Scope.PROJECT;
    pmsYamlSchemaService.allowedParallelStages = 0;

    when(pmsYamlSchemaHelper.isFeatureFlagEnabled(FeatureName.DISABLE_PIPELINE_SCHEMA_VALIDATION, ACC_ID))
        .thenReturn(false);
    when(pmsYamlSchemaHelper.isFeatureFlagEnabled(FeatureName.DONT_RESTRICT_PARALLEL_STAGE_COUNT, ACC_ID))
        .thenReturn(false);

    try (MockedStatic<JsonPipelineUtils> pipelineUtils = mockStatic(JsonPipelineUtils.class)) {
      pipelineUtils.when(() -> JsonPipelineUtils.writeJsonString(any())).thenReturn(schemaString);
      prepareAndAssertGetPipelineYamlSchemaInternal(
          scope, () -> pmsYamlSchemaService.validateYamlSchemaInternal(ACC_ID, ORG_ID, PRJ_ID, yaml));
    }

    verify(yamlSchemaValidator).validate(eq(yaml), eq(schemaString), anyBoolean(), anyInt(), anyString());
  }

  private JsonNode readJsonNode(String resourceName) throws IOException {
    final String resource = IOUtils.resourceToString(resourceName, StandardCharsets.UTF_8, getClass().getClassLoader());
    ObjectMapper objectMapper = new ObjectMapper();
    return objectMapper.readTree(resource);
  }

  // WE PREPARE THE SAME BEHAVIOR TO EVERY CALL THAT NEED USE THE GetPipelineYamlSchemaInternal PRIVATE METHOD.
  // USING THIS APPROACH IS NOT REQUIRED DUPLICATE CODE JUST TO CREATE A SUCCESSFUL BEHAVIOR TO EVERYONE. BUT IS
  // IMPORTANT TO NOTE THAT WE DON'T COVERAGE SPECIFIC CASES, JUST THE SUNNY DAY.
  private void prepareAndAssertGetPipelineYamlSchemaInternal(Scope scope, PipelineYamlSchemaInternal verification)
      throws Throwable {
    ObjectNode pipelineSchema = mock(ObjectNode.class);
    JsonNode pipelineSteps = mock(JsonNode.class);
    when(yamlSchemaProvider.getYamlSchema(EntityType.PIPELINES, ORG_ID, PRJ_ID, scope)).thenReturn(pipelineSchema);
    when(yamlSchemaProvider.getYamlSchema(EntityType.PIPELINE_STEPS, ORG_ID, PRJ_ID, scope)).thenReturn(pipelineSteps);

    ObjectNode pipelineDefinitions = mock(ObjectNode.class);
    ObjectNode pipelineStepsDefinitions = mock(ObjectNode.class);
    when(pipelineSchema.get(DEFINITIONS_NODE)).thenReturn(pipelineDefinitions);
    when(pipelineSteps.get(DEFINITIONS_NODE)).thenReturn(pipelineStepsDefinitions);

    ObjectNode stageElementConfig = mock(ObjectNode.class);
    when(pipelineDefinitions.get(STAGE_ELEMENT_CONFIG)).thenReturn(stageElementConfig);
    when(stageElementConfig.get(ONE_OF_NODE)).thenReturn(mock(ArrayNode.class));

    try (MockedStatic<JsonNodeUtils> jsonNodeUtils = mockStatic(JsonNodeUtils.class);
         MockedStatic<YamlSchemaTransientHelper> yamlHelper = mockStatic(YamlSchemaTransientHelper.class);
         MockedStatic<PmsYamlSchemaHelper> schemaHelper = mockStatic(PmsYamlSchemaHelper.class)) {
      ObjectNode mergedDefinitions = mock(ObjectNode.class);
      jsonNodeUtils.when(() -> JsonNodeUtils.merge(pipelineDefinitions, pipelineStepsDefinitions))
          .thenReturn(mergedDefinitions);

      ObjectNode finalMergedDefinitions = mock(ObjectNode.class);
      when(yamlSchemaProvider.mergeAllV2StepsDefinitions(
               eq(PRJ_ID), eq(ORG_ID), eq(scope), eq(mergedDefinitions), any()))
          .thenReturn(finalMergedDefinitions);
      ObjectNode stepElementConfig = mock(ObjectNode.class);
      when(finalMergedDefinitions.get(STEP_ELEMENT_CONFIG)).thenReturn(stepElementConfig);

      // EXECUTE
      verification.apply();
      verify(pipelineSchema).set(DEFINITIONS_NODE, pipelineDefinitions);

      yamlHelper.verify(() -> YamlSchemaTransientHelper.removeV2StepEnumsFromStepElementConfig(stepElementConfig));
      yamlHelper.verify(() -> YamlSchemaTransientHelper.deleteSpecNodeInStageElementConfig(stageElementConfig));
      schemaHelper.verify(() -> PmsYamlSchemaHelper.flattenParallelElementConfig(any()));

      verify(pmsYamlSchemaHelper).getNodeEntityTypesByYamlGroup(StepCategory.STEP.name());
      verify(pmsSdkInstanceService).getActiveInstanceNames();
      verify(yamlSchemaProvider, times(2))
          .mergeAllV2StepsDefinitions(eq(PRJ_ID), eq(ORG_ID), eq(scope), eq(mergedDefinitions), any());
    }
  }

  private interface PipelineYamlSchemaInternal {
    void apply() throws Throwable;
  }
}
