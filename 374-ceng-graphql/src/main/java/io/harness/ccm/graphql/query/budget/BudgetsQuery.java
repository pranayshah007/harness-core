/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.graphql.query.budget;

import static io.harness.ccm.budget.AlertThresholdBase.ACTUAL_COST;
import static io.harness.ccm.budget.AlertThresholdBase.FORECASTED_COST;
import static io.harness.ccm.rbac.CCMRbacHelperImpl.PERMISSION_MISSING_MESSAGE;
import static io.harness.ccm.rbac.CCMRbacHelperImpl.RESOURCE_FOLDER;
import static io.harness.ccm.rbac.CCMRbacPermissions.BUDGET_VIEW;

import io.harness.accesscontrol.NGAccessDeniedException;
import io.harness.ccm.budget.BudgetBreakdown;
import io.harness.ccm.budget.BudgetSummary;
import io.harness.ccm.budget.dao.BudgetDao;
import io.harness.ccm.budget.utils.BudgetUtils;
import io.harness.ccm.budgetGroup.BudgetGroup;
import io.harness.ccm.budgetGroup.dao.BudgetGroupDao;
import io.harness.ccm.budgetGroup.service.BudgetGroupService;
import io.harness.ccm.commons.entities.CCMSortOrder;
import io.harness.ccm.commons.entities.billing.Budget;
import io.harness.ccm.commons.entities.billing.BudgetSortType;
import io.harness.ccm.commons.entities.budget.BudgetData;
import io.harness.ccm.graphql.core.budget.BudgetCostService;
import io.harness.ccm.graphql.core.budget.BudgetService;
import io.harness.ccm.graphql.utils.GraphQLUtils;
import io.harness.ccm.graphql.utils.annotations.GraphQLApi;
import io.harness.ccm.rbac.CCMRbacHelper;
import io.harness.ccm.views.service.CEViewService;
import io.harness.exception.WingsException;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.leangen.graphql.annotations.GraphQLArgument;
import io.leangen.graphql.annotations.GraphQLEnvironment;
import io.leangen.graphql.annotations.GraphQLQuery;
import io.leangen.graphql.execution.ResolutionEnvironment;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@GraphQLApi
public class BudgetsQuery {
  @Inject private GraphQLUtils graphQLUtils;
  @Inject private BudgetDao budgetDao;
  @Inject private BudgetGroupDao budgetGroupDao;
  @Inject private CEViewService ceViewService;
  @Inject private BudgetService budgetService;
  @Inject private BudgetGroupService budgetGroupService;
  @Inject private BudgetCostService budgetCostService;
  @Inject private CCMRbacHelper rbacHelper;

  @GraphQLQuery(name = "budgetSummary", description = "Budget card for perspectives")
  public BudgetSummary budgetSummaryForPerspective(@GraphQLArgument(name = "perspectiveId") String perspectiveId,
      @GraphQLArgument(name = "budgetId") String budgetId, @GraphQLEnvironment final ResolutionEnvironment env) {
    final String accountId = graphQLUtils.getAccountIdentifier(env);
    try {
      Budget budget = null;
      if (perspectiveId != null) {
        List<Budget> perspectiveBudgets = budgetService.list(accountId, perspectiveId);
        if (!perspectiveBudgets.isEmpty()) {
          // UI allows only one budget per perspective
          budget = perspectiveBudgets.get(0);
        }
      } else if (budgetId != null) {
        budget = budgetDao.get(budgetId, accountId);
      }

      if (budget != null) {
        return buildBudgetSummary(budget, true,
            ceViewService
                .getPerspectiveFolderIds(
                    accountId, Collections.singletonList(BudgetUtils.getPerspectiveIdForBudget(budget)))
                .iterator()
                .next());
      }

      // If budget is null and budgetId is not null
      // the budget group id might have been passed as budgetId
      if (budget == null && budgetId != null) {
        return buildBudgetGroupSummary(budgetGroupDao.get(budgetId, accountId));
      }
    } catch (Exception e) {
      log.info("Exception while fetching budget for given perspective: ", e);
    }
    return null;
  }

