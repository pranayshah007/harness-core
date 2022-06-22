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
  public <T extends MongoCollectionChangeConsumer> T get(
      String collectionName, ChangeConsumerConfig changeConsumerConfig) {
    ConsumerType consumerType = changeConsumerConfig.getConsumerType();

    switch (consumerType) {
      case EVENTS_FRAMEWORK:
        return (T) new EventsFrameworkChangeConsumer(collectionName, producerFactory);
      default:
        throw new InvalidRequestException("Change Consumer not Supported for " + consumerType.toString());
    }
  }
}
