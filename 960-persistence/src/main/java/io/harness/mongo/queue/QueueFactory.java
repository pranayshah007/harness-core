/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.mongo.queue;

import io.harness.config.PublisherConfiguration;
import io.harness.queue.NoopQueueConsumer;
import io.harness.queue.NoopQueuePublisher;
import io.harness.queue.Queuable;
import io.harness.queue.QueueConsumer;
import io.harness.queue.QueuePublisher;

import com.google.inject.Injector;
import java.time.Duration;
import java.util.List;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@UtilityClass
@Slf4j
public class QueueFactory {
  public static <T extends Queuable> QueuePublisher<T> createQueuePublisher(
      Injector injector, Class<T> klass, List<String> topicPrefixElements, PublisherConfiguration configuration) {
    if (configuration.isPublisherActive(klass)) {
      final MongoQueuePublisher mongoQueuePublisher =
          new MongoQueuePublisher(klass.getSimpleName(), topicPrefixElements);
      injector.injectMembers(mongoQueuePublisher);
      return mongoQueuePublisher;
    } else {
      log.error("NoOpQueue has been setup for eventType:[{}]", klass.getName());
      return new NoopQueuePublisher();
    }
  }

  public static <T extends Queuable> QueueConsumer<T> createQueueConsumer(Injector injector, Class<T> klass,
      Duration heartbeat, List<List<String>> topicExpression, PublisherConfiguration configuration) {
    if (configuration.isPublisherActive(klass)) {
      final MongoQueueConsumer mongoQueueConsumer = new MongoQueueConsumer(klass, heartbeat, topicExpression);
      injector.injectMembers(mongoQueueConsumer);
      return mongoQueueConsumer;
    } else {
      log.error("NoOpQueue has been setup for eventType:[{}]", klass.getName());
      return new NoopQueueConsumer<>();
    }
  }
}
