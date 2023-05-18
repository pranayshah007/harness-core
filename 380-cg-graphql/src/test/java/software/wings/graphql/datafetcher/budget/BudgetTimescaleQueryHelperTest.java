/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.budget;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.rule.OwnerRule.SHUBHANSHU;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.ccm.budget.entities.BudgetAlertsData;
import io.harness.ccm.commons.utils.TimeUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;
import io.harness.timescaledb.TimeScaleDBService;

import software.wings.beans.User;
import software.wings.graphql.datafetcher.AbstractDataFetcherTestBase;
import software.wings.graphql.datafetcher.billing.BillingDataQueryMetadata;
import software.wings.graphql.datafetcher.billing.QLBillingAmountData;
import software.wings.graphql.datafetcher.billing.QLCCMAggregateOperation;
import software.wings.graphql.datafetcher.billing.QLCCMAggregationFunction;
import software.wings.graphql.schema.type.aggregation.QLIdFilter;
import software.wings.graphql.schema.type.aggregation.QLIdOperator;
import software.wings.graphql.schema.type.aggregation.QLTimeFilter;
import software.wings.graphql.schema.type.aggregation.QLTimeOperator;
import software.wings.graphql.schema.type.aggregation.billing.QLBillingDataFilter;
import software.wings.security.UserThreadLocal;

import com.google.inject.Inject;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.stubbing.Answer;

@TargetModule(HarnessModule._375_CE_GRAPHQL)
@OwnedBy(CE)
public class BudgetTimescaleQueryHelperTest extends AbstractDataFetcherTestBase {
  @Mock TimeScaleDBService timeScaleDBService;
  @Mock private TimeUtils utils;
  @Inject @InjectMocks BudgetTimescaleQueryHelper queryHelper;

  @Mock Statement statement;
  @Mock ResultSet resultSet;

  final int[] count = {0};
  final double[] doubleVal = {0};
  final long currentTime = System.currentTimeMillis();
  final long[] calendar = {currentTime};
  private static final double ALERT_THRESHOLD = 50.0;
  private static final String BUDGET_ID = "BUDGET_ID";
  private static final double BUDGETED_COST = 1000.0;
  private static final double ACTUAL_COST = 501.0;
  private static final long TIME = System.currentTimeMillis();
  private static final long START_TIME = TIME;
  private static final long END_TIME = TIME + 86400000;

