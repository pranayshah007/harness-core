/*
 * Copyright 2008-2020 Async-IO.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package io.harness.stream.redisson;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.UnexpectedException;
import io.harness.redis.RedisConfig;
import io.harness.redis.RedissonKryoCodec;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.ReadMode;

@OwnedBy(PL)
@UtilityClass
@Slf4j
public class RedissonFactory {
  static RedissonClient getRedissonClient(RedisConfig redisConfig) {
    Config config = new Config();
    if (!redisConfig.isSentinel()) {
      config.useSingleServer().setAddress(redisConfig.getRedisUrl());
      config.useSingleServer().setSubscriptionsPerConnection(redisConfig.getSubscriptionsPerConnection());
      config.useSingleServer().setSubscriptionConnectionPoolSize(redisConfig.getSubscriptionConnectionPoolSize());
    } else {
      config.useSentinelServers().setMasterName(redisConfig.getMasterName());
      for (String sentinelUrl : redisConfig.getSentinelUrls()) {
        config.useSentinelServers().addSentinelAddress(sentinelUrl);
      }
      config.useSentinelServers().setReadMode(ReadMode.valueOf(redisConfig.getReadMode().name()));
    }
    config.setCodec(new RedissonKryoCodec(false));
    config.setNettyThreads(redisConfig.getNettyThreads());
    config.setUseScriptCache(redisConfig.isUseScriptCache());

    try {
      return Redisson.create(config);
    } catch (Exception e) {
      throw new UnexpectedException("Could not create a redis client", e);
    }
  }
}
