/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.cvng.notification.utils.errortracking;

import static io.harness.cvng.notification.utils.errortracking.interfaces.EmailNotification.EMAIL_EVENT_DETAILS_BUTTON;
import static io.harness.cvng.notification.utils.errortracking.interfaces.EmailNotification.EMAIL_FORMATTED_VERSION_LIST;
import static io.harness.cvng.notification.utils.errortracking.interfaces.EmailNotification.EMAIL_MONITORED_SERVICE_NAME_HYPERLINK;
import static io.harness.cvng.notification.utils.errortracking.interfaces.EmailNotification.EMAIL_NOTIFICATION_NAME_HYPERLINK;
import static io.harness.cvng.notification.utils.errortracking.interfaces.EmailNotification.getEmailFormattedVersionList;
import static io.harness.cvng.notification.utils.errortracking.interfaces.EmailNotification.getEmailMonitoredServiceNameHyperlink;
import static io.harness.cvng.notification.utils.errortracking.interfaces.EmailNotification.getEmailNotificationLink;
import static io.harness.cvng.notification.utils.errortracking.interfaces.ErrorTrackingNotification.ENVIRONMENT_NAME;
import static io.harness.cvng.notification.utils.errortracking.interfaces.ErrorTrackingNotification.EVENT_STATUS;
import static io.harness.cvng.notification.utils.errortracking.interfaces.ErrorTrackingNotification.NOTIFICATION_EVENT_TRIGGER_LIST;
import static io.harness.cvng.notification.utils.errortracking.interfaces.ErrorTrackingNotification.validateTemplateValues;

import io.harness.cvng.beans.errortracking.ErrorTrackingNotificationData;
import io.harness.cvng.core.entities.MonitoredService;
import io.harness.cvng.notification.entities.MonitoredServiceNotificationRule;
import io.harness.cvng.notification.entities.NotificationRule;
import io.harness.cvng.notification.utils.errortracking.interfaces.SavedFilterNotification;

import java.util.HashMap;
import java.util.Map;

public class SavedFilterEmailNotification {
  public static final String EMAIL_SAVED_SEARCH_FILTER_SECTION = "EMAIL_SAVED_SEARCH_FILTER_SECTION";
  public static final String EMAIL_SAVED_SEARCH_FILTER_SECTION_VALUE =
      "<div style=\"margin-bottom: 8.5px\">\n<span style=\"color: #6b6d85\">Saved Search Filter </span>\n<span>${SAVED_SEARCH_FILTER_NAME}</span>\n</div>";

  public static Map<String, String> getNotificationDataMap(ErrorTrackingNotificationData errorTrackingNotificationData,
      MonitoredServiceNotificationRule.MonitoredServiceCodeErrorCondition codeErrorCondition, String baseLinkUrl,
      MonitoredService monitoredService, NotificationRule notificationRule, String environmentId) {
    final Map<String, String> notificationDataMap = new HashMap<>();

    notificationDataMap.put(ENVIRONMENT_NAME, environmentId);
    notificationDataMap.put(EVENT_STATUS, SavedFilterNotification.getEventStatus(errorTrackingNotificationData));
    notificationDataMap.put(NOTIFICATION_EVENT_TRIGGER_LIST,
        SavedFilterNotification.getNotificationEventTriggerList(errorTrackingNotificationData));
    notificationDataMap.put(
        EMAIL_MONITORED_SERVICE_NAME_HYPERLINK, getEmailMonitoredServiceNameHyperlink(baseLinkUrl, monitoredService));
    notificationDataMap.put(
        EMAIL_NOTIFICATION_NAME_HYPERLINK, getEmailNotificationLink(baseLinkUrl, monitoredService, notificationRule));
    notificationDataMap.put(
        EMAIL_SAVED_SEARCH_FILTER_SECTION, getEmailSavedSearchFilterSection(errorTrackingNotificationData));
    notificationDataMap.put(EMAIL_FORMATTED_VERSION_LIST,
        getEmailFormattedVersionList(codeErrorCondition, errorTrackingNotificationData, baseLinkUrl));
    notificationDataMap.put(EMAIL_EVENT_DETAILS_BUTTON, "");

    validateTemplateValues(notificationDataMap, EMAIL_EVENT_DETAILS_BUTTON);

    return notificationDataMap;
  }

  private static String getEmailSavedSearchFilterSection(ErrorTrackingNotificationData errorTrackingNotificationData) {
    return EMAIL_SAVED_SEARCH_FILTER_SECTION_VALUE.replace(
        "${SAVED_SEARCH_FILTER_NAME}", errorTrackingNotificationData.getFilter().getFilterName());
  }
}
