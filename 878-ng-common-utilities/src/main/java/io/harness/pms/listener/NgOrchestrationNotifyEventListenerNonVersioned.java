/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.listener;

import static java.time.Duration.ofSeconds;
import static java.util.Arrays.asList;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.config.PublisherConfiguration;
import io.harness.mongo.queue.NgQueueFactory;
import io.harness.version.VersionInfoManager;
import io.harness.waiter.NotifyEvent;
import io.harness.waiter.NotifyEventListener;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import org.springframework.data.mongodb.core.MongoTemplate;

@Singleton
@OwnedBy(HarnessTeam.PIPELINE)
public final class NgOrchestrationNotifyEventListenerNonVersioned extends NotifyEventListener {
  public static final String NG_ORCHESTRATION = "ng_orchestration";

  @Inject
  public NgOrchestrationNotifyEventListenerNonVersioned(
      Injector injector, VersionInfoManager versionInfoManager, PublisherConfiguration config) {
    super(NgQueueFactory.createNgQueueConsumer(injector, NotifyEvent.class, ofSeconds(5),
        asList(asList(NG_ORCHESTRATION)), config, injector.getInstance(MongoTemplate.class)));
  }
}
