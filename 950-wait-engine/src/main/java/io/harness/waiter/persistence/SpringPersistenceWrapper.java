/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.waiter.persistence;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;
import static io.harness.waiter.WaitInstanceService.MAX_CALLBACK_PROCESSING_TIME;
import static io.harness.waiter.WaitNotifyEngine.MIN_WAIT_INSTANCE_TIMEOUT;

import static java.util.stream.Collectors.toList;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.GeneralException;
import io.harness.exception.InvalidArgumentsException;
import io.harness.serializer.KryoSerializer;
import io.harness.springdata.SpringDataMongoUtils;
import io.harness.tasks.ResponseData;
import io.harness.timeout.TimeoutEngine;
import io.harness.timeout.TimeoutInstance;
import io.harness.waiter.NotifyResponse;
import io.harness.waiter.NotifyResponse.NotifyResponseKeys;
import io.harness.waiter.ProcessedMessageResponse;
import io.harness.waiter.ProgressUpdate;
import io.harness.waiter.ProgressUpdate.ProgressUpdateKeys;
import io.harness.waiter.WaitEngineEntity;
import io.harness.waiter.WaitInstance;
import io.harness.waiter.WaitInstance.WaitInstanceKeys;
import io.harness.waiter.WaitInstanceTimeoutCallback;

