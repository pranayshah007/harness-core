/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.events;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.impl.redis.RedisUtils;
import io.harness.redis.RedisConfig;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.redisson.api.RedissonClient;

@OwnedBy(HarnessTeam.PIPELINE)
public class PmsRedissonClientFactory {
  private static final Map<RedisConfig, RedissonClient> redisConfigRedissonClientMap = new ConcurrentHashMap<>();

  public static RedissonClient getRedisClient(RedisConfig redisConfig) {
    if (!redisConfigRedissonClientMap.containsKey(redisConfig)) {
      redisConfigRedissonClientMap.put(redisConfig, RedisUtils.getClient(redisConfig));
    }
    return redisConfigRedissonClientMap.get(redisConfig);
  }
}
