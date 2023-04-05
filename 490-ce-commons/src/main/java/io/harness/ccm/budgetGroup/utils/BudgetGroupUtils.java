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
import io.harness.ccm.budget.dao.BudgetDao;
import io.harness.ccm.budget.utils.BudgetUtils;
import io.harness.ccm.budgetGroup.BudgetGroup;
import io.harness.ccm.budgetGroup.BudgetGroupChildEntityDTO;
import io.harness.ccm.budgetGroup.CascadeType;
import io.harness.ccm.budgetGroup.dao.BudgetGroupDao;
import io.harness.ccm.commons.entities.billing.Budget;
import io.harness.ccm.commons.entities.budget.BudgetCostData;
import io.harness.exception.InvalidRequestException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Stack;
import java.util.TreeMap;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
public class BudgetGroupUtils {
  @Autowired private static BudgetDao budgetDao;
  @Autowired private static BudgetGroupDao budgetGroupDao;

  public static final String BUDGET_GROUP_NAME_EXISTS_EXCEPTION =
      "Error in creating budget group. Budget group with given name already exists";
  public static final String INVALID_CHILD_ENTITY_ID_EXCEPTION =
      "Error in performing operation. Some of the child entity IDs are invalid.";
  public static final String INVALID_CHILD_ENTITY_START_TIME_EXCEPTION =
      "Error in performing operation. StartTime of child entities don't match.";
  public static final String INVALID_CHILD_ENTITY_TYPE_EXCEPTION =
      "Error in performing operation. Type(budget/budget group) of child entities don't match.";
  public static final String CHILD_ENTITY_START_TIME_NOT_PRESENT_EXCEPTION =
      "Error in performing operation. Start time not found.";
  public static final String INVALID_CHILD_ENTITY_PERIOD_EXCEPTION =
      "Error in performing operation. Period of child entities don't match.";
  public static final String CHILD_ENTITY_PERIOD_NOT_PRESENT_EXCEPTION =
      "Error in performing operation. Period not found.";
  public static final String INVALID_CHILD_ENTITY_BUDGET_BREAKDOWN_EXCEPTION =
      "Error in performing operation. Budget breakdown of child entities don't match.";
  public static final String INVALID_CHILD_ENTITY_PARENT_EXCEPTION =
      "Error in performing operation. Parent of child entities don't match.";
  public static final String CHILD_ENTITY_BUDGET_BREAKDOWN_NOT_PRESENT_EXCEPTION =
      "Error in performing operation. Budget breakdown not found.";
  public static final int MONTHS = 12;
  public static final String CHILD_ENTITY_TYPE_NOT_PRESENT_EXCEPTION =
      "Error in performing operation. Child entity type not found.";
  public static final String CHILD_ENTITY_NOT_PRESENT_EXCEPTION =
      "Error in performing operation. Child entity not configured for budget group.";
  public static final String CHILD_ENTITY_PARENT_NOT_PRESENT_EXCEPTION =
      "Error in performing operation. Parent for child entities not found.";
  public static final String CHILD_ENTITY_PARENT_PRESENT_EXCEPTION =
      "Error in performing operation. Parent for child entities already configured.";
  public static final String INVALID_CASCADE_TYPE_EXCEPTION =
      "Error in performing operation. Cannot perform cascading.";
  public static final String COST_TYPE_ACTUAL = "Actual cost";
  public static final String COST_TYPE_FORECASTED = "Forecasted cost";
  public static final String COST_TYPE_LAST_PERIOD = "Last period cost";
  public static final String NO_CHILD_ENTITY_PRESENT_EXCEPTION =
      "Error in performing operation. Budget group must have atleast one child budget/budget group";
  public static final String INVALID_PARENT_EXCEPTION = "Error in creating budget group. Invalid parent Id specified";
  public static final String INVALID_INDIVIDUAL_PROPORTION =
      "Error in performing operation. Individual Proportion for budget group children should be in valid range";
  public static final String INVALID_TOTAL_PROPORTION =
      "Error in performing operation. Total Proportion for budget group children should be 100";
  public static final String INVALID_BUDGET_DETAILS_EXCEPTION =
      "Error in updating budget group. The budget group period/ start period should be same as it's parent budget group";

