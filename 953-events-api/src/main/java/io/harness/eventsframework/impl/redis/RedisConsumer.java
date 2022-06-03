/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.eventsframework.impl.redis;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.impl.redis.monitoring.publisher.RedisEventMetricPublisher;
import io.harness.redis.RedisConfig;

import java.time.Duration;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;

@OwnedBy(PL)
@Slf4j
public class RedisConsumer extends RedisAbstractConsumer {
  public RedisConsumer(
      String topicName, String groupName, @NotNull RedisConfig redisConfig, Duration maxProcessingTime, int batchSize) {
    super(topicName, groupName, redisConfig, maxProcessingTime, batchSize);
  }

  public RedisConsumer(String topicName, String groupName, @NotNull RedissonClient redissonClient,
      Duration maxProcessingTime, int batchSize, String envNamespace) {
    super(topicName, groupName, redissonClient, maxProcessingTime, batchSize, envNamespace);
  }

  public RedisConsumer(String topicName, String groupName, @NotNull RedissonClient redissonClient,
      Duration maxProcessingTime, int batchSize, String envNamespace,
      RedisEventMetricPublisher redisEventMetricPublisher) {
    super(topicName, groupName, redissonClient, maxProcessingTime, batchSize, envNamespace, redisEventMetricPublisher);
  }

  @Override
  public List<Message> read(Duration maxWaitTime) {
    return getMessages(false, maxWaitTime);
  }

  public static RedisConsumer of(
      String topicName, String groupName, @NotNull RedisConfig redisConfig, Duration maxProcessingTime, int batchSize) {
    return new RedisConsumer(topicName, groupName, redisConfig, maxProcessingTime, batchSize);
  }

  public static RedisConsumer of(String topicName, String groupName, @NotNull RedissonClient redissonClient,
      Duration maxProcessingTime, int batchSize, String envNamespace) {
    return new RedisConsumer(topicName, groupName, redissonClient, maxProcessingTime, batchSize, envNamespace);
  }

  public static RedisConsumer of(String topicName, String groupName, @NotNull RedissonClient redissonClient,
      Duration maxProcessingTime, int batchSize, String envNamespace,
      RedisEventMetricPublisher redisEventMetricPublisher) {
    return new RedisConsumer(
        topicName, groupName, redissonClient, maxProcessingTime, batchSize, envNamespace, redisEventMetricPublisher);
  }
}
