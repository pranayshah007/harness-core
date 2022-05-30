/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.template.helpers;

import static io.harness.ng.core.template.TemplateEntityConstants.STAGE;
import static io.harness.rule.OwnerRule.INDER;
import static io.harness.template.beans.NGTemplateConstants.STABLE_VERSION;
import static io.harness.template.helpers.TemplateReferenceTestHelper.ACCOUNT_ID;
import static io.harness.template.helpers.TemplateReferenceTestHelper.ORG_ID;
import static io.harness.template.helpers.TemplateReferenceTestHelper.PROJECT_ID;
import static io.harness.template.helpers.TemplateReferenceTestHelper.connectorEntityDetailProto_StageTemplate;
import static io.harness.template.helpers.TemplateReferenceTestHelper.connectorEntityDetailProto_StepTemplate;
import static io.harness.template.helpers.TemplateReferenceTestHelper.generateIdentifierRefWithUnknownScope;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.EntityType;
import io.harness.TemplateServiceTestBase;
import io.harness.account.AccountClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.eventsframework.schemas.entity.IdentifierRefProtoDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.entitydetail.EntityDetailProtoToRestMapper;
import io.harness.ng.core.entitysetupusage.dto.EntitySetupUsageDTO;
import io.harness.ng.core.template.TemplateEntityType;
import io.harness.ng.core.template.TemplateReferenceRequestDTO;
import io.harness.pms.contracts.service.EntityReferenceServiceGrpc;
import io.harness.pms.gitsync.PmsGitSyncHelper;
import io.harness.preflight.PreFlightCheckMetadata;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.template.entity.TemplateEntity;
import io.harness.template.handler.TemplateYamlConversionHandler;
import io.harness.template.handler.TemplateYamlConversionHandlerRegistry;
import io.harness.template.services.NGTemplateServiceHelper;

import com.google.common.io.Resources;
import com.google.inject.Inject;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.testing.GrpcCleanupRule;
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
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(HarnessTeam.CDC)
public class TemplateReferenceHelperTest extends TemplateServiceTestBase {
  @Rule public final GrpcCleanupRule grpcCleanupRule = new GrpcCleanupRule();

  EntityReferenceServiceGrpc.EntityReferenceServiceBlockingStub entityReferenceServiceBlockingStub;
  @Inject TemplateYamlConversionHelper templateYamlConversionHelper;
  @Mock PmsGitSyncHelper pmsGitSyncHelper;
  @Mock TemplateSetupUsageHelper templateSetupUsageHelper;
  @Mock NGTemplateServiceHelper templateServiceHelper;
  @Inject TemplateYamlConversionHandlerRegistry templateYamlConversionHandlerRegistry;
  @Inject EntityDetailProtoToRestMapper entityDetailProtoToRestMapper;
  @Mock AccountClient accountClient;

  TemplateReferenceHelper templateReferenceHelper;

