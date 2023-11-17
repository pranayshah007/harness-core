/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.cvng.notification.utils.errortracking.interfaces;

import static io.harness.cvng.notification.utils.errortracking.AggregatedEvent.getAggregatedEvents;

import io.harness.cvng.beans.errortracking.ErrorTrackingNotificationData;
import io.harness.cvng.notification.entities.MonitoredServiceNotificationRule;
import io.harness.cvng.notification.utils.errortracking.AggregatedEvent;

import java.util.List;
import java.util.stream.Collectors;

public interface SlackNotification {
  String SLACK_FORMATTED_VERSION_LIST = "SLACK_FORMATTED_VERSION_LIST";
  String NOTIFICATION_URL = "NOTIFICATION_URL";
  String NOTIFICATION_NAME = "NOTIFICATION_NAME";
  String SLACK_EVENT_DETAILS_BUTTON = "SLACK_EVENT_DETAILS_BUTTON";

  static String getSlackFormattedVersionList(
      MonitoredServiceNotificationRule.MonitoredServiceCodeErrorCondition codeErrorCondition,
      ErrorTrackingNotificationData errorTrackingNotificationData, String baseLinkUrl) {
    final List<AggregatedEvent> aggregatedEvents =
        getAggregatedEvents(codeErrorCondition, errorTrackingNotificationData, baseLinkUrl);
    return aggregatedEvents.stream().map(AggregatedEvent::toSlackString).collect(Collectors.joining("\n"));
  }
}
