/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.graphql.core.budget;

import static io.harness.rule.OwnerRule.ANMOL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ccm.bigQuery.BigQueryService;
import io.harness.ccm.budget.AlertThreshold;
import io.harness.ccm.budget.BudgetPeriod;
import io.harness.ccm.budget.BudgetType;
import io.harness.ccm.budget.PerspectiveBudgetScope;
import io.harness.ccm.budget.dao.BudgetDao;
import io.harness.ccm.budget.utils.BudgetUtils;
import io.harness.ccm.budgetGroup.dao.BudgetGroupDao;
import io.harness.ccm.budgetGroup.service.BudgetGroupService;
import io.harness.ccm.commons.entities.billing.Budget;
import io.harness.ccm.commons.utils.BigQueryHelper;
import io.harness.ccm.graphql.core.perspectives.PerspectiveTimeSeriesHelper;
import io.harness.ccm.views.entities.CEView;
import io.harness.ccm.views.graphql.ViewsQueryHelper;
import io.harness.ccm.views.service.CEViewService;
import io.harness.ccm.views.service.ViewsBillingService;
import io.harness.rule.Owner;

import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.Silent.class)
public class BudgetServiceImplTest extends CategoryTest {
  @Mock private BudgetDao budgetDao;
  @Mock private BudgetGroupDao budgetGroupDao;
  @Mock private BudgetGroupService budgetGroupService;
  @Mock private CEViewService ceViewService;
  @Mock ViewsBillingService viewsBillingService;
  @Mock ViewsQueryHelper viewsQueryHelper;
  @Mock PerspectiveTimeSeriesHelper perspectiveTimeSeriesHelper;
  @Mock BigQueryService bigQueryService;
  @Mock BigQueryHelper bigQueryHelper;
  @Mock BudgetCostService budgetCostService;
  @InjectMocks private BudgetServiceImpl budgetService;
  private Budget budget;
  private final String BUDGET_NAME = "budgetName";
  private final String ACCOUNT_ID = "accountId";
  private final String PERSPECTIVE_UUID = "perspectiveUuid";
  private final String BUDGET_UUID = "BudgetUuid";

  @Before
  public void setUp() throws Exception {
    budget = Budget.builder()
                 .name(BUDGET_NAME)
                 .budgetAmount(100.0)
                 .startTime(BudgetUtils.getStartOfCurrentDay())
                 .period(BudgetPeriod.MONTHLY)
                 .uuid(BUDGET_UUID)
                 .accountId(ACCOUNT_ID)
                 .scope(PerspectiveBudgetScope.builder().viewId(PERSPECTIVE_UUID).build())
                 .type(BudgetType.SPECIFIED_AMOUNT)
                 .period(BudgetPeriod.DAILY)
                 .alertThresholds(new AlertThreshold[] {AlertThreshold.builder().build()})
                 .isNgBudget(true)
                 .build();
  }

  @Test
  @Owner(developers = ANMOL)
  @Category(UnitTests.class)
  public void create() {
    when(budgetDao.list(ACCOUNT_ID, BUDGET_NAME)).thenReturn(Collections.emptyList());
    when(ceViewService.get(PERSPECTIVE_UUID)).thenReturn(CEView.builder().uuid(PERSPECTIVE_UUID).build());
    when(budgetCostService.getActualCost(budget)).thenReturn(0.0);
    when(budgetCostService.getForecastCost(budget)).thenReturn(120.0);
    when(budgetCostService.getLastPeriodCost(budget)).thenReturn(90.0);
    when(budgetCostService.getBudgetHistory(budget)).thenReturn(null);
    when(budgetDao.save(budget)).thenReturn(BUDGET_UUID);
    String actualBudgetUuid = budgetService.create(budget);
    verify(budgetDao).save(budget);
    assertThat(actualBudgetUuid).isEqualTo(BUDGET_UUID);
  }

  @Test
  @Owner(developers = ANMOL)
  @Category(UnitTests.class)
  public void testClone() {}

  @Test
  @Owner(developers = ANMOL)
  @Category(UnitTests.class)
  public void get() {}

  @Test
  @Owner(developers = ANMOL)
  @Category(UnitTests.class)
  public void update() {}

  @Test
  @Owner(developers = ANMOL)
  @Category(UnitTests.class)
  public void updatePerspectiveName() {}

  @Test
  @Owner(developers = ANMOL)
  @Category(UnitTests.class)
  public void list() {}

  @Test
  @Owner(developers = ANMOL)
  @Category(UnitTests.class)
  public void testList() {}

  @Test
  @Owner(developers = ANMOL)
  @Category(UnitTests.class)
  public void delete() {}

  @Test
  @Owner(developers = ANMOL)
  @Category(UnitTests.class)
  public void deleteBudgetsForPerspective() {}

  @Test
  @Owner(developers = ANMOL)
  @Category(UnitTests.class)
  public void getBudgetTimeSeriesStats() {}

  @Test
  @Owner(developers = ANMOL)
  @Category(UnitTests.class)
  public void isBudgetBasedOnGivenPerspective() {}

  @Test
  @Owner(developers = ANMOL)
  @Category(UnitTests.class)
  public void updateBudgetCosts() {}

  @Test
  @Owner(developers = ANMOL)
  @Category(UnitTests.class)
  public void updateBudgetHistory() {}

  @Test
  @Owner(developers = ANMOL)
  @Category(UnitTests.class)
  public void getRootBudgetGroup() {}
}
