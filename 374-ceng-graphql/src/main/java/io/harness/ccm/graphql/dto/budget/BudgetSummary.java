package io.harness.ccm.graphql.dto.budget;

import io.harness.ccm.budget.AlertThreshold;
import io.harness.ccm.budget.BudgetPeriod;
import io.harness.ccm.budget.BudgetType;

import io.leangen.graphql.annotations.GraphQLNonNull;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class BudgetSummary {
  @GraphQLNonNull String id;
  @GraphQLNonNull String name;
  @GraphQLNonNull String perspectiveId;
  @GraphQLNonNull Double budgetAmount;
  @GraphQLNonNull Double actualCost;
  @GraphQLNonNull Double forecastCost;
  @GraphQLNonNull int timeLeft;
  @GraphQLNonNull String timeUnit;
  @GraphQLNonNull String timeScope;
  @GraphQLNonNull List<Double> actualCostAlerts;
  @GraphQLNonNull List<Double> forecastCostAlerts;
  @GraphQLNonNull AlertThreshold[] alertThresholds;
  @GraphQLNonNull BudgetPeriod period;
  @GraphQLNonNull BudgetType type;
  @GraphQLNonNull Double growthRate;
  @GraphQLNonNull long startTime;
}
