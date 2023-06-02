/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng;

import static io.harness.authorization.AuthorizationServiceHeader.CV_NEXT_GEN;
import static io.harness.cvng.CVConstants.CUSTOM_CHANGE_PUBLISHER;
import static io.harness.cvng.CVConstants.STATEMACHINE_PUBLISHER;

import io.harness.eventsframework.EventsFrameworkConfiguration;
import io.harness.eventsframework.EventsFrameworkConstants;
import io.harness.eventsframework.api.AbstractProducer;
import io.harness.eventsframework.api.Consumer;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.impl.noop.NoOpConsumer;
import io.harness.eventsframework.impl.noop.NoOpProducer;
import io.harness.eventsframework.impl.redis.RedisConsumer;
import io.harness.eventsframework.impl.redis.RedisProducer;
import io.harness.redis.RedisConfig;
import io.harness.redis.RedissonClientFactory;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import lombok.AllArgsConstructor;
import org.redisson.api.RedissonClient;

@AllArgsConstructor
public class EventsFrameworkModule extends AbstractModule {
  private final EventsFrameworkConfiguration eventsFrameworkConfiguration;

  @Override
  protected void configure() {
    RedisConfig redisConfig = this.eventsFrameworkConfiguration.getRedisConfig();
    if (redisConfig.getRedisUrl().equals("dummyRedisUrl")) {
      bind(AbstractProducer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.ENTITY_CRUD))
          .toInstance(NoOpProducer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME));
      bind(Consumer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.ENTITY_CRUD))
          .toInstance(
              NoOpConsumer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME, EventsFrameworkConstants.DUMMY_GROUP_NAME));
      bind(Consumer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.CD_DEPLOYMENT_EVENT))
          .toInstance(
              NoOpConsumer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME, EventsFrameworkConstants.DUMMY_GROUP_NAME));
      bind(Consumer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.INTERNAL_CHANGE_EVENT_FF))
          .toInstance(
              NoOpConsumer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME, EventsFrameworkConstants.DUMMY_GROUP_NAME));
      bind(Consumer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.INTERNAL_CHANGE_EVENT_CE))
          .toInstance(
              NoOpConsumer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME, EventsFrameworkConstants.DUMMY_GROUP_NAME));
      bind(Producer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.SETUP_USAGE))
          .toInstance(NoOpProducer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME));
      bind(Producer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.SRM_STATEMACHINE_EVENT))
          .toInstance(NoOpProducer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME));
      bind(Consumer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.SRM_STATEMACHINE_EVENT))
          .toInstance(
              NoOpConsumer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME, EventsFrameworkConstants.DUMMY_GROUP_NAME));
      bind(Producer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.CUSTOM_CHANGE_EVENT))
          .toInstance(NoOpProducer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME));
      bind(Producer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.INTERNAL_CHANGE_EVENT_FF))
          .toInstance(NoOpProducer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME));
      bind(Producer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.INTERNAL_CHANGE_EVENT_CE))
          .toInstance(NoOpProducer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME));
      bind(Consumer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.CUSTOM_CHANGE_EVENT))
          .toInstance(
              NoOpConsumer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME, EventsFrameworkConstants.DUMMY_GROUP_NAME));
    } else {
      RedissonClient redissonClient = RedissonClientFactory.getClient(redisConfig);
      bind(AbstractProducer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.ENTITY_CRUD))
          .toInstance(RedisProducer.of(EventsFrameworkConstants.ENTITY_CRUD, redissonClient,
              EventsFrameworkConstants.ENTITY_CRUD_MAX_TOPIC_SIZE, CV_NEXT_GEN.getServiceId(),
              redisConfig.getEnvNamespace()));
      bind(Consumer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.ENTITY_CRUD))
          .toInstance(RedisConsumer.of(EventsFrameworkConstants.ENTITY_CRUD, CV_NEXT_GEN.getServiceId(), redissonClient,
              EventsFrameworkConstants.ENTITY_CRUD_MAX_PROCESSING_TIME,
              EventsFrameworkConstants.ENTITY_CRUD_READ_BATCH_SIZE, redisConfig.getEnvNamespace()));
      bind(Consumer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.CD_DEPLOYMENT_EVENT))
          .toInstance(RedisConsumer.of(EventsFrameworkConstants.CD_DEPLOYMENT_EVENT, CV_NEXT_GEN.getServiceId(),
              redissonClient, EventsFrameworkConstants.CD_DEPLOYMENT_EVENT_MAX_PROCESSING_TIME,
              EventsFrameworkConstants.CD_DEPLOYMENT_EVENT_BATCH_SIZE, redisConfig.getEnvNamespace()));
      bind(Consumer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.INTERNAL_CHANGE_EVENT_FF))
          .toInstance(RedisConsumer.of(EventsFrameworkConstants.INTERNAL_CHANGE_EVENT_FF, CV_NEXT_GEN.getServiceId(),
              redissonClient, EventsFrameworkConstants.INTERNAL_CHANGE_EVENT_MAX_PROCESSING_TIME,
              EventsFrameworkConstants.INTERNAL_CHANGE_EVENT_FF_BATCH_SIZE, redisConfig.getEnvNamespace()));
      bind(Producer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.INTERNAL_CHANGE_EVENT_FF))
          .toInstance(RedisProducer.of(EventsFrameworkConstants.INTERNAL_CHANGE_EVENT_FF, redissonClient, 5000,
              "internal_change_publisher", redisConfig.getEnvNamespace()));

      bind(Consumer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.INTERNAL_CHANGE_EVENT_CE))
          .toInstance(RedisConsumer.of(EventsFrameworkConstants.INTERNAL_CHANGE_EVENT_CE, CV_NEXT_GEN.getServiceId(),
              redissonClient, EventsFrameworkConstants.INTERNAL_CHANGE_EVENT_MAX_PROCESSING_TIME,
              EventsFrameworkConstants.INTERNAL_CHANGE_EVENT_CE_BATCH_SIZE, redisConfig.getEnvNamespace()));

      bind(Producer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.SETUP_USAGE))
          .toInstance(RedisProducer.of(EventsFrameworkConstants.SETUP_USAGE, redissonClient,
              EventsFrameworkConstants.ENTITY_CRUD_MAX_TOPIC_SIZE, CV_NEXT_GEN.getServiceId(),
              redisConfig.getEnvNamespace()));

      bind(Producer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.SRM_STATEMACHINE_EVENT))
          .toInstance(RedisProducer.of(EventsFrameworkConstants.SRM_STATEMACHINE_EVENT, redissonClient,
              EventsFrameworkConstants.SRM_STATEMACHINE_EVENT_MAX_TOPIC_SIZE, STATEMACHINE_PUBLISHER,
              redisConfig.getEnvNamespace()));

      bind(Consumer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.SRM_STATEMACHINE_EVENT))
          .toInstance(RedisConsumer.of(EventsFrameworkConstants.SRM_STATEMACHINE_EVENT, CV_NEXT_GEN.getServiceId(),
              redissonClient, EventsFrameworkConstants.CD_DEPLOYMENT_EVENT_MAX_PROCESSING_TIME,
              EventsFrameworkConstants.SRM_STATEMACHINE_EVENT_BATCH_SIZE, redisConfig.getEnvNamespace()));

      bind(Producer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.CUSTOM_CHANGE_EVENT))
          .toInstance(RedisProducer.of(EventsFrameworkConstants.CUSTOM_CHANGE_EVENT, redissonClient,
              EventsFrameworkConstants.CUSTOM_CHANGE_EVENT_MAX_TOPIC_SIZE, CUSTOM_CHANGE_PUBLISHER,
              redisConfig.getEnvNamespace()));

      bind(Consumer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.CUSTOM_CHANGE_EVENT))
          .toInstance(RedisConsumer.of(EventsFrameworkConstants.CUSTOM_CHANGE_EVENT, CV_NEXT_GEN.getServiceId(),
              redissonClient, EventsFrameworkConstants.CUSTOM_CHANGE_EVENT_MAX_PROCESSING_TIME,
              EventsFrameworkConstants.CUSTOM_CHANGE_EVENT_BATCH_SIZE, redisConfig.getEnvNamespace()));
    }
  }
}
