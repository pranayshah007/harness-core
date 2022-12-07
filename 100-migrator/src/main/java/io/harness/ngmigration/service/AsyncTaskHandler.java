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
import io.harness.beans.MigrationTrackRespPayload;
import io.harness.ngmigration.beans.summary.DiscoverySummaryReqDTO;
import io.harness.persistence.HPersistence;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
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
public abstract class AsyncTaskHandler {
  private static final ExecutorService service = Executors.newFixedThreadPool(8);

  private static final Cache<String, String> cache =
      CacheBuilder.newBuilder().expireAfterWrite(10, TimeUnit.MINUTES).build();

  abstract String getTaskType();

  abstract MigrationTrackRespPayload processTask(String accountId, String requestId);

  abstract HPersistence getHPersistence();

  public synchronized String queue(String accountId) {
    HPersistence hPersistence = getHPersistence();
    String reqId = cache.getIfPresent(accountId);
    try {
      if (StringUtils.isNotBlank(reqId)) {
        return reqId;
      }
      reqId = hPersistence.insert(MigrationAsyncTracker.builder()
                                      .accountId(accountId)
                                      .requestType(getTaskType())
                                      .status(MigrationAsyncTrackerStatus.PROCESSING)
                                      .requestPayload(DiscoverySummaryReqDTO.builder().accountId(accountId).build())
                                      .build());
      cache.put(accountId, reqId);

      final String trackerId = reqId;
      service.submit(() -> process(accountId, trackerId));
    } catch (Exception e) {
      log.error(String.format("There was an error queuing the %s", getTaskType()), e);
      if (StringUtils.isNotBlank(reqId)) {
        hPersistence.delete(MigrationAsyncTracker.class, reqId);
        cache.invalidate(reqId);
      }
      throw new InternalServerErrorException(
          String.format("Could not queue the %s. Please try after sometime", getTaskType()), e);
    }
    return reqId;
  }

  public MigrationAsyncTracker getTaskResult(String accountId, String reqId) {
    return getHPersistence()
        .createQuery(MigrationAsyncTracker.class)
        .filter(MigrationAsyncTracker.UUID_KEY, reqId)
        .filter(MigrationAsyncTracker.ACCOUNT_ID_KEY, accountId)
        .filter(MigrationAsyncTrackerKeys.requestType, getTaskType())
        .get();
  }

  void process(String accountId, String reqId) {
    try {
      MigrationTrackRespPayload respPayload = processTask(accountId, reqId);
      onComplete(accountId, reqId, respPayload);
    } catch (Exception e) {
      onError(accountId, reqId, e);
    }
  }

  void onError(String accountId, String reqId, Exception e) {
    HPersistence hPersistence = getHPersistence();
    Query<MigrationAsyncTracker> query = hPersistence.createQuery(MigrationAsyncTracker.class)
                                             .filter(MigrationAsyncTracker.UUID_KEY, reqId)
                                             .filter(MigrationAsyncTracker.ACCOUNT_ID_KEY, accountId);
    log.error("There was an error processing the similar workflows", e);
    UpdateOperations<MigrationAsyncTracker> updateOperations =
        hPersistence.createUpdateOperations(MigrationAsyncTracker.class)
            .set(MigrationAsyncTrackerKeys.status, MigrationAsyncTrackerStatus.ERROR);
    hPersistence.update(query, updateOperations);
    cache.invalidate(accountId);
  }

  void onComplete(String accountId, String reqId, MigrationTrackRespPayload result) {
    HPersistence hPersistence = getHPersistence();
    Query<MigrationAsyncTracker> query = hPersistence.createQuery(MigrationAsyncTracker.class)
                                             .filter(MigrationAsyncTracker.UUID_KEY, reqId)
                                             .filter(MigrationAsyncTracker.ACCOUNT_ID_KEY, accountId);
    UpdateOperations<MigrationAsyncTracker> updateOperations =
        hPersistence.createUpdateOperations(MigrationAsyncTracker.class)
            .set(MigrationAsyncTrackerKeys.responsePayload, result)
            .set(MigrationAsyncTrackerKeys.status, MigrationAsyncTrackerStatus.DONE);
    hPersistence.update(query, updateOperations);
    cache.invalidate(accountId);
  }
}