  public static void validateBudgetGroup(BudgetGroup budgetGroup, List<BudgetGroup> existingBudgetGroups) {
    populateDefaultBudgetGroupBreakdown(budgetGroup);
    validateBudgetGroupName(budgetGroup, existingBudgetGroups);
    validateBudgetGroupParent(budgetGroup, existingBudgetGroups);
  }

  public static void validateChildBudgets(List<Budget> childBudgets) {
    validatePeriodForChildBudgets(childBudgets);
    validateStartTimeForChildBudgets(childBudgets);
    validateBreakdownForChildBudgets(childBudgets);
  }

  public static void validateChildBudgetGroups(List<BudgetGroup> childBudgetGroups) {
    validatePeriodForChildBudgetGroups(childBudgetGroups);
    validateStartTimeForChildBudgetGroups(childBudgetGroups);
    validateBreakdownForChildBudgetGroups(childBudgetGroups);
  }

  public static boolean areChildEntitiesBudgetGroups(List<BudgetGroupChildEntityDTO> childEntities) {
    Set<Boolean> childEntityType =
        childEntities.stream().map(BudgetGroupChildEntityDTO::isBudgetGroup).collect(Collectors.toSet());
    if (childEntityType.size() > 1) {
      throw new InvalidRequestException(INVALID_CHILD_ENTITY_TYPE_EXCEPTION);
    }
    if (childEntityType.stream().findFirst().isPresent()) {
      return childEntityType.stream().findFirst().get();
    } else {
      throw new InvalidRequestException(CHILD_ENTITY_TYPE_NOT_PRESENT_EXCEPTION);
    }
  }

  public static void validatePeriodForChildBudgets(List<Budget> childBudgets) {
    getPeriodForChildBudgets(childBudgets);
  }

  public static void validateStartTimeForChildBudgets(List<Budget> childBudgets) {
    getStartTimeForChildBudgets(childBudgets);
  }

  public static void validateBreakdownForChildBudgets(List<Budget> childBudgets) {
    getBudgetBreakdownForChildBudgets(childBudgets);
  }

  public static void validateNoParentPresentForChildBudgets(List<Budget> childBudgets) {
    Set<String> parentIds =
        childBudgets.stream().map(Budget::getParentBudgetGroupId).filter(Objects::nonNull).collect(Collectors.toSet());
    if (parentIds.size() != 0) {
      throw new InvalidRequestException(CHILD_ENTITY_PARENT_PRESENT_EXCEPTION);
    }
  }

  public static void validatePeriodForChildBudgetGroups(List<BudgetGroup> childBudgetGroups) {
    getPeriodForChildBudgetGroups(childBudgetGroups);
  }

  public static void validateStartTimeForChildBudgetGroups(List<BudgetGroup> childBudgetGroups) {
    getStartTimeForChildBudgetGroups(childBudgetGroups);
  }

  public static void validateBreakdownForChildBudgetGroups(List<BudgetGroup> childBudgetGroups) {
    getBudgetBreakdownForChildBudgetGroups(childBudgetGroups);
  }

  public static void validateNoParentPresentForChildBudgetGroups(List<BudgetGroup> childBudgetGroups) {
    Set<String> parentIds = childBudgetGroups.stream()
                                .map(BudgetGroup::getParentBudgetGroupId)
                                .filter(Objects::nonNull)
                                .collect(Collectors.toSet());
    if (parentIds.size() != 0) {
      throw new InvalidRequestException(CHILD_ENTITY_PARENT_PRESENT_EXCEPTION);
    }
  }

