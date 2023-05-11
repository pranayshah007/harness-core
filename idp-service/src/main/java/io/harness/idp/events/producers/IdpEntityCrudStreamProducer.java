/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.events.producers;

import static io.harness.annotations.dev.HarnessTeam.IDP;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ASYNC_CATALOG_IMPORT_ENTITY;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.EventsFrameworkConstants;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.eventsframework.api.EventsFrameworkDownException;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.entity_crud.EntityChangeDTO;
import io.harness.eventsframework.producer.Message;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.protobuf.ByteString;
import com.google.protobuf.StringValue;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(IDP)
public class IdpEntityCrudStreamProducer {
  private final Producer eventProducer;

  @Inject
  public IdpEntityCrudStreamProducer(@Named(EventsFrameworkConstants.ENTITY_CRUD) Producer eventProducer) {
    this.eventProducer = eventProducer;
  }

  public boolean publishAsyncCatalogImportChangeEventToRedis(String accountIdentifier, String action) {
    try {
      String eventId = eventProducer.send(
          Message.newBuilder()
              .putAllMetadata(Map.of("accountId", accountIdentifier, EventsFrameworkMetadataConstants.ENTITY_TYPE,
                  ASYNC_CATALOG_IMPORT_ENTITY, EventsFrameworkMetadataConstants.ACTION, action))
              .setData(getAsyncCatalogImportPayload(accountIdentifier))
              .build());
      log.info("Produced event id:[{}] for asyncCatalogImportAccountId: [{}], action:[{}]", eventId, accountIdentifier,
          action);
    } catch (EventsFrameworkDownException e) {
      log.error(
          "Failed to send event to events framework asyncCatalogImport accountIdentifier: " + accountIdentifier, e);
      return false;
    }
    return true;
  }

  private ByteString getAsyncCatalogImportPayload(String accountIdentifier) {
    return EntityChangeDTO.newBuilder().setAccountIdentifier(StringValue.of(accountIdentifier)).build().toByteString();
  }
}
