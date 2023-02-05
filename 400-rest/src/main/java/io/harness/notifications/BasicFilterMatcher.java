/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notifications;

import io.harness.notifications.beans.Conditions;
import io.harness.notifications.beans.Conditions.Operator;

import software.wings.beans.alert.Alert;
import software.wings.beans.alert.AlertFilter;

import lombok.Value;

/**
 * This matcher should be used there are no filters other than on alert type
 */
@Value
public class BasicFilterMatcher implements FilterMatcher {
  private AlertFilter alertFilter;
  private Alert alert;

  @Override
  public boolean matchesCondition() {
    if (alertFilter == null) {
      return false;
    }
    boolean matches = alert.getType() == alertFilter.getAlertType();

    Conditions conditions = alertFilter.getConditions();
    Operator op = conditions.getOperator();

    switch (op) {
      case MATCHING:
        return matches;
      case NOT_MATCHING:
        return !matches;
      default:
        throw new IllegalArgumentException("Unexpected value of alert filter operator: " + op);
    }
  }
}
