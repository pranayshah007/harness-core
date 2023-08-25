/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.budgetGroup.service;

import static io.harness.ccm.budget.BudgetBreakdown.MONTHLY;
import static io.harness.ccm.budget.BudgetPeriod.YEARLY;
import static io.harness.ccm.budget.utils.BudgetUtils.MONTHS;
import static io.harness.ccm.budgetGroup.CascadeType.EQUAL;
import static io.harness.ccm.budgetGroup.CascadeType.NO_CASCADE;
import static io.harness.ccm.budgetGroup.CascadeType.PROPORTIONAL;
import static io.harness.ccm.budgetGroup.utils.BudgetGroupUtils.INVALID_INDIVIDUAL_PROPORTION;
import static io.harness.ccm.budgetGroup.utils.BudgetGroupUtils.INVALID_TOTAL_PROPORTION;

import io.harness.ccm.budget.BudgetBreakdown;
import io.harness.ccm.budget.BudgetMonthlyBreakdown;
import io.harness.ccm.budget.BudgetPeriod;
import io.harness.ccm.budget.BudgetSummary;
import io.harness.ccm.budget.ValueDataPoint;
import io.harness.ccm.budget.dao.BudgetDao;
import io.harness.ccm.budget.utils.BudgetUtils;
import io.harness.ccm.budgetGroup.BudgetGroup;
import io.harness.ccm.budgetGroup.BudgetGroupChildEntityDTO;
import io.harness.ccm.budgetGroup.BudgetGroupSortType;
import io.harness.ccm.budgetGroup.dao.BudgetGroupDao;
import io.harness.ccm.budgetGroup.utils.BudgetGroupUtils;
import io.harness.ccm.commons.entities.CCMSortOrder;
import io.harness.ccm.commons.entities.billing.Budget;
import io.harness.ccm.commons.entities.budget.BudgetCostData;
import io.harness.ccm.commons.entities.budget.BudgetData;
import io.harness.exception.InvalidRequestException;

