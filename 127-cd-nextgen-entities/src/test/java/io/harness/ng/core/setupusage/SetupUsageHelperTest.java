/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.setupusage;

import static io.harness.rule.OwnerRule.TATHAGAT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.producer.Message;
import io.harness.eventsframework.protohelper.IdentifierRefProtoDTOHelper;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.eventsframework.schemas.entity.InfraDefinitionReferenceProtoDTO;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import com.google.protobuf.StringValue;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class SetupUsageHelperTest extends CategoryTest {
  @Mock private Producer producer;
  @Mock private IdentifierRefProtoDTOHelper identifierRefProtoDTOHelper;
  @InjectMocks @Inject private SetupUsageHelper setupUsageHelper;

  @Before
  public void setUp() {
    initMocks(this);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testDeleteSetupUsages() {
    final EntityDetailProtoDTO entityDetail =
        EntityDetailProtoDTO.newBuilder()
            .setInfraDefRef(
                InfraDefinitionReferenceProtoDTO.newBuilder().setIdentifier(StringValue.of("infraId")).build())
            .setType(EntityTypeProtoEnum.INFRASTRUCTURE)
            .setName("infraName")
            .build();
    setupUsageHelper.deleteSetupUsages(entityDetail, "accountId");
    ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
    verify(producer, times(1)).send(captor.capture());
    assertThat(captor.getValue()).isNotNull();
    final Message message = captor.getValue();
    assertThat(message.getMetadataMap().values())
        .containsExactlyInAnyOrder("accountId", EventsFrameworkMetadataConstants.FLUSH_CREATE_ACTION);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testPublishEntitySetupUsage() {
    final EntityDetailProtoDTO referredEntityDetail =
        EntityDetailProtoDTO.newBuilder()
            .setInfraDefRef(
                InfraDefinitionReferenceProtoDTO.newBuilder().setIdentifier(StringValue.of("connectorId")).build())
            .setType(EntityTypeProtoEnum.CONNECTORS)
            .setName("connectorName")
            .build();

    final EntityDetailProtoDTO referredByEntityDetail =
        EntityDetailProtoDTO.newBuilder()
            .setInfraDefRef(
                InfraDefinitionReferenceProtoDTO.newBuilder().setIdentifier(StringValue.of("infraId")).build())
            .setType(EntityTypeProtoEnum.INFRASTRUCTURE)
            .setName("infraName")
            .build();

    setupUsageHelper.publishEntitySetupUsage(
        referredByEntityDetail, Collections.singleton(referredEntityDetail), "accountId");
    ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
    verify(producer, times(1)).send(captor.capture());
    assertThat(captor.getValue()).isNotNull();
    final Message message = captor.getValue();
    assertThat(message.getMetadataMap().keySet())
        .containsExactlyInAnyOrder("accountId", EventsFrameworkMetadataConstants.REFERRED_ENTITY_TYPE,
            EventsFrameworkMetadataConstants.ACTION);
    assertThat(message.getMetadataMap().values())
        .containsExactlyInAnyOrder("accountId", "CONNECTORS", EventsFrameworkMetadataConstants.FLUSH_CREATE_ACTION);
  }
}
