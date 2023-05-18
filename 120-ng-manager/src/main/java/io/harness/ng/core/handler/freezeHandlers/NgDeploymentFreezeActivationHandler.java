/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.handler.freezeHandlers;

import static io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType.IRREGULAR_SKIP_MISSED;

import static java.time.Duration.ofDays;
import static java.time.Duration.ofSeconds;
import static java.util.Objects.isNull;
import static org.springframework.data.mongodb.core.query.Criteria.where;

import io.harness.cdng.helpers.NgExpressionHelper;
import io.harness.freeze.beans.CurrentOrUpcomingWindow;
import io.harness.freeze.beans.FreezeStatus;
import io.harness.freeze.beans.FreezeType;
import io.harness.freeze.beans.FreezeWindow;
import io.harness.freeze.beans.yaml.FreezeConfig;
import io.harness.freeze.beans.yaml.FreezeInfoConfig;
import io.harness.freeze.entity.FreezeConfigEntity;
import io.harness.freeze.entity.FreezeConfigEntity.FreezeConfigEntityKeys;
import io.harness.freeze.helpers.FreezeTimeUtils;
import io.harness.freeze.mappers.NGFreezeDtoMapper;
import io.harness.freeze.notifications.NotificationHelper;
import io.harness.freeze.service.FreezeCRUDService;
import io.harness.iterator.PersistenceIterator;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.filter.SpringFilterExpander;
import io.harness.mongo.iterator.provider.SpringPersistenceRequiredProvider;

import com.google.inject.Inject;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Semaphore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

@Slf4j
public class NgDeploymentFreezeActivationHandler implements MongoPersistenceIterator.Handler<FreezeConfigEntity> {
  @Inject private PersistenceIteratorFactory persistenceIteratorFactory;
  @Inject private MongoTemplate mongoTemplate;
  @Inject NotificationHelper notificationHelper;
  @Inject NgExpressionHelper ngExpressionHelper;
  @Inject FreezeCRUDService freezeCRUDService;
  MongoPersistenceIterator<FreezeConfigEntity, SpringFilterExpander> iterator;
  private static final int BATCH_SIZE_MULTIPLY_FACTOR = 2; // The factor by how much the batchSize should be increased
  private static final int REDIS_LOCK_TIMEOUT_SECONDS = 5;

  public void registerIterators(int threadPoolSize) {
    int redisBatchSize = BATCH_SIZE_MULTIPLY_FACTOR * threadPoolSize;

    PersistenceIteratorFactory.RedisBatchExecutorOptions executorOptions =
        PersistenceIteratorFactory.RedisBatchExecutorOptions.builder()
            .name("NgDeploymentFreezeActivation")
            .poolSize(threadPoolSize)
            .batchSize(redisBatchSize)
            .lockTimeout(REDIS_LOCK_TIMEOUT_SECONDS)
            .interval(ofSeconds(45))
            .build();

    iterator = (MongoPersistenceIterator<FreezeConfigEntity, SpringFilterExpander>)
                   persistenceIteratorFactory.createRedisBatchIteratorWithDedicatedThreadPool(executorOptions,
                       NgDeploymentFreezeActivationHandler.class,
                       MongoPersistenceIterator.<FreezeConfigEntity, SpringFilterExpander>builder()
                           .mode(PersistenceIterator.ProcessMode.REDIS_BATCH)
                           .clazz(FreezeConfigEntity.class)
                           .fieldName(FreezeConfigEntityKeys.nextIteration)
                           .acceptableNoAlertDelay(ofSeconds(60))
                           .targetInterval(ofDays(1))
                           .semaphore(new Semaphore(10))
                           .handler(this)
                           .persistenceProvider(new SpringPersistenceRequiredProvider<>(mongoTemplate))
                           .schedulingType(IRREGULAR_SKIP_MISSED)
                           .filterExpander(q
                               -> q.addCriteria(where(FreezeConfigEntityKeys.status).is(FreezeStatus.ENABLED))
                                      .addCriteria(where(FreezeConfigEntityKeys.nextIteration).ne(null))
                                      .addCriteria(where(FreezeConfigEntityKeys.shouldSendNotification).is(true))));
  }

  public void wakeup() {
    if (iterator != null) {
      iterator.wakeup();
    }
  }

  @Override
  public void handle(FreezeConfigEntity entity) {
    try {
      FreezeConfig freezeConfig = NGFreezeDtoMapper.toFreezeConfig(entity.getYaml());
      FreezeInfoConfig freezeInfoConfig = freezeConfig.getFreezeInfoConfig();
      List<FreezeWindow> windows = freezeInfoConfig.getWindows();
      CurrentOrUpcomingWindow currentOrUpcomingWindow = FreezeTimeUtils.fetchCurrentOrUpcomingTimeWindow(windows);
      long currentTime = new Date().getTime();

      if (isNull(currentOrUpcomingWindow)) {
        entity.setShouldSendNotification(false);
        entity.setNextIteration(null);
        freezeCRUDService.updateExistingFreezeConfigEntity(entity);
        return;
      } else {
        boolean freezeWindowActive = (currentTime >= currentOrUpcomingWindow.getStartTime())
            && (currentTime <= (currentOrUpcomingWindow.getEndTime()));
        if (!freezeWindowActive) {
          return;
        }
      }
      String baseUrl = ngExpressionHelper.getBaseUrl(entity.getAccountId());
      notificationHelper.sendNotification(entity.getYaml(), false, true, null, entity.getAccountId(), null, baseUrl,
          entity.getType() == FreezeType.GLOBAL);
    } catch (Exception e) {
      log.error(
          String.format("Unable to send notifications for freeze with identifier - %s", entity.getIdentifier()), e);
    }
  }
}
