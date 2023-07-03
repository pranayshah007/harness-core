/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.remote.resources;

import static io.harness.ccm.rbac.CCMRbacPermissions.BUDGET_VIEW;
import static io.harness.rule.OwnerRule.ANMOL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.accesscontrol.NGAccessDeniedException;
import io.harness.category.element.UnitTests;
import io.harness.ccm.budget.AlertThreshold;
import io.harness.ccm.budget.BudgetBreakdown;
import io.harness.ccm.budget.BudgetPeriod;
import io.harness.ccm.budget.BudgetType;
import io.harness.ccm.budget.PerspectiveBudgetScope;
import io.harness.ccm.commons.entities.CCMSortOrder;
import io.harness.ccm.commons.entities.billing.Budget;
import io.harness.ccm.commons.entities.billing.BudgetSortType;
import io.harness.ccm.commons.entities.budget.BudgetCostData;
import io.harness.ccm.commons.entities.budget.BudgetData;
import io.harness.ccm.graphql.core.budget.BudgetService;
import io.harness.ccm.rbac.CCMRbacHelper;
import io.harness.ccm.views.entities.CEView;
import io.harness.ccm.views.service.CEViewService;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.outbox.api.OutboxService;
import io.harness.rule.Owner;
import io.harness.telemetry.TelemetryReporter;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@RunWith(MockitoJUnitRunner.class)
public class BudgetResourceTest extends CategoryTest {
  @Mock private BudgetService mockBudgetService;
  @Mock private CEViewService mockCeViewService;
  @Mock private TransactionTemplate mockTransactionTemplate;
  @Mock private TelemetryReporter telemetryReporter;
  @Mock private OutboxService mockOutboxService;
  @Mock private CCMRbacHelper mockRbacHelper;

  @InjectMocks private BudgetResource budgetResourceUnderTest;

  private Budget budget;
  private CEView ceView;

  private final String ACCOUNT_ID = "accountId";
  private final String PERSPECTIVE_UUID = "PerspectiveUuid";
  private final String FOLDER_ID = "folderId";
  private final String BUDGET_ID = "budgetId";
  private final String BUDGET_CREATED = "Budget Created";
  private final String BUDGET_CLONED = "Budget Cloned";
  private final String BUDGET_DELETED = "Successfully deleted the budget";
  private final String BUDGET_UPDATED = "Successfully updated the budget";
  private final String BUDGET_NAME = "Budget Name";

  @Before
  public void setUp() throws IllegalAccessException, IOException {
    budget = Budget.builder()
                 .name(BUDGET_NAME)
                 .accountId(ACCOUNT_ID)
                 .scope(PerspectiveBudgetScope.builder().viewId(PERSPECTIVE_UUID).build())
                 .type(BudgetType.SPECIFIED_AMOUNT)
                 .period(BudgetPeriod.DAILY)
                 .alertThresholds(new AlertThreshold[] {AlertThreshold.builder().build()})
                 .isNgBudget(true)
                 .build();
    ceView = CEView.builder().folderId(FOLDER_ID).build();
    when(mockCeViewService.get(PERSPECTIVE_UUID)).thenReturn(ceView);
  }

  @Test
  @Owner(developers = ANMOL)
  @Category(UnitTests.class)
  public void testSave() throws Exception {
    when(mockBudgetService.create(budget)).thenReturn(BUDGET_CREATED);
    when(mockTransactionTemplate.execute(any(TransactionCallback.class))).thenReturn(BUDGET_CREATED);

    final ResponseDTO<String> result = budgetResourceUnderTest.save(ACCOUNT_ID, budget);

    verify(mockRbacHelper).checkBudgetEditPermission(ACCOUNT_ID, null, null, FOLDER_ID);
    assertThat(result.getData()).isEqualTo(BUDGET_CREATED);
  }

  @Test
  @Owner(developers = ANMOL)
  @Category(UnitTests.class)
  public void testClone() {
    when(mockBudgetService.get(BUDGET_ID, ACCOUNT_ID)).thenReturn(budget);
    when(mockBudgetService.clone(BUDGET_ID, BUDGET_NAME, ACCOUNT_ID)).thenReturn(BUDGET_CLONED);

    final ResponseDTO<String> result = budgetResourceUnderTest.clone(ACCOUNT_ID, BUDGET_ID, BUDGET_NAME);

    verify(mockRbacHelper).checkBudgetEditPermission(ACCOUNT_ID, null, null, FOLDER_ID);
    assertThat(result.getData()).isEqualTo(BUDGET_CLONED);
  }

