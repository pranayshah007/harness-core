/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.billing;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.rule.OwnerRule.HITESH;
import static io.harness.rule.OwnerRule.ROHIT;
import static io.harness.rule.OwnerRule.SHUBHANSHU;

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
import io.harness.ccm.cluster.dao.K8sWorkloadDao;
import io.harness.ccm.commons.entities.k8s.K8sWorkload;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;
import io.harness.timescaledb.TimeScaleDBService;

import software.wings.beans.User;
import software.wings.graphql.datafetcher.AbstractDataFetcherTestBase;
import software.wings.graphql.datafetcher.DataFetcherUtils;
import software.wings.graphql.schema.type.aggregation.QLAggregationKind;
import software.wings.graphql.schema.type.aggregation.QLBillingStackedTimeSeriesData;
import software.wings.graphql.schema.type.aggregation.QLData;
import software.wings.graphql.schema.type.aggregation.QLIdFilter;
import software.wings.graphql.schema.type.aggregation.QLIdOperator;
import software.wings.graphql.schema.type.aggregation.QLSortOrder;
import software.wings.graphql.schema.type.aggregation.QLTimeFilter;
import software.wings.graphql.schema.type.aggregation.QLTimeOperator;
import software.wings.graphql.schema.type.aggregation.billing.QLBillingDataFilter;
import software.wings.graphql.schema.type.aggregation.billing.QLBillingDataLabelAggregation;
import software.wings.graphql.schema.type.aggregation.billing.QLBillingDataLabelFilter;
import software.wings.graphql.schema.type.aggregation.billing.QLBillingDataTagAggregation;
import software.wings.graphql.schema.type.aggregation.billing.QLBillingDataTagType;
import software.wings.graphql.schema.type.aggregation.billing.QLBillingSortCriteria;
import software.wings.graphql.schema.type.aggregation.billing.QLBillingSortType;
import software.wings.graphql.schema.type.aggregation.billing.QLCCMEntityGroupBy;
import software.wings.graphql.schema.type.aggregation.billing.QLCCMGroupBy;
import software.wings.graphql.schema.type.aggregation.billing.QLCCMTimeSeriesAggregation;
import software.wings.graphql.schema.type.aggregation.billing.QLK8sLabelInput;
import software.wings.graphql.schema.type.aggregation.billing.QLTimeGroupType;
import software.wings.security.UserThreadLocal;
import software.wings.service.intfc.ce.CeAccountExpirationChecker;

import com.google.inject.Inject;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
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
public class BillingTimeSeriesDataFetcherTest extends AbstractDataFetcherTestBase {
  @Mock TimeScaleDBService timeScaleDBService;
  @Mock private DataFetcherUtils utils;
  @Mock QLBillingStatsHelper statsHelper;
  @Mock CeAccountExpirationChecker accountChecker;
  @Inject @InjectMocks BillingStatsTimeSeriesDataFetcher billingStatsTimeSeriesDataFetcher;
  @Inject private K8sWorkloadDao k8sWorkloadDao;

  @Mock Statement statement;
  @Mock ResultSet resultSet;

  final int[] count = {0};
  final double[] doubleVal = {0};
  final long currentTime = System.currentTimeMillis();
  final long[] calendar = {currentTime};
  private List<QLCCMAggregationFunction> aggregationFunction;

  private static String IDLECOST = "idlecost";
  private static String CPUIDLECOST = "cpuidlecost";
  private static String MEMORYIDLECOST = "memoryidlecost";
  private static String MAXCPUUTILIZATION = "maxcpuutilization";
  private static String MAXMEMORYUTILIZATION = "maxmemoryutilization";
  private static String AVGCPUUTILIZATION = "avgcpuutilization";
  private static String AVGMEMORYUTILIZATION = "avgmemoryutilization";
  private static String AGGREGATED_CPU_UTILIZATION_VALUE = "avgcpuutilizationvalue*usagedurationseconds";
  private static String AGGREGATED_MEMORY_UTILIZATION_VALUE = "avgmemoryutilizationvalue*usagedurationseconds";
  private static String AGGREGATED_CPU_LIMIT = "cpulimit*usagedurationseconds";
  private static String AGGREGATED_MEMORY_LIMIT = "memorylimit*usagedurationseconds";
  private static String AGGREGATED_CPU_REQUEST = "cpurequest*usagedurationseconds";
  private static String AGGREGATED_MEMORY_REQUEST = "memoryrequest*usagedurationseconds";

  private static double IDLECOST_VALUE = 2.0;
  private static double CPUIDLECOST_VALUE = 2.0;
  private static double MEMORYIDLECOST_VALUE = 2.0;
  private static double MAXCPUUTILIZATION_VALUE = 0.5;
  private static double MAXMEMORYUTILIZATION_VALUE = 0.5;
  private static double AVGCPUUTILIZATION_VALUE = 0.5;
  private static double AVGMEMORYTILIZATION_VALUE = 0.5;
  private static double AGGREGATEDMEMORYTILIZATION = 86400 * 1024;
  private static double AGGREGATED_CPU_UTILIZATION = 86400 * 1024;
  private static double AGGREGATED_CPU_LIMIT_VALUE = 172800 * 1024;
  private static double AGGREGATED_MEMORY_LIMIT_VALUE = 172800 * 1024;
  private static double AGGREGATED_MEMORY_REQUEST_VALUE = 86400 * 1024;
  private static double AGGREGATED_CPU_REQUEST_VALUE = 86400 * 1024;

  private static Integer LIMIT = Integer.MAX_VALUE - 1;
  private static Integer OFFSET = 0;
  private static boolean INCLUDE_OTHERS = true;

