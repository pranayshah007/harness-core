/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.debezium;

import io.harness.exception.InvalidRequestException;
import io.harness.logging.AutoLogContext;
import io.harness.redis.RedisConfig;
import io.harness.redis.RedissonClientFactory;
import io.harness.serializer.JsonUtils;

import com.mongodb.MongoCommandException;
import io.debezium.engine.DebeziumEngine;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;

@UtilityClass
@Slf4j
public class DebeziumUtils {
  public DebeziumEngine.ConnectorCallback getConnectorCallback(String collection, String mode) {
    try (AutoLogContext ignore = getAutoLogContext(collection, mode)) {
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
  }

  // Stopping the Debezium engine when snapshot is completed (this will only happen if engine running in snapshot only
  // mode) and resetting the offset only when error code is in the listOfErrorCodesForOffsetReset
  public DebeziumEngine.CompletionCallback getCompletionCallback(String redisConfigJson, String redisKey,
      DebeziumController debeziumController, String collection, List<Integer> listOfErrorCodesForOffsetReset,
      String mode) {
    return (success, message, error) -> {
      try (AutoLogContext ignore = getAutoLogContext(collection, mode)) {
        if (error instanceof InvalidRequestException
            && error.getMessage().contains("Stopping Debezium controller for collection:")) {
          log.info("Snapshot Completed for collection {}, stopping debezium controller..", collection);
          debeziumController.stopDebeziumController();
        } else if (!success && error != null && isErrorForOplogRotation(error, listOfErrorCodesForOffsetReset)) {
          resetOffset(JsonUtils.asObject(redisConfigJson, RedisConfig.class), redisKey);
          log.error(String.format("Offset reset for key: %s at %s", redisKey, System.currentTimeMillis()), error);
        } else if (error != null) {
          log.error(String.format("error in callback, message: %s", message), error);
        } else {
          log.info("Success: {}, message: {}, error: {}", success, message, error);
        }
      }
    };
  }

  public void resetOffset(RedisConfig redisConfig, String redisKey) {
    RedissonClient redisson = RedissonClientFactory.getClient(redisConfig);
    redisson.getKeys().delete(redisKey);
  }

  private boolean isErrorForOplogRotation(Throwable error, List<Integer> listOfErrorCodesForOffsetReset) {
    return error.getCause() != null && error.getCause().getCause() != null
        && error.getCause().getCause() instanceof MongoCommandException
        && listOfErrorCodesForOffsetReset.contains(((MongoCommandException) error.getCause().getCause()).getCode());
  }

  AutoLogContext getAutoLogContext(String collection, String mode) {
    Map<String, String> map = new HashMap<>();
    map.put("mode", mode);
    map.put("collection", collection);
    return new AutoLogContext(map, AutoLogContext.OverrideBehavior.OVERRIDE_NESTS);
  }
}
