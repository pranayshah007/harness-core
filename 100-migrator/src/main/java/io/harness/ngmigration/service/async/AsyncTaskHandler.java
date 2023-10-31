/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.async;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.MigrationAsyncTracker;
import io.harness.beans.MigrationAsyncTracker.MigrationAsyncTrackerKeys;
import io.harness.beans.MigrationAsyncTrackerStatus;
import io.harness.beans.MigrationTrackReqPayload;
import io.harness.beans.MigrationTrackRespPayload;
import io.harness.eraro.ErrorCode;
import io.harness.logging.AutoLogContext;
import io.harness.ng.core.Status;
import io.harness.ngmigration.dto.ErrorDTO;
import io.harness.persistence.HPersistence;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableMap;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.ws.rs.InternalServerErrorException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_MIGRATOR})
@Slf4j
@OwnedBy(HarnessTeam.CDC)
public abstract class AsyncTaskHandler {
  private static final String ACCOUNT_IDENTIFIER = "accountIdentifier";
  private static final String REQUEST_ID = "requestId";
  private static final String TASK_TYPE = "taskType";

  private static final ExecutorService service = Executors.newFixedThreadPool(8);

  private static final Cache<String, String> cache =
      CacheBuilder.newBuilder().expireAfterWrite(10, TimeUnit.MINUTES).build();

  abstract String getTaskType();

  abstract MigrationTrackRespPayload processTask(
      String apiKey, String accountId, String requestId, MigrationTrackReqPayload reqPayload);

  abstract HPersistence getHPersistence();

  public synchronized String queue(String apiKey, String accountId, MigrationTrackReqPayload reqPayload) {
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
                                      .requestPayload(reqPayload)
                                      .build());
      cache.put(accountId, reqId);

      final String trackerId = reqId;
      service.submit(() -> process(apiKey, accountId, trackerId, reqPayload));
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

  void process(String apiKey, String accountId, String reqId, MigrationTrackReqPayload reqPayload) {
    try (AutoLogContext ignore1 = new AutoLogContext(
             ImmutableMap.of(ACCOUNT_IDENTIFIER, accountId, REQUEST_ID, reqId, TASK_TYPE, getTaskType()),
             OVERRIDE_ERROR)) {
      try {
        MigrationTrackRespPayload respPayload = processTask(apiKey, accountId, reqId, reqPayload);
        onComplete(accountId, reqId, respPayload);
      } catch (Exception e) {
        onError(accountId, reqId, e);
      }
    }
  }

  void onError(String accountId, String reqId, Exception e) {
    log.error("There was an error processing the task", e);
    HPersistence hPersistence = getHPersistence();
    Query<MigrationAsyncTracker> query = hPersistence.createQuery(MigrationAsyncTracker.class)
                                             .filter(MigrationAsyncTracker.UUID_KEY, reqId)
                                             .filter(MigrationAsyncTracker.ACCOUNT_ID_KEY, accountId);
    UpdateOperations<MigrationAsyncTracker> updateOperations =
        hPersistence.createUpdateOperations(MigrationAsyncTracker.class)
            .set(MigrationAsyncTrackerKeys.responsePayload,
                ErrorDTO.newError(Status.ERROR, ErrorCode.DEFAULT_ERROR_CODE, e.getMessage()))
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
