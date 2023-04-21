/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.credit.schedular;

import static io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType.REGULAR;

import static java.time.Duration.ofMinutes;
import static java.time.Duration.ofSeconds;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.credit.entities.Credit;
import io.harness.credit.services.CreditService;
import io.harness.credit.utils.CreditStatus;
import io.harness.iterator.IteratorExecutionHandler;
import io.harness.iterator.IteratorPumpAndRedisModeHandler;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.iterator.PersistenceIteratorFactory.PumpExecutorOptions;
import io.harness.iterator.PersistenceIteratorFactory.RedisBatchExecutorOptions;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.MongoPersistenceIterator.Handler;
import io.harness.mongo.iterator.filter.MorphiaFilterExpander;
import io.harness.mongo.iterator.provider.MorphiaPersistenceProvider;

import com.google.inject.Inject;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;

/**
 * Handler class that checks for license expiry
 * @author rktummala
 */

@Slf4j
@OwnedBy(HarnessTeam.GTM)
public class CreditExpiryHandler extends IteratorPumpAndRedisModeHandler implements Handler<Credit> {
  private static final Duration ACCEPTABLE_NO_ALERT_DELAY = ofMinutes(60);
  private static final Duration ACCEPTABLE_EXECUTION_TIME = ofSeconds(15);

  @Inject private PersistenceIteratorFactory persistenceIteratorFactory;
  @Inject private MorphiaPersistenceProvider<Credit> persistenceProvider;
  @Inject private CreditService creditService;

  @Override
  protected void createAndStartIterator(PumpExecutorOptions executorOptions, Duration targetInterval) {
    iterator = (MongoPersistenceIterator<Credit, MorphiaFilterExpander<Credit>>)
                   persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(executorOptions,
                       CreditExpiryHandler.class,
                       MongoPersistenceIterator.<Credit, MorphiaFilterExpander<Credit>>builder()
                           .clazz(Credit.class)
                           .fieldName(Credit.CreditsKeys.creditExpiryCheckIteration)
                           .targetInterval(targetInterval)
                           .acceptableNoAlertDelay(ACCEPTABLE_NO_ALERT_DELAY)
                           .acceptableExecutionTime(ACCEPTABLE_EXECUTION_TIME)
                           .handler(this)
                           .filterExpander(query
                               -> query.field(Credit.CreditsKeys.creditStatus)
                                      .equal(Credit.CreditsKeys.creditStatus.equals(CreditStatus.ACTIVE))
                                      .field(Credit.CreditsKeys.expiryTime)
                                      .greaterThan(0)
                                      .field(Credit.CreditsKeys.expiryTime)
                                      .lessThan(System.currentTimeMillis()))
                           .schedulingType(REGULAR)
                           .persistenceProvider(persistenceProvider)
                           .redistribute(true));
  }

  @Override
  protected void createAndStartRedisBatchIterator(RedisBatchExecutorOptions executorOptions, Duration targetInterval) {
    iterator = (MongoPersistenceIterator<Credit, MorphiaFilterExpander<Credit>>)
                   persistenceIteratorFactory.createRedisBatchIteratorWithDedicatedThreadPool(executorOptions,
                       CreditExpiryHandler.class,
                       MongoPersistenceIterator.<Credit, MorphiaFilterExpander<Credit>>builder()
                           .clazz(Credit.class)
                           .fieldName(Credit.CreditsKeys.creditExpiryCheckIteration)
                           .targetInterval(targetInterval)
                           .acceptableNoAlertDelay(ACCEPTABLE_NO_ALERT_DELAY)
                           .acceptableExecutionTime(ACCEPTABLE_EXECUTION_TIME)
                           .handler(this)
                           .filterExpander(query
                               -> query.field(Credit.CreditsKeys.creditStatus)
                                      .equal(Credit.CreditsKeys.creditStatus.equals(CreditStatus.ACTIVE))
                                      .field(Credit.CreditsKeys.expiryTime)
                                      .greaterThan(0)
                                      .field(Credit.CreditsKeys.expiryTime)
                                      .lessThan(System.currentTimeMillis()))
                           .persistenceProvider(persistenceProvider));
  }

  @Override
  public void registerIterator(IteratorExecutionHandler iteratorExecutionHandler) {
    iteratorName = "CreditExpiryHandler";

    // Register the iterator with the iterator config handler.
    iteratorExecutionHandler.registerIteratorHandler(iteratorName, this);
  }

  public void registerIterator(int threadPoolSize) {
    registerIteratorFactory(threadPoolSize);
  }

  public void registerIteratorFactory(int threadPoolSize) {
    persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(
        PersistenceIteratorFactory.PumpExecutorOptions.builder()
            .name(this.getClass().getName())
            .poolSize(threadPoolSize)
            // need to determine later
            .interval(ofSeconds(5))
            .build(),
        Credit.class,
        MongoPersistenceIterator.<Credit, MorphiaFilterExpander<Credit>>builder()
            .clazz(Credit.class)
            .fieldName(Credit.CreditsKeys.creditExpiryCheckIteration)
            .targetInterval(ofSeconds(31))
            .acceptableNoAlertDelay(ACCEPTABLE_NO_ALERT_DELAY)
            .acceptableExecutionTime(ACCEPTABLE_EXECUTION_TIME)
            .handler(this)
            .filterExpander(query
                -> query.field(Credit.CreditsKeys.creditStatus)
                       .equal(Credit.CreditsKeys.creditStatus.equals(CreditStatus.ACTIVE))
                       .field(Credit.CreditsKeys.expiryTime)
                       .greaterThan(0)
                       .field(Credit.CreditsKeys.expiryTime)
                       .lessThan(System.currentTimeMillis()))
            .schedulingType(REGULAR)
            .persistenceProvider(persistenceProvider)
            .redistribute(true));
  }

  @Override
  public void handle(Credit entity) {
    if (entity == null) {
      log.warn("Credit entity is null for credit expiry check");
      return;
    }
    try {
      log.info("Running credits check job");
      creditService.checkForCreditExpiry(entity);
      log.info("credit check job complete");
    } catch (Exception ex) {
      log.error("Error while checking credit", ex);
    }
  }
}
