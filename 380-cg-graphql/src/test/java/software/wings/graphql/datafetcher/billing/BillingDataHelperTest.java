/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.billing;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.rule.OwnerRule.HITESH;
import static io.harness.rule.OwnerRule.SANDESH;
import static io.harness.rule.OwnerRule.SHUBHANSHU;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.timescaledb.TimeScaleDBService;

import software.wings.beans.User;
import software.wings.graphql.datafetcher.AbstractDataFetcherTestBase;
import software.wings.graphql.datafetcher.DataFetcherUtils;
import software.wings.graphql.schema.type.aggregation.QLTimeFilter;
import software.wings.graphql.schema.type.aggregation.QLTimeOperator;
import software.wings.graphql.schema.type.aggregation.billing.QLBillingDataFilter;
import software.wings.graphql.schema.type.aggregation.billing.QLCCMEntityGroupBy;
import software.wings.graphql.schema.type.aggregation.billing.QLStatsBreakdownInfo;
import software.wings.security.UserThreadLocal;

import com.google.inject.Inject;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
public class BillingDataHelperTest extends AbstractDataFetcherTestBase {
  @Mock TimeScaleDBService timeScaleDBService;
  @Mock private DataFetcherUtils utils;
  @Inject @InjectMocks BillingDataHelper billingDataHelper;

  @Mock Statement statement;
  @Mock ResultSet resultSet;

  final int[] count = {0};
  final double[] doubleVal = {0};
  final long currentTime = System.currentTimeMillis();
  final long[] calendar = {currentTime};
  private static final long ONE_DAY_MILLIS = 86400000;
  private final BigDecimal TOTAL_COST = new BigDecimal("10.660");
  private final BigDecimal TOTAL_TREND_COST = new BigDecimal(5);
  private Instant END_TIME = Instant.ofEpochMilli(1571509800000l);
  private Instant START_TIME = Instant.ofEpochMilli(1570645800000l);

