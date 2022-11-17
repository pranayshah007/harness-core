/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.workers.background.iterator;

import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;
import static io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType.REGULAR;

import static software.wings.service.impl.instance.InstanceSyncFlow.ITERATOR;

import static java.time.Duration.ofMinutes;
import static java.time.Duration.ofSeconds;

import io.harness.iterator.IteratorExecutionHandler;
import io.harness.iterator.IteratorPumpModeHandler;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;
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
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;

/**
 * Handler class that syncs all the instances of an inframapping.
 */

@Slf4j
public class InstanceSyncHandler extends IteratorPumpModeHandler implements Handler<InfrastructureMapping> {
  @Inject private AccountService accountService;
  @Inject private PersistenceIteratorFactory persistenceIteratorFactory;
  @Inject private InstanceHelper instanceHelper;
  @Inject private MorphiaPersistenceProvider<InfrastructureMapping> persistenceProvider;

  @Override
  protected void createAndStartIterator(
      PersistenceIteratorFactory.PumpExecutorOptions executorOptions, Duration targetInterval) {
    iterator =
        (MongoPersistenceIterator<InfrastructureMapping, MorphiaFilterExpander<InfrastructureMapping>>)
            persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(executorOptions,
                InstanceSyncHandler.class,
                MongoPersistenceIterator.<InfrastructureMapping, MorphiaFilterExpander<InfrastructureMapping>>builder()
                    .clazz(InfrastructureMapping.class)
                    .fieldName(InfrastructureMappingKeys.nextIteration)
                    .targetInterval(targetInterval)
                    .acceptableNoAlertDelay(ofMinutes(10))
                    .acceptableExecutionTime(ofSeconds(30))
                    .handler(this)
                    .entityProcessController(new AccountStatusBasedEntityProcessController<>(accountService))
                    .schedulingType(REGULAR)
                    .persistenceProvider(persistenceProvider)
                    .redistribute(true));
  }

  @Override
  public void registerIterator(IteratorExecutionHandler iteratorExecutionHandler) {
    iteratorName = "InstanceSync";

    // Register the iterator with the iterator config handler.
    iteratorExecutionHandler.registerIteratorHandler(iteratorName, this);
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
