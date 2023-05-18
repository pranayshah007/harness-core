/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.billing;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.rule.OwnerRule.ROHIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;
import io.harness.timescaledb.TimeScaleDBService;

import software.wings.graphql.datafetcher.AbstractDataFetcherTestBase;
import software.wings.graphql.datafetcher.billing.BillingDataQueryMetadata.BillingDataMetaDataFields;
import software.wings.graphql.schema.type.aggregation.QLData;
import software.wings.graphql.schema.type.aggregation.QLSortOrder;
import software.wings.graphql.schema.type.aggregation.QLTimeFilter;
import software.wings.graphql.schema.type.aggregation.QLTimeOperator;
import software.wings.graphql.schema.type.aggregation.billing.QLBillingDataFilter;
import software.wings.graphql.schema.type.aggregation.billing.QLBillingSortCriteria;
import software.wings.graphql.schema.type.aggregation.billing.QLBillingSortType;
import software.wings.graphql.schema.type.aggregation.billing.QLCCMEntityGroupBy;
import software.wings.graphql.schema.type.aggregation.billing.QLCCMGroupBy;
import software.wings.graphql.schema.type.aggregation.billing.QLSunburstChartData;
import software.wings.graphql.schema.type.aggregation.billing.QLSunburstGridDataPoint;
import software.wings.service.intfc.ce.CeAccountExpirationChecker;

import com.google.inject.Inject;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.stubbing.Answer;

@TargetModule(HarnessModule._375_CE_GRAPHQL)
@OwnedBy(CE)
public class SunburstChartStatsDataFetcherTest extends AbstractDataFetcherTestBase {
  @Inject @InjectMocks SunburstChartStatsDataFetcher sunburstChartStatsDataFetcher;
  @Mock BillingDataQueryBuilder billingDataQueryBuilder;
  @Mock TimeScaleDBService timeScaleDBService;
  @Mock BillingDataHelper billingDataHelper;
  @Mock Statement statement;
  @Mock ResultSet resultSet;
  @Mock CeAccountExpirationChecker accountChecker;

  private final Double TOTAL_COST = 100.0;
  private final Double IDLE_COST = 30.0;
  private final Double UNALLOCATED_COST = 20.0;
  private final Double TREND = 10.0;
  private final int EFF_SCORE = 76;
  private long END_TIME = 1571509800000l;
  private long START_TIME = 1570645800000l;
  final int[] count = {0};
  final int[] iterableCount = {0};

  private final String CLUSTER_TYPE = "K8S";
  private static String IDLE_COST_COLUMN = "idlecost";
  private static String TOTAL_COST_COLUMN = "billingamount";
  private static String UNALLOCATED_COST_COLUMN = "unallocatedcost";
  private static String QUERY = "query";

  private static Integer LIMIT = 1;
  private static Integer OFFSET = 0;

  List<QLCCMAggregationFunction> aggregateFunction;
  List<QLBillingDataFilter> filters;
  List<QLBillingSortCriteria> sort;

