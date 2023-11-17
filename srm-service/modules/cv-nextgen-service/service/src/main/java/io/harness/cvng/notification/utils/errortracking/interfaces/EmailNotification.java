/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.cvng.notification.utils.errortracking.interfaces;

import static io.harness.cvng.notification.utils.errortracking.AggregatedEvent.getAggregatedEvents;
import static io.harness.cvng.notification.utils.errortracking.interfaces.ErrorTrackingNotification.getNotificationUrl;

import io.harness.cvng.beans.errortracking.ErrorTrackingNotificationData;
import io.harness.cvng.core.entities.MonitoredService;
import io.harness.cvng.notification.entities.MonitoredServiceNotificationRule;
import io.harness.cvng.notification.entities.NotificationRule;
import io.harness.cvng.notification.utils.errortracking.AggregatedEvent;

import java.util.List;
import java.util.stream.Collectors;

public interface EmailNotification {
  String EMAIL_LINK_BEGIN = "<a style=\"text-decoration: none; color: #0278D5;\" href=\"";
  String EMAIL_LINK_MIDDLE = "\">";
  String EMAIL_LINK_END = "</a>";
  String EMAIL_MONITORED_SERVICE_NAME_HYPERLINK = "EMAIL_MONITORED_SERVICE_NAME_HYPERLINK";
  String EMAIL_FORMATTED_VERSION_LIST = "EMAIL_FORMATTED_VERSION_LIST";
  String EMAIL_NOTIFICATION_NAME_HYPERLINK = "EMAIL_NOTIFICATION_NAME_HYPERLINK";
  String EMAIL_EVENT_DETAILS_BUTTON = "EMAIL_EVENT_DETAILS_BUTTON";

  static String getEmailFormattedVersionList(
      MonitoredServiceNotificationRule.MonitoredServiceCodeErrorCondition codeErrorCondition,
      ErrorTrackingNotificationData errorTrackingNotificationData, String baseLinkUrl) {
    final List<AggregatedEvent> aggregatedEvents =
        getAggregatedEvents(codeErrorCondition, errorTrackingNotificationData, baseLinkUrl);
    return aggregatedEvents.stream().map(AggregatedEvent::toEmailString).collect(Collectors.joining());
  }

  static String getEmailMonitoredServiceNameHyperlink(String baseUrl, MonitoredService monitoredService) {
    return EMAIL_LINK_BEGIN + getNotificationUrl(baseUrl, monitoredService) + EMAIL_LINK_MIDDLE
        + monitoredService.getIdentifier() + EMAIL_LINK_END;
  }

  static String getEmailNotificationLink(
      String baseUrl, MonitoredService monitoredService, NotificationRule notificationRule) {
    return EMAIL_LINK_BEGIN + getNotificationUrl(baseUrl, monitoredService) + EMAIL_LINK_MIDDLE
        + notificationRule.getName() + EMAIL_LINK_END;
  }
}
