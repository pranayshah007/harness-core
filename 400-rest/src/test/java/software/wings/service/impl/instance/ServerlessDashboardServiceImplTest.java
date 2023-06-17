/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.instance;

import static io.harness.rule.OwnerRule.ROHIT_KUMAR;

import static software.wings.beans.instance.dashboard.InstanceStatsByArtifact.Builder.anInstanceStatsByArtifact;
import static software.wings.service.impl.instance.ServerlessDashboardServiceImpl.SERVERLESS_FUNCTION_INVOCATION;
import static software.wings.service.impl.instance.ServerlessDashboardServiceImpl.SERVICE_ID;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.beans.PageResponse;
import io.harness.category.element.UnitTests;
import io.harness.exception.NoResultFoundException;
import io.harness.rule.Owner;

import software.wings.beans.EntityType;
import software.wings.beans.SettingAttribute.SettingCategory;
import software.wings.beans.User;
import software.wings.beans.infrastructure.instance.ServerlessInstance;
import software.wings.beans.infrastructure.instance.SyncStatus;
import software.wings.beans.infrastructure.instance.stats.ServerlessInstanceStats;
import software.wings.beans.instance.dashboard.ArtifactSummary;
import software.wings.beans.instance.dashboard.EntitySummary;
import software.wings.beans.instance.dashboard.EntitySummaryStats;
import software.wings.beans.instance.dashboard.InstanceStatsByArtifact;
import software.wings.beans.instance.dashboard.InstanceStatsByEnvironment;
import software.wings.beans.instance.dashboard.InstanceSummaryStats;
import software.wings.beans.instance.dashboard.InstanceSummaryStatsByService;
import software.wings.dl.WingsPersistence;
import software.wings.security.UserPermissionInfo;
import software.wings.security.UserRequestContext;
import software.wings.security.UserThreadLocal;
import software.wings.service.impl.instance.ServerlessDashboardServiceImpl.ArtifactInfo;
import software.wings.service.impl.instance.ServerlessDashboardServiceImpl.EnvType;
import software.wings.service.impl.instance.ServerlessDashboardServiceImpl.FlatEntitySummaryStats;
import software.wings.service.impl.instance.ServerlessDashboardServiceImpl.InstanceCount;
import software.wings.service.impl.instance.ServerlessDashboardServiceImpl.ServiceAggregationInfo;
import software.wings.service.impl.instance.ServerlessDashboardServiceImpl.ServiceAggregationInfo.ID;
import software.wings.service.impl.instance.ServerlessDashboardServiceImpl.ServiceInstanceCount;
import software.wings.service.impl.instance.ServerlessTestHelper.Mocks;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.UserService;
import software.wings.service.intfc.instance.ServerlessInstanceService;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import dev.morphia.aggregation.AggregationPipeline;
import dev.morphia.query.MorphiaIterator;
import dev.morphia.query.Query;
import io.fabric8.utils.Maps;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

public class ServerlessDashboardServiceImplTest extends CategoryTest {
  public static final String ACCOUNTID = "accountid";
  public static final String APPID_1 = "appid1";
  public static final String SERVICEID = "serviceid";
  @Mock private WingsPersistence wingsPersistence;
  @Mock private UserService userService;
  @Mock private AppService appService;
  @Mock private ServerlessInstanceService serverlessInstanceService;
  @Spy InstanceUtils instanceUtil;

  @InjectMocks @Inject @Spy ServerlessDashboardServiceImpl serverlessDashboardService;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void test_getAppInstanceSummaryStats() {
    // setup_getInstanceQueryAtTime();

    setup_getAppInstanceSummaryStats();

    InstanceSummaryStats appInstanceSummaryStats = serverlessDashboardService.getAppInstanceSummaryStats(ACCOUNTID,
        Arrays.asList(APPID_1),
        Arrays.asList(EntityType.SERVICE.name(), SettingCategory.CLOUD_PROVIDER.name(), SERVERLESS_FUNCTION_INVOCATION),
        0);
    assertThat(appInstanceSummaryStats.getTotalCount()).isEqualTo(1);
    assertThat(appInstanceSummaryStats.getCountMap().size()).isEqualTo(3);
  }

