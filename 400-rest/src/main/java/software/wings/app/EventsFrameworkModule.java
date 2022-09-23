/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.app;

import static io.harness.AuthorizationServiceHeader.DMS;
import static io.harness.AuthorizationServiceHeader.MANAGER;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.eventsframework.EventsFrameworkConstants.DEFAULT_MAX_PROCESSING_TIME;
import static io.harness.eventsframework.EventsFrameworkConstants.DEFAULT_READ_BATCH_SIZE;
import static io.harness.eventsframework.EventsFrameworkConstants.DEFAULT_TOPIC_SIZE;
import static io.harness.eventsframework.EventsFrameworkConstants.DUMMY_GROUP_NAME;
import static io.harness.eventsframework.EventsFrameworkConstants.DUMMY_TOPIC_NAME;
import static io.harness.eventsframework.EventsFrameworkConstants.ENTITY_ACTIVITY;
import static io.harness.eventsframework.EventsFrameworkConstants.ENTITY_ACTIVITY_MAX_TOPIC_SIZE;
import static io.harness.eventsframework.EventsFrameworkConstants.ENTITY_CRUD;
import static io.harness.eventsframework.EventsFrameworkConstants.ENTITY_CRUD_MAX_PROCESSING_TIME;
import static io.harness.eventsframework.EventsFrameworkConstants.ENTITY_CRUD_MAX_TOPIC_SIZE;
import static io.harness.eventsframework.EventsFrameworkConstants.ENTITY_CRUD_READ_BATCH_SIZE;
import static io.harness.eventsframework.EventsFrameworkConstants.LDAP_GROUP_SYNC;
import static io.harness.eventsframework.EventsFrameworkConstants.OBSERVER_EVENT_CHANNEL;
import static io.harness.eventsframework.EventsFrameworkConstants.SAML_AUTHORIZATION_ASSERTION;

import io.harness.annotations.dev.OwnedBy;
import io.harness.configuration.DeployMode;
import io.harness.eventsframework.EventsFrameworkConfiguration;
import io.harness.eventsframework.api.Consumer;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.impl.noop.NoOpConsumer;
import io.harness.eventsframework.impl.noop.NoOpProducer;
import io.harness.eventsframework.impl.redis.RedisConsumer;
import io.harness.eventsframework.impl.redis.RedisProducer;
import io.harness.eventsframework.impl.redis.RedisUtils;
import io.harness.redis.RedisConfig;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import lombok.AllArgsConstructor;
import org.redisson.api.RedissonClient;

@OwnedBy(PL)
@AllArgsConstructor
public class EventsFrameworkModule extends AbstractModule {
  private final EventsFrameworkConfiguration eventsFrameworkConfiguration;
  private final boolean isEventsFrameworkAvailableInOnPrem;
  private final boolean isDmsMode;

  @Override
  protected void configure() {
    final RedisConfig redisConfig = this.eventsFrameworkConfiguration.getRedisConfig();

    final String deployMode = System.getenv(DeployMode.DEPLOY_MODE);
    if ((DeployMode.isOnPrem(deployMode) && !isEventsFrameworkAvailableInOnPrem)
        || redisConfig.getRedisUrl().equals("dummyRedisUrl")) {
      bind(Producer.class).annotatedWith(Names.named(ENTITY_CRUD)).toInstance(NoOpProducer.of(DUMMY_TOPIC_NAME));
      bind(Consumer.class)
          .annotatedWith(Names.named(ENTITY_CRUD))
          .toInstance(NoOpConsumer.of(DUMMY_TOPIC_NAME, DUMMY_GROUP_NAME));
      bind(Producer.class).annotatedWith(Names.named(ENTITY_ACTIVITY)).toInstance(NoOpProducer.of(DUMMY_TOPIC_NAME));
      bind(Producer.class)
          .annotatedWith(Names.named(SAML_AUTHORIZATION_ASSERTION))
          .toInstance(NoOpProducer.of(DUMMY_TOPIC_NAME));
      bind(Producer.class).annotatedWith(Names.named(LDAP_GROUP_SYNC)).toInstance(NoOpProducer.of(DUMMY_TOPIC_NAME));
      bind(Producer.class)
          .annotatedWith(Names.named(OBSERVER_EVENT_CHANNEL))
          .toInstance(NoOpProducer.of(DUMMY_TOPIC_NAME));
      bind(Consumer.class)
          .annotatedWith(Names.named(OBSERVER_EVENT_CHANNEL))
          .toInstance(NoOpConsumer.of(DUMMY_TOPIC_NAME, DUMMY_GROUP_NAME));
    } else {
      RedissonClient redissonClient = RedisUtils.getClient(redisConfig);
      bind(Producer.class)
          .annotatedWith(Names.named(ENTITY_CRUD))
          .toInstance(RedisProducer.of(ENTITY_CRUD, redissonClient, ENTITY_CRUD_MAX_TOPIC_SIZE, MANAGER.getServiceId(),
              redisConfig.getEnvNamespace()));
      bind(Consumer.class)
          .annotatedWith(Names.named(ENTITY_CRUD))
          .toInstance(RedisConsumer.of(ENTITY_CRUD, MANAGER.getServiceId(), redissonClient,
              ENTITY_CRUD_MAX_PROCESSING_TIME, ENTITY_CRUD_READ_BATCH_SIZE, redisConfig.getEnvNamespace()));
      bind(Producer.class)
          .annotatedWith(Names.named(ENTITY_ACTIVITY))
          .toInstance(RedisProducer.of(ENTITY_ACTIVITY, redissonClient, ENTITY_ACTIVITY_MAX_TOPIC_SIZE,
              MANAGER.getServiceId(), redisConfig.getEnvNamespace()));
      bind(Producer.class)
          .annotatedWith(Names.named(SAML_AUTHORIZATION_ASSERTION))
          .toInstance(RedisProducer.of(SAML_AUTHORIZATION_ASSERTION, redissonClient, DEFAULT_TOPIC_SIZE,
              MANAGER.getServiceId(), redisConfig.getEnvNamespace()));
      bind(Producer.class)
          .annotatedWith(Names.named(LDAP_GROUP_SYNC))
          .toInstance(RedisProducer.of(LDAP_GROUP_SYNC, redissonClient, DEFAULT_TOPIC_SIZE, MANAGER.getServiceId(),
              redisConfig.getEnvNamespace()));

      String authorizationServiceHeader = MANAGER.getServiceId();
      if (isDmsMode) {
        authorizationServiceHeader = DMS.getServiceId();
      }
      bind(Producer.class)
          .annotatedWith(Names.named(OBSERVER_EVENT_CHANNEL))
          .toInstance(RedisProducer.of(OBSERVER_EVENT_CHANNEL, redissonClient, DEFAULT_TOPIC_SIZE,
              authorizationServiceHeader, redisConfig.getEnvNamespace()));
      bind(Consumer.class)
          .annotatedWith(Names.named(OBSERVER_EVENT_CHANNEL))
          .toInstance(RedisConsumer.of(OBSERVER_EVENT_CHANNEL, authorizationServiceHeader, redissonClient,
              DEFAULT_MAX_PROCESSING_TIME, DEFAULT_READ_BATCH_SIZE, redisConfig.getEnvNamespace()));
    }
  }
}
