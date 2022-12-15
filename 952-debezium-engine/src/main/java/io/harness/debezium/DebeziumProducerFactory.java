/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.debezium;

import static io.harness.authorization.AuthorizationServiceHeader.DEBEZIUM_SERVICE;
import static io.harness.debezium.DebeziumConstants.DEBEZIUM_PREFIX;

import io.harness.eventsframework.EventsFrameworkConfiguration;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.impl.redis.RedisProducerFactory;
import io.harness.redis.RedissonClientFactory;

import com.google.inject.Inject;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;

@Slf4j
public class DebeziumProducerFactory {
  private static final Map<String, Producer> producerMap = new ConcurrentHashMap<>();

  @Inject RedisProducerFactory redisProducerFactory;
  @Inject EventsFrameworkConfiguration configuration;

  public Producer get(String collection, int redisStreamSize) {
    if (producerMap.containsKey(collection)) {
      return producerMap.get(collection);
    }

    RedissonClient redissonClient = RedissonClientFactory.getClient(configuration.getRedisConfig());
    Producer producer = redisProducerFactory.createRedisProducer(DEBEZIUM_PREFIX + collection, redissonClient,
        redisStreamSize, DEBEZIUM_SERVICE.getServiceId(), configuration.getRedisConfig().getEnvNamespace());
    producerMap.put(collection, producer);
    return producer;
  }
}
