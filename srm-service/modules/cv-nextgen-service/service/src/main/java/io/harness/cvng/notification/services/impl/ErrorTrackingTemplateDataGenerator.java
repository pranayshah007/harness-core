/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.cvng.notification.services.impl;

import static io.harness.cvng.notification.utils.NotificationRuleConstants.ERROR_TRACKING_TYPE;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.NO_METRIC_ASSIGNED_TO_MONITORED_SERVICE;

import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.notification.beans.ErrorTrackingEventStatus;
import io.harness.cvng.notification.beans.ErrorTrackingEventType;
import io.harness.cvng.notification.entities.MonitoredServiceNotificationRule.MonitoredServiceCodeErrorCondition;

import java.util.Map;
import java.util.stream.Collectors;

public class ErrorTrackingTemplateDataGenerator
    extends MonitoredServiceTemplateDataGenerator<MonitoredServiceCodeErrorCondition> {
  @Override
  protected String getHeaderMessage(Map<String, String> notificationDataMap) {
    return "observed new events for the following event summaries: " + notificationDataMap.get(ERROR_TRACKING_TYPE)
        + " for";
  }

  @Override
  protected String getTriggerMessage(MonitoredServiceCodeErrorCondition condition) {
    String changeEventStatusString = condition.getErrorTrackingEventStatus()
                                         .stream()
                                         .map(ErrorTrackingEventStatus::getDisplayName)
                                         .collect(Collectors.joining(", "));

    String changeEventTypeString = condition.getErrorTrackingEventTypes()
                                       .stream()
                                       .map(ErrorTrackingEventType::getDisplayName)
                                       .collect(Collectors.joining(", "));
    return "When a change observed for " + changeEventStatusString + " in a " + changeEventTypeString;
  }

  @Override
  protected String getAnomalousMetrics(
      ProjectParams projectParams, String identifier, long startTime, MonitoredServiceCodeErrorCondition condition) {
    return NO_METRIC_ASSIGNED_TO_MONITORED_SERVICE;
  }
}