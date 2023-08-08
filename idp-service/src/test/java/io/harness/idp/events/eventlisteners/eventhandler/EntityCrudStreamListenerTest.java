/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.events.eventlisteners.eventhandler;

import static io.harness.rule.OwnerRule.DEVESH;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.entity_crud.EntityChangeDTO;
import io.harness.eventsframework.entity_crud.account.AccountEntityChangeDTO;
import io.harness.idp.events.eventlisteners.eventhandler.utils.ResourceLocker;
import io.harness.idp.events.eventlisteners.factory.EventMessageHandlerFactory;
import io.harness.idp.events.eventlisteners.messagehandler.ConnectorMessageHandler;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableMap;
import com.google.protobuf.StringValue;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.IDP)
public class EntityCrudStreamListenerTest extends CategoryTest {
  @Mock EventMessageHandlerFactory eventMessageHandlerFactory;
  @Mock ResourceLocker resourceLocker;
  @InjectMocks EntityCrudStreamListener entityCrudStreamListener;

  @Mock ConnectorMessageHandler connectorMessageHandler;

  public static final String TEST_ACCOUNT_ID = "test-account-id";
  private static final String TEST_CONNECTOR_ID = "test-connector-id";

  @Before
  public void setup() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testHandleMessage() {
    assertTrue(entityCrudStreamListener.handleMessage(null));

    Message message =
        Message.newBuilder()
            .setMessage(
                io.harness.eventsframework.producer.Message.newBuilder()
                    .putAllMetadata(ImmutableMap.of("accountId", TEST_ACCOUNT_ID,
                        EventsFrameworkMetadataConstants.ACTION, EventsFrameworkMetadataConstants.DELETE_ACTION))
                    .setData(AccountEntityChangeDTO.newBuilder().setAccountId(TEST_ACCOUNT_ID).build().toByteString())
                    .build())
            .build();
    assertTrue(entityCrudStreamListener.handleMessage(message));
    message =
        Message.newBuilder()
            .setMessage(
                io.harness.eventsframework.producer.Message.newBuilder()
                    .putAllMetadata(
                        ImmutableMap.of("accountId", TEST_ACCOUNT_ID, EventsFrameworkMetadataConstants.ENTITY_TYPE,
                            EventsFrameworkMetadataConstants.CONNECTOR_ENTITY))
                    .setData(AccountEntityChangeDTO.newBuilder().setAccountId(TEST_ACCOUNT_ID).build().toByteString())
                    .build())
            .build();
    assertTrue(entityCrudStreamListener.handleMessage(message));

    message =
        Message.newBuilder()
            .setMessage(
                io.harness.eventsframework.producer.Message.newBuilder()
                    .putAllMetadata(ImmutableMap.of("accountId", TEST_ACCOUNT_ID,
                        EventsFrameworkMetadataConstants.ENTITY_TYPE, EventsFrameworkMetadataConstants.CONNECTOR_ENTITY,
                        EventsFrameworkMetadataConstants.ACTION, EventsFrameworkMetadataConstants.DELETE_ACTION))
                    .setData(EntityChangeDTO.newBuilder()
                                 .setAccountIdentifier(StringValue.of(TEST_ACCOUNT_ID))
                                 .setIdentifier(StringValue.of(TEST_CONNECTOR_ID))
                                 .build()
                                 .toByteString())
                    .build())
            .build();
    when(eventMessageHandlerFactory.getEventMessageHandler(any())).thenReturn(connectorMessageHandler);
    assertTrue(entityCrudStreamListener.handleMessage(message));

    message = Message.newBuilder()
                  .setMessage(
                      io.harness.eventsframework.producer.Message.newBuilder()
                          .putAllMetadata(ImmutableMap.of("accountId", TEST_ACCOUNT_ID,
                              EventsFrameworkMetadataConstants.ENTITY_TYPE,
                              EventsFrameworkMetadataConstants.ASYNC_CATALOG_IMPORT_ENTITY,
                              EventsFrameworkMetadataConstants.ACTION, EventsFrameworkMetadataConstants.DELETE_ACTION))
                          .setData(EntityChangeDTO.newBuilder()
                                       .setIdentifier(StringValue.of(TEST_CONNECTOR_ID))
                                       .build()
                                       .toByteString())
                          .build())
                  .build();
    assertTrue(entityCrudStreamListener.handleMessage(message));

    when(eventMessageHandlerFactory.getEventMessageHandler(any())).thenReturn(null);
    assertTrue(entityCrudStreamListener.handleMessage(message));
  }
}
