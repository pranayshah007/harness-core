/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.waiter.persistence;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.persistence.HQuery.excludeAuthority;
import static io.harness.waiter.WaitInstanceService.MAX_CALLBACK_PROCESSING_TIME;

import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.GeneralException;
import io.harness.persistence.HIterator;
import io.harness.persistence.HKeyIterator;
import io.harness.persistence.HPersistence;
import io.harness.serializer.KryoSerializer;
import io.harness.tasks.ResponseData;
import io.harness.waiter.NotifyResponse;
import io.harness.waiter.NotifyResponse.NotifyResponseKeys;
import io.harness.waiter.ProcessedMessageResponse;
import io.harness.waiter.ProgressUpdate;
import io.harness.waiter.ProgressUpdate.ProgressUpdateKeys;
import io.harness.waiter.WaitEngineEntity;
import io.harness.waiter.WaitInstance;
import io.harness.waiter.WaitInstance.WaitInstanceKeys;

import com.google.inject.Inject;
import com.mongodb.WriteConcern;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.FindAndModifyOptions;
import org.mongodb.morphia.Key;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.Sort;
import org.mongodb.morphia.query.UpdateOperations;

@Slf4j
@OwnedBy(HarnessTeam.DEL)
public class MorphiaPersistenceWrapper implements PersistenceWrapper {
  @Inject private HPersistence hPersistence;
  @Inject private KryoSerializer kryoSerializer;

  private FindAndModifyOptions findAndModifyOptions =
      new FindAndModifyOptions().writeConcern(WriteConcern.MAJORITY).upsert(false).returnNew(false);

  @Override
  public String save(WaitEngineEntity entity) {
    return hPersistence.save(entity);
  }

  @Override
  public void delete(WaitEngineEntity entity) {
    boolean deleted = hPersistence.delete(entity);
    if (!deleted) {
      throw new GeneralException("Not able to Delete wait instance");
    }
  }

  @Override
  public void deleteWaitInstance(WaitInstance entity) {
    delete(entity);
  }

  @Override
  public ProcessedMessageResponse processMessage(WaitInstance waitInstance) {
    boolean isError = false;
    Map<String, ResponseData> responseMap = new HashMap<>();

    Query<NotifyResponse> query = hPersistence.createQuery(NotifyResponse.class, excludeAuthority)
                                      .field(NotifyResponseKeys.uuid)
                                      .in(waitInstance.getCorrelationIds());

    if (waitInstance.getProgressCallback() != null) {
      query.order(Sort.ascending(NotifyResponseKeys.createdAt));
    }

    try (HIterator<NotifyResponse> notifyResponses = new HIterator(query.fetch())) {
      for (NotifyResponse notifyResponse : notifyResponses) {
        if (notifyResponse.isError()) {
          log.info("Failed notification response {}", notifyResponse.getUuid());
          isError = true;
        }
        if (notifyResponse.getResponseData() != null) {
          responseMap.put(notifyResponse.getUuid(),
              (ResponseData) kryoSerializer.asInflatedObject(notifyResponse.getResponseData()));
        }
      }
    }

    return ProcessedMessageResponse.builder().isError(isError).responseDataMap(responseMap).build();
  }

  @Override
  public List<WaitInstance> fetchWaitInstances(String correlationId) {
    List<WaitInstance> waitInstances = new ArrayList<>();
    Query<WaitInstance> query = hPersistence.createQuery(WaitInstance.class, excludeAuthority)
                                    .filter(WaitInstanceKeys.correlationIds, correlationId);
    try (HIterator<WaitInstance> iterator = new HIterator<>(query.fetch())) {
      for (WaitInstance waitInstance : iterator) {
        waitInstances.add(waitInstance);
      }
    }
    return waitInstances;
  }

  @Override
  public List<String> fetchNotifyResponseKeys(long limit) {
    List<String> keys = new ArrayList<>();
    try (HKeyIterator<NotifyResponse> iterator =
             new HKeyIterator<>(hPersistence.createQuery(NotifyResponse.class, excludeAuthority)
                                    .field(NotifyResponseKeys.createdAt)
                                    .lessThan(limit)
                                    .fetchKeys())) {
      for (Key<NotifyResponse> key : iterator) {
        keys.add(key.getId().toString());
      }
    }
    return keys;
  }

