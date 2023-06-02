/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.notification.services.impl;

import static io.harness.cvng.notification.utils.NotificationRuleCommonUtils.getDurationAsStringWithoutSuffix;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.BURN_RATE;

import io.harness.cvng.notification.entities.SLONotificationRule.SLOErrorBudgetBurnRateCondition;

import java.util.Map;

public class BurnRateTemplateDataGenerator extends SLOTemplateDataGenerator<SLOErrorBudgetBurnRateCondition> {
  @Override
  public String getHeaderMessage(Map<String, String> notificationDataMap) {
    return "current burn rate is " + notificationDataMap.get(BURN_RATE) + "%";
  }

  @Override
  public String getTriggerMessage(SLOErrorBudgetBurnRateCondition condition) {
    String durationAsString = getDurationAsStringWithoutSuffix(condition.getLookBackDuration());
    return "When Error Budget burn rate goes above " + condition.getThreshold() + "% in the last " + durationAsString
        + " minutes";
  }
}
