/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.budgetGroup.utils;

import static io.harness.ccm.budget.AlertThresholdBase.ACTUAL_COST;
import static io.harness.ccm.budget.AlertThresholdBase.FORECASTED_COST;
import static io.harness.ccm.budget.BudgetBreakdown.MONTHLY;
import static io.harness.ccm.budget.BudgetPeriod.DAILY;
import static io.harness.ccm.budget.BudgetPeriod.YEARLY;

import io.harness.ccm.budget.AlertThreshold;
import io.harness.ccm.budget.AlertThresholdBase;
import io.harness.ccm.budget.BudgetBreakdown;
import io.harness.ccm.budget.BudgetMonthlyBreakdown;
import io.harness.ccm.budget.BudgetPeriod;
import io.harness.ccm.budget.BudgetSummary;
import io.harness.ccm.budget.ValueDataPoint;
import io.harness.ccm.budget.utils.BudgetUtils;
import io.harness.ccm.budgetGroup.BudgetGroup;
import io.harness.ccm.commons.entities.billing.Budget;
import io.harness.exception.InvalidRequestException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BudgetGroupUtils {
  public static final String BUDGET_GROUP_NAME_EXISTS_EXCEPTION =
      "Error in creating budget group. Budget group with given name already exists";
  public static final String INVALID_CHILD_ENTITY_ID_EXCEPTION =
      "Error in performing operation. Some of the child entity IDs are invalid.";
  public static final String INVALID_CHILD_ENTITY_START_TIME_EXCEPTION =
      "Error in performing operation. StartTime of child entities don't match.";
  public static final String CHILD_ENTITY_START_TIME_NOT_PRESENT_EXCEPTION =
      "Error in performing operation. Start time not found.";
  public static final String INVALID_CHILD_ENTITY_PERIOD_EXCEPTION =
      "Error in performing operation. Period of child entities don't match.";
  public static final String CHILD_ENTITY_PERIOD_NOT_PRESENT_EXCEPTION =
      "Error in performing operation. Period not found.";
  public static final String INVALID_CHILD_ENTITY_BUDGET_BREAKDOWN_EXCEPTION =
      "Error in performing operation. Budget breakdown of child entities don't match.";
  public static final String CHILD_ENTITY_BUDGET_BREAKDOWN_NOT_PRESENT_EXCEPTION =
      "Error in performing operation. Budget breakdown not found.";

  public static void validateBudgetGroup(BudgetGroup budgetGroup, List<BudgetGroup> existingBudgetGroups) {
    populateDefaultBudgetGroupBreakdown(budgetGroup);
    validateBudgetGroupName(budgetGroup, existingBudgetGroups);
  }

  public static BudgetPeriod getStartOfPeriodForChildBudgets(List<Budget> childBudgets) {
    Set<BudgetPeriod> timePeriods = childBudgets.stream().map(Budget::getPeriod).collect(Collectors.toSet());
    if (timePeriods.size() > 1) {
      throw new InvalidRequestException(INVALID_CHILD_ENTITY_PERIOD_EXCEPTION);
    }
    if (timePeriods.stream().findFirst().isPresent()) {
      return timePeriods.stream().findFirst().get();
    } else {
      throw new InvalidRequestException(CHILD_ENTITY_PERIOD_NOT_PRESENT_EXCEPTION);
    }
  }

  public static BudgetPeriod getStartOfPeriodForChildBudgetGroups(List<BudgetGroup> childBudgetGroups) {
    Set<BudgetPeriod> timePeriods = childBudgetGroups.stream().map(BudgetGroup::getPeriod).collect(Collectors.toSet());
    if (timePeriods.size() > 1) {
      throw new InvalidRequestException(INVALID_CHILD_ENTITY_START_TIME_EXCEPTION);
    }
    if (timePeriods.stream().findFirst().isPresent()) {
      return timePeriods.stream().findFirst().get();
    } else {
      throw new InvalidRequestException(CHILD_ENTITY_START_TIME_NOT_PRESENT_EXCEPTION);
    }
  }

  public static Long getStartTimeForChildBudgets(List<Budget> childBudgets) {
    Set<Long> startTimes = childBudgets.stream().map(Budget::getStartTime).collect(Collectors.toSet());
    if (startTimes.size() > 1) {
      throw new InvalidRequestException(INVALID_CHILD_ENTITY_START_TIME_EXCEPTION);
    }
    if (startTimes.stream().findFirst().isPresent()) {
      return startTimes.stream().findFirst().get();
    } else {
      throw new InvalidRequestException(CHILD_ENTITY_START_TIME_NOT_PRESENT_EXCEPTION);
    }
  }

  public static Long getStartTimeForChildBudgetGroups(List<BudgetGroup> childBudgetGroups) {
    Set<Long> startTimes = childBudgetGroups.stream().map(BudgetGroup::getStartTime).collect(Collectors.toSet());
    if (startTimes.size() > 1) {
      throw new InvalidRequestException(INVALID_CHILD_ENTITY_START_TIME_EXCEPTION);
    }
    if (startTimes.stream().findFirst().isPresent()) {
      return startTimes.stream().findFirst().get();
    } else {
      throw new InvalidRequestException(CHILD_ENTITY_START_TIME_NOT_PRESENT_EXCEPTION);
    }
  }

  public static BudgetBreakdown getBudgetBreakdownChildBudgets(List<Budget> childBudgets) {
    Set<BudgetBreakdown> budgetBreakdowns = childBudgets.stream()
                                                .map(budget -> budget.getBudgetMonthlyBreakdown().getBudgetBreakdown())
                                                .collect(Collectors.toSet());
    if (budgetBreakdowns.size() > 1) {
      throw new InvalidRequestException(INVALID_CHILD_ENTITY_BUDGET_BREAKDOWN_EXCEPTION);
    }
    if (budgetBreakdowns.stream().findFirst().isPresent()) {
      return budgetBreakdowns.stream().findFirst().get();
    } else {
      throw new InvalidRequestException(CHILD_ENTITY_BUDGET_BREAKDOWN_NOT_PRESENT_EXCEPTION);
    }
  }

  public static BudgetBreakdown getBudgetBreakdownChildBudgetGroups(List<BudgetGroup> childBudgetGroups) {
    Set<BudgetBreakdown> budgetBreakdowns =
        childBudgetGroups.stream()
            .map(budgetGroup -> budgetGroup.getBudgetGroupMonthlyBreakdown().getBudgetBreakdown())
            .collect(Collectors.toSet());
    if (budgetBreakdowns.size() > 1) {
      throw new InvalidRequestException(INVALID_CHILD_ENTITY_BUDGET_BREAKDOWN_EXCEPTION);
    }
    if (budgetBreakdowns.stream().findFirst().isPresent()) {
      return budgetBreakdowns.stream().findFirst().get();
    } else {
      throw new InvalidRequestException(CHILD_ENTITY_BUDGET_BREAKDOWN_NOT_PRESENT_EXCEPTION);
    }
  }

  public static int getTimeLeftForBudgetGroup(BudgetGroup budgetGroup) {
    return Math.toIntExact(
        (budgetGroup.getEndTime() - BudgetUtils.getStartOfCurrentDay()) / BudgetUtils.ONE_DAY_MILLIS);
  }

  public static BudgetPeriod getBudgetGroupPeriod(BudgetGroup budgetGroup) {
    if (budgetGroup.getPeriod() != null) {
      return budgetGroup.getPeriod();
    }
    return BudgetPeriod.MONTHLY;
  }

  public static List<Double> getAlertThresholdsForBudgetGroup(BudgetGroup budgetGroup, AlertThresholdBase basedOn) {
    AlertThreshold[] alertThresholds = budgetGroup.getAlertThresholds();
    List<Double> costAlertsPercentage = new ArrayList<>();
    if (alertThresholds != null) {
      for (AlertThreshold alertThreshold : alertThresholds) {
        if (alertThreshold.getBasedOn() == basedOn) {
          costAlertsPercentage.add(alertThreshold.getPercentage());
        }
      }
    }
    return costAlertsPercentage;
  }

  public static long getBudgetGroupStartTime(BudgetGroup budgetGroup) {
    if (budgetGroup.getStartTime() != 0) {
      if (budgetGroup.getPeriod() == DAILY) {
        return budgetGroup.getStartTime() - 2 * BudgetUtils.ONE_DAY_MILLIS;
      }
      return budgetGroup.getStartTime();
    }
    return BudgetUtils.getStartOfMonth(false);
  }

  public static List<ValueDataPoint> getLastPeriodCostOfChildBudgets(List<Budget> childBudgets) {
    long startTime = getStartTimeForChildBudgets(childBudgets);
    BudgetPeriod period = getStartOfPeriodForChildBudgets(childBudgets);
    BudgetBreakdown budgetBreakdown = getBudgetBreakdownChildBudgets(childBudgets);
    boolean isMonthlyBreakdownPresent = period == YEARLY && budgetBreakdown == MONTHLY;
    if (isMonthlyBreakdownPresent) {
      Double[] aggregatedLastPeriodCosts = getAggregatedLastPeriodCostsForBudgets(childBudgets);
      return BudgetUtils.getYearlyMonthWiseKeyValuePairs(
          BudgetUtils.getStartOfLastPeriod(startTime, period), aggregatedLastPeriodCosts);
    } else {
      Double aggregatedLastPeriodCost =
          childBudgets.stream().map(Budget::getLastMonthCost).mapToDouble(Double::doubleValue).sum();
      return Collections.singletonList(ValueDataPoint.builder()
                                           .time(BudgetUtils.getStartOfLastPeriod(startTime, period))
                                           .value(aggregatedLastPeriodCost)
                                           .build());
    }
  }

  public static List<ValueDataPoint> getLastPeriodCostOfChildBudgetGroups(List<BudgetGroup> childBudgetGroups) {
    long startTime = getStartTimeForChildBudgetGroups(childBudgetGroups);
    BudgetPeriod period = getStartOfPeriodForChildBudgetGroups(childBudgetGroups);
    BudgetBreakdown budgetBreakdown = getBudgetBreakdownChildBudgetGroups(childBudgetGroups);
    boolean isMonthlyBreakdownPresent = period == YEARLY && budgetBreakdown == MONTHLY;
    if (isMonthlyBreakdownPresent) {
      Double[] lastPeriodCosts = getAggregatedLastPeriodCostsForBudgetGroups(childBudgetGroups);
      return BudgetUtils.getYearlyMonthWiseKeyValuePairs(
          BudgetUtils.getStartOfLastPeriod(startTime, period), lastPeriodCosts);
    } else {
      Double aggregatedLastPeriodCost =
          childBudgetGroups.stream().map(BudgetGroup::getLastMonthCost).mapToDouble(Double::doubleValue).sum();
      return Collections.singletonList(ValueDataPoint.builder()
                                           .time(BudgetUtils.getStartOfLastPeriod(startTime, period))
                                           .value(aggregatedLastPeriodCost)
                                           .build());
    }
  }
  public static BudgetSummary buildBudgetGroupSummary(BudgetGroup budgetGroup, List<BudgetSummary> childEntities) {
    return BudgetSummary.builder()
        .id(budgetGroup.getUuid())
        .name(budgetGroup.getName())
        .budgetAmount(budgetGroup.getBudgetGroupAmount())
        .actualCost(budgetGroup.getActualCost())
        .forecastCost(budgetGroup.getForecastCost())
        .timeLeft(BudgetGroupUtils.getTimeLeftForBudgetGroup(budgetGroup))
        .timeUnit(BudgetUtils.DEFAULT_TIME_UNIT)
        .timeScope(BudgetGroupUtils.getBudgetGroupPeriod(budgetGroup).toString().toLowerCase())
        .actualCostAlerts(BudgetGroupUtils.getAlertThresholdsForBudgetGroup(budgetGroup, ACTUAL_COST))
        .forecastCostAlerts(BudgetGroupUtils.getAlertThresholdsForBudgetGroup(budgetGroup, FORECASTED_COST))
        .alertThresholds(budgetGroup.getAlertThresholds())
        .period(BudgetGroupUtils.getBudgetGroupPeriod(budgetGroup))
        .startTime(BudgetGroupUtils.getBudgetGroupStartTime(budgetGroup))
        .budgetMonthlyBreakdown(budgetGroup.getBudgetGroupMonthlyBreakdown())
        .isBudgetGroup(true)
        .childEntities(childEntities)
        .build();
  }

  private static Double[] getAggregatedLastPeriodCostsForBudgets(List<Budget> budgets) {
    Double[] aggregatedCosts = new Double[12];
    Arrays.fill(aggregatedCosts, 0.0);
    for (Budget budget : budgets) {
      Double[] budgetLastPeriodCosts = budget.getBudgetMonthlyBreakdown().getYearlyLastPeriodCost();
      for (int index = 0; index < BudgetUtils.MONTHS; index++) {
        aggregatedCosts[index] = aggregatedCosts[index] + budgetLastPeriodCosts[index];
      }
    }
    return aggregatedCosts;
  }

  private static Double[] getAggregatedLastPeriodCostsForBudgetGroups(List<BudgetGroup> budgetGroups) {
    Double[] aggregatedCosts = new Double[12];
    Arrays.fill(aggregatedCosts, 0.0);
    for (BudgetGroup budgetGroup : budgetGroups) {
      Double[] budgetLastPeriodCosts = budgetGroup.getBudgetGroupMonthlyBreakdown().getYearlyLastPeriodCost();
      for (int index = 0; index < BudgetUtils.MONTHS; index++) {
        aggregatedCosts[index] = aggregatedCosts[index] + budgetLastPeriodCosts[index];
      }
    }
    return aggregatedCosts;
  }

  private static void populateDefaultBudgetGroupBreakdown(BudgetGroup budgetGroup) {
    if (budgetGroup.getBudgetGroupMonthlyBreakdown() == null) {
      budgetGroup.setBudgetGroupMonthlyBreakdown(
          BudgetMonthlyBreakdown.builder().budgetBreakdown(BudgetBreakdown.YEARLY).build());
      return;
    }
    if (budgetGroup.getBudgetGroupMonthlyBreakdown().getBudgetBreakdown() == null) {
      budgetGroup.getBudgetGroupMonthlyBreakdown().setBudgetBreakdown(BudgetBreakdown.YEARLY);
    }
  }

  private static void validateBudgetGroupName(BudgetGroup budget, List<BudgetGroup> existingBudgetGroups) {
    if (!existingBudgetGroups.isEmpty() && (!existingBudgetGroups.get(0).getUuid().equals(budget.getUuid()))) {
      throw new InvalidRequestException(BUDGET_GROUP_NAME_EXISTS_EXCEPTION);
    }
  }
}
