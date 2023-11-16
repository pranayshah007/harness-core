/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.aggregator.consumers;

import static io.harness.aggregator.OpType.DELETE;
import static io.harness.aggregator.OpType.UPDATE;

import io.harness.accesscontrol.AccessControlEntity;
import io.harness.aggregator.AccessControlAdminService;
import io.harness.aggregator.OpType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.DuplicateFieldException;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Singleton;
import io.debezium.embedded.EmbeddedEngineChangeEvent;
import io.debezium.engine.ChangeEvent;
import io.debezium.engine.DebeziumEngine;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.connect.source.SourceRecord;
import org.springframework.dao.DuplicateKeyException;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.PL)
public class AccessControlDebeziumChangeConsumer implements DebeziumEngine.ChangeConsumer<ChangeEvent<String, String>> {
  private static final String OP_FIELD = "__op";
  private final Deserializer<String> idDeserializer;
  private final Map<String, Deserializer<? extends AccessControlEntity>> collectionToDeserializerMap;
  private final Map<String, ChangeConsumer<? extends AccessControlEntity>> collectionToConsumerMap;
  private final Retry retry;
  private final ChangeEventFailureHandler changeEventFailureHandler;
  private final AccessControlAdminService accessControlAdminService;
  private boolean enableAclProcessingThroughOutbox;

  public AccessControlDebeziumChangeConsumer(Deserializer<String> idDeserializer,
      Map<String, Deserializer<? extends AccessControlEntity>> collectionToDeserializerMap,
      Map<String, ChangeConsumer<? extends AccessControlEntity>> collectionToConsumerMap,
      ChangeEventFailureHandler changeEventFailureHandler, AccessControlAdminService accessControlAdminService,
      boolean enableAclProcessingThroughOutbox) {
    this.idDeserializer = idDeserializer;
    this.collectionToDeserializerMap = collectionToDeserializerMap;
    this.collectionToConsumerMap = collectionToConsumerMap;
    this.changeEventFailureHandler = changeEventFailureHandler;
    this.accessControlAdminService = accessControlAdminService;
    this.enableAclProcessingThroughOutbox = enableAclProcessingThroughOutbox;

    IntervalFunction intervalFunction = IntervalFunction.ofExponentialBackoff(1000, 2);
    RetryConfig retryConfig = RetryConfig.custom()
                                  .ignoreExceptions(DuplicateKeyException.class, DuplicateFieldException.class)
                                  .intervalFunction(intervalFunction)
                                  .maxAttempts(10)
                                  .build();
    retry = Retry.of("debeziumEngineRetry", retryConfig);
  }

  @VisibleForTesting
  protected boolean handleEvent(ChangeEvent<String, String> changeEvent, Set<String> updateEventsSeen) {
    // If ACL Processing is enabled through outbox framework, disable it via Debezium.
    if (enableAclProcessingThroughOutbox) {
      return true;
    }

    String id = idDeserializer.deserialize(null, changeEvent.key().getBytes());
    Optional<String> collectionName = getCollectionName(changeEvent.destination());

    Optional<OpType> opType =
        getOperationType(((EmbeddedEngineChangeEvent<String, String>) changeEvent).sourceRecord());
    if (opType.isPresent() && collectionName.isPresent()) {
      if (opType.get() == UPDATE) {
        if (updateEventsSeen.contains(id)) {
          log.info("Skipping UPDATE event for entity: {}.{}", collectionName.get(), id);
          return true;
        }
      }
      log.info("Handling {} event for entity: {}.{}", opType.get(), collectionName.get(), id);

      ChangeConsumer<? extends AccessControlEntity> changeConsumer = collectionToConsumerMap.get(collectionName.get());

      AccessControlEntity accessControlEntity = deserialize(collectionName.get(), changeEvent);

      // For DELETE change event we will skip block/unblock check because
      // * We do not have deleted entity in change event
      // * We do not have account information in change event to decide if we should block it
      // * We can not get entity from db because it was already deleted
      // * It is ok to process delete change event irrespective of block/unblock now because after unblock it would be
      // difficult to reconcile these deleted entities
      if (!DELETE.equals(opType.get()) && isBlocked(accessControlEntity)) {
        return true;
      }
      boolean eventHandled =
          changeConsumer.consumeEvent(opType.get(), id, deserialize(collectionName.get(), changeEvent));
      // Skipping duplicate update events of same entity only when valid events get processed. Since there seems issue
      // with Debezium where it passes UserGroup/Resource Group with null properties except for Id. We can't consider
      // those events as processed as it will lead to skipping valid events.
      if (opType.get() == UPDATE && eventHandled && !updateEventsSeen.contains(id)) {
        updateEventsSeen.add(id);
      }
    }
    return true;
  }

  private boolean isBlocked(AccessControlEntity accessControlEntity) {
    Optional<String> accountId = accessControlEntity.getAccountId();
    return accountId.filter(accessControlAdminService::isBlocked).isPresent();
  }

  @Override
  public void handleBatch(List<ChangeEvent<String, String>> changeEvents,
      DebeziumEngine.RecordCommitter<ChangeEvent<String, String>> recordCommitter) throws InterruptedException {
    Set<String> updateEventsSeen = new HashSet<>();
    for (ChangeEvent<String, String> changeEvent : changeEvents) {
      try {
        retry.executeSupplier(() -> handleEvent(changeEvent, updateEventsSeen));
      } catch (Exception exception) {
        log.error(
            String.format(
                "Exception caught when trying to process event: [%s]. Retrying this event with exponential backoff now...",
                changeEvent),
            exception);
        changeEventFailureHandler.handle(changeEvent, exception);
      }
      recordCommitter.markProcessed(changeEvent);
    }
    recordCommitter.markBatchFinished();
  }

  private <T extends AccessControlEntity> T deserialize(
      String collectionName, ChangeEvent<String, String> changeEvent) {
    return (T) collectionToDeserializerMap.get(collectionName).deserialize(null, getValue(changeEvent));
  }

  private byte[] getValue(ChangeEvent<String, String> changeEvent) {
    return changeEvent.value() == null ? null : changeEvent.value().getBytes();
  }

  private Optional<OpType> getOperationType(SourceRecord sourceRecord) {
    return Optional.ofNullable(sourceRecord.headers().lastWithName(OP_FIELD))
        .flatMap(x -> OpType.fromString((String) x.value()));
  }

  private Optional<String> getCollectionName(String sourceRecordTopic) {
    return Optional.of(sourceRecordTopic.split("\\.")).filter(x -> x.length >= 2).map(x -> x[2]);
  }
}
