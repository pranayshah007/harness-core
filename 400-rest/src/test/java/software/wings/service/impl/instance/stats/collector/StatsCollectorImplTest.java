/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.instance.stats.collector;

import static io.harness.rule.OwnerRule.IVAN;
import static io.harness.rule.OwnerRule.ROHIT_KUMAR;
import static io.harness.rule.OwnerRule.UJJAWAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.event.usagemetrics.UsageMetricsEventPublisher;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;

import software.wings.beans.infrastructure.instance.Instance;
import software.wings.beans.infrastructure.instance.InstanceType;
import software.wings.beans.infrastructure.instance.ServerlessInstance;
import software.wings.beans.infrastructure.instance.stats.InstanceStatsSnapshot;
import software.wings.beans.infrastructure.instance.stats.ServerlessInstanceStats;
import software.wings.service.intfc.instance.DashboardStatisticsService;
import software.wings.service.intfc.instance.ServerlessDashboardService;
import software.wings.service.intfc.instance.stats.InstanceStatService;
import software.wings.service.intfc.instance.stats.ServerlessInstanceStatService;

import com.google.inject.Inject;
import java.time.Instant;
import java.time.Period;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

public class StatsCollectorImplTest extends CategoryTest {
  public static final String ACCOUNTID = "accountid";
  public static final String APPID_1 = "appid1";
  private static final String APP_NAME = "APP_NAME";
  private static final String SERVICE_NAME = "SERVICE_NAME";

  @Mock private ServerlessInstanceStatService serverlessInstanceStatService;
  @Mock private ServerlessDashboardService serverlessDashboardService;
  @Mock private FeatureFlagService featureFlagService;
  @Mock private InstanceStatService statService;
  @Mock private DashboardStatisticsService dashboardStatisticsService;
  @Mock private UsageMetricsEventPublisher usageMetricsEventPublisher;

  @InjectMocks @Inject @Spy StatsCollectorImpl statsCollector;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void alignedWith10thMinute() {
    Instant instant = Instant.parse("2018-12-03T10:10:30.00Z");
    Instant aligned = StatsCollectorImpl.alignedWithMinute(instant, 10);
    assertThat(Instant.parse("2018-12-03T10:10:00.00Z")).isEqualTo(aligned);

    Instant instant2 = Instant.parse("2018-12-03T10:12:30.00Z");
    aligned = StatsCollectorImpl.alignedWithMinute(instant2, 10);
    assertThat(Instant.parse("2018-12-03T10:10:00.00Z")).isEqualTo(aligned);
  }

  @Test
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void test_createServerlessStats() {
    final boolean serverlessStats = setup_createServerlessStats();
    assertThat(serverlessStats).isTrue();
  }

  private boolean setup_createServerlessStats() {
    doReturn(Instant.now().minusSeconds(1000)).when(serverlessInstanceStatService).getLastSnapshotTime(anyString());
    doReturn(Collections.singletonList(getServerlessInstance()))
        .when(serverlessDashboardService)
        .getAppInstancesForAccount(anyString(), anyLong());
    doReturn(true).when(serverlessInstanceStatService).save(any(ServerlessInstanceStats.class));
    return statsCollector.createServerlessStats(ACCOUNTID);
  }

  @Test
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void test_createServerlessStats_no_last_snapshot() {
    doReturn(null).when(serverlessInstanceStatService).getLastSnapshotTime(anyString());
    doReturn(Collections.singletonList(getServerlessInstance()))
        .when(serverlessDashboardService)
        .getAppInstancesForAccount(anyString(), anyLong());
    doReturn(true).when(serverlessInstanceStatService).save(any(ServerlessInstanceStats.class));
    final boolean serverlessStats = statsCollector.createServerlessStats(ACCOUNTID);
    assertThat(serverlessStats).isTrue();
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testCreateStatsAtIfMissing() {
    long snapshotTimestamp = Instant.now().minus(Period.ofDays(30)).toEpochMilli();
    List<Instance> instances = getInstances();

    doReturn(null).when(statService).getLastSnapshotTime(ACCOUNTID);
    doReturn(instances).when(dashboardStatisticsService).getAppInstancesForAccount(anyString(), anyLong());
    doReturn(true).when(statService).save(any(InstanceStatsSnapshot.class));
    doNothing().when(usageMetricsEventPublisher).publishInstanceTimeSeries(anyString(), anyLong(), any());
    ArgumentCaptor<List<Instance>> instancesCaptor = ArgumentCaptor.forClass(List.class);
    final boolean stats = statsCollector.createStatsAtIfMissing(ACCOUNTID, snapshotTimestamp);
    assertThat(stats).isTrue();

    verify(usageMetricsEventPublisher, times(1))
        .publishInstanceTimeSeries(eq(ACCOUNTID), anyLong(), instancesCaptor.capture());
    List<Instance> instancesCaptorValue = instancesCaptor.getValue();
    Instance instance = instancesCaptorValue.get(0);
    assertThat(instance.getAccountId()).isEqualTo(ACCOUNTID);
    assertThat(instance.getAppName()).isEqualTo(APP_NAME);
    assertThat(instance.getServiceName()).isEqualTo(SERVICE_NAME);
    assertThat(instance.getInstanceType()).isEqualTo(InstanceType.AZURE_WEB_APP_INSTANCE);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testCreateStatsAtIfMissingWithExistingSnapshot() {
    long snapshotTimestamp = Instant.now().minus(Period.ofDays(30)).toEpochMilli();
    doReturn(Instant.now()).when(statService).getLastSnapshotTime(ACCOUNTID);

    final boolean stats = statsCollector.createStatsAtIfMissing(ACCOUNTID, snapshotTimestamp);

    assertThat(stats).isTrue();
  }

  private List<Instance> getInstances() {
    return Collections.singletonList(Instance.builder()
                                         .accountId(ACCOUNTID)
                                         .appName(APP_NAME)
                                         .serviceName(SERVICE_NAME)
                                         .instanceType(InstanceType.AZURE_WEB_APP_INSTANCE)
                                         .build());
  }

  private ServerlessInstance getServerlessInstance() {
    return ServerlessInstance.builder()
        .uuid("instanceid")
        .appId(APPID_1)
        .createdAt(Instant.now().toEpochMilli())
        .build();
  }
}
