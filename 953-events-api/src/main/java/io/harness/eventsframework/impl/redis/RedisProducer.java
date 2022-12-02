/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.eventsframework.impl.redis;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.eventsframework.impl.redis.RedisUtils.REDIS_STREAM_INTERNAL_KEY;
import static io.harness.eventsframework.impl.redis.RedisUtils.REDIS_STREAM_TRACE_ID_KEY;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.api.AbstractProducer;
import io.harness.eventsframework.api.EventsFrameworkDownException;
import io.harness.eventsframework.impl.redis.monitoring.dto.RedisEventMetricDTOMapper;
import io.harness.eventsframework.impl.redis.monitoring.publisher.RedisEventMetricPublisher;
import io.harness.eventsframework.producer.Message;

import com.google.inject.Inject;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.opentelemetry.api.trace.Span;
import io.vavr.control.Try;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.StreamMessageId;
import org.slf4j.MDC;

@OwnedBy(PL)
@Slf4j
public class RedisProducer extends AbstractProducer {
  private static final String PRODUCER = "producer";
  private static final String REDIS_PRODUCER_EVENT_METRIC = "redis_producer_event_metric";
  private RStream<String, String> stream;
  private RedissonClient redissonClient;
  @Inject private RedisEventMetricPublisher redisEventMetricPublisher;
  // This is used when the consumer for the event are no longer accepting due to some failure and
  // the messages are continuously being accumulated in Redis. To come up with this number, it is
  // very important to understand the alerting on the consumers and the scale estimations of a
  // particular use-case which is pushing to the topic
  private int maxTopicSize;

  private Retry retry;

  public RedisProducer(String topicName, @NotNull RedissonClient redissonClient, int maxTopicSize, String producerName,
      String envNamespace, RedisEventMetricPublisher redisEventMetricPublisher) {
    super(topicName, producerName);
    initProducer(topicName, redissonClient, maxTopicSize, envNamespace);
    this.redisEventMetricPublisher = redisEventMetricPublisher;
  }

  public RedisProducer(String topicName, @NotNull RedissonClient redissonClient, int maxTopicSize, String producerName,
      String envNamespace) {
    super(topicName, producerName);
    initProducer(topicName, redissonClient, maxTopicSize, envNamespace);
  }

  private void initProducer(
      String topicName, @NotNull RedissonClient redissonClient, int maxTopicSize, String envNamespace) {
    this.maxTopicSize = maxTopicSize;
    this.redissonClient = redissonClient;
    this.stream = RedisUtils.getStream(topicName, redissonClient, envNamespace);
    RetryConfig retryConfig =
        RetryConfig.custom().intervalFunction(IntervalFunction.ofExponentialBackoff(1000, 1.5)).maxAttempts(6).build();

    this.retry = Retry.of("redisProducer:" + topicName, retryConfig);
  }

  @Override
  public String send(Message message) {
    return handleMessage(message);
  }

  private String sendInternal(Message message) {
    try {
      Map<String, String> redisData = new HashMap<>(message.getMetadataMap());
      addTraceId(redisData);
      redisData.put(REDIS_STREAM_INTERNAL_KEY, Base64.getEncoder().encodeToString(message.getData().toByteArray()));
      populateOtherProducerSpecificData(redisData);

      StreamMessageId messageId = stream.addAll(redisData, maxTopicSize, false);
      redisData.remove(REDIS_STREAM_INTERNAL_KEY);
      log.info("Events framework message inserted - messageId: {}, metaData: {} in the topic: {}", messageId, redisData,
          this.getTopicName());
      return messageId.toString();
    } catch (Exception ex) {
      log.warn("Exception occurred in sendInternal", ex);
      throw ex;
    }
  }

  protected void populateOtherProducerSpecificData(Map<String, String> redisData) {
    redisData.put(PRODUCER, this.getProducerName());
  }

  private String handleMessage(Message message) {
    Supplier<String> sendMessageSupplier = () -> sendInternal(message);

    Supplier<String> retryingSendMessage = Retry.decorateSupplier(retry, sendMessageSupplier);

    return Try.ofSupplier(retryingSendMessage)
        .recover(throwable -> {
          // Exhausted exponential backoff to try operating on redis
          throw new EventsFrameworkDownException(throwable.getMessage());
        })
        .get();
  }

  @Override
  public void shutdown() {
    redissonClient.shutdown();
  }

  public static RedisProducer of(String topicName, @NotNull RedissonClient redissonClient, int maxTopicSize,
      String producerName, String envNamespace) {
    return new RedisProducer(topicName, redissonClient, maxTopicSize, producerName, envNamespace);
  }

  private void addMonitoring(Message message) {
    try {
      redisEventMetricPublisher.sendMetricWithEventContext(
          RedisEventMetricDTOMapper.prepareRedisEventMetricDTO(message, getTopicName()), REDIS_PRODUCER_EVENT_METRIC);
    } catch (Exception ex) {
      log.warn("Error while sending metrics for redis producer events :", ex);
    }
  }

  private void addTraceId(Map<String, String> redisData) {
    try {
      if (!Span.getInvalid().equals(Span.current())) {
        redisData.put(REDIS_STREAM_TRACE_ID_KEY, Span.current().getSpanContext().getTraceId());
      } else {
        Map<String, String> contextMap = MDC.getCopyOfContextMap();
        if (contextMap != null && contextMap.containsKey(REDIS_STREAM_TRACE_ID_KEY)) {
          redisData.put(REDIS_STREAM_TRACE_ID_KEY, contextMap.get(REDIS_STREAM_TRACE_ID_KEY));
        }
      }
    } catch (Exception e) {
      log.warn("Error while adding traceId ", e);
    }
  }
}