  @Before
  public void setup() throws SQLException {
    User user = testUtils.createUser(testUtils.createAccount());
    UserThreadLocal.set(user);
    // Account1
    createAccount(ACCOUNT1_ID, getLicenseInfo());
    createApp(ACCOUNT1_ID, APP1_ID_ACCOUNT1, APP1_ID_ACCOUNT1, TAG_TEAM, TAG_VALUE_TEAM1);
    Map<String, String> labels = new HashMap<>();
    labels.put(LABEL_NAME, LABEL_VALUE);
    k8sWorkloadDao.save(getTestWorkload(WORKLOAD_NAME_ACCOUNT1, labels));
    aggregationFunction = Collections.singletonList(makeBillingAmtAggregation());
    when(statsHelper.getEntityName(any(), anyString())).thenAnswer(i -> i.getArgument(1, String.class));

    Connection mockConnection = mock(Connection.class);
    Statement mockStatement = mock(Statement.class);
    when(timeScaleDBService.getDBConnection()).thenReturn(mockConnection);
    when(timeScaleDBService.isValid()).thenReturn(true);
    when(mockConnection.createStatement()).thenReturn(mockStatement);
    when(mockStatement.executeQuery(anyString())).thenReturn(resultSet);
    doNothing().when(accountChecker).checkIsCeEnabled(anyString());
    resetValues();
    mockResultSet();
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testGetBillingTrendWhenDbIsInvalid() {
    when(timeScaleDBService.isValid()).thenReturn(false);
    assertThatThrownBy(()
                           -> billingStatsTimeSeriesDataFetcher.fetch(ACCOUNT1_ID, aggregationFunction,
                               Collections.EMPTY_LIST, Collections.EMPTY_LIST, Collections.EMPTY_LIST, LIMIT, OFFSET))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testGetBillingTrendWhenQueryThrowsException() throws SQLException {
    when(timeScaleDBService.isValid()).thenReturn(true);
    Statement mockStatement = mock(Statement.class);
    Connection mockConnection = mock(Connection.class);
    when(timeScaleDBService.getDBConnection()).thenReturn(mockConnection);
    when(mockConnection.createStatement()).thenReturn(mockStatement);
    when(mockStatement.executeQuery(anyString())).thenThrow(new SQLException());

    QLData data = billingStatsTimeSeriesDataFetcher.fetch(ACCOUNT1_ID, aggregationFunction, Collections.EMPTY_LIST,
        Collections.EMPTY_LIST, Collections.EMPTY_LIST, LIMIT, OFFSET);
    assertThat(data).isNull();
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testFetchMethodInBillingTimeSeriesDataFetcher() {
    String[] appIdFilterValues = new String[] {APP1_ID_ACCOUNT1};

    List<QLBillingDataFilter> filters = Arrays.asList(
        makeApplicationFilter(appIdFilterValues), makeTimeFilter(0L), makeNodeFilter(new String[] {NODE1}));
    List<QLCCMGroupBy> groupBy = Arrays.asList(makeStartTimeEntityGroupBy());
    List<QLBillingSortCriteria> sortCriteria = Collections.EMPTY_LIST;

    List<QLCCMAggregationFunction> aggFunction = Collections.singletonList(makeCountAggregation());
    QLBillingStackedTimeSeriesData data = (QLBillingStackedTimeSeriesData) billingStatsTimeSeriesDataFetcher.fetch(
        ACCOUNT1_ID, aggFunction, filters, groupBy, sortCriteria, LIMIT, OFFSET);

    assertThat(aggFunction.get(0).getColumnName()).isEqualTo("instanceId");
    assertThat(aggFunction.get(0).getOperationType()).isEqualTo(QLCCMAggregateOperation.COUNT);
    assertThat(filters.get(0).getApplication().getValues()).isEqualTo(appIdFilterValues);
    assertThat(groupBy.get(0).getEntityGroupBy().getAggregationKind()).isEqualTo(QLAggregationKind.SIMPLE);
    assertThat(data).isNotNull();
    assertThat(data.getData().get(0).getValues().get(0).getValue()).isEqualTo(0);
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void testFetchMethodWithMultipleAggregationInBillingTimeSeriesDataFetcher() {
    String[] appIdFilterValues = new String[] {APP1_ID_ACCOUNT1};
    List<QLCCMAggregationFunction> multiAggregationFunctionForIdleCost =
        Arrays.asList(makeIdleCostAggregation(), makeCpuIdleCostAggregation(), makeMemoryIdleCostAggregation(),
            makeMaxCpuUtilizationAggregation(), makeAvgCpuUtilizationAggregation(),
            makeMaxMemoryUtilizationAggregation(), makeAvgMemoryUtilizationAggregation());
    List<QLBillingDataFilter> filters = Arrays.asList(makeApplicationFilter(appIdFilterValues), makeTimeFilter(0L));
    List<QLCCMGroupBy> groupBy = Arrays.asList(makeApplicationEntityGroupBy(), makeStartTimeEntityGroupBy());
    List<QLBillingSortCriteria> sortCriteria = Arrays.asList();

    QLBillingStackedTimeSeriesData data = (QLBillingStackedTimeSeriesData) billingStatsTimeSeriesDataFetcher.fetch(
        ACCOUNT1_ID, multiAggregationFunctionForIdleCost, filters, groupBy, sortCriteria, LIMIT, OFFSET);

    assertThat(multiAggregationFunctionForIdleCost.get(0).getColumnName()).isEqualTo("idlecost");
    assertThat(multiAggregationFunctionForIdleCost.get(0).getOperationType()).isEqualTo(QLCCMAggregateOperation.SUM);
    assertThat(filters.get(0).getApplication().getValues()).isEqualTo(appIdFilterValues);
    assertThat(groupBy.get(0).getEntityGroupBy().getAggregationKind()).isEqualTo(QLAggregationKind.SIMPLE);
    assertThat(data).isNotNull();
    assertThat(data.getData().get(0).getValues().get(0).getKey().getId()).isEqualTo(APP1_ID_ACCOUNT1);
    assertThat(data.getData().get(0).getValues().get(0).getKey().getType()).isEqualTo("APPID");
    assertThat(data.getData().get(0).getValues().get(0).getValue()).isEqualTo(0);
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testFetchMethodInBillingTimeSeriesDataFetcherForClusterInsight() {
    String[] cloudServiceNameFilterValues = new String[] {CLOUD_SERVICE_NAME_ACCOUNT1};

    List<QLCCMGroupBy> groupBy = Arrays.asList(makeCloudServiceNameEntityGroupBy(), makeStartTimeEntityGroupBy());
    List<QLBillingDataFilter> filters = new ArrayList<>();
    filters.add(makeCloudServiceNameFilter(cloudServiceNameFilterValues));
    List<QLBillingSortCriteria> sortCriteria = Arrays.asList(makeAscByAmountSortingCriteria());

    QLBillingStackedTimeSeriesData data = (QLBillingStackedTimeSeriesData) billingStatsTimeSeriesDataFetcher.fetch(
        ACCOUNT1_ID, aggregationFunction, filters, groupBy, sortCriteria, LIMIT, OFFSET);

    assertThat(aggregationFunction.get(0).getColumnName()).isEqualTo("billingamount");
    assertThat(aggregationFunction.get(0).getOperationType()).isEqualTo(QLCCMAggregateOperation.SUM);
    assertThat(groupBy.get(0).getEntityGroupBy().getAggregationKind()).isEqualTo(QLAggregationKind.SIMPLE);
    assertThat(sortCriteria.get(0).getSortType()).isEqualTo(QLBillingSortType.Amount);
    assertThat(sortCriteria.get(0).getSortOrder()).isEqualTo(QLSortOrder.ASCENDING);
    assertThat(data).isNotNull();
    assertThat(data.getData().get(0).getValues().get(0).getKey().getId()).isEqualTo(CLOUD_SERVICE_NAME_ACCOUNT1);
    assertThat(data.getData().get(0).getValues().get(0).getKey().getType()).isEqualTo("CLOUDSERVICENAME");
    assertThat(data.getData().get(0).getValues().get(0).getValue()).isEqualTo(28.0);
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testFetchMethodInBillingTimeSeriesDataFetcherServiceQuery() {
    String[] serviceValues = new String[] {SERVICE1_ID_APP1_ACCOUNT1};

    List<QLCCMGroupBy> groupBy = Arrays.asList(makeServiceEntityGroupBy(), makeStartTimeEntityGroupBy());
    List<QLBillingDataFilter> filters = new ArrayList<>();
    filters.add(makeServiceFilter(serviceValues));
    List<QLBillingSortCriteria> sortCriteria = Arrays.asList(makeAscByAmountSortingCriteria());

    QLBillingStackedTimeSeriesData data = (QLBillingStackedTimeSeriesData) billingStatsTimeSeriesDataFetcher.fetch(
        ACCOUNT1_ID, aggregationFunction, filters, groupBy, sortCriteria, LIMIT, OFFSET);

    assertThat(aggregationFunction.get(0).getColumnName()).isEqualTo("billingamount");
    assertThat(aggregationFunction.get(0).getOperationType()).isEqualTo(QLCCMAggregateOperation.SUM);
    assertThat(groupBy.get(0).getEntityGroupBy().getAggregationKind()).isEqualTo(QLAggregationKind.SIMPLE);
    assertThat(sortCriteria.get(0).getSortType()).isEqualTo(QLBillingSortType.Amount);
    assertThat(sortCriteria.get(0).getSortOrder()).isEqualTo(QLSortOrder.ASCENDING);
    assertThat(data).isNotNull();
    assertThat(data.getData().get(0).getValues().get(0).getKey().getId()).isEqualTo(SERVICE1_ID_APP1_ACCOUNT1);
    assertThat(data.getData().get(0).getValues().get(0).getKey().getType()).isEqualTo("SERVICEID");
    assertThat(data.getData().get(0).getValues().get(0).getValue()).isEqualTo(23.0);
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testFetchMethodInBillingTimeSeriesDataFetcherEnvironmentQuery() {
    String[] environmentValues = new String[] {SERVICE1_ID_APP1_ACCOUNT1};

    List<QLCCMGroupBy> groupBy = Arrays.asList(makeEnvironmentEntityGroupBy(), makeStartTimeEntityGroupBy());
    List<QLBillingDataFilter> filters = Collections.singletonList(makeEnvironmentFilter(environmentValues));
    List<QLBillingSortCriteria> sortCriteria = Arrays.asList(makeAscByAmountSortingCriteria());

    QLBillingStackedTimeSeriesData data = (QLBillingStackedTimeSeriesData) billingStatsTimeSeriesDataFetcher.fetch(
        ACCOUNT1_ID, aggregationFunction, filters, groupBy, sortCriteria, LIMIT, OFFSET);

    assertThat(aggregationFunction.get(0).getColumnName()).isEqualTo("billingamount");
    assertThat(aggregationFunction.get(0).getOperationType()).isEqualTo(QLCCMAggregateOperation.SUM);
    assertThat(groupBy.get(0).getEntityGroupBy().getAggregationKind()).isEqualTo(QLAggregationKind.SIMPLE);
    assertThat(sortCriteria.get(0).getSortType()).isEqualTo(QLBillingSortType.Amount);
    assertThat(sortCriteria.get(0).getSortOrder()).isEqualTo(QLSortOrder.ASCENDING);
    assertThat(data).isNotNull();
    assertThat(data.getData().get(0).getValues().get(0).getKey().getId()).isEqualTo(ENV1_ID_APP1_ACCOUNT1);
    assertThat(data.getData().get(0).getValues().get(0).getKey().getType()).isEqualTo("ENVID");
    assertThat(data.getData().get(0).getValues().get(0).getValue()).isEqualTo(23.0);
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testFetchMethodInBillingTimeSeriesDataFetcherClusterQuery() {
    String[] clusterValues = new String[] {CLUSTER1_ID};
    String[] workloadNameValues = new String[] {WORKLOAD_NAME_ACCOUNT1};

    List<QLCCMGroupBy> groupBy = Arrays.asList(makeClusterEntityGroupBy(), makeStartTimeEntityGroupBy());
    List<QLBillingDataFilter> filters = new ArrayList<>();
    filters.add(makeClusterFilter(clusterValues));
    filters.add(makeWorkloadNameFilter(workloadNameValues));
    List<QLBillingSortCriteria> sortCriteria = Arrays.asList(makeAscByAmountSortingCriteria());

    QLBillingStackedTimeSeriesData data = (QLBillingStackedTimeSeriesData) billingStatsTimeSeriesDataFetcher.fetch(
        ACCOUNT1_ID, aggregationFunction, filters, groupBy, sortCriteria, LIMIT, OFFSET);

    assertThat(aggregationFunction.get(0).getColumnName()).isEqualTo("billingamount");
    assertThat(aggregationFunction.get(0).getOperationType()).isEqualTo(QLCCMAggregateOperation.SUM);
    assertThat(groupBy.get(0).getEntityGroupBy().getAggregationKind()).isEqualTo(QLAggregationKind.SIMPLE);
    assertThat(sortCriteria.get(0).getSortType()).isEqualTo(QLBillingSortType.Amount);
    assertThat(sortCriteria.get(0).getSortOrder()).isEqualTo(QLSortOrder.ASCENDING);
    assertThat(data).isNotNull();
    assertThat(data.getData().get(0).getValues().get(0).getKey().getId()).isEqualTo(CLUSTER1_ID);
    assertThat(data.getData().get(0).getValues().get(0).getKey().getType()).isEqualTo("CLUSTERID");
    assertThat(data.getData().get(0).getValues().get(0).getValue()).isEqualTo(23.0);
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testNoneGroupByInClusterViewCharts() {
    List<QLCCMGroupBy> groupBy = Collections.singletonList(makeStartTimeEntityGroupBy());
    List<QLBillingDataFilter> filters = new ArrayList<>();
    filters.add(makeNotNullClusterFilter());
    List<QLBillingSortCriteria> sortCriteria = Arrays.asList(makeAscByAmountSortingCriteria());

    QLBillingStackedTimeSeriesData data = (QLBillingStackedTimeSeriesData) billingStatsTimeSeriesDataFetcher.fetch(
        ACCOUNT1_ID, aggregationFunction, filters, groupBy, sortCriteria, LIMIT, OFFSET);

    assertThat(data).isNotNull();
    assertThat(data.getData().get(0).getValues().get(0).getValue()).isEqualTo(23.0);
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testFetchMethodInBillingTimeSeriesDataFetcherInstanceIdQuery() {
    String[] instanceIdValues = new String[] {INSTANCE1_SERVICE1_ENV1_APP1_ACCOUNT1};

    List<QLCCMGroupBy> groupBy = Arrays.asList(makeInstanceIdEntityGroupBy(), makeStartTimeEntityGroupBy());
    List<QLBillingDataFilter> filters = new ArrayList<>();
    filters.add(makeTaskIdFilter(instanceIdValues));
    List<QLBillingSortCriteria> sortCriteria = Arrays.asList(makeAscByAmountSortingCriteria());

    QLBillingStackedTimeSeriesData data = (QLBillingStackedTimeSeriesData) billingStatsTimeSeriesDataFetcher.fetch(
        ACCOUNT1_ID, aggregationFunction, filters, groupBy, sortCriteria, LIMIT, OFFSET);

    assertThat(aggregationFunction.get(0).getColumnName()).isEqualTo("billingamount");
    assertThat(aggregationFunction.get(0).getOperationType()).isEqualTo(QLCCMAggregateOperation.SUM);
    assertThat(groupBy.get(0).getEntityGroupBy().getAggregationKind()).isEqualTo(QLAggregationKind.SIMPLE);
    assertThat(sortCriteria.get(0).getSortType()).isEqualTo(QLBillingSortType.Amount);
    assertThat(sortCriteria.get(0).getSortOrder()).isEqualTo(QLSortOrder.ASCENDING);
    assertThat(data).isNotNull();
    assertThat(data.getData().get(0).getValues().get(0).getKey().getId())
        .isEqualTo(INSTANCE1_SERVICE1_ENV1_APP1_ACCOUNT1);
    assertThat(data.getData().get(0).getValues().get(0).getKey().getType()).isEqualTo("TASKID");
    assertThat(data.getData().get(0).getValues().get(0).getValue()).isEqualTo(28.0);
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testFetchMethodInBillingTimeSeriesDataFetcherLaunchTypeQuery() {
    String[] launchTypeValues = new String[] {LAUNCH_TYPE1};

    List<QLCCMGroupBy> groupBy = Arrays.asList(makeLaunchTypeEntityGroupBy(), makeStartTimeEntityGroupBy());
    List<QLBillingDataFilter> filters = new ArrayList<>();
    filters.add(makeLaunchTypeFilter(launchTypeValues));
    List<QLBillingSortCriteria> sortCriteria = Arrays.asList(makeAscByAmountSortingCriteria());

    QLBillingStackedTimeSeriesData data = (QLBillingStackedTimeSeriesData) billingStatsTimeSeriesDataFetcher.fetch(
        ACCOUNT1_ID, aggregationFunction, filters, groupBy, sortCriteria, LIMIT, OFFSET);

    assertThat(aggregationFunction.get(0).getColumnName()).isEqualTo("billingamount");
    assertThat(aggregationFunction.get(0).getOperationType()).isEqualTo(QLCCMAggregateOperation.SUM);
    assertThat(groupBy.get(0).getEntityGroupBy().getAggregationKind()).isEqualTo(QLAggregationKind.SIMPLE);
    assertThat(sortCriteria.get(0).getSortType()).isEqualTo(QLBillingSortType.Amount);
    assertThat(sortCriteria.get(0).getSortOrder()).isEqualTo(QLSortOrder.ASCENDING);
    assertThat(data).isNotNull();
    assertThat(data.getData().get(0).getValues().get(0).getKey().getId()).isEqualTo(LAUNCH_TYPE1);
    assertThat(data.getData().get(0).getValues().get(0).getKey().getType()).isEqualTo("LAUNCHTYPE");
    assertThat(data.getData().get(0).getValues().get(0).getValue()).isEqualTo(28.0);
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testFetchMethodInBillingTimeSeriesDataFetcherRegionQuery() {
    String[] instanceTypeValues = new String[] {INSTANCE_TYPE1};

    List<QLCCMGroupBy> groupBy = Arrays.asList(makeRegionEntityGroupBy(), makeStartTimeEntityGroupBy());
    List<QLBillingDataFilter> filters = Collections.singletonList(makeInstanceTypeFilter(instanceTypeValues));
    List<QLBillingSortCriteria> sortCriteria = Arrays.asList(makeAscByAmountSortingCriteria());

    QLBillingStackedTimeSeriesData data = (QLBillingStackedTimeSeriesData) billingStatsTimeSeriesDataFetcher.fetch(
        ACCOUNT1_ID, aggregationFunction, filters, groupBy, sortCriteria, LIMIT, OFFSET);

    assertThat(aggregationFunction.get(0).getColumnName()).isEqualTo("billingamount");
    assertThat(aggregationFunction.get(0).getOperationType()).isEqualTo(QLCCMAggregateOperation.SUM);
    assertThat(groupBy.get(0).getEntityGroupBy().getAggregationKind()).isEqualTo(QLAggregationKind.SIMPLE);
    assertThat(sortCriteria.get(0).getSortType()).isEqualTo(QLBillingSortType.Amount);
    assertThat(sortCriteria.get(0).getSortOrder()).isEqualTo(QLSortOrder.ASCENDING);
    assertThat(data).isNotNull();
    assertThat(data.getData().get(0).getValues().get(0).getKey().getId()).isEqualTo(REGION1);
    assertThat(data.getData().get(0).getValues().get(0).getKey().getType()).isEqualTo("REGION");
    assertThat(data.getData().get(0).getValues().get(0).getValue()).isEqualTo(23.0);
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testFetchMethodInBillingTimeSeriesDataFetcherNamespaceQuery() {
    String[] namespaceValues = new String[] {NAMESPACE1};

    List<QLCCMGroupBy> groupBy = Arrays.asList(makeNamespaceEntityGroupBy(), makeStartTimeEntityGroupBy());
    List<QLBillingDataFilter> filters = new ArrayList<>();
    filters.add(makeNamespaceFilter(namespaceValues));
    List<QLBillingSortCriteria> sortCriteria = Arrays.asList(makeAscByAmountSortingCriteria());

    QLBillingStackedTimeSeriesData data = (QLBillingStackedTimeSeriesData) billingStatsTimeSeriesDataFetcher.fetch(
        ACCOUNT1_ID, aggregationFunction, filters, groupBy, sortCriteria, LIMIT, OFFSET);

    assertThat(aggregationFunction.get(0).getColumnName()).isEqualTo("billingamount");
    assertThat(aggregationFunction.get(0).getOperationType()).isEqualTo(QLCCMAggregateOperation.SUM);
    assertThat(groupBy.get(0).getEntityGroupBy().getAggregationKind()).isEqualTo(QLAggregationKind.SIMPLE);
    assertThat(sortCriteria.get(0).getSortType()).isEqualTo(QLBillingSortType.Amount);
    assertThat(sortCriteria.get(0).getSortOrder()).isEqualTo(QLSortOrder.ASCENDING);
    assertThat(data).isNotNull();
    assertThat(data.getData().get(0).getValues().get(0).getKey().getId()).isEqualTo(NAMESPACE1);
    assertThat(data.getData().get(0).getValues().get(0).getKey().getType()).isEqualTo("NAMESPACE");
    assertThat(data.getData().get(0).getValues().get(0).getValue()).isEqualTo(28.0);

    data = (QLBillingStackedTimeSeriesData) billingStatsTimeSeriesDataFetcher.postFetch(
        ACCOUNT1_ID, groupBy, aggregationFunction, sortCriteria, data, 10, INCLUDE_OTHERS);
    assertThat(data).isNotNull();
    assertThat(data.getData().get(0).getValues().get(0).getKey().getId()).isEqualTo(NAMESPACE1);
    assertThat(data.getData().get(0).getValues().get(0).getKey().getType()).isEqualTo("NAMESPACE");
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testFetchMethodInBillingTimeSeriesDataFetcherClusterTypeQuery() {
    String[] clusterIdValues = new String[] {CLUSTER1_ID};

    List<QLCCMGroupBy> groupBy = Arrays.asList(makeClusterTypeEntityGroupBy(), makeStartTimeEntityGroupBy());
    List<QLBillingDataFilter> filters = Collections.singletonList(makeNamespaceFilter(clusterIdValues));
    List<QLBillingSortCriteria> sortCriteria = Arrays.asList(makeAscByAmountSortingCriteria());

    QLBillingStackedTimeSeriesData data = (QLBillingStackedTimeSeriesData) billingStatsTimeSeriesDataFetcher.fetch(
        ACCOUNT1_ID, aggregationFunction, filters, groupBy, sortCriteria, LIMIT, OFFSET);

    assertThat(aggregationFunction.get(0).getColumnName()).isEqualTo("billingamount");
    assertThat(aggregationFunction.get(0).getOperationType()).isEqualTo(QLCCMAggregateOperation.SUM);
    assertThat(groupBy.get(0).getEntityGroupBy().getAggregationKind()).isEqualTo(QLAggregationKind.SIMPLE);
    assertThat(sortCriteria.get(0).getSortType()).isEqualTo(QLBillingSortType.Amount);
    assertThat(sortCriteria.get(0).getSortOrder()).isEqualTo(QLSortOrder.ASCENDING);
    assertThat(data).isNotNull();
    assertThat(data.getData().get(0).getValues().get(0).getKey().getId()).isEqualTo(CLUSTER_TYPE1);
    assertThat(data.getData().get(0).getValues().get(0).getKey().getType()).isEqualTo("CLUSTERTYPE");
    assertThat(data.getData().get(0).getValues().get(0).getValue()).isEqualTo(23.0);
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testFetchMethodInBillingTimeSeriesDataFetcherWithMonthlyTimeAggregation() {
    String[] clusterValues = new String[] {CLUSTER1_ID};

    List<QLCCMGroupBy> groupBy = Arrays.asList(makeClusterEntityGroupBy(), makeMonthlyTimeAggregationGroupBy());
    List<QLBillingDataFilter> filters = new ArrayList<>();
    filters.add(makeClusterFilter(clusterValues));
    List<QLBillingSortCriteria> sortCriteria = Arrays.asList(makeAscByAmountSortingCriteria());

    QLBillingStackedTimeSeriesData data = (QLBillingStackedTimeSeriesData) billingStatsTimeSeriesDataFetcher.fetch(
        ACCOUNT1_ID, aggregationFunction, filters, groupBy, sortCriteria, LIMIT, OFFSET);

    assertThat(aggregationFunction.get(0).getColumnName()).isEqualTo("billingamount");
    assertThat(aggregationFunction.get(0).getOperationType()).isEqualTo(QLCCMAggregateOperation.SUM);
    assertThat(groupBy.get(0).getEntityGroupBy().getAggregationKind()).isEqualTo(QLAggregationKind.SIMPLE);
    assertThat(sortCriteria.get(0).getSortType()).isEqualTo(QLBillingSortType.Amount);
    assertThat(sortCriteria.get(0).getSortOrder()).isEqualTo(QLSortOrder.ASCENDING);
    assertThat(data).isNotNull();
    assertThat(data.getData().get(0).getValues().get(0).getKey().getId()).isEqualTo(CLUSTER1_ID);
    assertThat(data.getData().get(0).getValues().get(0).getKey().getType()).isEqualTo("CLUSTERID");
    assertThat(data.getData().get(0).getValues().get(0).getValue()).isEqualTo(23.0);
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testFetchMethodInBillingTimeSeriesDataFetcherWithDailyTimeAggregation() {
    String[] clusterValues = new String[] {CLUSTER1_ID};

    List<QLCCMGroupBy> groupBy = Arrays.asList(makeClusterEntityGroupBy(), makeDailyTimeAggregationGroupBy());
    List<QLBillingDataFilter> filters = new ArrayList<>();
    filters.add(makeClusterFilter(clusterValues));
    List<QLBillingSortCriteria> sortCriteria = Arrays.asList(makeAscByAmountSortingCriteria());

    QLBillingStackedTimeSeriesData data = (QLBillingStackedTimeSeriesData) billingStatsTimeSeriesDataFetcher.fetch(
        ACCOUNT1_ID, aggregationFunction, filters, groupBy, sortCriteria, LIMIT, OFFSET);

    assertThat(aggregationFunction.get(0).getColumnName()).isEqualTo("billingamount");
    assertThat(aggregationFunction.get(0).getOperationType()).isEqualTo(QLCCMAggregateOperation.SUM);
    assertThat(groupBy.get(0).getEntityGroupBy().getAggregationKind()).isEqualTo(QLAggregationKind.SIMPLE);
    assertThat(sortCriteria.get(0).getSortType()).isEqualTo(QLBillingSortType.Amount);
    assertThat(sortCriteria.get(0).getSortOrder()).isEqualTo(QLSortOrder.ASCENDING);
    assertThat(data).isNotNull();
    assertThat(data.getData().get(0).getValues().get(0).getKey().getId()).isEqualTo(CLUSTER1_ID);
    assertThat(data.getData().get(0).getValues().get(0).getKey().getType()).isEqualTo("CLUSTERID");
    assertThat(data.getData().get(0).getValues().get(0).getValue()).isEqualTo(23.0);
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testFetchAndPostFetchMethodsInBillingTimeSeriesDataFetcherWithTagAggregation() {
    List<QLCCMGroupBy> groupBy = Arrays.asList(makeApplicationTagGroupBy(TAG_TEAM));
    List<QLBillingDataFilter> filters = new ArrayList<>();
    List<QLBillingSortCriteria> sortCriteria = Arrays.asList(makeAscByAmountSortingCriteria());

    QLBillingStackedTimeSeriesData data = (QLBillingStackedTimeSeriesData) billingStatsTimeSeriesDataFetcher.fetch(
        ACCOUNT1_ID, aggregationFunction, filters, groupBy, sortCriteria, LIMIT, OFFSET);

    assertThat(aggregationFunction.get(0).getColumnName()).isEqualTo("billingamount");
    assertThat(aggregationFunction.get(0).getOperationType()).isEqualTo(QLCCMAggregateOperation.SUM);
    assertThat(sortCriteria.get(0).getSortType()).isEqualTo(QLBillingSortType.Amount);
    assertThat(sortCriteria.get(0).getSortOrder()).isEqualTo(QLSortOrder.ASCENDING);
    assertThat(data).isNotNull();
    assertThat(data.getData().get(0).getValues().get(0).getKey().getId()).isEqualTo(APP1_ID_ACCOUNT1);
    assertThat(data.getData().get(0).getValues().get(0).getKey().getType()).isEqualTo("APPID");
    assertThat(data.getData().get(0).getValues().get(0).getValue()).isEqualTo(23.0);

    data = (QLBillingStackedTimeSeriesData) billingStatsTimeSeriesDataFetcher.postFetch(
        ACCOUNT1_ID, groupBy, aggregationFunction, sortCriteria, data, LIMIT, INCLUDE_OTHERS);
    assertThat(data).isNotNull();
    assertThat(data.getData().get(0).getValues().get(0).getKey().getId()).isEqualTo(TAG_TEAM1);
    assertThat(data.getData().get(0).getValues().get(0).getKey().getType()).isEqualTo("TAG");
    assertThat(data.getData().get(0).getValues().get(0).getValue()).isEqualTo(125.0);

    // checking post fetch in case of no tag group by
    data = (QLBillingStackedTimeSeriesData) billingStatsTimeSeriesDataFetcher.postFetch(
        ACCOUNT1_ID, Collections.emptyList(), aggregationFunction, sortCriteria, data, LIMIT, INCLUDE_OTHERS);
    assertThat(data).isNotNull();
    assertThat(data.getData().get(0).getValues().get(0).getKey().getId()).isEqualTo(TAG_TEAM1);
    assertThat(data.getData().get(0).getValues().get(0).getKey().getType()).isEqualTo("TAG");
    assertThat(data.getData().get(0).getValues().get(0).getValue()).isEqualTo(125.0);

    // checking post fetch when data is empty
    QLBillingStackedTimeSeriesData emptyData =
        QLBillingStackedTimeSeriesData.builder().data(Collections.emptyList()).build();
    emptyData = (QLBillingStackedTimeSeriesData) billingStatsTimeSeriesDataFetcher.postFetch(
        ACCOUNT1_ID, groupBy, aggregationFunction, sortCriteria, emptyData, LIMIT, INCLUDE_OTHERS);
    assertThat(emptyData).isNotNull();
    assertThat(emptyData.getData()).hasSize(0);
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testFetchAndPostFetchMethodsInBillingTimeSeriesDataFetcherWithInvalidTagAggregation() {
    List<QLCCMGroupBy> groupBy = Arrays.asList(makeApplicationTagGroupBy(TAG_TEAM + "invalid"));
    List<QLBillingDataFilter> filters = new ArrayList<>();
    List<QLBillingSortCriteria> sortCriteria = Arrays.asList(makeAscByAmountSortingCriteria());

    QLBillingStackedTimeSeriesData data = (QLBillingStackedTimeSeriesData) billingStatsTimeSeriesDataFetcher.fetch(
        ACCOUNT1_ID, aggregationFunction, filters, groupBy, sortCriteria, LIMIT, OFFSET);

    assertThat(aggregationFunction.get(0).getColumnName()).isEqualTo("billingamount");
    assertThat(aggregationFunction.get(0).getOperationType()).isEqualTo(QLCCMAggregateOperation.SUM);
    assertThat(groupBy.get(0).getTagAggregation().getTagName()).isEqualTo(TAG_TEAM + "invalid");
    assertThat(sortCriteria.get(0).getSortType()).isEqualTo(QLBillingSortType.Amount);
    assertThat(sortCriteria.get(0).getSortOrder()).isEqualTo(QLSortOrder.ASCENDING);
    assertThat(data).isNotNull();
    assertThat(data.getData().get(0).getValues().get(0).getKey().getId()).isEqualTo(APP1_ID_ACCOUNT1);
    assertThat(data.getData().get(0).getValues().get(0).getKey().getType()).isEqualTo("APPID");
    assertThat(data.getData().get(0).getValues().get(0).getValue()).isEqualTo(23.0);

    data = (QLBillingStackedTimeSeriesData) billingStatsTimeSeriesDataFetcher.postFetch(
        ACCOUNT1_ID, groupBy, aggregationFunction, sortCriteria, data, LIMIT, INCLUDE_OTHERS);
    assertThat(data).isNotNull();
    assertThat(data.getData().get(0).getValues()).hasSize(1);
    assertThat(data.getData().get(0).getValues().get(0).getKey().getName())
        .isEqualTo(BillingStatsDefaultKeys.DEFAULT_TAG);
    assertThat(data.getData().get(0).getValues().get(0).getKey().getId())
        .isEqualTo(BillingStatsDefaultKeys.DEFAULT_TAG);
    assertThat(data.getData().get(0).getValues().get(0).getValue()).isEqualTo(125.0);
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testFetchMethodInBillingTimeSeriesDataFetcherWithLabelFilter() {
    String[] clusterValues = new String[] {CLUSTER1_ID};
    List<QLCCMGroupBy> groupBy = Arrays.asList(makeWorkloadNameEntityGroupBy());
    List<QLBillingDataFilter> filters = new ArrayList<>();
    filters.add(makeClusterFilter(clusterValues));
    filters.add(makeLabelFilter(LABEL_NAME, LABEL_VALUE));
    List<QLBillingSortCriteria> sortCriteria = Arrays.asList(makeAscByAmountSortingCriteria());

    QLBillingStackedTimeSeriesData data = (QLBillingStackedTimeSeriesData) billingStatsTimeSeriesDataFetcher.fetch(
        ACCOUNT1_ID, aggregationFunction, filters, groupBy, sortCriteria, LIMIT, OFFSET);

    assertThat(aggregationFunction.get(0).getColumnName()).isEqualTo("billingamount");
    assertThat(aggregationFunction.get(0).getOperationType()).isEqualTo(QLCCMAggregateOperation.SUM);
    assertThat(sortCriteria.get(0).getSortType()).isEqualTo(QLBillingSortType.Amount);
    assertThat(sortCriteria.get(0).getSortOrder()).isEqualTo(QLSortOrder.ASCENDING);
    assertThat(data).isNotNull();
    assertThat(data.getData().get(0).getValues().get(0).getKey().getId())
        .isEqualTo(NAMESPACE1 + ":" + WORKLOAD_NAME_ACCOUNT1);
    assertThat(data.getData().get(0).getValues().get(0).getKey().getType()).isEqualTo("WORKLOADNAME");
    assertThat(data.getData().get(0).getValues().get(0).getValue()).isEqualTo(28.0);
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testFetchMethodInBillingTimeSeriesDataFetcherGroupByNoneQuery() {
    List<QLCCMGroupBy> groupBy = Arrays.asList(makeDailyTimeAggregationGroupBy());
    List<QLBillingSortCriteria> sortCriteria = Arrays.asList(makeAscByAmountSortingCriteria());

    QLBillingStackedTimeSeriesData data = (QLBillingStackedTimeSeriesData) billingStatsTimeSeriesDataFetcher.fetch(
        ACCOUNT1_ID, aggregationFunction, Collections.emptyList(), groupBy, sortCriteria, LIMIT, OFFSET);

    assertThat(aggregationFunction.get(0).getColumnName()).isEqualTo("billingamount");
    assertThat(aggregationFunction.get(0).getOperationType()).isEqualTo(QLCCMAggregateOperation.SUM);
    assertThat(sortCriteria.get(0).getSortType()).isEqualTo(QLBillingSortType.Amount);
    assertThat(sortCriteria.get(0).getSortOrder()).isEqualTo(QLSortOrder.ASCENDING);
    assertThat(data).isNotNull();
    assertThat(data.getData().get(0).getValues().get(0).getKey()).isEqualTo(null);
    assertThat(data.getData().get(0).getValues().get(0).getValue()).isEqualTo(23.0);
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testFetchMethodInBillingTimeSeriesDataFetcherWorkloadDetailsQuery() {
    String[] clusterIdValues = new String[] {CLUSTER1_ID};
    String[] namespaceValues = new String[] {NAMESPACE1};
    String[] workloadNameValues = new String[] {WORKLOAD_NAME_ACCOUNT1};

    List<QLCCMGroupBy> groupBy = Arrays.asList(makeWorkloadNameEntityGroupBy(), makeDailyTimeAggregationGroupBy());
    List<QLBillingDataFilter> filters = new ArrayList<>();
    filters.add(makeClusterFilter(clusterIdValues));
    filters.add(makeNamespaceFilter(namespaceValues));
    filters.add(makeWorkloadNameFilter(workloadNameValues));
    List<QLCCMAggregationFunction> aggregations = new ArrayList<>();
    aggregations.add(makeAvgCpuUtilizationValueAggregation());
    aggregations.add(makeAvgMemoryUtilizationValueAggregation());
    aggregations.add(makeCpuLimitAggregation());
    aggregations.add(makeMemoryLimitAggregation());
    aggregations.add(makeCpuRequestAggregation());
    aggregations.add(makeMemoryRequestAggregation());

    QLBillingStackedTimeSeriesData data = (QLBillingStackedTimeSeriesData) billingStatsTimeSeriesDataFetcher.fetch(
        ACCOUNT1_ID, aggregations, filters, groupBy, Collections.emptyList(), LIMIT, OFFSET);

    assertThat(data).isNotNull();
    assertThat(data.getCpuLimit()).isNotNull();
    assertThat(data.getCpuLimit().get(0).getValues().get(0).getKey().getName()).isEqualTo("LIMIT");
    assertThat(data.getCpuLimit().get(0).getValues().get(0).getValue()).isEqualTo(2.0);
    assertThat(data.getMemoryLimit()).isNotNull();
    assertThat(data.getMemoryLimit().get(0).getValues().get(0).getKey().getName()).isEqualTo("LIMIT");
    assertThat(data.getMemoryLimit().get(0).getValues().get(0).getValue()).isEqualTo(2.0);
    assertThat(data.getCpuRequest()).isNotNull();
    assertThat(data.getCpuRequest().get(0).getValues().get(0).getKey().getName()).isEqualTo("REQUEST");
    assertThat(data.getCpuRequest().get(0).getValues().get(0).getValue()).isEqualTo(1.0);
    assertThat(data.getMemoryRequest()).isNotNull();
    assertThat(data.getMemoryRequest().get(0).getValues().get(0).getKey().getName()).isEqualTo("REQUEST");
    assertThat(data.getMemoryRequest().get(0).getValues().get(0).getValue()).isEqualTo(1.0);
    assertThat(data.getCpuUtilValues()).isNotNull();
    assertThat(data.getCpuUtilValues().get(0).getValues().get(0).getKey().getName()).isEqualTo("AVG");
    assertThat(data.getCpuUtilValues().get(0).getValues().get(0).getValue()).isEqualTo(1.0);
    assertThat(data.getMemoryUtilValues()).isNotNull();
    assertThat(data.getMemoryUtilValues().get(0).getValues().get(0).getKey().getName()).isEqualTo("AVG");
    assertThat(data.getMemoryUtilValues().get(0).getValues().get(0).getValue()).isEqualTo(1.0);
  }

  private QLCCMAggregationFunction makeBillingAmtAggregation() {
    return QLCCMAggregationFunction.builder()
        .operationType(QLCCMAggregateOperation.SUM)
        .columnName("billingamount")
        .build();
  }

  private QLCCMAggregationFunction makeCountAggregation() {
    return QLCCMAggregationFunction.builder()
        .operationType(QLCCMAggregateOperation.COUNT)
        .columnName("instanceId")
        .build();
  }

  private QLCCMAggregationFunction makeIdleCostAggregation() {
    return QLCCMAggregationFunction.builder().operationType(QLCCMAggregateOperation.SUM).columnName(IDLECOST).build();
  }

  private QLCCMAggregationFunction makeCpuIdleCostAggregation() {
    return QLCCMAggregationFunction.builder()
        .operationType(QLCCMAggregateOperation.SUM)
        .columnName(CPUIDLECOST)
        .build();
  }

  private QLCCMAggregationFunction makeMemoryIdleCostAggregation() {
    return QLCCMAggregationFunction.builder()
        .operationType(QLCCMAggregateOperation.SUM)
        .columnName(MEMORYIDLECOST)
        .build();
  }

  private QLCCMAggregationFunction makeMaxCpuUtilizationAggregation() {
    return QLCCMAggregationFunction.builder()
        .operationType(QLCCMAggregateOperation.MAX)
        .columnName(MAXCPUUTILIZATION)
        .build();
  }

  private QLCCMAggregationFunction makeAvgCpuUtilizationAggregation() {
    return QLCCMAggregationFunction.builder()
        .operationType(QLCCMAggregateOperation.AVG)
        .columnName(AVGCPUUTILIZATION)
        .build();
  }

  private QLCCMAggregationFunction makeMaxMemoryUtilizationAggregation() {
    return QLCCMAggregationFunction.builder()
        .operationType(QLCCMAggregateOperation.MAX)
        .columnName(MAXMEMORYUTILIZATION)
        .build();
  }

  private QLCCMAggregationFunction makeAvgMemoryUtilizationAggregation() {
    return QLCCMAggregationFunction.builder()
        .operationType(QLCCMAggregateOperation.AVG)
        .columnName(AVGMEMORYUTILIZATION)
        .build();
  }

  private QLCCMAggregationFunction makeAvgMemoryUtilizationValueAggregation() {
    return QLCCMAggregationFunction.builder()
        .operationType(QLCCMAggregateOperation.SUM)
        .columnName(AGGREGATED_MEMORY_UTILIZATION_VALUE)
        .build();
  }

  private QLCCMAggregationFunction makeAvgCpuUtilizationValueAggregation() {
    return QLCCMAggregationFunction.builder()
        .operationType(QLCCMAggregateOperation.SUM)
        .columnName(AGGREGATED_CPU_UTILIZATION_VALUE)
        .build();
  }

  private QLCCMAggregationFunction makeCpuLimitAggregation() {
    return QLCCMAggregationFunction.builder()
        .operationType(QLCCMAggregateOperation.SUM)
        .columnName(AGGREGATED_CPU_LIMIT)
        .build();
  }

  private QLCCMAggregationFunction makeMemoryLimitAggregation() {
    return QLCCMAggregationFunction.builder()
        .operationType(QLCCMAggregateOperation.SUM)
        .columnName(AGGREGATED_MEMORY_LIMIT)
        .build();
  }

  private QLCCMAggregationFunction makeCpuRequestAggregation() {
    return QLCCMAggregationFunction.builder()
        .operationType(QLCCMAggregateOperation.SUM)
        .columnName(AGGREGATED_CPU_REQUEST)
        .build();
  }

  private QLCCMAggregationFunction makeMemoryRequestAggregation() {
    return QLCCMAggregationFunction.builder()
        .operationType(QLCCMAggregateOperation.SUM)
        .columnName(AGGREGATED_MEMORY_REQUEST)
        .build();
  }

  private QLBillingSortCriteria makeAscByAmountSortingCriteria() {
    return QLBillingSortCriteria.builder().sortOrder(QLSortOrder.ASCENDING).sortType(QLBillingSortType.Amount).build();
  }

  private QLCCMGroupBy makeMonthlyTimeAggregationGroupBy() {
    QLCCMTimeSeriesAggregation timeSeriesAggregation =
        QLCCMTimeSeriesAggregation.builder().timeGroupType(QLTimeGroupType.MONTH).build();
    return QLCCMGroupBy.builder().timeAggregation(timeSeriesAggregation).build();
  }

  private QLCCMGroupBy makeDailyTimeAggregationGroupBy() {
    QLCCMTimeSeriesAggregation timeSeriesAggregation =
        QLCCMTimeSeriesAggregation.builder().timeGroupType(QLTimeGroupType.DAY).build();
    return QLCCMGroupBy.builder().timeAggregation(timeSeriesAggregation).build();
  }

  private QLCCMGroupBy makeStartTimeEntityGroupBy() {
    QLCCMEntityGroupBy startTimeGroupBy = QLCCMEntityGroupBy.StartTime;
    return QLCCMGroupBy.builder().entityGroupBy(startTimeGroupBy).build();
  }

  private QLCCMGroupBy makeApplicationEntityGroupBy() {
    QLCCMEntityGroupBy applicationGroupBy = QLCCMEntityGroupBy.Application;
    return QLCCMGroupBy.builder().entityGroupBy(applicationGroupBy).build();
  }

  private QLCCMGroupBy makeCloudServiceNameEntityGroupBy() {
    QLCCMEntityGroupBy cloudServiceNameGroupBy = QLCCMEntityGroupBy.CloudServiceName;
    return QLCCMGroupBy.builder().entityGroupBy(cloudServiceNameGroupBy).build();
  }

  private QLCCMGroupBy makeServiceEntityGroupBy() {
    QLCCMEntityGroupBy serviceGroupBy = QLCCMEntityGroupBy.Service;
    return QLCCMGroupBy.builder().entityGroupBy(serviceGroupBy).build();
  }

  private QLCCMGroupBy makeRegionEntityGroupBy() {
    QLCCMEntityGroupBy regionGroupBy = QLCCMEntityGroupBy.Region;
    return QLCCMGroupBy.builder().entityGroupBy(regionGroupBy).build();
  }

  private QLCCMGroupBy makeClusterEntityGroupBy() {
    QLCCMEntityGroupBy clusterGroupBy = QLCCMEntityGroupBy.Cluster;
    return QLCCMGroupBy.builder().entityGroupBy(clusterGroupBy).build();
  }

  private QLCCMGroupBy makeInstanceIdEntityGroupBy() {
    QLCCMEntityGroupBy instanceIdGroupBy = QLCCMEntityGroupBy.TaskId;
    return QLCCMGroupBy.builder().entityGroupBy(instanceIdGroupBy).build();
  }

  private QLCCMGroupBy makeEnvironmentEntityGroupBy() {
    QLCCMEntityGroupBy environmentGroupBy = QLCCMEntityGroupBy.Environment;
    return QLCCMGroupBy.builder().entityGroupBy(environmentGroupBy).build();
  }

  private QLCCMGroupBy makeLaunchTypeEntityGroupBy() {
    QLCCMEntityGroupBy launchTypeGroupBy = QLCCMEntityGroupBy.LaunchType;
    return QLCCMGroupBy.builder().entityGroupBy(launchTypeGroupBy).build();
  }

  private QLCCMGroupBy makeNamespaceEntityGroupBy() {
    QLCCMEntityGroupBy namespaceGroupBy = QLCCMEntityGroupBy.Namespace;
    return QLCCMGroupBy.builder().entityGroupBy(namespaceGroupBy).build();
  }

  private QLCCMGroupBy makeClusterTypeEntityGroupBy() {
    QLCCMEntityGroupBy clusterTypeGroupBy = QLCCMEntityGroupBy.ClusterType;
    return QLCCMGroupBy.builder().entityGroupBy(clusterTypeGroupBy).build();
  }

  private QLCCMGroupBy makeApplicationTagGroupBy(String tagName) {
    QLBillingDataTagAggregation tagAggregation =
        QLBillingDataTagAggregation.builder().entityType(QLBillingDataTagType.APPLICATION).tagName(tagName).build();
    return QLCCMGroupBy.builder().tagAggregation(tagAggregation).build();
  }

  private QLCCMGroupBy makeWorkloadNameEntityGroupBy() {
    QLCCMEntityGroupBy workloadNameGroupBy = QLCCMEntityGroupBy.WorkloadName;
    return QLCCMGroupBy.builder().entityGroupBy(workloadNameGroupBy).build();
  }

  private QLCCMGroupBy makeLabelGroupBy(String labelName) {
    return QLCCMGroupBy.builder()
        .labelAggregation(QLBillingDataLabelAggregation.builder().name(labelName).build())
        .build();
  }

  private QLBillingDataFilter makeApplicationFilter(String[] values) {
    QLIdFilter applicationFilter = QLIdFilter.builder().operator(QLIdOperator.EQUALS).values(values).build();
    return QLBillingDataFilter.builder().application(applicationFilter).build();
  }

  private QLBillingDataFilter makeTaskIdFilter(String[] values) {
    QLIdFilter instanceIdFilter = QLIdFilter.builder().operator(QLIdOperator.EQUALS).values(values).build();
    return QLBillingDataFilter.builder().taskId(instanceIdFilter).build();
  }

  private QLBillingDataFilter makeLaunchTypeFilter(String[] values) {
    QLIdFilter launchTypeFilter = QLIdFilter.builder().operator(QLIdOperator.EQUALS).values(values).build();
    return QLBillingDataFilter.builder().launchType(launchTypeFilter).build();
  }

  private QLBillingDataFilter makeTimeFilter(Long filterTime) {
    QLTimeFilter timeFilter = QLTimeFilter.builder().operator(QLTimeOperator.AFTER).value(filterTime).build();
    return QLBillingDataFilter.builder().startTime(timeFilter).build();
  }

  private QLBillingDataFilter makeNodeFilter(String[] values) {
    QLIdFilter podFilter = QLIdFilter.builder().operator(QLIdOperator.NOT_NULL).values(values).build();
    return QLBillingDataFilter.builder().nodeInstanceId(podFilter).build();
  }

  private QLBillingDataFilter makeServiceFilter(String[] values) {
    QLIdFilter serviceFilter = QLIdFilter.builder().operator(QLIdOperator.EQUALS).values(values).build();
    return QLBillingDataFilter.builder().service(serviceFilter).build();
  }

  private QLBillingDataFilter makeClusterFilter(String[] values) {
    QLIdFilter clusterFilter = QLIdFilter.builder().operator(QLIdOperator.EQUALS).values(values).build();
    return QLBillingDataFilter.builder().cluster(clusterFilter).build();
  }

  private QLBillingDataFilter makeNotNullClusterFilter() {
    String[] values = new String[] {""};
    QLIdFilter clusterFilter = QLIdFilter.builder().operator(QLIdOperator.NOT_NULL).values(values).build();
    return QLBillingDataFilter.builder().cluster(clusterFilter).build();
  }

  private QLBillingDataFilter makeEnvironmentFilter(String[] values) {
    QLIdFilter environmentFilter = QLIdFilter.builder().operator(QLIdOperator.EQUALS).values(values).build();
    return QLBillingDataFilter.builder().service(environmentFilter).build();
  }

  private QLBillingDataFilter makeCloudServiceNameFilter(String[] values) {
    QLIdFilter cloudServiceNameFilter = QLIdFilter.builder().operator(QLIdOperator.EQUALS).values(values).build();
    return QLBillingDataFilter.builder().cloudServiceName(cloudServiceNameFilter).build();
  }

  private QLBillingDataFilter makeInstanceTypeFilter(String[] values) {
    QLIdFilter instanceTypeFilter = QLIdFilter.builder().operator(QLIdOperator.EQUALS).values(values).build();
    return QLBillingDataFilter.builder().instanceType(instanceTypeFilter).build();
  }

  private QLBillingDataFilter makeNamespaceFilter(String[] values) {
    QLIdFilter namespaceFilter = QLIdFilter.builder().operator(QLIdOperator.EQUALS).values(values).build();
    return QLBillingDataFilter.builder().namespace(namespaceFilter).build();
  }

  private QLBillingDataFilter makeWorkloadNameFilter(String[] values) {
    QLIdFilter workloadNameFilter = QLIdFilter.builder().operator(QLIdOperator.EQUALS).values(values).build();
    return QLBillingDataFilter.builder().workloadName(workloadNameFilter).build();
  }

  private QLBillingDataFilter makeLabelFilter(String labelName, String labelValue) {
    QLK8sLabelInput input = QLK8sLabelInput.builder().name(labelName).values(Arrays.asList(labelValue)).build();
    return QLBillingDataFilter.builder()
        .label(QLBillingDataLabelFilter.builder().labels(Arrays.asList(input)).build())
        .build();
  }

  private void mockResultSet() throws SQLException {
    Connection connection = mock(Connection.class);
    statement = mock(Statement.class);
    resultSet = mock(ResultSet.class);
    when(timeScaleDBService.getDBConnection()).thenReturn(connection);
    when(connection.createStatement()).thenReturn(statement);
    when(statement.executeQuery(anyString())).thenReturn(resultSet);

    when(resultSet.getDouble(anyString())).thenAnswer((Answer<Double>) invocation -> 10.0 + doubleVal[0]++);

    when(resultSet.getString("APPID")).thenAnswer((Answer<String>) invocation -> APP1_ID_ACCOUNT1);
    when(resultSet.getString("CLOUDSERVICENAME"))
        .thenAnswer((Answer<String>) invocation -> CLOUD_SERVICE_NAME_ACCOUNT1);
    when(resultSet.getString("SERVICEID")).thenAnswer((Answer<String>) invocation -> SERVICE1_ID_APP1_ACCOUNT1);
    when(resultSet.getString("CLUSTERID")).thenAnswer((Answer<String>) invocation -> CLUSTER1_ID);
    when(resultSet.getString("WORKLOADNAME")).thenAnswer((Answer<String>) invocation -> WORKLOAD_NAME_ACCOUNT1);
    when(resultSet.getString("REGION")).thenAnswer((Answer<String>) invocation -> REGION1);
    when(resultSet.getString("LAUNCHTYPE")).thenAnswer((Answer<String>) invocation -> LAUNCH_TYPE1);
    when(resultSet.getString("ENVID")).thenAnswer((Answer<String>) invocation -> ENV1_ID_APP1_ACCOUNT1);
    when(resultSet.getString("NAMESPACE")).thenAnswer((Answer<String>) invocation -> NAMESPACE1);
    when(resultSet.getString("REGION")).thenAnswer((Answer<String>) invocation -> REGION1);
    when(resultSet.getString("CLUSTERTYPE")).thenAnswer((Answer<String>) invocation -> CLUSTER_TYPE1);
    when(resultSet.getString("IDLECOST")).thenAnswer((Answer<String>) invocation -> IDLECOST);
    when(resultSet.getDouble("IDLECOST")).thenAnswer((Answer<Double>) invocation -> IDLECOST_VALUE);
    when(resultSet.getDouble("CPUIDLECOST")).thenAnswer((Answer<Double>) invocation -> CPUIDLECOST_VALUE);
    when(resultSet.getDouble("MEMORYIDLECOST")).thenAnswer((Answer<Double>) invocation -> MEMORYIDLECOST_VALUE);
    when(resultSet.getDouble("MAXCPUUTILIZATION")).thenAnswer((Answer<Double>) invocation -> MAXCPUUTILIZATION_VALUE);
    when(resultSet.getDouble("MAXMEMORYUTILIZATION"))
        .thenAnswer((Answer<Double>) invocation -> MAXMEMORYUTILIZATION_VALUE);
    when(resultSet.getDouble("AVGCPUUTILIZATION")).thenAnswer((Answer<Double>) invocation -> AVGCPUUTILIZATION_VALUE);
    when(resultSet.getDouble("AVGMEMORYUTILIZATION"))
        .thenAnswer((Answer<Double>) invocation -> AVGMEMORYTILIZATION_VALUE);
    when(resultSet.getDouble("AGGREGATEDCPUUTILIZATIONVALUE"))
        .thenAnswer((Answer<Double>) invocation -> AGGREGATED_CPU_UTILIZATION);
    when(resultSet.getDouble("AGGREGATEDMEMORYUTILIZATIONVALUE"))
        .thenAnswer((Answer<Double>) invocation -> AGGREGATEDMEMORYTILIZATION);
    when(resultSet.getDouble("AGGREGATEDCPULIMIT"))
        .thenAnswer((Answer<Double>) invocation -> AGGREGATED_CPU_LIMIT_VALUE);
    when(resultSet.getDouble("AGGREGATEDMEMORYLIMIT"))
        .thenAnswer((Answer<Double>) invocation -> AGGREGATED_MEMORY_LIMIT_VALUE);
    when(resultSet.getDouble("AGGREGATEDCPUREQUEST"))
        .thenAnswer((Answer<Double>) invocation -> AGGREGATED_CPU_REQUEST_VALUE);
    when(resultSet.getDouble("AGGREGATEDMEMORYREQUEST"))
        .thenAnswer((Answer<Double>) invocation -> AGGREGATED_MEMORY_REQUEST_VALUE);

    when(resultSet.getString("TASKID"))
        .thenAnswer((Answer<String>) invocation -> INSTANCE1_SERVICE1_ENV1_APP1_ACCOUNT1);
    when(resultSet.getTimestamp("STARTTIME", utils.getDefaultCalendar())).thenAnswer((Answer<Timestamp>) invocation -> {
      calendar[0] = calendar[0] + 3600000;
      return new Timestamp(calendar[0]);
    });
    when(resultSet.getTimestamp("STARTTIMEBUCKET", utils.getDefaultCalendar()))
        .thenAnswer((Answer<Timestamp>) invocation -> {
          calendar[0] = calendar[0] + 3600000;
          return new Timestamp(calendar[0]);
        });
    returnResultSet(5);
  }

  private void returnResultSet(int limit) throws SQLException {
    when(resultSet.next()).then((Answer<Boolean>) invocation -> {
      if (count[0] < limit) {
        count[0]++;
        return true;
      }
      count[0] = 0;
      return false;
    });
  }

  private void resetValues() {
    count[0] = 0;
    doubleVal[0] = 0;
  }

  private K8sWorkload getTestWorkload(String workloadName, Map<String, String> labels) {
    return K8sWorkload.builder()
        .accountId(ACCOUNT1_ID)
        .clusterId(CLUSTER1_ID)
        .settingId(SETTING_ID1)
        .kind("WORKLOAD_KIND")
        .labels(labels)
        .name(workloadName)
        .namespace(NAMESPACE1)
        .uid("UID")
        .uuid("UUID")
        .build();
  }
}
