/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.waiter;

import static java.time.Duration.ofSeconds;
import static java.util.Arrays.asList;

import io.harness.OrchestrationPublisherName;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.config.PublisherConfiguration;
import io.harness.mongo.queue.QueueFactory;
import io.harness.queue.QueueConsumer;
import io.harness.version.VersionInfoManager;
import io.harness.waiter.WaiterConfiguration.PersistenceLayer;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.springframework.data.mongodb.core.MongoTemplate;

@Singleton
@OwnedBy(HarnessTeam.PIPELINE)
public final class PmsNotifyEventListener extends NotifyEventListener {
  public static final String PMS_ORCHESTRATION = "pms_orchestration";

  @Inject
  public PmsNotifyEventListener(Injector injector, VersionInfoManager versionInfoManager, PublisherConfiguration config,
      @Named(OrchestrationPublisherName.PERSISTENCE_LAYER) PersistenceLayer persistenceLayer) {
    super(persistenceLayer == PersistenceLayer.SPRING ? getNgQueueConsumer(injector, versionInfoManager, config)
                                                      : getQueueConsumer(injector, versionInfoManager, config));
  }

  private static QueueConsumer<NotifyEvent> getNgQueueConsumer(
      Injector injector, VersionInfoManager versionInfoManager, PublisherConfiguration config) {
    return QueueFactory.createNgQueueConsumer(injector, NotifyEvent.class, ofSeconds(10),
        asList(asList(versionInfoManager.getVersionInfo().getVersion()), asList(PMS_ORCHESTRATION)), config,
        injector.getInstance(MongoTemplate.class));
  }

  private static QueueConsumer<NotifyEvent> getQueueConsumer(
      Injector injector, VersionInfoManager versionInfoManager, PublisherConfiguration config) {
    return QueueFactory.createQueueConsumer(injector, NotifyEvent.class, ofSeconds(10),
        asList(asList(versionInfoManager.getVersionInfo().getVersion()), asList(PMS_ORCHESTRATION)), config);
  }
}
