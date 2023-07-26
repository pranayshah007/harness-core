/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.eventsframework.monitor.publisher;

import static io.harness.eventsframework.monitor.EventsFrameworkMonitorConstants.REDIS_STREAMS_AVERAGE_MESSAGE_SIZE;
import static io.harness.eventsframework.monitor.EventsFrameworkMonitorConstants.REDIS_STREAMS_CONSUMER_GROUP_BEHIND_BY_COUNT;
import static io.harness.eventsframework.monitor.EventsFrameworkMonitorConstants.REDIS_STREAMS_CONSUMER_GROUP_PENDING_COUNT;
import static io.harness.eventsframework.monitor.EventsFrameworkMonitorConstants.REDIS_STREAMS_EVENTS_FRAMEWORK_DEADLETTER_QUEUE_SIZE;
import static io.harness.eventsframework.monitor.EventsFrameworkMonitorConstants.REDIS_STREAMS_LENGTH_METRIC;
import static io.harness.eventsframework.monitor.EventsFrameworkMonitorConstants.REDIS_STREAMS_MEMORY_USAGE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.monitor.context.RedisStreamConsumerGroupContext;
import io.harness.eventsframework.monitor.context.RedisStreamContext;
import io.harness.eventsframework.monitor.dto.AggregateRedisStreamMetricsDTO;
import io.harness.eventsframework.monitor.dto.RedisStreamConsumerGroupMetricsDTO;
import io.harness.eventsframework.monitor.dto.RedisStreamDTO;
import io.harness.eventsframework.monitor.dto.RedisStreamMetricsDTO;
import io.harness.metrics.service.api.MetricService;
import io.harness.metrics.service.api.MetricsPublisher;

import com.google.inject.Inject;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import service.RedisStreamsMetricsAggregator;

@OwnedBy(HarnessTeam.PL)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class RedisStreamStatsPublisher implements MetricsPublisher {
  private final MetricService metricService;
  private final RedisStreamsMetricsAggregator redisStreamsMetricsAggregator;

  private void sendMetricWithStreamContext(RedisStreamDTO redisStreamDTO, String metricName, double value) {
    try (RedisStreamContext context = new RedisStreamContext(redisStreamDTO)) {
      metricService.recordMetric(metricName, value);
    }
  }

  private void sendMetricWithConsumerGroupContext(
      RedisStreamDTO redisStreamDTO, String consumergroupName, String metricName, double value) {
    try (RedisStreamConsumerGroupContext context =
             new RedisStreamConsumerGroupContext(redisStreamDTO, consumergroupName)) {
      metricService.recordMetric(metricName, value);
    }
  }

  @Override
  public void recordMetrics() {
    AggregateRedisStreamMetricsDTO aggregateRedisStreamMetricsDTO = redisStreamsMetricsAggregator.getStreamStats();
    log.info("Aggregated Data to publish {}", aggregateRedisStreamMetricsDTO.toString());
    for (RedisStreamMetricsDTO streamMetrics : aggregateRedisStreamMetricsDTO.getRedisStreamMetricsDTOList()) {
      RedisStreamDTO redisStreamDTO = streamMetrics.getRedisStreamDTO();
      String streamName = redisStreamDTO.getStreamName();
      log.info("Publishing data for {}", streamName);
      sendMetricWithStreamContext(redisStreamDTO, REDIS_STREAMS_LENGTH_METRIC, streamMetrics.getStreamSize());
      sendMetricWithStreamContext(
          redisStreamDTO, REDIS_STREAMS_EVENTS_FRAMEWORK_DEADLETTER_QUEUE_SIZE, streamMetrics.getDeadLetterQueueSize());
      sendMetricWithStreamContext(redisStreamDTO, REDIS_STREAMS_MEMORY_USAGE, streamMetrics.getMemoryUsageInMBs());
      sendMetricWithStreamContext(
          redisStreamDTO, REDIS_STREAMS_AVERAGE_MESSAGE_SIZE, streamMetrics.getAverageMessageSizeInKBs());
      for (RedisStreamConsumerGroupMetricsDTO consumerGroupMetricsDTO : streamMetrics.getConsumergroupMetricsDTOs()) {
        String consumergroupName = consumerGroupMetricsDTO.getConsumergroupName();
        sendMetricWithConsumerGroupContext(redisStreamDTO, consumergroupName,
            REDIS_STREAMS_CONSUMER_GROUP_PENDING_COUNT, consumerGroupMetricsDTO.getPendingCount());
        sendMetricWithConsumerGroupContext(redisStreamDTO, consumergroupName,
            REDIS_STREAMS_CONSUMER_GROUP_BEHIND_BY_COUNT, consumerGroupMetricsDTO.getBehindByCount());
      }
    }
  }
}
