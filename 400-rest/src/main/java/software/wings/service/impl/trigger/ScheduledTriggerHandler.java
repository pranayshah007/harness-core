/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.trigger;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType.IRREGULAR_SKIP_MISSED;

import static java.time.Duration.ofHours;
import static java.time.Duration.ofSeconds;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.iterator.PersistenceIterator;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.MongoPersistenceIterator.Handler;
import io.harness.mongo.iterator.filter.MorphiaFilterExpander;
import io.harness.mongo.iterator.provider.MorphiaPersistenceRequiredProvider;
import io.harness.workers.background.AccountStatusBasedEntityProcessController;

import software.wings.beans.trigger.Trigger;
import software.wings.beans.trigger.Trigger.TriggerKeys;
import software.wings.beans.trigger.TriggerConditionType;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.TriggerService;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@Singleton
@Slf4j
@TargetModule(HarnessModule._815_CG_TRIGGERS)
public class ScheduledTriggerHandler implements Handler<Trigger> {
  private static final int POOL_SIZE = 8;
  @Inject private PersistenceIteratorFactory persistenceIteratorFactory;
  @Inject private TriggerService triggerService;
  PersistenceIterator<Trigger> iterator;
  @Inject private MorphiaPersistenceRequiredProvider<Trigger> persistenceProvider;
  @Inject private AccountService accountService;

  private static ExecutorService executor =
      Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat("scheduled-trigger-handler").build());
  private static final ScheduledThreadPoolExecutor executorService = new ScheduledThreadPoolExecutor(
      POOL_SIZE, new ThreadFactoryBuilder().setNameFormat("Iterator-ScheduledTriggerThread").build());

  public void registerIterators() {
    iterator = persistenceIteratorFactory.createIterator(ScheduledTriggerHandler.class,
        MongoPersistenceIterator.<Trigger, MorphiaFilterExpander<Trigger>>builder()
            .mode(PersistenceIterator.ProcessMode.LOOP)
            .iteratorName("ScheduledTrigger")
            .clazz(Trigger.class)
            .fieldName(TriggerKeys.nextIterations)
            .acceptableNoAlertDelay(ofSeconds(60))
            .maximumDelayForCheck(ofHours(6))
            .executorService(executorService)
            .semaphore(new Semaphore(10))
            .handler(this)
            .entityProcessController(new AccountStatusBasedEntityProcessController<>(accountService))
            .persistenceProvider(persistenceProvider)
            .schedulingType(IRREGULAR_SKIP_MISSED)
            .filterExpander(query
                -> query.field(TriggerKeys.triggerConditionType)
                       .equal(TriggerConditionType.SCHEDULED)
                       .field(TriggerKeys.nextIterations)
                       .exists()
                       .field(TriggerKeys.nextIterations)
                       .notEqual(null)
                       .field(TriggerKeys.nextIterations)
                       .notEqual(Collections.emptyList())
                       .field(TriggerKeys.nextIterations)
                       .not()
                       .sizeEq(0))
            .throttleInterval(ofSeconds(45)));

    executor.submit(() -> iterator.process());
  }

  public void wakeup() {
    if (iterator != null) {
      iterator.wakeup();
    }
  }

  @Override
  public void handle(Trigger entity) {
    triggerService.triggerScheduledExecutionAsync(entity, Date.from(Instant.now()));
  }
}
