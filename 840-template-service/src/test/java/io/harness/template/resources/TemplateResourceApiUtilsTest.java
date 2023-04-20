/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.resources;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.rule.OwnerRule.ADITHYA;
import static io.harness.rule.OwnerRule.TARUN_UBA;
import static io.harness.template.resources.NGTemplateResource.TEMPLATE;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.encryption.Scope;
import io.harness.exception.InvalidRequestException;
import io.harness.git.model.ChangeType;
import io.harness.ng.core.template.TemplateEntityType;
import io.harness.pms.contracts.plan.YamlOutputProperties;
import io.harness.pms.contracts.service.VariableMergeResponseProto;
import io.harness.pms.contracts.service.VariableResponseMapValueProto;
import io.harness.pms.contracts.service.VariablesServiceGrpc;
import io.harness.pms.contracts.service.VariablesServiceGrpc.VariablesServiceBlockingStub;
import io.harness.pms.contracts.service.VariablesServiceRequest;
import io.harness.rule.Owner;
import io.harness.spec.server.template.v1.model.GitImportDetails;
import io.harness.spec.server.template.v1.model.TemplateImportRequestDTO;
import io.harness.spec.server.template.v1.model.TemplateImportResponseBody;
import io.harness.spec.server.template.v1.model.TemplateMetadataSummaryResponse;
import io.harness.spec.server.template.v1.model.TemplateResponse;
import io.harness.spec.server.template.v1.model.TemplateUpdateStableResponse;
import io.harness.spec.server.template.v1.model.TemplateWithInputsResponse;
import io.harness.template.beans.PermissionTypes;
import io.harness.template.entity.TemplateEntity;
import io.harness.template.entity.TemplateEntity.TemplateEntityKeys;
import io.harness.template.services.NGTemplateService;

import com.google.common.io.Resources;
import com.google.inject.Inject;
import io.grpc.ManagedChannel;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import io.grpc.testing.GrpcCleanupRule;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Response;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.AdditionalAnswers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@OwnedBy(CDC)
public class TemplateResourceApiUtilsTest extends CategoryTest {
  @Rule public final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();

  @InjectMocks TemplateResourceApiUtils templateResourceApiUtils;
  @Mock NGTemplateService templateService;
  @Mock AccessControlClient accessControlClient;
  @Inject VariablesServiceBlockingStub variablesServiceBlockingStub;
  @Mock TemplateResourceApiMapper templateResourceApiMapper;

  private final String ACCOUNT_ID = "account_id";
  private final String ORG_IDENTIFIER = "orgId";
  private final String PROJ_IDENTIFIER = "projId";
  private final String TEMPLATE_IDENTIFIER = "template1";
  private final String TEMPLATE_VERSION_LABEL = "version1";
  private final String TEMPLATE_CHILD_TYPE = "ShellScript";
  private final String INPUT_YAML = "Input YAML not requested";
  private String yaml;

  TemplateEntity entity;
  TemplateEntity entityWithMongoVersion;

  private final VariablesServiceGrpc.VariablesServiceImplBase serviceImpl =
      mock(VariablesServiceGrpc.VariablesServiceImplBase.class,
          AdditionalAnswers.delegatesTo(new VariablesServiceGrpc.VariablesServiceImplBase() {
            @Override
            public void getVariables(
                VariablesServiceRequest request, StreamObserver<VariableMergeResponseProto> responseObserver) {
              Map<String, VariableResponseMapValueProto> metadataMap = new HashMap<>();
              metadataMap.put("v1",
                  VariableResponseMapValueProto.newBuilder()
                      .setYamlOutputProperties(YamlOutputProperties.newBuilder().build())
                      .build());
              VariableMergeResponseProto variableMergeResponseProto =
                  VariableMergeResponseProto.newBuilder().setYaml("temp1").putAllMetadataMap(metadataMap).build();
              responseObserver.onNext(variableMergeResponseProto);
              responseObserver.onCompleted();
            }
          }));
  private AutoCloseable mocks;

  @After
  public void tearDown() throws Exception {
    if (mocks != null) {
      mocks.close();
    }
  }

  @Before
  public void setUp() throws IOException {
    mocks = MockitoAnnotations.openMocks(this);

    // Generate a unique in-process server name.
    String serverName = InProcessServerBuilder.generateName();
    // Create a server, add service, start, and register for automatic graceful shutdown.
    grpcCleanup.register(
        InProcessServerBuilder.forName(serverName).directExecutor().addService(serviceImpl).build().start());
    // Create a client channel and register for automatic graceful shutdown.
    ManagedChannel channel = grpcCleanup.register(InProcessChannelBuilder.forName(serverName).directExecutor().build());
    // Create a VariablesStub using the in-process channel;
    variablesServiceBlockingStub = VariablesServiceGrpc.newBlockingStub(channel);

    ClassLoader classLoader = this.getClass().getClassLoader();
    String filename = "template.yaml";
    yaml = Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);

