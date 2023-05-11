/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.billing;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.ccm.commons.beans.InstanceType.K8S_NODE;
import static io.harness.ccm.commons.beans.InstanceType.K8S_POD;
import static io.harness.rule.OwnerRule.SHUBHANSHU;
import static io.harness.rule.OwnerRule.UTSAV;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.ccm.commons.beans.InstanceType;
import io.harness.ccm.commons.beans.Resource;
import io.harness.ccm.commons.entities.batch.InstanceData;
import io.harness.ccm.commons.service.impl.InstanceDataServiceImpl;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;
import io.harness.timescaledb.TimeScaleDBService;

import software.wings.beans.User;
import software.wings.graphql.datafetcher.AbstractDataFetcherTestBase;
import software.wings.graphql.datafetcher.DataFetcherUtils;
import software.wings.graphql.schema.type.aggregation.QLData;
import software.wings.graphql.schema.type.aggregation.QLIdFilter;
import software.wings.graphql.schema.type.aggregation.QLIdOperator;
import software.wings.graphql.schema.type.aggregation.QLSortOrder;
import software.wings.graphql.schema.type.aggregation.QLTimeFilter;
import software.wings.graphql.schema.type.aggregation.QLTimeOperator;
import software.wings.graphql.schema.type.aggregation.billing.QLBillingDataFilter;
import software.wings.graphql.schema.type.aggregation.billing.QLBillingSortCriteria;
import software.wings.graphql.schema.type.aggregation.billing.QLBillingSortType;
import software.wings.graphql.schema.type.aggregation.billing.QLCCMEntityGroupBy;
import software.wings.graphql.schema.type.aggregation.billing.QLCCMGroupBy;
import software.wings.graphql.schema.type.aggregation.billing.QLNodeAndPodDetailsTableData;
import software.wings.graphql.schema.type.aggregation.billing.QLNodeAndPodDetailsTableRow;
import software.wings.security.UserThreadLocal;
import software.wings.service.intfc.ce.CeAccountExpirationChecker;

import com.google.inject.Inject;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.stubbing.Answer;

@TargetModule(HarnessModule._375_CE_GRAPHQL)
@OwnedBy(CE)
public class NodeAndPodDetailsDataFetcherTest extends AbstractDataFetcherTestBase {
  @Mock TimeScaleDBService timeScaleDBService;
  @Mock private DataFetcherUtils utils;
  @Mock QLBillingStatsHelper statsHelper;
  @Mock InstanceDataServiceImpl instanceDataService;
  @Mock CeAccountExpirationChecker accountChecker;
  @Inject @InjectMocks BillingDataHelper billingDataHelper;
  @Inject @InjectMocks NodeAndPodDetailsDataFetcher nodeAndPodDetailsDataFetcher;

  @Mock Statement statement;
  @Mock ResultSet resultSet;

  final int[] count = {0};
  final double[] doubleVal = {0, 0, 0, 0};
  final long currentTime = System.currentTimeMillis();
  final long[] calendar = {currentTime};

  private static final String ACCOUNT_ID = "accountId";
  private static final String CLUSTER_ID = "clusterId";
  private static final String UUID = "UUID";
  private static final String NODE_INSTANCE_ID = "nodeInstanceId";
  private static final String INSTANCE_ID = "instanceId";
  private static final String INSTANCE_NAME = "instanceName";
  private static final Instant USAGE_START_TIME = Instant.now();
  private static final Instant USAGE_STOP_TIME = Instant.now();
  private static final double CPU_UNITS = 4096;
  private static final double MEMORY_MB = 8192;
  private static final String INSTANCE_CATEGORY = "instance_category";
  private static final String OPERATING_SYSTEM = "operating_system";
  private static final String INSTANCE_TYPE_NODE = "K8S_NODE";
  private static final String INSTANCE_TYPE_PODS = "K8S_POD";
  private static final String NAMESPACE = "namespace";
  private static final String WORKLOAD = "workload_name";
  private static final String PARENT_RESOURCE_ID = "parent_resource_id";
  private static final String NODE_POOL_NAME = "node_pool_name";
  private static final Integer LIMIT = 10;
  private static final Integer OFFSET = 0;

