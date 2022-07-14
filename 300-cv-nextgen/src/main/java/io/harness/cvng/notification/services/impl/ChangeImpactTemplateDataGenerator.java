/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.notification.services.impl;

import static io.harness.cvng.notification.utils.NotificationRuleCommonUtils.getDurationAsStringWithoutSuffix;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.CURRENT_HEALTH_SCORE;

import io.harness.cvng.notification.beans.MonitoredServiceChangeEventType;
import io.harness.cvng.notification.entities.MonitoredServiceNotificationRule.MonitoredServiceChangeImpactCondition;

import java.util.Map;
import java.util.stream.Collectors;

public class ChangeImpactTemplateDataGenerator
    extends MonitoredServiceTemplateDataGenerator<MonitoredServiceChangeImpactCondition> {
  @Override
  public String getHeaderMessage(Map<String, String> notificationDataMap) {
    return "health score drops below " + notificationDataMap.get(CURRENT_HEALTH_SCORE) + " for";
  }

  @Override
  public String getTriggerMessage(MonitoredServiceChangeImpactCondition condition) {
    String changeEventTypeString = condition.getChangeEventTypes()
                                       .stream()
                                       .map(MonitoredServiceChangeEventType::getDisplayName)
                                       .collect(Collectors.joining(", "));
    String durationAsString = getDurationAsStringWithoutSuffix(condition.getPeriod());
    return "When service health score drops below " + condition.getThreshold().intValue() + " for longer than "
        + durationAsString + " minutes due to a change in " + changeEventTypeString;
  }
}
