/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.cvng.notification.services.impl;

import static io.harness.cvng.notification.utils.NotificationRuleConstants.CET_MODULE_NAME;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.MONITORED_SERVICE_NAME;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.MONITORED_SERVICE_URL;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.NO_METRIC_ASSIGNED_TO_MONITORED_SERVICE;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.URL;

import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.notification.beans.NotificationRuleType;
import io.harness.cvng.notification.channelDetails.CVNGNotificationChannelType;
import io.harness.cvng.notification.entities.MonitoredServiceNotificationRule.MonitoredServiceCodeErrorCondition;
import io.harness.cvng.notification.services.api.NotificationRuleTemplateDataGenerator;

import java.util.Map;

public class ErrorTrackingTemplateDataGenerator
    extends MonitoredServiceTemplateDataGenerator<MonitoredServiceCodeErrorCondition> {
  public static final String ET_MONITORED_SERVICE_URL_FORMAT =
      "%s/account/%s/%s/orgs/%s/projects/%s/etmonitoredservices/edit/%s";
  // Variables from the Monitored Service condition that triggered the notification
  public static final String ENVIRONMENT_NAME = "ENVIRONMENT_NAME";

  // Generic template variables not set in the parent
  public static final String EVENT_STATUS = "EVENT_STATUS";
  public static final String NOTIFICATION_EVENT_TRIGGER_LIST = "NOTIFICATION_EVENT_TRIGGER_LIST";

  // Email template variables
  public static final String EMAIL_MONITORED_SERVICE_NAME_HYPERLINK = "EMAIL_MONITORED_SERVICE_NAME_HYPERLINK";
  public static final String EMAIL_FORMATTED_VERSION_LIST = "EMAIL_FORMATTED_VERSION_LIST";
  public static final String EMAIL_NOTIFICATION_NAME_HYPERLINK = "EMAIL_NOTIFICATION_NAME_HYPERLINK";
  public static final String EMAIL_SAVED_SEARCH_FILTER_NAME_HYPERLINK = "EMAIL_SAVED_SEARCH_FILTER_NAME_HYPERLINK";
  public static final String EMAIL_SAVED_SEARCH_FILTER_SECTION = "EMAIL_SAVED_SEARCH_FILTER_SECTION";
  public static final String EMAIL_SAVED_SEARCH_FILTER_SECTION_VALUE =
      "<div style=\"margin-bottom: 8.5px\">\n<span style=\"color: #6b6d85\">Saved Search Filter </span>\n<span>${SAVED_SEARCH_FILTER_NAME}</span>\n</div>";

  // Slack template variables
  public static final String SLACK_FORMATTED_VERSION_LIST = "SLACK_FORMATTED_VERSION_LIST";
  public static final String NOTIFICATION_URL = "NOTIFICATION_URL";
  public static final String NOTIFICATION_NAME = "NOTIFICATION_NAME";

  public static final String SAVED_SEARCH_FILTER_URL = "SAVED_SEARCH_FILTER_URL";
  public static final String SAVED_SEARCH_FILTER_NAME = "SAVED_SEARCH_FILTER_NAME";
  public static final String SLACK_SAVED_SEARCH_FILTER_SECTION = "SLACK_SAVED_SEARCH_FILTER_SECTION";
  public static final String SLACK_SAVED_SEARCH_FILTER_SECTION_VALUE =
      "{\"type\": \"section\",\"text\": {\"type\": \"mrkdwn\",\"text\": \"Saved Search Filter ${SAVED_SEARCH_FILTER_NAME}\"}},";
  public static final String SLACK_EVENT_DETAILS_BUTTON = "SLACK_EVENT_DETAILS_BUTTON";
  public static final String SLACK_EVENT_DETAILS_BUTTON_BLOCK_VALUE =
      "{\"type\": \"actions\",\"elements\": [{\"type\": \"button\",\"text\": {\"type\": \"plain_text\",\"text\": \"View Event Details\",\"emoji\": true},\"url\": \"${ARC_SCREEN_URL}\"}]}";
  public static final String EMAIL_EVENT_DETAILS_BUTTON = "EMAIL_EVENT_DETAILS_BUTTON";
  public static final String EMAIL_EVENT_DETAILS_BUTTON_VALUE =
      "<a style=\"float:left;font-size: 13px;font-weight: bold;line-height: 16px;text-decoration: none;background-color: #EEEEEE;color: #333333;background-color: white;border: solid;border-width: 1px;border-radius: 3px;border-color: #BABABA;padding: 8px;padding-left: 16px;padding-right: 16px;\" href=\"${ARC_SCREEN_URL}\" class=\"button\">View Event Details</a>";
  public static final String EMAIL_LINK_BEGIN = "<a style=\"text-decoration: none; color: #0278D5;\" href=\"";
  public static final String EMAIL_LINK_MIDDLE = "\">";
  public static final String EMAIL_LINK_END = "</a>";
  public static final String EMAIL_HORIZONTAL_LINE_DIV =
      "<div style=\"display: inline; border-right: 1px solid #b0b1c3; height: 20px; margin: 0px 16px 0px 16px\"></div>";

  @Override
  public Map<String, String> getTemplateData(ProjectParams projectParams, Map<String, Object> entityDetails,
      MonitoredServiceCodeErrorCondition condition, Map<String, String> notificationDataMap) {
    final Map<String, String> templateData =
        super.getTemplateData(projectParams, entityDetails, condition, notificationDataMap);

    templateData.put(ENVIRONMENT_NAME, replaceNullWithBlank(notificationDataMap.get(ENVIRONMENT_NAME)));

    // Slack variables
    templateData.put(EVENT_STATUS, replaceNullWithBlank(notificationDataMap.get(EVENT_STATUS)));
    templateData.put(NOTIFICATION_EVENT_TRIGGER_LIST,
        replaceNullWithBlank(notificationDataMap.get(NOTIFICATION_EVENT_TRIGGER_LIST)));
    templateData.put(MONITORED_SERVICE_URL, replaceNullWithBlank(templateData.get(URL)));
    templateData.put(
        SLACK_FORMATTED_VERSION_LIST, replaceNullWithBlank(notificationDataMap.get(SLACK_FORMATTED_VERSION_LIST)));
    templateData.put(NOTIFICATION_URL, replaceNullWithBlank(notificationDataMap.get(NOTIFICATION_URL)));
    templateData.put(NOTIFICATION_NAME, replaceNullWithBlank(notificationDataMap.get(NOTIFICATION_NAME)));
    templateData.put(SLACK_SAVED_SEARCH_FILTER_SECTION,
        replaceNullWithBlank(notificationDataMap.get(SLACK_SAVED_SEARCH_FILTER_SECTION)));
    templateData.put(
        SLACK_EVENT_DETAILS_BUTTON, replaceNullWithBlank(notificationDataMap.get(SLACK_EVENT_DETAILS_BUTTON)));

    // Email variables
    String emailMonitoredServiceLink = EMAIL_LINK_BEGIN + templateData.get(MONITORED_SERVICE_URL) + EMAIL_LINK_MIDDLE
        + templateData.get(MONITORED_SERVICE_NAME) + EMAIL_LINK_END;
    templateData.put(EMAIL_MONITORED_SERVICE_NAME_HYPERLINK, emailMonitoredServiceLink);
    String emailNotificationLink = EMAIL_LINK_BEGIN + templateData.get(NOTIFICATION_URL) + EMAIL_LINK_MIDDLE
        + templateData.get(NOTIFICATION_NAME) + EMAIL_LINK_END;
    templateData.put(EMAIL_NOTIFICATION_NAME_HYPERLINK, emailNotificationLink);
    String emailSavedSearchFilterLink = EMAIL_LINK_BEGIN + templateData.get(SAVED_SEARCH_FILTER_URL) + EMAIL_LINK_MIDDLE
        + templateData.get(SAVED_SEARCH_FILTER_NAME) + EMAIL_LINK_END;
    templateData.put(EMAIL_SAVED_SEARCH_FILTER_NAME_HYPERLINK, emailSavedSearchFilterLink);
    templateData.put(
        EMAIL_FORMATTED_VERSION_LIST, replaceNullWithBlank(notificationDataMap.get(EMAIL_FORMATTED_VERSION_LIST)));
    templateData.put(EMAIL_SAVED_SEARCH_FILTER_SECTION,
        replaceNullWithBlank(notificationDataMap.get(EMAIL_SAVED_SEARCH_FILTER_SECTION)));
    templateData.put(
        EMAIL_EVENT_DETAILS_BUTTON, replaceNullWithBlank(notificationDataMap.get(EMAIL_EVENT_DETAILS_BUTTON)));

    return templateData;
  }

  public String getBaseLinkUrl(String accountIdentifier) {
    return NotificationRuleTemplateDataGenerator.getBaseUrl(this.getPortalUrl(), this.getVanityUrl(accountIdentifier));
  }

  @Override
  public String getUrl(String baseUrl, ProjectParams projectParams, String identifier, Long endTime) {
    return String.format(ET_MONITORED_SERVICE_URL_FORMAT, baseUrl, projectParams.getAccountIdentifier(),
        CET_MODULE_NAME, projectParams.getOrgIdentifier(), projectParams.getProjectIdentifier(), identifier);
  }

  @Override
  public String getTemplateId(
      NotificationRuleType notificationRuleType, CVNGNotificationChannelType notificationChannelType) {
    return String.format("cvng_%s_et_%s", notificationRuleType.getTemplateSuffixIdentifier().toLowerCase(),
        notificationChannelType.getTemplateSuffixIdentifier().toLowerCase());
  }

  @Override
  protected String getHeaderMessage(Map<String, String> notificationDataMap) {
    return "No Header Message";
  }

  @Override
  protected String getTriggerMessage(MonitoredServiceCodeErrorCondition condition) {
    return "No Trigger Message";
  }

  @Override
  protected String getAnomalousMetrics(
      ProjectParams projectParams, String identifier, long startTime, MonitoredServiceCodeErrorCondition condition) {
    return NO_METRIC_ASSIGNED_TO_MONITORED_SERVICE;
  }

  @Override
  public String getEntityName() {
    return MONITORED_SERVICE_NAME;
  }
  private String replaceNullWithBlank(String value) {
    if (value == null) {
      return "";
    }
    return value;
  }
}