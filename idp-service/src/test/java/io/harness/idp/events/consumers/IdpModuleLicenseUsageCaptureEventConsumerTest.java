/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.events.consumers;

import static io.harness.eventsframework.EventsFrameworkConstants.IDP_MODULE_LICENSE_USAGE_CAPTURE_EVENT;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.CREATE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ENTITY_TYPE;
import static io.harness.rule.OwnerRule.SATHISH;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.schemas.idp.IdpLicenseUsageCaptureEvent;
import io.harness.idp.events.eventlisteners.eventhandler.utils.ResourceLocker;
import io.harness.idp.license.usage.service.IDPModuleLicenseUsage;
import io.harness.lock.redis.RedisAcquiredLock;
import io.harness.rule.Owner;

import com.google.protobuf.ByteString;
import java.util.Map;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class IdpModuleLicenseUsageCaptureEventConsumerTest extends CategoryTest {
  static final String TEST_ACCOUNT_IDENTIFIER = "testAccount123";
  static final String TEST_USER_IDENTIFIER = "testUser123";
  static final String TEST_USER_EMAIL = "testEmail123";
  static final String TEST_USER_NAME = "testName123";
  static final long TEST_LAST_ACCESSED_AT = 1698294600000L;

  @Mock ResourceLocker resourceLocker;
  @Mock IDPModuleLicenseUsage idpModuleLicenseUsage;

  @InjectMocks @Spy IdpModuleLicenseUsageCaptureEventConsumer idpModuleLicenseUsageCaptureEventConsumer;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    assertTrue(true);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testProcessMessage() throws InterruptedException {
    ByteString data = IdpLicenseUsageCaptureEvent.newBuilder()
                          .setAccountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                          .setUserIdentifier(TEST_USER_IDENTIFIER)
                          .setEmail(TEST_USER_EMAIL)
                          .setUserName(TEST_USER_NAME)
                          .setAccessedAt(TEST_LAST_ACCESSED_AT)
                          .build()
                          .toByteString();
    Message message =
        Message.newBuilder()
            .setId("test-event-id")
            .setMessage(
                io.harness.eventsframework.producer.Message.newBuilder()
                    .putAllMetadata(Map.of(ENTITY_TYPE, IDP_MODULE_LICENSE_USAGE_CAPTURE_EVENT, ACTION, CREATE_ACTION))
                    .setData(data)
                    .build())
            .build();

    when(resourceLocker.acquireLock(any())).thenReturn(RedisAcquiredLock.builder().build());
    doNothing().when(resourceLocker).releaseLock(any());
    doNothing().when(idpModuleLicenseUsage).saveLicenseUsageInDB(any());

    boolean result = idpModuleLicenseUsageCaptureEventConsumer.processMessage(message);
    assertTrue(result);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testProcessMessageValidationFailure() throws InterruptedException {
    ByteString data = IdpLicenseUsageCaptureEvent.newBuilder()
                          .setAccountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                          .setUserIdentifier(TEST_USER_IDENTIFIER)
                          .setEmail(TEST_USER_EMAIL)
                          .setUserName(TEST_USER_NAME)
                          .setAccessedAt(TEST_LAST_ACCESSED_AT)
                          .build()
                          .toByteString();
    Message message = Message.newBuilder()
                          .setId("test-event-id")
                          .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                                          .putAllMetadata(Map.of(ACTION, CREATE_ACTION))
                                          .setData(data)
                                          .build())
                          .build();

    when(resourceLocker.acquireLock(any())).thenReturn(RedisAcquiredLock.builder().build());
    doNothing().when(resourceLocker).releaseLock(any());
    doNothing().when(idpModuleLicenseUsage).saveLicenseUsageInDB(any());

    boolean result = idpModuleLicenseUsageCaptureEventConsumer.processMessage(message);
    assertTrue(result);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testProcessMessageError() throws InterruptedException {
    ByteString data = IdpLicenseUsageCaptureEvent.newBuilder()
                          .setAccountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                          .setUserIdentifier(TEST_USER_IDENTIFIER)
                          .setEmail(TEST_USER_EMAIL)
                          .setUserName(TEST_USER_NAME)
                          .setAccessedAt(TEST_LAST_ACCESSED_AT)
                          .build()
                          .toByteString();
    Message message =
        Message.newBuilder()
            .setId("test-event-id")
            .setMessage(
                io.harness.eventsframework.producer.Message.newBuilder()
                    .putAllMetadata(Map.of(ENTITY_TYPE, IDP_MODULE_LICENSE_USAGE_CAPTURE_EVENT, ACTION, CREATE_ACTION))
                    .setData(data)
                    .build())
            .build();

    when(resourceLocker.acquireLock(any())).thenReturn(RedisAcquiredLock.builder().build());
    doNothing().when(resourceLocker).releaseLock(any());
    willAnswer(invocation -> { throw new Exception("Exception Throw"); })
        .given(idpModuleLicenseUsage)
        .saveLicenseUsageInDB(any());

    boolean result = idpModuleLicenseUsageCaptureEventConsumer.processMessage(message);
    assertFalse(result);
  }
}
