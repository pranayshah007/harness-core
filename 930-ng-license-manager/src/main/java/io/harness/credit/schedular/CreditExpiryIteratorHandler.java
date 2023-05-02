/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.credit.schedular;

import static io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType.REGULAR;

import static java.time.Duration.ofHours;
import static java.time.Duration.ofMinutes;
import static java.time.Duration.ofSeconds;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.credit.entities.CICredit;
import io.harness.credit.entities.Credit;
import io.harness.credit.entities.Credit.CreditsKeys;
import io.harness.credit.services.CreditService;
import io.harness.credit.utils.CreditStatus;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.MongoPersistenceIterator.Handler;
import io.harness.mongo.iterator.filter.MorphiaFilterExpander;
import io.harness.mongo.iterator.provider.MorphiaPersistenceProvider;

import com.google.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.GTM)
public class CreditExpiryIteratorHandler implements Handler<CICredit> {
  private static final Duration ACCEPTABLE_NO_ALERT_DELAY = ofMinutes(60);
  private static final Duration ACCEPTABLE_EXECUTION_TIME = ofSeconds(15);
  private static final Duration TARGET_INTERVAL = ofSeconds(31);
  private static final Duration INTERVAL = ofHours(6);

  private final PersistenceIteratorFactory persistenceIteratorFactory;
  private final MorphiaPersistenceProvider<CICredit> persistenceProvider;
  private CreditService creditService;

  @Inject
  public CreditExpiryIteratorHandler(PersistenceIteratorFactory persistenceIteratorFactory,
      MorphiaPersistenceProvider<CICredit> persistenceProvider, CreditService creditService) {
    this.persistenceIteratorFactory = persistenceIteratorFactory;
    this.persistenceProvider = persistenceProvider;
    this.creditService = creditService;
  }

  public void registerIterator(int threadPoolSize) {
    persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(
        PersistenceIteratorFactory.PumpExecutorOptions.builder()
            .name(this.getClass().getName())
            .poolSize(threadPoolSize)
            .interval(INTERVAL)
            .build(),
        Credit.class,
        MongoPersistenceIterator.<CICredit, MorphiaFilterExpander<CICredit>>builder()
            .clazz(CICredit.class)
            .fieldName(CreditsKeys.creditExpiryCheckIteration)
            .targetInterval(TARGET_INTERVAL)
            .acceptableNoAlertDelay(ACCEPTABLE_NO_ALERT_DELAY)
            .acceptableExecutionTime(ACCEPTABLE_EXECUTION_TIME)
            .handler(this)
            .filterExpander(query
                -> query.field(CreditsKeys.creditStatus)
                       .equal(CreditStatus.ACTIVE)
                       .field(CreditsKeys.expiryTime)
                       .greaterThan(0)
                       .field(CreditsKeys.expiryTime)
                       .lessThan(Instant.now().toEpochMilli()))
            .schedulingType(REGULAR)
            .persistenceProvider(persistenceProvider)
            .redistribute(true));
  }

  @Override
  public void handle(CICredit entity) {
    if (entity == null) {
      log.warn("Credit entity is null for credit expiry check");
      return;
    }
    try {
      creditService.setCreditStatusExpired(entity);
    } catch (Exception ex) {
      log.error("Error while handling credit expiry check", ex);
    }
  }
}
