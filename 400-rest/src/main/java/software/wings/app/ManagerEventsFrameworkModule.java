/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.app;

import static io.harness.authorization.AuthorizationServiceHeader.MANAGER;
import static io.harness.eventsframework.EventsFrameworkConstants.CG_GENERAL_EVENT;
import static io.harness.eventsframework.EventsFrameworkConstants.CG_GENERAL_EVENT_BATCH_SIZE;
import static io.harness.eventsframework.EventsFrameworkConstants.CG_GENERAL_EVENT_MAX_PROCESSING_TIME;
import static io.harness.eventsframework.EventsFrameworkConstants.CG_GENERAL_EVENT_TOPIC_SIZE;
import static io.harness.eventsframework.EventsFrameworkConstants.CG_NOTIFY_EVENT;
import static io.harness.eventsframework.EventsFrameworkConstants.CG_NOTIFY_EVENT_BATCH_SIZE;
import static io.harness.eventsframework.EventsFrameworkConstants.CG_NOTIFY_EVENT_MAX_PROCESSING_TIME;
import static io.harness.eventsframework.EventsFrameworkConstants.CG_NOTIFY_EVENT_TOPIC_SIZE;

import io.harness.eventsframework.EventsFrameworkConfiguration;
import io.harness.eventsframework.EventsFrameworkConstants;
import io.harness.eventsframework.api.Consumer;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.impl.noop.NoOpConsumer;
import io.harness.eventsframework.impl.noop.NoOpProducer;
import io.harness.eventsframework.impl.redis.RedisConsumer;
import io.harness.eventsframework.impl.redis.RedisProducer;
import io.harness.ng.core.event.MessageListener;
import io.harness.queue.consumers.listeners.GeneralEventMessageListenerCg;
import io.harness.queue.consumers.listeners.NotifyEventMessageListenerCg;
import io.harness.redis.RedisConfig;
import io.harness.redis.RedissonClientFactory;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import lombok.AllArgsConstructor;
import org.redisson.api.RedissonClient;

@AllArgsConstructor
public class ManagerEventsFrameworkModule extends AbstractModule {
  private final EventsFrameworkConfiguration eventsFrameworkConfiguration;

  @Override
  protected void configure() {
    RedisConfig redisConfig = this.eventsFrameworkConfiguration.getRedisConfig();
    if (redisConfig.getRedisUrl().equals("dummyRedisUrl")) {
      bind(Producer.class).annotatedWith(Names.named(CG_NOTIFY_EVENT)).toInstance(NoOpProducer.of(CG_NOTIFY_EVENT));
      bind(Producer.class).annotatedWith(Names.named(CG_GENERAL_EVENT)).toInstance(NoOpProducer.of(CG_GENERAL_EVENT));
      bind(Consumer.class)
          .annotatedWith(Names.named(CG_NOTIFY_EVENT))
          .toInstance(
              NoOpConsumer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME, EventsFrameworkConstants.DUMMY_GROUP_NAME));
      bind(Consumer.class)
          .annotatedWith(Names.named(CG_GENERAL_EVENT))
          .toInstance(
              NoOpConsumer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME, EventsFrameworkConstants.DUMMY_GROUP_NAME));
    } else {
      RedissonClient redissonClient = RedissonClientFactory.getClient(redisConfig);
      bind(Producer.class)
          .annotatedWith(Names.named(CG_NOTIFY_EVENT))
          .toInstance(RedisProducer.of(CG_NOTIFY_EVENT, redissonClient, CG_NOTIFY_EVENT_TOPIC_SIZE,
              MANAGER.getServiceId(), redisConfig.getEnvNamespace()));
      bind(Producer.class)
          .annotatedWith(Names.named(CG_GENERAL_EVENT))
          .toInstance(RedisProducer.of(CG_GENERAL_EVENT, redissonClient, CG_GENERAL_EVENT_TOPIC_SIZE,
              MANAGER.getServiceId(), redisConfig.getEnvNamespace()));

      bind(Consumer.class)
          .annotatedWith(Names.named(CG_NOTIFY_EVENT))
          .toInstance(RedisConsumer.of(CG_NOTIFY_EVENT, MANAGER.getServiceId(), redissonClient,
              CG_NOTIFY_EVENT_MAX_PROCESSING_TIME, CG_NOTIFY_EVENT_BATCH_SIZE, redisConfig.getEnvNamespace()));
      bind(Consumer.class)
          .annotatedWith(Names.named(CG_GENERAL_EVENT))
          .toInstance(RedisConsumer.of(CG_GENERAL_EVENT, MANAGER.getServiceId(), redissonClient,
              CG_GENERAL_EVENT_MAX_PROCESSING_TIME, CG_GENERAL_EVENT_BATCH_SIZE, redisConfig.getEnvNamespace()));

      bind(MessageListener.class).annotatedWith(Names.named(CG_NOTIFY_EVENT)).to(NotifyEventMessageListenerCg.class);
      bind(MessageListener.class).annotatedWith(Names.named(CG_GENERAL_EVENT)).to(GeneralEventMessageListenerCg.class);
    }
  }
}
