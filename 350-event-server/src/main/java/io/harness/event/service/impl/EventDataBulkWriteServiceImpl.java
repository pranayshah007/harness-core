/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.event.service.impl;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.commons.entities.events.PublishedMessage;
import io.harness.ccm.commons.entities.events.PublishedMessage.PublishedMessageKeys;
import io.harness.event.app.EventServiceConfig;
import io.harness.event.service.intfc.EventBatchQueryFnFactory;
import io.harness.event.service.intfc.EventDataBulkWriteService;
import io.harness.persistence.HPersistence;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.BasicDBObject;
import com.mongodb.BulkWriteOperation;
import com.mongodb.BulkWriteResult;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(CE)
public class EventDataBulkWriteServiceImpl implements EventDataBulkWriteService {
  private static final int BULK_WRITE_MAX_RETRIES = 3;
  private final HPersistence hPersistence;
  private final EventServiceConfig eventServiceConfig;

  @Inject
  public EventDataBulkWriteServiceImpl(final HPersistence hPersistence, final EventServiceConfig eventServiceConfig) {
    this.hPersistence = hPersistence;
    this.eventServiceConfig = eventServiceConfig;
  }

  private final EventBatchQueryFnFactory<PublishedMessage> publishedMessageInsertQueryFn =
      (bulkWriteOperation, publishedMessage) -> {
    final BasicDBObject publishedMessageBasicDBObject =
        new BasicDBObject()
            .append("_id", publishedMessage.getUuid())
            .append(PublishedMessageKeys.accountId, publishedMessage.getAccountId())
            .append(PublishedMessageKeys.createdAt, Instant.now().toEpochMilli())
            .append(PublishedMessageKeys.occurredAt, publishedMessage.getOccurredAt())
            .append(PublishedMessageKeys.validUntil, publishedMessage.getValidUntil())
            .append(PublishedMessageKeys.type, publishedMessage.getType())
            .append(PublishedMessageKeys.data, publishedMessage.getData())
            .append(PublishedMessageKeys.category, publishedMessage.getCategory())
            .append(PublishedMessageKeys.attributes, publishedMessage.getAttributes());

    bulkWriteOperation.insert(publishedMessageBasicDBObject);
  };

  @Override
  public boolean bulkInsertPublishedMessages(final List<PublishedMessage> publishedMessages) {
    return batchQueryExecutor(publishedMessages, publishedMessageInsertQueryFn, PublishedMessage.class);
  }

  private <T> boolean batchQueryExecutor(
      final List<T> itemsList, final EventBatchQueryFnFactory<T> eventBatchQueryFnFactory, final Class clazz) {
    final int bulkWriteLimit = eventServiceConfig.getEventDataBatchQueryConfig().getQueryBatchSize();

    for (final List<T> itemsListPartitioned : Lists.partition(itemsList, bulkWriteLimit)) {
      final BulkWriteOperation bulkWriteOperation =
          hPersistence.getCollection(clazz).initializeUnorderedBulkOperation();
      for (final T singleItem : itemsListPartitioned) {
        try {
          eventBatchQueryFnFactory.addQueryFor(bulkWriteOperation, singleItem);
        } catch (final Exception ex) {
          log.error("Error updating {}:[{}]", clazz.getSimpleName(), singleItem.toString(), ex);
        }
      }
      final BulkWriteResult result = bulkWriteExecutor(bulkWriteOperation);
      if (!result.isAcknowledged()) {
        return false;
      }
    }
    return true;
  }

  private BulkWriteResult bulkWriteExecutor(final BulkWriteOperation bulkWriteOperation) {
    BulkWriteResult result;
    for (int i = 1; i < BULK_WRITE_MAX_RETRIES; i++) {
      try {
        result = bulkWriteOperation.execute();
        log.info("BulkWriteExecutor result: {}", result);
        return result;
      } catch (final IllegalArgumentException ex) {
        log.error("Exception occurred with bulkWriteExecutor", ex);
        throw ex;
      } catch (final Exception ex) {
        log.warn("Exception occurred with bulkWriteExecutor, retry:{}", i, ex);
      }
    }
    result = bulkWriteOperation.execute();
    log.info(
        "BulkWriteExecutor result [acknowledged:{}, insertedCount:{}, matchedCount:{}, modifiedCount:{}, removedCount:{}]",
        result.isAcknowledged(), result.getInsertedCount(), result.getMatchedCount(), result.getModifiedCount(),
        result.getRemovedCount());
    return result;
  }
}