    entity = TemplateEntity.builder()
                 .accountId(ACCOUNT_ID)
                 .orgIdentifier(ORG_IDENTIFIER)
                 .projectIdentifier(PROJ_IDENTIFIER)
                 .identifier(TEMPLATE_IDENTIFIER)
                 .name(TEMPLATE_IDENTIFIER)
                 .versionLabel(TEMPLATE_VERSION_LABEL)
                 .yaml(yaml)
                 .description("")
                 .templateEntityType(TemplateEntityType.STEP_TEMPLATE)
                 .childType(TEMPLATE_CHILD_TYPE)
                 .fullyQualifiedIdentifier("account_id/orgId/projId/template1/version1/")
                 .templateScope(Scope.PROJECT)
                 .build();

    entityWithMongoVersion = TemplateEntity.builder()
                                 .accountId(ACCOUNT_ID)
                                 .orgIdentifier(ORG_IDENTIFIER)
                                 .projectIdentifier(PROJ_IDENTIFIER)
                                 .identifier(TEMPLATE_IDENTIFIER)
                                 .name(TEMPLATE_IDENTIFIER)
                                 .versionLabel(TEMPLATE_VERSION_LABEL)
                                 .yaml(yaml)
                                 .templateEntityType(TemplateEntityType.STEP_TEMPLATE)
                                 .childType(TEMPLATE_CHILD_TYPE)
                                 .fullyQualifiedIdentifier("account_id/orgId/projId/template1/version1/")
                                 .templateScope(Scope.PROJECT)
                                 .version(1L)
                                 .build();
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testCreateTemplate() {
    doReturn(entityWithMongoVersion).when(templateService).create(entity, false, "", false);
    TemplateResponse templateResponse = new TemplateResponse();
    templateResponse.setAccount(entity.getAccountId());
    templateResponse.setOrg(entity.getOrgIdentifier());
    templateResponse.setProject(entity.getProjectIdentifier());
    templateResponse.setIdentifier(entity.getIdentifier());
    templateResponse.setName(entity.getName());
    templateResponse.setDescription(entity.getDescription());
    templateResponse.setYaml(entity.getYaml());
    templateResponse.setVersionLabel(entity.getVersionLabel());
    TemplateResponse.EntityTypeEnum templateEntityType =
        TemplateResponse.EntityTypeEnum.fromValue(entity.getTemplateEntityType().toString());
    templateResponse.setEntityType(templateEntityType);
    templateResponse.setChildType(entity.getChildType());
    templateResponse.setUpdated(entity.getLastUpdatedAt());
    templateResponse.setConnectorRef(entity.getConnectorRef());
    templateResponse.setStableTemplate(entity.isStableTemplate());
    when(templateResourceApiMapper.toTemplateResponse(any())).thenReturn(templateResponse);
    Response response =
        templateResourceApiUtils.createTemplate(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, yaml, false, "");
    assertThat(response.getEntity()).isNotNull();
    verify(accessControlClient)
        .checkForAccessOrThrow(ResourceScope.of(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER),
            Resource.of(TEMPLATE, null), PermissionTypes.TEMPLATE_EDIT_PERMISSION);
    TemplateResponse templateResponseFinal = (TemplateResponse) response.getEntity();
    assertThat(response.getEntityTag().getValue()).isEqualTo("1");
    assertEquals(templateResponseFinal.getIdentifier(), TEMPLATE_IDENTIFIER);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testImportTemplates() {
    GitImportDetails gitImportDetails = new GitImportDetails();
    gitImportDetails.isForceImport(false);
    TemplateImportRequestDTO templateImportRequestDTO = new TemplateImportRequestDTO();
    doReturn(TemplateEntity.builder().identifier(TEMPLATE_IDENTIFIER).build())
        .when(templateService)
        .importTemplateFromRemote(any(), any(), any(), any(), any(), anyBoolean());
    Response response = templateResourceApiUtils.importTemplate(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, TEMPLATE_IDENTIFIER, gitImportDetails, templateImportRequestDTO);
    TemplateImportResponseBody responseBody = (TemplateImportResponseBody) response.getEntity();
    assertEquals(responseBody.getTemplateIdentifier(), TEMPLATE_IDENTIFIER);
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testGetTemplate() {
    doReturn(Optional.of(entityWithMongoVersion))
        .when(templateService)
        .get(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, TEMPLATE_IDENTIFIER, TEMPLATE_VERSION_LABEL, false, false);
    TemplateWithInputsResponse templateWithInputsResponse = new TemplateWithInputsResponse();
    TemplateResponse templateResponse = new TemplateResponse();
    templateResponse.setAccount(entity.getAccountId());
    templateResponse.setOrg(entity.getOrgIdentifier());
    templateResponse.setProject(entity.getProjectIdentifier());
    templateResponse.setIdentifier(entity.getIdentifier());
    templateResponse.setName(entity.getName());
    templateResponse.setDescription(entity.getDescription());
    templateResponse.setYaml(entity.getYaml());
    templateResponse.setVersionLabel(entity.getVersionLabel());
    TemplateResponse.EntityTypeEnum templateEntityType =
        TemplateResponse.EntityTypeEnum.fromValue(entity.getTemplateEntityType().toString());
    templateResponse.setEntityType(templateEntityType);
    templateResponse.setChildType(entity.getChildType());
    templateResponse.setUpdated(entity.getLastUpdatedAt());
    templateResponse.setConnectorRef(entity.getConnectorRef());
    templateResponse.setStableTemplate(entity.isStableTemplate());
    templateWithInputsResponse.setTemplate(templateResponse);
    templateWithInputsResponse.setInputs("Input YAML not requested");
    when(templateResourceApiMapper.toTemplateResponseDefault(any())).thenReturn(templateWithInputsResponse);
    Response response = templateResourceApiUtils.getTemplate(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
        TEMPLATE_IDENTIFIER, TEMPLATE_VERSION_LABEL, false, null, null, null, null, null, null, false);
    TemplateWithInputsResponse templateResponseInput = (TemplateWithInputsResponse) response.getEntity();
    assertThat(response.getEntityTag().getValue()).isEqualTo("1");
    assertEquals(templateResponseInput.getTemplate().getIdentifier(), TEMPLATE_IDENTIFIER);
    assertEquals(templateResponseInput.getInputs(), INPUT_YAML);
    verify(accessControlClient)
        .checkForAccessOrThrow(ResourceScope.of(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER),
            Resource.of(TEMPLATE, TEMPLATE_IDENTIFIER), PermissionTypes.TEMPLATE_VIEW_PERMISSION);
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testGetTemplateWithInvalidTemplateIdentifier() {
    String incorrectTemplateIdentifier = "notTheIdentifierWeNeed";
    doReturn(Optional.empty())
        .when(templateService)
        .get(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, incorrectTemplateIdentifier, TEMPLATE_VERSION_LABEL, false,
            false);
    assertThatThrownBy(
        ()
            -> templateResourceApiUtils.getTemplate(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
                incorrectTemplateIdentifier, TEMPLATE_VERSION_LABEL, false, null, null, null, null, null, null, false))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testUpdateTemplate() {
    doReturn(entityWithMongoVersion).when(templateService).updateTemplateEntity(entity, ChangeType.MODIFY, false, "");
    TemplateResponse templateResponse = new TemplateResponse();
    templateResponse.setAccount(entity.getAccountId());
    templateResponse.setOrg(entity.getOrgIdentifier());
    templateResponse.setProject(entity.getProjectIdentifier());
    templateResponse.setIdentifier(entity.getIdentifier());
    templateResponse.setName(entity.getName());
    templateResponse.setDescription(entity.getDescription());
    templateResponse.setYaml(entity.getYaml());
    templateResponse.setVersionLabel(entity.getVersionLabel());
    TemplateResponse.EntityTypeEnum templateEntityType =
        TemplateResponse.EntityTypeEnum.fromValue(entity.getTemplateEntityType().toString());
    templateResponse.setEntityType(templateEntityType);
    templateResponse.setChildType(entity.getChildType());
    templateResponse.setUpdated(entity.getLastUpdatedAt());
    templateResponse.setConnectorRef(entity.getConnectorRef());
    templateResponse.setStableTemplate(entity.isStableTemplate());
    when(templateResourceApiMapper.toTemplateResponse(any())).thenReturn(templateResponse);
    Response response = templateResourceApiUtils.updateTemplate(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
        TEMPLATE_IDENTIFIER, TEMPLATE_VERSION_LABEL, null, yaml, false, "");
    TemplateResponse templateResponseFinal = (TemplateResponse) response.getEntity();
    verify(accessControlClient)
        .checkForAccessOrThrow(ResourceScope.of(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER),
            Resource.of(TEMPLATE, TEMPLATE_IDENTIFIER), PermissionTypes.TEMPLATE_EDIT_PERMISSION);
    assertEquals(templateResponseFinal.getIdentifier(), TEMPLATE_IDENTIFIER);
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testUpdateStableTemplate() {
    doReturn(entityWithMongoVersion)
        .when(templateService)
        .updateStableTemplateVersion(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, TEMPLATE_IDENTIFIER, TEMPLATE_VERSION_LABEL, "");
    Response response = templateResourceApiUtils.updateStableTemplate(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, TEMPLATE_IDENTIFIER, TEMPLATE_VERSION_LABEL, null, "");
    TemplateUpdateStableResponse templateResponse = (TemplateUpdateStableResponse) response.getEntity();
    assertThat(templateResponse.getStableVersion()).isEqualTo(TEMPLATE_VERSION_LABEL);
    verify(accessControlClient)
        .checkForAccessOrThrow(ResourceScope.of(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER),
            Resource.of(TEMPLATE, TEMPLATE_IDENTIFIER), PermissionTypes.TEMPLATE_EDIT_PERMISSION);
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testUpdateTemplateWithWrongIdentifier() {
    String incorrectPipelineIdentifier = "notTheIdentifierWeNeed";
    assertThatThrownBy(()
                           -> templateResourceApiUtils.updateTemplate(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
                               incorrectPipelineIdentifier, TEMPLATE_VERSION_LABEL, null, yaml, false, ""))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testDeleteTemplate() {
    doReturn(true)
        .when(templateService)
        .delete(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, TEMPLATE_IDENTIFIER, TEMPLATE_VERSION_LABEL, null, "", false);
    Response response = templateResourceApiUtils.deleteTemplate(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, TEMPLATE_IDENTIFIER, TEMPLATE_VERSION_LABEL, null, false);
    assertEquals(response.getStatus(), Response.Status.NO_CONTENT.getStatusCode());
    verify(accessControlClient)
        .checkForAccessOrThrow(ResourceScope.of(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER),
            Resource.of(TEMPLATE, TEMPLATE_IDENTIFIER), PermissionTypes.TEMPLATE_DELETE_PERMISSION);
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testGetListOfTemplates() {
    Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, TemplateEntityKeys.createdAt));
    PageImpl<TemplateEntity> templateEntities =
        new PageImpl<>(Collections.singletonList(entityWithMongoVersion), pageable, 1);
    TemplateMetadataSummaryResponse templateMetadataSummaryResponse = new TemplateMetadataSummaryResponse();
    templateMetadataSummaryResponse.setAccount(entity.getAccountId());
    templateMetadataSummaryResponse.setOrg(entity.getOrgIdentifier());
    templateMetadataSummaryResponse.setProject(entity.getProjectIdentifier());
    templateMetadataSummaryResponse.setIdentifier(entity.getIdentifier());
    templateMetadataSummaryResponse.setName(entity.getName());
    templateMetadataSummaryResponse.setDescription(entity.getDescription());
    templateMetadataSummaryResponse.setVersionLabel(entity.getVersionLabel());
    TemplateMetadataSummaryResponse.EntityTypeEnum templateEntityType =
        TemplateMetadataSummaryResponse.EntityTypeEnum.fromValue(entity.getTemplateEntityType().toString());
    templateMetadataSummaryResponse.setEntityType(templateEntityType);
    templateMetadataSummaryResponse.setChildType(entity.getChildType());
    templateMetadataSummaryResponse.setUpdated(entity.getLastUpdatedAt());
    templateMetadataSummaryResponse.setConnectorRef(entity.getConnectorRef());
    templateMetadataSummaryResponse.setStableTemplate(entity.isStableTemplate());
    when(templateResourceApiMapper.mapToTemplateMetadataResponse(any())).thenReturn(templateMetadataSummaryResponse);
    doReturn(templateEntities).when(templateService).listTemplateMetadata(any(), any(), any(), any(), any());
    Response response = templateResourceApiUtils.getTemplates(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, 0, 25, null,
        null, null, "ALL", false, null, null, null, Collections.singletonList("Stage"), null);
    List<TemplateMetadataSummaryResponse> templates = (List<TemplateMetadataSummaryResponse>) response.getEntity();
    assertThat(templates).isNotEmpty().hasSize(1);

    TemplateMetadataSummaryResponse responseDTO = templates.get(0);
    assertThat(responseDTO.getIdentifier()).isEqualTo(TEMPLATE_IDENTIFIER);
    assertThat(responseDTO.getName()).isEqualTo(TEMPLATE_IDENTIFIER);
    verify(accessControlClient)
        .checkForAccessOrThrow(ResourceScope.of(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER),
            Resource.of(TEMPLATE, null), PermissionTypes.TEMPLATE_VIEW_PERMISSION);
  }
}
