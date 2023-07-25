/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.monitor.dto.AggregateRedisStreamMetricsDTO;
import io.harness.eventsframework.monitor.dto.RedisStreamConsumerGroupMetricsDTO;
import io.harness.eventsframework.monitor.dto.RedisStreamDTO;
import io.harness.eventsframework.monitor.dto.RedisStreamMetricsDTO;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.StreamGroup;
import org.redisson.api.StreamMessageId;
import org.redisson.client.RedisClient;
import org.redisson.client.codec.StringCodec;

@OwnedBy(HarnessTeam.PL)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class RedisStreamsMetricsAggregatorImpl implements RedisStreamsMetricsAggregator {
  private final RedissonClient redisClient;
  private final RedisClient lowLevelClient;

  private long getStreamLength(RStream<String, String> stream) {
    return stream.size();
  }

  private long getDeadletterQueueLength(RedisStreamDTO redisStreamDTO) {
    String namespace = redisStreamDTO.getNamespace();
    String prefix = namespace.isEmpty() ? "streams:" : namespace + ":streams:";
    RStream<String, String> stream = getRedisStream(prefix + "deadletter_queue:" + redisStreamDTO.getUsecaseName());
    return stream.size();
  }

  private double getAverageMessageSize(String streamName) {
    RStream<String, String> stream = getRedisStream(streamName);
    int batchSize = 100;
    Map<StreamMessageId, Map<String, String>> randomSample =
        stream.rangeReversed(batchSize, StreamMessageId.MAX, StreamMessageId.MIN);
    double total = 0;
    for (Map.Entry<StreamMessageId, Map<String, String>> streamMessageEntry : randomSample.entrySet()) {
      total += streamMessageEntry.getKey().toString().length();
      for (Map.Entry<String, String> messageAttributeEntry : streamMessageEntry.getValue().entrySet()) {
        total += messageAttributeEntry.getKey().length();
        total += messageAttributeEntry.getValue().length();
      }
    }

    return total / batchSize / 1024;
  }

  private int getConsumerBehindCount(
      RStream<String, String> currentStream, StreamMessageId startId, StreamMessageId endId) {
    int batchSize = 100;
    int count = 0;
    while (true) {
      Map<StreamMessageId, Map<String, String>> range = currentStream.range(batchSize, startId, endId);
      int retrievedSize = range.size();
      count += retrievedSize - 1;
      if (retrievedSize <= 1) {
        break;
      }
      StreamMessageId[] objects = range.keySet().toArray(new StreamMessageId[0]);
      startId = objects[objects.length - 1];
    }
    return Math.max(0, count);
  }

  private RStream<String, String> getRedisStream(String streamName) {
    return redisClient.getStream(streamName, new StringCodec("UTF-8"));
  }

  private RedisStreamDTO createRedisStreamDTO(String streamName) {
    String[] streamNameParts = streamName.split(":");
    String namespace = streamNameParts[0].equals("streams") ? "" : streamNameParts[0];
    String usecaseName = streamNameParts[streamNameParts.length - 1];
    return RedisStreamDTO.builder().namespace(namespace).usecaseName(usecaseName).streamName(streamName).build();
  }

  @Override
  public AggregateRedisStreamMetricsDTO getStreamStats() {
    Iterator<String> keysIterator = redisClient.getKeys().getKeysByPattern("*:streams:*", 1000).iterator();

    List<RedisStreamMetricsDTO> redisStreamMetricsDTOList = new ArrayList<>();
    while (keysIterator.hasNext()) {
      try {
        String streamName = keysIterator.next();
        if (streamName.matches("(.*)streams:deadletter_queue(.*)")) {
          log.debug("Skipping deadletter queue key {}", streamName);
          continue;
        }

        RedisStreamDTO redisStreamDTO = createRedisStreamDTO(streamName);
        RStream<String, String> currentStream = getRedisStream(streamName);

        long streamLength = getStreamLength(currentStream);
        double averageMessageSize = getAverageMessageSize(streamName);
        double memoryUsageInMBs = redisClient.getStream(streamName).sizeInMemory();

        long deadletterQueueLength = getDeadletterQueueLength(redisStreamDTO);
        List<StreamGroup> groups = redisClient.getStream(streamName).listGroups();
        StreamMessageId lastGeneratedId = currentStream.getInfo().getLastGeneratedId();
        List<RedisStreamConsumerGroupMetricsDTO> consumerGroupMetricsDTOS = new ArrayList<>();
        for (StreamGroup group : groups) {
          consumerGroupMetricsDTOS.add(
              RedisStreamConsumerGroupMetricsDTO.builder()
                  .consumergroupName(group.getName())
                  .pendingCount(group.getPending())
                  .behindByCount(getConsumerBehindCount(currentStream, group.getLastDeliveredId(), lastGeneratedId))
                  .build());
        }
        redisStreamMetricsDTOList.add(RedisStreamMetricsDTO.builder()
                                          .redisStreamDTO(redisStreamDTO)
                                          .averageMessageSizeInKBs(averageMessageSize)
                                          .deadLetterQueueSize(deadletterQueueLength)
                                          .memoryUsageInMBs(memoryUsageInMBs)
                                          .streamSize(streamLength)
                                          .consumergroupMetricsDTOs(consumerGroupMetricsDTOS)
                                          .build());
      } catch (Exception e) {
        log.error("Failed in getting stream stats", e);
      }
    }
    return AggregateRedisStreamMetricsDTO.builder().redisStreamMetricsDTOList(redisStreamMetricsDTOList).build();
  }
}
