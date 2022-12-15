/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import io.harness.eventsframework.EventsFrameworkConfiguration;
import io.harness.lock.DistributedLockImplementation;
import io.harness.redis.RedisConfig;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class DebeziumServiceModuleConfig {
  RedisConfig redisLockConfig;
  DistributedLockImplementation lockImplementation;
  EventsFrameworkConfiguration eventsFrameworkConfiguration;
}
