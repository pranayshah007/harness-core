/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.eventsframework.impl.redis;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static com.google.protobuf.util.Timestamps.fromMillis;
import static java.lang.Long.parseLong;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.consumer.Message;
import io.harness.redis.RedisConfig;
import io.harness.redis.RedisSSLConfig;

import com.google.protobuf.ByteString;
import com.google.protobuf.Timestamp;
import java.io.File;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.redisson.Redisson;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.StreamMessageId;
import org.redisson.client.RedisClient;
import org.redisson.client.RedisClientConfig;
import org.redisson.client.codec.StringCodec;
import org.redisson.config.Config;
import org.redisson.config.ReadMode;
import org.redisson.config.SingleServerConfig;

@OwnedBy(PL)
@UtilityClass
@Slf4j
public class RedisUtils {
  // Keeping this as small as possible to save on memory for redis instance
  public static final String REDIS_STREAM_INTERNAL_KEY = "o";
  public static final int MAX_DEAD_LETTER_QUEUE_SIZE = 50000;
  public static final int UNACKED_RETRY_COUNT = 10;
  private static final int DEFAULT_MIN_CONNECTION_IDLE_SIZE = 5;

  public RedissonClient getClient(RedisConfig redisConfig) {
    Config config = new Config();
    if (!redisConfig.isSentinel()) {
      SingleServerConfig serverConfig = config.useSingleServer().setAddress(redisConfig.getRedisUrl());
      String redisPassword = redisConfig.getPassword();
      String redisUserName = redisConfig.getUserName();

      if (isNotEmpty(redisUserName)) {
        serverConfig.setUsername(redisUserName);
      }

      if (isNotEmpty(redisPassword)) {
        serverConfig.setPassword(redisPassword);
      }

      // Default retry interval is 1500 milliseconds
      serverConfig.setRetryAttempts(10);

      serverConfig.setConnectionMinimumIdleSize(
          Math.max(DEFAULT_MIN_CONNECTION_IDLE_SIZE, redisConfig.getConnectionMinimumIdleSize()));

      RedisSSLConfig sslConfig = redisConfig.getSslConfig();
      if (sslConfig != null && sslConfig.isEnabled()) {
        try {
          serverConfig.setSslTruststore(new File(sslConfig.getCATrustStorePath()).toURI().toURL());
          serverConfig.setSslTruststorePassword(sslConfig.getCATrustStorePassword());
        } catch (MalformedURLException e) {
          log.error("Malformed URL provided for Redis SSL CA trustStore file", e);
          return null;
        }
      }
    } else {
      config.useSentinelServers().setMasterName(redisConfig.getMasterName());
      for (String sentinelUrl : redisConfig.getSentinelUrls()) {
        config.useSentinelServers().addSentinelAddress(sentinelUrl);
      }
      config.useSentinelServers().setReadMode(ReadMode.valueOf(redisConfig.getReadMode().name()));
    }
    config.setNettyThreads(redisConfig.getNettyThreads());
    config.setUseScriptCache(redisConfig.isUseScriptCache());
    return Redisson.create(config);
  }

  public RedisClient getLowLevelClient(RedisConfig redisConfig) {
    RedisClientConfig config = new RedisClientConfig();
    if (!redisConfig.isSentinel()) {
      config = config.setAddress(redisConfig.getRedisUrl());
      String redisPassword = redisConfig.getPassword();
      String redisUserName = redisConfig.getUserName();

      if (isNotEmpty(redisUserName)) {
        config.setUsername(redisUserName);
      }

      if (isNotEmpty(redisPassword)) {
        config.setPassword(redisPassword);
      }

      RedisSSLConfig sslConfig = redisConfig.getSslConfig();
      if (sslConfig != null && sslConfig.isEnabled()) {
        try {
          config.setSslTruststore(new File(sslConfig.getCATrustStorePath()).toURI().toURL());
          config.setSslTruststorePassword(sslConfig.getCATrustStorePassword());
        } catch (MalformedURLException e) {
          log.error("Malformed URL provided for Redis SSL CA trustStore file", e);
          return null;
        }
      }
    } else {
      throw new NotImplementedException("Sentinel support is not added for low level redis client");
    }
    return RedisClient.create(config);
  }

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
