/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.events.producers;

import static io.harness.annotations.dev.HarnessTeam.IDP;
import static io.harness.eventsframework.EventsFrameworkConstants.IDP_MODULE_LICENSE_USAGE_CAPTURE_EVENT;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.CREATE_ACTION;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.producer.Message;
import io.harness.eventsframework.schemas.idp.IdpLicenseUsageCaptureEvent;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.protobuf.ByteString;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(IDP)
public class IdpServiceMiscRedisProducer {
  private final Producer eventProducer;

  @Inject
  public IdpServiceMiscRedisProducer(@Named(IDP_MODULE_LICENSE_USAGE_CAPTURE_EVENT) Producer eventProducer) {
    this.eventProducer = eventProducer;
  }

  public void publishIDPLicenseUsageUserCaptureDTOToRedis(
      String accountIdentifier, String userIdentifier, String email, String userName, long accessedAt) {
    try {
      String eventId = eventProducer.send(
          Message.newBuilder()
              .putAllMetadata(
                  Map.of("accountIdentifier", accountIdentifier, EventsFrameworkMetadataConstants.ENTITY_TYPE,
                      IDP_MODULE_LICENSE_USAGE_CAPTURE_EVENT, EventsFrameworkMetadataConstants.ACTION, CREATE_ACTION))
              .setData(
                  getIdpLicenseUsageCaptureEventData(accountIdentifier, userIdentifier, email, userName, accessedAt))
              .build());
      log.info(
          "Produced event {} to redis for IDPLicenseUsageUserCapture accountIdentifier {} userIdentifier {}, email {}, userName {}",
          eventId, accountIdentifier, userIdentifier, email, userName);
    } catch (Exception ex) {
      log.error(
          "Failed to produce event to redis for IDPLicenseUsageUserCapture accountIdentifier {} userIdentifier {}, email {}, userName {}. Error = {}",
          accountIdentifier, userIdentifier, email, userName, ex.getMessage(), ex);
      throw ex;
    }
  }

  private ByteString getIdpLicenseUsageCaptureEventData(
      String accountIdentifier, String userIdentifier, String email, String userName, long accessedAt) {
    return IdpLicenseUsageCaptureEvent.newBuilder()
        .setAccountIdentifier(accountIdentifier)
        .setUserIdentifier(userIdentifier)
        .setEmail(email)
        .setUserName(userName)
        .setAccessedAt(accessedAt)
        .build()
        .toByteString();
  }
}
