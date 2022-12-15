/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.budget;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;

@Data
@Builder
@FieldNameConstants(innerTypeName = "BudgetMonthlyBreakdownKeys")
@FieldDefaults(level = AccessLevel.PRIVATE)
@Schema(description = "The budget monthly breakdown of a Yearly Budget")
public class BudgetMonthlyBreakdown {
  @Schema(description = "Budget breakdown Monthly/Yearly") BudgetBreakdown budgetBreakdown;
  @Schema(description = "Budgeted monthly amount for yearly budget") List<ValueDataPoint> budgetMonthlyAmount;
  @Schema(description = "Actual monthly cost for yearly budget") Double[] actualMonthlyCost;
  @Schema(description = "Forecasted monthly cost for yearly budget") Double[] forecastMonthlyCost;
  @Schema(description = "Yearly monthly cost for last year budget") Double[] yearlyLastPeriodCost;
}
