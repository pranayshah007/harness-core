package io.harness.ccm.commons.entities.budget;

import io.leangen.graphql.annotations.GraphQLNonNull;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class BudgetCostData {
  @GraphQLNonNull long time;
  @GraphQLNonNull long endTime;
  @GraphQLNonNull double actualCost;
  @GraphQLNonNull double budgeted;
  @GraphQLNonNull double budgetVariance;
  @GraphQLNonNull double budgetVariancePercentage;
}
