/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.debezium;

import static io.harness.AuthorizationServiceHeader.DEBEZIUM_SERVICE;
import static io.harness.debezium.DebeziumConstants.DEBEZIUM_PREFIX;
import static io.harness.debezium.DebeziumConstants.DEBEZIUM_TOPIC_SIZE;

import io.harness.eventsframework.EventsFrameworkConfiguration;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.impl.redis.RedisProducerFactory;
import io.harness.eventsframework.impl.redis.RedisUtils;
import io.harness.redis.RedisConfig;

import com.google.inject.Inject;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;

@Slf4j
public class DebeziumProducerFactory {
  private static final Map<String, Producer> producerMap = new ConcurrentHashMap<>();
  private static final Map<RedisConfig, RedissonClient> redissonClientMap = new ConcurrentHashMap<>();

  @Inject RedisProducerFactory redisProducerFactory;
  @Inject EventsFrameworkConfiguration configuration;

  RedissonClient getRedissonClient(RedisConfig redisConfig) {
    if (redisConfig != null) {
      if (redissonClientMap.containsKey(redisConfig)) {
        return redissonClientMap.get(redisConfig);
      }
      RedissonClient client = RedisUtils.getClient(redisConfig);
      redissonClientMap.put(redisConfig, client);
      return client;
    } else {
      log.error("RedisConfig is null");
      return null;
    }
  }

  public Producer get(String collection) {
    if (producerMap.containsKey(collection)) {
      return producerMap.get(collection);
    }

    RedisConfig redisConfig = configuration.getRedisConfig();
    RedissonClient client = getRedissonClient(redisConfig);
    Producer producer = redisProducerFactory.createRedisProducer(DEBEZIUM_PREFIX + collection, client,
        DEBEZIUM_TOPIC_SIZE, DEBEZIUM_SERVICE.getServiceId(), redisConfig.getEnvNamespace());
    producerMap.put(collection, producer);
    return producer;
  }
}
