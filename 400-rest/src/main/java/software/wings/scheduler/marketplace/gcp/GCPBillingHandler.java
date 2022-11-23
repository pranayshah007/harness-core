/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.scheduler.marketplace.gcp;

import static io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType.REGULAR;

import static java.time.Duration.ofMinutes;

import io.harness.iterator.IteratorExecutionHandler;
import io.harness.iterator.IteratorPumpModeHandler;
import io.harness.iterator.PersistenceIterator.ProcessMode;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.MongoPersistenceIterator.Handler;
import io.harness.mongo.iterator.filter.MorphiaFilterExpander;
import io.harness.mongo.iterator.provider.MorphiaPersistenceProvider;
import io.harness.workers.background.AccountStatusBasedEntityProcessController;

import software.wings.beans.marketplace.gcp.GCPBillingJobEntity;
import software.wings.beans.marketplace.gcp.GCPBillingJobEntity.GCPBillingJobEntityKeys;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.marketplace.gcp.GCPMarketPlaceService;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import java.time.Duration;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GCPBillingHandler extends IteratorPumpModeHandler implements Handler<GCPBillingJobEntity> {
  @Inject private AccountService accountService;
  @Inject private PersistenceIteratorFactory persistenceIteratorFactory;
  @Inject GCPMarketPlaceService gcpMarketPlaceService;
  @Inject private MorphiaPersistenceProvider<GCPBillingJobEntity> persistenceProvider;

  @Override
  protected void createAndStartIterator(
      PersistenceIteratorFactory.PumpExecutorOptions executorOptions, Duration targetInterval) {
    ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(
        executorOptions.getPoolSize(), new ThreadFactoryBuilder().setNameFormat("Iterator-GCPBilling").build());
    Semaphore semaphore = new Semaphore(executorOptions.getPoolSize());
    iterator = MongoPersistenceIterator.<GCPBillingJobEntity, MorphiaFilterExpander<GCPBillingJobEntity>>builder()
                   .mode(ProcessMode.PUMP)
                   .clazz(GCPBillingJobEntity.class)
                   .fieldName(GCPBillingJobEntityKeys.nextIteration)
                   .targetInterval(targetInterval)
                   .acceptableNoAlertDelay(ofMinutes(1))
                   .executorService(executor)
                   .semaphore(semaphore)
                   .handler(this)
                   .entityProcessController(new AccountStatusBasedEntityProcessController<>(accountService))
                   .schedulingType(REGULAR)
                   .redistribute(true)
                   .persistenceProvider(persistenceProvider)
                   .build();
    // this'll check every 30 minutes if there are any new jobs to process.
    // this value must be lower than `targetInterval`
    executor.scheduleAtFixedRate(() -> iterator.process(), 0, 30, TimeUnit.MINUTES);
  }

  @Override
  public void registerIterator(IteratorExecutionHandler iteratorExecutionHandler) {
    iteratorName = "GCPBilling";

    // Register the iterator with the iterator config handler.
    iteratorExecutionHandler.registerIteratorHandler(iteratorName, this);
  }

  @Override
  public void handle(GCPBillingJobEntity entity) {
    log.info("Inside GCP billing handler ! {} ", entity.toString());
    try {
      gcpMarketPlaceService.createUsageReport(entity.getAccountId());
    } catch (Exception ex) {
      log.error("GCP_MKT_PLACE exception in handling request : ", ex);
    }
  }
}
