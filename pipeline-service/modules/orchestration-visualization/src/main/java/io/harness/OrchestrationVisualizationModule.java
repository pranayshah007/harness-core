/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.authorization.AuthorizationServiceHeader.PIPELINE_SERVICE;
import static io.harness.eventsframework.EventsFrameworkConstants.ORCHESTRATION_LOG;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.EventsFrameworkConfiguration;
import io.harness.eventsframework.EventsFrameworkConstants;
import io.harness.eventsframework.api.Consumer;
import io.harness.eventsframework.impl.noop.NoOpConsumer;
import io.harness.eventsframework.impl.redis.RedisConsumer;
import io.harness.redis.RedisConfig;
import io.harness.redis.RedissonClientFactory;
import io.harness.service.GraphGenerationService;
import io.harness.service.impl.GraphGenerationServiceImpl;
import io.harness.skip.service.VertexSkipperService;
import io.harness.skip.service.impl.VertexSkipperServiceImpl;
import io.harness.threading.ThreadPool;
import io.harness.threading.ThreadPoolConfig;

import com.codahale.metrics.MetricRegistry;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import java.util.concurrent.ExecutorService;
import org.redisson.api.RedissonClient;

@OwnedBy(CDC)
public class OrchestrationVisualizationModule extends AbstractModule {
  private static OrchestrationVisualizationModule instance;
  private final EventsFrameworkConfiguration eventsFrameworkConfiguration;
  private final ThreadPoolConfig visualizationThreadPoolConfig;
  private final MetricRegistry threadPoolMetricRegistry;
  private final Integer graphConsumerSleepMs;

  public static OrchestrationVisualizationModule getInstance(EventsFrameworkConfiguration eventsFrameworkConfiguration,
      ThreadPoolConfig visualizationThreadPoolConfig, Integer graphConsumerSleepMs) {
    if (instance == null) {
      instance = new OrchestrationVisualizationModule(
          eventsFrameworkConfiguration, visualizationThreadPoolConfig, graphConsumerSleepMs);
    }
    return instance;
  }

  public static OrchestrationVisualizationModule getInstance(EventsFrameworkConfiguration eventsFrameworkConfiguration,
      ThreadPoolConfig visualizationThreadPoolConfig, Integer graphConsumerSleepMs,
      MetricRegistry threadPoolMetricRegistry) {
    if (instance == null) {
      instance = new OrchestrationVisualizationModule(
          eventsFrameworkConfiguration, visualizationThreadPoolConfig, graphConsumerSleepMs, threadPoolMetricRegistry);
    }
    return instance;
  }

  OrchestrationVisualizationModule(EventsFrameworkConfiguration eventsFrameworkConfiguration,
      ThreadPoolConfig visualizationThreadPoolConfig, Integer graphConsumerSleepMs) {
    this.eventsFrameworkConfiguration = eventsFrameworkConfiguration;
    this.visualizationThreadPoolConfig = visualizationThreadPoolConfig;
    this.graphConsumerSleepMs = graphConsumerSleepMs == null ? 0 : graphConsumerSleepMs;
    this.threadPoolMetricRegistry = new MetricRegistry();
  }

  OrchestrationVisualizationModule(EventsFrameworkConfiguration eventsFrameworkConfiguration,
      ThreadPoolConfig visualizationThreadPoolConfig, Integer graphConsumerSleepMs,
      MetricRegistry threadPoolMetricRegistry) {
    this.eventsFrameworkConfiguration = eventsFrameworkConfiguration;
    this.visualizationThreadPoolConfig = visualizationThreadPoolConfig;
    this.graphConsumerSleepMs = graphConsumerSleepMs == null ? 0 : graphConsumerSleepMs;
    this.threadPoolMetricRegistry = threadPoolMetricRegistry;
  }

  @Override
  protected void configure() {
    bind(GraphGenerationService.class).to(GraphGenerationServiceImpl.class);
    bind(VertexSkipperService.class).to(VertexSkipperServiceImpl.class);
    RedisConfig redisConfig = this.eventsFrameworkConfiguration.getRedisConfig();
    if (redisConfig.getRedisUrl().equals("dummyRedisUrl")) {
      bind(Consumer.class)
          .annotatedWith(Names.named(ORCHESTRATION_LOG))
          .toInstance(
              NoOpConsumer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME, EventsFrameworkConstants.DUMMY_GROUP_NAME));
    } else {
      RedissonClient redissonClient = RedissonClientFactory.getClient(redisConfig);
      bind(Consumer.class)
          .annotatedWith(Names.named(ORCHESTRATION_LOG))
          .toInstance(RedisConsumer.of(ORCHESTRATION_LOG, PIPELINE_SERVICE.getServiceId(), redissonClient,
              EventsFrameworkConstants.ORCHESTRATION_LOG_MAX_PROCESSING_TIME,
              EventsFrameworkConstants.ORCHESTRATION_LOG_READ_BATCH_SIZE, redisConfig.getEnvNamespace()));
    }
  }

  @Provides
  @Singleton
  @Named("OrchestrationVisualizationExecutorService")
  public ExecutorService orchestrationVisualizationExecutorService() {
    return ThreadPool.getInstrumentedExecutorService(
        visualizationThreadPoolConfig, "OrchestrationVisualizationExecutorService", threadPoolMetricRegistry);
  }

  @Provides
  @Singleton
  @Named("GraphConsumerSleepMs")
  public int graphConsumerSleepMs() {
    return graphConsumerSleepMs;
  }
}
