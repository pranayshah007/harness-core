/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.setupusage;

import static io.harness.rule.OwnerRule.TATHAGAT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.eventsframework.schemas.entity.InfraDefinitionReferenceProtoDTO;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.ng.core.setupusage.SetupUsageHelper;
import io.harness.rule.Owner;
import io.harness.walktree.visitor.SimpleVisitorFactory;
import io.harness.walktree.visitor.entityreference.EntityReferenceExtractorVisitor;

import com.google.inject.Inject;
import com.google.protobuf.StringValue;
import java.util.Collections;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class InfrastructureEntitySetupUsageHelperTest extends CategoryTest {
  @Mock private SimpleVisitorFactory mockedFactory;
  @Mock private SetupUsageHelper setupUsageHelper;
  @Mock EntityReferenceExtractorVisitor mockedVisitor;
  @InjectMocks @Inject private InfrastructureEntitySetupUsageHelper infraSetupUsageHelper;

  private static final String ACCOUNT = "ACCOUNT";
  private static final String ORG = "ORG";
  private static final String PROJECT = "PROJECT";
  private static final String INFRA_ROOT_NAME = "infrastructureDefinition";

  @Before
  public void setUp() {
    initMocks(this);
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
    assertThat(referredEntityProtoSet).isNotEmpty();
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
