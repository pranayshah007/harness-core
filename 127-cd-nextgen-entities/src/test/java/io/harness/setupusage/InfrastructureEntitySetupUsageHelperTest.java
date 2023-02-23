/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.setupusage;

import static io.harness.rule.OwnerRule.HINGER;
import static io.harness.rule.OwnerRule.TATHAGAT;
import static io.harness.rule.OwnerRule.YOGESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.EntityType;
import io.harness.beans.IdentifierRef;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.eventsframework.schemas.entity.InfraDefinitionReferenceProtoDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ReferencedEntityException;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.entitysetupusage.dto.EntitySetupUsageDTO;
import io.harness.ng.core.entitysetupusage.service.EntitySetupUsageService;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.ng.core.infrastructure.InfrastructureType;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.ng.core.setupusage.SetupUsageHelper;
import io.harness.rule.Owner;
import io.harness.utils.FullyQualifiedIdentifierHelper;
import io.harness.walktree.visitor.SimpleVisitorFactory;
import io.harness.walktree.visitor.entityreference.EntityReferenceExtractorVisitor;

import com.google.inject.Inject;
import com.google.protobuf.StringValue;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

public class InfrastructureEntitySetupUsageHelperTest extends CategoryTest {
  @Mock private SimpleVisitorFactory mockedFactory;
  @Mock private SetupUsageHelper setupUsageHelper;
  @Mock EntityReferenceExtractorVisitor mockedVisitor;
  @Mock EnvironmentService environmentService;

  @Mock EntitySetupUsageService setupUsageService;
  @InjectMocks @Inject private InfrastructureEntitySetupUsageHelper infraSetupUsageHelper;

  private static final String ACCOUNT = "ACCOUNT";
  private static final String ORG = "ORG";
  private static final String PROJECT = "PROJECT";
  private static final String ENV_NAME = "ENV_NAME";

  private AutoCloseable mocks;
  @Before
  public void setUp() {
    mocks = MockitoAnnotations.openMocks(this);
    doReturn(Optional.of(Environment.builder().name(ENV_NAME).build()))
        .when(environmentService)
        .get(anyString(), anyString(), anyString(), anyString(), anyBoolean());
  }

