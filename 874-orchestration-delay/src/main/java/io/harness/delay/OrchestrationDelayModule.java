/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delay;

import static java.time.Duration.ofSeconds;
import static java.util.Collections.singletonList;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.config.PublisherConfiguration;
import io.harness.mongo.queue.NgQueueFactory;
import io.harness.mongo.queue.QueueFactory;
import io.harness.queue.QueueConsumer;
import io.harness.queue.QueueListener;
import io.harness.queue.QueuePublisher;
import io.harness.version.VersionInfoManager;

import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class OrchestrationDelayModule extends AbstractModule {
  private static OrchestrationDelayModule instance;

  static OrchestrationDelayModule getInstance() {
    if (instance == null) {
      instance = new OrchestrationDelayModule();
    }
    return instance;
  }

  @Override
  protected void configure() {
    bind(new TypeLiteral<QueueListener<DelayEvent>>() {}).to(DelayEventListener.class);
  }

  @Provides
  @Singleton
  QueuePublisher<DelayEvent> delayQueuePublisher(Injector injector, VersionInfoManager versionInfoManager,
      PublisherConfiguration config, @Named("forNG") boolean forNG) {
    if (forNG) {
      return NgQueueFactory.createNgQueuePublisher(injector, DelayEvent.class,
          singletonList(versionInfoManager.getVersionInfo().getVersion()), config,
          injector.getInstance(MongoTemplate.class));
    }
    return QueueFactory.createQueuePublisher(injector, io.harness.delay.DelayEvent.class,
        singletonList(versionInfoManager.getVersionInfo().getVersion()), config);
  }

  @Provides
  @Singleton
  QueueConsumer<DelayEvent> delayQueueConsumer(Injector injector, VersionInfoManager versionInfoManager,
      PublisherConfiguration config, @Named("forNG") boolean forNG) {
    if (forNG) {
      return NgQueueFactory.createNgQueueConsumer(injector, io.harness.delay.DelayEvent.class, ofSeconds(5),
          singletonList(singletonList(versionInfoManager.getVersionInfo().getVersion())), config,
          injector.getInstance(MongoTemplate.class));
    }
    return QueueFactory.createQueueConsumer(injector, io.harness.delay.DelayEvent.class, ofSeconds(5),
        singletonList(singletonList(versionInfoManager.getVersionInfo().getVersion())), config);
  }
}