  @GraphQLQuery(name = "budgetList", description = "Budget List")
  public List<BudgetSummary> budgetList(@GraphQLArgument(name = "fetchOnlyPerspectiveBudgets",
                                            defaultValue = "false") boolean fetchOnlyPerspectiveBudgets,
      @GraphQLArgument(name = "limit", defaultValue = "1000") Integer limit,
      @GraphQLArgument(name = "offset", defaultValue = "0") Integer offset,
      @GraphQLEnvironment final ResolutionEnvironment env,
      @GraphQLArgument(name = "sortOrder") CCMSortOrder ccmSortOrder,
      @GraphQLArgument(name = "budgetSortType") BudgetSortType budgetSortType) {
    final String accountId = graphQLUtils.getAccountIdentifier(env);
    List<Budget> budgets = budgetDao.list(accountId, limit, offset, budgetSortType, ccmSortOrder);
    if (fetchOnlyPerspectiveBudgets) {
      budgets = budgets.stream().filter(BudgetUtils::isPerspectiveBudget).collect(Collectors.toList());
    }
    List<String> perspectiveIds = budgets.stream()
                                      .filter(BudgetUtils::isPerspectiveBudget)
                                      .map(BudgetUtils::getPerspectiveIdForBudget)
                                      .collect(Collectors.toList());
    Set<String> folderIds = ceViewService.getPerspectiveFolderIds(accountId, perspectiveIds);
    HashMap<String, String> perspectiveIdAndFolderIds =
        ceViewService.getPerspectiveIdAndFolderId(accountId, perspectiveIds);
    List<Budget> allowedBudgets = null;
    if (folderIds != null) {
      Set<String> allowedFolderIds =
          rbacHelper.checkFolderIdsGivenPermission(accountId, null, null, folderIds, BUDGET_VIEW);
      allowedBudgets = budgets.stream()
                           .filter(budget
                               -> BudgetUtils.isPerspectiveBudget(budget)
                                   && allowedFolderIds.contains(
                                       perspectiveIdAndFolderIds.get(BudgetUtils.getPerspectiveIdForBudget(budget))))
                           .collect(Collectors.toList());
    }
    List<BudgetSummary> budgetSummaryList = new ArrayList<>();
    if (allowedBudgets == null || allowedBudgets.size() == 0) {
      if (budgets.size() > 0) {
        throw new NGAccessDeniedException(
            String.format(PERMISSION_MISSING_MESSAGE, BUDGET_VIEW, RESOURCE_FOLDER), WingsException.USER, null);
      }
      return budgetSummaryList;
    }
    allowedBudgets.forEach(budget
        -> budgetSummaryList.add(buildBudgetSummary(
            budget, false, perspectiveIdAndFolderIds.get(BudgetUtils.getPerspectiveIdForBudget(budget)))));

    return budgetSummaryList;
  }

  @GraphQLQuery(name = "budgetCostData", description = "Budget cost data")
  public BudgetData budgetCostData(@GraphQLArgument(name = "budgetId", defaultValue = "") String budgetId,
      @GraphQLEnvironment final ResolutionEnvironment env,
      @GraphQLArgument(name = "breakdown") BudgetBreakdown breakdown) {
    final String accountId = graphQLUtils.getAccountIdentifier(env);
    // Check if any budget with this id exists
    Budget budget = budgetDao.get(budgetId, accountId);

    // If budget exists we return time series stats of the budget
    if (budget != null) {
      return budgetService.getBudgetTimeSeriesStats(budget, breakdown == null ? BudgetBreakdown.YEARLY : breakdown);
    }

    // If no budget exists with such id then we check if it's a budget group
    // And we get the time series stats of the budget group
    return budgetGroupService.getBudgetGroupTimeSeriesStats(
        budgetGroupDao.get(budgetId, accountId), breakdown == null ? BudgetBreakdown.YEARLY : breakdown);
  }

