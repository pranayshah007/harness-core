/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.debezium;

import static io.harness.rule.OwnerRule.SHALINI;

import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.EventsFrameworkConfiguration;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.impl.redis.RedisProducer;
import io.harness.eventsframework.impl.redis.RedisProducerFactory;
import io.harness.eventsframework.impl.redis.RedisUtils;
import io.harness.redis.RedisConfig;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import org.redisson.api.RedissonClient;

@OwnedBy(HarnessTeam.PIPELINE)

@RunWith(MockitoJUnitRunner.class)
public class DebeziumProducerFactoryTest extends CategoryTest {
  @Mock RedisProducerFactory redisProducerFactory;
  @Mock RedisConfig redisConfig;
  @Mock EventsFrameworkConfiguration configuration;
  @Mock RedisProducer redisProducer;
  @Mock RedissonClient redissonClient;
  @InjectMocks @Spy DebeziumProducerFactory debeziumProducerFactory;

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGetProducer() {
    doReturn(redisConfig).when(configuration).getRedisConfig();
    doReturn(redissonClient).when(debeziumProducerFactory).getRedissonClient(any(RedisConfig.class));
    doReturn(redisProducer)
        .when(redisProducerFactory)
        .createRedisProducer(anyString(), any(RedissonClient.class), anyInt(), anyString(), isNull());
    assertThat(debeziumProducerFactory.get("coll")).isInstanceOf(Producer.class);
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGetRedissonClient() {
    RedisConfig redisConfig = RedisConfig.builder().build();
    MockedStatic<RedisUtils> utilities = Mockito.mockStatic(RedisUtils.class);
    utilities.when(() -> RedisUtils.getClient(any(RedisConfig.class))).thenReturn(redissonClient);
    RedissonClient redissonClient = debeziumProducerFactory.getRedissonClient(redisConfig);
    assertNotNull(redissonClient);
    assertThat(redissonClient).isInstanceOf(RedissonClient.class);
    assertNull(debeziumProducerFactory.getRedissonClient(null));
  }
}
