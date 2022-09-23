/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.debezium;

import static io.harness.rule.OwnerRule.SHALINI;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.impl.redis.RedisUtils;
import io.harness.redis.RedisConfig;
import io.harness.rule.Owner;
import io.harness.serializer.JsonUtils;

import org.apache.kafka.connect.runtime.WorkerConfig;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import org.redisson.api.RedissonClient;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(HarnessTeam.PIPELINE)
public class RedisOffsetBackingStoreTest extends CategoryTest {
  @Mock WorkerConfig workerConfig;
  @Mock RedissonClient redissonClient;
  @Mock RedisConfig redisConfig;

  @Before
  public void setUp() {
    doReturn("topic").when(workerConfig).getString("offset.storage.topic");
    doReturn("redisLockConfig").when(workerConfig).getString("offset.storage.file.filename");
    MockedStatic<RedisUtils> utilities = Mockito.mockStatic(RedisUtils.class);
    utilities.when(() -> RedisUtils.getClient(any(RedisConfig.class))).thenReturn(redissonClient);
    MockedStatic<JsonUtils> jsonUtilities = Mockito.mockStatic(JsonUtils.class);
    jsonUtilities.when(() -> JsonUtils.asObject("redisLockConfig", RedisConfig.class)).thenReturn(redisConfig);
  }

  @Spy RedisOffsetBackingStore redisOffsetBackingStore = new RedisOffsetBackingStore();
  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testConfigure() {
    assertThatCode(() -> redisOffsetBackingStore.configure(workerConfig)).doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testStart() {
    doNothing().when(redisOffsetBackingStore).load();
    redisOffsetBackingStore.start();
    verify(redisOffsetBackingStore, times(1)).load();
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testStop() {
    assertThatCode(() -> redisOffsetBackingStore.stop()).doesNotThrowAnyException();
  }
}
