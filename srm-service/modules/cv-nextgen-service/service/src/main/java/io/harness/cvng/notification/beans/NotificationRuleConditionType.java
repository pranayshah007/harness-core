/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.notification.beans;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum NotificationRuleConditionType {
  @JsonProperty("ErrorBudgetRemainingPercentage")
  ERROR_BUDGET_REMAINING_PERCENTAGE(NotificationRuleType.SLO, "Error Budget Remaining Percentage"),
  @JsonProperty("ErrorBudgetRemainingMinutes")
  ERROR_BUDGET_REMAINING_MINUTES(NotificationRuleType.SLO, "Error Budget Remaining Minutes"),
  @JsonProperty("ErrorBudgetBurnRate") ERROR_BUDGET_BURN_RATE(NotificationRuleType.SLO, "Error Budget Burn Rate"),
  @JsonProperty("ChangeImpact") CHANGE_IMPACT(NotificationRuleType.MONITORED_SERVICE, "Change Impact"),
  @JsonProperty("HealthScore") HEALTH_SCORE(NotificationRuleType.MONITORED_SERVICE, "Health Score"),
  @JsonProperty("ChangeObserved") CHANGE_OBSERVED(NotificationRuleType.MONITORED_SERVICE, "Change Observed"),
  @JsonProperty("CodeErrors") CODE_ERRORS(NotificationRuleType.MONITORED_SERVICE, "Code Errors"),
  @JsonProperty("FireHydrantReport") FIRE_HYDRANT_REPORT(NotificationRuleType.FIRE_HYDRANT, "Fire Hydrant Report"),

  @JsonProperty("DeploymentImpactReport")
  DEPLOYMENT_IMPACT_REPORT(NotificationRuleType.MONITORED_SERVICE, "Deployment Impact Report");

  private final NotificationRuleType notificationRuleType;
  private final String displayName;

  NotificationRuleConditionType(NotificationRuleType notificationRuleType, String displayName) {
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
