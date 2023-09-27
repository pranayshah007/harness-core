/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.scheduler.account;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType.REGULAR;

import static software.wings.utils.TimeUtils.isWeekend;

import static java.time.Duration.ofMinutes;
import static java.time.Duration.ofSeconds;

import io.harness.annotations.dev.OwnedBy;
import io.harness.iterator.IteratorExecutionHandler;
import io.harness.iterator.IteratorPumpAndRedisModeHandler;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.MongoPersistenceIterator.Handler;
import io.harness.mongo.iterator.filter.MorphiaFilterExpander;
import io.harness.mongo.iterator.provider.MorphiaPersistenceProvider;

import software.wings.app.JobsFrequencyConfig;
import software.wings.beans.Account;
import software.wings.beans.Account.AccountKeys;
import software.wings.beans.account.AccountStatus;
import software.wings.service.intfc.AccountService;

import com.google.inject.Inject;
import java.time.Duration;
import java.util.Collections;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@Slf4j
public class DeleteAccountHandler extends IteratorPumpAndRedisModeHandler implements Handler<Account> {
  private static final Duration ACCEPTABLE_NO_ALERT_DELAY = ofMinutes(Integer.MAX_VALUE);
  private static final Duration ACCEPTABLE_EXECUTION_TIME = ofSeconds(120);

  @Inject private PersistenceIteratorFactory persistenceIteratorFactory;
  @Inject private AccountService accountService;
  @Inject private JobsFrequencyConfig jobsFrequencyConfig;
  @Inject private MorphiaPersistenceProvider<Account> persistenceProvider;

  @Override
  protected void createAndStartIterator(
      PersistenceIteratorFactory.PumpExecutorOptions executorOptions, Duration targetInterval) {
    iterator = (MongoPersistenceIterator<Account, MorphiaFilterExpander<Account>>)
                   persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(executorOptions,
                       DeleteAccountHandler.class,
                       MongoPersistenceIterator.<Account, MorphiaFilterExpander<Account>>builder()
                           .clazz(Account.class)
                           .fieldName(AccountKeys.accountDeletionIteration)
                           .targetInterval(targetInterval)
                           .acceptableNoAlertDelay(ACCEPTABLE_NO_ALERT_DELAY)
                           .acceptableExecutionTime(ACCEPTABLE_EXECUTION_TIME)
                           .persistenceProvider(persistenceProvider)
                           .filterExpander(getFilterQuery())
                           .handler(this)
                           .schedulingType(REGULAR)
                           .redistribute(true));
  }

  @Override
  protected void createAndStartRedisBatchIterator(
      PersistenceIteratorFactory.RedisBatchExecutorOptions executorOptions, Duration targetInterval) {
    iterator = (MongoPersistenceIterator<Account, MorphiaFilterExpander<Account>>)
                   persistenceIteratorFactory.createRedisBatchIteratorWithDedicatedThreadPool(executorOptions,
                       DeleteAccountHandler.class,
                       MongoPersistenceIterator.<Account, MorphiaFilterExpander<Account>>builder()
                           .clazz(Account.class)
                           .fieldName(AccountKeys.accountDeletionIteration)
                           .targetInterval(targetInterval)
                           .acceptableNoAlertDelay(ACCEPTABLE_NO_ALERT_DELAY)
                           .acceptableExecutionTime(ACCEPTABLE_EXECUTION_TIME)
                           .filterExpander(getFilterQuery())
                           .persistenceProvider(persistenceProvider)
                           .handler(this));
  }

  private MorphiaFilterExpander<Account> getFilterQuery() {
    return query -> {
      query.and(
          query.criteria(AccountKeys.accountStatusKey).in(Collections.singleton(AccountStatus.MARKED_FOR_DELETION)));
    };
  }

  @Override
  public void registerIterator(IteratorExecutionHandler iteratorExecutionHandler) {
    iteratorName = "DeleteAccountIterator";

    // Register the iterator with the iterator config handler.
    iteratorExecutionHandler.registerIteratorHandler(iteratorName, this);
  }

  @Override
  public void handle(Account account) {
    // Delete accounts only on weekend
    if (isWeekend() && AccountStatus.MARKED_FOR_DELETION.equals(account.getLicenseInfo().getAccountStatus())) {
      accountService.delete(account.getUuid());
    }
  }
}
