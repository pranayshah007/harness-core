/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.beans;

import io.harness.cvng.core.utils.Thresholds;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErrorBudgetRisk {
  // the order determines the order of summary in SLO dashboard page
  EXHAUSTED("Exhausted"),
  UNHEALTHY("Unhealthy"),
  NEED_ATTENTION("Need Attention"),
  OBSERVE("Observe"),
  HEALTHY("Healthy");

  private String displayName;

  public static ErrorBudgetRisk getFromPercentage(double errorBudgetRemainingPercentage) {
    if (errorBudgetRemainingPercentage >= Thresholds.HEALTHY_PERCENTAGE) {
      return ErrorBudgetRisk.HEALTHY;
    } else if (errorBudgetRemainingPercentage >= Thresholds.OBSERVE_PERCENTAGE) {
      return ErrorBudgetRisk.OBSERVE;
    } else if (errorBudgetRemainingPercentage >= Thresholds.NEED_ATTENTION_PERCENTAGE) {
      return ErrorBudgetRisk.NEED_ATTENTION;
    } else if (errorBudgetRemainingPercentage >= Thresholds.UNHEALTHY_PERCENTAGE) {
      return ErrorBudgetRisk.UNHEALTHY;
    } else {
      return ErrorBudgetRisk.EXHAUSTED;
    }
  }
}
