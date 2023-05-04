/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.credit.schedular;

import static io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType.REGULAR;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.credit.entities.CICredit;
import io.harness.credit.entities.Credit.CreditsKeys;
import io.harness.credit.services.CreditService;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.filter.MorphiaFilterExpander;
import io.harness.mongo.iterator.provider.MorphiaPersistenceProvider;

import com.google.inject.Inject;

@OwnedBy(HarnessTeam.GTM)
public class CICreditExpiryIteratorHandler extends CreditExpiryIteratorHandler<CICredit> {
  @Inject
  public CICreditExpiryIteratorHandler(PersistenceIteratorFactory persistenceIteratorFactory,
      MorphiaPersistenceProvider<CICredit> persistenceProvider, CreditService creditService,
      PersistenceIteratorFactory persistenceIteratorFactory1) {
    super(persistenceIteratorFactory, persistenceProvider, creditService);
  }

  public void registerIterator(int threadPoolSize) {
    persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(
        PersistenceIteratorFactory.PumpExecutorOptions.builder()
            .name(this.getClass().getName())
            .poolSize(threadPoolSize)
            .interval(INTERVAL)
            .build(),
        CICredit.class,
        MongoPersistenceIterator.<CICredit, MorphiaFilterExpander<CICredit>>builder()
            .clazz(CICredit.class)
            .fieldName(CreditsKeys.creditExpiryCheckIteration)
            .targetInterval(TARGET_INTERVAL)
            .acceptableNoAlertDelay(ACCEPTABLE_NO_ALERT_DELAY)
            .acceptableExecutionTime(ACCEPTABLE_EXECUTION_TIME)
            .handler(this)
            .filterExpander(getFilterQuery())
            .schedulingType(REGULAR)
            .persistenceProvider(persistenceProvider)
            .redistribute(true));
  }
}
