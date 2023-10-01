/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.notification.event;

import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACCOUNT_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.DELETE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ENTITY_TYPE;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.UPDATE_ACTION;
import static io.harness.rule.OwnerRule.SAHIBA;

import static junit.framework.TestCase.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.consumer.Message;
import io.harness.notification.eventframework.NotificationEntityCrudStreamListener;
import io.harness.notification.service.api.NotificationService;
import io.harness.notification.service.api.NotificationSettingsService;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class NotificationEntityCrudStreamListenerTest extends CategoryTest {
  @Mock private NotificationSettingsService notificationSettingsService;
  @Mock private NotificationService notificationService;
  @InjectMocks NotificationEntityCrudStreamListener notificationEntityCrudStreamListener;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = SAHIBA)
  @Category(UnitTests.class)
  public void testEmptyHandleMessage() {
    Message message = Message.newBuilder().build();
    assertTrue(notificationEntityCrudStreamListener.handleMessage(message));
  }

  @Test
  @Owner(developers = SAHIBA)
  @Category(UnitTests.class)
  public void testNotificationDeleteEventHandleMessage() {
    Message message = Message.newBuilder()
                          .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                                          .putMetadata(ENTITY_TYPE, ACCOUNT_ENTITY)
                                          .putMetadata(ACTION, DELETE_ACTION)
                                          .build())
                          .build();
    assertTrue(notificationEntityCrudStreamListener.handleMessage(message));
    verify(notificationSettingsService, times(1)).deleteByAccount(any());
    verify(notificationService, times(1)).deleteByAccountIdentifier(any());
  }

  @Test
  @Owner(developers = SAHIBA)
  @Category(UnitTests.class)
  public void testEventHandleMessageWithNonDeleteAction() {
    Message message = Message.newBuilder()
                          .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                                          .putMetadata(ENTITY_TYPE, ACCOUNT_ENTITY)
                                          .putMetadata(ACTION, UPDATE_ACTION)
                                          .build())
                          .build();
    assertTrue(notificationEntityCrudStreamListener.handleMessage(message));
    verify(notificationSettingsService, times(0)).deleteByAccount(any());
    verify(notificationService, times(0)).deleteByAccountIdentifier(any());
  }
}
