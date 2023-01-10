/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.template.eventsframework;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.authorization.AuthorizationServiceHeader.TEMPLATE_SERVICE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.EventsFrameworkConfiguration;
import io.harness.eventsframework.EventsFrameworkConstants;
import io.harness.eventsframework.api.Consumer;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.impl.noop.NoOpProducer;
import io.harness.eventsframework.impl.redis.GitAwareRedisProducer;
import io.harness.eventsframework.impl.redis.RedisConsumer;
import io.harness.eventsframework.impl.redis.RedisProducer;
import io.harness.redis.RedisConfig;
import io.harness.redis.RedissonClientFactory;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import lombok.AllArgsConstructor;
import org.redisson.api.RedissonClient;

@OwnedBy(CDC)
@AllArgsConstructor
public class TemplateEventsFrameworkModule extends AbstractModule {
  private final EventsFrameworkConfiguration eventsFrameworkConfiguration;

  @Override
  protected void configure() {
    RedisConfig redisConfig = this.eventsFrameworkConfiguration.getRedisConfig();
    if (redisConfig.getRedisUrl().equals("dummyRedisUrl")) {
      bind(Producer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.ENTITY_CRUD))
          .toInstance(NoOpProducer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME));
      bind(Producer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.SETUP_USAGE))
          .toInstance(NoOpProducer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME));
    } else {
      RedissonClient redissonClient = RedissonClientFactory.getClient(redisConfig);
      bind(Producer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.ENTITY_CRUD))
          .toInstance(RedisProducer.of(EventsFrameworkConstants.ENTITY_CRUD, redissonClient,
              EventsFrameworkConstants.ENTITY_CRUD_MAX_TOPIC_SIZE, TEMPLATE_SERVICE.getServiceId(),
              redisConfig.getEnvNamespace()));
      bind(Producer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.SETUP_USAGE))
          .toInstance(GitAwareRedisProducer.of(EventsFrameworkConstants.SETUP_USAGE, redissonClient,
              EventsFrameworkConstants.ENTITY_CRUD_MAX_TOPIC_SIZE, TEMPLATE_SERVICE.getServiceId(),
              redisConfig.getEnvNamespace()));
      bind(Consumer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.ENTITY_CRUD))
          .toInstance(RedisConsumer.of(EventsFrameworkConstants.ENTITY_CRUD, TEMPLATE_SERVICE.getServiceId(),
              redissonClient, EventsFrameworkConstants.ENTITY_CRUD_MAX_PROCESSING_TIME,
              EventsFrameworkConstants.ENTITY_CRUD_READ_BATCH_SIZE, redisConfig.getEnvNamespace()));
    }
  }
}
