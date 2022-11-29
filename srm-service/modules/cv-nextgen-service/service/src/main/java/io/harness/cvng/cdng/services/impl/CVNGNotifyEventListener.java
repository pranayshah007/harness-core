/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.cdng.services.impl;

import static java.time.Duration.ofSeconds;
import static java.util.Arrays.asList;

import io.harness.config.PublisherConfiguration;
import io.harness.mongo.queue.QueueFactory;
import io.harness.version.VersionInfoManager;
import io.harness.waiter.NotifyEvent;
import io.harness.waiter.NotifyEventListener;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;

@Singleton
public final class CVNGNotifyEventListener extends NotifyEventListener {
  public static final String CVNG_ORCHESTRATION = "cvng_orchestration";

  @Inject
  public CVNGNotifyEventListener(
      Injector injector, VersionInfoManager versionInfoManager, PublisherConfiguration config) {
    super(QueueFactory.createQueueConsumer(injector, NotifyEvent.class, ofSeconds(5),
        asList(asList(versionInfoManager.getVersionInfo().getVersion()), asList(CVNG_ORCHESTRATION)), config));
  }
}
