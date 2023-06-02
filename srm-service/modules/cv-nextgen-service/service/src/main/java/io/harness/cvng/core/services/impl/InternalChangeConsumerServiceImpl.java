/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import io.harness.cvng.beans.activity.ActivityType;
import io.harness.cvng.beans.change.ChangeEventDTO;
import io.harness.cvng.beans.change.ChangeSourceType;
import io.harness.cvng.beans.change.DeepLink;
import io.harness.cvng.beans.change.InternalChangeEvent;
import io.harness.cvng.beans.change.InternalChangeEventMetaData;
import io.harness.cvng.beans.change.InternalChangeEventMetaData.InternalChangeEventMetaDataBuilder;
import io.harness.cvng.core.services.api.ChangeEventService;
import io.harness.cvng.core.services.api.InternalChangeConsumerService;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.schemas.cv.InternalChangeEventDTO;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class InternalChangeConsumerServiceImpl implements InternalChangeConsumerService {
  @Inject ChangeEventService changeEventService;

  @Override
  public boolean processMessage(Message message) {
    InternalChangeEventDTO internalChangeEventDTO;
    try {
      internalChangeEventDTO = InternalChangeEventDTO.parseFrom(message.getMessage().getData());
    } catch (InvalidProtocolBufferException e) {
      log.error("Exception in unpacking DeploymentInfoDTO for key {}", message.getId(), e);
      throw new IllegalStateException(e);
    }
    validateEvent(internalChangeEventDTO);
    registerChangeEvents(internalChangeEventDTO);
    return true;
  }

  private void validateEvent(InternalChangeEventDTO internalChangeEventDTO) {
    Preconditions.checkArgument(
        !internalChangeEventDTO.getAccountId().isEmpty(), "AccountId is invalid for current Internal Change Event");
    Preconditions.checkArgument(!internalChangeEventDTO.getOrgIdentifier().isEmpty(),
        "OrgIdentifier is invalid for current Internal Change Event");
    Preconditions.checkArgument(!internalChangeEventDTO.getProjectIdentifier().isEmpty(),
        "ProjectIdentifier is invalid for current Internal Change Event");
    Preconditions.checkArgument(internalChangeEventDTO.getServiceIdentifierCount() > 0,
        "No ServiceIdentifier present for current Internal Change Event");
    Preconditions.checkArgument(internalChangeEventDTO.getEnvironmentIdentifierCount() > 0,
        "No EnvIdentifier present for current Internal Change Event");
    Preconditions.checkArgument(
        !internalChangeEventDTO.getType().isEmpty(), "Type is invalid for current Internal Change Event");
    Preconditions.checkArgument(
        internalChangeEventDTO.getExecutionTime() > 0, "ExecutionTime is invalid for current Internal Change Event");
  }

  private void registerChangeEvents(InternalChangeEventDTO internalChangeEventDTO) {
    InternalChangeEventMetaDataBuilder internalChangeEventMetaDataBuilder =
        InternalChangeEventMetaData.builder()
            .activityType(ActivityType.fromString(internalChangeEventDTO.getType()))
            .internalChangeEvent(
                InternalChangeEvent.builder()
                    .eventDescriptions(internalChangeEventDTO.getEventDetails().getEventDetailsList())
                    .changeEventDetailsLink(
                        DeepLink.builder()
                            .action(DeepLink.Action.FETCH_DIFF_DATA)
                            .url(internalChangeEventDTO.getEventDetails().getChangeEventDetailsLink())
                            .build())
                    .internalLinkToEntity(DeepLink.builder()
                                              .action(DeepLink.Action.REDIRECT_URL)
                                              .url(internalChangeEventDTO.getEventDetails().getInternalLinkToEntity())
                                              .build())
                    .build())
            .updatedBy(internalChangeEventDTO.getEventDetails().getUser())
            .eventStartTime(internalChangeEventDTO.getExecutionTime());

    if (internalChangeEventDTO.hasField(InternalChangeEventDTO.getDescriptor().findFieldByName("executionEndTime"))) {
      internalChangeEventMetaDataBuilder.eventEndTime(internalChangeEventDTO.getExecutionEndTime());
    }

    ChangeEventDTO changeEventDTO =
        ChangeEventDTO.builder()
            .name(internalChangeEventDTO.getEventDetails().getName())
            .accountId(internalChangeEventDTO.getAccountId())
            .orgIdentifier(internalChangeEventDTO.getOrgIdentifier())
            .projectIdentifier(internalChangeEventDTO.getProjectIdentifier())
            .eventTime(internalChangeEventDTO.getExecutionTime())
            .type(ChangeSourceType.ofActivityType(ActivityType.fromString(internalChangeEventDTO.getType())))
            .metadata(internalChangeEventMetaDataBuilder.build())
            .build();

    for (int i = 0; i < internalChangeEventDTO.getEnvironmentIdentifierCount(); i++) {
      for (int j = 0; j < internalChangeEventDTO.getServiceIdentifierCount(); j++) {
        changeEventDTO.setServiceIdentifier(internalChangeEventDTO.getServiceIdentifier(j));
        changeEventDTO.setEnvIdentifier(internalChangeEventDTO.getEnvironmentIdentifier(i));
        changeEventService.register(changeEventDTO);
        changeEventDTO.setMonitoredServiceIdentifier(null);
      }
    }
  }
}
