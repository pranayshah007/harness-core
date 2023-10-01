/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.commons.events;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.dropwizard.lifecycle.Managed;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@OwnedBy(HarnessTeam.PL)
public abstract class EventListenerService implements Managed {
  private final EventListener eventListener;
  private final ExecutorService executorService;
  private Future<?> eventListenerFuture;

  public abstract String getServiceName();

  public EventListenerService(EventListener eventListener, EventsConfig eventsConfig) {
    this.eventListener = eventListener;
    executorService = Executors.newFixedThreadPool(eventsConfig.getThreads(),
        new ThreadFactoryBuilder().setNameFormat(getServiceName() + "-listener-main-thread-%d").build());
  }

  @Override
  public void start() throws Exception {
    eventListenerFuture = executorService.submit(eventListener);
  }

  @Override
  public void stop() throws Exception {
    eventListenerFuture.cancel(true);
    executorService.shutdownNow();
  }
}
