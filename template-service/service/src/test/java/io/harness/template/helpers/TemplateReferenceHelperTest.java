/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.template.helpers;

import static io.harness.ng.core.template.TemplateEntityConstants.STAGE;
import static io.harness.rule.OwnerRule.INDER;
import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;
import static io.harness.template.helpers.TemplateReferenceTestHelper.ACCOUNT_ID;
import static io.harness.template.helpers.TemplateReferenceTestHelper.ORG_ID;
import static io.harness.template.helpers.TemplateReferenceTestHelper.PROJECT_ID;
import static io.harness.template.helpers.TemplateReferenceTestHelper.generateIdentifierRefWithUnknownScope;
import static io.harness.template.resources.beans.NGTemplateConstants.STABLE_VERSION;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.EntityType;
import io.harness.TemplateServiceTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.entitydetail.EntityDetailProtoToRestMapper;
import io.harness.ng.core.entitysetupusage.dto.EntitySetupUsageDTO;
import io.harness.ng.core.template.TemplateEntityType;
import io.harness.preflight.PreFlightCheckMetadata;
import io.harness.rule.Owner;
import io.harness.template.async.beans.SetupUsageParams;
import io.harness.template.entity.TemplateEntity;
import io.harness.template.handler.TemplateYamlConversionHandler;
import io.harness.template.handler.TemplateYamlConversionHandlerRegistry;
import io.harness.template.helpers.crud.PipelineTemplateCrudHelper;
import io.harness.template.helpers.crud.TemplateCrudHelperFactory;
import io.harness.template.services.NGTemplateServiceHelper;

import com.google.common.io.Resources;
import com.google.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.CDC)
public class TemplateReferenceHelperTest extends TemplateServiceTestBase {
  @Inject TemplateYamlConversionHelper templateYamlConversionHelper;

  @Mock TemplateSetupUsageHelper templateSetupUsageHelper;
  @Mock NGTemplateServiceHelper templateServiceHelper;
  @Inject TemplateYamlConversionHandlerRegistry templateYamlConversionHandlerRegistry;
  @Inject EntityDetailProtoToRestMapper entityDetailProtoToRestMapper;

  @Mock TemplateCrudHelperFactory templateCrudHelperFactory;
  @Mock PipelineTemplateCrudHelper pipelineTemplateCrudHelper;

  TemplateReferenceHelper templateReferenceHelper;

  @Before
  public void setup() throws IOException {
    templateReferenceHelper = new TemplateReferenceHelper(
        templateYamlConversionHelper, templateServiceHelper, templateSetupUsageHelper, templateCrudHelperFactory);
  }

