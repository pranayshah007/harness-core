/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.eventsframework.impl.redis;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.eventsframework.api.AbstractConsumer;
import io.harness.eventsframework.api.EventsFrameworkDownException;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.impl.redis.monitoring.dto.RedisEventMetricDTOMapper;
import io.harness.eventsframework.impl.redis.monitoring.publisher.RedisEventMetricPublisher;

import com.google.inject.Inject;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.vavr.control.Try;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.PendingEntry;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.StreamGroup;
import org.redisson.api.StreamMessageId;
import org.redisson.client.RedisException;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PL)
@Slf4j
public abstract class RedisAbstractConsumer extends AbstractConsumer {
  private static final String REDIS_CONSUMER_EVENT_METRIC = "redis_consumer_event_metric";
  protected RStream<String, String> stream;
  protected RStream<String, String> deadLetterQueue;
  protected RedissonClient redissonClient;
  protected Duration maxProcessingTime;
  protected Set<String> consumerGroupNames;
  @Getter protected int batchSize;
  private Retry retry;
  @Inject RedisEventMetricPublisher redisEventMetricPublisher;

  public RedisAbstractConsumer(String topicName, String groupName, @NotNull RedissonClient redissonClient,
      Duration maxProcessingTime, int batchSize, String envNamespace) {
    super(topicName, groupName);
    initConsumerGroup(topicName, redissonClient, maxProcessingTime, batchSize, envNamespace);
  }

  public RedisAbstractConsumer(String topicName, String groupName, @NotNull RedissonClient redissonClient,
      Duration maxProcessingTime, int batchSize, String envNamespace,
      RedisEventMetricPublisher redisEventMetricPublisher) {
    super(topicName, groupName);
    initConsumerGroup(topicName, redissonClient, maxProcessingTime, batchSize, envNamespace);
    this.redisEventMetricPublisher = redisEventMetricPublisher;
  }

  public RedisAbstractConsumer(String topicName, String groupName, String consumerName,
      @NotNull RedissonClient redissonClient, Duration maxProcessingTime, int batchSize, String envNamespace) {
    super(topicName, groupName, consumerName);
    initConsumerGroup(topicName, redissonClient, maxProcessingTime, batchSize, envNamespace);
  }

  private void initConsumerGroup(
      String topicName, RedissonClient redissonClient, Duration maxProcessingTime, int batchSize, String envNamespace) {
    this.redissonClient = redissonClient;
    this.stream = RedisUtils.getStream(getTopicName(), redissonClient, envNamespace);
    this.deadLetterQueue = RedisUtils.getDeadLetterStream(getTopicName(), redissonClient, envNamespace);
    this.maxProcessingTime = maxProcessingTime;
    this.batchSize = batchSize;
    RetryConfig retryConfig =
        RetryConfig.custom().intervalFunction(IntervalFunction.ofExponentialBackoff(1000, 1.5)).maxAttempts(5).build();
    this.retry = Retry.of("redisConsumer:" + topicName, retryConfig);
    this.consumerGroupNames = stream.isExists()
        ? stream.listGroups().stream().map(StreamGroup::getName).collect(Collectors.toSet())
        : new HashSet<>();
    createConsumerGroup();
  }

  private void createConsumerGroup() {
    String groupName = getGroupName();
    try {
      if (!consumerGroupNames.contains(getGroupName())) {
        stream.createGroup(groupName, StreamMessageId.ALL);
      } else {
        log.info(
            "[REDIS-UPGRADE]: Consumer group {} already exists, continuing with consumer operations...", groupName);
      }
    } catch (RedisException e) {
      // TODO: Review if this code is still required and remove in the next commit
      log.info("Consumer group {} already exists, continuing with consumer operations...", groupName);
    }
  }

  private void moveMessageToDeadLetterQueue(
      StreamMessageId messageId, String groupName, Map<StreamMessageId, Map<String, String>> messages) {
    Map<String, String> messageData = messages.getOrDefault(messageId, new HashMap<>());
    messageData.put("prevMessageId", messageId.toString());
    deadLetterQueue.addAll(messageData, RedisUtils.MAX_DEAD_LETTER_QUEUE_SIZE, false);
    stream.ack(groupName, messageId);
    log.warn("Pushed {} to dead letter queue as max retries exceeded. Message data: {}", messageId, messageData);
  }

  private List<PendingEntry> getPendingEntries() {
    Supplier<List<PendingEntry>> getPendingEntriesSupplier = () -> getPendingEntriesInternal();

    Supplier<List<PendingEntry>> retryingGetPendingEntries = Retry.decorateSupplier(retry, getPendingEntriesSupplier);
    return Try.ofSupplier(retryingGetPendingEntries)
        .recover(throwable -> {
          createConsumerGroupIfNotPresent(throwable);
          // Exhausted exponential backoff to try operating on redis
          throw new EventsFrameworkDownException(throwable.getMessage());
        })
        .get();
  }

  private List<PendingEntry> getPendingEntriesInternal() {
    try {
      return stream.listPending(getGroupName(), StreamMessageId.MIN, StreamMessageId.MAX, batchSize);
    } catch (Exception ex) {
      log.warn("Exception occurred while listing pending entries", ex);
      throw ex;
    }
  }

