/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.events.producers;

import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ASYNC_CATALOG_IMPORT_ENTITY;
import static io.harness.rule.OwnerRule.VIKYATH_HAREKAL;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.eventsframework.api.EventsFrameworkDownException;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.entity_crud.EntityChangeDTO;
import io.harness.eventsframework.producer.Message;
import io.harness.rule.Owner;

import com.google.protobuf.ByteString;
import com.google.protobuf.StringValue;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.IDP)
public class IdpEntityCrudStreamProducerTest extends CategoryTest {
  public static final String ACCOUNT_IDENTIFIER = "example-account";
  public static final String ACTION = "import";

  @Mock private Producer eventProducer;

  private IdpEntityCrudStreamProducer streamProducer;

  @Before
  public void setup() {
    MockitoAnnotations.openMocks(this);
    streamProducer = new IdpEntityCrudStreamProducer(eventProducer);
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testPublishAsyncCatalogImportChangeEventToRedisSuccessful() throws EventsFrameworkDownException {
    String eventId = "test-event-id";
    when(eventProducer.send(any(Message.class))).thenReturn(eventId);

    boolean result = streamProducer.publishAsyncCatalogImportChangeEventToRedis(ACCOUNT_IDENTIFIER, ACTION);

    assertTrue(result);

    ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
    verify(eventProducer).send(messageCaptor.capture());
    Message sentMessage = messageCaptor.getValue();
    assertEquals(ACCOUNT_IDENTIFIER, sentMessage.getMetadataOrDefault("accountId", ""));
    assertEquals(ASYNC_CATALOG_IMPORT_ENTITY,
        sentMessage.getMetadataOrDefault(EventsFrameworkMetadataConstants.ENTITY_TYPE, ""));
    assertEquals(ACTION, sentMessage.getMetadataOrDefault(EventsFrameworkMetadataConstants.ACTION, ""));
    ByteString expectedPayload =
        EntityChangeDTO.newBuilder().setAccountIdentifier(StringValue.of(ACCOUNT_IDENTIFIER)).build().toByteString();
    assertEquals(expectedPayload, sentMessage.getData());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testPublishAsyncCatalogImportChangeEventToRedisFailure() throws EventsFrameworkDownException {
    when(eventProducer.send(any(Message.class))).thenThrow(EventsFrameworkDownException.class);

    boolean result = streamProducer.publishAsyncCatalogImportChangeEventToRedis(ACCOUNT_IDENTIFIER, ACTION);

    assertFalse(result);
  }
}
