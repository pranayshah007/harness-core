/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.scheduler;

import static io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType.REGULAR;

import static software.wings.beans.Account.GLOBAL_ACCOUNT_ID;

import static java.time.Duration.ofMinutes;
import static java.time.Duration.ofSeconds;

import io.harness.event.usagemetrics.UsageMetricsService;
import io.harness.iterator.IteratorExecutionHandler;
import io.harness.iterator.IteratorPumpAndRedisModeHandler;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.iterator.PersistenceIteratorFactory.PumpExecutorOptions;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.MongoPersistenceIterator.Handler;
import io.harness.mongo.iterator.filter.MorphiaFilterExpander;
import io.harness.mongo.iterator.provider.MorphiaPersistenceProvider;
import io.harness.workers.background.AccountLevelEntityProcessController;

import software.wings.beans.Account;
import software.wings.beans.Account.AccountKeys;
import software.wings.beans.AccountType;
import software.wings.beans.account.AccountStatus;
import software.wings.service.intfc.AccountService;

import com.google.inject.Inject;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class UsageMetricsHandler extends IteratorPumpAndRedisModeHandler implements Handler<Account> {
  private static final Duration ACCEPTABLE_NO_ALERT_DELAY = ofMinutes(30);
  private static final Duration ACCEPTABLE_EXECUTION_TIME = ofSeconds(30);

  @Inject private UsageMetricsService usageMetricsService;
  @Inject private AccountService accountService;
  @Inject private PersistenceIteratorFactory persistenceIteratorFactory;
  @Inject private MorphiaPersistenceProvider<Account> persistenceProvider;

  @Override
  protected void createAndStartIterator(PumpExecutorOptions executorOptions, Duration targetInterval) {
    iterator = (MongoPersistenceIterator<Account, MorphiaFilterExpander<Account>>)
                   persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(executorOptions,
                       UsageMetricsHandler.class,
                       MongoPersistenceIterator.<Account, MorphiaFilterExpander<Account>>builder()
                           .clazz(Account.class)
                           .fieldName(AccountKeys.usageMetricsTaskIteration)
                           .targetInterval(targetInterval)
                           .acceptableNoAlertDelay(ACCEPTABLE_NO_ALERT_DELAY)
                           .acceptableExecutionTime(ACCEPTABLE_EXECUTION_TIME)
                           .handler(this)
                           .entityProcessController(new AccountLevelEntityProcessController(accountService))
                           .schedulingType(REGULAR)
                           .persistenceProvider(persistenceProvider)
                           .redistribute(true));
  }

  @Override
  protected void createAndStartRedisBatchIterator(
      PersistenceIteratorFactory.RedisBatchExecutorOptions executorOptions, Duration targetInterval) {
    iterator = (MongoPersistenceIterator<Account, MorphiaFilterExpander<Account>>)
                   persistenceIteratorFactory.createRedisBatchIteratorWithDedicatedThreadPool(executorOptions,
                       UsageMetricsHandler.class,
                       MongoPersistenceIterator.<Account, MorphiaFilterExpander<Account>>builder()
                           .clazz(Account.class)
                           .fieldName(AccountKeys.usageMetricsTaskIteration)
                           .targetInterval(targetInterval)
                           .acceptableNoAlertDelay(ACCEPTABLE_NO_ALERT_DELAY)
                           .acceptableExecutionTime(ACCEPTABLE_EXECUTION_TIME)
                           .handler(this)
                           .entityProcessController(new AccountLevelEntityProcessController(accountService))
                           .persistenceProvider(persistenceProvider));
  }

  @Override
  public void registerIterator(IteratorExecutionHandler iteratorExecutionHandler) {
    iteratorName = "UsageMetricsHandler";

    // Register the iterator with the iterator config handler.
    iteratorExecutionHandler.registerIteratorHandler(iteratorName, this);
  }

  @Override
  public void handle(Account account) {
    if ((account.getLicenseInfo() == null
            || ((account.getLicenseInfo() != null && account.getLicenseInfo().getAccountStatus() != null)
                && (account.getLicenseInfo().getAccountStatus().equals(AccountStatus.ACTIVE)
                    && (account.getLicenseInfo().getAccountType().equals(AccountType.TRIAL)
                        || account.getLicenseInfo().getAccountType().equals(AccountType.PAID)))))
        && (!account.getUuid().equals(GLOBAL_ACCOUNT_ID))) {
      usageMetricsService.createVerificationUsageEvents(account);
      usageMetricsService.createSetupEventsForTimescaleDB(account);
    }
  }
}
