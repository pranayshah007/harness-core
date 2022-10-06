/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.eventsframework.impl.redis;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static com.google.protobuf.util.Timestamps.fromMillis;
import static java.lang.Long.parseLong;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.consumer.Message;

import com.google.protobuf.ByteString;
import com.google.protobuf.Timestamp;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.StreamMessageId;
import org.redisson.client.codec.StringCodec;

@OwnedBy(PL)
@UtilityClass
public class RedisUtils {
  // Keeping this as small as possible to save on memory for redis instance
  public static final String REDIS_STREAM_INTERNAL_KEY = "o";
  public static final String REDIS_STREAM_TRACE_ID_KEY = "trace_id";
  public static final int MAX_DEAD_LETTER_QUEUE_SIZE = 50000;
  public static final int UNACKED_RETRY_COUNT = 10;

  public Timestamp getMessageTimestamp(String messageId) {
    return fromMillis(parseLong(messageId.split("-")[0]));
  }

  public RStream<String, String> getStream(String topicName, RedissonClient client, String envNamespace) {
    return client.getStream(getStreamName(envNamespace, topicName), new StringCodec("UTF-8"));
  }

  public RStream<String, String> getDeadLetterStream(String topicName, RedissonClient client, String envNamespace) {
    String deadLetterStreamName = "deadletter_queue:" + topicName;
    return getStream(deadLetterStreamName, client, envNamespace);
  }

  public String getStreamName(String envNamespace, String topicName) {
    return (envNamespace.isEmpty() ? "" : envNamespace + ":") + "streams:" + topicName;
  }

  public StreamMessageId getStreamId(String messageId) {
    if (messageId.equals("$")) {
      return StreamMessageId.NEWEST;
    }

    String[] parts = messageId.split("-");
    return new StreamMessageId(parseLong(parts[0]), parseLong(parts[1]));
  }

  public List<Message> getMessageObject(Map<StreamMessageId, Map<String, String>> result) {
    if (isEmpty(result)) {
      return Collections.emptyList();
    } else {
      List<Message> messages = new ArrayList<>();
      Map<String, String> messageMap;
      StreamMessageId messageId;
      for (Map.Entry<StreamMessageId, Map<String, String>> entry : result.entrySet()) {
        messageId = entry.getKey();
        messageMap = entry.getValue();
        messages.add(getConsumerMessageObject(messageId, messageMap));
      }

      return messages;
    }
  }

  public io.harness.eventsframework.producer.Message getProducedMessage(
      String messageData, Map<String, String> messageMap) {
    return io.harness.eventsframework.producer.Message.newBuilder()
        .setData(ByteString.copyFrom(Base64.getDecoder().decode(messageData)))
        .putAllMetadata(messageMap)
        .build();
  }

  public Message getConsumerMessageObject(StreamMessageId messageId, Map<String, String> messageMap) {
    String messageData = messageMap.remove(REDIS_STREAM_INTERNAL_KEY);

    return Message.newBuilder()
        .setId(messageId.toString())
        .setMessage(getProducedMessage(messageData, messageMap))
        .setTimestamp(RedisUtils.getMessageTimestamp(messageId.toString()))
        .build();
  }
}