  @Before
  public void setup() throws SQLException {
    User user = testUtils.createUser(testUtils.createAccount());
    UserThreadLocal.set(user);
    // Account1
    createAccount(ACCOUNT1_ID, getLicenseInfo());
    createApp(ACCOUNT1_ID, APP1_ID_ACCOUNT1, APP1_ID_ACCOUNT1, TAG_TEAM, TAG_VALUE_TEAM1);
    when(statsHelper.getEntityName(any(), anyString())).thenAnswer(i -> i.getArgument(1, String.class));
    when(instanceDataService.fetchInstanceDataForGivenInstances(ACCOUNT_ID, Collections.singletonList(INSTANCE_ID)))
        .thenReturn(Collections.singletonList(getTestInstanceData(INSTANCE_ID, INSTANCE_ID, K8S_POD)));
    Connection mockConnection = mock(Connection.class);
    Statement mockStatement = mock(Statement.class);
    when(timeScaleDBService.getDBConnection()).thenReturn(mockConnection);
    when(timeScaleDBService.isValid()).thenReturn(true);
    when(mockConnection.createStatement()).thenReturn(mockStatement);
    when(mockStatement.executeQuery(anyString())).thenReturn(resultSet);
    doNothing().when(accountChecker).checkIsCeEnabled(anyString());
    mockResultSet();
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testGetNodeAndPodDetailsWhenDbIsInvalid() {
    when(timeScaleDBService.isValid()).thenReturn(false);
    assertThatThrownBy(()
                           -> nodeAndPodDetailsDataFetcher.fetch(ACCOUNT1_ID, Collections.EMPTY_LIST,
                               Collections.EMPTY_LIST, Collections.EMPTY_LIST, Collections.EMPTY_LIST, LIMIT, OFFSET))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testGetNodeAndPodDetailsWhenQueryThrowsException() throws SQLException {
    when(timeScaleDBService.isValid()).thenReturn(true);
    Statement mockStatement = mock(Statement.class);
    Connection mockConnection = mock(Connection.class);
    when(timeScaleDBService.getDBConnection()).thenReturn(mockConnection);
    when(mockConnection.createStatement()).thenReturn(mockStatement);
    when(mockStatement.executeQuery(anyString())).thenThrow(new SQLException());

    QLData data = nodeAndPodDetailsDataFetcher.fetch(ACCOUNT1_ID, Collections.EMPTY_LIST, Collections.EMPTY_LIST,
        Collections.EMPTY_LIST, Collections.EMPTY_LIST, LIMIT, OFFSET);
    assertThat(data).isNull();
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testFetchMethodForNodeDetails() {
    String[] clusterIdFilterValues = new String[] {CLUSTER_ID};
    when(instanceDataService.fetchInstanceDataForGivenInstances(ACCOUNT_ID, Collections.singletonList(INSTANCE_ID)))
        .thenReturn(Collections.singletonList(getTestInstanceData(INSTANCE_ID, INSTANCE_ID, K8S_NODE)));

    List<QLBillingDataFilter> filters = new ArrayList<>();
    filters.add(makeClusterFilter(clusterIdFilterValues));
    filters.add(makeTimeFilter(0L));
    List<QLCCMGroupBy> groupBy = Arrays.asList(makeNodeEntityGroupBy(), makeClusterEntityGroupBy());
    List<QLBillingSortCriteria> sortCriteria = Arrays.asList(makeAscByAmountSortingCriteria());
    List<QLCCMAggregationFunction> aggregationFunctions = Arrays.asList(makeBillingAmtAggregation(),
        makeIdleCostAggregation(), makeUnallocatedCostAggregation(), makeNetworkCostAggregation());

    QLNodeAndPodDetailsTableData data = (QLNodeAndPodDetailsTableData) nodeAndPodDetailsDataFetcher.fetch(
        ACCOUNT_ID, aggregationFunctions, filters, groupBy, sortCriteria, LIMIT, OFFSET);

    assertThat(data).isNotNull();
    assertThat(data.getData().get(0).getId()).isEqualTo(CLUSTER_ID + ":" + INSTANCE_ID);
    assertThat(data.getData().get(0).getTotalCost()).isEqualTo(10.0);
    assertThat(data.getData().get(0).getIdleCost()).isEqualTo(3.0);
    assertThat(data.getData().get(0).getUnallocatedCost()).isEqualTo(4.0);
    assertThat(data.getData().get(0).getNetworkCost()).isEqualTo(0.0);
    assertThat(data.getData().get(0).getCpuAllocatable()).isEqualTo(CPU_UNITS / 1024);
    assertThat(data.getData().get(0).getMemoryAllocatable()).isEqualTo(MEMORY_MB / 1024);
    assertThat(data.getData().get(0).getMachineType()).isEqualTo("linux");
    assertThat(data.getData().get(0).getInstanceCategory()).isEqualTo("SPOT");
    assertThat(data.getData().get(0).getCreateTime()).isEqualTo(USAGE_START_TIME.toEpochMilli());
    assertThat(data.getData().get(0).getNodePoolName()).isEqualTo("pool-ui-pr-non-preemptible");
    assertThat(data.getData().get(0).getName()).isEqualTo(INSTANCE_NAME);
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testFetchMethodNodeDetailsWithTTLedInstanceData() {
    final String defaultStringValue = "-";
    when(instanceDataService.fetchInstanceDataForGivenInstances(ACCOUNT_ID, Collections.singletonList(INSTANCE_ID)))
        .thenReturn(Collections.emptyList());

    String[] clusterIdFilterValues = new String[] {CLUSTER_ID};

    List<QLBillingDataFilter> filters = new ArrayList<>();
    filters.add(makeClusterFilter(clusterIdFilterValues));
    filters.add(makeTimeFilter(0L));
    List<QLCCMGroupBy> groupBy = Arrays.asList(makeNodeEntityGroupBy(), makeClusterEntityGroupBy());
    List<QLBillingSortCriteria> sortCriteria = Arrays.asList(makeAscByAmountSortingCriteria());
    List<QLCCMAggregationFunction> aggregationFunctions = Arrays.asList(makeBillingAmtAggregation(),
        makeIdleCostAggregation(), makeUnallocatedCostAggregation(), makeNetworkCostAggregation());

    QLNodeAndPodDetailsTableData data = (QLNodeAndPodDetailsTableData) nodeAndPodDetailsDataFetcher.fetch(
        ACCOUNT_ID, aggregationFunctions, filters, groupBy, sortCriteria, LIMIT, OFFSET);

    assertThat(data).isNotNull();
    QLNodeAndPodDetailsTableRow row0 = data.getData().get(0);

    assertThat(row0.getId()).isEqualTo(CLUSTER_ID + ":" + INSTANCE_ID);
    assertThat(row0.getTotalCost()).isEqualTo(10.0);
    assertThat(row0.getIdleCost()).isEqualTo(3.0);
    assertThat(row0.getUnallocatedCost()).isEqualTo(4.0);
    assertThat(row0.getNetworkCost()).isEqualTo(0.0);
    assertThat(row0.getCpuAllocatable()).isEqualTo(-1D);
    assertThat(row0.getMemoryAllocatable()).isEqualTo(-1D);
    assertThat(row0.getMachineType()).isEqualTo(defaultStringValue);
    assertThat(row0.getInstanceCategory()).isEqualTo(defaultStringValue);
    assertThat(row0.getCreateTime()).isEqualTo(0L);
    assertThat(row0.getDeleteTime()).isEqualTo(0L);
    assertThat(row0.getNodePoolName()).isEqualTo(defaultStringValue);
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testFetchMethodPodDetailsWithTTLedInstanceData() {
    final String defaultStringValue = "-";
    when(instanceDataService.fetchInstanceDataForGivenInstances(ACCOUNT_ID, Collections.singletonList(INSTANCE_ID)))
        .thenReturn(Collections.emptyList());

    List<QLBillingDataFilter> filters = new ArrayList<>();
    filters.add(makeInstanceTypeFilter(new String[] {"K8S_POD"}));
    filters.add(makeClusterFilter(new String[] {CLUSTER_ID}));
    filters.add(makeTimeFilter(0L));

    List<QLCCMGroupBy> groupBy = Arrays.asList(makePodEntityGroupBy(), makeClusterEntityGroupBy());
    List<QLBillingSortCriteria> sortCriteria = Arrays.asList(makeAscByAmountSortingCriteria());
    List<QLCCMAggregationFunction> aggregationFunctions = Arrays.asList(makeBillingAmtAggregation(),
        makeIdleCostAggregation(), makeUnallocatedCostAggregation(), makeNetworkCostAggregation());

    QLNodeAndPodDetailsTableData data = (QLNodeAndPodDetailsTableData) nodeAndPodDetailsDataFetcher.fetch(
        ACCOUNT_ID, aggregationFunctions, filters, groupBy, sortCriteria, LIMIT, OFFSET);

    assertThat(data).isNotNull();
    QLNodeAndPodDetailsTableRow row0 = data.getData().get(0);

    assertThat(row0.getId()).isEqualTo(INSTANCE_ID);
    assertThat(row0.getTotalCost()).isEqualTo(10.0);
    assertThat(row0.getIdleCost()).isEqualTo(3.0);
    assertThat(row0.getCpuRequested()).isEqualTo(-1D);
    assertThat(row0.getMemoryRequested()).isEqualTo(-1D);
    assertThat(row0.getWorkload()).isEqualTo(WORKLOAD);
    assertThat(row0.getNamespace()).isEqualTo(NAMESPACE);
    assertThat(row0.getCreateTime()).isEqualTo(0L);
    assertThat(row0.getDeleteTime()).isEqualTo(0L);
    assertThat(row0.getNodePoolName()).isEqualTo(defaultStringValue);
    assertThat(row0.getName()).isEqualTo(INSTANCE_NAME);
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testFetchMethodForPodDetails() {
    when(instanceDataService.fetchInstanceDataForGivenInstances(ACCOUNT_ID, Collections.singletonList(INSTANCE_ID)))
        .thenReturn(Collections.singletonList(getTestInstanceData(INSTANCE_ID, INSTANCE_ID, K8S_POD)));

    String[] clusterIdFilterValues = new String[] {CLUSTER_ID};
    String[] parentInstanceIdFilterValues = new String[] {NODE_INSTANCE_ID};

    List<QLBillingDataFilter> filters = new ArrayList<>();
    filters.add(makeClusterFilter(clusterIdFilterValues));
    filters.add(makeParentInstanceIdFilter(parentInstanceIdFilterValues));
    filters.add(makeTimeFilter(0L));
    List<QLCCMGroupBy> groupBy = Arrays.asList(makePodEntityGroupBy());
    List<QLBillingSortCriteria> sortCriteria = Arrays.asList(makeAscByAmountSortingCriteria());
    List<QLCCMAggregationFunction> aggregationFunctions =
        Arrays.asList(makeBillingAmtAggregation(), makeIdleCostAggregation(), makeUnallocatedCostAggregation());

    QLNodeAndPodDetailsTableData data = (QLNodeAndPodDetailsTableData) nodeAndPodDetailsDataFetcher.fetch(
        ACCOUNT_ID, aggregationFunctions, filters, groupBy, sortCriteria, LIMIT, OFFSET);

    assertThat(data).isNotNull();
    assertThat(data.getData().get(0).getId()).isEqualTo(INSTANCE_ID);
    assertThat(data.getData().get(0).getNamespace()).isEqualTo("namespace");
    assertThat(data.getData().get(0).getWorkload()).isEqualTo(WORKLOAD);
    assertThat(data.getData().get(0).getTotalCost()).isEqualTo(10.0);
    assertThat(data.getData().get(0).getIdleCost()).isEqualTo(3.0);
    assertThat(data.getData().get(0).getUnallocatedCost()).isEqualTo(4.0);
    assertThat(data.getData().get(0).getCpuRequested()).isEqualTo(CPU_UNITS / 1024);
    assertThat(data.getData().get(0).getMemoryRequested()).isEqualTo(MEMORY_MB / 1024);
    assertThat(data.getData().get(0).getCpuRequested()).isEqualTo(CPU_UNITS / 1024);
    assertThat(data.getData().get(0).getCreateTime()).isEqualTo(USAGE_START_TIME.toEpochMilli());
    assertThat(data.getData().get(0).getNodePoolName()).isEqualTo("pool-ui-pr-non-preemptible");
    assertThat(data.getData().get(0).getName()).isEqualTo(INSTANCE_NAME);
  }

  private void mockResultSet() throws SQLException {
    Connection connection = mock(Connection.class);
    statement = mock(Statement.class);
    resultSet = mock(ResultSet.class);
    when(timeScaleDBService.getDBConnection()).thenReturn(connection);
    when(connection.createStatement()).thenReturn(statement);
    when(statement.executeQuery(anyString())).thenReturn(resultSet);

    when(resultSet.getDouble("COST")).thenAnswer((Answer<Double>) invocation -> 10.0 + doubleVal[0]++);
    when(resultSet.getDouble("IDLECOST")).thenAnswer((Answer<Double>) invocation -> 3.0 + doubleVal[1]++);
    when(resultSet.getDouble("ACTUALIDLECOST")).thenAnswer((Answer<Double>) invocation -> 3.0 + doubleVal[1]++);
    when(resultSet.getDouble("UNALLOCATEDCOST")).thenAnswer((Answer<Double>) invocation -> 4.0 + doubleVal[2]++);
    when(resultSet.getDouble("NETWORKCOST")).thenAnswer((Answer<Double>) invocation -> 3.0 + doubleVal[3]++);
    when(resultSet.getString("INSTANCEID")).thenAnswer((Answer<String>) invocation -> INSTANCE_ID);
    when(resultSet.getString("CLUSTERID")).thenAnswer((Answer<String>) invocation -> CLUSTER_ID);
    when(resultSet.getString("CLUSTERNAME")).thenAnswer((Answer<String>) invocation -> CLUSTER1_NAME);
    when(resultSet.getString("INSTANCENAME")).thenAnswer((Answer<String>) invocation -> INSTANCE_NAME);
    when(resultSet.getString("NAMESPACE")).thenAnswer((Answer<String>) invocation -> NAMESPACE);
    when(resultSet.getString("WORKLOADNAME")).thenAnswer((Answer<String>) invocation -> WORKLOAD);

    returnResultSet(1);
  }

  private void returnResultSet(int limit) throws SQLException {
    when(resultSet.next()).then((Answer<Boolean>) invocation -> {
      if (count[0] < limit) {
        count[0]++;
        return true;
      }
      return false;
    });
  }

  private InstanceData getTestInstanceData(String instanceId, String parentInstanceId, InstanceType instanceType) {
    Map<String, String> metaData = new HashMap<>();
    metaData.put(INSTANCE_CATEGORY, "SPOT");
    metaData.put(OPERATING_SYSTEM, "linux");
    metaData.put(NAMESPACE, "namespace");
    metaData.put(WORKLOAD, "workload");
    metaData.put(PARENT_RESOURCE_ID, parentInstanceId);
    metaData.put(NODE_POOL_NAME, "pool-ui-pr-non-preemptible");

    return InstanceData.builder()
        .uuid(UUID)
        .instanceType(instanceType)
        .accountId(ACCOUNT_ID)
        .clusterId(CLUSTER_ID)
        .instanceId(instanceId)
        .instanceName(INSTANCE_NAME)
        .totalResource(Resource.builder().cpuUnits(CPU_UNITS).memoryMb(MEMORY_MB).build())
        .metaData(metaData)
        .usageStartTime(USAGE_START_TIME)
        .usageStopTime(USAGE_STOP_TIME)
        .build();
  }

  private QLCCMGroupBy makeNodeEntityGroupBy() {
    QLCCMEntityGroupBy nodeGroupBy = QLCCMEntityGroupBy.Node;
    return QLCCMGroupBy.builder().entityGroupBy(nodeGroupBy).build();
  }

  private QLCCMGroupBy makePodEntityGroupBy() {
    QLCCMEntityGroupBy podGroupBy = QLCCMEntityGroupBy.Pod;
    return QLCCMGroupBy.builder().entityGroupBy(podGroupBy).build();
  }

  private QLCCMGroupBy makeClusterEntityGroupBy() {
    QLCCMEntityGroupBy clusterGroupBy = QLCCMEntityGroupBy.Cluster;
    return QLCCMGroupBy.builder().entityGroupBy(clusterGroupBy).build();
  }

  private QLBillingDataFilter makeClusterFilter(String[] values) {
    QLIdFilter clusterFilter = QLIdFilter.builder().operator(QLIdOperator.EQUALS).values(values).build();
    return QLBillingDataFilter.builder().cluster(clusterFilter).build();
  }

  private QLBillingDataFilter makeInstanceTypeFilter(String[] values) {
    QLIdFilter clusterFilter = QLIdFilter.builder().operator(QLIdOperator.EQUALS).values(values).build();
    return QLBillingDataFilter.builder().instanceType(clusterFilter).build();
  }

  private QLBillingDataFilter makeParentInstanceIdFilter(String[] values) {
    QLIdFilter parentInstanceIdFilter = QLIdFilter.builder().operator(QLIdOperator.EQUALS).values(values).build();
    return QLBillingDataFilter.builder().parentInstanceId(parentInstanceIdFilter).build();
  }

  private QLBillingDataFilter makeTimeFilter(Long filterTime) {
    QLTimeFilter timeFilter = QLTimeFilter.builder().operator(QLTimeOperator.AFTER).value(filterTime).build();
    return QLBillingDataFilter.builder().startTime(timeFilter).build();
  }

  private QLCCMAggregationFunction makeBillingAmtAggregation() {
    return QLCCMAggregationFunction.builder()
        .operationType(QLCCMAggregateOperation.SUM)
        .columnName("billingamount")
        .build();
  }

  public QLCCMAggregationFunction makeNetworkCostAggregation() {
    return QLCCMAggregationFunction.builder()
        .operationType(QLCCMAggregateOperation.SUM)
        .columnName("networkcost")
        .build();
  }

  private QLCCMAggregationFunction makeIdleCostAggregation() {
    return QLCCMAggregationFunction.builder().operationType(QLCCMAggregateOperation.SUM).columnName("idlecost").build();
  }

  private QLCCMAggregationFunction makeUnallocatedCostAggregation() {
    return QLCCMAggregationFunction.builder()
        .operationType(QLCCMAggregateOperation.SUM)
        .columnName("unallocatedcost")
        .build();
  }

  private QLBillingSortCriteria makeAscByAmountSortingCriteria() {
    return QLBillingSortCriteria.builder().sortOrder(QLSortOrder.ASCENDING).sortType(QLBillingSortType.Amount).build();
  }
}
