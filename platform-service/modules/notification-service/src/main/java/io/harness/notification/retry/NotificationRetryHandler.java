/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notification.retry;

import static io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType.REGULAR;

import static java.time.Duration.ofSeconds;

import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.filter.MorphiaFilterExpander;
import io.harness.mongo.iterator.provider.MorphiaPersistenceRequiredProvider;
import io.harness.notification.entities.Notification;
import io.harness.notification.entities.Notification.NotificationKeys;
import io.harness.notification.service.api.NotificationService;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class NotificationRetryHandler implements MongoPersistenceIterator.Handler<Notification> {
  public static final String GROUP = "NOTIFICATION_REQUEST_RETRY_CRON_GROUP";

  @Inject private final PersistenceIteratorFactory persistenceIteratorFactory;
  @Inject private final MorphiaPersistenceRequiredProvider<Notification> persistenceProvider;
  @Inject private final NotificationService notificationService;

  @Override
  public void handle(Notification notification) {
    notificationService.processRetries(notification);
  }

  public void registerIterators() {
    MorphiaFilterExpander<Notification> filterExpander = query
        -> query.field(NotificationKeys.shouldRetry)
               .equal(Boolean.TRUE)
               .field(NotificationKeys.retries)
               .in(ImmutableList.of(1, 2));

    persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(
        PersistenceIteratorFactory.PumpExecutorOptions.builder()
            .name("NotificationRetryHandler")
            .poolSize(5)
            .interval(ofSeconds(5))
            .build(),
        Notification.class,
        MongoPersistenceIterator.<Notification, MorphiaFilterExpander<Notification>>builder()
            .clazz(Notification.class)
            .fieldName(NotificationKeys.nextIteration)
            .targetInterval(ofSeconds(10))
            .acceptableNoAlertDelay(ofSeconds(20))
            .handler(this)
            .filterExpander(filterExpander)
            .schedulingType(REGULAR)
            .persistenceProvider(persistenceProvider)
            .redistribute(true));
  }
}
