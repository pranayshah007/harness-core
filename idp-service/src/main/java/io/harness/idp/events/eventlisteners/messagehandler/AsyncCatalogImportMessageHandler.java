/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.events.eventlisteners.messagehandler;

import static io.harness.eventsframework.EventsFrameworkMetadataConstants.CREATE_ACTION;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.entity_crud.EntityChangeDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.events.eventlisteners.utility.EventListenerLogger;
import io.harness.idp.onboarding.service.impl.OnboardingServiceImpl;

import com.google.inject.Inject;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class AsyncCatalogImportMessageHandler implements EventMessageHandler {
  private OnboardingServiceImpl onboardingService;

  @Override
  public void handleMessage(Message message, String action) {
    EntityChangeDTO entityChangeDTO;
    try {
      entityChangeDTO = EntityChangeDTO.parseFrom(message.getMessage().getData());
    } catch (InvalidProtocolBufferException e) {
      throw new InvalidRequestException(
          String.format("Exception in unpacking EntityChangeDTO for id %s", message.getId()), e);
    }
    if (entityChangeDTO != null) {
      EventListenerLogger.logForEventReceived(message);
      if (action.equals(CREATE_ACTION)) {
        onboardingService.asyncCatalogImport(entityChangeDTO);
      } else {
        log.warn("ACTION - {} is not to be handled by IDP async catalog import event handler", action);
      }
    }
  }
}
