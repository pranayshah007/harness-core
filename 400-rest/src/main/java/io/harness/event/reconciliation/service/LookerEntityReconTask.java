/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.event.reconciliation.service;

import static io.harness.mongo.MongoConfig.NO_LIMIT;

import io.harness.beans.FeatureName;
import io.harness.event.reconciliation.ReconciliationStatus;
import io.harness.ff.FeatureFlagService;
import io.harness.persistence.HIterator;

import software.wings.beans.Account;
import software.wings.search.framework.TimeScaleEntity;
import software.wings.service.intfc.AccountService;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import dev.morphia.query.Query;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LookerEntityReconTask implements Runnable {
  @Inject AccountService accountService;
  @Inject private Set<TimeScaleEntity<?>> timeScaleEntities;
  @Inject private FeatureFlagService featureFlagService;

  /**
   * Fixed size threadPool to have max 5 threads only
   */
  @Inject @Named("LookerEntityReconTaskExecutor") ExecutorService executorService;
  @Override
  public void run() {
    try {
      long startTime = System.currentTimeMillis();
      Set<String> accountIds = featureFlagService.getAccountIds(FeatureName.TIME_SCALE_CG_SYNC);
      Query<Account> query = accountService.getBasicAccountQuery().limit(NO_LIMIT);
      try (HIterator<Account> iterator = new HIterator<>(query.fetch())) {
        for (Account account : iterator) {
          if (accountIds.contains(account.getUuid())) {
            for (TimeScaleEntity timeScaleEntity : timeScaleEntities) {
              executorService.submit(() -> {
                final long durationStartTs = startTime - 45 * 60 * 1000;
                final long durationEndTs = startTime - 5 * 60 * 1000;
                try {
                  LookerEntityReconService lookerEntityReconService = timeScaleEntity.getReconService();
                  ReconciliationStatus reconciliationStatus = lookerEntityReconService.performReconciliation(
                      account.getUuid(), durationStartTs, durationEndTs, timeScaleEntity);
                  log.info(
                      "Completed reconciliation for accountID:[{}],accountName:[{}] durationStart:[{}],durationEnd:[{}],status:[{}],entity[{}]",
                      account.getUuid(), account.getAccountName(), new Date(durationStartTs), new Date(durationEndTs),
                      reconciliationStatus, timeScaleEntity.getSourceEntityClass());
                } catch (Exception e) {
                  log.error(
                      "Error while performing reconciliation for accountID:[{}],accountName:[{}] durationStart:[{}],durationEnd:[{}],entity[{}]",
                      account.getUuid(), account.getAccountName(), new Date(durationStartTs), new Date(durationEndTs),
                      timeScaleEntity.getSourceEntityClass(), e);
                }
              });
            }
          }
        }
      }
    } catch (Exception e) {
      log.error("Failed to run reconciliation", e);
    }
  }
}