  @Before
  public void setup() throws IOException {
    String serverName = InProcessServerBuilder.generateName();
    grpcCleanupRule.register(InProcessServerBuilder.forName(serverName)
                                 .directExecutor()
                                 .addService(new TemplateReferenceTestHelper())
                                 .build()
                                 .start());
    entityReferenceServiceBlockingStub = EntityReferenceServiceGrpc.newBlockingStub(
        grpcCleanupRule.register(InProcessChannelBuilder.forName(serverName).directExecutor().build()));

    templateReferenceHelper = new TemplateReferenceHelper(entityReferenceServiceBlockingStub,
        templateYamlConversionHelper, pmsGitSyncHelper, templateServiceHelper, templateSetupUsageHelper, accountClient);
    Call<RestResponse<Boolean>> ffCall = mock(Call.class);
    when(accountClient.isFeatureFlagEnabled(eq(FeatureName.NG_TEMPLATE_REFERENCES_SUPPORT.name()), anyString()))
        .thenReturn(ffCall);
    when(ffCall.execute()).thenReturn(Response.success(new RestResponse<>(true)));
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
  public void testConvertFQNsOfReferredEntities_StepTemplate() {
    List<EntityDetailProtoDTO> referredEntitiesWithModifiedFQNs = templateReferenceHelper.correctFQNsOfReferredEntities(
        Collections.singletonList(connectorEntityDetailProto_StepTemplate), TemplateEntityType.STEP_TEMPLATE);

    assertThat(referredEntitiesWithModifiedFQNs).isNotNull().hasSize(1);
    assertThat(referredEntitiesWithModifiedFQNs.get(0)).isNotNull();
    assertThat(referredEntitiesWithModifiedFQNs.get(0).getType()).isEqualTo(EntityTypeProtoEnum.CONNECTORS);

    IdentifierRefProtoDTO expectedIdentifierRef =
        connectorEntityDetailProto_StepTemplate.getIdentifierRef()
            .toBuilder()
            .clearMetadata()
            .putMetadata(PreFlightCheckMetadata.FQN, "templateInputs.spec.connector")
            .build();
    assertThat(referredEntitiesWithModifiedFQNs.get(0).getIdentifierRef()).isNotNull().isEqualTo(expectedIdentifierRef);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testConvertFQNsOfReferredEntities_OtherTemplates() {
    List<EntityDetailProtoDTO> referredEntitiesWithModifiedFQNs = templateReferenceHelper.correctFQNsOfReferredEntities(
        Collections.singletonList(connectorEntityDetailProto_StageTemplate), TemplateEntityType.STAGE_TEMPLATE);

    assertThat(referredEntitiesWithModifiedFQNs).isNotNull().hasSize(1);
    assertThat(referredEntitiesWithModifiedFQNs.get(0)).isNotNull();
    assertThat(referredEntitiesWithModifiedFQNs.get(0).getType()).isEqualTo(EntityTypeProtoEnum.CONNECTORS);

    IdentifierRefProtoDTO expectedIdentifierRef =
        connectorEntityDetailProto_StepTemplate.getIdentifierRef()
            .toBuilder()
            .clearMetadata()
            .putMetadata(PreFlightCheckMetadata.FQN, "templateInputs.spec.execution.steps.jira.spec.connector")
            .build();
    assertThat(referredEntitiesWithModifiedFQNs.get(0).getIdentifierRef()).isNotNull().isEqualTo(expectedIdentifierRef);
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

    List<EntityDetailProtoDTO> templateReferences = templateReferenceHelper.getNestedTemplateReferences(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, TemplateReferenceRequestDTO.builder().yaml(yaml).build(), true);
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

    templateYamlConversionHandlerRegistry.register(STAGE, new TemplateYamlConversionHandler());
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

    templateReferenceHelper.populateTemplateReferences(templateEntity);

    ArgumentCaptor<List> referredEntitiesArgumentCapture = ArgumentCaptor.forClass(List.class);
    verify(templateSetupUsageHelper)
        .publishSetupUsageEvent(eq(templateEntity), referredEntitiesArgumentCapture.capture());
    List<EntityDetailProtoDTO> referredEntities = referredEntitiesArgumentCapture.getValue();
    assertThat(referredEntities).isNotNull().hasSize(4);
    assertThat(referredEntities).containsExactlyInAnyOrderElementsOf(getStageTemplateProtoReferences());
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

    when(templateServiceHelper.getOrThrowExceptionIfInvalid(
             ACCOUNT_ID, ORG_ID, PROJECT_ID, "approvalTemplate", "", false))
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

    List<EntityDetailProtoDTO> referredEntities = templateReferenceHelper.getNestedTemplateReferences(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, TemplateReferenceRequestDTO.builder().yaml(pipelineYaml).build(), false);

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

    assertThatThrownBy(()
                           -> templateReferenceHelper.getNestedTemplateReferences(ACCOUNT_ID, ORG_ID, null,
                               TemplateReferenceRequestDTO.builder().yaml(pipelineYaml).build(), false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("ProjectIdentifier cannot be empty for PROJECT scope");
  }
}