import com.google.inject.Inject;
import io.fabric8.utils.Lists;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.Stack;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BudgetGroupServiceImpl implements BudgetGroupService {
  @Inject BudgetGroupDao budgetGroupDao;
  @Inject BudgetDao budgetDao;
  public static final String INVALID_BUDGET_GROUP_ID_EXCEPTION = "Invalid budget group id";
  public static final String MISSING_BUDGET_GROUP_DATA_EXCEPTION = "Missing Budget Group data exception";

  @Override
  public String save(BudgetGroup budgetGroup) {
    BudgetGroupUtils.validateBudgetGroup(
        budgetGroup, budgetGroupDao.list(budgetGroup.getAccountId(), budgetGroup.getName()));

    // Validating child entities
    List<BudgetGroupChildEntityDTO> childEntities = budgetGroup.getChildEntities();
    if (childEntities == null || childEntities.size() < 1) {
      throw new InvalidRequestException(BudgetGroupUtils.CHILD_ENTITY_NOT_PRESENT_EXCEPTION);
    }
    boolean areChildEntitiesBudgetGroups =
        BudgetGroupUtils.areChildEntitiesBudgetGroups(budgetGroup.getChildEntities());
    List<String> childEntityIds =
        budgetGroup.getChildEntities().stream().map(BudgetGroupChildEntityDTO::getId).collect(Collectors.toList());
    validateChildEntities(budgetGroup, areChildEntitiesBudgetGroups, childEntityIds);
    validateParentOfChildEntities(budgetGroup, areChildEntitiesBudgetGroups, childEntityIds);
    validateProportion(budgetGroup);

    // Saving budget group
    budgetGroup.setParentBudgetGroupId(null);
    updateBudgetGroupBreakdown(budgetGroup);
    updateBudgetGroupAmount(budgetGroup);
    updateBudgetGroupCosts(budgetGroup);
    updateBudgetGroupEndTime(budgetGroup);
    updateBudgetGroupHistory(budgetGroup, budgetGroup.getAccountId());
    String budgetGroupId = budgetGroupDao.save(budgetGroup);
    cascadeBudgetGroupAmount(budgetGroup);

    // Updating parent id for child entities
    if (areChildEntitiesBudgetGroups) {
      budgetGroupDao.updateParentId(budgetGroupId, childEntityIds);
    } else {
      budgetDao.updateParentId(budgetGroupId, childEntityIds);
    }
    return budgetGroupId;
  }

  @Override
  public void update(String uuid, String accountId, BudgetGroup budgetGroup) {
    BudgetGroup oldBudgetGroup = budgetGroupDao.get(uuid, accountId);
    budgetGroup.setUuid(uuid);
    budgetGroup.setParentBudgetGroupId(oldBudgetGroup.getParentBudgetGroupId());
    BudgetGroupUtils.validateBudgetGroup(
        budgetGroup, budgetGroupDao.list(budgetGroup.getAccountId(), budgetGroup.getName()));

    // Validating child entities
    List<BudgetGroupChildEntityDTO> childEntities = budgetGroup.getChildEntities();
    if (childEntities == null || childEntities.size() < 1) {
      throw new InvalidRequestException(BudgetGroupUtils.CHILD_ENTITY_NOT_PRESENT_EXCEPTION);
    }
    boolean areChildEntitiesBudgetGroups =
        BudgetGroupUtils.areChildEntitiesBudgetGroups(budgetGroup.getChildEntities());
    List<String> childEntityIds =
        budgetGroup.getChildEntities().stream().map(BudgetGroupChildEntityDTO::getId).collect(Collectors.toList());
    List<String> oldChildEntityIds =
        oldBudgetGroup.getChildEntities().stream().map(BudgetGroupChildEntityDTO::getId).collect(Collectors.toList());

    validateChildEntities(budgetGroup, areChildEntitiesBudgetGroups, childEntityIds);
    validateProportion(budgetGroup);
    updateBudgetGroupParent(budgetGroup);
    validateBudgetGroupDetails(budgetGroup, oldBudgetGroup);

    // Saving budget group
    updateBudgetGroupBreakdown(budgetGroup);
    updateBudgetGroupAmount(budgetGroup);
    updateBudgetGroupCosts(budgetGroup);
    updateBudgetGroupEndTime(budgetGroup);
    updateBudgetGroupHistory(budgetGroup, budgetGroup.getAccountId());
    budgetGroupDao.update(uuid, accountId, budgetGroup);
    if (budgetGroup.getParentBudgetGroupId() != null
        && budgetGroup.getBudgetGroupAmount() != oldBudgetGroup.getBudgetGroupAmount()) {
      cascadeUpwardBudgetGroupAmount(budgetGroup, oldBudgetGroup);
    }
    cascadeBudgetGroupAmount(budgetGroup);

    // Updating parent id for child entities
    // and also updating parent of child entities which are no longer part of this budget group
    List<String> freeChildEntities =
        oldChildEntityIds.stream().filter(id -> !childEntityIds.contains(id)).collect(Collectors.toList());
    if (areChildEntitiesBudgetGroups) {
      budgetGroupDao.updateParentId(uuid, childEntityIds);
      budgetGroupDao.updateParentId(null, freeChildEntities);
    } else {
      budgetDao.updateParentId(uuid, childEntityIds);
      budgetDao.updateParentId(null, freeChildEntities);
    }
  }

  @Override
  public BudgetGroup get(String uuid, String accountId) {
    return budgetGroupDao.get(uuid, accountId);
  }

  @Override
  public List<BudgetGroup> list(String accountId, BudgetGroupSortType budgetGroupSortType, CCMSortOrder ccmSortOrder) {
    return budgetGroupDao.list(accountId, Integer.MAX_VALUE, 0, budgetGroupSortType, ccmSortOrder);
  }

  @Override
  public boolean delete(String uuid, String accountId) {
    BudgetGroup budgetGroup = budgetGroupDao.get(uuid, accountId);
    boolean areChildEntitiesBudgetGroups =
        BudgetGroupUtils.areChildEntitiesBudgetGroups(budgetGroup.getChildEntities());
    List<String> childEntityIds =
        budgetGroup.getChildEntities().stream().map(BudgetGroupChildEntityDTO::getId).collect(Collectors.toList());
    if (areChildEntitiesBudgetGroups) {
      budgetGroupDao.unsetParent(childEntityIds);
    } else {
      budgetDao.unsetParent(childEntityIds);
    }

    if (budgetGroup.getParentBudgetGroupId() != null) {
      BudgetGroup parentBudgetGroup = budgetGroupDao.get(budgetGroup.getParentBudgetGroupId(), accountId);
      if (parentBudgetGroup.getChildEntities() != null && parentBudgetGroup.getChildEntities().size() > 1) {
        BudgetGroupChildEntityDTO deletedChildEntity = parentBudgetGroup.getChildEntities()
                                                           .stream()
                                                           .filter(childEntity -> childEntity.getId().equals(uuid))
                                                           .collect(Collectors.toList())
                                                           .get(0);
        parentBudgetGroup = updateProportionsOnDeletion(deletedChildEntity, parentBudgetGroup);
        parentBudgetGroup =
            BudgetGroupUtils.updateBudgetGroupAmountOnChildEntityDeletion(parentBudgetGroup, budgetGroup);
        updateCostsOfParentBudgetGroupsOnEntityDeletion(parentBudgetGroup);
        BudgetGroup rootBudgetGroup = getRootBudgetGroup(budgetGroup);
        cascadeBudgetGroupAmount(rootBudgetGroup);
      } else {
        delete(budgetGroup.getParentBudgetGroupId(), accountId);
      }
    }
    return budgetGroupDao.delete(uuid, accountId);
  }

  public void updateCostsOfParentBudgetGroupsOnEntityDeletion(BudgetGroup immediateParent) {
    update(immediateParent.getUuid(), immediateParent.getAccountId(), immediateParent);
    if (immediateParent.getParentBudgetGroupId() != null) {
      updateCostsOfParentBudgetGroupsOnEntityDeletion(
          budgetGroupDao.get(immediateParent.getParentBudgetGroupId(), immediateParent.getAccountId()));
    }
  }

  public void updateBudgetGroupAmount(BudgetGroup budgetGroup, String accountId) {
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
              for (int month = 0; month < BudgetGroupUtils.MONTHS; month++) {
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

  public void updateBudgetGroupCosts(BudgetGroup budgetGroup, String accountId) {
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

  @Override
  public BudgetGroup updateProportionsOnDeletion(
      BudgetGroupChildEntityDTO deletedChildEntity, BudgetGroup parentBudgetGroup) {
    List<BudgetGroupChildEntityDTO> updatedChildEntities =
        parentBudgetGroup.getChildEntities()
            .stream()
            .filter(childEntity -> !childEntity.getId().equals(deletedChildEntity.getId()))
            .collect(Collectors.toList());
    double totalRemainingChildEntities = updatedChildEntities.size();
    if (updatedChildEntities.size() == 0) {
      throw new InvalidRequestException(BudgetGroupUtils.NO_CHILD_ENTITY_PRESENT_EXCEPTION);
    }
    switch (parentBudgetGroup.getCascadeType()) {
      case EQUAL:
      case NO_CASCADE:
        parentBudgetGroup.setChildEntities(updatedChildEntities);
        budgetGroupDao.updateChildEntities(parentBudgetGroup.getUuid(), updatedChildEntities);
        return parentBudgetGroup;
      case PROPORTIONAL:
        List<BudgetGroupChildEntityDTO> updatedChildEntitiesWithProportionsAdjusted = new ArrayList<>();
        double proportionToBeAdded =
            BudgetUtils.getRoundedValue(deletedChildEntity.getProportion() / totalRemainingChildEntities);
        updatedChildEntities.forEach(childEntity
            -> updatedChildEntitiesWithProportionsAdjusted.add(
                BudgetGroupChildEntityDTO.builder()
                    .id(childEntity.getId())
                    .isBudgetGroup(childEntity.isBudgetGroup())
                    .proportion(BudgetUtils.getRoundedValue(childEntity.getProportion() + proportionToBeAdded))
                    .build()));
        parentBudgetGroup.setChildEntities(updatedChildEntitiesWithProportionsAdjusted);
        budgetGroupDao.updateChildEntities(parentBudgetGroup.getUuid(), updatedChildEntitiesWithProportionsAdjusted);
        return parentBudgetGroup;
      default:
        return null;
    }
  }

  @Override
  public List<ValueDataPoint> getAggregatedAmount(
      String accountId, boolean areChildEntitiesBudgets, List<String> childEntityIds) {
    List<Budget> childBudgets = budgetDao.list(accountId, childEntityIds);
    if (childBudgets == null || childBudgets.size() == 0) {
      areChildEntitiesBudgets = false;
    }
    if (areChildEntitiesBudgets) {
      if (childBudgets.size() != childEntityIds.size()) {
        throw new InvalidRequestException(BudgetGroupUtils.INVALID_CHILD_ENTITY_ID_EXCEPTION);
      }
      return BudgetGroupUtils.getAggregatedBudgetAmountOfChildBudgets(childBudgets);
    } else {
      List<BudgetGroup> childBudgetGroups = budgetGroupDao.list(accountId, childEntityIds);
      if (childBudgetGroups == null || childBudgetGroups.size() != childEntityIds.size()) {
        throw new InvalidRequestException(BudgetGroupUtils.INVALID_CHILD_ENTITY_ID_EXCEPTION);
      }
      return BudgetGroupUtils.getAggregatedBudgetAmountOfChildBudgetGroups(childBudgetGroups);
    }
  }

  @Override
  public List<BudgetSummary> listAllEntities(
      String accountId, BudgetGroupSortType budgetGroupSortType, CCMSortOrder ccmSortOrder) {
    HashMap<String, List<BudgetSummary>> flattenedChildList =
        getFlattenedList(accountId, budgetGroupSortType, ccmSortOrder);
    List<BudgetSummary> summaryList = new ArrayList<>();
    List<BudgetGroup> budgetGroups = list(accountId, budgetGroupSortType, ccmSortOrder);
    List<Budget> budgets = budgetDao.list(accountId, Integer.MAX_VALUE - 1, 0,
        budgetGroupSortType == null ? null : budgetGroupSortType.getBudgetSortType(), ccmSortOrder);

    budgets.forEach(budget -> summaryList.add(BudgetUtils.buildBudgetSummary(budget)));
    budgetGroups.forEach(budgetGroup
        -> summaryList.add(
            BudgetGroupUtils.buildBudgetGroupSummary(budgetGroup, flattenedChildList.get(budgetGroup.getUuid()))));
    summaryList.sort(getComparator(budgetGroupSortType, ccmSortOrder));

    return summaryList;
  }

  @Override
  public List<BudgetSummary> listBudgetsAndBudgetGroupsSummary(
      String accountId, String id, BudgetGroupSortType budgetGroupSortType, CCMSortOrder ccmSortOrder) {
    List<BudgetSummary> summaryList = new ArrayList<>();

    List<BudgetGroup> budgetGroups = list(accountId, budgetGroupSortType, ccmSortOrder);
    final Map<String, BudgetGroup> budgetGroupIdMapping =
        budgetGroups.stream().collect(Collectors.toMap(BudgetGroup::getUuid, budgetGroup -> budgetGroup));

    List<Budget> budgets = budgetDao.list(accountId, Integer.MAX_VALUE - 1, 0,
        budgetGroupSortType == null ? null : budgetGroupSortType.getBudgetSortType(), ccmSortOrder);
    budgets = budgets.stream().filter(BudgetUtils::isPerspectiveBudget).collect(Collectors.toList());
    unsetInvalidParents(budgetGroups, budgets);
    List<Budget> budgetsPartOfBudgetGroups =
        budgets.stream().filter(budget -> budget.getParentBudgetGroupId() != null).collect(Collectors.toList());
    List<Budget> budgetsNotPartOfBudgetGroups =
        budgets.stream().filter(budget -> budget.getParentBudgetGroupId() == null).collect(Collectors.toList());

    if (budgetsPartOfBudgetGroups.size() != 0) {
      Map<String, List<BudgetSummary>> childEntitySummaryMapping = new LinkedHashMap<>();
      Set<String> parentBudgetGroupIds = new LinkedHashSet<>();

      for (Budget budget : budgetsPartOfBudgetGroups) {
        String parentBudgetGroupId = budget.getParentBudgetGroupId();
        parentBudgetGroupIds.add(parentBudgetGroupId);
        if (childEntitySummaryMapping.containsKey(parentBudgetGroupId)) {
          childEntitySummaryMapping.get(parentBudgetGroupId).add(BudgetUtils.buildBudgetSummary(budget));
        } else {
          List<BudgetSummary> childEntitySummary = new ArrayList<>();
          childEntitySummary.add(BudgetUtils.buildBudgetSummary(budget));
          childEntitySummaryMapping.put(parentBudgetGroupId, childEntitySummary);
        }
      }

      while (parentBudgetGroupIds.size() != 0) {
        if (id != null && childEntitySummaryMapping.containsKey(id)) {
          return childEntitySummaryMapping.get(id);
        }
        Set<String> newParentBudgetGroupIds = new LinkedHashSet<>();
        parentBudgetGroupIds.forEach(budgetGroupId -> {
          BudgetGroup budgetGroup = budgetGroupIdMapping.get(budgetGroupId);
          String parentBudgetGroupId = budgetGroup.getParentBudgetGroupId();
          if (parentBudgetGroupId != null) {
            newParentBudgetGroupIds.add(parentBudgetGroupId);
            if (childEntitySummaryMapping.containsKey(parentBudgetGroupId)) {
              childEntitySummaryMapping.get(parentBudgetGroupId)
                  .add(BudgetGroupUtils.buildBudgetGroupSummary(
                      budgetGroup, childEntitySummaryMapping.get(budgetGroupId)));
            } else {
              List<BudgetSummary> childEntitySummary = new ArrayList<>();
              childEntitySummary.add(
                  BudgetGroupUtils.buildBudgetGroupSummary(budgetGroup, childEntitySummaryMapping.get(budgetGroupId)));
              childEntitySummaryMapping.put(parentBudgetGroupId, childEntitySummary);
            }
          } else {
            summaryList.add(
                BudgetGroupUtils.buildBudgetGroupSummary(budgetGroup, childEntitySummaryMapping.get(budgetGroupId)));
          }
        });
        parentBudgetGroupIds = newParentBudgetGroupIds;
      }
    }

    budgetsNotPartOfBudgetGroups.forEach(budget -> summaryList.add(BudgetUtils.buildBudgetSummary(budget)));
    summaryList.sort(getComparator(budgetGroupSortType, ccmSortOrder));
    return summaryList;
  }

  @Override
  public void cascadeBudgetGroupAmount(BudgetGroup budgetGroup) {
    List<BudgetGroupChildEntityDTO> childEntities = budgetGroup.getChildEntities();
    boolean areChildEntitiesBudgetGroups = BudgetGroupUtils.areChildEntitiesBudgetGroups(childEntities);
    if (budgetGroup.getCascadeType() == NO_CASCADE) {
      if (areChildEntitiesBudgetGroups) {
        List<String> childEntityIds =
            childEntities.stream().map(BudgetGroupChildEntityDTO::getId).collect(Collectors.toList());
        List<BudgetGroup> childBudgetGroups = budgetGroupDao.list(budgetGroup.getAccountId(), childEntityIds);
        childBudgetGroups.forEach(this::cascadeBudgetGroupAmount);
      }
    } else {
      boolean isMonthlyBreakdownPresent = budgetGroup.getPeriod() == YEARLY
          && budgetGroup.getBudgetGroupMonthlyBreakdown().getBudgetBreakdown() == MONTHLY;
      double totalNumberOfChildEntities = childEntities.size();
      if (areChildEntitiesBudgetGroups) {
        List<String> childEntityIds =
            budgetGroup.getChildEntities().stream().map(BudgetGroupChildEntityDTO::getId).collect(Collectors.toList());

        if (isMonthlyBreakdownPresent) {
          childEntities.forEach(childEntity
              -> budgetGroupDao.updateBudgetGroupAmountInBreakdown(childEntity.getId(),
                  BudgetGroupUtils.getCascadedMonthlyAmount(budgetGroup.getCascadeType(), totalNumberOfChildEntities,
                      childEntity.getProportion(),
                      budgetGroup.getBudgetGroupMonthlyBreakdown().getBudgetMonthlyAmount())));
        }
        childEntities.forEach(childEntity
            -> budgetGroupDao.updateBudgetGroupAmount(childEntity.getId(),
                BudgetGroupUtils.getCascadedAmount(budgetGroup.getCascadeType(), totalNumberOfChildEntities,
                    childEntity.getProportion(), budgetGroup.getBudgetGroupAmount())));

        List<BudgetGroup> childBudgetGroups = budgetGroupDao.list(budgetGroup.getAccountId(), childEntityIds);
        childBudgetGroups.forEach(this::cascadeBudgetGroupAmount);
      } else {
        if (isMonthlyBreakdownPresent) {
          childEntities.forEach(childEntity
              -> budgetDao.updateBudgetAmountInBreakdown(childEntity.getId(),
                  BudgetGroupUtils.getCascadedMonthlyAmount(budgetGroup.getCascadeType(), totalNumberOfChildEntities,
                      childEntity.getProportion(),
                      budgetGroup.getBudgetGroupMonthlyBreakdown().getBudgetMonthlyAmount())));
        }
        childEntities.forEach(childEntity
            -> budgetDao.updateBudgetAmount(childEntity.getId(),
                BudgetGroupUtils.getCascadedAmount(budgetGroup.getCascadeType(), totalNumberOfChildEntities,
                    childEntity.getProportion(), budgetGroup.getBudgetGroupAmount())));
      }
    }
  }

  @Override
  public void upwardCascadeBudgetGroupAmount(BudgetGroup budgetGroup, Boolean isMonthlyBreadownBudget,
      Double budgetAmountDiff, Double[] budgetAmountMonthlyDiff) {
    if (budgetGroup.getCascadeType() == NO_CASCADE) {
      return;
    }

    double newBudgetGroupAmount = budgetGroup.getBudgetGroupAmount() + budgetAmountDiff;
    budgetGroup.setBudgetGroupAmount(newBudgetGroupAmount);
    if (isMonthlyBreadownBudget) {
      budgetGroup.getBudgetGroupMonthlyBreakdown().setBudgetMonthlyAmount(
          BudgetGroupUtils.updateBudgetGroupMonthlyAmount(
              budgetGroup.getBudgetGroupMonthlyBreakdown().getBudgetMonthlyAmount(), budgetAmountMonthlyDiff));
    }

    List<BudgetGroupChildEntityDTO> budgetGroupChildEntities = budgetGroup.getChildEntities();
    if (!Lists.isNullOrEmpty(budgetGroupChildEntities)) {
      double totalAmount = 0.0;
      Boolean areChildrenBudgetGroup = budgetGroupChildEntities.get(0).isBudgetGroup();
      if (areChildrenBudgetGroup) {
        List<BudgetGroup> childBudgetGroups = budgetGroupDao.list(budgetGroup.getAccountId(),
            budgetGroupChildEntities.stream().map(childEntity -> childEntity.getId()).collect(Collectors.toList()));
        for (BudgetGroup childBudgetGroup : childBudgetGroups) {
          totalAmount += childBudgetGroup.getBudgetGroupAmount();
        }
        double totalRatioChange = newBudgetGroupAmount / totalAmount;
        budgetGroupChildEntities = new ArrayList<>();
        Boolean sameProportion = true;
        Double currentProportion = -1.0;
        for (BudgetGroup childBudgetGroup : childBudgetGroups) {
          double proportion = (childBudgetGroup.getBudgetGroupAmount() * totalRatioChange) / newBudgetGroupAmount;
          budgetGroupChildEntities.add(BudgetGroupChildEntityDTO.builder()
                                           .isBudgetGroup(true)
                                           .id(childBudgetGroup.getUuid())
                                           .proportion(proportion)
                                           .build());
          if (currentProportion < 0) {
            currentProportion = proportion;
          } else if (currentProportion != proportion) {
            sameProportion = false;
          }
        }
        if (!sameProportion && budgetGroup.getCascadeType() == EQUAL) {
          budgetGroup.setCascadeType(PROPORTIONAL);
        }
        budgetGroup.setChildEntities(budgetGroupChildEntities);
      } else {
        List<Budget> childBudgets = budgetDao.list(budgetGroup.getAccountId(),
            budgetGroupChildEntities.stream().map(childEntity -> childEntity.getId()).collect(Collectors.toList()));
        for (Budget childBudget : childBudgets) {
          totalAmount += childBudget.getBudgetAmount();
        }
        double totalRatioChange = newBudgetGroupAmount / totalAmount;
        budgetGroupChildEntities = new ArrayList<>();
        Boolean sameProportion = true;
        Double currentProportion = -1.0;
        for (Budget childBudget : childBudgets) {
          double proportion = (childBudget.getBudgetAmount() * totalRatioChange) / newBudgetGroupAmount;
          budgetGroupChildEntities.add(BudgetGroupChildEntityDTO.builder()
                                           .isBudgetGroup(false)
                                           .id(childBudget.getUuid())
                                           .proportion(proportion)
                                           .build());
          if (currentProportion < 0) {
            currentProportion = proportion;
          } else if (currentProportion != proportion) {
            sameProportion = false;
          }
        }
        if (!sameProportion && budgetGroup.getCascadeType() == EQUAL) {
          budgetGroup.setCascadeType(PROPORTIONAL);
        }
        budgetGroup.setChildEntities(budgetGroupChildEntities);
      }
    }

    budgetGroupDao.update(budgetGroup.getUuid(), budgetGroup.getAccountId(), budgetGroup);

    if (budgetGroup.getParentBudgetGroupId() != null) {
      BudgetGroup parentBudgetGroup = get(budgetGroup.getParentBudgetGroupId(), budgetGroup.getAccountId());
      upwardCascadeBudgetGroupAmount(
          parentBudgetGroup, isMonthlyBreadownBudget, budgetAmountDiff, budgetAmountMonthlyDiff);
    }
  }

  @Override
  public void updateBudgetGroupCosts(BudgetGroup budgetGroup) {
    boolean areChildEntitiesBudgetGroups =
        BudgetGroupUtils.areChildEntitiesBudgetGroups(budgetGroup.getChildEntities());
    List<String> childEntityIds =
        budgetGroup.getChildEntities().stream().map(BudgetGroupChildEntityDTO::getId).collect(Collectors.toList());
    boolean isBreakdownMonthly = budgetGroup.getPeriod() == YEARLY
        && budgetGroup.getBudgetGroupMonthlyBreakdown().getBudgetBreakdown() == MONTHLY;
    if (areChildEntitiesBudgetGroups) {
      List<BudgetGroup> childBudgetGroups = budgetGroupDao.list(budgetGroup.getAccountId(), childEntityIds);
      if (isBreakdownMonthly) {
        BudgetMonthlyBreakdown breakdown =
            BudgetMonthlyBreakdown.builder()
                .budgetBreakdown(budgetGroup.getBudgetGroupMonthlyBreakdown().getBudgetBreakdown())
                .budgetMonthlyAmount(budgetGroup.getBudgetGroupMonthlyBreakdown().getBudgetMonthlyAmount())
                .actualMonthlyCost(BudgetGroupUtils.getAggregatedCostsForBudgetGroupsWithBreakdown(
                    childBudgetGroups, BudgetGroupUtils.COST_TYPE_ACTUAL))
                .forecastMonthlyCost(BudgetGroupUtils.getAggregatedCostsForBudgetGroupsWithBreakdown(
                    childBudgetGroups, BudgetGroupUtils.COST_TYPE_FORECASTED))
                .yearlyLastPeriodCost(BudgetGroupUtils.getAggregatedCostsForBudgetGroupsWithBreakdown(
                    childBudgetGroups, BudgetGroupUtils.COST_TYPE_LAST_PERIOD))
                .build();
        budgetGroup.setBudgetGroupMonthlyBreakdown(breakdown);
      }
      budgetGroup.setActualCost(
          BudgetGroupUtils.getAggregatedCostsForBudgetGroups(childBudgetGroups, BudgetGroupUtils.COST_TYPE_ACTUAL));
      budgetGroup.setForecastCost(
          BudgetGroupUtils.getAggregatedCostsForBudgetGroups(childBudgetGroups, BudgetGroupUtils.COST_TYPE_FORECASTED));
      budgetGroup.setLastMonthCost(BudgetGroupUtils.getAggregatedCostsForBudgetGroups(
          childBudgetGroups, BudgetGroupUtils.COST_TYPE_LAST_PERIOD));

    } else {
      List<Budget> childBudgets = budgetDao.list(budgetGroup.getAccountId(), childEntityIds);
      if (isBreakdownMonthly) {
        BudgetMonthlyBreakdown breakdown =
            BudgetMonthlyBreakdown.builder()
                .budgetBreakdown(budgetGroup.getBudgetGroupMonthlyBreakdown().getBudgetBreakdown())
                .budgetMonthlyAmount(budgetGroup.getBudgetGroupMonthlyBreakdown().getBudgetMonthlyAmount())
                .actualMonthlyCost(BudgetGroupUtils.getAggregatedCostsForBudgetsWithBreakdown(
                    childBudgets, BudgetGroupUtils.COST_TYPE_ACTUAL))
                .forecastMonthlyCost(BudgetGroupUtils.getAggregatedCostsForBudgetsWithBreakdown(
                    childBudgets, BudgetGroupUtils.COST_TYPE_FORECASTED))
                .yearlyLastPeriodCost(BudgetGroupUtils.getAggregatedCostsForBudgetsWithBreakdown(
                    childBudgets, BudgetGroupUtils.COST_TYPE_LAST_PERIOD))
                .build();
        budgetGroup.setBudgetGroupMonthlyBreakdown(breakdown);
      }
      budgetGroup.setActualCost(
          BudgetGroupUtils.getAggregatedCostsForBudgets(childBudgets, BudgetGroupUtils.COST_TYPE_ACTUAL));
      budgetGroup.setForecastCost(
          BudgetGroupUtils.getAggregatedCostsForBudgets(childBudgets, BudgetGroupUtils.COST_TYPE_FORECASTED));
      budgetGroup.setLastMonthCost(
          BudgetGroupUtils.getAggregatedCostsForBudgets(childBudgets, BudgetGroupUtils.COST_TYPE_LAST_PERIOD));
    }
  }

  private void updateBudgetGroupBreakdown(BudgetGroup budgetGroup) {
    boolean isBreakdownMonthly = budgetGroup.getPeriod() == YEARLY
        && budgetGroup.getBudgetGroupMonthlyBreakdown().getBudgetMonthlyAmount() != null
        && budgetGroup.getBudgetGroupMonthlyBreakdown().getBudgetMonthlyAmount().size() == MONTHS;
    if (isBreakdownMonthly) {
      budgetGroup.getBudgetGroupMonthlyBreakdown().setBudgetBreakdown(MONTHLY);
    }
  }

  private void updateBudgetGroupAmount(BudgetGroup budgetGroup) {
    boolean isBreakdownMonthly = budgetGroup.getPeriod() == YEARLY
        && budgetGroup.getBudgetGroupMonthlyBreakdown().getBudgetBreakdown() == MONTHLY;

    if (budgetGroup.getCascadeType() == NO_CASCADE) {
      List<BudgetGroupChildEntityDTO> childEntities = budgetGroup.getChildEntities();
      boolean areChildEntitiesBudgetGroups = BudgetGroupUtils.areChildEntitiesBudgetGroups(childEntities);
      List<String> childEntityIds =
          childEntities.stream().map(BudgetGroupChildEntityDTO::getId).collect(Collectors.toList());
      List<ValueDataPoint> budgetAmount =
          getAggregatedAmount(budgetGroup.getAccountId(), !areChildEntitiesBudgetGroups, childEntityIds);
      budgetGroup.setBudgetGroupAmount(BudgetGroupUtils.getSumGivenTimeAndValueList(budgetAmount));
      if (isBreakdownMonthly) {
        budgetGroup.getBudgetGroupMonthlyBreakdown().setBudgetMonthlyAmount(budgetAmount);
      }
    } else {
      if (isBreakdownMonthly) {
        budgetGroup.setBudgetGroupAmount(BudgetGroupUtils.getSumGivenTimeAndValueList(
            budgetGroup.getBudgetGroupMonthlyBreakdown().getBudgetMonthlyAmount()));
      }
    }
  }

  public void validateChildEntities(
      BudgetGroup budgetGroup, boolean areChildEntitiesBudgetGroups, List<String> childEntityIds) {
    if (areChildEntitiesBudgetGroups) {
      BudgetGroupUtils.validateChildBudgetGroups(budgetGroupDao.list(budgetGroup.getAccountId(), childEntityIds));
    } else {
      BudgetGroupUtils.validateChildBudgets(budgetDao.list(budgetGroup.getAccountId(), childEntityIds));
    }
  }

  private void validateBudgetGroupDetails(BudgetGroup budgetGroup, BudgetGroup oldBudgetGroup) {
    // We should throw Exception in case there is a parent for budget group
    // And startTime, period is modified
    if (budgetGroup.getParentBudgetGroupId() != null) {
      if (budgetGroup.getStartTime() != oldBudgetGroup.getStartTime()
          || budgetGroup.getPeriod() != oldBudgetGroup.getPeriod()) {
        throw new InvalidRequestException(BudgetGroupUtils.INVALID_BUDGET_DETAILS_EXCEPTION);
      }
    }
  }

  public void validateParentOfChildEntities(
      BudgetGroup budgetGroup, boolean areChildEntitiesBudgetGroups, List<String> childEntityIds) {
    if (areChildEntitiesBudgetGroups) {
      BudgetGroupUtils.validateNoParentPresentForChildBudgetGroups(
          budgetGroupDao.list(budgetGroup.getAccountId(), childEntityIds));
    } else {
      BudgetGroupUtils.validateNoParentPresentForChildBudgets(
          budgetDao.list(budgetGroup.getAccountId(), childEntityIds));
    }
  }

  public void validateProportion(BudgetGroup budgetGroup) {
    if (budgetGroup.getCascadeType() == PROPORTIONAL) {
      List<Double> childProportions = budgetGroup.getChildEntities()
                                          .stream()
                                          .map(budgetGroupChildEntityDTO -> budgetGroupChildEntityDTO.getProportion())
                                          .collect(Collectors.toList());
      Double totalProportion = 0.0;
      for (int child = 0; child < childProportions.size(); child++) {
        if (childProportions.get(child) < 0 || childProportions.get(child) > 100) {
          throw new InvalidRequestException(INVALID_INDIVIDUAL_PROPORTION);
        }
        totalProportion += childProportions.get(child);
      }
      if (totalProportion != 100.0) {
        throw new InvalidRequestException(INVALID_TOTAL_PROPORTION);
      }
    }
  }

  @Override
  public BudgetData getBudgetGroupTimeSeriesStats(BudgetGroup budgetGroup, BudgetBreakdown breakdown) {
    if (budgetGroup == null) {
      throw new InvalidRequestException(INVALID_BUDGET_GROUP_ID_EXCEPTION);
    }

    List<BudgetCostData> budgetGroupCostDataList = new ArrayList<>();
    Double budgetedGroupAmount = budgetGroup.getBudgetGroupAmount();
    if (budgetedGroupAmount == null) {
      budgetedGroupAmount = 0.0;
    }

    if (budgetGroup.getPeriod() == BudgetPeriod.YEARLY && breakdown == BudgetBreakdown.MONTHLY) {
      Double[] actualCost = budgetGroup.getBudgetGroupMonthlyBreakdown().getActualMonthlyCost();
      if (actualCost == null || actualCost.length != MONTHS) {
        log.error("Missing monthly actualCost of yearly budget group with id:" + budgetGroup.getUuid());
        throw new InvalidRequestException(MISSING_BUDGET_GROUP_DATA_EXCEPTION);
      }

      Double[] budgetGroupAmount =
          BudgetUtils.getYearlyMonthWiseValues(budgetGroup.getBudgetGroupMonthlyBreakdown().getBudgetMonthlyAmount());
      if (budgetGroupAmount == null || budgetGroupAmount.length != MONTHS) {
        log.error("Missing monthly budgetCost of yearly budget group with id:" + budgetGroup.getUuid());
        throw new InvalidRequestException(MISSING_BUDGET_GROUP_DATA_EXCEPTION);
      }

      Double[] forecastMonthlyCost = budgetGroup.getBudgetGroupMonthlyBreakdown().getForecastMonthlyCost();
      if (forecastMonthlyCost == null || forecastMonthlyCost.length != MONTHS) {
        log.error("Missing monthly forecastCost of yearly budget group with id:" + budgetGroup.getUuid());
        throw new InvalidRequestException(MISSING_BUDGET_GROUP_DATA_EXCEPTION);
      }

      long startTime = budgetGroup.getStartTime();
      for (int month = 0; month < MONTHS; month++) {
        long endTime = BudgetUtils.getEndTimeForBudget(startTime, BudgetPeriod.MONTHLY) - BudgetUtils.ONE_DAY_MILLIS;
        double budgetGroupVariance =
            BudgetUtils.getBudgetVariance(budgetGroupAmount[month], forecastMonthlyCost[month]);
        double budgetGroupVariancePercentage =
            BudgetUtils.getBudgetVariancePercentage(budgetGroupVariance, budgetGroupAmount[month]);
        BudgetCostData budgetCostData =
            BudgetCostData.builder()
                .actualCost(BudgetUtils.getRoundedValue(actualCost[month]))
                .forecastCost(BudgetUtils.getRoundedValue(forecastMonthlyCost[month]))
                .budgeted(BudgetUtils.getRoundedValue(budgetGroupAmount[month]))
                .budgetVariance(BudgetUtils.getRoundedValue(budgetGroupVariance))
                .budgetVariancePercentage(BudgetUtils.getRoundedValue(budgetGroupVariancePercentage))
                .time(startTime)
                .endTime(endTime)
                .build();
        budgetGroupCostDataList.add(budgetCostData);
        startTime = endTime + BudgetUtils.ONE_DAY_MILLIS;
      }
    } else {
      for (BudgetCostData historyBudgetGroupCostData : budgetGroup.getBudgetGroupHistory().values()) {
        budgetGroupCostDataList.add(historyBudgetGroupCostData);
      }
      double budgetGroupAmount = budgetGroup.getBudgetGroupAmount();
      double budgetGroupVariance = BudgetUtils.getBudgetVariance(budgetGroupAmount, budgetGroup.getActualCost());
      double budgetGroupVariancePercentage =
          BudgetUtils.getBudgetVariancePercentage(budgetGroupVariance, budgetGroupAmount);
      BudgetCostData latestBudgetCostData = BudgetCostData.builder()
                                                .time(budgetGroup.getStartTime())
                                                .endTime(budgetGroup.getEndTime())
                                                .actualCost(budgetGroup.getActualCost())
                                                .forecastCost(budgetGroup.getForecastCost())
                                                .budgeted(budgetGroupAmount)
                                                .budgetVariance(budgetGroupVariance)
                                                .budgetVariancePercentage(budgetGroupVariancePercentage)
                                                .build();
      budgetGroupCostDataList.add(latestBudgetCostData);
    }
    return BudgetData.builder().costData(budgetGroupCostDataList).forecastCost(budgetGroup.getForecastCost()).build();
  }

  private void updateBudgetGroupEndTime(BudgetGroup budgetGroup) {
    boolean isStartTimeValid = true;
    try {
      budgetGroup.setEndTime(BudgetUtils.getEndTimeForBudget(budgetGroup.getStartTime(), budgetGroup.getPeriod()));
      if (budgetGroup.getEndTime() < BudgetUtils.getStartOfCurrentDay()) {
        isStartTimeValid = false;
      }
    } catch (Exception e) {
      log.error("Error occurred while updating end time of budget group: {}, Exception : {}", budgetGroup.getUuid(), e);
    }

    if (!isStartTimeValid) {
      throw new InvalidRequestException(BudgetUtils.INVALID_START_TIME_EXCEPTION);
    }
  }

  private void updateBudgetGroupHistory(BudgetGroup budgetGroup, String accountId) {
    HashMap<Long, BudgetCostData> budgetGroupHistory = new HashMap<>();
    for (BudgetGroupChildEntityDTO budgetGroupChildEntityDTO : budgetGroup.getChildEntities()) {
      HashMap<Long, BudgetCostData> childHistory;
      if (budgetGroupChildEntityDTO.isBudgetGroup()) {
        BudgetGroup childBudgetGroup = get(budgetGroupChildEntityDTO.getId(), accountId);
        childHistory = childBudgetGroup != null ? childBudgetGroup.getBudgetGroupHistory() : null;
      } else {
        Budget childBudget = budgetDao.get(budgetGroupChildEntityDTO.getId(), accountId);
        childHistory = childBudget != null ? childBudget.getBudgetHistory() : null;
      }
      for (Long startTime : childHistory.keySet()) {
        double actualCost;
        if (budgetGroupHistory.containsKey(startTime)) {
          actualCost = budgetGroupHistory.get(startTime).getActualCost() + childHistory.get(startTime).getActualCost();
        } else {
          actualCost = childHistory.get(startTime).getActualCost();
        }
        double budgetVariance = BudgetUtils.getBudgetVariance(budgetGroup.getBudgetGroupAmount(), actualCost);
        double budgetVariancePercentage =
            BudgetUtils.getBudgetVariancePercentage(budgetVariance, budgetGroup.getBudgetGroupAmount());
        BudgetCostData newBudgetGroupCostData = BudgetCostData.builder()
                                                    .actualCost(actualCost)
                                                    .time(childHistory.get(startTime).getTime())
                                                    .endTime(childHistory.get(startTime).getEndTime())
                                                    .budgeted(budgetGroup.getBudgetGroupAmount())
                                                    .forecastCost(0.0)
                                                    .budgetVariance(budgetVariance)
                                                    .budgetVariancePercentage(budgetVariancePercentage)
                                                    .build();
        budgetGroupHistory.put(startTime, newBudgetGroupCostData);
      }
    }
    budgetGroup.setBudgetGroupHistory(budgetGroupHistory);
  }

  private void unsetInvalidParents(List<BudgetGroup> budgetGroups, List<Budget> budgets) {
    List<String> validParentIds = budgetGroups.stream().map(BudgetGroup::getUuid).collect(Collectors.toList());
    List<String> budgetsWithInvalidParents =
        budgets.stream()
            .filter(budget -> !validParentIds.contains(budget.getParentBudgetGroupId()))
            .map(Budget::getUuid)
            .collect(Collectors.toList());
    budgetDao.unsetParent(budgetsWithInvalidParents);
  }

  public BudgetGroup getRootBudgetGroup(BudgetGroup budgetGroup) {
    BudgetGroup rootBudgetGroup = budgetGroupDao.get(budgetGroup.getParentBudgetGroupId(), budgetGroup.getAccountId());
    while (rootBudgetGroup.getParentBudgetGroupId() != null) {
      rootBudgetGroup = budgetGroupDao.get(rootBudgetGroup.getParentBudgetGroupId(), rootBudgetGroup.getAccountId());
    }
    return rootBudgetGroup;
  }

  private HashMap<String, List<BudgetSummary>> getFlattenedList(
      String accountId, BudgetGroupSortType budgetGroupSortType, CCMSortOrder ccmSortOrder) {
    List<BudgetSummary> nestedSummaryList =
        listBudgetsAndBudgetGroupsSummary(accountId, null, budgetGroupSortType, ccmSortOrder);
    nestedSummaryList =
        nestedSummaryList.stream().filter(budgetSummary -> budgetSummary.isBudgetGroup()).collect(Collectors.toList());

    LinkedHashMap<String, List<BudgetSummary>> flattenedChildList = new LinkedHashMap<>();
    Queue<BudgetSummary> budgetSummaryStore = new LinkedList<>();
    nestedSummaryList.forEach(summary -> budgetSummaryStore.add(summary));

    while (budgetSummaryStore.size() > 0) {
      BudgetSummary currentBudgetSummary = budgetSummaryStore.poll();
      flattenedChildList.put(currentBudgetSummary.getId(), currentBudgetSummary.getChildEntities());
      if (currentBudgetSummary.getChildEntities() != null) {
        nestedSummaryList = currentBudgetSummary.getChildEntities()
                                .stream()
                                .filter(budgetSummary -> budgetSummary.isBudgetGroup())
                                .collect(Collectors.toList());
        nestedSummaryList.forEach(summary -> budgetSummaryStore.add(summary));
      }
    }
    return flattenedChildList;
  }

  private void updateBudgetGroupParent(BudgetGroup budgetGroup) {
    if (budgetGroup.getParentBudgetGroupId() != null) {
      BudgetGroup parentBudgetGroup =
          budgetGroupDao.get(budgetGroup.getParentBudgetGroupId(), budgetGroup.getAccountId());
      if (parentBudgetGroup == null) {
        budgetGroup.setParentBudgetGroupId(null);
      }
    }
  }

  private void cascadeUpwardBudgetGroupAmount(BudgetGroup budgetGroup, BudgetGroup oldBudgetGroup) {
    BudgetGroup parentBudgetGroup = get(budgetGroup.getParentBudgetGroupId(), budgetGroup.getAccountId());
    Double amountDiff = budgetGroup.getBudgetGroupAmount() - oldBudgetGroup.getBudgetGroupAmount();
    Double[] amountMonthlyDiff = null;
    Boolean isMonthlyBreadownBudgetGroup = false;
    if (budgetGroup.getBudgetGroupMonthlyBreakdown().getBudgetBreakdown() == MONTHLY) {
      isMonthlyBreadownBudgetGroup = true;
    }
    if (isMonthlyBreadownBudgetGroup) {
      amountMonthlyDiff = BudgetUtils.getBudgetAmountMonthlyDifference(
          budgetGroup.getBudgetGroupMonthlyBreakdown().getBudgetMonthlyAmount(),
          oldBudgetGroup.getBudgetGroupMonthlyBreakdown().getBudgetMonthlyAmount());
    }
    upwardCascadeBudgetGroupAmount(parentBudgetGroup, isMonthlyBreadownBudgetGroup, amountDiff, amountMonthlyDiff);
  }

  private Comparator getComparator(BudgetGroupSortType budgetGroupSortType, CCMSortOrder ccmSortOrder) {
    Comparator comparator;
    if (budgetGroupSortType == BudgetGroupSortType.BUDGET_GROUP_AMOUNT) {
      comparator = Comparator.comparingDouble(BudgetSummary::getBudgetAmount);
    } else if (budgetGroupSortType == BudgetGroupSortType.BUDGET_GROUP_ACTUAL_COST) {
      comparator = Comparator.comparingDouble(BudgetSummary::getActualCost);
    } else if (budgetGroupSortType == BudgetGroupSortType.BUDGET_GROUP_FORECASTED_COST) {
      comparator = Comparator.comparingDouble(BudgetSummary::getForecastCost);
    } else {
      comparator = Comparator.comparing(BudgetSummary::getName, String.CASE_INSENSITIVE_ORDER);
    }
    if (ccmSortOrder == CCMSortOrder.DESCENDING) {
      comparator = comparator.reversed();
    }
    return comparator;
  }
}
