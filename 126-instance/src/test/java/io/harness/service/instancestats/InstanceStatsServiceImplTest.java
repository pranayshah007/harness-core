/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service.instancestats;

import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.harness.InstancesTestBase;
import io.harness.category.element.UnitTests;
import io.harness.models.InstanceStats;
import io.harness.repositories.instancestats.InstanceStatsRepository;
import io.harness.rule.Owner;

import java.sql.Timestamp;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
public class InstanceStatsServiceImplTest extends InstancesTestBase {
  @Mock InstanceStatsRepository instanceStatsRepository;
  @InjectMocks InstanceStatsServiceImpl instanceStatsService;

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void getLastSnapshotTimeTestIfRecordReturnedNotNull() {
    InstanceStats instanceStats = InstanceStats.builder().reportedAt(Timestamp.valueOf("2012-07-07 01:01:01")).build();
    when(instanceStatsRepository.getLatestRecord("accountId")).thenReturn(instanceStats);
    assertThat(instanceStatsService.getLastSnapshotTime("accountId"))
        .isEqualTo(instanceStats.getReportedAt().toInstant());
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void getLastSnapshotTimeTestIfRecordReturnedNull() {
    when(instanceStatsRepository.getLatestRecord("accountId")).thenReturn(null);
    assertThat(instanceStatsService.getLastSnapshotTime("accountId")).isNull();
  }
}
