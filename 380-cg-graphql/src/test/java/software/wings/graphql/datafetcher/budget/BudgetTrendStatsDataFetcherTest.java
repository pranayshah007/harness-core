/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.budget;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.ccm.budget.AlertThresholdBase.ACTUAL_COST;
import static io.harness.ccm.budget.BudgetScopeType.APPLICATION;
import static io.harness.ccm.budget.BudgetType.SPECIFIED_AMOUNT;
import static io.harness.rule.OwnerRule.SHUBHANSHU;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.ccm.budget.AlertThreshold;
import io.harness.ccm.budget.ApplicationBudgetScope;
import io.harness.ccm.budget.BudgetService;
import io.harness.ccm.budget.BudgetType;
import io.harness.ccm.budget.ClusterBudgetScope;
import io.harness.ccm.budget.EnvironmentType;
import io.harness.ccm.commons.entities.billing.Budget;
import io.harness.ccm.commons.entities.billing.Budget.BudgetBuilder;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;

import software.wings.beans.User;
import software.wings.graphql.datafetcher.AbstractDataFetcherTestBase;
import software.wings.graphql.datafetcher.DataFetcherUtils;
import software.wings.graphql.datafetcher.billing.BillingDataHelper;
import software.wings.graphql.schema.query.QLBudgetQueryParameters;
import software.wings.graphql.schema.type.aggregation.budget.QLBudgetTableData;
import software.wings.graphql.schema.type.aggregation.budget.QLBudgetTrendStats;
import software.wings.security.UserThreadLocal;
import software.wings.service.intfc.ce.CeAccountExpirationChecker;

import java.sql.SQLException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@TargetModule(HarnessModule._375_CE_GRAPHQL)
@OwnedBy(CE)
public class BudgetTrendStatsDataFetcherTest extends AbstractDataFetcherTestBase {
  @Mock BudgetService budgetService;
  @Mock private DataFetcherUtils utils;
  @Mock BillingDataHelper billingDataHelper;
  @Mock CeAccountExpirationChecker accountChecker;
  @InjectMocks BudgetTrendStatsDataFetcher budgetTrendStatsDataFetcher;

  private QLBudgetTableData budgetDetails;
  private String budgetId = "budgetId";
  private String accountId = "accountId";
  private String budgetName = "budgetName";
  private String[] clusterIds = {"clusterId"};
  private String[] appIds = {"appId"};
  private Double[] alertAt = {50.0};
  private BudgetType budgetType = SPECIFIED_AMOUNT;
  private EnvironmentType environmentType = EnvironmentType.PROD;
  private AlertThreshold[] alertThresholds;
  private long createdAt = System.currentTimeMillis();
  private long lastUpdatedAt = System.currentTimeMillis();
  private double budgetAmount = 25000.0;
  private double actualCost = 15000.0;
  private double forecastCost = 30000.0;
  private long alertSentAt = 15;
  private QLBudgetQueryParameters queryParameters;
  private static final String ACTUAL_COST_LABEL = "Actual vs. budgeted";
  private static final String FORECASTED_COST_LABEL = "Forecasted vs. budgeted";
  private static final String STATUS_NOT_ON_TRACK = "Not on Track";

  @Before
  public void setup() throws SQLException {
    User user = testUtils.createUser(testUtils.createAccount());
    UserThreadLocal.set(user);
    createAccount(ACCOUNT1_ID, getLicenseInfo());
    createApp(ACCOUNT1_ID, APP1_ID_ACCOUNT1, APP1_ID_ACCOUNT1, TAG_TEAM, TAG_VALUE_TEAM1);
    queryParameters = new QLBudgetQueryParameters(budgetId);
    budgetDetails = QLBudgetTableData.builder()
                        .name(budgetName)
                        .id(budgetId)
                        .type(budgetType.toString())
                        .scopeType("APPLICATION")
                        .appliesTo(appIds)
                        .environment(environmentType.toString())
                        .alertAt(alertAt)
                        .budgetedAmount(budgetAmount)
                        .actualAmount(actualCost)
                        .forecastCost(forecastCost)
                        .build();

    alertThresholds = new AlertThreshold[] {
        AlertThreshold.builder().crossedAt(alertSentAt).basedOn(ACTUAL_COST).alertsSent(1).percentage(50.0).build()};
    when(budgetService.getBudgetDetails(any(Budget.class))).thenReturn(budgetDetails);
    when(billingDataHelper.getRoundedDoubleValue(budgetAmount)).thenReturn(budgetAmount);
    when(billingDataHelper.getRoundedDoubleValue(actualCost)).thenReturn(actualCost);
    when(billingDataHelper.getRoundedDoubleValue(forecastCost)).thenReturn(forecastCost);
    doNothing().when(accountChecker).checkIsCeEnabled(anyString());
  }

  private Budget mockBudget(String scope) {
    BudgetBuilder budgetBuilder = Budget.builder()
                                      .uuid(budgetId)
                                      .accountId(accountId)
                                      .name(budgetName)
                                      .alertThresholds(alertThresholds)
                                      .createdAt(createdAt)
                                      .lastUpdatedAt(lastUpdatedAt)
                                      .type(budgetType)
                                      .budgetAmount(budgetAmount)
                                      .actualCost(actualCost)
                                      .forecastCost(forecastCost);
    if (scope.equals("CLUSTER")) {
      budgetBuilder.scope(ClusterBudgetScope.builder().clusterIds(clusterIds).build());
    } else {
      budgetBuilder.scope(
          ApplicationBudgetScope.builder().applicationIds(appIds).environmentType(environmentType).build());
    }
    return budgetBuilder.build();
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void shouldThrowWhenFetchWithInvalidBudgetId() {
    when(budgetService.get(budgetId, accountId)).thenReturn(null);
    assertThatThrownBy(() -> budgetTrendStatsDataFetcher.fetch(queryParameters, accountId))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testFetch() {
    when(budgetService.get(budgetId, accountId)).thenReturn(mockBudget(APPLICATION));
    QLBudgetTrendStats budgetTrendStats = budgetTrendStatsDataFetcher.fetch(queryParameters, accountId);
    assertThat(budgetTrendStats.getTotalCost().getStatsLabel()).isEqualTo(ACTUAL_COST_LABEL);
    assertThat(budgetTrendStats.getTotalCost().getStatsValue()).isEqualTo("$15000.0 / $25000.0");
    assertThat(budgetTrendStats.getForecastCost().getStatsLabel()).isEqualTo(FORECASTED_COST_LABEL);
    assertThat(budgetTrendStats.getForecastCost().getStatsValue()).isEqualTo("$30000.0 / $25000.0");
    assertThat(budgetTrendStats.getBudgetDetails().getName()).isEqualTo(budgetName);
    assertThat(budgetTrendStats.getBudgetDetails().getId()).isEqualTo(budgetId);
    assertThat(budgetTrendStats.getBudgetDetails().getScopeType()).isEqualTo("APPLICATION");
    assertThat(budgetTrendStats.getBudgetDetails().getType()).isEqualTo(budgetType.toString());
    assertThat(budgetTrendStats.getBudgetDetails().getAppliesTo()[0]).isEqualTo("appId");
    assertThat(budgetTrendStats.getBudgetDetails().getEnvironment()).isEqualTo(environmentType.toString());
    assertThat(budgetTrendStats.getStatus().equals(STATUS_NOT_ON_TRACK));
  }
}
