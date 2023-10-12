/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.budget;

import io.harness.ccm.budgetGroup.BudgetGroupChildEntityDTO;
import io.harness.ccm.budgetGroup.CascadeType;

import io.leangen.graphql.annotations.GraphQLNonNull;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class BudgetSummary {
  @GraphQLNonNull String id;
  @GraphQLNonNull String name;
  String perspectiveId;
  String perspectiveName;
  String folderId;
  @GraphQLNonNull Double budgetAmount;
  @GraphQLNonNull Double actualCost;
  @GraphQLNonNull Double forecastCost;
  @GraphQLNonNull int timeLeft;
  @GraphQLNonNull String timeUnit;
  @GraphQLNonNull String timeScope;
  List<Double> actualCostAlerts;
  List<Double> forecastCostAlerts;
  AlertThreshold[] alertThresholds;
  @GraphQLNonNull BudgetPeriod period;
  BudgetType type;
  Double growthRate;
  @GraphQLNonNull long startTime;
  BudgetMonthlyBreakdown budgetMonthlyBreakdown;
  List<BudgetSummary> childEntities;
  List<BudgetGroupChildEntityDTO> childEntityProportions;
  boolean isBudgetGroup;
  CascadeType cascadeType;
  String parentId;
  Boolean disableCurrencyWarning;
}
