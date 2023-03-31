/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.budget;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.ccm.budget.AlertThresholdBase.ACTUAL_COST;
import static io.harness.ccm.budget.BudgetType.SPECIFIED_AMOUNT;
import static io.harness.rule.OwnerRule.SHUBHANSHU;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.ccm.budget.AlertThreshold;
import io.harness.ccm.budget.ApplicationBudgetScope;
import io.harness.ccm.budget.BudgetService;
import io.harness.ccm.budget.BudgetType;
import io.harness.ccm.budget.EnvironmentType;
import io.harness.ccm.commons.entities.billing.Budget;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;
import io.harness.timescaledb.TimeScaleDBService;

import software.wings.graphql.datafetcher.AbstractDataFetcherTestBase;
import software.wings.graphql.datafetcher.DataFetcherUtils;
import software.wings.graphql.schema.type.aggregation.QLTimeFilter;
import software.wings.graphql.schema.type.aggregation.QLTimeOperator;
import software.wings.graphql.schema.type.aggregation.billing.QLBillingDataFilter;
import software.wings.graphql.schema.type.aggregation.budget.QLBudgetNotificationsData;
import software.wings.service.intfc.ce.CeAccountExpirationChecker;

import com.google.inject.Inject;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.stubbing.Answer;

@TargetModule(HarnessModule._375_CE_GRAPHQL)
@OwnedBy(CE)
public class BudgetNotificationsDataFetcherTest extends AbstractDataFetcherTestBase {
  @Mock TimeScaleDBService timeScaleDBService;
  @Mock BudgetService budgetService;
  @Mock private DataFetcherUtils utils;
  @Mock CeAccountExpirationChecker accountChecker;
  @InjectMocks BudgetTimescaleQueryHelper queryHelper;
  @Inject @InjectMocks BudgetNotificationsDataFetcher budgetNotificationsDataFetcher;

  @Mock Statement statement;
  @Mock ResultSet resultSet;

  private String accountId = "ACCOUNT_ID";
  private String applicationId1 = "APPLICATION_ID_1";
  private String applicationId2 = "APPLICATION_ID_2";
  private String[] applicationIds = {applicationId1, applicationId2};
  private String budgetId = "BUDGET_ID";
  private String budgetName = "BUDGET_NAME";
  private BudgetType budgetType = SPECIFIED_AMOUNT;
  private double budgetAmount = 25000.0;
  private Double[] alertAt = {0.5};
  final int[] count = {0};

  private AlertThreshold alertThreshold;
  private Budget budget;

  @Before
  public void setup() throws SQLException {
    alertThreshold = AlertThreshold.builder().percentage(0.5).basedOn(ACTUAL_COST).alertsSent(1).build();
    budget = Budget.builder()
                 .uuid(budgetId)
                 .accountId(accountId)
                 .name(budgetName)
                 .scope(ApplicationBudgetScope.builder()
                            .applicationIds(applicationIds)
                            .environmentType(EnvironmentType.ALL)
                            .build())
                 .type(SPECIFIED_AMOUNT)
                 .budgetAmount(budgetAmount)
                 .alertThresholds(new AlertThreshold[] {alertThreshold})
                 .build();
    when(budgetService.list(accountId)).thenReturn(Arrays.asList(budget));
    doNothing().when(accountChecker).checkIsCeEnabled(anyString());
    mockResultSet();
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testGetBillingTrendWhenDbIsInvalid() {
    when(timeScaleDBService.isValid()).thenReturn(false);
    assertThatThrownBy(()
                           -> budgetNotificationsDataFetcher.fetch(accountId, null, Collections.emptyList(),
                               Collections.emptyList(), Collections.emptyList(), null))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testFetch() {
    List<QLBillingDataFilter> timeFilters = new ArrayList<>();
    long currentTime = System.currentTimeMillis();
    timeFilters.add(makeStartTimeFilter(currentTime - 86400000L));
    timeFilters.add(makeEndTimeFilter(currentTime));
    QLBudgetNotificationsData data = (QLBudgetNotificationsData) budgetNotificationsDataFetcher.fetch(
        accountId, null, timeFilters, Collections.emptyList(), Collections.emptyList(), null);
    assertThat(data).isNotNull();
    assertThat(data.getData().getCount()).isEqualTo(100L);
  }

  public QLBillingDataFilter makeStartTimeFilter(Long filterTime) {
    QLTimeFilter timeFilter = QLTimeFilter.builder().operator(QLTimeOperator.AFTER).value(filterTime).build();
    return QLBillingDataFilter.builder().startTime(timeFilter).build();
  }

  public QLBillingDataFilter makeEndTimeFilter(Long filterTime) {
    QLTimeFilter timeFilter = QLTimeFilter.builder().operator(QLTimeOperator.BEFORE).value(filterTime).build();
    return QLBillingDataFilter.builder().endTime(timeFilter).build();
  }

  private void mockResultSet() throws SQLException {
    Connection connection = mock(Connection.class);
    statement = mock(Statement.class);
    resultSet = mock(ResultSet.class);
    when(timeScaleDBService.getDBConnection()).thenReturn(connection);
    when(timeScaleDBService.isValid()).thenReturn(true);
    when(connection.createStatement()).thenReturn(statement);
    when(statement.executeQuery(anyString())).thenReturn(resultSet);

    when(resultSet.getLong("COUNT")).thenAnswer((Answer<Long>) invocation -> 100L);
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
}
