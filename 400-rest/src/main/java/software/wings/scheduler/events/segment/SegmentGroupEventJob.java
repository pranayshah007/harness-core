/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.scheduler.events.segment;

import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType.REGULAR;

import static java.time.Duration.ofMinutes;

import io.harness.beans.PageRequest;
import io.harness.beans.SearchFilter.Operator;
import io.harness.event.handler.impl.account.AccountChangeHandler;
import io.harness.iterator.IteratorExecutionHandler;
import io.harness.iterator.IteratorPumpAndRedisModeHandler;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.iterator.PersistenceIteratorFactory.PumpExecutorOptions;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.MongoPersistenceIterator.Handler;
import io.harness.mongo.iterator.filter.MorphiaFilterExpander;
import io.harness.mongo.iterator.provider.MorphiaPersistenceProvider;

import software.wings.beans.Account;
import software.wings.beans.account.AccountStatus;
import software.wings.scheduler.events.segment.SegmentGroupEventJobContext.SegmentGroupEventJobContextKeys;
import software.wings.service.intfc.AccountService;

import com.google.inject.Inject;
import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * Publishes `group` events to segment for all accounts at regular intervals.
 */
@Slf4j
public class SegmentGroupEventJob
    extends IteratorPumpAndRedisModeHandler implements Handler<SegmentGroupEventJobContext> {
  private static final Duration ACCEPTABLE_NO_ALERT_DELAY = ofMinutes(35);

  @Inject private PersistenceIteratorFactory persistenceIteratorFactory;
  @Inject private AccountChangeHandler accountChangeHandler;
  @Inject private AccountService accountService;
  @Inject private MorphiaPersistenceProvider<SegmentGroupEventJobContext> persistenceProvider;

  @Override
  protected void createAndStartIterator(PumpExecutorOptions executorOptions, Duration targetInterval) {
    iterator =
        (MongoPersistenceIterator<SegmentGroupEventJobContext, MorphiaFilterExpander<SegmentGroupEventJobContext>>)
            persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(executorOptions,
                SegmentGroupEventJob.class,
                MongoPersistenceIterator
                    .<SegmentGroupEventJobContext, MorphiaFilterExpander<SegmentGroupEventJobContext>>builder()
                    .clazz(SegmentGroupEventJobContext.class)
                    .fieldName(SegmentGroupEventJobContextKeys.nextIteration)
                    .targetInterval(targetInterval)
                    .acceptableNoAlertDelay(ACCEPTABLE_NO_ALERT_DELAY)
                    .handler(this)
                    .schedulingType(REGULAR)
                    .persistenceProvider(persistenceProvider)
                    .redistribute(true));
  }

  @Override
  protected void createAndStartRedisBatchIterator(
      PersistenceIteratorFactory.RedisBatchExecutorOptions executorOptions, Duration targetInterval) {
    iterator =
        (MongoPersistenceIterator<SegmentGroupEventJobContext, MorphiaFilterExpander<SegmentGroupEventJobContext>>)
            persistenceIteratorFactory.createRedisBatchIteratorWithDedicatedThreadPool(executorOptions,
                SegmentGroupEventJob.class,
                MongoPersistenceIterator
                    .<SegmentGroupEventJobContext, MorphiaFilterExpander<SegmentGroupEventJobContext>>builder()
                    .clazz(SegmentGroupEventJobContext.class)
                    .fieldName(SegmentGroupEventJobContextKeys.nextIteration)
                    .targetInterval(targetInterval)
                    .acceptableNoAlertDelay(ACCEPTABLE_NO_ALERT_DELAY)
                    .handler(this)
                    .persistenceProvider(persistenceProvider));
  }

  @Override
  public void registerIterator(IteratorExecutionHandler iteratorExecutionHandler) {
    iteratorName = "SegmentGroupEventJob";

    // Register the iterator with the iterator config handler.
    iteratorExecutionHandler.registerIteratorHandler(iteratorName, this);
  }

  @Override
  public void handle(SegmentGroupEventJobContext entity) {
    PageRequest<Account> request =
        aPageRequest().addFilter("_id", Operator.IN, entity.getAccountIds().toArray()).build();

    List<Account> accounts = accountService.list(request);

    log.info("Segment publish job with accounts. count={}", accounts.size());
    for (Account account : accounts) {
      if (Account.GLOBAL_ACCOUNT_ID.equals(account.getUuid())) {
        continue;
      }

      if (AccountStatus.ACTIVE.equals(accountService.getAccountStatus(account.getUuid()))) {
        log.info("publishing segment group event. accountId={}", account.getUuid());
        accountChangeHandler.publishAccountEventToSegment(account);
      }
    }
  }
}
