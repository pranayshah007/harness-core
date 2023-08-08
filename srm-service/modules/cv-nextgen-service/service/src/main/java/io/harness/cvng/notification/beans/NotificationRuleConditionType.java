/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.notification.beans;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public enum NotificationRuleConditionType {
  @JsonProperty("ErrorBudgetRemainingPercentage")
  ERROR_BUDGET_REMAINING_PERCENTAGE(
      "ErrorBudgetRemainingPercentage", NotificationRuleType.SLO, "Error Budget Remaining Percentage"),
  @JsonProperty("ErrorBudgetRemainingMinutes")
  ERROR_BUDGET_REMAINING_MINUTES(
      "ErrorBudgetRemainingMinutes", NotificationRuleType.SLO, "Error Budget Remaining Minutes"),
  @JsonProperty("ErrorBudgetBurnRate")
  ERROR_BUDGET_BURN_RATE("ErrorBudgetBurnRate", NotificationRuleType.SLO, "Error Budget Burn Rate"),
  @JsonProperty("ChangeImpact") CHANGE_IMPACT("ChangeImpact", NotificationRuleType.MONITORED_SERVICE, "Change Impact"),
  @JsonProperty("HealthScore") HEALTH_SCORE("HealthScore", NotificationRuleType.MONITORED_SERVICE, "Health Score"),
  @JsonProperty("ChangeObserved")
  CHANGE_OBSERVED("ChangeObserved", NotificationRuleType.MONITORED_SERVICE, "Change Observed"),
  @JsonProperty("CodeErrors") CODE_ERRORS("CodeErrors", NotificationRuleType.MONITORED_SERVICE, "Code Errors"),
  @JsonProperty("FireHydrantReport")
  FIRE_HYDRANT_REPORT("FireHydrantReport", NotificationRuleType.FIRE_HYDRANT, "Fire Hydrant Report"),

  @JsonProperty("DeploymentImpactReport")
  DEPLOYMENT_IMPACT_REPORT(
      "DeploymentImpactReport", NotificationRuleType.MONITORED_SERVICE, "Deployment Impact Report");

  private final NotificationRuleType notificationRuleType;
  private final String displayName;

  private String value;

  @JsonValue
  public String getValue() {
    return value;
  }

  @Override
  public String toString() {
    return this.value;
  }

  NotificationRuleConditionType(String value, NotificationRuleType notificationRuleType, String displayName) {
    this.value = value;
    this.notificationRuleType = notificationRuleType;
    this.displayName = displayName;
  }

  public NotificationRuleType getNotificationRuleType() {
    return this.notificationRuleType;
  }

  public String getDisplayName() {
    return this.displayName;
  }
}
