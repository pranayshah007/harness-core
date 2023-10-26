/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.events.consumers;

import static io.harness.eventsframework.EventsFrameworkConstants.IDP_MODULE_LICENSE_USAGE_CAPTURE_EVENT;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.CREATE_ACTION;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.api.Consumer;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.schemas.idp.IdpLicenseUsageCaptureEvent;
import io.harness.idp.events.eventlisteners.eventhandler.utils.ResourceLocker;
import io.harness.idp.license.usage.dto.IDPLicenseUsageUserCaptureDTO;
import io.harness.idp.license.usage.service.IDPModuleLicenseUsage;
import io.harness.queue.QueueController;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class IdpModuleLicenseUsageCaptureEventConsumer extends AbstractIdpServiceRedisStreamConsumer {
  private static final String CONSUMER_NAME = "IdpModuleLicenseUsageCaptureEventConsumer";

  @Inject private IDPModuleLicenseUsage idpModuleLicenseUsage;

  @Inject
  public IdpModuleLicenseUsageCaptureEventConsumer(
      @Named(IDP_MODULE_LICENSE_USAGE_CAPTURE_EVENT) Consumer redisConsumer, QueueController queueController,
      ResourceLocker resourceLocker) {
    super(redisConsumer, queueController, resourceLocker);
  }

  @Override
  protected boolean processMessage(Message message) {
    log.info("Processing message = {} in {} consumer", message, CONSUMER_NAME);
    if (message != null && message.hasMessage()) {
      try {
        boolean entityTypeAndActionValidation = entityTypeAndActionValidation(
            CONSUMER_NAME, message, IDP_MODULE_LICENSE_USAGE_CAPTURE_EVENT, CREATE_ACTION);
        if (entityTypeAndActionValidation) {
          ByteString data = message.getMessage().getData();
          IdpLicenseUsageCaptureEvent idpLicenseUsageCaptureEvent = IdpLicenseUsageCaptureEvent.parseFrom(data);
          lockAndProcessData(CONSUMER_NAME + "_EVENT_" + idpLicenseUsageCaptureEvent.getAccountIdentifier() + "_"
                  + idpLicenseUsageCaptureEvent.getUserIdentifier(),
              data);
        }
      } catch (Exception ex) {
        log.error(
            "Error in processing message = {} in {} consumer. Error = {}", message, CONSUMER_NAME, ex.getMessage(), ex);
        return false;
      }
      log.info("Processed messageId = {} in {} consumer", message.getId(), CONSUMER_NAME);
    }
    return true;
  }

  @Override
  protected void processInternal(ByteString data) throws Exception {
    IdpLicenseUsageCaptureEvent idpLicenseUsageCaptureEvent = IdpLicenseUsageCaptureEvent.parseFrom(data);
    IDPLicenseUsageUserCaptureDTO idpLicenseUsageUserCapture =
        getIdpLicenseUsageCaptureDto(idpLicenseUsageCaptureEvent);
    idpModuleLicenseUsage.saveLicenseUsageInDB(idpLicenseUsageUserCapture);
  }

  private IDPLicenseUsageUserCaptureDTO getIdpLicenseUsageCaptureDto(
      IdpLicenseUsageCaptureEvent idpLicenseUsageCaptureEvent) {
    return IDPLicenseUsageUserCaptureDTO.builder()
        .accountIdentifier(idpLicenseUsageCaptureEvent.getAccountIdentifier())
        .userIdentifier(idpLicenseUsageCaptureEvent.getUserIdentifier())
        .email(idpLicenseUsageCaptureEvent.getEmail())
        .userName(idpLicenseUsageCaptureEvent.getUserName())
        .accessedAt(idpLicenseUsageCaptureEvent.getAccessedAt())
        .build();
  }
}
