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
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.USER_GROUP;
import static io.harness.rule.OwnerRule.DEVESH;

import static org.junit.Assert.*;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.idp.events.eventlisteners.messagehandler.AsyncCatalogImportMessageHandler;
import io.harness.idp.events.eventlisteners.messagehandler.ConnectorMessageHandler;
import io.harness.idp.events.eventlisteners.messagehandler.SecretMessageHandler;
import io.harness.idp.events.eventlisteners.messagehandler.UserGroupMessageHandler;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class EventMessageHandlerFactoryTest extends CategoryTest {
  @InjectMocks EventMessageHandlerFactory eventMessageHandlerFactory;

  @Mock SecretMessageHandler secretMessageHandler;
  @Mock ConnectorMessageHandler gitIntegrationConnectorMessageHandler;
  @Mock UserGroupMessageHandler userGroupMessageHandler;
  @Mock AsyncCatalogImportMessageHandler asyncCatalogImportMessageHandler;

  @Before
  public void setup() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetEventMessageHandler() {
    assertEquals(secretMessageHandler, eventMessageHandlerFactory.getEventMessageHandler(SECRET_ENTITY));
    assertEquals(
        gitIntegrationConnectorMessageHandler, eventMessageHandlerFactory.getEventMessageHandler(CONNECTOR_ENTITY));
    assertEquals(asyncCatalogImportMessageHandler,
        eventMessageHandlerFactory.getEventMessageHandler(ASYNC_CATALOG_IMPORT_ENTITY));
    assertEquals(userGroupMessageHandler, eventMessageHandlerFactory.getEventMessageHandler(USER_GROUP));
    assertNull(eventMessageHandlerFactory.getEventMessageHandler("test-invalid"));
  }
}
