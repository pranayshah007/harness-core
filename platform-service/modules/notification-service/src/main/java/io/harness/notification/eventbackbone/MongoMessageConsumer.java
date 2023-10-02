/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notification.eventbackbone;

import static io.harness.authorization.AuthorizationServiceHeader.NOTIFICATION_SERVICE;

import io.harness.notification.NotificationRequest;
import io.harness.notification.NotificationTriggerRequest;
import io.harness.notification.entities.MongoNotificationRequest;
import io.harness.notification.service.api.NotificationService;
import io.harness.queue.QueueConsumer;
import io.harness.queue.QueueListener;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.ServicePrincipal;

import com.google.inject.Inject;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MongoMessageConsumer extends QueueListener<MongoNotificationRequest> implements MessageConsumer {
  private NotificationService notificationService;

  private static final String NOTIFICATION_TRIGGER_TYPE = "io.harness.notification.NotificationTriggerRequest";

  @Inject
  public MongoMessageConsumer(
      QueueConsumer<MongoNotificationRequest> queueConsumer, NotificationService notificationService) {
    super(queueConsumer, true);
    this.notificationService = notificationService;
  }

  @Override
  public void onMessage(MongoNotificationRequest message) {
    if (message.getRequestType().equals(NOTIFICATION_TRIGGER_TYPE)) {
      processNotificationTriggerRequest(message);
      return;
    }
    try {
      NotificationRequest notificationRequest = NotificationRequest.parseFrom(message.getBytes());
      if (!notificationRequest.getUnknownFields().asMap().isEmpty()) {
        throw new InvalidProtocolBufferException("Unknown fields detected. Check Notification Request producer");
      }
      SecurityContextBuilder.setContext(new ServicePrincipal(NOTIFICATION_SERVICE.getServiceId()));
      notificationService.processNewMessage(notificationRequest);
    } catch (InvalidProtocolBufferException e) {
      log.error("Corrupted message received off the mongo queue");
    } finally {
      SecurityContextBuilder.unsetCompleteContext();
    }
  }

  private void processNotificationTriggerRequest(MongoNotificationRequest message) {
    try {
      NotificationTriggerRequest notificationTriggerRequest = NotificationTriggerRequest.parseFrom(message.getBytes());
      if (!notificationTriggerRequest.getUnknownFields().asMap().isEmpty()) {
        throw new InvalidProtocolBufferException(
            "Unknown fields detected. Check Notification Trigger Request producer");
      }
      SecurityContextBuilder.setContext(new ServicePrincipal(NOTIFICATION_SERVICE.getServiceId()));
      notificationService.processNewMessage(notificationTriggerRequest);
    } catch (InvalidProtocolBufferException e) {
      log.error("Corrupted message received off the mongo queue");
    } finally {
      SecurityContextBuilder.unsetCompleteContext();
    }
  }
}
