/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.resources;

import static io.harness.rule.OwnerRule.SHIVAM;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import io.harness.CategoryTest;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.category.element.UnitTests;
import io.harness.customDeployment.remote.CustomDeploymentResourceClient;
import io.harness.encryption.Scope;
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.interceptor.GitEntityCreateInfoDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.template.TemplateEntityType;
import io.harness.pms.contracts.plan.YamlOutputProperties;
import io.harness.pms.contracts.service.VariableMergeResponseProto;
import io.harness.pms.contracts.service.VariableResponseMapValueProto;
import io.harness.pms.contracts.service.VariablesServiceGrpc;
import io.harness.pms.contracts.service.VariablesServiceGrpc.VariablesServiceBlockingStub;
import io.harness.pms.contracts.service.VariablesServiceRequest;
import io.harness.rule.Owner;
import io.harness.template.entity.GlobalTemplateEntity;
import io.harness.template.mappers.NGTemplateDtoMapper;
import io.harness.template.resources.beans.TemplateWrapperResponseDTO;
import io.harness.template.services.NGGlobalTemplateService;
import io.harness.template.services.TemplateVariableCreatorFactory;

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
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.AdditionalAnswers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class NGGlobalTemplateResourceTest extends CategoryTest {
  @Rule public final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();

  NGGlobalTemplateResource ngGlobalTemplateResource;
  @Mock NGGlobalTemplateService templateService;
  @Mock AccessControlClient accessControlClient;
  @Inject VariablesServiceBlockingStub variablesServiceBlockingStub;
  @Mock CustomDeploymentResourceClient customDeploymentResourceClient;
  @Mock TemplateVariableCreatorFactory templateVariableCreatorFactory;

  private final String ACCOUNT_ID = "account_id";
  private final String ORG_IDENTIFIER = "orgId";
  private final String PROJ_IDENTIFIER = "projId";
  private final String TEMPLATE_IDENTIFIER = "template1";
  private final String TEMPLATE_VERSION_LABEL = "version1";
  private final String TEMPLATE_CHILD_TYPE = "ShellScript";
  private String yaml;

  GlobalTemplateEntity entity;
  GlobalTemplateEntity entityWithMongoVersion;
  private AutoCloseable mocks;

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

    ngGlobalTemplateResource = new NGGlobalTemplateResourceImpl(
        templateService, customDeploymentResourceClient, templateVariableCreatorFactory);
    ClassLoader classLoader = this.getClass().getClassLoader();
    String filename = "template.yaml";
    yaml = Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);

    entity = GlobalTemplateEntity.builder()
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

    entityWithMongoVersion = GlobalTemplateEntity.builder()
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

  @After
  public void tearDown() throws Exception {
    if (mocks != null) {
      mocks.close();
    }
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testCreateTemplate() {
    doReturn(Collections.singletonList(
                 TemplateWrapperResponseDTO.builder()
                     .isValid(true)
                     .templateResponseDTO(NGTemplateDtoMapper.writeTemplateResponseDto(entityWithMongoVersion))
                     .build()))
        .when(templateService)
        .createUpdateGlobalTemplate(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, false, "comments", true, "CONNECTOR", "event");
    ResponseDTO<List<TemplateWrapperResponseDTO>> responseDTO =
        ngGlobalTemplateResource.createAndUpdate(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "CONNECTOR", "master",
            GitEntityCreateInfoDTO.builder().build(), "event", false, "comments", true);
    assertThat(responseDTO.getData()).isNotNull();
    assertThat(responseDTO.getData().get(0).isValid()).isTrue();
    assertThat(responseDTO.getData().get(0).getTemplateResponseDTO().getVersion()).isEqualTo(1L);
    assertThat(responseDTO.getData().get(0).getTemplateResponseDTO().getIdentifier()).isEqualTo(TEMPLATE_IDENTIFIER);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testCreateTemplateFailed() {
    doReturn(Collections.EMPTY_LIST)
        .when(templateService)
        .createUpdateGlobalTemplate(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, false, "comments", true, "CONNECTOR", "event");
    assertThatThrownBy(
        ()
            -> ngGlobalTemplateResource.createAndUpdate(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "CONNECTOR",
                "master", GitEntityCreateInfoDTO.builder().build(), "event", false, "comments", true))
        .isInstanceOf(InvalidRequestException.class);
  }
}
