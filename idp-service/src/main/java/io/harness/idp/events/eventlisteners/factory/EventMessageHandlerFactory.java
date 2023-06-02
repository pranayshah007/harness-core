/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.events.eventlisteners.factory;

import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ASYNC_CATALOG_IMPORT_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.CONNECTOR_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.SECRET_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.USER_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.USER_GROUP;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.events.eventlisteners.messagehandler.AsyncCatalogImportMessageHandler;
import io.harness.idp.events.eventlisteners.messagehandler.ConnectorMessageHandler;
import io.harness.idp.events.eventlisteners.messagehandler.EventMessageHandler;
import io.harness.idp.events.eventlisteners.messagehandler.SecretMessageHandler;
import io.harness.idp.events.eventlisteners.messagehandler.UserMessageHandler;

import lombok.AllArgsConstructor;

@AllArgsConstructor(onConstructor = @__({ @com.google.inject.Inject }))
@OwnedBy(HarnessTeam.IDP)
public class EventMessageHandlerFactory {
  SecretMessageHandler secretMessageHandler;
  ConnectorMessageHandler gitIntegrationConnectorMessageHandler;
  UserMessageHandler userMessageHandler;
  AsyncCatalogImportMessageHandler asyncCatalogImportMessageHandler;

  public EventMessageHandler getEventMessageHandler(String entity) {
    switch (entity) {
      case SECRET_ENTITY:
        return secretMessageHandler;
      case CONNECTOR_ENTITY:
        return gitIntegrationConnectorMessageHandler;
      case USER_ENTITY:
      case USER_GROUP:
        return userMessageHandler;
      case ASYNC_CATALOG_IMPORT_ENTITY:
        return asyncCatalogImportMessageHandler;
      default:
        return null;
    }
  }
}
