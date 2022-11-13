/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.scheduler;

import static io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType.REGULAR;

import static java.time.Duration.ofSeconds;

import io.harness.beans.FeatureName;
import io.harness.event.reconciliation.service.DeploymentReconTaskV2;
import io.harness.event.timeseries.processor.DeploymentEventProcessor;
import io.harness.ff.FeatureFlagService;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.lock.PersistentLocker;
import io.harness.mongo.iterator.IteratorConfig;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.MongoPersistenceIterator.Handler;
import io.harness.mongo.iterator.filter.MorphiaFilterExpander;
import io.harness.mongo.iterator.provider.MorphiaPersistenceProvider;
import io.harness.workers.background.AccountLevelEntityProcessController;

import software.wings.beans.Account;
import software.wings.search.framework.ExecutionEntity;
import software.wings.service.intfc.AccountService;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.Set;
import java.util.concurrent.ExecutorService;

public class DataReconciliationHandler implements Handler<Account> {
  @Inject AccountService accountService;
  @Inject DeploymentEventProcessor deploymentEventProcessor;
  @Inject private FeatureFlagService featureFlagService;

  @Inject private PersistentLocker persistentLocker;
  @Inject private Set<ExecutionEntity<?>> executionEntities;
  @Inject @Named("DeploymentReconTaskExecutor") ExecutorService executorService;

  @Inject private PersistenceIteratorFactory persistenceIteratorFactory;
  @Inject private MorphiaPersistenceProvider<Account> persistenceProvider;
  public void registerIterators(IteratorConfig iteratorConfig) {
    if (!iteratorConfig.isEnabled()
        && !featureFlagService.isGlobalEnabled(FeatureName.DEPLOYMENT_RECONCILIATION_REDESIGN)) {
      return;
    }
    persistenceIteratorFactory.createLoopIteratorWithDedicatedThreadPool(
        PersistenceIteratorFactory.PumpExecutorOptions.builder()
            .name("DataReconciliationHandler")
            .poolSize(iteratorConfig.getThreadPoolCount())
            .interval(ofSeconds(iteratorConfig.getTargetIntervalInSeconds()))
            .build(),
        DataReconciliationHandler.class,
        MongoPersistenceIterator.<Account, MorphiaFilterExpander<Account>>builder()
            .clazz(Account.class)
            .fieldName(Account.AccountKeys.dataReconciliationIteration)
            .targetInterval(ofSeconds(iteratorConfig.getTargetIntervalInSeconds()))
            .acceptableNoAlertDelay(ofSeconds(iteratorConfig.getTargetIntervalInSeconds() * 2))
            .handler(this)
            .entityProcessController(new AccountLevelEntityProcessController(accountService))
            .schedulingType(REGULAR)
            .persistenceProvider(persistenceProvider)
            .redistribute(true));
  }

  @Override
  public void handle(Account account) {
    DeploymentReconTaskV2 deploymentReconTaskV2 = new DeploymentReconTaskV2(accountService, deploymentEventProcessor,
        featureFlagService, persistentLocker, executionEntities, account, executorService);

    deploymentReconTaskV2.run();
  }
}
