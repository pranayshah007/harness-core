/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.cvng.notification.utils.errortracking;

import static io.harness.cvng.notification.utils.NotificationRuleConstants.MONITORED_SERVICE_NAME;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.MONITORED_SERVICE_URL;
import static io.harness.cvng.notification.utils.errortracking.interfaces.ErrorTrackingNotification.ENVIRONMENT_NAME;
import static io.harness.cvng.notification.utils.errortracking.interfaces.ErrorTrackingNotification.EVENT_STATUS;
import static io.harness.cvng.notification.utils.errortracking.interfaces.ErrorTrackingNotification.NOTIFICATION_EVENT_TRIGGER_LIST;
import static io.harness.cvng.notification.utils.errortracking.interfaces.ErrorTrackingNotification.getNotificationUrl;
import static io.harness.cvng.notification.utils.errortracking.interfaces.ErrorTrackingNotification.validateTemplateValues;
import static io.harness.cvng.notification.utils.errortracking.interfaces.SlackNotification.NOTIFICATION_NAME;
import static io.harness.cvng.notification.utils.errortracking.interfaces.SlackNotification.NOTIFICATION_URL;
import static io.harness.cvng.notification.utils.errortracking.interfaces.SlackNotification.SLACK_EVENT_DETAILS_BUTTON;
import static io.harness.cvng.notification.utils.errortracking.interfaces.SlackNotification.SLACK_FORMATTED_VERSION_LIST;
import static io.harness.cvng.notification.utils.errortracking.interfaces.SlackNotification.getSlackFormattedVersionList;

import io.harness.cvng.beans.errortracking.ErrorTrackingNotificationData;
import io.harness.cvng.core.entities.MonitoredService;
import io.harness.cvng.notification.entities.MonitoredServiceNotificationRule;
import io.harness.cvng.notification.entities.NotificationRule;
import io.harness.cvng.notification.utils.errortracking.interfaces.ErrorTrackingNotification;
import io.harness.cvng.notification.utils.errortracking.interfaces.SavedFilterNotification;

import java.util.HashMap;
import java.util.Map;

public class SavedFilterSlackNotification {
  public static final String SLACK_SAVED_SEARCH_FILTER_SECTION = "SLACK_SAVED_SEARCH_FILTER_SECTION";
  public static final String SLACK_SAVED_SEARCH_FILTER_SECTION_VALUE =
      "{\"type\": \"section\",\"text\": {\"type\": \"mrkdwn\",\"text\": \"Saved Search Filter ${SAVED_SEARCH_FILTER_NAME}\"}},";

  public static Map<String, String> getNotificationDataMap(ErrorTrackingNotificationData errorTrackingNotificationData,
      MonitoredServiceNotificationRule.MonitoredServiceCodeErrorCondition codeErrorCondition, String baseLinkUrl,
      MonitoredService monitoredService, NotificationRule notificationRule, String environmentId) {
    final Map<String, String> notificationDataMap = new HashMap<>();

    notificationDataMap.put(ENVIRONMENT_NAME, environmentId);
    notificationDataMap.put(EVENT_STATUS, SavedFilterNotification.getEventStatus(errorTrackingNotificationData));
    notificationDataMap.put(NOTIFICATION_EVENT_TRIGGER_LIST,
        SavedFilterNotification.getNotificationEventTriggerList(errorTrackingNotificationData));
    notificationDataMap.put(MONITORED_SERVICE_URL, getNotificationUrl(baseLinkUrl, monitoredService));
    notificationDataMap.put(MONITORED_SERVICE_NAME, monitoredService.getIdentifier());
    notificationDataMap.put(
        NOTIFICATION_URL, ErrorTrackingNotification.getNotificationUrl(baseLinkUrl, monitoredService));
    notificationDataMap.put(NOTIFICATION_NAME, notificationRule.getName());
    notificationDataMap.put(
        SLACK_SAVED_SEARCH_FILTER_SECTION, getSlackSavedSearchFilterSection(errorTrackingNotificationData));
    notificationDataMap.put(SLACK_FORMATTED_VERSION_LIST,
        getSlackFormattedVersionList(codeErrorCondition, errorTrackingNotificationData, baseLinkUrl));
    notificationDataMap.put(SLACK_EVENT_DETAILS_BUTTON, "");

    validateTemplateValues(notificationDataMap, SLACK_EVENT_DETAILS_BUTTON);

    return notificationDataMap;
  }

  private static String getSlackSavedSearchFilterSection(ErrorTrackingNotificationData errorTrackingNotificationData) {
    return SLACK_SAVED_SEARCH_FILTER_SECTION_VALUE.replace(
        "${SAVED_SEARCH_FILTER_NAME}", errorTrackingNotificationData.getFilter().getFilterName());
  }
}
