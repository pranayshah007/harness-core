/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import static io.harness.rule.OwnerRule.SOWMYA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.BuilderFactory.Context;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO;
import io.harness.cvng.core.entities.MonitoredService;
import io.harness.cvng.core.services.api.SetupUsageEventService;
import io.harness.eventsframework.api.AbstractProducer;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.producer.Message;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entitysetupusage.EntitySetupUsageCreateV2DTO;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

@Slf4j
public class SetupUsageEventsServiceImplTest extends CvNextGenTestBase {
  @Inject private SetupUsageEventService setupUsageEventService;
  private Producer producer;

  private BuilderFactory builderFactory;
  private Context context;

  @Before
  public void setup() throws IllegalAccessException {
    builderFactory = BuilderFactory.getDefault();
    context = builderFactory.getContext();
    producer = Mockito.mock(AbstractProducer.class);

    FieldUtils.writeField(setupUsageEventService, "eventProducer", producer, true);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testSendCreateEventsForMonitoredService() throws InvalidProtocolBufferException {
    MonitoredServiceDTO monitoredServiceDTO = builderFactory.monitoredServiceDTOBuilder().build();

    ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);
    setupUsageEventService.sendCreateEventsForMonitoredService(context.getProjectParams(), monitoredServiceDTO);

    verify(producer, times(3)).send(messageArgumentCaptor.capture());

    List<Message> messages = messageArgumentCaptor.getAllValues();
    assertThat(messages.size()).isEqualTo(3);
    assertThat(messages.get(0).getMetadataMap().get("referredEntityType")).isEqualTo("CONNECTORS");
    EntityDetailProtoDTO entityDetailProtoDTO =
        EntitySetupUsageCreateV2DTO.parseFrom(messages.get(0).getData()).getReferredByEntity();
    assertThat(entityDetailProtoDTO.getName()).isEqualTo(monitoredServiceDTO.getName());
    assertThat(entityDetailProtoDTO.getIdentifierRef().getIdentifier().getValue())
        .isEqualTo(monitoredServiceDTO.getIdentifier());
    assertThat(messages.get(0).getMetadataMap().get("action")).isEqualTo("flushCreate");
    assertThat(messages.get(1).getMetadataMap().get("referredEntityType")).isEqualTo("SERVICE");
    assertThat(messages.get(1).getMetadataMap().get("action")).isEqualTo("flushCreate");
    assertThat(messages.get(2).getMetadataMap().get("referredEntityType")).isEqualTo("ENVIRONMENT");
    assertThat(messages.get(2).getMetadataMap().get("action")).isEqualTo("flushCreate");
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testSendDeleteEventsForMonitoredService() throws InvalidProtocolBufferException {
    MonitoredServiceDTO monitoredServiceDTO = builderFactory.monitoredServiceDTOBuilder().build();

    ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);
    setupUsageEventService.sendDeleteEventsForMonitoredService(context.getProjectParams(),
        MonitoredService.builder()
            .identifier(monitoredServiceDTO.getIdentifier())
            .name(monitoredServiceDTO.getName())
            .build());

    verify(producer, times(3)).send(messageArgumentCaptor.capture());

    List<Message> messages = messageArgumentCaptor.getAllValues();
    assertThat(messages.size()).isEqualTo(3);
    assertThat(messages.get(0).getMetadataMap().get("referredEntityType")).isEqualTo("CONNECTORS");
    EntityDetailProtoDTO entityDetailProtoDTO =
        EntitySetupUsageCreateV2DTO.parseFrom(messages.get(0).getData()).getReferredByEntity();
    assertThat(entityDetailProtoDTO.getIdentifierRef().getIdentifier().getValue())
        .isEqualTo(monitoredServiceDTO.getIdentifier());
    assertThat(messages.get(0).getMetadataMap().get("action")).isEqualTo("flushCreate");
    assertThat(messages.get(1).getMetadataMap().get("referredEntityType")).isEqualTo("SERVICE");
    assertThat(messages.get(1).getMetadataMap().get("action")).isEqualTo("flushCreate");
    assertThat(messages.get(2).getMetadataMap().get("referredEntityType")).isEqualTo("ENVIRONMENT");
    assertThat(messages.get(2).getMetadataMap().get("action")).isEqualTo("flushCreate");
  }
}
