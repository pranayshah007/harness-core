/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.helpers.crud;

import static io.harness.rule.OwnerRule.INDER;
import static io.harness.rule.OwnerRule.TMACARI;
import static io.harness.template.helpers.TemplateReferenceTestHelper.ACCOUNT_ID;
import static io.harness.template.helpers.TemplateReferenceTestHelper.ORG_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.TemplateServiceTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.encryption.Scope;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.eventsframework.schemas.entity.IdentifierRefProtoDTO;
import io.harness.eventsframework.schemas.entity.InfraDefinitionReferenceProtoDTO;
import io.harness.exception.InvalidIdentifierRefException;
import io.harness.exception.ngexception.NGTemplateException;
import io.harness.ng.core.template.TemplateEntityType;
import io.harness.pms.contracts.service.EntityReferenceServiceGrpc;
import io.harness.pms.gitsync.PmsGitSyncHelper;
import io.harness.preflight.PreFlightCheckMetadata;
import io.harness.rule.Owner;
import io.harness.template.entity.TemplateEntity;
import io.harness.template.helpers.TemplateReferenceTestHelper;

import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.testing.GrpcCleanupRule;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.CDC)
public class PipelineTemplateCrudHelperTest extends TemplateServiceTestBase {
  @Rule public final GrpcCleanupRule grpcCleanupRule = new GrpcCleanupRule();

  EntityReferenceServiceGrpc.EntityReferenceServiceBlockingStub entityReferenceServiceBlockingStub;
  @Mock PmsGitSyncHelper pmsGitSyncHelper;
  @InjectMocks PipelineTemplateCrudHelper pipelineTemplateCrudHelper;

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

    pipelineTemplateCrudHelper = new PipelineTemplateCrudHelper(pmsGitSyncHelper, entityReferenceServiceBlockingStub);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testConvertFQNsOfReferredEntities_StepTemplate() {
    List<EntityDetailProtoDTO> referredEntitiesWithModifiedFQNs =
        pipelineTemplateCrudHelper.correctFQNsOfReferredEntities(
            Collections.singletonList(TemplateReferenceTestHelper.connectorEntityDetailProto_StepTemplate),
            TemplateEntityType.STEP_TEMPLATE);

    assertThat(referredEntitiesWithModifiedFQNs).isNotNull().hasSize(1);
    assertThat(referredEntitiesWithModifiedFQNs.get(0)).isNotNull();
    assertThat(referredEntitiesWithModifiedFQNs.get(0).getType()).isEqualTo(EntityTypeProtoEnum.CONNECTORS);

    IdentifierRefProtoDTO expectedIdentifierRef =
        TemplateReferenceTestHelper.connectorEntityDetailProto_StepTemplate.getIdentifierRef()
            .toBuilder()
            .clearMetadata()
            .putMetadata(PreFlightCheckMetadata.FQN, "templateInputs.spec.connector")
            .build();
    assertThat(referredEntitiesWithModifiedFQNs.get(0).getIdentifierRef()).isNotNull().isEqualTo(expectedIdentifierRef);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testConvertFQNsOfReferredEntitiesInfrastructure_StepTemplate() {
    List<EntityDetailProtoDTO> referredEntitiesWithModifiedFQNs =
        pipelineTemplateCrudHelper.correctFQNsOfReferredEntities(
            Collections.singletonList(TemplateReferenceTestHelper.infrastructureEntityDetailProto_StepTemplate),
            TemplateEntityType.STEP_TEMPLATE);

    assertThat(referredEntitiesWithModifiedFQNs).isNotNull().hasSize(1);
    assertThat(referredEntitiesWithModifiedFQNs.get(0)).isNotNull();
    assertThat(referredEntitiesWithModifiedFQNs.get(0).getType()).isEqualTo(EntityTypeProtoEnum.INFRASTRUCTURE);

    InfraDefinitionReferenceProtoDTO infraDefinitionRef =
        TemplateReferenceTestHelper.infrastructureEntityDetailProto_StepTemplate.getInfraDefRef()
            .toBuilder()
            .clearMetadata()
            .putMetadata(
                PreFlightCheckMetadata.FQN, "templateInputs.stage.spec.environment.infrastructureDefinitions.infraid")
            .build();
    assertThat(referredEntitiesWithModifiedFQNs.get(0).getInfraDefRef()).isNotNull().isEqualTo(infraDefinitionRef);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testConvertFQNsOfReferredEntities_OtherTemplates() {
    List<EntityDetailProtoDTO> referredEntitiesWithModifiedFQNs =
        pipelineTemplateCrudHelper.correctFQNsOfReferredEntities(
            Collections.singletonList(TemplateReferenceTestHelper.connectorEntityDetailProto_StageTemplate),
            TemplateEntityType.STAGE_TEMPLATE);

    assertThat(referredEntitiesWithModifiedFQNs).isNotNull().hasSize(1);
    assertThat(referredEntitiesWithModifiedFQNs.get(0)).isNotNull();
    assertThat(referredEntitiesWithModifiedFQNs.get(0).getType()).isEqualTo(EntityTypeProtoEnum.CONNECTORS);

    IdentifierRefProtoDTO expectedIdentifierRef =
        TemplateReferenceTestHelper.connectorEntityDetailProto_StepTemplate.getIdentifierRef()
            .toBuilder()
            .clearMetadata()
            .putMetadata(PreFlightCheckMetadata.FQN, "templateInputs.spec.execution.steps.jira.spec.connector")
            .build();
    assertThat(referredEntitiesWithModifiedFQNs.get(0).getIdentifierRef()).isNotNull().isEqualTo(expectedIdentifierRef);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void shouldThrowInvalidIdentifierRefExceptionForOutOfScopeReferences() {
    TemplateEntity templateEntity = TemplateEntity.builder()
                                        .accountId(ACCOUNT_ID)
                                        .orgIdentifier(ORG_ID)
                                        .versionLabel("v1")
                                        .templateScope(Scope.ORG)
                                        .yaml(TemplateReferenceTestHelper.INVALID_IDENTIFIER_REF_YAML)
                                        .templateEntityType(TemplateEntityType.STAGE_TEMPLATE)
                                        .build();

    assertThatThrownBy(()
                           -> pipelineTemplateCrudHelper.getReferences(
                               templateEntity, TemplateReferenceTestHelper.DUMMY_INVALID_IDENTIFIER_REF_YAML))
        .isInstanceOf(InvalidIdentifierRefException.class)
        .hasMessage(
            "Unable to save to ORG. Template can be saved to ORG only when all the referenced entities are available in the scope.");
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void shouldThrowExceptionOnErrorWhileCalculatingReferences() {
    TemplateEntity templateEntity = TemplateEntity.builder()
                                        .identifier("template1")
                                        .accountId(ACCOUNT_ID)
                                        .orgIdentifier(ORG_ID)
                                        .versionLabel("v1")
                                        .templateScope(Scope.ORG)
                                        .yaml(TemplateReferenceTestHelper.INVALID_YAML)
                                        .templateEntityType(TemplateEntityType.STAGE_TEMPLATE)
                                        .build();

    assertThatThrownBy(
        () -> pipelineTemplateCrudHelper.getReferences(templateEntity, TemplateReferenceTestHelper.DUMMY_INVALID_YAML))
        .isInstanceOf(NGTemplateException.class)
        .hasMessage(
            "Exception in calculating references for template with identifier template1 and version label v1: Some error1, Some error2");
  }
}
