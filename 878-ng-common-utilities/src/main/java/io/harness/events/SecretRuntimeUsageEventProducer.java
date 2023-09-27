/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.events;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.EventsFrameworkConstants;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.producer.Message;
import io.harness.eventsframework.schemas.entityactivity.EntityActivityCreateDTO;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDP)
@Slf4j
public class SecretRuntimeUsageEventProducer {
  private final Producer eventProducer;

  @Inject
  public SecretRuntimeUsageEventProducer(@Named(EventsFrameworkConstants.ENTITY_ACTIVITY) Producer eventProducer) {
    this.eventProducer = eventProducer;
  }

  public void publishEvent(
      String accountIdentifier, String secretIdentifier, EntityActivityCreateDTO entityActivityCreateDTO) {
    try {
      eventProducer.send(
          Message.newBuilder()
              .putAllMetadata(ImmutableMap.of("accountId", accountIdentifier,
                  EventsFrameworkMetadataConstants.ENTITY_TYPE, EventsFrameworkConstants.ENTITY_ACTIVITY,
                  EventsFrameworkMetadataConstants.ACTION, EventsFrameworkMetadataConstants.CREATE_ACTION))
              .setData(entityActivityCreateDTO.toByteString())
              .build());
    } catch (Exception ex) {
      log.error("Failed to send event to events framework for secret runtime usage, secretId: {}, accountId: {} ",
          secretIdentifier, accountIdentifier, ex);
    }
  }
}