  @Test
  @Owner(developers = ANMOL)
  @Category(UnitTests.class)
  public void testGet() throws Exception {
    when(mockBudgetService.get(BUDGET_ID, ACCOUNT_ID)).thenReturn(budget);

    final ResponseDTO<Budget> result = budgetResourceUnderTest.get(ACCOUNT_ID, BUDGET_ID);

    verify(mockRbacHelper).checkBudgetViewPermission(ACCOUNT_ID, null, null, FOLDER_ID);
    assertThat(result.getData()).isEqualTo(budget);
  }

  @Test
  @Owner(developers = ANMOL)
  @Category(UnitTests.class)
  public void testList() {
    when(mockBudgetService.list(ACCOUNT_ID, BudgetSortType.NAME, CCMSortOrder.ASCENDING)).thenReturn(List.of(budget));
    when(mockCeViewService.getPerspectiveFolderIds(ACCOUNT_ID, List.of(PERSPECTIVE_UUID)))
        .thenReturn(Set.of(FOLDER_ID));
    final HashMap<String, String> perspectiveAndFolderUuid =
        new HashMap<>(Map.ofEntries(Map.entry(PERSPECTIVE_UUID, FOLDER_ID)));
    when(mockCeViewService.getPerspectiveIdAndFolderId(ACCOUNT_ID, List.of(PERSPECTIVE_UUID)))
        .thenReturn(perspectiveAndFolderUuid);

    when(mockRbacHelper.checkFolderIdsGivenPermission(ACCOUNT_ID, null, null, Set.of(FOLDER_ID), BUDGET_VIEW))
        .thenReturn(Set.of(FOLDER_ID));

    final ResponseDTO<List<Budget>> result =
        budgetResourceUnderTest.list(ACCOUNT_ID, BudgetSortType.NAME, CCMSortOrder.ASCENDING);

    assertThat(result.getData()).isEqualTo(List.of(budget));
  }

  @Test(expected = NGAccessDeniedException.class)
  @Owner(developers = ANMOL)
  @Category(UnitTests.class)
  public void testList_NoFolderAllowed() {
    when(mockBudgetService.list(ACCOUNT_ID, BudgetSortType.NAME, CCMSortOrder.ASCENDING)).thenReturn(List.of(budget));
    when(mockCeViewService.getPerspectiveFolderIds(ACCOUNT_ID, List.of(PERSPECTIVE_UUID)))
        .thenReturn(Set.of(FOLDER_ID));
    final HashMap<String, String> perspectiveAndFolderUuid =
        new HashMap<>(Map.ofEntries(Map.entry(PERSPECTIVE_UUID, FOLDER_ID)));
    when(mockCeViewService.getPerspectiveIdAndFolderId(ACCOUNT_ID, List.of(PERSPECTIVE_UUID)))
        .thenReturn(perspectiveAndFolderUuid);

    when(mockRbacHelper.checkFolderIdsGivenPermission(ACCOUNT_ID, null, null, Set.of(FOLDER_ID), BUDGET_VIEW))
        .thenReturn(Set.of());

    budgetResourceUnderTest.list(ACCOUNT_ID, BudgetSortType.NAME, CCMSortOrder.ASCENDING);
  }

  @Test
  @Owner(developers = ANMOL)
  @Category(UnitTests.class)
  public void testListForPerspective() {
    when(mockBudgetService.list(ACCOUNT_ID, PERSPECTIVE_UUID)).thenReturn(List.of(budget));

    final ResponseDTO<List<Budget>> result = budgetResourceUnderTest.list(ACCOUNT_ID, PERSPECTIVE_UUID);

    verify(mockRbacHelper).checkBudgetViewPermission(ACCOUNT_ID, null, null, FOLDER_ID);
    assertThat(result.getData()).isEqualTo(List.of(budget));
  }

  @Test
  @Owner(developers = ANMOL)
  @Category(UnitTests.class)
  public void testListForPerspective_NoBudgets() {
    when(mockBudgetService.list(ACCOUNT_ID, PERSPECTIVE_UUID)).thenReturn(List.of());

    final ResponseDTO<List<Budget>> result = budgetResourceUnderTest.list(ACCOUNT_ID, PERSPECTIVE_UUID);

    verify(mockRbacHelper).checkBudgetViewPermission(ACCOUNT_ID, null, null, FOLDER_ID);
    assertThat(result.getData()).isEqualTo(List.of());
  }

