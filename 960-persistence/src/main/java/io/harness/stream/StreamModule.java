/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.stream;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.stream.AtmosphereBroadcaster.REDIS;

import io.harness.redis.RedisConfig;
import io.harness.stream.redisson.RedissonBroadcaster;

import com.google.inject.AbstractModule;
import com.google.inject.Provider;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.atmosphere.cpr.ApplicationConfig;
import org.atmosphere.cpr.AtmosphereServlet;
import org.atmosphere.cpr.BroadcasterFactory;
import org.atmosphere.cpr.DefaultBroadcaster;
import org.atmosphere.cpr.DefaultMetaBroadcaster;
import org.atmosphere.cpr.MetaBroadcaster;

public class StreamModule extends AbstractModule {
  private static final String ATMOSPHERE_MESSAGE_PROCESSING_THREADPOOL_MAXSIZE =
      "ATMOSPHERE_MESSAGE_PROCESSING_THREADPOOL_MAXSIZE";
  private static final String ATMOSPHERE_ASYNC_WRITE_THREADPOOL_MAXSIZE = "ATMOSPHERE_ASYNC_WRITE_THREADPOOL_MAXSIZE";
  private static volatile StreamModule instance;

  public static StreamModule getInstance() {
    if (instance == null) {
      instance = new StreamModule();
    }
    return instance;
  }

  private StreamModule() {}

  @Override
  protected void configure() {}

  @Provides
  @Singleton
  AtmosphereServlet getAtmosphereServelet(
      AtmosphereBroadcaster atmosphereBroadcaster, @Named("atmosphere") Provider<RedisConfig> redisConfigProvider) {
    AtmosphereServlet atmosphereServlet = new AtmosphereServlet(false, false);
    atmosphereServlet.framework()
        .addInitParameter(ApplicationConfig.WEBSOCKET_CONTENT_TYPE, "application/json")
        .addInitParameter(ApplicationConfig.BROADCASTER_MESSAGE_PROCESSING_THREADPOOL_MAXSIZE,
            isEmpty(System.getenv(ATMOSPHERE_MESSAGE_PROCESSING_THREADPOOL_MAXSIZE))
                ? "40"
                : System.getenv(ATMOSPHERE_MESSAGE_PROCESSING_THREADPOOL_MAXSIZE))
        .addInitParameter(ApplicationConfig.BROADCASTER_ASYNC_WRITE_THREADPOOL_MAXSIZE,
            isEmpty(System.getenv(ATMOSPHERE_ASYNC_WRITE_THREADPOOL_MAXSIZE))
                ? "40"
                : System.getenv(ATMOSPHERE_ASYNC_WRITE_THREADPOOL_MAXSIZE))
        .addInitParameter(ApplicationConfig.WEBSOCKET_SUPPRESS_JSR356, "true")
        .addInitParameter(ApplicationConfig.WEBSOCKET_SUPPORT, "true")
        .addInitParameter(ApplicationConfig.ANNOTATION_PACKAGE, getClass().getPackage().getName());

    String broadcasterName;
    if (atmosphereBroadcaster == REDIS) {
      broadcasterName = RedissonBroadcaster.class.getName();
    } else {
      broadcasterName = DefaultBroadcaster.class.getName();
    }
    atmosphereServlet.framework().setDefaultBroadcasterClassName(broadcasterName);
    return atmosphereServlet;
  }

  @Provides
  @Singleton
  BroadcasterFactory getBroadcasterFactory(AtmosphereServlet atmosphereServlet) {
    return atmosphereServlet.framework().getBroadcasterFactory();
  }

  @Provides
  @Singleton
  MetaBroadcaster metaBroadcaster(AtmosphereServlet atmosphereServlet) {
    MetaBroadcaster metaBroadcaster = new DefaultMetaBroadcaster();
    metaBroadcaster.configure(atmosphereServlet.framework().getAtmosphereConfig());
    return metaBroadcaster;
  }
}
