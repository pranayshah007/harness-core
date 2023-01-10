/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.mongo.queue;

import static io.harness.manage.GlobalContextManager.obtainGlobalContext;

import io.harness.logging.AutoLogRemoveContext;
import io.harness.persistence.HPersistence;
import io.harness.queue.MessageLogContext;
import io.harness.queue.Queuable;
import io.harness.queue.QueuePublisher;
import io.harness.queue.TopicUtils;

import com.google.inject.Inject;
import java.util.List;
import java.util.Objects;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MongoQueuePublisher<T extends Queuable> implements QueuePublisher<T> {
  @Getter private final String name;
  @Getter private final String topicPrefix;

  @Inject private HPersistence persistence;

  public MongoQueuePublisher(String name, List<String> topicPrefixElements) {
    this.name = name;
    topicPrefix = TopicUtils.combineElements(topicPrefixElements);
  }

  private void store(T payload) {
    try (AutoLogRemoveContext ignore = new AutoLogRemoveContext(
             MessageLogContext.MESSAGE_CLASS, MessageLogContext.MESSAGE_ID, MessageLogContext.MESSAGE_TOPIC)) {
      log.debug("Notification saved [{}]", payload);
      log.debug("Current Size: [{}]", persistence.createQuery(payload.getClass()).count());
      payload.setGlobalContext(obtainGlobalContext());
    }
    persistence.insertIgnoringDuplicateKeys(payload);
  }

  @Override
  public void send(final T payload) {
    Objects.requireNonNull(payload);
    payload.setTopic(topicPrefix);
    store(payload);
  }

  @Override
  public void send(List<String> additionalTopicElements, final T payload) {
    Objects.requireNonNull(payload);
    payload.setTopic(TopicUtils.appendElements(topicPrefix, additionalTopicElements));
    store(payload);
  }
}
