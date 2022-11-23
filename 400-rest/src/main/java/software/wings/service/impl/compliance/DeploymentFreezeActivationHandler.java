/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.compliance;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType.IRREGULAR_SKIP_MISSED;

import static java.time.Duration.ofHours;
import static java.time.Duration.ofSeconds;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.governance.GovernanceFreezeConfig;
import io.harness.iterator.IteratorExecutionHandler;
import io.harness.iterator.IteratorLoopModeHandler;
import io.harness.iterator.PersistenceIterator;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.MongoPersistenceIterator.Handler;
import io.harness.mongo.iterator.filter.MorphiaFilterExpander;
import io.harness.mongo.iterator.provider.MorphiaPersistenceRequiredProvider;
import io.harness.workers.background.AccountStatusBasedEntityProcessController;

import software.wings.beans.governance.GovernanceConfig;
import software.wings.beans.governance.GovernanceConfig.GovernanceConfigKeys;
import software.wings.service.impl.deployment.checks.DeploymentFreezeUtils;
import software.wings.service.intfc.AccountService;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@Singleton
@Slf4j
@TargetModule(HarnessModule._953_EVENTS_API)
public class DeploymentFreezeActivationHandler extends IteratorLoopModeHandler implements Handler<GovernanceConfig> {
  @Inject private PersistenceIteratorFactory persistenceIteratorFactory;
  @Inject DeploymentFreezeUtils deploymentFreezeUtils;
  @Inject private MorphiaPersistenceRequiredProvider<GovernanceConfig> persistenceProvider;
  @Inject private AccountService accountService;

  @Override
  protected void createAndStartIterator(
      PersistenceIteratorFactory.PumpExecutorOptions executorOptions, Duration throttleInterval) {
    executor = Executors.newSingleThreadExecutor(
        new ThreadFactoryBuilder().setNameFormat("deployment-freeze-activation-handler").build());
    ExecutorService executorService = new ScheduledThreadPoolExecutor(executorOptions.getPoolSize(),
        new ThreadFactoryBuilder().setNameFormat("Iterator-DeploymentFreezeActivationThread").build());
    iterator = (MongoPersistenceIterator<GovernanceConfig, MorphiaFilterExpander<GovernanceConfig>>)
                   persistenceIteratorFactory.createIterator(DeploymentFreezeActivationHandler.class,
                       MongoPersistenceIterator.<GovernanceConfig, MorphiaFilterExpander<GovernanceConfig>>builder()
                           .mode(PersistenceIterator.ProcessMode.LOOP)
                           .iteratorName(iteratorName)
                           .clazz(GovernanceConfig.class)
                           .fieldName(GovernanceConfigKeys.nextIterations)
                           .acceptableNoAlertDelay(ofSeconds(60))
                           .maximumDelayForCheck(ofHours(6))
                           .executorService(executorService)
                           .semaphore(new Semaphore(10))
                           .handler(this)
                           .entityProcessController(new AccountStatusBasedEntityProcessController<>(accountService))
                           .persistenceProvider(persistenceProvider)
                           .schedulingType(IRREGULAR_SKIP_MISSED)
                           .filterExpander(query
                               -> query.field(GovernanceConfigKeys.nextIterations)
                                      .exists()
                                      .field(GovernanceConfigKeys.nextIterations)
                                      .notEqual(null)
                                      .field(GovernanceConfigKeys.nextIterations)
                                      .not()
                                      .sizeEq(0))
                           .throttleInterval(throttleInterval));

    executor.submit(() -> iterator.process());
  }

  @Override
  public void registerIterator(IteratorExecutionHandler iteratorExecutionHandler) {
    iteratorName = "DeploymentFreezeActivities";

    // Register the iterator with the iterator config handler.
    iteratorExecutionHandler.registerIteratorHandler(iteratorName, this);
  }

  public void wakeup() {
    if (iterator != null) {
      iterator.wakeup();
    }
  }

  @Override
  public void handle(GovernanceConfig entity) {
    long iteratorTime = System.currentTimeMillis();
    List<GovernanceFreezeConfig> governanceFreezeConfigs =
        entity.getTimeRangeBasedFreezeConfigs()
            .stream()
            .filter(freezeConfig
                -> (iteratorTime > freezeConfig.getTimeRange().getFrom())
                    && (iteratorTime - freezeConfig.getTimeRange().getFrom()
                        < DeploymentFreezeUtils.MAXIMUM_ITERATOR_DELAY))
            .collect(Collectors.toList());

    if (EmptyPredicate.isEmpty(governanceFreezeConfigs)) {
      log.warn("No deployment freeze windows found within 5 minutes of the current time: " + iteratorTime);
      return;
    }

    governanceFreezeConfigs.forEach(freezeWindow -> {
      try {
        deploymentFreezeUtils.handleActivationEvent(freezeWindow, entity.getAccountId());
      } catch (Exception e) {
        log.error("Failed to handle deployment freeze activation {}", freezeWindow.getName(), e);
      }
    });
  }
}
