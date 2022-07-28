/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.MigrationAsyncTracker;
import io.harness.beans.MigrationAsyncTracker.MigrationAsyncTrackerKeys;
import io.harness.beans.MigrationAsyncTrackerStatus;
import io.harness.ngmigration.beans.summary.BaseSummary;
import io.harness.ngmigration.beans.summary.DiscoverySummaryReqDTO;
import io.harness.ngmigration.beans.summary.DiscoverySummaryResult;
import io.harness.persistence.HPersistence;

import software.wings.ngmigration.NGMigrationEntityType;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.inject.Inject;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.ws.rs.InternalServerErrorException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;

@Slf4j
@OwnedBy(HarnessTeam.CDC)
public class AsyncDiscoveryHandler {
  private static final ExecutorService service = Executors.newFixedThreadPool(8);

  private static final Cache<String, String> cache =
      CacheBuilder.newBuilder().expireAfterWrite(10, TimeUnit.MINUTES).build();

  @Inject DiscoveryService discoveryService;
  @Inject private HPersistence hPersistence;

  public synchronized String queueAccountSummary(String accountId) {
    String reqId = cache.getIfPresent(accountId);
    try {
      if (StringUtils.isNotBlank(reqId)) {
        return reqId;
      }
      reqId = hPersistence.insert(MigrationAsyncTracker.builder()
                                      .accountId(accountId)
                                      .status(MigrationAsyncTrackerStatus.PROCESSING)
                                      .requestPayload(DiscoverySummaryReqDTO.builder().accountId(accountId).build())
                                      .build());
      cache.put(accountId, reqId);

      final String trackerId = reqId;
      service.submit(() -> handleDiscoverySummary(accountId, trackerId));
    } catch (Exception e) {
      log.error("There was an error queuing the discovery summary", e);
      if (StringUtils.isNotBlank(reqId)) {
        hPersistence.delete(MigrationAsyncTracker.class, reqId);
        cache.invalidate(reqId);
      }
      throw new InternalServerErrorException("Could not queue the discovery. Please try after sometime", e);
    }
    return reqId;
  }

  public MigrationAsyncTracker getAccountSummary(String accountId, String reqId) {
    return hPersistence.createQuery(MigrationAsyncTracker.class)
        .filter(MigrationAsyncTracker.UUID_KEY, reqId)
        .filter(MigrationAsyncTracker.ACCOUNT_ID_KEY, accountId)
        .get();
  }

  private void handleDiscoverySummary(String accountId, String reqId) {
    Query<MigrationAsyncTracker> query = hPersistence.createQuery(MigrationAsyncTracker.class)
                                             .filter(MigrationAsyncTracker.UUID_KEY, reqId)
                                             .filter(MigrationAsyncTracker.ACCOUNT_ID_KEY, accountId);
    try {
      Map<NGMigrationEntityType, BaseSummary> summary =
          discoveryService.getSummary(accountId, null, accountId, NGMigrationEntityType.ACCOUNT);
      UpdateOperations<MigrationAsyncTracker> updateOperations =
          hPersistence.createUpdateOperations(MigrationAsyncTracker.class)
              .set(MigrationAsyncTrackerKeys.responsePayload, DiscoverySummaryResult.builder().summary(summary).build())
              .set(MigrationAsyncTrackerKeys.status, MigrationAsyncTrackerStatus.DONE);
      hPersistence.update(query, updateOperations);
    } catch (Exception e) {
      log.error("There was an error processing the discovery", e);
      UpdateOperations<MigrationAsyncTracker> updateOperations =
          hPersistence.createUpdateOperations(MigrationAsyncTracker.class)
              .set(MigrationAsyncTrackerKeys.status, MigrationAsyncTrackerStatus.ERROR);
      hPersistence.update(query, updateOperations);
    } finally {
      cache.invalidate(accountId);
    }
  }
}
