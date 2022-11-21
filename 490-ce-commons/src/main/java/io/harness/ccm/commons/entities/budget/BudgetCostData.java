/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

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
  @GraphQLNonNull double forecastCost;
  @GraphQLNonNull double budgeted;
  @GraphQLNonNull double budgetVariance;
  @GraphQLNonNull double budgetVariancePercentage;
}
