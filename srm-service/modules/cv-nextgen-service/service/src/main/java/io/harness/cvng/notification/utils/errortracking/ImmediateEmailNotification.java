/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.cvng.notification.utils.errortracking;

import static io.harness.cvng.notification.utils.errortracking.SavedFilterEmailNotification.EMAIL_SAVED_SEARCH_FILTER_SECTION;
import static io.harness.cvng.notification.utils.errortracking.interfaces.EmailNotification.EMAIL_EVENT_DETAILS_BUTTON;
import static io.harness.cvng.notification.utils.errortracking.interfaces.EmailNotification.EMAIL_FORMATTED_VERSION_LIST;
import static io.harness.cvng.notification.utils.errortracking.interfaces.EmailNotification.EMAIL_MONITORED_SERVICE_NAME_HYPERLINK;
import static io.harness.cvng.notification.utils.errortracking.interfaces.EmailNotification.EMAIL_NOTIFICATION_NAME_HYPERLINK;
import static io.harness.cvng.notification.utils.errortracking.interfaces.EmailNotification.getEmailMonitoredServiceNameHyperlink;
import static io.harness.cvng.notification.utils.errortracking.interfaces.EmailNotification.getEmailNotificationLink;
import static io.harness.cvng.notification.utils.errortracking.interfaces.ErrorTrackingNotification.ENVIRONMENT_NAME;
import static io.harness.cvng.notification.utils.errortracking.interfaces.ErrorTrackingNotification.EVENT_STATUS;
import static io.harness.cvng.notification.utils.errortracking.interfaces.ErrorTrackingNotification.NOTIFICATION_EVENT_TRIGGER_LIST;
import static io.harness.cvng.notification.utils.errortracking.interfaces.ErrorTrackingNotification.validateTemplateValues;

import io.harness.cvng.beans.errortracking.ErrorTrackingHitSummary;
import io.harness.cvng.core.entities.MonitoredService;
import io.harness.cvng.notification.entities.NotificationRule;
import io.harness.cvng.notification.utils.errortracking.interfaces.ImmediateNotification;

import java.util.HashMap;
import java.util.Map;

public class ImmediateEmailNotification {
  public static final String EMAIL_EVENT_DETAILS_BUTTON_VALUE =
      "<a style=\"float:left;font-size: 13px;font-weight: bold;line-height: 16px;text-decoration: none;background-color: #EEEEEE;color: #333333;background-color: white;border: solid;border-width: 1px;border-radius: 3px;border-color: #BABABA;padding: 8px;padding-left: 16px;padding-right: 16px;\" href=\"${ARC_SCREEN_URL}\" class=\"button\">View Event Details</a>";

  public static Map<String, String> getNotificationDataMap(ErrorTrackingHitSummary errorTrackingHitSummary,
      String baseLinkUrl, MonitoredService monitoredService, NotificationRule notificationRule, String environmentId) {
    final Map<String, String> notificationDataMap = new HashMap<>();

    notificationDataMap.put(ENVIRONMENT_NAME, environmentId);
    notificationDataMap.put(EVENT_STATUS, ImmediateNotification.getEventStatus());
    notificationDataMap.put(NOTIFICATION_EVENT_TRIGGER_LIST, ImmediateNotification.getNotificationEventTriggerList());
    notificationDataMap.put(
        EMAIL_MONITORED_SERVICE_NAME_HYPERLINK, getEmailMonitoredServiceNameHyperlink(baseLinkUrl, monitoredService));
    notificationDataMap.put(
        EMAIL_NOTIFICATION_NAME_HYPERLINK, getEmailNotificationLink(baseLinkUrl, monitoredService, notificationRule));
    notificationDataMap.put(EMAIL_SAVED_SEARCH_FILTER_SECTION, "");
    notificationDataMap.put(EMAIL_FORMATTED_VERSION_LIST, getEmailFormattedVersionList(errorTrackingHitSummary));
    notificationDataMap.put(EMAIL_EVENT_DETAILS_BUTTON,
        getEmailEventDetailsButton(errorTrackingHitSummary, monitoredService, environmentId, baseLinkUrl));

    validateTemplateValues(notificationDataMap, EMAIL_SAVED_SEARCH_FILTER_SECTION);

    return notificationDataMap;
  }

  static String getEmailEventDetailsButton(ErrorTrackingHitSummary errorTrackingHitSummary,
      MonitoredService monitoredService, String environmentId, String baseLinkUrl) {
    final String arcScreenUrl = ImmediateNotification.buildArcScreenUrlWithParameters(errorTrackingHitSummary,
        baseLinkUrl, monitoredService.getAccountId(), monitoredService.getOrgIdentifier(),
        monitoredService.getProjectIdentifier(), errorTrackingHitSummary.getRequestId(), environmentId,
        monitoredService.getIdentifier(), errorTrackingHitSummary.getVersionId());

    return EMAIL_EVENT_DETAILS_BUTTON_VALUE.replace("${ARC_SCREEN_URL}", arcScreenUrl);
  }

  private static String getEmailFormattedVersionList(ErrorTrackingHitSummary errorTrackingHitSummary) {
    StackTraceEvent stackTraceEvent = StackTraceEvent.builder()
                                          .version(errorTrackingHitSummary.getVersionId())
                                          .stackTrace(String.join(",", errorTrackingHitSummary.getStackTrace()))
                                          .build();
    return stackTraceEvent.toEmailString();
  }
}