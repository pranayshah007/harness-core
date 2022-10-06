/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.debezium;

import io.harness.exception.InvalidRequestException;

import com.google.inject.Inject;

public class ChangeConsumerFactory {
  @Inject private DebeziumProducerFactory producerFactory;

  @SuppressWarnings("unchecked")
  public <T extends MongoCollectionChangeConsumer> T get(long sleepInterval, String collectionName,
      ChangeConsumerConfig changeConsumerConfig, long producingCountPerBatch, int redisStreamSize) {
    ConsumerType consumerType = changeConsumerConfig.getConsumerType();
    if (consumerType != null) {
      switch (consumerType) {
        case EVENTS_FRAMEWORK:
          return (T) new EventsFrameworkChangeConsumer(
              sleepInterval, collectionName, producerFactory, producingCountPerBatch, redisStreamSize);
        default:
          throw new InvalidRequestException("Change Consumer not Supported for " + consumerType.toString());
      }
    } else {
      throw new InvalidRequestException("Consumer Type is null");
    }
  }
}
