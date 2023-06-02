/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.notification.services.impl;

import static io.harness.cvng.notification.utils.NotificationRuleConstants.REMAINING_PERCENTAGE;

import io.harness.cvng.notification.entities.SLONotificationRule.SLOErrorBudgetRemainingPercentageCondition;

import java.util.Map;

public class RemainingPercentageTemplateDataGenerator
    extends SLOTemplateDataGenerator<SLOErrorBudgetRemainingPercentageCondition> {
  @Override
  public String getHeaderMessage(Map<String, String> notificationDataMap) {
    return "error budget remains less than " + notificationDataMap.get(REMAINING_PERCENTAGE) + "%";
  }

  @Override
  public String getTriggerMessage(SLOErrorBudgetRemainingPercentageCondition condition) {
    return "When Error Budget remaining percentage drops below " + condition.getThreshold() + "%";
  }
}
