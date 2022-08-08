/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.debezium;

import static io.harness.rule.OwnerRule.SHALINI;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.redis.RedisConfig;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import org.apache.commons.lang3.concurrent.ConcurrentUtils;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(HarnessTeam.PIPELINE)
@RunWith(MockitoJUnitRunner.class)
public class DebeziumControllerStarterTest extends CategoryTest {
  @Mock ChangeConsumerFactory changeConsumerFactory;
  @Mock DebeziumConfig debeziumConfig;
  @Mock ExecutorService executorService;
  @InjectMocks DebeziumControllerStarter debeziumControllerStarter;
  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testStartDebeziumController() {
    List<String> collections = new ArrayList<>();
    doReturn(collections).when(debeziumConfig).getMonitoredCollections();
    debeziumControllerStarter.startDebeziumController(debeziumConfig, null, null, null);
    verify(executorService, times(0)).submit(any(Runnable.class));
    collections.add("coll1");
    collections.add("coll2");
    doReturn(collections).when(debeziumConfig).getMonitoredCollections();
    doReturn(null).when(changeConsumerFactory).get(anyLong(), anyString(), any(ChangeConsumerConfig.class), anyLong());
    MockedStatic<DebeziumConfiguration> utilities = Mockito.mockStatic(DebeziumConfiguration.class);
    utilities.when(() -> DebeziumConfiguration.getDebeziumProperties(any(DebeziumConfig.class), any(RedisConfig.class)))
        .thenReturn(null);
    when(executorService.submit(any(Callable.class))).thenReturn(ConcurrentUtils.constantFuture(""));
    debeziumControllerStarter.startDebeziumController(debeziumConfig, null, null, null);
    verify(executorService, times(2)).submit(any(Runnable.class));
  }
}