  public static BudgetPeriod getPeriodForChildBudgets(List<Budget> childBudgets) {
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

  public static BudgetPeriod getPeriodForChildBudgetGroups(List<BudgetGroup> childBudgetGroups) {
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

  public static BudgetBreakdown getBudgetBreakdownForChildBudgets(List<Budget> childBudgets) {
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

  public static BudgetBreakdown getBudgetBreakdownForChildBudgetGroups(List<BudgetGroup> childBudgetGroups) {
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

  public static String getParentIdForChildBudgets(List<Budget> childBudgets) {
    Set<String> parentIds = childBudgets.stream().map(Budget::getParentBudgetGroupId).collect(Collectors.toSet());
    if (parentIds.size() > 1) {
      throw new InvalidRequestException(INVALID_CHILD_ENTITY_PARENT_EXCEPTION);
    }
    if (parentIds.stream().findFirst().isPresent()) {
      return parentIds.stream().findFirst().get();
    } else {
      throw new InvalidRequestException(CHILD_ENTITY_PARENT_NOT_PRESENT_EXCEPTION);
    }
  }

  public static String getParentIdForChildBudgetGroups(List<BudgetGroup> childBudgetGroups) {
    Set<String> parentIds =
        childBudgetGroups.stream().map(BudgetGroup::getParentBudgetGroupId).collect(Collectors.toSet());
    if (parentIds.size() > 1) {
      throw new InvalidRequestException(INVALID_CHILD_ENTITY_PARENT_EXCEPTION);
    }
    if (parentIds.stream().findFirst().isPresent()) {
      return parentIds.stream().findFirst().get();
    } else {
      throw new InvalidRequestException(CHILD_ENTITY_PARENT_NOT_PRESENT_EXCEPTION);
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

  public static Double getAggregatedCostsForBudgets(List<Budget> budgets, String costType) {
    switch (costType) {
      case COST_TYPE_ACTUAL:
        return budgets.stream().map(Budget::getActualCost).mapToDouble(Double::doubleValue).sum();
      case COST_TYPE_FORECASTED:
        return budgets.stream().map(Budget::getForecastCost).mapToDouble(Double::doubleValue).sum();
      case COST_TYPE_LAST_PERIOD:
        return budgets.stream().map(Budget::getLastMonthCost).mapToDouble(Double::doubleValue).sum();
      default:
        return 0.0;
    }
  }

  public static Double getAggregatedCostsForBudgetGroups(List<BudgetGroup> budgetGroups, String costType) {
    switch (costType) {
      case COST_TYPE_ACTUAL:
        return budgetGroups.stream().map(BudgetGroup::getActualCost).mapToDouble(Double::doubleValue).sum();
      case COST_TYPE_FORECASTED:
        return budgetGroups.stream().map(BudgetGroup::getForecastCost).mapToDouble(Double::doubleValue).sum();
      case COST_TYPE_LAST_PERIOD:
        return budgetGroups.stream().map(BudgetGroup::getLastMonthCost).mapToDouble(Double::doubleValue).sum();
      default:
        return 0.0;
    }
  }

  public static Double[] getAggregatedCostsForBudgetsWithBreakdown(List<Budget> budgets, String costType) {
    Double[] aggregatedCosts = new Double[12];
    Arrays.fill(aggregatedCosts, 0.0);
    for (Budget budget : budgets) {
      Double[] budgetCosts = getCostForBudgetWithBreakdown(budget, costType);
      for (int index = 0; index < BudgetUtils.MONTHS; index++) {
        aggregatedCosts[index] = aggregatedCosts[index] + budgetCosts[index];
      }
    }
    return aggregatedCosts;
  }

  public static Double[] getAggregatedCostsForBudgetGroupsWithBreakdown(
      List<BudgetGroup> budgetGroups, String costType) {
    Double[] aggregatedCosts = new Double[12];
    Arrays.fill(aggregatedCosts, 0.0);
    for (BudgetGroup budgetGroup : budgetGroups) {
      Double[] budgetGroupCosts = getCostForBudgetGroupWithBreakdown(budgetGroup, costType);
      for (int index = 0; index < BudgetUtils.MONTHS; index++) {
        aggregatedCosts[index] = aggregatedCosts[index] + budgetGroupCosts[index];
      }
    }
    return aggregatedCosts;
  }

  public static List<ValueDataPoint> getAggregatedBudgetAmountOfChildBudgets(List<Budget> childBudgets) {
    long startTime = getStartTimeForChildBudgets(childBudgets);
    if (isMonthlyBreakdownPresentForChildBudgets(childBudgets)) {
      return getAggregatedBudgetAmountForBudgets(childBudgets);
    } else {
      Double aggregatedBudgetAmount =
          childBudgets.stream().map(Budget::getBudgetAmount).mapToDouble(Double::doubleValue).sum();
      return Collections.singletonList(ValueDataPoint.builder().time(startTime).value(aggregatedBudgetAmount).build());
    }
  }

  public static List<ValueDataPoint> getAggregatedBudgetAmountOfChildBudgetGroups(List<BudgetGroup> childBudgetGroups) {
    long startTime = getStartTimeForChildBudgetGroups(childBudgetGroups);
    if (isMonthlyBreakdownPresentForChildBudgetGroups(childBudgetGroups)) {
      return getAggregatedBudgetGroupAmountsForBudgetGroups(childBudgetGroups);
    } else {
      Double aggregatedBudgetGroupAmount =
          childBudgetGroups.stream().map(BudgetGroup::getBudgetGroupAmount).mapToDouble(Double::doubleValue).sum();
      return Collections.singletonList(
          ValueDataPoint.builder().time(startTime).value(aggregatedBudgetGroupAmount).build());
    }
  }

  public static boolean isMonthlyBreakdownPresentForChildBudgets(List<Budget> childBudgets) {
    BudgetPeriod period = getPeriodForChildBudgets(childBudgets);
    BudgetBreakdown budgetBreakdown = getBudgetBreakdownForChildBudgets(childBudgets);
    return period == YEARLY && budgetBreakdown == MONTHLY;
  }

  public static boolean isMonthlyBreakdownPresentForChildBudgetGroups(List<BudgetGroup> childBudgetGroups) {
    BudgetPeriod period = getPeriodForChildBudgetGroups(childBudgetGroups);
    BudgetBreakdown budgetBreakdown = getBudgetBreakdownForChildBudgetGroups(childBudgetGroups);
    return period == YEARLY && budgetBreakdown == MONTHLY;
  }

  public static BudgetGroup updateBudgetGroupAmountOnChildEntityDeletion(
      BudgetGroup budgetGroup, BudgetGroup childBudgetGroup) {
    budgetGroup.setBudgetGroupAmount(
        BudgetUtils.getRoundedValue(budgetGroup.getBudgetGroupAmount() - childBudgetGroup.getBudgetGroupAmount()));
    if (budgetGroup.getPeriod() == YEARLY
        && budgetGroup.getBudgetGroupMonthlyBreakdown().getBudgetBreakdown() == MONTHLY) {
      List<ValueDataPoint> updatedBudgetGroupAmounts = new ArrayList<>();
      Map<Long, Double> currentAmountPerTimestamp =
          budgetGroup.getBudgetGroupMonthlyBreakdown().getBudgetMonthlyAmount().stream().collect(
              Collectors.toMap(ValueDataPoint::getTime, ValueDataPoint::getValue));
      childBudgetGroup.getBudgetGroupMonthlyBreakdown().getBudgetMonthlyAmount().forEach(entry
          -> updatedBudgetGroupAmounts.add(
              ValueDataPoint.builder()
                  .time(entry.getTime())
                  .value(BudgetUtils.getRoundedValue(currentAmountPerTimestamp.get(entry.getTime()) - entry.getValue()))
                  .build()));
      budgetGroup.getBudgetGroupMonthlyBreakdown().setBudgetMonthlyAmount(updatedBudgetGroupAmounts);
    }
    return budgetGroup;
  }

  public static BudgetGroup updateBudgetGroupAmountOnChildEntityDeletion(BudgetGroup budgetGroup, Budget childBudget) {
    budgetGroup.setBudgetGroupAmount(
        BudgetUtils.getRoundedValue(budgetGroup.getBudgetGroupAmount() - childBudget.getBudgetAmount()));
    if (budgetGroup.getPeriod() == YEARLY
        && budgetGroup.getBudgetGroupMonthlyBreakdown().getBudgetBreakdown() == MONTHLY) {
      List<ValueDataPoint> updatedBudgetGroupAmounts = new ArrayList<>();
      Map<Long, Double> currentAmountPerTimestamp =
          budgetGroup.getBudgetGroupMonthlyBreakdown().getBudgetMonthlyAmount().stream().collect(
              Collectors.toMap(ValueDataPoint::getTime, ValueDataPoint::getValue));
      childBudget.getBudgetMonthlyBreakdown().getBudgetMonthlyAmount().forEach(entry
          -> updatedBudgetGroupAmounts.add(
              ValueDataPoint.builder()
                  .time(entry.getTime())
                  .value(BudgetUtils.getRoundedValue(currentAmountPerTimestamp.get(entry.getTime()) - entry.getValue()))
                  .build()));
      budgetGroup.getBudgetGroupMonthlyBreakdown().setBudgetMonthlyAmount(updatedBudgetGroupAmounts);
    }
    return budgetGroup;
  }

  public static double getCascadedAmount(
      CascadeType cascadeType, Double totalNumberOfChildEntities, Double proportion, Double amount) {
    switch (cascadeType) {
      case PROPORTIONAL:
        return BudgetUtils.getRoundedValue(amount * (proportion / 100));
      case EQUAL:
        return BudgetUtils.getRoundedValue(amount * (1 / totalNumberOfChildEntities));
      default:
        throw new InvalidRequestException(INVALID_CASCADE_TYPE_EXCEPTION);
    }
  }

  public static List<ValueDataPoint> getCascadedMonthlyAmount(CascadeType cascadeType,
      Double totalNumberOfChildEntities, Double proportion, List<ValueDataPoint> monthlyAmounts) {
    List<ValueDataPoint> cascadedMonthlyAmounts = new ArrayList<>();
    monthlyAmounts.forEach(dataPoint
        -> cascadedMonthlyAmounts.add(
            ValueDataPoint.builder()
                .time(dataPoint.getTime())
                .value(getCascadedAmount(cascadeType, totalNumberOfChildEntities, proportion, dataPoint.getValue()))
                .build()));
    return cascadedMonthlyAmounts;
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
        .childEntityProportions(budgetGroup.getChildEntities())
        .cascadeType(budgetGroup.getCascadeType())
        .parentId(budgetGroup.getParentBudgetGroupId())
        .build();
  }

  private static List<ValueDataPoint> getAggregatedBudgetAmountForBudgets(List<Budget> budgets) {
    List<ValueDataPoint> aggregatedBudgetAmounts = new ArrayList<>();
    Map<Long, Double> aggregatedBudgetAmountPerTimestamp = new TreeMap<>();
    for (Budget budget : budgets) {
      List<ValueDataPoint> budgetAmounts = budget.getBudgetMonthlyBreakdown().getBudgetMonthlyAmount();
      budgetAmounts.forEach(budgetAmount -> {
        Long timestamp = budgetAmount.getTime();
        if (aggregatedBudgetAmountPerTimestamp.containsKey(timestamp)) {
          aggregatedBudgetAmountPerTimestamp.put(
              timestamp, aggregatedBudgetAmountPerTimestamp.get(timestamp) + budgetAmount.getValue());
        } else {
          aggregatedBudgetAmountPerTimestamp.put(timestamp, budgetAmount.getValue());
        }
      });
    }
    if (aggregatedBudgetAmountPerTimestamp.keySet().size() != BudgetUtils.MONTHS) {
      throw new InvalidRequestException(INVALID_CHILD_ENTITY_BUDGET_BREAKDOWN_EXCEPTION);
    }
    aggregatedBudgetAmountPerTimestamp.keySet().forEach(timestamp -> {
      aggregatedBudgetAmounts.add(
          ValueDataPoint.builder().time(timestamp).value(aggregatedBudgetAmountPerTimestamp.get(timestamp)).build());
    });
    return aggregatedBudgetAmounts;
  }

  private static List<ValueDataPoint> getAggregatedBudgetGroupAmountsForBudgetGroups(List<BudgetGroup> budgetGroups) {
    List<ValueDataPoint> aggregatedBudgetGroupAmounts = new ArrayList<>();
    Map<Long, Double> aggregatedBudgetGroupAmountPerTimestamp = new TreeMap<>();
    for (BudgetGroup budgetGroup : budgetGroups) {
      List<ValueDataPoint> budgetGroupAmounts = budgetGroup.getBudgetGroupMonthlyBreakdown().getBudgetMonthlyAmount();
      budgetGroupAmounts.forEach(budgetGroupAmount -> {
        Long timestamp = budgetGroupAmount.getTime();
        if (aggregatedBudgetGroupAmountPerTimestamp.containsKey(timestamp)) {
          aggregatedBudgetGroupAmountPerTimestamp.put(
              timestamp, aggregatedBudgetGroupAmountPerTimestamp.get(timestamp) + budgetGroupAmount.getValue());
        } else {
          aggregatedBudgetGroupAmountPerTimestamp.put(timestamp, budgetGroupAmount.getValue());
        }
      });
    }
    if (aggregatedBudgetGroupAmountPerTimestamp.keySet().size() != BudgetUtils.MONTHS) {
      throw new InvalidRequestException(INVALID_CHILD_ENTITY_BUDGET_BREAKDOWN_EXCEPTION);
    }
    aggregatedBudgetGroupAmountPerTimestamp.keySet().forEach(timestamp -> {
      aggregatedBudgetGroupAmounts.add(ValueDataPoint.builder()
                                           .time(timestamp)
                                           .value(aggregatedBudgetGroupAmountPerTimestamp.get(timestamp))
                                           .build());
    });
    return aggregatedBudgetGroupAmounts;
  }

  private static Double[] getCostForBudgetWithBreakdown(Budget budget, String costType) {
    switch (costType) {
      case COST_TYPE_ACTUAL:
        return budget.getBudgetMonthlyBreakdown().getActualMonthlyCost();
      case COST_TYPE_FORECASTED:
        return budget.getBudgetMonthlyBreakdown().getForecastMonthlyCost();
      case COST_TYPE_LAST_PERIOD:
        return budget.getBudgetMonthlyBreakdown().getYearlyLastPeriodCost();
      default:
        return new Double[12];
    }
  }

  private static Double[] getCostForBudgetGroupWithBreakdown(BudgetGroup budgetGroup, String costType) {
    switch (costType) {
      case COST_TYPE_ACTUAL:
        return budgetGroup.getBudgetGroupMonthlyBreakdown().getActualMonthlyCost();
      case COST_TYPE_FORECASTED:
        return budgetGroup.getBudgetGroupMonthlyBreakdown().getForecastMonthlyCost();
      case COST_TYPE_LAST_PERIOD:
        return budgetGroup.getBudgetGroupMonthlyBreakdown().getYearlyLastPeriodCost();
      default:
        return new Double[12];
    }
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

  private static void validateBudgetGroupName(BudgetGroup budgetGroup, List<BudgetGroup> existingBudgetGroups) {
    if (!existingBudgetGroups.isEmpty() && (!existingBudgetGroups.get(0).getUuid().equals(budgetGroup.getUuid()))) {
      throw new InvalidRequestException(BUDGET_GROUP_NAME_EXISTS_EXCEPTION);
    }
  }

  private static void validateBudgetGroupParent(BudgetGroup budgetGroup, List<BudgetGroup> existingBudgetGroups) {
    if (!existingBudgetGroups.isEmpty()) {
      List<String> validParentIds =
          existingBudgetGroups.stream().map(BudgetGroup::getParentBudgetGroupId).collect(Collectors.toList());
      if (budgetGroup.getParentBudgetGroupId() != null
          && !validParentIds.contains(budgetGroup.getParentBudgetGroupId())) {
        throw new InvalidRequestException(INVALID_PARENT_EXCEPTION);
      }
    }
  }

  public static void updateBudgetGroupAmount(BudgetGroup budgetGroup, String accountId) {
    try {
      Double[] budgetGroupAmountMonthly = new Double[12];

      double budgetGroupAmount = 0.0;

      Arrays.fill(budgetGroupAmountMonthly, 0.0);

      // budgetGroupStack is used to traverse through all the children of budgetGroup
      Stack<BudgetGroup> budgetGroupStack = new Stack<>();

      // budgetGroupUuid & budgetUuid is used to avoid any circular link that might exist
      Set<String> budgetGroupUuid = new HashSet<>();
      Set<String> budgetUuid = new HashSet<>();

      budgetGroupStack.add(budgetGroup);
      budgetGroupUuid.add(budgetGroup.getUuid());

      while (!budgetGroupStack.isEmpty()) {
        BudgetGroup budgetGroupTemp = budgetGroupStack.peek();
        budgetGroupStack.pop();

        // Here we explore all children of the given budgetGroup
        for (BudgetGroupChildEntityDTO childEntityDTO : budgetGroupTemp.getChildEntities()) {
          // If child entity is a budgetGroup & not yet explored we add it to stack and mark it as visited
          if (childEntityDTO.isBudgetGroup() && !budgetGroupUuid.contains(childEntityDTO.getId())) {
            budgetGroupStack.add(budgetGroupDao.get(childEntityDTO.getId(), accountId));
            budgetGroupUuid.add(childEntityDTO.getId());
          }

          // If child entity id a budget & not yet explored then we add the amount to budgetGroup Amount
          // And also mark it as visited
          if (!childEntityDTO.isBudgetGroup() && !budgetUuid.contains(childEntityDTO.getId())) {
            budgetUuid.add(childEntityDTO.getId());
            Budget childBudget = budgetDao.get(childEntityDTO.getId(), accountId);
            budgetGroupAmount += childBudget.getBudgetAmount();
            if (childBudget.getPeriod() == BudgetPeriod.YEARLY && childBudget.getBudgetMonthlyBreakdown() != null
                && childBudget.getBudgetMonthlyBreakdown().getBudgetBreakdown() == BudgetBreakdown.MONTHLY) {
              Double[] budgetAmountMonthly = BudgetUtils.getYearlyMonthWiseValues(
                  childBudget.getBudgetMonthlyBreakdown().getBudgetMonthlyAmount());
              for (int month = 0; month < MONTHS; month++) {
                budgetGroupAmountMonthly[month] += budgetAmountMonthly[month];
              }
            }
          }
        }
      }

      budgetGroup.setBudgetGroupAmount(budgetGroupAmount);
      if (budgetGroup.getPeriod() == BudgetPeriod.YEARLY && budgetGroup.getBudgetGroupMonthlyBreakdown() != null
          && budgetGroup.getBudgetGroupMonthlyBreakdown().getBudgetBreakdown() == BudgetBreakdown.MONTHLY) {
        budgetGroup.getBudgetGroupMonthlyBreakdown().setBudgetMonthlyAmount(
            BudgetUtils.getYearlyMonthWiseKeyValuePairs(budgetGroup.getStartTime(), budgetGroupAmountMonthly));
      }
    } catch (Exception e) {
      log.error("Exception while calculating updated budget group amount for budget group : {}. Exception: {}",
          budgetGroup.getUuid(), e);
    }
  }

  public static void updateBudgetGroupCosts(BudgetGroup budgetGroup, String accountId) {
    try {
      Double[] budgetGroupActualCostMonthly = new Double[12];
      Double[] budgetGroupForecastCostMonthly = new Double[12];
      Double[] budgetGroupLastPeriodCostMonthly = new Double[12];

      double budgetGroupActualCost = 0.0;
      double budgetGroupForecastCost = 0.0;
      double budgetGroupLastPeriodCost = 0.0;

      Arrays.fill(budgetGroupActualCostMonthly, 0.0);
      Arrays.fill(budgetGroupForecastCostMonthly, 0.0);
      Arrays.fill(budgetGroupLastPeriodCostMonthly, 0.0);

      // budgetGroupStack is used to traverse through all the children of budgetGroup
      Stack<BudgetGroup> budgetGroupStack = new Stack<>();

      // budgetGroupUuid & budgetUuid is used to avoid any circular link that might exist
      Set<String> budgetGroupUuid = new HashSet<>();
      Set<String> budgetUuid = new HashSet<>();

      budgetGroupStack.add(budgetGroup);
      budgetGroupUuid.add(budgetGroup.getUuid());

      while (!budgetGroupStack.isEmpty()) {
        BudgetGroup budgetGroupTemp = budgetGroupStack.peek();
        budgetGroupStack.pop();

        // Here we explore all children of the given budgetGroup
        for (BudgetGroupChildEntityDTO childEntityDTO : budgetGroupTemp.getChildEntities()) {
          // If child entity is a budgetGroup & not yet explored we add it to stack and mark it as visited
          if (childEntityDTO.isBudgetGroup() && !budgetGroupUuid.contains(childEntityDTO.getId())) {
            budgetGroupStack.add(budgetGroupDao.get(childEntityDTO.getId(), accountId));
            budgetGroupUuid.add(childEntityDTO.getId());
          }

          // If child entity id a budget & not yet explored then we add the amount to budgetGroup Amount
          // And also mark it as visited
          if (!childEntityDTO.isBudgetGroup() && !budgetUuid.contains(childEntityDTO.getId())) {
            budgetUuid.add(childEntityDTO.getId());
            Budget childBudget = budgetDao.get(childEntityDTO.getId(), accountId);
            budgetGroupActualCost += childBudget.getActualCost();
            budgetGroupForecastCost += childBudget.getForecastCost();
            budgetGroupLastPeriodCost += childBudget.getLastMonthCost();
            if (childBudget.getPeriod() == BudgetPeriod.YEARLY && childBudget.getBudgetMonthlyBreakdown() != null
                && childBudget.getBudgetMonthlyBreakdown().getBudgetBreakdown() == BudgetBreakdown.MONTHLY) {
              Double[] budgetActualCostMonthly = childBudget.getBudgetMonthlyBreakdown().getActualMonthlyCost();
              Double[] budgetForecastCostMonthly = childBudget.getBudgetMonthlyBreakdown().getForecastMonthlyCost();
              Double[] budgetLastPeriodCostMonthly = childBudget.getBudgetMonthlyBreakdown().getYearlyLastPeriodCost();
              for (int month = 0; month < MONTHS; month++) {
                budgetGroupActualCostMonthly[month] += budgetActualCostMonthly[month];
                budgetGroupForecastCostMonthly[month] += budgetForecastCostMonthly[month];
                budgetGroupLastPeriodCostMonthly[month] += budgetLastPeriodCostMonthly[month];
              }
            }
          }
        }
      }

      budgetGroup.setActualCost(budgetGroupActualCost);
      budgetGroup.setForecastCost(budgetGroupForecastCost);
      budgetGroup.setLastMonthCost(budgetGroupLastPeriodCost);
      if (budgetGroup.getPeriod() == BudgetPeriod.YEARLY && budgetGroup.getBudgetGroupMonthlyBreakdown() != null
          && budgetGroup.getBudgetGroupMonthlyBreakdown().getBudgetBreakdown() == BudgetBreakdown.MONTHLY) {
        budgetGroup.getBudgetGroupMonthlyBreakdown().setActualMonthlyCost(budgetGroupActualCostMonthly);
        budgetGroup.getBudgetGroupMonthlyBreakdown().setForecastMonthlyCost(budgetGroupForecastCostMonthly);
        budgetGroup.getBudgetGroupMonthlyBreakdown().setYearlyLastPeriodCost(budgetGroupLastPeriodCostMonthly);
      }
    } catch (Exception e) {
      log.error("Exception while calculating updated budget group costs for budget group : {}. Exception: {}",
          budgetGroup.getUuid(), e);
    }
  }

  public static HashMap<Long, BudgetCostData> adjustBudgetGroupHistory(BudgetGroup budgetGroup) {
    HashMap<Long, BudgetCostData> budgetGroupHistory = budgetGroup.getBudgetGroupHistory();
    int maxAllowedSize;

    switch (budgetGroup.getPeriod()) {
      case DAILY:
      case WEEKLY:
      case MONTHLY:
        maxAllowedSize = 12;
        break;
      case QUARTERLY:
        maxAllowedSize = 4;
        break;
      case YEARLY:
        maxAllowedSize = 1;
        break;
      default:
        maxAllowedSize = 0;
    }

    while (budgetGroupHistory.size() >= maxAllowedSize) {
      Long minStartTime = Collections.min(budgetGroupHistory.keySet());
      budgetGroupHistory.remove(minStartTime);
    }

    double budgetVariance =
        BudgetUtils.getBudgetVariance(budgetGroup.getBudgetGroupAmount(), budgetGroup.getActualCost());
    double budgetVariancePercentage =
        BudgetUtils.getBudgetVariancePercentage(budgetVariance, budgetGroup.getBudgetGroupAmount());
    BudgetCostData currentBudgetCostData = BudgetCostData.builder()
                                               .time(budgetGroup.getStartTime())
                                               .endTime(budgetGroup.getEndTime())
                                               .budgeted(budgetGroup.getBudgetGroupAmount())
                                               .actualCost(budgetGroup.getActualCost())
                                               .forecastCost(budgetGroup.getForecastCost())
                                               .budgetVariance(budgetVariance)
                                               .budgetVariancePercentage(budgetVariancePercentage)
                                               .build();
    budgetGroupHistory.put(budgetGroup.getStartTime(), currentBudgetCostData);
    return budgetGroupHistory;
  }

  public static Double getSumGivenTimeAndValueList(List<ValueDataPoint> valueDataPoints) {
    return valueDataPoints.stream().map(valueDataPoint -> valueDataPoint.getValue()).reduce(0.0D, (a, b) -> a + b);
  }
}
