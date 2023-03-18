/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.service.impl;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.core.ApiFuture;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.common.collect.ImmutableMap;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.TopicName;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@UtilityClass
@Slf4j
@OwnedBy(HarnessTeam.CE)
public class EntityChangeEventServiceHelper {
  public void publishMessage(ArrayList<ImmutableMap<String, String>> entityChangeEvents, String harnessGcpProjectId,
      String inventoryPubSubTopic, GoogleCredentials sourceGcpCredentials) {
    if (entityChangeEvents.isEmpty()) {
      log.info("Visibility is not enabled. Not sending event");
      return;
    }

    if (sourceGcpCredentials == null) {
      log.info("WI: Using workload identity");
      try {
        sourceGcpCredentials = GoogleCredentials.getApplicationDefault();
      } catch (IOException e) {
        log.error("Exception in using Google ADC", e);
      }
    }
    TopicName topicName = TopicName.of(harnessGcpProjectId, inventoryPubSubTopic);
    Publisher publisher = null;
    log.info("Publishing event to topic: {}", topicName);
    try {
      // Create a publisher instance with default settings bound to the topic
      publisher = Publisher.newBuilder(topicName)
                      .setCredentialsProvider(FixedCredentialsProvider.create(sourceGcpCredentials))
                      .build();
      ObjectMapper objectMapper = new ObjectMapper();
      String message = objectMapper.writeValueAsString(entityChangeEvents);
      ByteString data = ByteString.copyFromUtf8(message);
      log.info("Sending event with data: {}", data);
      PubsubMessage pubsubMessage = PubsubMessage.newBuilder().setData(data).build();

      // Once published, returns a server-assigned message id (unique within the topic)
      ApiFuture<String> messageIdFuture = publisher.publish(pubsubMessage);
      String messageId = messageIdFuture.get();
      log.info("Published event with data: {}, messageId: {}", message, messageId);
    } catch (Exception e) {
      log.error("Error occurred while sending event in pubsub\n", e);
    }

    if (publisher != null) {
      // When finished with the publisher, shutdown to free up resources.
      publisher.shutdown();
      try {
        publisher.awaitTermination(1, TimeUnit.MINUTES);
      } catch (InterruptedException e) {
        log.error("Error occurred while terminating pubsub publisher\n", e);
      }
    }
  }
}