  @Override
  public ProgressUpdate fetchForProcessingProgressUpdate(Set<String> busyCorrelationIds, long now) {
    Query<ProgressUpdate> query;
    if (busyCorrelationIds.isEmpty()) {
      query = hPersistence.createQuery(ProgressUpdate.class, excludeAuthority).order(ProgressUpdateKeys.createdAt);
    } else {
      query = hPersistence.createQuery(ProgressUpdate.class, excludeAuthority)
                  .field(ProgressUpdateKeys.correlationId)
                  .notIn(busyCorrelationIds)
                  .order(ProgressUpdateKeys.createdAt);
    }

    UpdateOperations<ProgressUpdate> updateOperations =
        hPersistence.createUpdateOperations(ProgressUpdate.class)
            .set(ProgressUpdateKeys.expireProcessing, now + MAX_CALLBACK_PROCESSING_TIME.toMillis());

    return hPersistence.findAndModify(query, updateOperations, findAndModifyOptions);
  }

  @Override
  public WaitInstance fetchForProcessingWaitInstance(String waitInstanceId, long now) {
    final Query<WaitInstance> waitInstanceQuery = hPersistence.createQuery(WaitInstance.class)
                                                      .filter(WaitInstanceKeys.uuid, waitInstanceId)
                                                      .field(WaitInstanceKeys.callbackProcessingAt)
                                                      .lessThan(now);

    final UpdateOperations<WaitInstance> updateOperations =
        hPersistence.createUpdateOperations(WaitInstance.class)
            .set(WaitInstanceKeys.callbackProcessingAt, now + MAX_CALLBACK_PROCESSING_TIME.toMillis());

    return hPersistence.findAndModify(waitInstanceQuery, updateOperations, findAndModifyOptions);
  }

  @Override
  public WaitInstance modifyAndFetchWaitInstanceForExistingResponse(
      String waitInstanceId, List<String> correlationIds) {
    // We cannot combine the logic of obtaining the responses before the save, because this will create a race with
    // storing the responses.
    final List<String> keys = hPersistence.createQuery(NotifyResponse.class, excludeAuthority)
                                  .field(NotifyResponseKeys.uuid)
                                  .in(correlationIds)
                                  .asKeyList()
                                  .stream()
                                  .map(key -> (String) key.getId())
                                  .collect(toList());
    if (isEmpty(keys)) {
      return null;
    }

    final Query<WaitInstance> query =
        hPersistence.createQuery(WaitInstance.class, excludeAuthority).filter(WaitInstanceKeys.uuid, waitInstanceId);

    final UpdateOperations<WaitInstance> operations = hPersistence.createUpdateOperations(WaitInstance.class)
                                                          .removeAll(WaitInstanceKeys.waitingOnCorrelationIds, keys);
    return hPersistence.findAndModify(query, operations, HPersistence.returnNewOptions);
  }

  @Override
  public WaitInstance modifyAndFetchWaitInstance(String waitingOnCorrelationId) {
    final Query<WaitInstance> query = hPersistence.createQuery(WaitInstance.class, excludeAuthority)
                                          .filter(WaitInstanceKeys.waitingOnCorrelationIds, waitingOnCorrelationId);

    final UpdateOperations<WaitInstance> operations =
        hPersistence.createUpdateOperations(WaitInstance.class)
            .removeAll(WaitInstanceKeys.waitingOnCorrelationIds, waitingOnCorrelationId);
    return hPersistence.findAndModify(query, operations, HPersistence.returnNewOptions);
  }

  @Override
  public String saveWithTimeout(WaitInstance waitInstance, Duration timeout) {
    if (!timeout.isZero()) {
      log.warn("Timeout Not supported for MORPHIA persistence layer. This argument will have no effect");
    }
    return save(waitInstance);
  }

  @Override
  public void deleteNotifyResponses(List<String> responseIds) {
    if (isEmpty(responseIds)) {
      return;
    }
    log.info("Deleting {} not needed responses", responseIds.size());
    boolean deleted = hPersistence.deleteOnServer(hPersistence.createQuery(NotifyResponse.class, excludeAuthority)
                                                      .field(NotifyResponseKeys.uuid)
                                                      .in(responseIds));
    if (!deleted) {
      log.warn("Not Able to delete Notify Responses");
    }
  }
}
