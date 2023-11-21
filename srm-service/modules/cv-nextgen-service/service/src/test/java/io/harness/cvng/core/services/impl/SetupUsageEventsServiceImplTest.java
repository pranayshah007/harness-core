/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import static io.harness.rule.OwnerRule.KARAN_SARASWAT;
import static io.harness.rule.OwnerRule.SOWMYA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.BuilderFactory.Context;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO;
import io.harness.cvng.core.beans.template.TemplateDTO;
import io.harness.cvng.core.beans.template.TemplateMetadata;
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
  private MonitoredServiceDTO monitoredServiceDTO;

  @Before
  public void setup() throws IllegalAccessException {
    builderFactory = BuilderFactory.getDefault();
    context = builderFactory.getContext();
    producer = Mockito.mock(AbstractProducer.class);
    monitoredServiceDTO = builderFactory.monitoredServiceDTOBuilder().build();

    FieldUtils.writeField(setupUsageEventService, "eventProducer", producer, true);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testSendCreateEventsForMonitoredService() throws InvalidProtocolBufferException {
    ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);
    setupUsageEventService.sendCreateEventsForMonitoredService(context.getProjectParams(), monitoredServiceDTO);

    verify(producer, times(3)).send(messageArgumentCaptor.capture());

    List<Message> messages = messageArgumentCaptor.getAllValues();
    assertThat(messages).hasSize(3);

    EntityDetailProtoDTO entityDetailProtoDTO =
        EntitySetupUsageCreateV2DTO.parseFrom(messages.get(0).getData()).getReferredByEntity();
    assertThat(entityDetailProtoDTO.getName()).isEqualTo(monitoredServiceDTO.getName());
    assertThat(entityDetailProtoDTO.getIdentifierRef().getIdentifier().getValue())
        .isEqualTo(monitoredServiceDTO.getIdentifier());

    assertThat(messages.get(0).getMetadataMap()).containsEntry("referredEntityType", "CONNECTORS");
    assertThat(messages.get(0).getMetadataMap()).containsEntry("action", "flushCreate");
    assertThat(messages.get(1).getMetadataMap()).containsEntry("referredEntityType", "SERVICE");
    assertThat(messages.get(1).getMetadataMap()).containsEntry("action", "flushCreate");
    assertThat(messages.get(2).getMetadataMap()).containsEntry("referredEntityType", "ENVIRONMENT");
    assertThat(messages.get(2).getMetadataMap()).containsEntry("action", "flushCreate");
  }

  @Test
  @Owner(developers = KARAN_SARASWAT)
  @Category(UnitTests.class)
  public void testSendCreateEventsForTemplateReferencedMonitoredService() throws InvalidProtocolBufferException {
    monitoredServiceDTO.setTemplate(
        TemplateDTO.builder().templateRef("template1").versionLabel("v1").isTemplateByReference(true).build());

    ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);
    setupUsageEventService.sendCreateEventsForMonitoredService(context.getProjectParams(), monitoredServiceDTO);

    verify(producer, times(4)).send(messageArgumentCaptor.capture());

    List<Message> messages = messageArgumentCaptor.getAllValues();
    assertThat(messages).hasSize(4);

    EntityDetailProtoDTO entityDetailProtoDTO =
        EntitySetupUsageCreateV2DTO.parseFrom(messages.get(0).getData()).getReferredByEntity();
    assertThat(entityDetailProtoDTO.getName()).isEqualTo(monitoredServiceDTO.getName());
    assertThat(entityDetailProtoDTO.getIdentifierRef().getIdentifier().getValue())
        .isEqualTo(monitoredServiceDTO.getIdentifier());

    assertThat(messages.get(3).getMetadataMap()).containsEntry("referredEntityType", "TEMPLATE");
    assertThat(messages.get(3).getMetadataMap()).containsEntry("action", "flushCreate");
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testSendDeleteEventsForMonitoredService() throws InvalidProtocolBufferException {
    ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);
    setupUsageEventService.sendDeleteEventsForMonitoredService(context.getProjectParams(),
        MonitoredService.builder()
            .identifier(monitoredServiceDTO.getIdentifier())
            .name(monitoredServiceDTO.getName())
            .build());

    verify(producer, times(3)).send(messageArgumentCaptor.capture());

    List<Message> messages = messageArgumentCaptor.getAllValues();
    assertThat(messages).hasSize(3);

    EntityDetailProtoDTO entityDetailProtoDTO =
        EntitySetupUsageCreateV2DTO.parseFrom(messages.get(0).getData()).getReferredByEntity();
    assertThat(entityDetailProtoDTO.getIdentifierRef().getIdentifier().getValue())
        .isEqualTo(monitoredServiceDTO.getIdentifier());

    assertThat(messages.get(0).getMetadataMap()).containsEntry("referredEntityType", "CONNECTORS");
    assertThat(messages.get(0).getMetadataMap()).containsEntry("action", "flushCreate");
    assertThat(messages.get(1).getMetadataMap()).containsEntry("referredEntityType", "SERVICE");
    assertThat(messages.get(1).getMetadataMap()).containsEntry("action", "flushCreate");
    assertThat(messages.get(2).getMetadataMap()).containsEntry("referredEntityType", "ENVIRONMENT");
    assertThat(messages.get(2).getMetadataMap()).containsEntry("action", "flushCreate");
  }

  @Test
  @Owner(developers = KARAN_SARASWAT)
  @Category(UnitTests.class)
  public void testSendDeleteEventsForTemplateReferencedMonitoredService() throws InvalidProtocolBufferException {
    ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);
    setupUsageEventService.sendDeleteEventsForMonitoredService(context.getProjectParams(),
        MonitoredService.builder()
            .identifier(monitoredServiceDTO.getIdentifier())
            .name(monitoredServiceDTO.getName())
            .templateMetadata(TemplateMetadata.builder()
                                  .templateIdentifier("template")
                                  .versionLabel("v1")
                                  .isTemplateByReference(true)
                                  .build())
            .build());

    verify(producer, times(4)).send(messageArgumentCaptor.capture());

    List<Message> messages = messageArgumentCaptor.getAllValues();
    assertThat(messages).hasSize(4);

    EntityDetailProtoDTO entityDetailProtoDTO =
        EntitySetupUsageCreateV2DTO.parseFrom(messages.get(0).getData()).getReferredByEntity();
    assertThat(entityDetailProtoDTO.getIdentifierRef().getIdentifier().getValue())
        .isEqualTo(monitoredServiceDTO.getIdentifier());

    assertThat(messages.get(3).getMetadataMap()).containsEntry("referredEntityType", "TEMPLATE");
    assertThat(messages.get(3).getMetadataMap()).containsEntry("action", "flushCreate");
  }
}
