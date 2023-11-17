/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.cvng.notification.services.impl;

import static io.harness.cvng.notification.utils.NotificationRuleConstants.CET_MODULE_NAME;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.MONITORED_SERVICE_NAME;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.NO_METRIC_ASSIGNED_TO_MONITORED_SERVICE;
import static io.harness.cvng.notification.utils.errortracking.interfaces.ErrorTrackingNotification.ET_MONITORED_SERVICE_URL_FORMAT;

import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.notification.beans.NotificationRuleType;
import io.harness.cvng.notification.channelDetails.CVNGNotificationChannelType;
import io.harness.cvng.notification.entities.MonitoredServiceNotificationRule.MonitoredServiceCodeErrorCondition;
import io.harness.cvng.notification.services.api.NotificationRuleTemplateDataGenerator;

import java.util.Map;

public class ErrorTrackingTemplateDataGenerator
    extends MonitoredServiceTemplateDataGenerator<MonitoredServiceCodeErrorCondition> {
  @Override
  public Map<String, String> getTemplateData(ProjectParams projectParams, Map<String, Object> entityDetails,
      MonitoredServiceCodeErrorCondition condition, Map<String, String> notificationDataMap) {
    final Map<String, String> templateData =
        super.getTemplateData(projectParams, entityDetails, condition, notificationDataMap);

    templateData.putAll(notificationDataMap);

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
}