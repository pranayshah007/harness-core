/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service.stats.statscollector;

import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.InstancesTestBase;
import io.harness.category.element.UnitTests;
import io.harness.dtos.InstanceDTO;
import io.harness.rule.Owner;
import io.harness.service.instance.InstanceService;
import io.harness.service.instancestats.InstanceStatsService;
import io.harness.service.stats.usagemetrics.eventpublisher.UsageMetricsEventPublisher;

import java.time.Instant;
import java.util.Arrays;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class InstanceStatsCollectorImplTest extends InstancesTestBase {
  private final String ACCOUNT_ID = "acc";
  private static final int SYNC_INTERVAL_MINUTES = 30;

  @Mock private InstanceStatsService instanceStatsService;
  @Mock private InstanceService instanceService;
  @Mock private UsageMetricsEventPublisher usageMetricsEventPublisher;
  @InjectMocks InstanceStatsCollectorImpl instanceStatsCollector;

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void createStatsTest() {
    Instant lastSnapshot = Instant.now().minusSeconds((SYNC_INTERVAL_MINUTES + 5) * 60);
    InstanceDTO instanceDTO = InstanceDTO.builder().build();
    when(instanceStatsService.getLastSnapshotTime(ACCOUNT_ID)).thenReturn(lastSnapshot);
    when(instanceService.getActiveInstancesByAccount(eq(ACCOUNT_ID), anyLong())).thenReturn(Arrays.asList(instanceDTO));
    assertThat(instanceStatsCollector.createStats(ACCOUNT_ID)).isTrue();
    verify(instanceService, times(1)).getActiveInstancesByAccount(eq(ACCOUNT_ID), anyLong());
  }
}
