package io.harness.eventframework;

import static io.harness.authorization.AuthorizationServiceHeader.DMS;
import static io.harness.eventsframework.EventsFrameworkConstants.DEFAULT_TOPIC_SIZE;
import static io.harness.eventsframework.EventsFrameworkConstants.DUMMY_TOPIC_NAME;
import static io.harness.eventsframework.EventsFrameworkConstants.OBSERVER_EVENT_CHANNEL;

import io.harness.eventsframework.EventsFrameworkConfiguration;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.impl.noop.NoOpProducer;
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
      bind(Producer.class)
          .annotatedWith(Names.named(OBSERVER_EVENT_CHANNEL))
          .toInstance(NoOpProducer.of(DUMMY_TOPIC_NAME));
    } else {
      RedissonClient redissonClient = RedissonClientFactory.getClient(redisConfig);

      bind(Producer.class)
          .annotatedWith(Names.named(OBSERVER_EVENT_CHANNEL))
          .toInstance(RedisProducer.of(OBSERVER_EVENT_CHANNEL, redissonClient, DEFAULT_TOPIC_SIZE, DMS.getServiceId(),
              redisConfig.getEnvNamespace()));
    }
  }
}
