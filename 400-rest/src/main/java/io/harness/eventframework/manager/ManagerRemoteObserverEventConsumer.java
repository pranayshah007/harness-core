/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.eventframework.manager;

import static io.harness.eventsframework.EventsFrameworkConstants.OBSERVER_EVENT_CHANNEL;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.authorization.AuthorizationServiceHeader;
import io.harness.eventsframework.api.Consumer;
import io.harness.observer.RemoteObserver;
import io.harness.observer.consumer.AbstractRemoteObserverEventConsumer;
import io.harness.observer.consumer.RemoteObserverProcessor;
import io.harness.queue.QueueController;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.Set;

@OwnedBy(HarnessTeam.DEL)
public class ManagerRemoteObserverEventConsumer extends AbstractRemoteObserverEventConsumer {
  @Inject
  public ManagerRemoteObserverEventConsumer(@Named(OBSERVER_EVENT_CHANNEL) Consumer redisConsumer,
      Set<RemoteObserver> remoteObservers, QueueController queueController,
      RemoteObserverProcessor remoteObserverProcessor) {
    super(redisConsumer, remoteObservers, queueController, remoteObserverProcessor);
  }

  @Override
  public String getServicePrincipal() {
    return AuthorizationServiceHeader.DMS.getServiceId();
  }
}
