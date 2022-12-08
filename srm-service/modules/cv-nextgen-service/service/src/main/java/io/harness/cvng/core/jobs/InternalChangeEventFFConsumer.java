/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.jobs;

import io.harness.cvng.beans.change.ChangeEventDTO;
import io.harness.cvng.beans.change.ChangeSourceType;
import io.harness.cvng.beans.change.DeepLinkData;
import io.harness.cvng.beans.change.EventDetails;
import io.harness.cvng.beans.change.InternalChangeEventMetaData;
import io.harness.cvng.core.services.api.ChangeEventService;
import io.harness.eventsframework.EventsFrameworkConstants;
import io.harness.eventsframework.api.Consumer;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.schemas.cv.InternalChangeEventDTO;
import io.harness.queue.QueueController;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class InternalChangeEventFFConsumer extends AbstractStreamConsumer {
  private static final int MAX_WAIT_TIME_SEC = 10;
  ChangeEventService changeEventService;

  @Inject
  public InternalChangeEventFFConsumer(@Named(EventsFrameworkConstants.INTERNAL_CHANGE_EVENT_FF) Consumer consumer,
      QueueController queueController, ChangeEventService changeEventService) {
    super(MAX_WAIT_TIME_SEC, consumer, queueController);
    this.changeEventService = changeEventService;
  }

  @Override
  protected boolean processMessage(Message message) {
    InternalChangeEventDTO internalChangeEventDTO;
    try {
      internalChangeEventDTO = InternalChangeEventDTO.parseFrom(message.getMessage().getData());
    } catch (InvalidProtocolBufferException e) {
      log.error("Exception in unpacking DeploymentInfoDTO for key {}", message.getId(), e);
      throw new IllegalStateException(e);
    }
    registerChangeEvents(internalChangeEventDTO);
    return true;
  }

  private void registerChangeEvents(InternalChangeEventDTO internalChangeEventDTO) {
    InternalChangeEventMetaData.InternalChangeEventMetaDataBuilder internalChangeEventMetaDataBuilder =
        InternalChangeEventMetaData.builder()
            .eventType(internalChangeEventDTO.getType())
            .eventDetails(
                EventDetails.builder()
                    .eventDetail(internalChangeEventDTO.getEventDetails().getEventDetailsList())
                    .changeEventDetailsLink(
                        DeepLinkData.builder()
                            .action(DeepLinkData.Action.FETCH_DIFF_DATA)
                            .url(internalChangeEventDTO.getEventDetails().getChangeEventDetailsLink())
                            .build())
                    .internalLinkToEntity(DeepLinkData.builder()
                                              .action(DeepLinkData.Action.REDIRECT_URL)
                                              .url(internalChangeEventDTO.getEventDetails().getInternalLinkToEntity())
                                              .build())
                    .build())
            .updateBy(internalChangeEventDTO.getEventDetails().getUser());

    ChangeEventDTO changeEventDTO = ChangeEventDTO.builder()
                                        .accountId(internalChangeEventDTO.getAccountId())
                                        .orgIdentifier(internalChangeEventDTO.getOrgIdentifier())
                                        .projectIdentifier(internalChangeEventDTO.getProjectIdentifier())
                                        .eventTime(internalChangeEventDTO.getExecutionTime())
                                        .type(ChangeSourceType.INTERNAL_CHANGE_SOURCE_FF)
                                        .metadata(internalChangeEventMetaDataBuilder.build())
                                        .build();

    for (int i = 0; i < internalChangeEventDTO.getEnvironmentIdentifierCount(); i++) {
      for (int j = 0; j < internalChangeEventDTO.getServiceIdentifierCount(); j++) {
        changeEventDTO.setServiceIdentifier(internalChangeEventDTO.getServiceIdentifier(j));
        changeEventDTO.setEnvIdentifier(internalChangeEventDTO.getEnvironmentIdentifier(i));
      }
      changeEventService.register(changeEventDTO);
    }
  }
}
