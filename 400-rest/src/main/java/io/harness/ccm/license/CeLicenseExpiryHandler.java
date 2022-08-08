/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.license;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType.REGULAR;

import static java.time.Duration.ofDays;
import static java.time.Duration.ofMinutes;

import io.harness.annotations.dev.OwnedBy;
import io.harness.iterator.PersistenceIterator;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.MongoPersistenceIterator.Handler;
import io.harness.mongo.iterator.filter.MorphiaFilterExpander;
import io.harness.mongo.iterator.provider.MorphiaPersistenceProvider;

import software.wings.beans.Account;
import software.wings.beans.Account.AccountKeys;
import software.wings.service.intfc.AccountService;

import com.google.inject.Inject;
import java.time.Instant;
import java.util.Optional;

@OwnedBy(CE)
public class CeLicenseExpiryHandler implements Handler<Account> {
  private static final int CE_LICENSE_EXPIRY_INTERVAL_DAY = 1;

  @Inject private PersistenceIteratorFactory persistenceIteratorFactory;
  @Inject private MorphiaPersistenceProvider<Account> persistenceProvider;
  @Inject private AccountService accountService;
  PersistenceIterator<Account> iterator;

  public void registerIterators() {
    iterator = persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(
        PersistenceIteratorFactory.PumpExecutorOptions.builder()
            .name("CeLicenceExpiryProcessor")
            .poolSize(2)
            .interval(ofDays(CE_LICENSE_EXPIRY_INTERVAL_DAY))
            .build(),
        CeLicenseExpiryHandler.class,
        MongoPersistenceIterator.<Account, MorphiaFilterExpander<Account>>builder()
            .clazz(Account.class)
            .fieldName(AccountKeys.ceLicenseExpiryIteration)
            .targetInterval(ofMinutes(CE_LICENSE_EXPIRY_INTERVAL_DAY))
            .acceptableNoAlertDelay(ofMinutes(60))
            .acceptableExecutionTime(ofMinutes(5))
            .handler(this)
            .filterExpander(query -> query.field(AccountKeys.ceLicenseInfo).exists())
            .schedulingType(REGULAR)
            .persistenceProvider(persistenceProvider)
            .redistribute(true));
  }

  @Override
  public void handle(Account account) {
    CeLicenseInfo ceLicenseInfo =
        Optional.ofNullable(account.getCeLicenseInfo()).orElse(CeLicenseInfo.builder().build());
    long actualExpiryTime = ceLicenseInfo.getExpiryTime();
    long expiryTime = ceLicenseInfo.getExpiryTimeWithGracePeriod();
    if (expiryTime != 0L && Instant.now().toEpochMilli() > expiryTime) {
      account.setCloudCostEnabled(false);
      accountService.update(account);
    }

    if (actualExpiryTime == Long.MAX_VALUE && !account.isCloudCostEnabled()) {
      account.setCloudCostEnabled(true);
      accountService.update(account);
    }
  }
}
