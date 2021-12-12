package io.harness.service.impl;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.persistence.HQuery.excludeAuthority;

import static java.lang.System.currentTimeMillis;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateAsyncTaskResponse;
import io.harness.delegate.beans.DelegateAsyncTaskResponse.DelegateAsyncTaskResponseKeys;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.persistence.HPersistence;
import io.harness.queue.QueueController;
import io.harness.serializer.KryoSerializer;
import io.harness.service.intfc.DelegateAsyncService;
import io.harness.tasks.BinaryResponseData;
import io.harness.tasks.ResponseData;
import io.harness.waiter.WaitNotifyEngine;

import com.google.common.base.Stopwatch;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.DEL)
public class DelegateAsyncServiceImpl implements DelegateAsyncService {
  @Inject private HPersistence persistence;
  @Inject private KryoSerializer kryoSerializer;
  @Inject private WaitNotifyEngine waitNotifyEngine;
  @Inject @Named("disableDeserialization") private boolean disableDeserialization;
  @Inject @Named("enablePrimaryCheck") private boolean enablePrimaryCheck;
  private static final int DELETE_THRESHOLD = 20;
  private static final long MAX_PROCESSING_DURATION_MILLIS = 60000L;
  @Inject private QueueController queueController;

  @Override
  public void run() {
    // TODO - method guaranties at least one delivery
    Set<String> responsesToBeDeleted = new HashSet<>();

    boolean consumeResponse = true;
    if (enablePrimaryCheck && queueController != null) {
      consumeResponse = queueController.isPrimary();
    }
    final Stopwatch globalStopwatch = Stopwatch.createStarted();
    long loopStartTime = 0;
    while (consumeResponse) {
      try {
        final Stopwatch stopwatch = Stopwatch.createStarted();
        Query<DelegateAsyncTaskResponse> taskResponseQuery =
            persistence.createQuery(DelegateAsyncTaskResponse.class, excludeAuthority)
                .field(DelegateAsyncTaskResponseKeys.processAfter)
                .lessThan(currentTimeMillis() - MAX_PROCESSING_DURATION_MILLIS);

        UpdateOperations<DelegateAsyncTaskResponse> updateOperations =
            persistence.createUpdateOperations(DelegateAsyncTaskResponse.class)
                .set(DelegateAsyncTaskResponseKeys.processAfter, currentTimeMillis());

        long queryStartTime = stopwatch.elapsed(TimeUnit.MILLISECONDS);
        DelegateAsyncTaskResponse lockedAsyncTaskResponse =
            persistence.findAndModify(taskResponseQuery, updateOperations, HPersistence.returnNewOptions);
        long queryEndTime = stopwatch.elapsed(TimeUnit.MILLISECONDS);

        if (lockedAsyncTaskResponse == null) {
          break;
        }

        long queryTime = queryEndTime - queryStartTime;
        long loopProcessingTime =
            Math.max((globalStopwatch.elapsed(TimeUnit.MILLISECONDS) - loopStartTime) - queryTime, 0l);

        log.info("Process won the async task response {}, mongo queryTime {}, loop processing time {} .",
            lockedAsyncTaskResponse.getUuid(), queryTime, loopProcessingTime);

        loopStartTime = globalStopwatch.elapsed(TimeUnit.MILLISECONDS);
        ResponseData responseData = disableDeserialization
            ? BinaryResponseData.builder().data(lockedAsyncTaskResponse.getResponseData()).build()
            : (DelegateResponseData) kryoSerializer.asInflatedObject(lockedAsyncTaskResponse.getResponseData());
        waitNotifyEngine.doneWith(lockedAsyncTaskResponse.getUuid(), responseData);

        if (lockedAsyncTaskResponse.getHoldUntil() == null
            || lockedAsyncTaskResponse.getHoldUntil() < currentTimeMillis()) {
          responsesToBeDeleted.add(lockedAsyncTaskResponse.getUuid());
          if (responsesToBeDeleted.size() >= DELETE_THRESHOLD) {
            deleteProcessedResponses(responsesToBeDeleted);
            responsesToBeDeleted.clear();
          }
        } else {
          Query<DelegateAsyncTaskResponse> uuidTaskResponseQuery =
              persistence.createQuery(DelegateAsyncTaskResponse.class, excludeAuthority)
                  .filter(DelegateAsyncTaskResponseKeys.uuid, lockedAsyncTaskResponse.getUuid());

          UpdateOperations<DelegateAsyncTaskResponse> uuidUpdateOperations =
              persistence.createUpdateOperations(DelegateAsyncTaskResponse.class)
                  .set(DelegateAsyncTaskResponseKeys.processAfter, lockedAsyncTaskResponse.getHoldUntil());

          persistence.findAndModify(uuidTaskResponseQuery, uuidUpdateOperations, HPersistence.returnNewOptions);
        }
      } catch (Exception ex) {
        log.warn(String.format("Ignoring async task response because of the following error: %s", ex.getMessage()), ex);
      }
    }

    deleteProcessedResponses(responsesToBeDeleted);
  }

  private boolean deleteProcessedResponses(Set<String> responsesToBeDeleted) {
    if (isEmpty(responsesToBeDeleted)) {
      return true;
    }

    boolean deleteSuccessful =
        persistence.deleteOnServer(persistence.createQuery(DelegateAsyncTaskResponse.class, excludeAuthority)
                                       .field(DelegateAsyncTaskResponseKeys.uuid)
                                       .in(responsesToBeDeleted));

    if (deleteSuccessful) {
      log.info("Deleted process responses for task list {}", responsesToBeDeleted);
      responsesToBeDeleted.clear();
    }

    return deleteSuccessful;
  }

  @Getter(lazy = true)
  private final byte[] timeoutMessage = kryoSerializer.asDeflatedBytes(
      ErrorNotifyResponseData.builder()
          .errorMessage("Delegate service did not provide response and the task time-outed")
          .build());

  @Override
  public void setupTimeoutForTask(String taskId, long expiry, long holdUntil) {
    Instant validUntilInstant = Instant.ofEpochMilli(expiry).plusSeconds(Duration.ofHours(1).getSeconds());
    UpdateOperations<DelegateAsyncTaskResponse> updateOperations =
        persistence.createUpdateOperations(DelegateAsyncTaskResponse.class)
            .setOnInsert(DelegateAsyncTaskResponseKeys.responseData, getTimeoutMessage())
            .setOnInsert(DelegateAsyncTaskResponseKeys.processAfter, expiry)
            .setOnInsert(DelegateAsyncTaskResponseKeys.validUntil, Date.from(validUntilInstant))
            .set(DelegateAsyncTaskResponseKeys.holdUntil, holdUntil);

    Query<DelegateAsyncTaskResponse> upsertQuery =
        persistence.createQuery(DelegateAsyncTaskResponse.class, excludeAuthority)
            .filter(DelegateAsyncTaskResponseKeys.uuid, taskId);
    persistence.upsert(upsertQuery, updateOperations);
  }
}
