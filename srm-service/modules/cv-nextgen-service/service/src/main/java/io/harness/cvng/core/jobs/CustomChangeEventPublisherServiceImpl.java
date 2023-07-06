/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.jobs;

import static io.harness.eventsframework.EventsFrameworkConstants.CUSTOM_CHANGE_EVENT;

import io.harness.cvng.core.beans.CustomChangeWebhookPayload;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.producer.Message;
import io.harness.eventsframework.schemas.cv.CustomChangeEventDTO;
import io.harness.eventsframework.schemas.cv.CustomChangeEventDetails;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.name.Named;

public class CustomChangeEventPublisherServiceImpl implements CustomChangeEventPublisherService {
  @Inject @Named(CUSTOM_CHANGE_EVENT) private Producer eventProducer;

  @Override
  public void publishCustomChangeEvent(ProjectParams projectParams, String monitoredServiceIdentifier,
      String changeSourceIdentifier, CustomChangeWebhookPayload customChangeWebhookPayload) {
    eventProducer.send(getCustomChangeEventMessage(
        projectParams, monitoredServiceIdentifier, changeSourceIdentifier, customChangeWebhookPayload));
  }

  @VisibleForTesting
  public Message getCustomChangeEventMessage(ProjectParams projectParams, String monitoredServiceIdentifier,
      String changeSourceIdentifier, CustomChangeWebhookPayload customChangeWebhookPayload) {
    CustomChangeEventDetails.Builder customChangeEventDetailsBuilder =
        CustomChangeEventDetails.newBuilder()
            .setDescription(customChangeWebhookPayload.getEventDetail().getDescription())
            .setName(customChangeWebhookPayload.getEventDetail().getName());

    if (customChangeWebhookPayload.getEventDetail().getChangeEventDetailsLink() != null) {
      customChangeEventDetailsBuilder.setChangeEventDetailsLink(
          customChangeWebhookPayload.getEventDetail().getChangeEventDetailsLink());
    }
    if (customChangeWebhookPayload.getEventDetail().getExternalLinkToEntity() != null) {
      customChangeEventDetailsBuilder.setExternalLinkToEntity(
          customChangeWebhookPayload.getEventDetail().getExternalLinkToEntity());
    }
    if (customChangeWebhookPayload.getEventDetail().getChannelUrl() != null) {
      customChangeEventDetailsBuilder.setChannelUrl(customChangeWebhookPayload.getEventDetail().getChannelUrl());
    }
    if (customChangeWebhookPayload.getEventDetail().getWebhookUrl() != null) {
      customChangeEventDetailsBuilder.setWebhookUrl(customChangeWebhookPayload.getEventDetail().getWebhookUrl());
    }

    CustomChangeEventDTO.Builder customChangeEventDTOBuilder =
        CustomChangeEventDTO.newBuilder()
            .setAccountId(projectParams.getAccountIdentifier())
            .setOrgIdentifier(projectParams.getOrgIdentifier())
            .setProjectIdentifier(projectParams.getProjectIdentifier())
            .setMonitoredServiceIdentifier(monitoredServiceIdentifier)
            .setChangeSourceIdentifier(changeSourceIdentifier)
            .setEventDetails(customChangeEventDetailsBuilder.build())
            .setStartTime(customChangeWebhookPayload.getStartTime())
            .setEndTime(customChangeWebhookPayload.getEndTime())
            .setUser(customChangeWebhookPayload.getUser());

    if (customChangeWebhookPayload.getEventIdentifier() != null) {
      customChangeEventDTOBuilder.setEventIdentifier(customChangeWebhookPayload.getEventIdentifier());
    }

    return Message.newBuilder().setData(customChangeEventDTOBuilder.build().toByteString()).build();
  }
}
