/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.secretusage;

import static io.harness.EntityType.SECRETS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.category.element.UnitTests;
import io.harness.events.SecretRuntimeUsageEventProducer;
import io.harness.eventsframework.api.EventsFrameworkDownException;
import io.harness.eventsframework.protohelper.IdentifierRefProtoDTOHelper;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.eventsframework.schemas.entity.EntityUsageDetailProto;
import io.harness.eventsframework.schemas.entity.IdentifierRefProtoDTO;
import io.harness.eventsframework.schemas.entityactivity.EntityActivityCreateDTO;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.ng.core.entityusageactivity.EntityUsageTypes;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.utils.IdentifierRefHelper;

import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class SecretRuntimeUsageServiceImplTest extends CategoryTest {
  @Mock SecretRuntimeUsageEventProducer secretRuntimeUsageEventProducer;
  @InjectMocks SecretRuntimeUsageServiceImpl secretRuntimeUsageService;

  private static final String accountId = "account";
  private static final String orgId = "org";
  private static final String projectId = "project";
  private static final String secretId = "identifier";
  private static final String referredByName = "referredBy";

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    try (MockedStatic<IdentifierRefProtoDTOHelper> mockedStatic =
             Mockito.mockStatic(IdentifierRefProtoDTOHelper.class)) {
      mockedStatic
          .when(()
                    -> IdentifierRefProtoDTOHelper.createIdentifierRefProtoDTO(
                        anyString(), anyString(), anyString(), anyString()))
          .thenCallRealMethod();
    }

    try (MockedStatic<IdentifierRefProtoDTOHelper> mockedIdentifierRefProtoDTOHelper =
             Mockito.mockStatic(IdentifierRefProtoDTOHelper.class)) {
      mockedIdentifierRefProtoDTOHelper.when(() -> IdentifierRefProtoDTOHelper.fromIdentifierRef(any()))
          .thenCallRealMethod();
    }
  }

  @Test
  @Owner(developers = OwnerRule.MANISH)
  @Category(UnitTests.class)
  public void testProduceRuntimeUsageForSecretEventWithSecretDTOV2() {
    SecretDTOV2 secretDTOV2 =
        SecretDTOV2.builder().identifier(secretId).orgIdentifier(orgId).projectIdentifier(projectId).build();
    EntityDetailProtoDTO referredByEntity =
        EntityDetailProtoDTO.newBuilder().setName(referredByName).setType(EntityTypeProtoEnum.K8S_APPLY_STEP).build();
    IdentifierRefProtoDTO identifierRefProtoDTO = IdentifierRefProtoDTOHelper.createIdentifierRefProtoDTO(
        accountId, secretDTOV2.getOrgIdentifier(), secretDTOV2.getProjectIdentifier(), secretDTOV2.getIdentifier());
    EntityUsageDetailProto entityUsageDetailProto =
        EntityUsageDetailProto.newBuilder().setUsageType(EntityUsageTypes.TEST_CONNECTION).build();

    secretRuntimeUsageService.createSecretRuntimeUsage(
        accountId, secretDTOV2, referredByEntity, entityUsageDetailProto);

    ArgumentCaptor<EntityActivityCreateDTO> argumentCaptor = ArgumentCaptor.forClass(EntityActivityCreateDTO.class);
    try {
      verify(secretRuntimeUsageEventProducer, times(1))
          .publishEvent(eq(accountId), eq(secretId), argumentCaptor.capture());
    } catch (EventsFrameworkDownException e) {
      e.printStackTrace();
    }

    EntityActivityCreateDTO entityActivityCreateDTO = null;

    try {
      entityActivityCreateDTO = argumentCaptor.getValue();
    } catch (Exception ex) {
      log.error("Unexpected error :", ex);
    }

    assert entityActivityCreateDTO != null;
    assertThat(entityActivityCreateDTO.getReferredEntity().getType().toString()).isEqualTo(SECRETS.name());
    assertThat(entityActivityCreateDTO.getAccountIdentifier()).isEqualTo(accountId);
    assertThat(entityActivityCreateDTO.getReferredEntity().getIdentifierRef()).isEqualTo(identifierRefProtoDTO);
    assertThat(entityActivityCreateDTO.getEntityUsageDetail().getReferredByEntity()).isEqualTo(referredByEntity);
    assertThat(entityActivityCreateDTO.getEntityUsageDetail().getUsageDetail()).isEqualTo(entityUsageDetailProto);
  }

  @Test
  @Owner(developers = OwnerRule.MANISH)
  @Category(UnitTests.class)
  public void testProduceRuntimeUsageForSecretEventWithSecretIdentifierRef() {
    SecretDTOV2 secretDTOV2 =
        SecretDTOV2.builder().identifier(secretId).orgIdentifier(orgId).projectIdentifier(projectId).build();
    EntityDetailProtoDTO referredByEntity =
        EntityDetailProtoDTO.newBuilder().setName(referredByName).setType(EntityTypeProtoEnum.K8S_APPLY_STEP).build();
    IdentifierRefProtoDTO identifierRefProtoDTO = IdentifierRefProtoDTOHelper.createIdentifierRefProtoDTO(
        accountId, secretDTOV2.getOrgIdentifier(), secretDTOV2.getProjectIdentifier(), secretDTOV2.getIdentifier());
    EntityUsageDetailProto entityUsageDetailProto =
        EntityUsageDetailProto.newBuilder().setUsageType(EntityUsageTypes.TEST_CONNECTION).build();
    IdentifierRef secretIdentifierRef = IdentifierRefHelper.getIdentifierRef(secretId, accountId, orgId, projectId);

    secretRuntimeUsageService.createSecretRuntimeUsage(secretIdentifierRef, referredByEntity, entityUsageDetailProto);

    ArgumentCaptor<EntityActivityCreateDTO> argumentCaptor = ArgumentCaptor.forClass(EntityActivityCreateDTO.class);
    try {
      verify(secretRuntimeUsageEventProducer, times(1))
          .publishEvent(eq(accountId), eq(secretId), argumentCaptor.capture());
    } catch (EventsFrameworkDownException e) {
      e.printStackTrace();
    }

    EntityActivityCreateDTO entityActivityCreateDTO = null;

    try {
      entityActivityCreateDTO = argumentCaptor.getValue();
    } catch (Exception ex) {
      log.error("Unexpected error :", ex);
    }

    assert entityActivityCreateDTO != null;
    assertThat(entityActivityCreateDTO.getReferredEntity().getType().toString()).isEqualTo(SECRETS.name());
    assertThat(entityActivityCreateDTO.getAccountIdentifier()).isEqualTo(accountId);
    assertThat(entityActivityCreateDTO.getReferredEntity().getIdentifierRef()).isEqualTo(identifierRefProtoDTO);
    assertThat(entityActivityCreateDTO.getEntityUsageDetail().getReferredByEntity()).isEqualTo(referredByEntity);
    assertThat(entityActivityCreateDTO.getEntityUsageDetail().getUsageDetail()).isEqualTo(entityUsageDetailProto);
  }
}
