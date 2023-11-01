/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.events.producers;

import static io.harness.eventsframework.EventsFrameworkConstants.IDP_MODULE_LICENSE_USAGE_CAPTURE_EVENT;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.CREATE_ACTION;
import static io.harness.rule.OwnerRule.SATHISH;

import static junit.framework.TestCase.assertEquals;
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
import io.harness.eventsframework.producer.Message;
import io.harness.eventsframework.schemas.idp.IdpLicenseUsageCaptureEvent;
import io.harness.rule.Owner;

import com.google.protobuf.ByteString;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class IdpServiceMiscRedisProducerTest extends CategoryTest {
  static final String TEST_ACCOUNT_IDENTIFIER = "testAccount123";
  static final String TEST_USER_IDENTIFIER = "testUser123";
  static final String TEST_USER_EMAIL = "testEmail123";
  static final String TEST_USER_NAME = "testName123";
  static final long TEST_LAST_ACCESSED_AT = 1698294600000L;

  @Mock Producer eventProducer;
  IdpServiceMiscRedisProducer streamProducer;

  @Before
  public void setup() {
    MockitoAnnotations.openMocks(this);
    streamProducer = new IdpServiceMiscRedisProducer(eventProducer);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testPublishIDPLicenseUsageUserCaptureDTOToRedisSuccessful() throws EventsFrameworkDownException {
    String eventId = "test-event-id";
    when(eventProducer.send(any(Message.class))).thenReturn(eventId);

    streamProducer.publishIDPLicenseUsageUserCaptureDTOToRedis(
        TEST_ACCOUNT_IDENTIFIER, TEST_USER_IDENTIFIER, TEST_USER_EMAIL, TEST_USER_NAME, TEST_LAST_ACCESSED_AT);

    ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
    verify(eventProducer).send(messageCaptor.capture());
    Message sentMessage = messageCaptor.getValue();
    assertEquals(TEST_ACCOUNT_IDENTIFIER, sentMessage.getMetadataOrDefault("accountIdentifier", ""));
    assertEquals(IDP_MODULE_LICENSE_USAGE_CAPTURE_EVENT,
        sentMessage.getMetadataOrDefault(EventsFrameworkMetadataConstants.ENTITY_TYPE, ""));
    assertEquals(CREATE_ACTION, sentMessage.getMetadataOrDefault(EventsFrameworkMetadataConstants.ACTION, ""));
    ByteString expectedPayload = IdpLicenseUsageCaptureEvent.newBuilder()
                                     .setAccountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                     .setUserIdentifier(TEST_USER_IDENTIFIER)
                                     .setEmail(TEST_USER_EMAIL)
                                     .setUserName(TEST_USER_NAME)
                                     .setAccessedAt(TEST_LAST_ACCESSED_AT)
                                     .build()
                                     .toByteString();
    assertEquals(expectedPayload, sentMessage.getData());
  }

  @Test(expected = Exception.class)
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testPublishIDPLicenseUsageUserCaptureDTOToRedisFailure() {
    when(eventProducer.send(any(Message.class))).thenThrow(Exception.class);

    streamProducer.publishIDPLicenseUsageUserCaptureDTOToRedis(
        TEST_ACCOUNT_IDENTIFIER, TEST_USER_IDENTIFIER, TEST_USER_EMAIL, TEST_USER_NAME, TEST_LAST_ACCESSED_AT);
  }
}
