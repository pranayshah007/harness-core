/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notifications.conditions;

import io.harness.notifications.FilterMatcher;
import io.harness.notifications.beans.Conditions;
import io.harness.notifications.beans.Conditions.Operator;
import io.harness.notifications.beans.ManualInterventionAlertFilters;

import software.wings.beans.alert.Alert;
import software.wings.beans.alert.AlertFilter;
import software.wings.beans.alert.ManualInterventionNeededAlert;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Supplier;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@Value
@Slf4j
public class ManualInterventionFilterMatcher implements FilterMatcher {
  private AlertFilter alertFilter;
  private Alert alert;

  @Override
  public boolean matchesCondition() {
    if (alertFilter == null) {
      return false;
    }

    Conditions filterConditions = alertFilter.getConditions();
    Operator op = filterConditions.getOperator();

    ManualInterventionAlertFilters manualInterventionAlertFilters = filterConditions.getManualInterventionFilters();
    if (null == manualInterventionAlertFilters) {
      log.error("No manualInterventionFilters specified. Alert will be considered to not match filter.");
      return false;
    }

    List<Supplier<Boolean>> conditions = new LinkedList<>();
    conditions.add(() -> alert.getType() == alertFilter.getAlertType());

    List<String> appIds = manualInterventionAlertFilters.getAppIds();
    conditions.add(() -> appIds.contains(alert.getAppId()));

    List<String> envIds = manualInterventionAlertFilters.getEnvIds();

    conditions.add(() -> {
      ManualInterventionNeededAlert alertData = (ManualInterventionNeededAlert) alert.getAlertData();
      if (null == alertData) {
        log.error("Manual Intervention Alert data is null. Alert: {}", alert);
        return false;
      }
      return envIds.contains(alertData.getEnvId());
    });

    boolean matches = allTrue(conditions);

    switch (op) {
      case MATCHING:
        return matches;
      case NOT_MATCHING:
        return !matches;
      default:
        throw new IllegalArgumentException("Unexpected value of alert filter operator: " + op);
    }
  }

  private boolean allTrue(List<Supplier<Boolean>> booleanFns) {
    for (Supplier<Boolean> fn : booleanFns) {
      if (!fn.get()) {
        return false;
      }
    }

    return true;
  }
}
