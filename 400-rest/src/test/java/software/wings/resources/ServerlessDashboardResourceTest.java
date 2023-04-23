/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources;

import static io.harness.rule.OwnerRule.ROHIT_KUMAR;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.beans.PageResponse;
import io.harness.category.element.UnitTests;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;

import software.wings.beans.EntityType;
import software.wings.beans.SettingAttribute.SettingCategory;
import software.wings.beans.infrastructure.instance.InvocationCount.InvocationCountKey;
import software.wings.beans.infrastructure.instance.ServerlessInstance;
import software.wings.beans.instance.dashboard.InstanceSummaryStats;
import software.wings.resources.stats.model.ServerlessInstanceTimeline;
import software.wings.resources.stats.model.TimeRange;
import software.wings.service.impl.instance.InstanceHelper;
import software.wings.service.intfc.instance.ServerlessDashboardService;
import software.wings.service.intfc.instance.stats.ServerlessInstanceStatService;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import java.time.Instant;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

public class ServerlessDashboardResourceTest extends CategoryTest {
  @Mock private ServerlessDashboardService serverlessDashboardService;
  @Mock private InstanceHelper instanceHelper;
  @Mock private ServerlessInstanceStatService serverlessInstanceStatService;

  @InjectMocks @Inject @Spy ServerlessDashboardResource serverlessDashboardResource;

  public static final String ACCOUNTID = "accountid";
  public static final String APPID_1 = "appid1";
  public static final String SERVICEID = "serviceid";

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void test_getAppInstanceSummaryStats() {
    doReturn(mock(InstanceSummaryStats.class))
        .when(serverlessDashboardService)
        .getAppInstanceSummaryStats(anyString(), anyList(), anyList(), anyLong());

    serverlessDashboardResource.getAppInstanceSummaryStats(ACCOUNTID, Collections.singletonList(APPID_1),
        Arrays.asList(EntityType.SERVICE.name(), SettingCategory.CLOUD_PROVIDER.name()), 0l);
    verify(serverlessDashboardService, times(1))
        .getAppInstanceSummaryStats(ArgumentMatchers.eq(ACCOUNTID), eq(Collections.singletonList(APPID_1)),
            eq(Arrays.asList(EntityType.SERVICE.name(), SettingCategory.CLOUD_PROVIDER.name())), eq(0l));
  }

  @Test
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void test_getAppInstanceCountStats() {
    doReturn(mock(PageResponse.class))
        .when(serverlessDashboardService)
        .getAppInstanceSummaryStatsByService(anyString(), anyList(), anyLong(), anyInt(), anyInt());

    serverlessDashboardResource.getAppInstanceCountStats(ACCOUNTID, Collections.singletonList(APPID_1), 0l, 0, 10);
    verify(serverlessDashboardService, times(1))
        .getAppInstanceSummaryStatsByService(ACCOUNTID, Collections.singletonList(APPID_1), 0l, 0, 10);
  }

  @Test
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void test_getServiceInstanceStats() {
    doReturn(mock(PageResponse.class))
        .when(serverlessDashboardService)
        .getServiceInstances(anyString(), anyString(), anyLong());
    serverlessDashboardResource.getServiceInstanceStats(ACCOUNTID, SERVICEID, 0l);
    verify(serverlessDashboardService, times(1)).getServiceInstances(ACCOUNTID, SERVICEID, 0l);
  }

  @Test
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void test_getInstanceDetails() {
    doReturn(mock(ServerlessInstance.class)).when(serverlessDashboardService).getInstanceDetails(anyString());
    serverlessDashboardResource.getInstanceDetails(ACCOUNTID, "instanceid");
    verify(serverlessDashboardService, times(1)).getInstanceDetails("instanceid");
  }

  @Test
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void test_manualSync() {
    doReturn("").when(instanceHelper).manualSync(anyString(), anyString());
    serverlessDashboardResource.manualSyncServerlessInfraMapping(ACCOUNTID, APPID_1, "infraid");
    verify(instanceHelper, times(1)).manualSync(APPID_1, "infraid");
  }

  @Test
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void test_getManualSyncJobStatus() {
    doReturn(Collections.emptyList()).when(instanceHelper).getManualSyncJobsStatus(anyString(), anySet());
    serverlessDashboardResource.getManualSyncJobStatus(ACCOUNTID, ImmutableSet.of("jobid"));
    verify(instanceHelper, times(1)).getManualSyncJobsStatus(ACCOUNTID, ImmutableSet.of("jobid"));
  }

  @Test
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void test_getTimeRanges() {
    Calendar c = Calendar.getInstance();
    c.add(Calendar.MONTH, -1);
    final Instant now_minus_1_month = c.toInstant();
    doReturn(now_minus_1_month).when(serverlessInstanceStatService).getFirstSnapshotTime(anyString());
    final RestResponse<List<TimeRange>> timeRanges = serverlessDashboardResource.getTimeRanges(ACCOUNTID);

    assertThat(timeRanges.getResource().size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void test_getInstanceStatsForGivenTime() {
    doReturn(new ServerlessInstanceTimeline(null))
        .when(serverlessInstanceStatService)
        .aggregate(anyString(), anyLong(), anyLong(), any(InvocationCountKey.class));

    serverlessDashboardResource.getInstanceStatsForGivenTime(ACCOUNTID, 0, 100, InvocationCountKey.LAST_30_DAYS);
    verify(serverlessInstanceStatService, times(1)).aggregate(ACCOUNTID, 0, 100, InvocationCountKey.LAST_30_DAYS);
  }
}
