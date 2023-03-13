/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.notification.services.impl;

import static io.harness.cvng.notification.utils.NotificationRuleConstants.MODULE_NAME;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.SLO_NAME;

import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.notification.beans.NotificationRuleType;
import io.harness.cvng.notification.entities.SLONotificationRule.SLONotificationRuleCondition;
import io.harness.cvng.notification.services.api.NotificationRuleTemplateDataGenerator;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

public abstract class SLOTemplateDataGenerator<T extends SLONotificationRuleCondition>
    extends NotificationRuleTemplateDataGenerator<T> {
  public static final String MONITORED_SERVICE_URL = "MONITORED_SERVICE_URL";

  private static final String MONITORED_SERVICE_URL_FORMAT =
      "%s/account/%s/%s/orgs/%s/projects/%s/monitoringservices/edit/%s?tab=ServiceHealth&notificationTime=%s";

  private static final String SLO_URL_FORMAT =
      "%s/account/%s/%s/orgs/%s/projects/%s/slos/%s?tab=Details&sloType=Simple&notificationTime=%s";
  @Override
  public String getEntityName() {
    return SLO_NAME;
  }

  @Override
  public String getUrl(
      String baseUrl, ProjectParams projectParams, String identifier, NotificationRuleType type, Long endTime) {
    return String.format(SLO_URL_FORMAT, baseUrl, projectParams.getAccountIdentifier(), MODULE_NAME,
        projectParams.getOrgIdentifier(), projectParams.getProjectIdentifier(), identifier, endTime);
  }

  public String getMonitoredServiceUrl(String baseUrl, ProjectParams projectParams, String monitoredServiceIdentifier) {
    Instant currentInstant = this.clock.instant();
    Long endTime = currentInstant.plus(2, ChronoUnit.HOURS).toEpochMilli();
    return String.format(MONITORED_SERVICE_URL_FORMAT, baseUrl, projectParams.getAccountIdentifier(), MODULE_NAME,
        projectParams.getOrgIdentifier(), projectParams.getProjectIdentifier(), monitoredServiceIdentifier, endTime);
  }

  @Override
  public String getAnomalousMetrics(
      ProjectParams projectParams, String identifier, long startTime, SLONotificationRuleCondition condition) {
    return "";
  }

  @Override
  public Map<String, String> getTemplateData(ProjectParams projectParams, String name, String identifier,
      String serviceIdentifier, String monitoredServiceIdentifier, T condition,
      Map<String, String> notificationDataMap) {
    String vanityUrl = getVanityUrl(projectParams.getAccountIdentifier());
    String baseUrl = getBaseUrl(getPortalUrl(), vanityUrl);
    final Map<String, String> templateData = super.getTemplateData(
        projectParams, name, identifier, serviceIdentifier, monitoredServiceIdentifier, condition, notificationDataMap);
    templateData.put(MONITORED_SERVICE_URL, getMonitoredServiceUrl(baseUrl, projectParams, monitoredServiceIdentifier));
    return templateData;
  }
}