  private void setup_getAppInstanceSummaryStats() {
    final Mocks mocks = setup_AggregationPipeline();
    final InstanceCount instanceCount = new InstanceCount();
    instanceCount.setCount(1);
    doReturn(singletonList(instanceCount).iterator())
        .when(mocks.aggregationPipelineMock)
        .aggregate(nullable(Class.class), any());
    doReturn(Collections.emptyList())
        .when(serverlessDashboardService)
        .getEntitySummaryStats(any(), any(), any(), nullable(Query.class), any());
  }

  @Test
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void test_getAppInstanceSummaryStats_1() {
    // setup_getInstanceQueryAtTime();

    setup_getAppInstanceSummaryStats();

    InstanceSummaryStats appInstanceSummaryStats = serverlessDashboardService.getAppInstanceSummaryStats(ACCOUNTID,
        Arrays.asList(APPID_1),
        Arrays.asList(EntityType.SERVICE.name(), SettingCategory.CLOUD_PROVIDER.name(), SERVERLESS_FUNCTION_INVOCATION),
        Instant.now().toEpochMilli());
    assertThat(appInstanceSummaryStats.getTotalCount()).isEqualTo(1);
    assertThat(appInstanceSummaryStats.getCountMap().size()).isEqualTo(3);
  }

  @Test
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void test_getAppInstanceSummaryStats_error() {
    final Mocks mocks = setup_AggregationPipeline();
    doThrow(NoResultFoundException.newBuilder().message("").build())
        .when(serverlessDashboardService)
        .getServerlessInstanceQueryAtTime(anyString(), anyList(), anyLong());

    InstanceSummaryStats appInstanceSummaryStats = serverlessDashboardService.getAppInstanceSummaryStats(ACCOUNTID,
        Arrays.asList(APPID_1),
        Arrays.asList(EntityType.SERVICE.name(), SettingCategory.CLOUD_PROVIDER.name(), SERVERLESS_FUNCTION_INVOCATION),
        0);
    assertThat(appInstanceSummaryStats.getTotalCount()).isEqualTo(0);
    assertThat(Maps.isNullOrEmpty(appInstanceSummaryStats.getCountMap())).isTrue();
  }

  @Test
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void test_getAppInstanceSummaryStats_error1() {
    doThrow(new RuntimeException("error"))
        .when(serverlessDashboardService)
        .getServerlessInstanceQueryAtTime(anyString(), anyList(), anyLong());

    InstanceSummaryStats appInstanceSummaryStats = serverlessDashboardService.getAppInstanceSummaryStats(ACCOUNTID,
        Arrays.asList(APPID_1),
        Arrays.asList(EntityType.SERVICE.name(), SettingCategory.CLOUD_PROVIDER.name(), SERVERLESS_FUNCTION_INVOCATION),
        0);
    assertThat(appInstanceSummaryStats.getTotalCount()).isEqualTo(0);
    assertThat(Maps.isNullOrEmpty(appInstanceSummaryStats.getCountMap())).isTrue();
  }

  private void setup_getInstanceQueryAtTime() {
    doReturn(mock(Query.class))
        .when(serverlessDashboardService)
        .getServerlessInstanceQueryAtTime(anyString(), anyList(), anyLong());

    doReturn(mock(Query.class))
        .when(serverlessDashboardService)
        .getInstanceQueryAtTime(anyString(), anyString(), anyLong());
  }

