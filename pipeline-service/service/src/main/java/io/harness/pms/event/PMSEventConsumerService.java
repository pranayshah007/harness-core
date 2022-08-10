/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.event;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.eventsframework.EventsFrameworkConstants.ENTITY_CRUD;
import static io.harness.eventsframework.EventsFrameworkConstants.ENTITY_CRUD_MAX_PROCESSING_TIME;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.EventsFrameworkConstants;
import io.harness.pms.event.entitycrud.PMSEntityCRUDStreamConsumer;
import io.harness.pms.event.pollingevent.PollingEventStreamConsumer;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import io.dropwizard.lifecycle.Managed;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

/**
 * Todo: Migrate these to use event controller and delete this class.
 */
@Slf4j
@OwnedBy(PIPELINE)
public class PMSEventConsumerService implements Managed {
  @Inject private PMSEntityCRUDStreamConsumer entityCRUDStreamConsumer;
  @Inject private PollingEventStreamConsumer pollingEventStreamConsumer;

  private ExecutorService entityCRUDConsumerService;
  private ExecutorService pollingEventConsumerService;

  @Override
  public void start() {
    entityCRUDConsumerService =
        Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat(ENTITY_CRUD).build());
    entityCRUDConsumerService.execute(entityCRUDStreamConsumer);

    pollingEventConsumerService = Executors.newFixedThreadPool(
        5, new ThreadFactoryBuilder().setNameFormat(EventsFrameworkConstants.POLLING_EVENTS_STREAM).build());
    pollingEventConsumerService.execute(pollingEventStreamConsumer);
  }

  @Override
  public void stop() throws InterruptedException {
    entityCRUDConsumerService.shutdownNow();
    entityCRUDConsumerService.awaitTermination(ENTITY_CRUD_MAX_PROCESSING_TIME.getSeconds(), TimeUnit.SECONDS);

    pollingEventConsumerService.shutdownNow();
    pollingEventConsumerService.awaitTermination(
        EventsFrameworkConstants.POLLING_EVENTS_STREAM_MAX_PROCESSING_TIME.getSeconds(), TimeUnit.SECONDS);
  }
}
