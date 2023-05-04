/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.credit.schedular;

import static java.time.Duration.ofHours;
import static java.time.Duration.ofMinutes;
import static java.time.Duration.ofSeconds;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.credit.entities.Credit;
import io.harness.credit.entities.Credit.CreditsKeys;
import io.harness.credit.services.CreditService;
import io.harness.credit.utils.CreditStatus;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.iterator.PersistentIterable;
import io.harness.mongo.iterator.MongoPersistenceIterator.Handler;
import io.harness.mongo.iterator.filter.MorphiaFilterExpander;
import io.harness.mongo.iterator.provider.MorphiaPersistenceProvider;

import com.google.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.GTM)
public abstract class CreditExpiryIteratorHandler<T extends Credit & PersistentIterable> implements Handler<T> {
  protected static final Duration ACCEPTABLE_NO_ALERT_DELAY = ofMinutes(60);
  protected static final Duration ACCEPTABLE_EXECUTION_TIME = ofSeconds(15);
  protected static final Duration TARGET_INTERVAL = ofSeconds(31);
  protected static final Duration INTERVAL = ofHours(6);

  protected final PersistenceIteratorFactory persistenceIteratorFactory;
  protected final MorphiaPersistenceProvider<T> persistenceProvider;
  protected CreditService creditService;

  @Inject
  public CreditExpiryIteratorHandler(PersistenceIteratorFactory persistenceIteratorFactory,
      MorphiaPersistenceProvider<T> persistenceProvider, CreditService creditService) {
    this.persistenceIteratorFactory = persistenceIteratorFactory;
    this.persistenceProvider = persistenceProvider;
    this.creditService = creditService;
  }

  public abstract void registerIterator(int threadPoolSize);

  @Override
  public void handle(T entity) {
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

  protected MorphiaFilterExpander<T> getFilterQuery() {
    return query
        -> query.field(CreditsKeys.creditStatus)
               .equal(CreditStatus.ACTIVE)
               .field(CreditsKeys.expiryTime)
               .greaterThan(0)
               .field(CreditsKeys.expiryTime)
               .lessThan(Instant.now().toEpochMilli());
  }
}
