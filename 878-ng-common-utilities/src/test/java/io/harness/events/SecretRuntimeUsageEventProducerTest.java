/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.api.EventsFrameworkDownException;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.producer.Message;
import io.harness.eventsframework.protohelper.IdentifierRefProtoDTOHelper;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.eventsframework.schemas.entity.EntityUsageDetailProto;
import io.harness.eventsframework.schemas.entity.IdentifierRefProtoDTO;
import io.harness.eventsframework.schemas.entityactivity.EntityActivityCreateDTO;
import io.harness.ng.core.entityusageactivity.EntityUsageTypes;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

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
public class SecretRuntimeUsageEventProducerTest extends CategoryTest {
  @Mock Producer eventProducer;
  @InjectMocks SecretRuntimeUsageEventProducer secretRuntimeUsageEventProducer;

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
  public void testPublishEvent() {
    EntityDetailProtoDTO referredByEntity =
        EntityDetailProtoDTO.newBuilder().setName(referredByName).setType(EntityTypeProtoEnum.K8S_APPLY_STEP).build();
    IdentifierRefProtoDTO identifierRefProtoDTO =
        IdentifierRefProtoDTOHelper.createIdentifierRefProtoDTO(accountId, orgId, projectId, secretId);
    EntityUsageDetailProto entityUsageDetailProto =
        EntityUsageDetailProto.newBuilder().setUsageType(EntityUsageTypes.TEST_CONNECTION).build();

    EntityActivityCreateDTO entityActivityCreateDTO1 =
        EntityActivityCreateDTO.newBuilder()
            .setAccountIdentifier(accountId)
            .setReferredEntity(EntityDetailProtoDTO.newBuilder()
                                   .setType(EntityTypeProtoEnum.SECRETS)
                                   .setIdentifierRef(identifierRefProtoDTO)
                                   .build())
            .setEntityUsageDetail(EntityActivityCreateDTO.EntityUsageActivityDetailProtoDTO.newBuilder()
                                      .setReferredByEntity(referredByEntity)
                                      .setUsageDetail(entityUsageDetailProto)
                                      .build())
            .build();

    secretRuntimeUsageEventProducer.publishEvent(accountId, secretId, entityActivityCreateDTO1);

    ArgumentCaptor<Message> argumentCaptor = ArgumentCaptor.forClass(Message.class);
    try {
      verify(eventProducer, times(1)).send(argumentCaptor.capture());
    } catch (EventsFrameworkDownException e) {
      e.printStackTrace();
    }

    EntityActivityCreateDTO entityActivityCreateDTO = null;

    try {
      entityActivityCreateDTO = EntityActivityCreateDTO.parseFrom(argumentCaptor.getValue().getData());
    } catch (Exception ex) {
      log.error("Unexpected error :", ex);
    }

    assert entityActivityCreateDTO != null;
    assertThat(entityActivityCreateDTO).isEqualTo(entityActivityCreateDTO1);
  }
}
