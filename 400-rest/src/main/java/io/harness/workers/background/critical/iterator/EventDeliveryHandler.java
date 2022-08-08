/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.workers.background.critical.iterator;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.ExecutionContext.MANAGER;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;
import static io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType.REGULAR;

import static java.time.Duration.ofSeconds;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Event;
import io.harness.beans.Event.EventsKeys;
import io.harness.beans.EventStatus;
import io.harness.exception.WingsException;
import io.harness.iterator.PersistenceIterator;
import io.harness.iterator.PersistenceIterator.ProcessMode;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.logging.AutoLogContext;
import io.harness.logging.ExceptionLogger;
import io.harness.metrics.HarnessMetricRegistry;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.MongoPersistenceIterator.Handler;
import io.harness.mongo.iterator.filter.MorphiaFilterExpander;
import io.harness.mongo.iterator.provider.MorphiaPersistenceRequiredProvider;
import io.harness.workers.background.AccountStatusBasedEntityProcessController;

import software.wings.beans.Account;
import software.wings.beans.Permit;
import software.wings.service.impl.EventDeliveryService;
import software.wings.service.impl.PermitServiceImpl;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.PermitService;

import com.codahale.metrics.InstrumentedExecutorService;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Date;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@Singleton
@Slf4j
public class EventDeliveryHandler implements Handler<Event> {
  public static final String GROUP = "EVENT_TELEMETRY_CRON_GROUP";

  @Inject private AccountService accountService;
  @Inject private PersistenceIteratorFactory persistenceIteratorFactory;
  @Inject private PermitService permitService;
  @Inject private HarnessMetricRegistry harnessMetricRegistry;
  @Inject private MorphiaPersistenceRequiredProvider<Event> persistenceProvider;
  @Inject private EventDeliveryService eventDeliveryService;

  // TODO: check the order
  public void registerIterators(ScheduledThreadPoolExecutor eventDeliveryExecutor) {
    InstrumentedExecutorService instrumentedExecutorService = new InstrumentedExecutorService(
        eventDeliveryExecutor, harnessMetricRegistry.getThreadPoolMetricRegistry(), "Iterator-Event-Delivery");
    PersistenceIterator iterator = persistenceIteratorFactory.createIterator(EventDeliveryHandler.class,
        MongoPersistenceIterator.<Event, MorphiaFilterExpander<Event>>builder()
            .mode(ProcessMode.PUMP)
            .iteratorName("EventDelivery")
            .clazz(Event.class)
            .fieldName(EventsKeys.nextIteration)
            .targetInterval(ofSeconds(5))
            .acceptableNoAlertDelay(ofSeconds(30))
            .executorService(instrumentedExecutorService)
            .semaphore(new Semaphore(25))
            .handler(this)
            .entityProcessController(new AccountStatusBasedEntityProcessController<>(accountService))
            .schedulingType(REGULAR)
            .filterExpander(query -> query.field(EventsKeys.status).equal(EventStatus.QUEUED))
            .persistenceProvider(persistenceProvider)
            .redistribute(true));

    if (iterator != null) {
      eventDeliveryExecutor.scheduleAtFixedRate(() -> iterator.process(), 0, 10, TimeUnit.SECONDS);
    }
  }

  @Override
  public void handle(Event event) {
    try (AutoLogContext ignore2 = new AutoLogContext(ImmutableMap.<String, String>builder()
                                                         .put(EventsKeys.uuid, event.getUuid())
                                                         .put(EventsKeys.eventConfigId, event.getEventConfigId())
                                                         .build(),
             OVERRIDE_ERROR)) {
      executeInternal(event);
    }
  }

  private void executeInternal(Event event) {
    String eventUuid = event.getUuid();
    try {
      // TODO: If retry count is high or the event is disabled then we will not deliver and mark it as failed

      int leaseDuration =
          (int) (TimeUnit.MINUTES.toMillis(2) * PermitServiceImpl.getBackoffMultiplier(event.getFailedRetryCount()));
      String permitId = permitService.acquirePermit(Permit.builder()
                                                        .appId(event.getAppId())
                                                        .group(GROUP)
                                                        .key(eventUuid)
                                                        .expireAt(new Date(System.currentTimeMillis() + leaseDuration))
                                                        .leaseDuration(leaseDuration)
                                                        .accountId(event.getAccountId())
                                                        .build());
      if (isNotEmpty(permitId)) {
        log.info("Permit [{}] acquired for event [failedCount: {}] for [{}] minutes", permitId,
            event.getFailedRetryCount(), TimeUnit.MILLISECONDS.toMinutes(leaseDuration));
        eventDeliveryService.deliveryEvent(event, permitId);
      } else {
        log.info("Permit already exists for event");
      }
    } catch (WingsException exception) {
      log.warn("Failed to deliver the event. Reason {}", exception.getMessage());
      if (event.getAccountId() != null) {
        exception.addContext(Account.class, event.getAccountId());
      }
      exception.addContext(Event.class, eventUuid);
      ExceptionLogger.logProcessedMessages(exception, MANAGER, log);
    } catch (Exception e) {
      log.warn("Failed to deliver the event. Reason {}", e.getMessage());
    }
  }
}
