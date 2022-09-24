/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng;

import static io.harness.AuthorizationServiceHeader.CV_NEXT_GEN;
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

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import lombok.AllArgsConstructor;

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
    } else {
      bind(AbstractProducer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.ENTITY_CRUD))
          .toInstance(RedisProducer.of(EventsFrameworkConstants.ENTITY_CRUD, redisConfig,
              EventsFrameworkConstants.ENTITY_CRUD_MAX_TOPIC_SIZE, CV_NEXT_GEN.getServiceId()));
      bind(Consumer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.ENTITY_CRUD))
          .toInstance(RedisConsumer.of(EventsFrameworkConstants.ENTITY_CRUD, CV_NEXT_GEN.getServiceId(), redisConfig,
              EventsFrameworkConstants.ENTITY_CRUD_MAX_PROCESSING_TIME,
              EventsFrameworkConstants.ENTITY_CRUD_READ_BATCH_SIZE));
      bind(Consumer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.CD_DEPLOYMENT_EVENT))
          .toInstance(RedisConsumer.of(EventsFrameworkConstants.CD_DEPLOYMENT_EVENT, CV_NEXT_GEN.getServiceId(),
              redisConfig, EventsFrameworkConstants.CD_DEPLOYMENT_EVENT_MAX_PROCESSING_TIME,
              EventsFrameworkConstants.CD_DEPLOYMENT_EVENT_BATCH_SIZE));
      bind(Producer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.SETUP_USAGE))
          .toInstance(RedisProducer.of(EventsFrameworkConstants.SETUP_USAGE, redisConfig,
              EventsFrameworkConstants.ENTITY_CRUD_MAX_TOPIC_SIZE, CV_NEXT_GEN.getServiceId()));

      bind(Producer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.SRM_STATEMACHINE_EVENT))
          .toInstance(RedisProducer.of(EventsFrameworkConstants.SRM_STATEMACHINE_EVENT, redisConfig,
              EventsFrameworkConstants.SRM_STATEMACHINE_EVENT_MAX_TOPIC_SIZE, STATEMACHINE_PUBLISHER));

      bind(Consumer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.SRM_STATEMACHINE_EVENT))
          .toInstance(RedisConsumer.of(EventsFrameworkConstants.SRM_STATEMACHINE_EVENT, CV_NEXT_GEN.getServiceId(),
              redisConfig, EventsFrameworkConstants.CD_DEPLOYMENT_EVENT_MAX_PROCESSING_TIME,
              EventsFrameworkConstants.SRM_STATEMACHINE_EVENT_BATCH_SIZE));
    }
  }
}
