/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.debezium;

import io.harness.redis.RedisConfig;
import io.harness.redis.RedissonClientFactory;
import io.harness.serializer.JsonUtils;

import io.debezium.engine.DebeziumEngine;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;

@UtilityClass
@Slf4j
public class DebeziumUtils {
  public static DebeziumEngine.ConnectorCallback getConnectorCallback(String collection) {
    return new DebeziumEngine.ConnectorCallback() {
      @Override
      public void connectorStopped() {
        log.info("Debezium connector stopped for collection {}", collection);
      }
      @Override
      public void taskStopped() {
        log.info("Task stopped for collection {}", collection);
      }
    };
  }

  public static DebeziumEngine.CompletionCallback getCompletionCallback(String redisConfigJson, String redisKey) {
    return (success, message, error) -> {
      if (!success) {
        resetOffset(JsonUtils.asObject(redisConfigJson, RedisConfig.class), redisKey);
        log.error(
            "Offset reset for key: {} because of exception: {}, at {}", redisKey, error, System.currentTimeMillis());
      }
    };
  }

  public static void resetOffset(RedisConfig redisConfig, String redisKey) {
    RedissonClient redisson = RedissonClientFactory.getClient(redisConfig);
    redisson.getKeys().delete(redisKey);
  }
}
