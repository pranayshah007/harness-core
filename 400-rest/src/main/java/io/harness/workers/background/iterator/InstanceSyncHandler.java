/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.workers.background.iterator;

import static io.harness.logging.logcontext.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;
import static io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType.REGULAR;

import static software.wings.service.impl.instance.InstanceSyncFlow.ITERATOR;

import static java.time.Duration.ofMinutes;
import static java.time.Duration.ofSeconds;

import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.iterator.PersistenceIteratorFactory.PumpExecutorOptions;
import io.harness.logging.logcontext.AccountLogContext;
import io.harness.logging.logcontext.AutoLogContext;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.MongoPersistenceIterator.Handler;
import io.harness.mongo.iterator.filter.MorphiaFilterExpander;
import io.harness.mongo.iterator.provider.MorphiaPersistenceProvider;
import io.harness.workers.background.AccountStatusBasedEntityProcessController;

import software.wings.beans.InfrastructureMapping;
import software.wings.beans.InfrastructureMapping.InfrastructureMappingKeys;
import software.wings.service.impl.InfraMappingLogContext;
import software.wings.service.impl.instance.InstanceHelper;
import software.wings.service.intfc.AccountService;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

/**
 * Handler class that syncs all the instances of an inframapping.
 */

@Slf4j
public class InstanceSyncHandler implements Handler<InfrastructureMapping> {
  @Inject private AccountService accountService;
  @Inject private PersistenceIteratorFactory persistenceIteratorFactory;
  @Inject private InstanceHelper instanceHelper;
  @Inject private MorphiaPersistenceProvider<InfrastructureMapping> persistenceProvider;

  public void registerIterators(int threadPoolSize) {
    persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(
        PumpExecutorOptions.builder().name("InstanceSync").poolSize(threadPoolSize).interval(ofSeconds(30)).build(),
        InstanceSyncHandler.class,
        MongoPersistenceIterator.<InfrastructureMapping, MorphiaFilterExpander<InfrastructureMapping>>builder()
            .clazz(InfrastructureMapping.class)
            .fieldName(InfrastructureMappingKeys.nextIteration)
            .targetInterval(ofMinutes(10))
            .acceptableNoAlertDelay(ofMinutes(10))
            .acceptableExecutionTime(ofSeconds(30))
            .handler(this)
            .entityProcessController(new AccountStatusBasedEntityProcessController<>(accountService))
            .schedulingType(REGULAR)
            .persistenceProvider(persistenceProvider)
            .redistribute(true));
  }

  @Override
  public void handle(InfrastructureMapping infrastructureMapping) {
    try (AutoLogContext ignore1 = new AccountLogContext(infrastructureMapping.getAccountId(), OVERRIDE_ERROR);
         AutoLogContext ignore2 = new InfraMappingLogContext(infrastructureMapping.getUuid(), OVERRIDE_ERROR)) {
      if (instanceHelper.shouldSkipIteratorInstanceSync(infrastructureMapping)) {
        return;
      }

      try {
        instanceHelper.syncNow(infrastructureMapping.getAppId(), infrastructureMapping, ITERATOR);
      } catch (Exception ex) {
        log.error("Error while syncing instances for Infrastructure Mapping {}", infrastructureMapping.getUuid(), ex);
      }
    }
  }
}
