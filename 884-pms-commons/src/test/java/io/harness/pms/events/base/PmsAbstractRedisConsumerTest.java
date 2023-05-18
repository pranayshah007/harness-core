/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.events.base;

import static io.harness.rule.OwnerRule.GARVIT;

import static org.jooq.tools.reflect.Reflect.on;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
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

@OwnedBy(HarnessTeam.PIPELINE)
public class PmsAbstractRedisConsumerTest extends PmsCommonsTestBase {
  @Mock private PmsGitSyncHelper pmsGitSyncHelper;
  @Mock private EventMonitoringService eventMonitoringService;
  private NoopPmsEventHandler eventHandler;

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
    NoopPmsRedisConsumer redisConsumer = new NoopPmsRedisConsumer(new NoopRedisConsumer("t", "g"), messageListener);
    redisConsumer.pollAndProcessMessages();
    verify(messageListener, times(1)).handleMessage(any());
  }
}
