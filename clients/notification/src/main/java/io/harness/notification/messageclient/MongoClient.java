/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notification.messageclient;

import io.harness.notification.NotificationRequest;
import io.harness.notification.entities.MongoNotificationRequest;
import io.harness.queue.QueuePublisher;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class MongoClient implements MessageClient {
  @Inject QueuePublisher<MongoNotificationRequest> producer;

  @Override
  public void send(NotificationRequest notificationRequest, String accountId) {
    log.info("Mongo sending notification to producer: {}", notificationRequest);
    byte[] message = notificationRequest.toByteArray();
    producer.send(MongoNotificationRequest.builder().bytes(message).build());
  }
}
