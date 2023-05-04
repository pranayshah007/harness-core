/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.notification.services.impl;

import static io.harness.cvng.notification.utils.NotificationRuleConstants.CHANGE_EVENT_TYPE;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.NO_METRIC_ASSIGNED_TO_MONITORED_SERVICE;

import io.harness.cvng.beans.change.ChangeCategory;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.notification.entities.MonitoredServiceNotificationRule.MonitoredServiceChangeObservedCondition;

import java.util.Map;
import java.util.stream.Collectors;

public class ChangeObservedTemplateDataGenerator
    extends MonitoredServiceTemplateDataGenerator<MonitoredServiceChangeObservedCondition> {
  @Override
  public String getHeaderMessage(Map<String, String> notificationDataMap) {
    return "observed a change in a " + notificationDataMap.get(CHANGE_EVENT_TYPE) + " for";
  }

  @Override
  public String getTriggerMessage(MonitoredServiceChangeObservedCondition condition) {
    String changeEventTypeString =
        condition.getChangeCategories().stream().map(ChangeCategory::getDisplayName).collect(Collectors.joining(", "));
    return "When a change observed in a " + changeEventTypeString;
  }

  @Override
  protected String getAnomalousMetrics(ProjectParams projectParams, String identifier, long startTime,
      MonitoredServiceChangeObservedCondition condition) {
    return NO_METRIC_ASSIGNED_TO_MONITORED_SERVICE;
  }
}