  @Before
  public void setup() throws SQLException {
    User user = testUtils.createUser(testUtils.createAccount());
    UserThreadLocal.set(user);

    // Account1
    createAccount(ACCOUNT1_ID, getLicenseInfo());
    createApp(ACCOUNT1_ID, APP1_ID_ACCOUNT1, APP1_ID_ACCOUNT1, TAG_TEAM, TAG_VALUE_TEAM1);

    Connection mockConnection = mock(Connection.class);
    Statement mockStatement = mock(Statement.class);
    when(timeScaleDBService.getDBConnection()).thenReturn(mockConnection);
    when(timeScaleDBService.isValid()).thenReturn(true);
    when(mockConnection.createStatement()).thenReturn(mockStatement);
    when(mockStatement.executeQuery(anyString())).thenReturn(resultSet);
    resetValues();
    mockResultSet();
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testGetBillingAmountDataForEntityCostTrend() {
    List<QLCCMAggregationFunction> aggregationFunction = Arrays.asList(makeBillingAmtAggregation());
    List<QLCCMEntityGroupBy> groupBy = Arrays.asList(QLCCMEntityGroupBy.Cluster);
    List<QLBillingDataFilter> filters = new ArrayList<>();
    filters.add(makeEndTimeFilter(currentTime));
    filters.add(makeStartTimeFilter(currentTime - ONE_DAY_MILLIS));

    Map<String, QLBillingAmountData> entityIdToPrevBillingAmountData =
        billingDataHelper.getBillingAmountDataForEntityCostTrend(
            ACCOUNT1_ID, aggregationFunction, filters, groupBy, null, null);
    assertThat(entityIdToPrevBillingAmountData).isNotNull();
    assertThat(entityIdToPrevBillingAmountData.containsKey(CLUSTER1_ID)).isTrue();
    assertThat(entityIdToPrevBillingAmountData.get(CLUSTER1_ID).getCost()).isEqualTo(BigDecimal.valueOf(19.0));
    assertThat(entityIdToPrevBillingAmountData.get(CLUSTER1_ID).getMinStartTime())
        .isEqualTo(currentTime - ONE_DAY_MILLIS);
    assertThat(entityIdToPrevBillingAmountData.get(CLUSTER1_ID).getMaxStartTime()).isEqualTo(currentTime);
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testGetCostTrendForEntity() throws SQLException {
    List<QLCCMAggregationFunction> aggregationFunction = Arrays.asList(makeBillingAmtAggregation());
    List<QLCCMEntityGroupBy> groupBy = Arrays.asList(QLCCMEntityGroupBy.Cluster);
    List<QLBillingDataFilter> filters = new ArrayList<>();
    filters.add(makeEndTimeFilter(currentTime));
    filters.add(makeStartTimeFilter(currentTime - ONE_DAY_MILLIS));

    Map<String, QLBillingAmountData> entityIdToPrevBillingAmountData =
        billingDataHelper.getBillingAmountDataForEntityCostTrend(
            ACCOUNT1_ID, aggregationFunction, filters, groupBy, null, null);
    assertThat(entityIdToPrevBillingAmountData).isNotNull();
    assertThat(entityIdToPrevBillingAmountData.containsKey(CLUSTER1_ID)).isTrue();

    double costTrend =
        billingDataHelper.getCostTrendForEntity(resultSet, entityIdToPrevBillingAmountData.get(CLUSTER1_ID), filters);
    assertThat(costTrend).isEqualTo(0.0);
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testGetCostTrendForEntityForNamespace() throws SQLException {
    List<QLCCMAggregationFunction> aggregationFunction = Arrays.asList(makeBillingAmtAggregation());
    List<QLCCMEntityGroupBy> groupBy = Arrays.asList(QLCCMEntityGroupBy.Namespace);
    List<QLBillingDataFilter> filters = new ArrayList<>();
    filters.add(makeEndTimeFilter(currentTime));
    filters.add(makeStartTimeFilter(currentTime - ONE_DAY_MILLIS));

    Map<String, QLBillingAmountData> entityIdToPrevBillingAmountData =
        billingDataHelper.getBillingAmountDataForEntityCostTrend(
            ACCOUNT1_ID, aggregationFunction, filters, groupBy, null, null);
    assertThat(entityIdToPrevBillingAmountData).isNotNull();
    assertThat(entityIdToPrevBillingAmountData.containsKey(NAMESPACE1)).isTrue();
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testGetCostTrendForEntityForWorkload() throws SQLException {
    List<QLCCMAggregationFunction> aggregationFunction = Arrays.asList(makeBillingAmtAggregation());
    List<QLCCMEntityGroupBy> groupBy = new ArrayList<>();
    groupBy.add(QLCCMEntityGroupBy.WorkloadName);
    List<QLBillingDataFilter> filters = new ArrayList<>();
    filters.add(makeEndTimeFilter(currentTime));
    filters.add(makeStartTimeFilter(currentTime - ONE_DAY_MILLIS));

    Map<String, QLBillingAmountData> entityIdToPrevBillingAmountData =
        billingDataHelper.getBillingAmountDataForEntityCostTrend(
            ACCOUNT1_ID, aggregationFunction, filters, groupBy, null, null);
    assertThat(entityIdToPrevBillingAmountData).isNotNull();
    assertThat(entityIdToPrevBillingAmountData.containsKey(WORKLOAD_NAME_ACCOUNT1)).isTrue();
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testGetCostTrendForEntityForApplication() throws SQLException {
    List<QLCCMAggregationFunction> aggregationFunction = Arrays.asList(makeBillingAmtAggregation());
    List<QLCCMEntityGroupBy> groupBy = Arrays.asList(QLCCMEntityGroupBy.Application);
    List<QLBillingDataFilter> filters = new ArrayList<>();
    filters.add(makeEndTimeFilter(currentTime));
    filters.add(makeStartTimeFilter(currentTime - ONE_DAY_MILLIS));

    Map<String, QLBillingAmountData> entityIdToPrevBillingAmountData =
        billingDataHelper.getBillingAmountDataForEntityCostTrend(
            ACCOUNT1_ID, aggregationFunction, filters, groupBy, null, null);
    assertThat(entityIdToPrevBillingAmountData).isNotNull();
    assertThat(entityIdToPrevBillingAmountData.containsKey(APP1_ID_ACCOUNT1)).isTrue();
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testGetCostTrendForEntityForService() throws SQLException {
    List<QLCCMAggregationFunction> aggregationFunction = Arrays.asList(makeBillingAmtAggregation());
    List<QLCCMEntityGroupBy> groupBy = Arrays.asList(QLCCMEntityGroupBy.Service);
    List<QLBillingDataFilter> filters = new ArrayList<>();
    filters.add(makeEndTimeFilter(currentTime));
    filters.add(makeStartTimeFilter(currentTime - ONE_DAY_MILLIS));

    Map<String, QLBillingAmountData> entityIdToPrevBillingAmountData =
        billingDataHelper.getBillingAmountDataForEntityCostTrend(
            ACCOUNT1_ID, aggregationFunction, filters, groupBy, null, null);
    assertThat(entityIdToPrevBillingAmountData).isNotNull();
    assertThat(entityIdToPrevBillingAmountData.containsKey(SERVICE1_ID_APP1_ACCOUNT1)).isTrue();
  }

  @Test
  @Owner(developers = SANDESH)
  @Category(UnitTests.class)
  public void testCalculateEfficiencyScore() {
    QLStatsBreakdownInfo costStats =
        QLStatsBreakdownInfo.builder().unallocated(30.0).utilized(50.0).idle(20.0).total(100.0).build();
    assertThat(billingDataHelper.calculateEfficiencyScore(costStats)).isEqualTo(77);
  }

  @Test
  @Owner(developers = SANDESH)
  @Category(UnitTests.class)
  public void testCalculateTrendPercentage() {
    assertThat(billingDataHelper.calculateTrendPercentage(BigDecimal.valueOf(100), BigDecimal.valueOf(50)))
        .isEqualTo(new BigDecimal("100.00"));
    assertThat(billingDataHelper.calculateTrendPercentage(BigDecimal.valueOf(100), BigDecimal.valueOf(0)))
        .isEqualTo(new BigDecimal("-1"));
    assertThat(billingDataHelper.calculateTrendPercentage(Double.valueOf(100), Double.valueOf(50)))
        .isEqualTo(Double.valueOf(100.00));
    assertThat(billingDataHelper.calculateTrendPercentage(Double.valueOf(100), Double.valueOf(50)))
        .isEqualTo(Double.valueOf(100.00));
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

    List<QLCCMAggregationFunction> aggregationFunction = Arrays.asList(makeBillingAmtAggregation());
    QLTrendStatsCostData data =
        billingDataHelper.getBillingAmountData(ACCOUNT1_ID, aggregationFunction, Collections.emptyList());
    assertThat(data).isNull();
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testGetBillingAmountData() throws SQLException {
    List<QLCCMAggregationFunction> aggregationFunction = Arrays.asList(makeBillingAmtAggregation());
    List<QLBillingDataFilter> filters = new ArrayList<>();
    filters.add(makeEndTimeFilter(currentTime));
    filters.add(makeStartTimeFilter(currentTime - ONE_DAY_MILLIS));

    QLTrendStatsCostData data = billingDataHelper.getBillingAmountData(ACCOUNT1_ID, aggregationFunction, filters);
    assertThat(data).isNotNull();
    assertThat(data.getTotalCostData().getCost()).isEqualTo(BigDecimal.valueOf(19.0));
    assertThat(data.getTotalCostData().getMinStartTime()).isEqualTo(currentTime - ONE_DAY_MILLIS);
    assertThat(data.getTotalCostData().getMaxStartTime()).isEqualTo(currentTime);
  }

  public QLCCMAggregationFunction makeBillingAmtAggregation() {
    return QLCCMAggregationFunction.builder()
        .operationType(QLCCMAggregateOperation.SUM)
        .columnName("billingamount")
        .build();
  }

  public QLBillingDataFilter makeStartTimeFilter(long value) {
    QLTimeFilter timeFilter = QLTimeFilter.builder().operator(QLTimeOperator.AFTER).value(value).build();
    return QLBillingDataFilter.builder().startTime(timeFilter).build();
  }

  public QLBillingDataFilter makeEndTimeFilter(long value) {
    QLTimeFilter timeFilter = QLTimeFilter.builder().operator(QLTimeOperator.BEFORE).value(value).build();
    return QLBillingDataFilter.builder().endTime(timeFilter).build();
  }

  private void mockResultSet() throws SQLException {
    Connection connection = mock(Connection.class);
    statement = mock(Statement.class);
    resultSet = mock(ResultSet.class);
    when(timeScaleDBService.getDBConnection()).thenReturn(connection);
    when(connection.createStatement()).thenReturn(statement);
    when(statement.executeQuery(anyString())).thenReturn(resultSet);

    when(resultSet.getDouble("COST")).thenAnswer((Answer<Double>) invocation -> 10.0 + doubleVal[0]++);
    when(resultSet.getBigDecimal("COST"))
        .thenAnswer((Answer<BigDecimal>) invocation -> BigDecimal.TEN.add(BigDecimal.valueOf(doubleVal[0]++)));
    when(resultSet.getString("APPID")).thenAnswer((Answer<String>) invocation -> APP1_ID_ACCOUNT1);
    when(resultSet.getString("SERVICEID")).thenAnswer((Answer<String>) invocation -> SERVICE1_ID_APP1_ACCOUNT1);
    when(resultSet.getString("ENVID")).thenAnswer((Answer<String>) invocation -> ENV1_ID_APP1_ACCOUNT1);
    when(resultSet.getString("WORKLOADNAME")).thenAnswer((Answer<String>) invocation -> WORKLOAD_NAME_ACCOUNT1);
    when(resultSet.getString("CLUSTERID")).thenAnswer((Answer<String>) invocation -> CLUSTER1_ID);
    when(resultSet.getString("NAMESPACE")).thenAnswer((Answer<String>) invocation -> NAMESPACE1);
    when(resultSet.getTimestamp(BillingDataQueryMetadata.BillingDataMetaDataFields.MIN_STARTTIME.getFieldName(),
             utils.getDefaultCalendar()))
        .thenReturn(new Timestamp(currentTime - ONE_DAY_MILLIS));
    when(resultSet.getTimestamp(BillingDataQueryMetadata.BillingDataMetaDataFields.MAX_STARTTIME.getFieldName(),
             utils.getDefaultCalendar()))
        .thenReturn(new Timestamp(currentTime));

    when(resultSet.getTimestamp(
             BillingDataQueryMetadata.BillingDataMetaDataFields.STARTTIME.getFieldName(), utils.getDefaultCalendar()))
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
      return false;
    });
  }

  private void resetValues() {
    count[0] = 0;
    doubleVal[0] = 0;
  }
}