  private String readFile(String filename) {
    ClassLoader classLoader = getClass().getClassLoader();
    try {
      return Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new InvalidRequestException("Could not read resource file: " + filename);
    }
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testGetNestedTemplateReferences_stageTemplateWithoutNestedReferenceTemplateInputs() throws IOException {
    String filename = "pms-stage-template.yaml";
    String yaml = readFile(filename);
    when(templateSetupUsageHelper.getReferencesOfTemplate(
             anyString(), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(Collections.EMPTY_LIST);

    List<EntityDetailProtoDTO> templateReferences =
        templateReferenceHelper.getNestedTemplateReferences(ACCOUNT_ID, ORG_ID, PROJECT_ID, yaml, true);
    assertThat(templateReferences).isNotNull().hasSize(2);
    Map<String, String> metadata1 = new HashMap<>();
    Map<String, String> metadata2 = new HashMap<>();
    metadata1.put("fqn", "stage.spec.execution.steps.http.template");
    metadata2.put("fqn", "stage.spec.execution.steps.approval.template");
    EntityDetailProtoDTO expected1 = TemplateReferenceTestHelper.generateTemplateRefEntityDetailProto(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, "approvalTemplate", "1", metadata2);
    EntityDetailProtoDTO expected2 = TemplateReferenceTestHelper.generateTemplateRefEntityDetailProto(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, "httpTemplate", "1", metadata1);
    assertThat(templateReferences).containsExactlyInAnyOrder(expected1, expected2);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testErrorMessageForInvalidScopeTemplates() {
    String templateYaml = readFile("stage-template-with-invalid-scope-references.yaml");
    // Project scope template cannot be used at Acc level
    assertThatThrownBy(
        () -> templateReferenceHelper.getNestedTemplateReferences(ACCOUNT_ID, null, null, templateYaml, false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("The project level template cannot be used at account level. Ref: [approvalTemplate]");

    // Project scope template cannot be used at Org level
    assertThatThrownBy(
        () -> templateReferenceHelper.getNestedTemplateReferences(ACCOUNT_ID, ORG_ID, null, templateYaml, false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("The project level template cannot be used at org level. Ref: [approvalTemplate]");

    // Project scope template can be used at Project level
    templateReferenceHelper.getNestedTemplateReferences(ACCOUNT_ID, ORG_ID, PROJECT_ID, templateYaml, false);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testPopulateTemplateReferences() throws IOException {
    String filename = "stage-template-with-references.yaml";
    TemplateEntity templateEntity = TemplateEntity.builder()
                                        .accountId(ACCOUNT_ID)
                                        .orgIdentifier(ORG_ID)
                                        .projectIdentifier(PROJECT_ID)
                                        .versionLabel("v1")
                                        .yaml(readFile(filename))
                                        .templateEntityType(TemplateEntityType.STAGE_TEMPLATE)
                                        .build();
    SetupUsageParams setupUsageParams = SetupUsageParams.builder().templateEntity(templateEntity).build();

    templateYamlConversionHandlerRegistry.register(STAGE, new TemplateYamlConversionHandler());
    when(templateCrudHelperFactory.getCrudHelperForTemplateType(TemplateEntityType.STAGE_TEMPLATE))
        .thenReturn(pipelineTemplateCrudHelper);
    Map<String, String> metadata = new HashMap<>();
    metadata.put(PreFlightCheckMetadata.FQN, "templateInputs.spec.execution.steps.jiraApproval.spec.connectorRef");
    when(pipelineTemplateCrudHelper.supportsReferences()).thenReturn(true);
    when(pipelineTemplateCrudHelper.getReferences(any(TemplateEntity.class), anyString()))
        .thenReturn(Collections.singletonList(TemplateReferenceTestHelper.generateIdentifierRefEntityDetailProto(
            ACCOUNT_ID, ORG_ID, PROJECT_ID, "jiraConnector", metadata, EntityTypeProtoEnum.CONNECTORS)));
    Map<String, String> metadataMap = new HashMap<>();
    metadataMap.put(PreFlightCheckMetadata.FQN, "templateInputs.spec.connectorRef");
    metadataMap.put(PreFlightCheckMetadata.EXPRESSION, "<+input>");
    when(templateSetupUsageHelper.getReferencesOfTemplate(
             anyString(), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(Collections.singletonList(
            EntitySetupUsageDTO.builder()
                .referredEntity(EntityDetail.builder()
                                    .entityRef(generateIdentifierRefWithUnknownScope(
                                        ACCOUNT_ID, ORG_ID, PROJECT_ID, "<+input>.allowedValues(a,b,c)", metadataMap))
                                    .type(EntityType.CONNECTORS)
                                    .build())
                .build()));

    templateReferenceHelper.populateTemplateReferences(
        SetupUsageParams.builder().templateEntity(templateEntity).build());

    ArgumentCaptor<List> referredEntitiesArgumentCapture = ArgumentCaptor.forClass(List.class);
    verify(templateSetupUsageHelper)
        .publishSetupUsageEvent(eq(setupUsageParams), referredEntitiesArgumentCapture.capture(), any());
    List<EntityDetailProtoDTO> referredEntities = referredEntitiesArgumentCapture.getValue();
    assertThat(referredEntities).isNotNull().hasSize(4);
    assertThat(referredEntities).containsExactlyInAnyOrderElementsOf(getStageTemplateProtoReferences());
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testCalculateTemplateReferences() throws IOException {
    String filename = "stage-template-with-references.yaml";
    TemplateEntity templateEntity = TemplateEntity.builder()
                                        .accountId(ACCOUNT_ID)
                                        .orgIdentifier(ORG_ID)
                                        .projectIdentifier(PROJECT_ID)
                                        .versionLabel("v1")
                                        .yaml(readFile(filename))
                                        .templateEntityType(TemplateEntityType.STAGE_TEMPLATE)
                                        .build();

    SetupUsageParams setupUsageParams = SetupUsageParams.builder().templateEntity(templateEntity).build();

    templateYamlConversionHandlerRegistry.register(STAGE, new TemplateYamlConversionHandler());
    when(templateCrudHelperFactory.getCrudHelperForTemplateType(TemplateEntityType.STAGE_TEMPLATE))
        .thenReturn(pipelineTemplateCrudHelper);
    Map<String, String> metadata = new HashMap<>();
    metadata.put(PreFlightCheckMetadata.FQN, "templateInputs.spec.execution.steps.jiraApproval.spec.connectorRef");
    when(pipelineTemplateCrudHelper.supportsReferences()).thenReturn(true);
    when(pipelineTemplateCrudHelper.getReferences(any(TemplateEntity.class), anyString()))
        .thenReturn(Collections.singletonList(TemplateReferenceTestHelper.generateIdentifierRefEntityDetailProto(
            ACCOUNT_ID, ORG_ID, PROJECT_ID, "jiraConnector", metadata, EntityTypeProtoEnum.CONNECTORS)));
    Map<String, String> metadataMap = new HashMap<>();
    metadataMap.put(PreFlightCheckMetadata.FQN, "templateInputs.spec.connectorRef");
    metadataMap.put(PreFlightCheckMetadata.EXPRESSION, "<+input>");
    when(templateSetupUsageHelper.getReferencesOfTemplate(
             anyString(), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(Collections.singletonList(
            EntitySetupUsageDTO.builder()
                .referredEntity(EntityDetail.builder()
                                    .entityRef(generateIdentifierRefWithUnknownScope(
                                        ACCOUNT_ID, ORG_ID, PROJECT_ID, "<+input>.allowedValues(a,b,c)", metadataMap))
                                    .type(EntityType.CONNECTORS)
                                    .build())
                .build()));

    List<EntityDetailProtoDTO> referredEntities = templateReferenceHelper.calculateTemplateReferences(templateEntity);
    templateReferenceHelper.publishTemplateReferences(
        SetupUsageParams.builder().templateEntity(templateEntity).build(), referredEntities);

    ArgumentCaptor<List> referredEntitiesArgumentCapture = ArgumentCaptor.forClass(List.class);
    verify(templateSetupUsageHelper)
        .publishSetupUsageEvent(eq(setupUsageParams), referredEntitiesArgumentCapture.capture(), any());
    List<EntityDetailProtoDTO> fetchedReferredEntities = referredEntitiesArgumentCapture.getValue();
    assertThat(fetchedReferredEntities).isNotNull().hasSize(4);
    assertThat(fetchedReferredEntities).isEqualTo(referredEntities);
    assertThat(fetchedReferredEntities).containsExactlyInAnyOrderElementsOf(getStageTemplateProtoReferences());
  }

  private List<EntityDetailProtoDTO> getStageTemplateProtoReferences() {
    HashMap<String, String> metadata1 = new HashMap<>();
    metadata1.put("fqn", "stage.spec.execution.steps.approval.template");
    HashMap<String, String> metadata2 = new HashMap<>();
    metadata2.put("fqn", "stage.spec.execution.steps.jiraApprovalTemplate.template");
    EntityDetailProtoDTO expected1 = TemplateReferenceTestHelper.generateTemplateRefEntityDetailProto(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, "approvalTemplate", "1", metadata1);
    EntityDetailProtoDTO expected2 = TemplateReferenceTestHelper.generateTemplateRefEntityDetailProto(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, "jiraApprovalTemplate", "1", metadata2);
    EntityDetailProtoDTO expected3 = TemplateReferenceTestHelper.generateIdentifierRefEntityDetailProto(ACCOUNT_ID,
        ORG_ID, PROJECT_ID, "jiraConnector",
        new HashMap<>(Collections.singletonMap(
            PreFlightCheckMetadata.FQN, "templateInputs.spec.execution.steps.jiraApproval.spec.connectorRef")),
        EntityTypeProtoEnum.CONNECTORS);
    Map<String, String> expectedMap = new HashMap<>();
    expectedMap.put(PreFlightCheckMetadata.FQN,
        "templateInputs.spec.execution.steps.jiraApprovalTemplate.template.templateInputs.spec.connectorRef");
    expectedMap.put(PreFlightCheckMetadata.EXPRESSION, "<+input>.allowedValues(\"c1\", \"c2\")");
    EntityDetailProtoDTO expected4 =
        TemplateReferenceTestHelper.generateIdentifierRefWithUnknownScopeEntityDetailProto(ACCOUNT_ID, ORG_ID,
            PROJECT_ID, "<+input>.allowedValues(\"c1\", \"c2\")", expectedMap, EntityTypeProtoEnum.CONNECTORS);

    return asList(expected1, expected2, expected3, expected4);
  }

  private List<EntityDetail> getStageTemplateRestReferences() {
    List<EntityDetailProtoDTO> stageTemplateProtoReferences = getStageTemplateProtoReferences();
    return stageTemplateProtoReferences.stream()
        .map(reference -> entityDetailProtoToRestMapper.createEntityDetailDTO(reference))
        .collect(Collectors.toList());
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testGetNestedTemplateReferencesForPipeline() throws IOException {
    String filename = "pipeline-with-references.yaml";
    String pipelineYaml = readFile(filename);

    when(templateServiceHelper.getTemplateOrThrowExceptionIfInvalid(
             ACCOUNT_ID, ORG_ID, PROJECT_ID, "approvalTemplate", "", false, false))
        .thenReturn(Optional.of(TemplateEntity.builder().identifier("approvalTemplate").versionLabel("1").build()));

    List<EntitySetupUsageDTO> setupUsages =
        getStageTemplateRestReferences()
            .stream()
            .map(referredEntity -> EntitySetupUsageDTO.builder().referredEntity(referredEntity).build())
            .collect(Collectors.toList());

    when(templateSetupUsageHelper.getReferencesOfTemplate("accountId", "orgId", "projectId", "stageTemplate", "1"))
        .thenReturn(setupUsages);
    when(templateSetupUsageHelper.getReferencesOfTemplate("accountId", "orgId", "projectId", "approvalTemplate", "1"))
        .thenReturn(Collections.emptyList());

    List<EntityDetailProtoDTO> referredEntities =
        templateReferenceHelper.getNestedTemplateReferences(ACCOUNT_ID, ORG_ID, PROJECT_ID, pipelineYaml, false);

    assertThat(referredEntities).isNotNull().hasSize(3);
    Map<String, String> metadata1 = new HashMap<>();
    Map<String, String> metadata2 = new HashMap<>();
    metadata1.put("fqn", "pipeline.stages.qaStage.template");
    metadata2.put("fqn", "pipeline.stages.qaStage2.spec.execution.steps.approval.template");
    EntityDetailProtoDTO expected1 = TemplateReferenceTestHelper.generateTemplateRefEntityDetailProto(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, "stageTemplate", "1", metadata1);
    EntityDetailProtoDTO expected2 = TemplateReferenceTestHelper.generateTemplateRefEntityDetailProto(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, "approvalTemplate", STABLE_VERSION, metadata2);
    Map<String, String> expectedMap = new HashMap<>();
    expectedMap.put(PreFlightCheckMetadata.FQN,
        "pipeline.stages.qaStage.template.templateInputs.spec.execution.steps.jiraApprovalTemplate.template.templateInputs.spec.connectorRef");
    EntityDetailProtoDTO expected3 = TemplateReferenceTestHelper.generateIdentifierRefEntityDetailProto(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, "c1", expectedMap, EntityTypeProtoEnum.CONNECTORS);
    assertThat(referredEntities).containsExactlyInAnyOrder(expected1, expected2, expected3);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void shouldThrowErrorIfReferenceProvidedWithInvalidScope() throws IOException {
    String filename = "pipeline-with-references.yaml";
    String pipelineYaml = readFile(filename);

    assertThatThrownBy(
        () -> templateReferenceHelper.getNestedTemplateReferences(ACCOUNT_ID, ORG_ID, null, pipelineYaml, false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("The project level template cannot be used at org level. Ref: [stageTemplate]");
  }
}