  @Test
  @Owner(developers = ANMOL)
  @Category(UnitTests.class)
  public void testUpdate() throws Exception {
    when(mockBudgetService.get(BUDGET_ID, ACCOUNT_ID)).thenReturn(budget);
    when(mockTransactionTemplate.execute(any(TransactionCallback.class))).thenReturn(BUDGET_UPDATED);

    final ResponseDTO<String> result = budgetResourceUnderTest.update(ACCOUNT_ID, BUDGET_ID, budget);

    verify(mockRbacHelper).checkBudgetEditPermission(ACCOUNT_ID, null, null, FOLDER_ID);
    verify(mockBudgetService).update(BUDGET_ID, budget);
    assertThat(result.getData()).isEqualTo(BUDGET_UPDATED);
  }

  @Test
  @Owner(developers = ANMOL)
  @Category(UnitTests.class)
  public void testDelete() throws Exception {
    when(mockBudgetService.get(BUDGET_ID, ACCOUNT_ID)).thenReturn(budget);
    when(mockTransactionTemplate.execute(any(TransactionCallback.class))).thenReturn(BUDGET_DELETED);

    final ResponseDTO<String> result = budgetResourceUnderTest.delete(ACCOUNT_ID, BUDGET_ID);

    verify(mockRbacHelper).checkBudgetDeletePermission(ACCOUNT_ID, null, null, FOLDER_ID);
    verify(mockBudgetService).delete(BUDGET_ID, ACCOUNT_ID);
    assertThat(result.getData()).isEqualTo(BUDGET_DELETED);
  }

  @Test
  @Owner(developers = ANMOL)
  @Category(UnitTests.class)
  public void testGetLastMonthCost() {
    when(mockCeViewService.getLastMonthCostForPerspective(ACCOUNT_ID, PERSPECTIVE_UUID)).thenReturn(100.0);

    final ResponseDTO<Double> result = budgetResourceUnderTest.getLastMonthCost(ACCOUNT_ID, PERSPECTIVE_UUID);

    verify(mockRbacHelper).checkBudgetViewPermission(ACCOUNT_ID, null, null, FOLDER_ID);
    assertThat(result.getData()).isEqualTo(100.0);
  }

  @Test
  @Owner(developers = ANMOL)
  @Category(UnitTests.class)
  public void testGetLastMonthCost_NoDataReturnsNull() {
    when(mockCeViewService.getLastMonthCostForPerspective(ACCOUNT_ID, PERSPECTIVE_UUID)).thenReturn(null);

    final ResponseDTO<Double> result = budgetResourceUnderTest.getLastMonthCost(ACCOUNT_ID, PERSPECTIVE_UUID);

    verify(mockRbacHelper).checkBudgetViewPermission(ACCOUNT_ID, null, null, FOLDER_ID);
    assertThat(result.getData()).isNull();
  }

  @Test
  @Owner(developers = ANMOL)
  @Category(UnitTests.class)
  public void testGetForecastCost() {
    when(mockCeViewService.getForecastCostForPerspective(ACCOUNT_ID, PERSPECTIVE_UUID)).thenReturn(100.0);

    final ResponseDTO<Double> result = budgetResourceUnderTest.getForecastCost(ACCOUNT_ID, PERSPECTIVE_UUID);

    verify(mockRbacHelper).checkBudgetViewPermission(ACCOUNT_ID, null, null, FOLDER_ID);
    assertThat(result.getData()).isEqualTo(100.0);
  }

  @Test
  @Owner(developers = ANMOL)
  @Category(UnitTests.class)
  public void testGetForecastCost_NoDataReturnsNull() {
    when(mockCeViewService.getForecastCostForPerspective(ACCOUNT_ID, PERSPECTIVE_UUID)).thenReturn(null);

    final ResponseDTO<Double> result = budgetResourceUnderTest.getForecastCost(ACCOUNT_ID, PERSPECTIVE_UUID);

    verify(mockRbacHelper).checkBudgetViewPermission(ACCOUNT_ID, null, null, FOLDER_ID);
    assertThat(result.getData()).isNull();
  }

  @Test
  @Owner(developers = ANMOL)
  @Category(UnitTests.class)
  public void testGetCostDetails() {
    when(mockBudgetService.get(BUDGET_ID, ACCOUNT_ID)).thenReturn(budget);
    BudgetData budgetData =
        BudgetData.builder()
            .costData(List.of(BudgetCostData.builder().budgeted(100).actualCost(70).forecastCost(120).build()))
            .build();
    when(mockBudgetService.getBudgetTimeSeriesStats(budget, BudgetBreakdown.YEARLY)).thenReturn(budgetData);

    final ResponseDTO<BudgetData> result =
        budgetResourceUnderTest.getCostDetails(ACCOUNT_ID, BUDGET_ID, BudgetBreakdown.YEARLY);

    verify(mockRbacHelper).checkBudgetViewPermission(ACCOUNT_ID, null, null, FOLDER_ID);
    assertThat(result.getData()).isEqualTo(budgetData);
  }
}
