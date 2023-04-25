/*
 * Copyright 2022 Harness Inc. All rights reserved.
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.ccm.budget.AlertThreshold;
import io.harness.ccm.budget.ApplicationBudgetScope;
import io.harness.ccm.budget.BudgetServiceImpl;
import io.harness.ccm.budget.BudgetType;
import io.harness.ccm.budget.EnvironmentType;
import io.harness.ccm.commons.entities.billing.Budget;
import io.harness.rule.Owner;

import software.wings.graphql.datafetcher.AbstractDataFetcherTestBase;
import software.wings.graphql.schema.query.QLBudgetQueryParameters;
import software.wings.graphql.schema.type.aggregation.budget.QLBudgetTableData;
import software.wings.service.intfc.ce.CeAccountExpirationChecker;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@TargetModule(HarnessModule._375_CE_GRAPHQL)
@OwnedBy(CE)
public class BudgetListDataFetcherTest extends AbstractDataFetcherTestBase {
  @Mock BudgetServiceImpl budgetService;
  @Mock CeAccountExpirationChecker accountChecker;
  @InjectMocks BudgetListDataFetcher budgetListDataFetcher;

  private String accountId = "ACCOUNT_ID";
  private String applicationId1 = "APPLICATION_ID_1";
  private String applicationId2 = "APPLICATION_ID_2";
  private String[] applicationIds = {applicationId1, applicationId2};
  private String budgetId = "BUDGET_ID";
  private String budgetName = "BUDGET_NAME";
  private BudgetType budgetType = SPECIFIED_AMOUNT;
  private double budgetAmount = 25000.0;
  private Double[] alertAt = {0.5};

  private AlertThreshold alertThreshold;
  private Budget budget;
  private QLBudgetTableData budgetDetails;
  private QLBudgetQueryParameters queryParameters;

  @Before
  public void setUp() throws SQLException {
    alertThreshold = AlertThreshold.builder().percentage(0.5).basedOn(ACTUAL_COST).build();
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
    budgetDetails = QLBudgetTableData.builder()
                        .name(budget.getName())
                        .id(budget.getUuid())
                        .type(budget.getType().toString())
                        .scopeType("APPLICATION")
                        .appliesTo(applicationIds)
                        .alertAt(alertAt)
                        .budgetedAmount(budget.getBudgetAmount())
                        .actualAmount(1000.0)
                        .build();
    queryParameters = new QLBudgetQueryParameters(budgetId);
    when(budgetService.list(accountId)).thenReturn(Arrays.asList(budget));
    when(budgetService.listCgBudgets(accountId)).thenReturn(Arrays.asList(budget));
    when(budgetService.getBudgetDetails(budget)).thenReturn(budgetDetails);
    doNothing().when(accountChecker).checkIsCeEnabled(anyString());
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testFetch() {
    List<QLBudgetTableData> budgetTableData = budgetListDataFetcher.fetch(queryParameters, accountId);
    assertThat(budgetTableData).hasSize(1);
    assertThat(budgetTableData.get(0).getName()).isEqualTo(budgetName);
    assertThat(budgetTableData.get(0).getId()).isEqualTo(budgetId);
  }
}
