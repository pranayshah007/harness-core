/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.jobs;

import static io.harness.eventsframework.EventsFrameworkConstants.CUSTOM_CHANGE_EVENT;

import io.harness.cvng.core.beans.CustomChangeWebhookEvent;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.producer.Message;
import io.harness.eventsframework.schemas.cv.CustomChangeEventDTO;
import io.harness.eventsframework.schemas.cv.CustomChangeEventDetails;

import com.google.inject.Inject;
import com.google.inject.name.Named;

public class CustomChangeEventPublisherServiceImpl implements CustomChangeEventPublisherService {
  @Inject @Named(CUSTOM_CHANGE_EVENT) private Producer eventProducer;

  @Override
  public void registerCustomChangeEvent(ProjectParams projectParams, String monitoredServiceIdentifier,
      String changeSourceIdentifier, CustomChangeWebhookEvent customChangeWebhookEvent) {
    CustomChangeEventDTO customChangeEventDTO =
        CustomChangeEventDTO.newBuilder()
            .setAccountId(projectParams.getAccountIdentifier())
            .setOrgIdentifier(projectParams.getOrgIdentifier())
            .setProjectIdentifier(projectParams.getProjectIdentifier())
            .setMonitoredServiceIdentifier(monitoredServiceIdentifier)
            .setChangeSourceIdentifier(changeSourceIdentifier)
            .setEventDetails(CustomChangeEventDetails.newBuilder()
                                 .setChangeEventDetailsLink(customChangeWebhookEvent.getChangeEventDetailsLink())
                                 .setInternalLinkToEntity(customChangeWebhookEvent.getInternalLinkToEntity())
                                 .setDescription(customChangeWebhookEvent.getDescription())
                                 .build())
            .setStartTime(customChangeWebhookEvent.getStartTime())
            .setEndTime(customChangeWebhookEvent.getEndTime())
            .setUser(customChangeWebhookEvent.getUser())
            .build();

    Message message = Message.newBuilder().setData(customChangeEventDTO.toByteString()).build();
    eventProducer.send(message);
  }
}