  @Test
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void test_getAppInstanceSummaryStatsByService() {
    setup_getInstanceQueryAtTime();
    AggregationPipeline aggregationPipelineMOck = setup_AggregationPipeline().getAggregationPipelineMock();
    final ServiceInstanceCount serviceInstanceCount = new ServiceInstanceCount();
    serviceInstanceCount.setServiceInfo(getEntitySummaryList().get(0));
    serviceInstanceCount.setAppInfo(getEntitySummaryList().get(0));
    serviceInstanceCount.setEnvTypeList(singletonList(new EnvType()));
    doReturn(Arrays.asList(serviceInstanceCount).iterator())
        .when(aggregationPipelineMOck)
        .aggregate(any(Class.class), any());

    PageResponse<InstanceSummaryStatsByService> appInstanceSummaryStatsByService =
        serverlessDashboardService.getAppInstanceSummaryStatsByService(ACCOUNTID, Arrays.asList(APPID_1), 0, 0, 10);

    verify(serverlessDashboardService, times(1)).constructInstanceSummaryStatsByService(anyList(), anyInt(), anyInt());
  }

  private Mocks setup_AggregationPipeline() {
    return ServerlessTestHelper.setup_AggregationPipeline(wingsPersistence);
  }

  @Test
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void test_getAppInstanceSummaryStatsByService_error() {
    doThrow(NoResultFoundException.newBuilder().message("").build())
        .when(serverlessDashboardService)
        .getServerlessInstanceQueryAtTime(anyString(), anyList(), anyLong());

    PageResponse<InstanceSummaryStatsByService> appInstanceSummaryStatsByService =
        serverlessDashboardService.getAppInstanceSummaryStatsByService(ACCOUNTID, Arrays.asList(APPID_1), 0, 0, 10);

    assertThat(appInstanceSummaryStatsByService.getTotal()).isEqualTo(0);
  }

  @Test
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void test_getAppInstanceSummaryStatsByService_error1() {
    doThrow(new RuntimeException("error"))
        .when(serverlessDashboardService)
        .getServerlessInstanceQueryAtTime(anyString(), anyList(), anyLong());

    PageResponse<InstanceSummaryStatsByService> appInstanceSummaryStatsByService =
        serverlessDashboardService.getAppInstanceSummaryStatsByService(ACCOUNTID, Arrays.asList(APPID_1), 0, 0, 10);

    assertThat(appInstanceSummaryStatsByService.getTotal()).isEqualTo(0);
  }

  @Test
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void test_getServiceInstances() {
    setup_getInstanceQueryAtTime();
    AggregationPipeline aggregationPipelineMock = setup_AggregationPipeline().getAggregationPipelineMock();
    doReturn(Arrays.asList(new ServiceAggregationInfo()).iterator())
        .when(aggregationPipelineMock)
        .aggregate(any(Class.class), any());

    doReturn(mock(PageResponse.class))
        .when(serverlessDashboardService)
        .constructInstanceStatsForService(eq(SERVICEID), anyList());
    List<InstanceStatsByEnvironment> response = serverlessDashboardService.getServiceInstances(ACCOUNTID, SERVICEID, 0);
    verify(serverlessDashboardService, times(1)).constructInstanceStatsForService(eq(SERVICEID), anyList());
  }