  @After
  public void tearDown() throws Exception {
    if (mocks != null) {
      mocks.close();
    }
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testUpdateSetupUsages() {
    final InfrastructureEntity infrastructure = getInfrastructureEntity();
    final EntityDetailProtoDTO referredEntityDetail =
        EntityDetailProtoDTO.newBuilder()
            .setInfraDefRef(
                InfraDefinitionReferenceProtoDTO.newBuilder().setIdentifier(StringValue.of("connectorId")).build())
            .setType(EntityTypeProtoEnum.CONNECTORS)
            .setName("connectorName")
            .build();
    doReturn(mockedVisitor).when(mockedFactory).obtainEntityReferenceExtractorVisitor(any(), any(), any(), any());
    doReturn(Collections.singleton(referredEntityDetail)).when(mockedVisitor).getEntityReferenceSet();

    infraSetupUsageHelper.updateSetupUsages(infrastructure);

    ArgumentCaptor<EntityDetailProtoDTO> referredByCaptor = ArgumentCaptor.forClass(EntityDetailProtoDTO.class);
    ArgumentCaptor<Set> referredCaptor = ArgumentCaptor.forClass(Set.class);
    verify(setupUsageHelper, times(1))
        .publishInfraEntitySetupUsage(referredByCaptor.capture(), referredCaptor.capture(), eq(ACCOUNT));

    final EntityDetailProtoDTO referredByEntity = referredByCaptor.getValue();
    verifyInfrastructureReferredByEntity(infrastructure, referredByEntity);

    final Set<EntityDetailProtoDTO> referredEntityProtoSet = (Set<EntityDetailProtoDTO>) referredCaptor.getValue();
    assertThat(referredEntityProtoSet).hasSize(1);
    final Object[] referredEntityProtoArray = referredEntityProtoSet.toArray();
    final EntityDetailProtoDTO referredEntityProto = (EntityDetailProtoDTO) referredEntityProtoArray[0];
    assertThat(referredEntityProto.getType()).isEqualTo(EntityTypeProtoEnum.CONNECTORS);
    assertThat(referredEntityProto.getName()).isEqualTo(referredEntityDetail.getName());
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testUpdateSetupUsagesNoReferredEntities() {
    final InfrastructureEntity infrastructure = getInfrastructureEntity();
    doReturn(mockedVisitor).when(mockedFactory).obtainEntityReferenceExtractorVisitor(any(), any(), any(), any());
    doReturn(Collections.EMPTY_SET).when(mockedVisitor).getEntityReferenceSet();

    infraSetupUsageHelper.updateSetupUsages(infrastructure);

    ArgumentCaptor<EntityDetailProtoDTO> captor = ArgumentCaptor.forClass(EntityDetailProtoDTO.class);
    verify(setupUsageHelper, times(1)).deleteInfraSetupUsages(captor.capture(), eq(ACCOUNT));
    final EntityDetailProtoDTO entityDetailProtoDTO = captor.getValue();
    verifyInfrastructureReferredByEntity(infrastructure, entityDetailProtoDTO);
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testGetReferredEntities_WithInvalidReference() {
    doReturn(mockedVisitor).when(mockedFactory).obtainEntityReferenceExtractorVisitor(any(), any(), any(), any());
    doReturn(Collections.EMPTY_SET).when(mockedVisitor).getEntityReferenceSet();

    doThrow(
        new InvalidRequestException("The org level connectors cannot be used at account level. Ref: [org.connectorId]"))
        .when(mockedVisitor)
        .walkElementTree(any());

    // account level infra
    final InfrastructureEntity infrastructure = InfrastructureEntity.builder()
                                                    .accountId(ACCOUNT)
                                                    .envIdentifier("env1")
                                                    .identifier("infra1")
                                                    .name("infra1")
                                                    .yaml("infrastructureDefinition:\n"
                                                        + "  name: infra1\n"
                                                        + "  identifier: infra1\n"
                                                        + "  description: \"\"\n"
                                                        + "  tags: {}\n"
                                                        + "  environmentRef: env1\n"
                                                        + "  deploymentType: Kubernetes\n"
                                                        + "  type: KubernetesDirect\n"
                                                        + "  spec:\n"
                                                        + "    connectorRef: org.connectorId\n"
                                                        + "    namespace: <+input>\n"
                                                        + "    releaseName: <+input>\n"
                                                        + "  allowSimultaneousDeployments: false")
                                                    .type(InfrastructureType.KUBERNETES_DIRECT)
                                                    .build();

    assertThatThrownBy(() -> infraSetupUsageHelper.getAllReferredEntities(infrastructure))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("The org level connectors cannot be used at account level. Ref: [org.connectorId]");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testDeleteSetupUsages() {
    final InfrastructureEntity infrastructure = getInfrastructureEntity();

    infraSetupUsageHelper.deleteSetupUsages(infrastructure);

    ArgumentCaptor<EntityDetailProtoDTO> captor = ArgumentCaptor.forClass(EntityDetailProtoDTO.class);
    verify(setupUsageHelper, times(1)).deleteInfraSetupUsages(captor.capture(), eq(ACCOUNT));
    final EntityDetailProtoDTO entityDetailProtoDTO = captor.getValue();
    verifyInfrastructureReferredByEntity(infrastructure, entityDetailProtoDTO);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void checkThatInfraIsNotReferredByOthers() {
    // no usages exist
    InfrastructureEntity entity = getInfrastructureEntity();
    infraSetupUsageHelper.checkThatInfraIsNotReferredByOthers(entity);

    String infraFqn = FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(entity.getAccountId(),
                          entity.getOrgIdentifier(), entity.getProjectIdentifier(), entity.getEnvIdentifier())
        + "/" + entity.getIdentifier();

    doReturn(Page.empty())
        .when(setupUsageService)
        .listAllEntityUsage(
            anyInt(), anyInt(), eq(entity.getAccountId()), eq(infraFqn), eq(EntityType.INFRASTRUCTURE), eq(""));
    infraSetupUsageHelper.checkThatInfraIsNotReferredByOthers(entity);

    // usages exist.
    List<EntitySetupUsageDTO> references =
        List.of(EntitySetupUsageDTO.builder()
                    .referredByEntity(EntityDetail.builder()
                                          .type(EntityType.PIPELINES)
                                          .name("my_pipeline")
                                          .entityRef(IdentifierRef.builder()
                                                         .accountIdentifier(entity.getAccountId())
                                                         .orgIdentifier(entity.getProjectIdentifier())
                                                         .projectIdentifier(entity.getProjectIdentifier())
                                                         .identifier("my_pipeline_id")
                                                         .build())
                                          .build())
                    .build());
    doReturn(new PageImpl<>(references))
        .when(setupUsageService)
        .listAllEntityUsage(
            anyInt(), anyInt(), eq(entity.getAccountId()), eq(infraFqn), eq(EntityType.INFRASTRUCTURE), eq(""));
    assertThatExceptionOfType(ReferencedEntityException.class)
        .isThrownBy(() -> infraSetupUsageHelper.checkThatInfraIsNotReferredByOthers(entity))
        .withMessageContaining(
            "The infrastructure infraId cannot be deleted because it is being referenced in 1 entity. To delete your infrastructure, please remove the reference infrastructure from these entities");
  }

  private void verifyInfrastructureReferredByEntity(
      InfrastructureEntity infrastructure, EntityDetailProtoDTO entityDetailProtoDTO) {
    assertThat(entityDetailProtoDTO).isNotNull();
    assertThat(entityDetailProtoDTO.getType()).isEqualTo(EntityTypeProtoEnum.INFRASTRUCTURE);
    assertThat(entityDetailProtoDTO.getName()).isEqualTo(infrastructure.getName());
    final InfraDefinitionReferenceProtoDTO infraDefRef = entityDetailProtoDTO.getInfraDefRef();
    assertThat(infraDefRef).isNotNull();
    assertThat(infraDefRef.getAccountIdentifier().getValue()).isEqualTo(ACCOUNT);
    assertThat(infraDefRef.getOrgIdentifier().getValue()).isEqualTo(ORG);
    assertThat(infraDefRef.getProjectIdentifier().getValue()).isEqualTo(PROJECT);
    assertThat(infraDefRef.getEnvIdentifier().getValue()).isEqualTo(infrastructure.getEnvIdentifier());
    assertThat(infraDefRef.getEnvName().getValue()).isEqualTo(ENV_NAME);
    assertThat(infraDefRef.getIdentifier().getValue()).isEqualTo(infrastructure.getIdentifier());
  }

  private InfrastructureEntity getInfrastructureEntity() {
    return InfrastructureEntity.builder()
        .accountId(ACCOUNT)
        .orgIdentifier(ORG)
        .projectIdentifier(PROJECT)
        .envIdentifier("envId")
        .identifier("infraId")
        .name("infraName")
        .build();
  }
}
