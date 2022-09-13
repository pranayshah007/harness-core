/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.metrics.impl;

import static io.harness.metrics.impl.CachedMetricsPublisher.ACTIVE_DELEGATE_PROCESS_CNT;
import static io.harness.metrics.impl.CachedMetricsPublisher.WEBSOCKET_CONNECTIONS_CNT;
import static io.harness.rule.OwnerRule.MARKO;

import static org.mockito.Mockito.verify;

import io.harness.category.element.UnitTests;
import io.harness.metrics.service.api.MetricService;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class CachedMetricsPublisherTest {
  private CachedMetricsPublisher underTest;
  @Mock private MetricService metricService;

  @Before
  public void setup() {
    underTest = new CachedMetricsPublisher(metricService);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testRecordDelegateProcess() {
    underTest.recordDelegateProcess("accountId", "connectionId1");
    underTest.recordDelegateProcess("accountId", "connectionId2");
    underTest.recordMetrics();
    verify(metricService).recordMetric(ACTIVE_DELEGATE_PROCESS_CNT, 2);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testRecordDelegateWebsocketConnection() {
    underTest.recordDelegateWebsocketConnection("accountId", "connectionUuid1");
    underTest.recordDelegateWebsocketConnection("accountId", "connectionUuid1");
    underTest.recordDelegateWebsocketConnection("accountId", "connectionUuid2");
    underTest.recordMetrics();
    verify(metricService).recordMetric(WEBSOCKET_CONNECTIONS_CNT, 2);
  }
}
