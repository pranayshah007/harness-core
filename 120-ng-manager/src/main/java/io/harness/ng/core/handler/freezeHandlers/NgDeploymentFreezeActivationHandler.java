/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.handler.freezeHandlers;

import static io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType.IRREGULAR_SKIP_MISSED;

import static java.time.Duration.ofMinutes;
import static java.time.Duration.ofSeconds;
import static org.springframework.data.mongodb.core.query.Criteria.where;

import io.harness.cdng.NgExpressionHelper;
import io.harness.freeze.beans.FreezeStatus;
import io.harness.freeze.beans.FreezeType;
import io.harness.freeze.entity.FreezeConfigEntity;
import io.harness.freeze.entity.FreezeConfigEntity.FreezeConfigEntityKeys;
import io.harness.freeze.notifications.NotificationHelper;
import io.harness.iterator.PersistenceIterator;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.filter.SpringFilterExpander;
import io.harness.mongo.iterator.provider.SpringPersistenceRequiredProvider;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

@Slf4j
public class NgDeploymentFreezeActivationHandler implements MongoPersistenceIterator.Handler<FreezeConfigEntity> {
  private static final int POOL_SIZE = 3;
  @Inject private PersistenceIteratorFactory persistenceIteratorFactory;
  @Inject private MongoTemplate mongoTemplate;
  @Inject NotificationHelper notificationHelper;
  @Inject NgExpressionHelper ngExpressionHelper;
  PersistenceIterator<FreezeConfigEntity> iterator;

  private static ExecutorService executor = Executors.newSingleThreadExecutor(
      new ThreadFactoryBuilder().setNameFormat("ng-deployment-freeze-activation-handler").build());
  private static final ScheduledThreadPoolExecutor executorService = new ScheduledThreadPoolExecutor(
      POOL_SIZE, new ThreadFactoryBuilder().setNameFormat("Iterator-NgDeploymentFreezeActivationThread").build());

  public void registerIterators() {
    iterator = persistenceIteratorFactory.createIterator(NgDeploymentFreezeActivationHandler.class,
        MongoPersistenceIterator.<FreezeConfigEntity, SpringFilterExpander>builder()
            .mode(PersistenceIterator.ProcessMode.LOOP)
            .iteratorName("NgDeploymentFreezeActivities")
            .clazz(FreezeConfigEntity.class)
            .fieldName(FreezeConfigEntityKeys.nextIterations)
            .acceptableNoAlertDelay(ofSeconds(60))
            .maximumDelayForCheck(ofMinutes(10))
            .executorService(executorService)
            .semaphore(new Semaphore(10))
            .handler(this)
            .persistenceProvider(new SpringPersistenceRequiredProvider<>(mongoTemplate))
            .schedulingType(IRREGULAR_SKIP_MISSED)
            .filterExpander(q
                -> q.addCriteria(where(FreezeConfigEntityKeys.status).is(FreezeStatus.ENABLED))
                       .addCriteria(where(FreezeConfigEntityKeys.nextIterations).not().size(0)))
            .throttleInterval(ofSeconds(45)));

    executor.submit(() -> iterator.process());
  }

  public void wakeup() {
    if (iterator != null) {
      iterator.wakeup();
    }
  }

  @Override
  public void handle(FreezeConfigEntity entity) {
    try {
      String baseUrl = ngExpressionHelper.getBaseUrl(entity.getAccountId());
      notificationHelper.sendNotification(entity.getYaml(), false, true, null, entity.getAccountId(), null, baseUrl,
          entity.getType() == FreezeType.GLOBAL);
    } catch (Exception e) {
      log.error(
          String.format("Unable to send notifications for freeze with identifier - %s", entity.getIdentifier()), e);
    }
  }
}
