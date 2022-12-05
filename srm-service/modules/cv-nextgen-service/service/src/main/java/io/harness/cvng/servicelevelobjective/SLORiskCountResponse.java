/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective;

import io.harness.cvng.servicelevelobjective.beans.ErrorBudgetRisk;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;

@Value
@Builder
public class SLORiskCountResponse {
  private Integer totalCount;
  @Singular private List<RiskCount> riskCounts;

  @Value
  @Builder
  public static class RiskCount {
    ErrorBudgetRisk errorBudgetRisk;
    Integer count;

    @JsonIgnore
    public ErrorBudgetRisk getErrorBudgetRisk() {
      return errorBudgetRisk;
    }

    public String getIdentifier() {
      return errorBudgetRisk.name();
    }

    public String getDisplayName() {
      return errorBudgetRisk.getDisplayName();
    }
  }
}