  private List<Message> claimEntries(List<PendingEntry> pendingEntries) {
    Supplier<List<Message>> getClaimEntriesSupplier = () -> claimEntriesInternal(pendingEntries);

    Supplier<List<Message>> retryingClaimEntries = Retry.decorateSupplier(retry, getClaimEntriesSupplier);
    return Try.ofSupplier(retryingClaimEntries)
        .recover(throwable -> {
          createConsumerGroupIfNotPresent(throwable);
          // Exhausted exponential backoff to try operating on redis
          throw new EventsFrameworkDownException(throwable.getMessage());
        })
        .get();
  }

  private List<Message> claimEntriesInternal(List<PendingEntry> pendingEntries) {
    try {
      String groupName = getGroupName();
      if (pendingEntries.isEmpty()) {
        return Collections.emptyList();
      } else {
        Map<StreamMessageId, Map<String, String>> messages = executeClaimCommand(pendingEntries);
        for (PendingEntry entry : pendingEntries) {
          StreamMessageId messageId = entry.getId();
          // If the message has been delivered breach our redelivery threshold then move this to deadLetterQueue
          if (entry.getLastTimeDelivered() >= RedisUtils.UNACKED_RETRY_COUNT) {
            moveMessageToDeadLetterQueue(messageId, groupName, messages);
          }
        }
        return RedisUtils.getMessageObject(messages);
      }
    } catch (Exception ex) {
      log.warn("Exception occurred while claiming entries", ex);
      throw ex;
    }
  }

  private Map<StreamMessageId, Map<String, String>> executeClaimCommand(List<PendingEntry> pendingEntries) {
    StreamMessageId[] messageIds = pendingEntries.stream().map(PendingEntry::getId).toArray(StreamMessageId[] ::new);
    Map<StreamMessageId, Map<String, String>> messages = Collections.emptyMap();
    try {
      // Try to claim the pending messages from the group that are pending over max max processing time
      messages =
          stream.claim(getGroupName(), getName(), maxProcessingTime.toMillis(), TimeUnit.MILLISECONDS, messageIds);
    } catch (RedisException e) {
      if (e.getMessage().matches("(.*)Unexpected exception while processing command(.*)")) {
        log.warn("Claim called with messageIds which were not present - {}", messageIds);
      }
    }
    return messages;
  }

  private List<Message> getNewMessages(Duration maxWaitTime) {
    Supplier<List<Message>> getNewMessagesSupplier = () -> getNewMessagesInternal(maxWaitTime);

    Supplier<List<Message>> retryingGetNewMessages = Retry.decorateSupplier(retry, getNewMessagesSupplier);
    return Try.ofSupplier(retryingGetNewMessages)
        .recover(throwable -> {
          createConsumerGroupIfNotPresent(throwable);
          // Exhausted exponential backoff to try operating on redis
          throw new EventsFrameworkDownException(throwable.getMessage());
        })
        .get();
  }

  private List<Message> getNewMessagesInternal(Duration maxWaitTime) {
    try {
      return RedisUtils.getMessageObject(
          stream.readGroup(getGroupName(), getName(), batchSize, maxWaitTime.toMillis(), TimeUnit.MILLISECONDS));
    } catch (Exception ex) {
      log.warn("Exception occurred while getting new messages", ex);
      throw ex;
    }
  }

  private void addMonitoring(Message message) {
    try {
      redisEventMetricPublisher.sendMetricWithEventContext(
          RedisEventMetricDTOMapper.prepareRedisEventMetricDTO(message.getMessage(), getTopicName()),
          REDIS_CONSUMER_EVENT_METRIC);
    } catch (Exception ex) {
      log.warn("Error while sending metrics for redis consumer events :", ex);
    }
  }

  protected List<Message> getMessages(boolean processUnackedMessagesBeforeNewMessages, Duration maxWaitTime) {
    // Find all the pending entries on the consumer group
    List<PendingEntry> pendingEntries = getPendingEntries();

    // If no entry is pending then we request for new messages
    if (pendingEntries.isEmpty()) {
      return getNewMessages(maxWaitTime);
    }

    // If pending entries are not empty then claim the pending entries
    List<Message> claimedMessages = claimEntries(pendingEntries);
    if (!claimedMessages.isEmpty()) {
      return claimedMessages;
    }

    if (processUnackedMessagesBeforeNewMessages) {
      return Collections.emptyList();
    } else {
      return getNewMessages(maxWaitTime);
    }
  }

  @Override
  public void acknowledge(String messageId) {
    Supplier<Void> acknowledgeSupplier = () -> {
      acknowledgeInternal(messageId);
      return null;
    };

    Supplier<Void> retryingAckMessage = Retry.decorateSupplier(retry, acknowledgeSupplier);
    Try.ofSupplier(retryingAckMessage)
        .recover(throwable -> {
          createConsumerGroupIfNotPresent(throwable);
          // Exhausted exponential backoff to try operating on redis
          throw new EventsFrameworkDownException(throwable.getMessage());
        })
        .get();
  }

  private void acknowledgeInternal(String messageId) {
    stream.ack(getGroupName(), RedisUtils.getStreamId(messageId));
  }

  private void createConsumerGroupIfNotPresent(Throwable e) {
    if (e.getMessage().matches("(.*)NOGROUP No such key(.*)or consumer group(.*)")) {
      log.info("Key or consumer group not present, attempting to create consumer group {} for {}", getGroupName(),
          getTopicName());
      createConsumerGroup();
    }
  }

  @Override
  public void shutdown() {
    redissonClient.shutdown();
  }
}
