/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.reader;

import static com.google.common.base.MoreObjects.firstNonNull;

import io.harness.batch.processing.dao.intfc.PublishedMessageDao;
import io.harness.ccm.commons.entities.events.PublishedMessage;

import java.util.List;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * This is a stateful reader and hence can't be singleton.
 */
@Slf4j
public class PublishedMessageBatchedReader extends BatchedItemReader<PublishedMessage> {
  private final String accountId;
  private final String messageType;
  private Long startTime;
  private final Long endTime;
  private final int readerBatchSize;
  private final PublishedMessageDao publishedMessageDao;

  public PublishedMessageBatchedReader(String accountId, String messageType, Long startDate, Long endDate,
      Integer readerBatchSize, PublishedMessageDao publishedMessageDao) {
    this.accountId = accountId;
    this.messageType = messageType;
    this.startTime = startDate;
    this.endTime = endDate;
    this.readerBatchSize = firstNonNull(readerBatchSize, DEFAULT_READER_BATCH_SIZE);
    this.publishedMessageDao = publishedMessageDao;
  }

  @Override
  @NonNull
  protected List<PublishedMessage> getMore() {
    final List<PublishedMessage> items =
        publishedMessageDao.fetchPublishedMessage(accountId, messageType, startTime, endTime, readerBatchSize);

    if (!items.isEmpty()) {
      Long firstStartTime = items.get(0).getCreatedAt();
      startTime = items.get(items.size() - 1).getCreatedAt();
      if (firstStartTime.equals(startTime)) {
        log.info(
            "Incrementing start Date by 1ms {} {} {} {} {}", items.size(), startTime, endTime, messageType, accountId);
        startTime = startTime + 1;
      }
    }

    return items;
  }
}
