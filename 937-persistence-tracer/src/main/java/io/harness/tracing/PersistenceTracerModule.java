/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.tracing;

import static io.harness.eventsframework.EventsFrameworkConstants.QUERY_ANALYSIS_TOPIC;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.EventsFrameworkConstants;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.impl.noop.NoOpProducer;
import io.harness.eventsframework.impl.redis.RedisProducer;
import io.harness.mongo.tracing.Tracer;
import io.harness.mongo.tracing.TracerConstants;
import io.harness.redis.RedisConfig;
import io.harness.threading.DiscardAndLogQueuePolicy;
import io.harness.threading.ThreadPool;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

@OwnedBy(HarnessTeam.PIPELINE)
class PersistenceTracerModule extends AbstractModule {
  private static PersistenceTracerModule instance;

  static PersistenceTracerModule getInstance() {
    if (instance == null) {
      instance = new PersistenceTracerModule();
    }
    return instance;
  }

  @Override
  protected void configure() {
    bind(Tracer.class).to(MongoRedisTracer.class).in(Singleton.class);
  }

  @Provides
  @Named(PersistenceTracerConstants.TRACING_THREAD_POOL)
  public ExecutorService executorService() {
    return ThreadPool.create(5, 10, 20L, TimeUnit.SECONDS,
        new ThreadFactoryBuilder().setNameFormat("query-analysis-%d").setPriority(Thread.MIN_PRIORITY).build(), 1000,
        new DiscardAndLogQueuePolicy());
  }

  @Provides
  @Named(PersistenceTracerConstants.QUERY_ANALYSIS_PRODUCER)
  public Producer obtainProducer(RedisConfig redisConfig, @Named(TracerConstants.SERVICE_ID) String serviceId) {
    if (redisConfig.getRedisUrl().equals("dummyRedisUrl")) {
      return NoOpProducer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME);
    } else {
      return RedisProducer.of(
          QUERY_ANALYSIS_TOPIC, redisConfig, EventsFrameworkConstants.QUERY_ANALYSIS_TOPIC_SIZE, serviceId);
    }
  }
}