  @Test
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void test_constructInstanceStatsForService() {
    ServiceAggregationInfo serviceAggregationInfo = new ServiceAggregationInfo();
    serviceAggregationInfo.setAppInfo(new EntitySummary("id", null, "Application"));
    serviceAggregationInfo.setInstanceInfoList(getEntitySummaryList());
    serviceAggregationInfo.setInvocationCount(1);
    final ArtifactInfo artifactInfo = new ArtifactInfo();
    serviceAggregationInfo.setArtifactInfo(artifactInfo);
    serviceAggregationInfo.setInfraMappingInfo(getEntitySummaryList().get(0));
    final ID id = new ID();
    id.setEnvId("envid");
    id.setLastArtifactId("lastartifactid");
    serviceAggregationInfo.set_id(id);
    serviceAggregationInfo.setEnvInfo(new EnvInfo());
    List<ServiceAggregationInfo> serviceAggregationInfos = singletonList(serviceAggregationInfo);

    doReturn(InstanceStatsByEnvironment.builder().build())
        .when(serverlessDashboardService)
        .getServerlessInstanceStatsByEnvironment(anyString(), anyString(), any(EnvInfo.class), anyList());

    List<InstanceStatsByEnvironment> instanceStatsByEnvironments =
        serverlessDashboardService.constructInstanceStatsForService(SERVICEID, serviceAggregationInfos);
    assertThat(instanceStatsByEnvironments.size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void test_getInstanceStatsByEnvironment() {
    EnvInfo envInfo = getEnvInfo();

    doReturn(Arrays.asList(SyncStatus.builder()
                               .lastSyncedAt(System.currentTimeMillis())
                               .lastSuccessfullySyncedAt(System.currentTimeMillis())
                               .build()))
        .when(serverlessInstanceService)
        .getSyncStatus(anyString(), anyString(), anyString());

    InstanceStatsByEnvironment instanceStatsByEnvironment =
        serverlessDashboardService.getServerlessInstanceStatsByEnvironment(
            APPID_1, SERVICEID, getEnvInfo(), singletonList(getInstanceStatsByArtifact()));
    assertThat(instanceStatsByEnvironment.getInstanceStatsByArtifactList().size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void test_getInstanceSummaryStatsByService() {
    ServiceInstanceCount serviceInstanceCount = new ServiceInstanceCount();
    serviceInstanceCount.setServiceInfo(getEntitySummaryList().get(0));
    serviceInstanceCount.setAppInfo(getEntitySummaryList().get(0));
    serviceInstanceCount.setEnvTypeList(singletonList(new EnvType()));
    serviceInstanceCount.setCount(1);

    InstanceSummaryStatsByService instanceSummaryStatsByService =
        serverlessDashboardService.getInstanceSummaryStatsByService(serviceInstanceCount);
    assertThat(instanceSummaryStatsByService.getTotalCount()).isEqualTo(1);
  }

  @Test
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void test_getInstanceQuery() {
    setup_AggregationPipeline();

    final User user = new User();
    final UserRequestContext userRequestContext = UserRequestContext.builder().build();
    userRequestContext.setAppIdFilterRequired(true);
    userRequestContext.setAppIds(ImmutableSet.of(APPID_1));
    userRequestContext.setUserPermissionInfo(UserPermissionInfo.builder().build());
    user.setUserRequestContext(userRequestContext);
    doReturn(true).when(userService).isAccountAdmin(anyString());
    doReturn(ImmutableSet.of("app2")).when(serverlessDashboardService).detectDeletedAppIds(anyString(), anyLong());
    UserThreadLocal.set(user);

    final Query<ServerlessInstance> instanceQuery =
        serverlessDashboardService.getInstanceQuery(ACCOUNTID, null, true, 0);
    assertThat(instanceQuery).isNotNull();
  }

  private ServerlessInstance getServerlessInstance() {
    return ServerlessInstance.builder().appId(APPID_1).createdAt(Instant.now().toEpochMilli()).build();
  }

  @Test
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void test_getDeletedAppIds() {
    setup_getDeletedAppIds();

    final Set<String> deletedAppIds = serverlessDashboardService.getDeletedAppIds(
        ACCOUNTID, Instant.now().minusSeconds(100).toEpochMilli(), Instant.now().toEpochMilli());
    assertThat(deletedAppIds).contains(APPID_1);
  }

  private Mocks setup_getDeletedAppIds() {
    final Mocks mocks = setup_AggregationPipeline();
    final Query queryMock = mocks.getQueryMock();
    final ServerlessInstance serverlessInstance = getServerlessInstance();
    doReturn(serverlessInstance).when(queryMock).get();
    final MorphiaIterator morphiaIteratorMock = mock(MorphiaIterator.class);
    doReturn(morphiaIteratorMock).when(queryMock).fetch();
    doReturn(getServerlessInstance()).when(morphiaIteratorMock).next();
    doReturn(true).doReturn(false).when(morphiaIteratorMock).hasNext();

    doReturn(singletonList("appid2")).when(appService).getAppIdsByAccountId(anyString());
    return mocks;
  }

  @Test
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void test_getCreatedTimeOfInstanceAtTimestamp() {
    final Mocks mocks = setup_getDeletedAppIds();
    final Query queryMock = mocks.getQueryMock();

    final long createdTimeOfInstanceAtTimestamp = serverlessDashboardService.getCreatedTimeOfInstanceAtTimestamp(
        ACCOUNTID, Instant.now().minusSeconds(100).toEpochMilli(), queryMock, true);
    assertThat(createdTimeOfInstanceAtTimestamp)
        .isBetween(Instant.now().minusSeconds(3).toEpochMilli(), Instant.now().plusSeconds(1).toEpochMilli());
    verify(queryMock, times(1)).get();
  }

  @Test
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void test_getInstancesForAccount() {
    final Mocks mocks = setup_AggregationPipeline();
    final MorphiaIterator morphiaIteratorMock = mock(MorphiaIterator.class);
    doReturn(morphiaIteratorMock).when(mocks.queryMock).fetch();
    doReturn(getServerlessInstance()).when(morphiaIteratorMock).next();
    doReturn(true).doReturn(false).when(morphiaIteratorMock).hasNext();
    final List<ServerlessInstance> instancesForAccount =
        serverlessDashboardService.getInstancesForAccount(ACCOUNTID, Instant.now().toEpochMilli(), mocks.queryMock);
    assertThat(instancesForAccount.get(0).getAppId()).isEqualTo(APPID_1);
  }

  @Test
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void test_getEntitySummaryStats() {
    final Mocks mocks = setup_AggregationPipeline();
    final AggregationPipeline aggregationPipelineMOck = mocks.aggregationPipelineMock;
    final Query queryMock = mocks.queryMock;
    doReturn(ServerlessInstanceStats.class).when(queryMock).getEntityClass();

    final FlatEntitySummaryStats flatEntitySummaryStats = new FlatEntitySummaryStats();
    flatEntitySummaryStats.setEntityId("entityid");
    flatEntitySummaryStats.setEntityName("entityname");
    doReturn(Arrays.asList(flatEntitySummaryStats).iterator())
        .when(aggregationPipelineMOck)
        .aggregate(any(Class.class), any());

    final List<EntitySummaryStats> summaryStats = serverlessDashboardService.getEntitySummaryStats(
        SERVICE_ID, "serviceName", EntityType.SERVICE.name(), queryMock, 1);

    assertThat(summaryStats.get(0).getEntitySummary().getId()).isEqualTo("entityid");
  }

  @Test
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void test_getInstanceQueryAtTime() {
    Mocks mocks = setup_AggregationPipeline();
    final Query<ServerlessInstance> instanceQueryAtTime =
        serverlessDashboardService.getInstanceQueryAtTime(ACCOUNTID, SERVICEID, Instant.now().toEpochMilli());
    assertThat(instanceQueryAtTime).isEqualTo(mocks.getQueryMock());

    mocks = setup_AggregationPipeline();
    final Query<ServerlessInstance> instanceQueryAtTime1 =
        serverlessDashboardService.getInstanceQueryAtTime(ACCOUNTID, SERVICEID, 0);

    assertThat(instanceQueryAtTime1).isEqualTo(mocks.getQueryMock());
  }

  @Test
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void test_getDeletedAppIds_1() {
    setup_getDeletedAppIds();

    final Set<String> deletedAppIds =
        serverlessDashboardService.detectDeletedAppIds(ACCOUNTID, Instant.now().minusSeconds(100).toEpochMilli());
    assertThat(deletedAppIds).contains(APPID_1);
  }

  @NotNull
  private EnvInfo getEnvInfo() {
    return new EnvInfo();
  }

  private InstanceStatsByArtifact getInstanceStatsByArtifact() {
    return anInstanceStatsByArtifact().withEntitySummary(ArtifactSummary.builder().build()).build();
  }

  private List<EntitySummary> getEntitySummaryList() {
    return Arrays.asList(new EntitySummary("id", null, "APPLICATION"), new EntitySummary("id1", null, "Application"));
  }
}