  @Before
  public void setup() throws SQLException {
    User user = testUtils.createUser(testUtils.createAccount());
    UserThreadLocal.set(user);

    createAccount(ACCOUNT1_ID, getLicenseInfo());
    createApp(ACCOUNT1_ID, APP1_ID_ACCOUNT1, APP1_ID_ACCOUNT1, TAG_TEAM, TAG_VALUE_TEAM1);

    Connection mockConnection = mock(Connection.class);
    Statement mockStatement = mock(Statement.class);
    when(timeScaleDBService.getDBConnection()).thenReturn(mockConnection);
    when(timeScaleDBService.isValid()).thenReturn(true);
    when(mockConnection.createStatement()).thenReturn(mockStatement);
    when(mockStatement.executeQuery(anyString())).thenReturn(resultSet);
    mockResultSet();
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testInsertAlertEntryWhenDbIsInvalid() {
    queryHelper.insertAlertEntryInTable(getTestBudgetData(), ACCOUNT1_ID);
    when(timeScaleDBService.isValid()).thenReturn(false);
    assertThatThrownBy(() -> queryHelper.insertAlertEntryInTable(getTestBudgetData(), ACCOUNT1_ID))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testGetLastAlertTimestampWhenDbIsInvalid() {
    when(timeScaleDBService.isValid()).thenReturn(false);
    assertThatThrownBy(() -> queryHelper.getLastAlertTimestamp(getTestBudgetData(), ACCOUNT1_ID))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testGetLastAlertTimestamp() {
    long time = queryHelper.getLastAlertTimestamp(getTestBudgetData(), ACCOUNT1_ID);
    assertThat(time).isEqualTo(TIME);
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testGetBudgetCostDataWhenDbIsInvalid() {
    when(timeScaleDBService.isValid()).thenReturn(false);

    assertThatThrownBy(()
                           -> queryHelper.getBudgetCostData(ACCOUNT1_ID, makeBillingAmtAggregation(),
                               Collections.singletonList(makeClusterFilter(new String[] {CLUSTER1_ID}))))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testGetBudgetCostData() {
    QLBillingAmountData data = queryHelper.getBudgetCostData(ACCOUNT1_ID, makeBillingAmtAggregation(),
        Arrays.asList(makeTimeFilter(START_TIME - 1), makeClusterFilter(new String[] {CLUSTER1_ID})));
    assertThat(data.getCost().doubleValue()).isEqualTo(10);
    assertThat(data.getMinStartTime()).isEqualTo(START_TIME);
    assertThat(data.getMaxStartTime()).isEqualTo(END_TIME);
  }

  private void mockResultSet() throws SQLException {
    Connection connection = mock(Connection.class);
    statement = mock(Statement.class);
    resultSet = mock(ResultSet.class);
    when(timeScaleDBService.getDBConnection()).thenReturn(connection);
    when(connection.createStatement()).thenReturn(statement);
    when(statement.executeQuery(anyString())).thenReturn(resultSet);

    when(resultSet.getBigDecimal("COST")).thenAnswer((Answer<BigDecimal>) invocation -> BigDecimal.TEN);
    when(resultSet.getDouble("ALERTTHRESHOLD")).thenAnswer((Answer<Double>) invocation -> ALERT_THRESHOLD);
    when(resultSet.getDouble("ACTUALCOST")).thenAnswer((Answer<Double>) invocation -> ACTUAL_COST);
    when(resultSet.getDouble("BUDGETEDCOST")).thenAnswer((Answer<Double>) invocation -> BUDGETED_COST);
    when(resultSet.getString("BUDGETID")).thenAnswer((Answer<String>) invocation -> BUDGET_ID);
    when(resultSet.getString("ACCOUNTID")).thenAnswer((Answer<String>) invocation -> ACCOUNT1_ID);
    when(resultSet.getTimestamp("alerttime", utils.getDefaultCalendar()))
        .thenAnswer((Answer<Timestamp>) invocation -> new Timestamp(TIME));
    when(resultSet.getTimestamp(BillingDataQueryMetadata.BillingDataMetaDataFields.MIN_STARTTIME.getFieldName(),
             utils.getDefaultCalendar()))
        .thenReturn(new Timestamp(START_TIME));
    when(resultSet.getTimestamp(BillingDataQueryMetadata.BillingDataMetaDataFields.MAX_STARTTIME.getFieldName(),
             utils.getDefaultCalendar()))
        .thenReturn(new Timestamp(END_TIME));

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

  private BudgetAlertsData getTestBudgetData() {
    return BudgetAlertsData.builder()
        .alertThreshold(ALERT_THRESHOLD)
        .budgetId(BUDGET_ID)
        .budgetedCost(BUDGETED_COST)
        .actualCost(ACTUAL_COST)
        .accountId(ACCOUNT1_ID)
        .time(TIME)
        .build();
  }

  private QLBillingDataFilter makeClusterFilter(String[] values) {
    QLIdFilter clusterFilter = QLIdFilter.builder().operator(QLIdOperator.EQUALS).values(values).build();
    return QLBillingDataFilter.builder().cluster(clusterFilter).build();
  }

  public QLBillingDataFilter makeTimeFilter(Long filterTime) {
    QLTimeFilter timeFilter = QLTimeFilter.builder().operator(QLTimeOperator.AFTER).value(filterTime).build();
    return QLBillingDataFilter.builder().startTime(timeFilter).build();
  }

  public QLCCMAggregationFunction makeBillingAmtAggregation() {
    return QLCCMAggregationFunction.builder()
        .operationType(QLCCMAggregateOperation.SUM)
        .columnName("billingamount")
        .build();
  }
}
