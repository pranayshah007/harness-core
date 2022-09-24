/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core;

import static io.harness.eventsframework.EventsFrameworkConstants.PIPELINE_PARTIAL_PLAN_RESPONSE;
import static io.harness.eventsframework.EventsFrameworkConstants.PIPELINE_SDK_RESPONSE_EVENT_TOPIC;
import static io.harness.pms.sdk.core.PmsSdkCoreEventsFrameworkConstants.PARTIAL_PLAN_RESPONSE_EVENT_PRODUCER;
import static io.harness.pms.sdk.core.PmsSdkCoreEventsFrameworkConstants.SDK_RESPONSE_EVENT_PRODUCER;

import io.harness.eventsframework.EventsFrameworkConfiguration;
import io.harness.eventsframework.EventsFrameworkConstants;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.impl.noop.NoOpProducer;
import io.harness.eventsframework.impl.redis.RedisProducer;
import io.harness.eventsframework.impl.redis.RedisUtils;
import io.harness.redis.RedisConfig;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import org.redisson.api.RedissonClient;

public class PmsSdkCoreEventsFrameworkModule extends AbstractModule {
  private static PmsSdkCoreEventsFrameworkModule instance;

  private final EventsFrameworkConfiguration eventsFrameworkConfiguration;
  private final PipelineSdkRedisEventsConfig pipelineSdkRedisEventsConfig;
  private final String serviceName;

  public static PmsSdkCoreEventsFrameworkModule getInstance(EventsFrameworkConfiguration config,
      PipelineSdkRedisEventsConfig pipelineSdkRedisEventsConfig, String serviceName) {
    if (instance == null) {
      instance = new PmsSdkCoreEventsFrameworkModule(config, pipelineSdkRedisEventsConfig, serviceName);
    }
    return instance;
  }

  private PmsSdkCoreEventsFrameworkModule(EventsFrameworkConfiguration config,
      PipelineSdkRedisEventsConfig pipelineSdkRedisEventsConfig, String serviceName) {
    this.eventsFrameworkConfiguration = config;
    this.pipelineSdkRedisEventsConfig = pipelineSdkRedisEventsConfig;
    this.serviceName = serviceName;
  }

  @Override
  protected void configure() {
    RedisConfig redisConfig = this.eventsFrameworkConfiguration.getRedisConfig();
    if (redisConfig.getRedisUrl().equals("dummyRedisUrl")) {
      bind(Producer.class)
          .annotatedWith(Names.named(SDK_RESPONSE_EVENT_PRODUCER))
          .toInstance(NoOpProducer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME));
      bind(Producer.class)
          .annotatedWith(Names.named(PARTIAL_PLAN_RESPONSE_EVENT_PRODUCER))
          .toInstance(NoOpProducer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME));
    } else {
      RedissonClient redissonClient = RedisUtils.getClient(redisConfig);
      bind(Producer.class)
          .annotatedWith(Names.named(SDK_RESPONSE_EVENT_PRODUCER))
          .toInstance(RedisProducer.of(PIPELINE_SDK_RESPONSE_EVENT_TOPIC, redissonClient,
              pipelineSdkRedisEventsConfig.getPipelineSdkResponseEvent().getMaxTopicSize(), serviceName,
              redisConfig.getEnvNamespace()));
      bind(Producer.class)
          .annotatedWith(Names.named(PARTIAL_PLAN_RESPONSE_EVENT_PRODUCER))
          .toInstance(RedisProducer.of(PIPELINE_PARTIAL_PLAN_RESPONSE, redissonClient,
              pipelineSdkRedisEventsConfig.getPipelineSdkResponseEvent().getMaxTopicSize(), serviceName,
              redisConfig.getEnvNamespace()));
    }
  }
}
