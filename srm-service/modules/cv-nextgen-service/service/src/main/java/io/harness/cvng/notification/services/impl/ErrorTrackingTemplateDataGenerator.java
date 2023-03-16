/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.cvng.notification.services.impl;

import static io.harness.cvng.notification.utils.NotificationRuleConstants.MONITORED_SERVICE_NAME;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.NO_METRIC_ASSIGNED_TO_MONITORED_SERVICE;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.URL;

import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.notification.beans.ErrorTrackingEventStatus;
import io.harness.cvng.notification.beans.ErrorTrackingEventType;
import io.harness.cvng.notification.beans.NotificationRuleType;
import io.harness.cvng.notification.channelDetails.CVNGNotificationChannelType;
import io.harness.cvng.notification.entities.MonitoredServiceNotificationRule.MonitoredServiceCodeErrorCondition;
import io.harness.cvng.notification.services.api.NotificationRuleTemplateDataGenerator;

import java.util.Map;
import java.util.stream.Collectors;

public class ErrorTrackingTemplateDataGenerator
    extends MonitoredServiceTemplateDataGenerator<MonitoredServiceCodeErrorCondition> {
  // Variables from the Monitored Service condition that triggered the notification
  public static final String ENVIRONMENT_NAME = "ENVIRONMENT_NAME";

  // Generic template variables not set in the parent
  public static final String EVENT_STATUS = "EVENT_STATUS";
  public static final String NOTIFICATION_EVENT_TRIGGER_LIST = "NOTIFICATION_EVENT_TRIGGER_LIST";

  // Email template variables
  public static final String EMAIL_MONITORED_SERVICE_NAME_HYPERLINK = "EMAIL_MONITORED_SERVICE_NAME_HYPERLINK";
  public static final String EMAIL_FORMATTED_VERSION_LIST = "EMAIL_FORMATTED_VERSION_LIST";
  public static final String EMAIL_NOTIFICATION_NAME_HYPERLINK = "EMAIL_NOTIFICATION_NAME_HYPERLINK";

  // Slack template variables
  public static final String MONITORED_SERVICE_URL = "MONITORED_SERVICE_URL";
  public static final String SLACK_FORMATTED_VERSION_LIST = "SLACK_FORMATTED_VERSION_LIST";
  public static final String NOTIFICATION_URL = "NOTIFICATION_URL";
  public static final String NOTIFICATION_NAME = "NOTIFICATION_NAME";

  public static final String EMAIL_LINK_BEGIN = "<a style=\"text-decoration: none; color: #0278D5;\" href=\"";
  public static final String EMAIL_LINK_MIDDLE = "\">";
  public static final String EMAIL_LINK_END = "</a>";
  public static final String EMAIL_HORIZONTAL_LINE_DIV =
      "<div style=\"display: inline; border-right: 1px solid #b0b1c3; height: 20px; margin: 0px 16px 0px 16px\"></div>";

  @Override
  public Map<String, String> getTemplateData(ProjectParams projectParams, String name, String identifier,
      String serviceIdentifier, String monitoredServiceIdentifier, MonitoredServiceCodeErrorCondition condition,
      Map<String, String> notificationDataMap) {
    final Map<String, String> templateData = super.getTemplateData(
        projectParams, name, identifier, serviceIdentifier, monitoredServiceIdentifier, condition, notificationDataMap);

    templateData.putAll(getConditionTemplateVariables(condition));

    templateData.put(ENVIRONMENT_NAME, notificationDataMap.get(ENVIRONMENT_NAME));

    // Slack variables
    templateData.put(MONITORED_SERVICE_URL, templateData.get(URL));
    templateData.put(SLACK_FORMATTED_VERSION_LIST, notificationDataMap.get(SLACK_FORMATTED_VERSION_LIST));
    templateData.put(NOTIFICATION_URL, notificationDataMap.get(NOTIFICATION_URL));
    templateData.put(NOTIFICATION_NAME, notificationDataMap.get(NOTIFICATION_NAME));

    // Email variables
    String emailMonitoredServiceLink = EMAIL_LINK_BEGIN + templateData.get(MONITORED_SERVICE_URL) + EMAIL_LINK_MIDDLE
        + templateData.get(MONITORED_SERVICE_NAME) + EMAIL_LINK_END;
    templateData.put(EMAIL_MONITORED_SERVICE_NAME_HYPERLINK, emailMonitoredServiceLink);
    String emailNotificationLink = EMAIL_LINK_BEGIN + templateData.get(NOTIFICATION_URL) + EMAIL_LINK_MIDDLE
        + templateData.get(NOTIFICATION_NAME) + EMAIL_LINK_END;
    templateData.put(EMAIL_NOTIFICATION_NAME_HYPERLINK, emailNotificationLink);
    templateData.put(EMAIL_FORMATTED_VERSION_LIST, notificationDataMap.get(EMAIL_FORMATTED_VERSION_LIST));

    return templateData;
  }

  public String getBaseLinkUrl(String accountIdentifier) {
    return NotificationRuleTemplateDataGenerator.getBaseUrl(this.getPortalUrl(), this.getVanityUrl(accountIdentifier));
  }

  @Override
  public String getTemplateId(
      NotificationRuleType notificationRuleType, CVNGNotificationChannelType notificationChannelType) {
    return String.format("cvng_%s_et_%s", notificationRuleType.getTemplateSuffixIdentifier().toLowerCase(),
        notificationChannelType.getTemplateSuffixIdentifier().toLowerCase());
  }

  /**
   * Sets variables from the Monitored Service condition that triggered the notification
   */
  private Map<String, String> getConditionTemplateVariables(MonitoredServiceCodeErrorCondition condition) {
    // Event status example is "New Events" or "Critical Events"
    String changeEventStatusString = condition.getErrorTrackingEventStatus()
                                         .stream()
                                         .map(ErrorTrackingEventStatus::getDisplayName)
                                         .collect(Collectors.joining(", "));

    // Event type example is "Exception", "Log Errors", etc
    String changeEventTypeString = condition.getErrorTrackingEventTypes()
                                       .stream()
                                       .map(ErrorTrackingEventType::getDisplayName)
                                       .collect(Collectors.joining(", "));

    return Map.of(EVENT_STATUS, changeEventStatusString, NOTIFICATION_EVENT_TRIGGER_LIST, changeEventTypeString);
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
}