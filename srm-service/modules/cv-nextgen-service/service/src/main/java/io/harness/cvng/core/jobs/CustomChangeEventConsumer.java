/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.jobs;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.cvng.beans.change.ChangeEventDTO;
import io.harness.cvng.beans.change.CustomChangeEvent;
import io.harness.cvng.beans.change.CustomChangeEventMetadata;
import io.harness.cvng.beans.change.CustomChangeEventMetadata.CustomChangeEventMetadataBuilder;
import io.harness.cvng.core.beans.params.MonitoredServiceParams;
import io.harness.cvng.core.entities.changeSource.ChangeSource;
import io.harness.cvng.core.services.api.ChangeEventService;
import io.harness.cvng.core.services.api.monitoredService.ChangeSourceService;
import io.harness.eventsframework.EventsFrameworkConstants;
import io.harness.eventsframework.api.Consumer;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.schemas.cv.CustomChangeEventDTO;
import io.harness.queue.QueueController;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class CustomChangeEventConsumer extends AbstractStreamConsumer {
  @Inject ChangeEventService changeEventService;
  @Inject private ChangeSourceService changeSourceService;
  private static final int MAX_WAIT_TIME_SEC = 10;

  @Inject
  public CustomChangeEventConsumer(
      @Named(EventsFrameworkConstants.CUSTOM_CHANGE_EVENT) Consumer consumer, QueueController queueController) {
    super(MAX_WAIT_TIME_SEC, consumer, queueController);
  }

  @Override
  protected boolean processMessage(Message message) {
    CustomChangeEventDTO customChangeEventDTO;
    try {
      customChangeEventDTO = CustomChangeEventDTO.parseFrom(message.getMessage().getData());
    } catch (InvalidProtocolBufferException e) {
      log.error("Exception in unpacking DeploymentInfoDTO for key {}", message.getId(), e);
      throw new IllegalStateException(e);
    }
    validateEvent(customChangeEventDTO);
    registerChangeEvents(customChangeEventDTO);
    return true;
  }

  private void validateEvent(CustomChangeEventDTO customChangeEventDTO) {
    Preconditions.checkArgument(
        !customChangeEventDTO.getAccountId().isEmpty(), "AccountId is invalid for current Internal Change Event");
    Preconditions.checkArgument(!customChangeEventDTO.getOrgIdentifier().isEmpty(),
        "OrgIdentifier is invalid for current Internal Change Event");
    Preconditions.checkArgument(!customChangeEventDTO.getProjectIdentifier().isEmpty(),
        "ProjectIdentifier is invalid for current Internal Change Event");
    Preconditions.checkArgument(!customChangeEventDTO.getMonitoredServiceIdentifier().isEmpty(),
        "MonitoredServiceIdentifier is invalid for current Internal Change Event");
    Preconditions.checkArgument(!customChangeEventDTO.getChangeSourceIdentifier().isEmpty(),
        "ChangeSourceIdentifier is invalid for current Internal Change Event");
  }

  private void registerChangeEvents(CustomChangeEventDTO customChangeEventDTO) {
    ChangeSource changeSource =
        changeSourceService.get(MonitoredServiceParams.builder()
                                    .projectIdentifier(customChangeEventDTO.getProjectIdentifier())
                                    .orgIdentifier(customChangeEventDTO.getOrgIdentifier())
                                    .accountIdentifier(customChangeEventDTO.getAccountId())
                                    .monitoredServiceIdentifier(customChangeEventDTO.getMonitoredServiceIdentifier())
                                    .build(),
            customChangeEventDTO.getChangeSourceIdentifier());

    CustomChangeEventMetadataBuilder customChangeEventMetadataBuilder =
        CustomChangeEventMetadata.builder()
            .startTime(customChangeEventDTO.getStartTime())
            .endTime(customChangeEventDTO.getEndTime())
            .user(customChangeEventDTO.getUser())
            .customChangeEvent(
                CustomChangeEvent.builder()
                    .changeEventDetailsLink(customChangeEventDTO.getEventDetails().getChangeEventDetailsLink())
                    .externalLinkToEntity(customChangeEventDTO.getEventDetails().getExternalLinkToEntity())
                    .description(customChangeEventDTO.getEventDetails().getDescription())
                    .channelUrl(customChangeEventDTO.getEventDetails().getChannelUrl())
                    .webhookUrl(customChangeEventDTO.getEventDetails().getWebhookUrl())
                    .build())
            .type(changeSource.getType());

    ChangeEventDTO changeEventDTO =
        ChangeEventDTO.builder()
            .id(customChangeEventDTO.getEventIdentifier())
            .name(customChangeEventDTO.getEventDetails().getName())
            .accountId(customChangeEventDTO.getAccountId())
            .orgIdentifier(customChangeEventDTO.getOrgIdentifier())
            .projectIdentifier(customChangeEventDTO.getProjectIdentifier())
            .monitoredServiceIdentifier(customChangeEventDTO.getMonitoredServiceIdentifier())
            .changeSourceIdentifier(customChangeEventDTO.getChangeSourceIdentifier())
            .eventTime(customChangeEventDTO.getStartTime())
            .type(changeSource.getType())
            .metadata(customChangeEventMetadataBuilder.build())
            .build();

    if (isNotEmpty(customChangeEventDTO.getEventDetails().getWebhookUrl())) {
      changeEventService.registerWithHealthReport(
          changeEventDTO, customChangeEventDTO.getEventDetails().getWebhookUrl());
    } else {
      changeEventService.register(changeEventDTO);
    }
  }
}