  @Before
  public void setup() throws SQLException {
    Connection mockConnection = mock(Connection.class);
    Statement mockStatement = mock(Statement.class);
    when(timeScaleDBService.getDBConnection()).thenReturn(mockConnection);
    when(timeScaleDBService.isValid()).thenReturn(true);
    when(mockConnection.createStatement()).thenReturn(mockStatement);
    when(mockStatement.executeQuery(QUERY)).thenReturn(resultSet);
    resetValues();
    mockResultSet();
    aggregateFunction =
        Arrays.asList(makeBillingAmtAggregation(), makeIdleCostAggregation(), makeUnallocatedCostAggregation());
    filters = Arrays.asList(makeStartTimeFilter(START_TIME), makeEndTimeFilter(END_TIME));
    sort = Arrays.asList(makeDescTotalCostSort());
    doNothing().when(accountChecker).checkIsCeEnabled(anyString());
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void testGetSunburstChartDataForDBInvalid() {
    when(timeScaleDBService.isValid()).thenReturn(false);
    assertThatThrownBy(()
                           -> sunburstChartStatsDataFetcher.fetch(ACCOUNT1_ID, Collections.EMPTY_LIST,
                               Collections.EMPTY_LIST, Collections.EMPTY_LIST, Collections.EMPTY_LIST, LIMIT, OFFSET))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void testGetSunburstChartDataForClusterDoughnut() throws SQLException {
    BillingDataQueryMetadata queryMetadataMock = mock(BillingDataQueryMetadata.class);
    List<QLCCMGroupBy> groupByList = new ArrayList<>();
    List<QLCCMEntityGroupBy> entityGroupByList = new ArrayList<>();
    groupByList.add(makeClusterGroupBy());
    groupByList.add(makeClusterTypeGroupBy());
    entityGroupByList.add(QLCCMEntityGroupBy.Cluster);
    entityGroupByList.add(QLCCMEntityGroupBy.ClusterType);
    doReturn(entityGroupByList).when(billingDataQueryBuilder).getGroupByEntity(groupByList);
    doReturn(null).when(billingDataQueryBuilder).getGroupByTime(groupByList);
    doReturn(queryMetadataMock)
        .when(billingDataQueryBuilder)
        .formQuery(ACCOUNT1_ID, filters, aggregateFunction, entityGroupByList, null, sort, true, true);
    doReturn(new HashMap<String, QLBillingAmountData>())
        .when(billingDataHelper)
        .getBillingAmountDataForEntityCostTrend(
            ACCOUNT1_ID, aggregateFunction, filters, entityGroupByList.subList(0, 1), null, sort);
    doReturn(QUERY).when(queryMetadataMock).getQuery();
    List<BillingDataMetaDataFields> billingDataMetaDataFieldsList = new ArrayList<>();
    billingDataMetaDataFieldsList.add(BillingDataMetaDataFields.SUM);
    billingDataMetaDataFieldsList.add(BillingDataMetaDataFields.IDLECOST);
    billingDataMetaDataFieldsList.add(BillingDataMetaDataFields.UNALLOCATEDCOST);
    billingDataMetaDataFieldsList.add(BillingDataMetaDataFields.CLUSTERID);
    billingDataMetaDataFieldsList.add(BillingDataMetaDataFields.CLUSTERTYPE);
    doReturn(billingDataMetaDataFieldsList).when(queryMetadataMock).getFieldNames();
    doCallRealMethod().when(billingDataHelper).roundingDoubleFieldValue(any(), any());
    doCallRealMethod().when(billingDataHelper).roundingDoubleFieldValue(any(), any(), anyBoolean());
    doCallRealMethod().when(billingDataHelper).getRoundedDoubleValue(anyDouble());
    QLSunburstChartData data = (QLSunburstChartData) sunburstChartStatsDataFetcher.fetch(
        ACCOUNT1_ID, aggregateFunction, filters, groupByList, sort, LIMIT, OFFSET);
    assertThat(data.getTotalCost()).isEqualTo(200.0);
    assertThat(data.getGridData().get(0).getId()).isEqualTo(CLUSTER1_ID + 0);
    assertThat(data.getGridData().get(0).getType()).isEqualTo("CLUSTERID");
    assertThat(data.getGridData().get(0).getClusterType()).isEqualTo(CLUSTER_TYPE);
    assertThat(data.getGridData().get(0).getEfficiencyScore()).isEqualTo(EFF_SCORE);
    assertThat(data.getGridData().get(1).getId()).isEqualTo(CLUSTER1_ID + 1);
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void testPostFetchMethod() {
    List<QLSunburstGridDataPoint> gridDataPoints = new ArrayList<>();
    gridDataPoints.add(
        QLSunburstGridDataPoint.builder().id(CLUSTER1_ID).value(TOTAL_COST / 2).efficiencyScore(EFF_SCORE).build());
    gridDataPoints.add(QLSunburstGridDataPoint.builder()
                           .id(CLUSTER2_ID)
                           .value(TOTAL_COST / 2)
                           .efficiencyScore(EFF_SCORE * 2 / 3)
                           .build());
    QLSunburstChartData qlData = QLSunburstChartData.builder().totalCost(TOTAL_COST).gridData(gridDataPoints).build();
    List<QLCCMGroupBy> groupByList = Collections.singletonList(makeClusterGroupBy());
    QLData postFetchData = sunburstChartStatsDataFetcher.postFetch(
        ACCOUNT1_ID, groupByList, aggregateFunction, sort, qlData, LIMIT, false);
    assertThat(postFetchData).isInstanceOf(QLSunburstChartData.class);
    assertThat(((QLSunburstChartData) postFetchData).getTotalCost()).isEqualTo(TOTAL_COST);
    assertThat(((QLSunburstChartData) postFetchData).getGridData().size()).isEqualTo(LIMIT);
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void testPostFetchMethodIncludeOthers() {
    doCallRealMethod().when(billingDataHelper).getRoundedDoubleValue(anyDouble());
    List<QLSunburstGridDataPoint> gridDataPoints = new ArrayList<>();
    gridDataPoints.add(QLSunburstGridDataPoint.builder()
                           .id(CLUSTER1_ID)
                           .value(TOTAL_COST / 2)
                           .trend(TREND * 3)
                           .efficiencyScore(EFF_SCORE)
                           .build());
    gridDataPoints.add(QLSunburstGridDataPoint.builder()
                           .id(CLUSTER2_ID)
                           .value(TOTAL_COST / 2)
                           .trend(TREND)
                           .efficiencyScore(EFF_SCORE * 2 / 3)
                           .build());
    gridDataPoints.add(QLSunburstGridDataPoint.builder()
                           .id(CLUSTER1_NAME)
                           .value(TOTAL_COST / 4)
                           .trend(TREND * 2)
                           .type(CLUSTER_TYPE1)
                           .efficiencyScore(EFF_SCORE * 4 / 3)
                           .build());
    QLSunburstChartData qlData = QLSunburstChartData.builder().totalCost(TOTAL_COST).gridData(gridDataPoints).build();
    List<QLCCMGroupBy> groupByList = Collections.singletonList(makeClusterGroupBy());
    QLData postFetchData =
        sunburstChartStatsDataFetcher.postFetch(ACCOUNT1_ID, groupByList, aggregateFunction, sort, qlData, LIMIT, true);
    assertThat(postFetchData).isInstanceOf(QLSunburstChartData.class);
    assertThat(((QLSunburstChartData) postFetchData).getTotalCost()).isEqualTo(TOTAL_COST);
    assertThat(((QLSunburstChartData) postFetchData).getGridData().size()).isEqualTo(LIMIT + 1);
    assertThat(((QLSunburstChartData) postFetchData).getGridData().get(1).getName()).isEqualTo("Others");
    assertThat(((QLSunburstChartData) postFetchData).getGridData().get(1).getType()).isEqualTo(CLUSTER_TYPE1);
    assertThat(((QLSunburstChartData) postFetchData).getGridData().get(1).getEfficiencyScore()).isEqualTo(67);
    assertThat(((QLSunburstChartData) postFetchData).getGridData().get(1).getValue()).isEqualTo(75.0);
    assertThat(((QLSunburstChartData) postFetchData).getGridData().get(1).getTrend()).isEqualTo(13.33);
  }

  private QLCCMAggregationFunction makeBillingAmtAggregation() {
    return QLCCMAggregationFunction.builder()
        .operationType(QLCCMAggregateOperation.SUM)
        .columnName(TOTAL_COST_COLUMN)
        .build();
  }

  private QLCCMAggregationFunction makeIdleCostAggregation() {
    return QLCCMAggregationFunction.builder()
        .operationType(QLCCMAggregateOperation.SUM)
        .columnName(IDLE_COST_COLUMN)
        .build();
  }

  private QLCCMAggregationFunction makeUnallocatedCostAggregation() {
    return QLCCMAggregationFunction.builder()
        .operationType(QLCCMAggregateOperation.SUM)
        .columnName(UNALLOCATED_COST_COLUMN)
        .build();
  }

  private QLCCMGroupBy makeClusterGroupBy() {
    QLCCMEntityGroupBy clusterGroupBy = QLCCMEntityGroupBy.Cluster;
    return QLCCMGroupBy.builder().entityGroupBy(clusterGroupBy).build();
  }

  private QLCCMGroupBy makeClusterTypeGroupBy() {
    QLCCMEntityGroupBy clusterTypeGroupBy = QLCCMEntityGroupBy.ClusterType;
    return QLCCMGroupBy.builder().entityGroupBy(clusterTypeGroupBy).build();
  }

  private QLBillingDataFilter makeStartTimeFilter(Long filterTime) {
    QLTimeFilter timeFilter = QLTimeFilter.builder().operator(QLTimeOperator.AFTER).value(filterTime).build();
    return QLBillingDataFilter.builder().startTime(timeFilter).build();
  }

  private QLBillingDataFilter makeEndTimeFilter(Long filterTime) {
    QLTimeFilter timeFilter = QLTimeFilter.builder().operator(QLTimeOperator.BEFORE).value(filterTime).build();
    return QLBillingDataFilter.builder().endTime(timeFilter).build();
  }

  private QLBillingSortCriteria makeDescTotalCostSort() {
    return QLBillingSortCriteria.builder().sortOrder(QLSortOrder.DESCENDING).sortType(QLBillingSortType.Amount).build();
  }

  private void mockResultSet() throws SQLException {
    Connection connection = mock(Connection.class);
    statement = mock(Statement.class);
    resultSet = mock(ResultSet.class);
    when(timeScaleDBService.getDBConnection()).thenReturn(connection);
    when(connection.createStatement()).thenReturn(statement);
    when(statement.executeQuery(anyString())).thenReturn(resultSet);

    when(resultSet.getDouble("COST")).thenReturn(TOTAL_COST);
    when(resultSet.getDouble("ACTUALIDLECOST")).thenReturn(IDLE_COST);
    when(resultSet.getDouble("UNALLOCATEDCOST")).thenReturn(UNALLOCATED_COST);
    when(resultSet.getString("CLUSTERTYPE")).thenReturn(CLUSTER_TYPE);
    when(resultSet.getString("CLUSTERID")).thenAnswer((Answer<String>) invocation -> CLUSTER1_ID + iterableCount[0]++);
    when(resultSet.getString("APPID")).thenAnswer((Answer<String>) invocation -> APP1_ID_ACCOUNT1 + iterableCount[0]++);
    when(resultSet.next()).thenReturn(true);

    returnResultSet(2);
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

  private void resetValues() {
    count[0] = 0;
    iterableCount[0] = 0;
  }
}
