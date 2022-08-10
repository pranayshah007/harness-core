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
import org.springframework.data.mongodb.core.MongoTemplate;

@UtilityClass
@Slf4j
public class NgQueueFactory {
  public static <T extends Queuable> QueuePublisher<T> createNgQueuePublisher(Injector injector, Class<T> klass,
      List<String> topicPrefixElements, PublisherConfiguration configuration, MongoTemplate mongoTemplate) {
    if (configuration.isPublisherActive(klass)) {
      final io.harness.mongo.queue.NGMongoQueuePublisher mongoQueuePublisher =
          new NGMongoQueuePublisher(klass.getSimpleName(), topicPrefixElements, mongoTemplate);
      injector.injectMembers(mongoQueuePublisher);
      return mongoQueuePublisher;
    } else {
      log.error("NoOpQueue has been setup for eventType:[{}]", klass.getName());
      return new NoopQueuePublisher();
    }
  }

  public static <T extends Queuable> QueueConsumer<T> createNgQueueConsumer(Injector injector, Class<T> klass,
      Duration heartbeat, List<List<String>> topicExpression, PublisherConfiguration configuration,
      MongoTemplate mongoTemplate) {
    if (configuration.isPublisherActive(klass)) {
      final io.harness.mongo.queue.NGMongoQueueConsumer mongoQueueConsumer =
          new NGMongoQueueConsumer(klass, heartbeat, topicExpression, mongoTemplate);
      injector.injectMembers(mongoQueueConsumer);
      return mongoQueueConsumer;
    } else {
      log.error("NoOpQueue has been setup for eventType:[{}]", klass.getName());
      return new NoopQueueConsumer<>();
    }
  }
}