  @GraphQLQuery(name = "budgetSummaryList", description = "List of budget cards for perspectives")
  public List<BudgetSummary> listBudgetSummaryForPerspective(
      @GraphQLArgument(name = "perspectiveId") String perspectiveId,
      @GraphQLEnvironment final ResolutionEnvironment env) {
    final String accountId = graphQLUtils.getAccountIdentifier(env);
    List<BudgetSummary> budgetSummaryList = new ArrayList<>();
    try {
      List<Budget> perspectiveBudgets = new ArrayList<>();
      if (perspectiveId != null) {
        List<Budget> budgets = budgetDao.list(accountId, Integer.MAX_VALUE - 1, 0, null, null);
        perspectiveBudgets =
            budgets.stream()
                .filter(
                    perspectiveBudget -> BudgetUtils.isBudgetBasedOnGivenPerspective(perspectiveBudget, perspectiveId))
                .collect(Collectors.toList());
      }

      perspectiveBudgets.forEach(budget
          -> budgetSummaryList.add(buildBudgetSummary(budget, false,
              ceViewService
                  .getPerspectiveFolderIds(
                      accountId, Collections.singletonList(BudgetUtils.getPerspectiveIdForBudget(budget)))
                  .iterator()
                  .next())));

    } catch (Exception e) {
      log.info("Exception while fetching budget summary cards for given perspective: ", e);
    }
    return budgetSummaryList;
  }

  private BudgetSummary buildBudgetSummary(Budget budget, boolean fetchLatestSpend, String folderId) {
    Double actualCost = budget.getActualCost();
    if (fetchLatestSpend) {
      actualCost = budgetCostService.getActualCost(budget);
    }
    return BudgetSummary.builder()
        .id(budget.getUuid())
        .name(budget.getName())
        .perspectiveId(BudgetUtils.getPerspectiveIdForBudget(budget))
        .perspectiveName(BudgetUtils.getPerspectiveNameForBudget(budget))
        .budgetAmount(budget.getBudgetAmount())
        .actualCost(actualCost)
        .forecastCost(budget.getForecastCost())
        .timeLeft(BudgetUtils.getTimeLeftForBudget(budget.getEndTime()))
        .timeUnit(BudgetUtils.DEFAULT_TIME_UNIT)
        .timeScope(BudgetUtils.getBudgetPeriod(budget).toString().toLowerCase())
        .actualCostAlerts(BudgetUtils.getAlertThresholdsForBudget(budget.getAlertThresholds(), ACTUAL_COST))
        .forecastCostAlerts(BudgetUtils.getAlertThresholdsForBudget(budget.getAlertThresholds(), FORECASTED_COST))
        .alertThresholds(budget.getAlertThresholds())
        .period(BudgetUtils.getBudgetPeriod(budget))
        .type(budget.getType())
        .growthRate(BudgetUtils.getBudgetGrowthRate(budget))
        .startTime(BudgetUtils.getBudgetStartTime(budget.getStartTime(), budget.getPeriod()))
        .budgetMonthlyBreakdown(budget.getBudgetMonthlyBreakdown())
        .isBudgetGroup(false)
        .disableCurrencyWarning(budget.getDisableCurrencyWarning())
        .folderId(folderId)
        .parentId(budget.getParentBudgetGroupId())
        .build();
  }

  private BudgetSummary buildBudgetGroupSummary(BudgetGroup budgetGroup) {
    if (budgetGroup == null) {
      return null;
    }

    return BudgetSummary.builder()
        .id(budgetGroup.getUuid())
        .name(budgetGroup.getName())
        .perspectiveId(null)
        .perspectiveName(null)
        .budgetAmount(budgetGroup.getBudgetGroupAmount())
        .actualCost(budgetGroup.getActualCost())
        .forecastCost(budgetGroup.getForecastCost())
        .timeLeft(BudgetUtils.getTimeLeftForBudget(budgetGroup.getEndTime()))
        .timeUnit(BudgetUtils.DEFAULT_TIME_UNIT)
        .timeScope(budgetGroup.getPeriod().toString().toLowerCase())
        .actualCostAlerts(BudgetUtils.getAlertThresholdsForBudget(budgetGroup.getAlertThresholds(), ACTUAL_COST))
        .forecastCostAlerts(BudgetUtils.getAlertThresholdsForBudget(budgetGroup.getAlertThresholds(), FORECASTED_COST))
        .alertThresholds(budgetGroup.getAlertThresholds())
        .period(budgetGroup.getPeriod())
        .type(null)
        .growthRate(null)
        .startTime(BudgetUtils.getBudgetStartTime(budgetGroup.getStartTime(), budgetGroup.getPeriod()))
        .budgetMonthlyBreakdown(budgetGroup.getBudgetGroupMonthlyBreakdown())
        .isBudgetGroup(true)
        .parentId(budgetGroup.getParentBudgetGroupId())
        .build();
  }
}
