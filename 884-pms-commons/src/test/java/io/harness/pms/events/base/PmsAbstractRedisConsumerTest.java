/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.events.base;

import static io.harness.rule.OwnerRule.GARVIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jooq.tools.reflect.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.PmsCommonsTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.monitoring.EventMonitoringService;
import io.harness.pms.gitsync.PmsGitSyncBranchContextGuard;
import io.harness.pms.gitsync.PmsGitSyncHelper;
import io.harness.rule.Owner;

import com.google.common.util.concurrent.MoreExecutors;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;

@OwnedBy(HarnessTeam.PIPELINE)
public class PmsAbstractRedisConsumerTest extends PmsCommonsTestBase {
  @Mock private PmsGitSyncHelper pmsGitSyncHelper;
  @Mock private EventMonitoringService eventMonitoringService;
  private NoopPmsEventHandler eventHandler;
  private RedissonClient client;
  RStream<String, String> stream;
  @Before
  public void setup() {
    eventHandler = new NoopPmsEventHandler();
    on(eventHandler).set("pmsGitSyncHelper", pmsGitSyncHelper);
    on(eventHandler).set("eventMonitoringService", eventMonitoringService);
    when(pmsGitSyncHelper.createGitSyncBranchContextGuard(any(), anyBoolean()))
        .thenReturn(new PmsGitSyncBranchContextGuard(null, false));
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldTestHandleMessageWithAmbiance() throws InterruptedException {
    NoopPmsMessageListener messageListener =
        spy(new NoopPmsMessageListener("RANDOM_SERVICE", eventHandler, MoreExecutors.newDirectExecutorService()));
    client = mock(RedissonClient.class);
    stream = mock(RStream.class);
    doReturn(stream).when(client).getStream(any(), any());
    NoopPmsRedisConsumer redisConsumer =
        new NoopPmsRedisConsumer(new NoopRedisConsumer("t", "g", client, 5), messageListener);
    long startTimeMillis = System.currentTimeMillis();
    redisConsumer.pollAndProcessMessages();
    long endTimeMillis = System.currentTimeMillis();
    verify(messageListener, times(2)).handleMessage(any());
    // Since batchSize was 5 and 2 events were read. So thread will sleep for 200 ms.
    assertThat(endTimeMillis - startTimeMillis).isGreaterThanOrEqualTo(200L);

    redisConsumer = new NoopPmsRedisConsumer(new NoopRedisConsumer("t", "g", client, 2), messageListener);
    startTimeMillis = System.currentTimeMillis();
    redisConsumer.pollAndProcessMessages();
    endTimeMillis = System.currentTimeMillis();
    verify(messageListener, times(4)).handleMessage(any());
    // Since batchSize was 2 and only 2 events were read. So thread will not sleep.
    assertThat(endTimeMillis - startTimeMillis).isLessThan(200L);
  }
}
