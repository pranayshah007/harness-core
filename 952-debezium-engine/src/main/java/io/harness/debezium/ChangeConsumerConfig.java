/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.debezium;

import io.harness.eventsframework.EventsFrameworkConfiguration;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ChangeConsumerConfig {
  // If add consumer type the we need to introduce fields here
  // And the config should be
  // oneOf { EventsFrameworkConfiguration, HttpConfig }
  ConsumerType consumerType;
  EventsFrameworkConfiguration eventsFrameworkConfiguration;
  // Length of redis stream
  int redisStreamSize;
  // Mode of consumer can be either one of snapshot or streaming
  ConsumerMode consumerMode;
}