import com.google.common.base.Stopwatch;
import com.google.inject.Inject;
import com.mongodb.client.result.DeleteResult;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class SpringPersistenceWrapper implements PersistenceWrapper {
  @Inject private MongoTemplate mongoTemplate;
  @Inject private KryoSerializer kryoSerializer;
  @Inject private TimeoutEngine timeoutEngine;
  @Inject private TransactionTemplate transactionTemplate;

  private FindAndModifyOptions findAndModifyOptions = new FindAndModifyOptions().returnNew(false).upsert(false);

  @Override
  public void deleteWaitInstance(WaitInstance waitInstance) {
    Failsafe.with(DEFAULT_RETRY_POLICY).get(() -> transactionTemplate.execute(transactionStatus -> {
      if (isNotEmpty(waitInstance.getTimeoutInstanceId())) {
        timeoutEngine.deleteTimeout(waitInstance.getTimeoutInstanceId());
      }
      delete(waitInstance);
      return null;
    }));
  }

  @Override
  public void delete(WaitEngineEntity entity) {
    DeleteResult remove = mongoTemplate.remove(entity);
    if (!remove.wasAcknowledged() || remove.getDeletedCount() != 1) {
      throw new GeneralException("Not able to Delete wait instance");
    }
  }

  @Override
  public List<String> fetchNotifyResponseKeys(long limit) {
    Query query = query(where(NotifyResponseKeys.createdAt).lt(limit));
    List<NotifyResponse> notifyResponses = mongoTemplate.find(query, NotifyResponse.class);
    return notifyResponses.stream().map(NotifyResponse::getUuid).collect(Collectors.toList());
  }

  @Override
  public List<WaitInstance> fetchWaitInstances(String correlationId) {
    Query query = query(where(WaitInstanceKeys.correlationIds).is(correlationId));
    return mongoTemplate.find(query, WaitInstance.class);
  }

  @Override
  public ProcessedMessageResponse processMessage(WaitInstance waitInstance) {
    boolean isError = false;
    Map<String, ResponseData> responseMap = new HashMap<>();

    Query query = query(where(NotifyResponseKeys.uuid).in(waitInstance.getCorrelationIds()));
    if (waitInstance.getProgressCallback() != null) {
      query.with(Sort.by(Direction.ASC, NotifyResponseKeys.createdAt));
    }

    List<NotifyResponse> notifyResponses = mongoTemplate.find(query, NotifyResponse.class);
    for (NotifyResponse notifyResponse : notifyResponses) {
      if (notifyResponse.isError()) {
        log.info("Failed notification response {}", notifyResponse.getUuid());
        isError = true;
      }
      if (notifyResponse.getResponseData() != null) {
        responseMap.put(
            notifyResponse.getUuid(), (ResponseData) kryoSerializer.asInflatedObject(notifyResponse.getResponseData()));
      }
    }
    return ProcessedMessageResponse.builder().isError(isError).responseDataMap(responseMap).build();
  }

  @Override
  public String save(WaitEngineEntity entity) {
    WaitEngineEntity savedEntity = mongoTemplate.insert(entity);
    return savedEntity.getUuid();
  }

  @Override
  public WaitInstance modifyAndFetchWaitInstance(String waitingOnCorrelationId) {
    Query query = query(where(WaitInstanceKeys.waitingOnCorrelationIds).in(waitingOnCorrelationId));
    Update update = new Update().pull(WaitInstanceKeys.waitingOnCorrelationIds, waitingOnCorrelationId);
    return mongoTemplate.findAndModify(query, update, SpringDataMongoUtils.returnNewOptions, WaitInstance.class);
  }

  @Override
  public WaitInstance fetchForProcessingWaitInstance(String waitInstanceId, long now) {
    final Query query = query(where(WaitInstanceKeys.uuid).is(waitInstanceId))
                            .addCriteria(where(WaitInstanceKeys.callbackProcessingAt).lt(now));
    final Update update =
        new Update().set(WaitInstanceKeys.callbackProcessingAt, now + MAX_CALLBACK_PROCESSING_TIME.toMillis());
    final Stopwatch stopwatch = Stopwatch.createStarted();
    long doneWithStartTime = stopwatch.elapsed(TimeUnit.MILLISECONDS);
    WaitInstance waitInstance = mongoTemplate.findAndModify(query, update, findAndModifyOptions, WaitInstance.class);
    long queryEndTime = stopwatch.elapsed(TimeUnit.MILLISECONDS);

    if (log.isDebugEnabled()) {
      log.debug("Process waitInstance mongo queryTime {}", queryEndTime - doneWithStartTime);
    }
    return waitInstance;
  }

  @Override
  public ProgressUpdate fetchForProcessingProgressUpdate(Set<String> busyCorrelationIds, long now) {
    Query query;
    if (busyCorrelationIds.isEmpty()) {
      query = new Query().with(Sort.by(ProgressUpdateKeys.createdAt));
    } else {
      query = query(where(ProgressUpdateKeys.correlationId).not().in(busyCorrelationIds))
                  .with(Sort.by(ProgressUpdateKeys.createdAt));
    }
    Update update =
        new Update().set(ProgressUpdateKeys.expireProcessing, now + MAX_CALLBACK_PROCESSING_TIME.toMillis());
    return mongoTemplate.findAndModify(query, update, findAndModifyOptions, ProgressUpdate.class);
  }

  @Override
  public WaitInstance modifyAndFetchWaitInstanceForExistingResponse(
      String waitInstanceId, List<String> correlationIds) {
    // We cannot combine the logic of obtaining the responses before the save, because this will create a race with
    // storing the responses.
    Query query = query(where(NotifyResponseKeys.uuid).in(correlationIds));
    List<NotifyResponse> notifyResponses = mongoTemplate.find(query, NotifyResponse.class);
    final List<String> keys = notifyResponses.stream().map(NotifyResponse::getUuid).collect(toList());
    if (isEmpty(keys)) {
      return null;
    }

    final Query wiQuery = query(where(WaitInstanceKeys.uuid).is(waitInstanceId));
    final Update wiUpdate = new Update().pullAll(WaitInstanceKeys.waitingOnCorrelationIds, keys.toArray(new String[0]));
    return mongoTemplate.findAndModify(wiQuery, wiUpdate, SpringDataMongoUtils.returnNewOptions, WaitInstance.class);
  }

  @Override
  public void deleteNotifyResponses(List<String> responseIds) {
    if (isEmpty(responseIds)) {
      return;
    }
    log.info("Deleting {} not needed responses", responseIds.size());
    DeleteResult deleteResult =
        mongoTemplate.remove(query(where(NotifyResponseKeys.uuid).in(responseIds)), NotifyResponse.class);
    if (!deleteResult.wasAcknowledged()) {
      log.warn("Not Able to delete Notify Responses");
    }
  }

  @Override
  public String saveWithTimeout(WaitInstance waitInstance, Duration timeout) {
    if (timeout != null && !timeout.isZero() && timeout.compareTo(Duration.ofSeconds(MIN_WAIT_INSTANCE_TIMEOUT)) < 0) {
      throw new InvalidArgumentsException("Timeout should be greater than " + MIN_WAIT_INSTANCE_TIMEOUT + "sec");
    }
    return Failsafe.with(DEFAULT_RETRY_POLICY).get(() -> transactionTemplate.execute(transactionStatus -> {
      if (timeout != null && !timeout.isZero()) {
        TimeoutInstance timeoutInstance = timeoutEngine.registerAbsoluteTimeout(
            timeout, WaitInstanceTimeoutCallback.builder().waitInstanceId(waitInstance.getUuid()).build());
        waitInstance.withTimeoutInstanceId(timeoutInstance.getUuid());
      }
      return save(waitInstance);
    }));
  }
}
