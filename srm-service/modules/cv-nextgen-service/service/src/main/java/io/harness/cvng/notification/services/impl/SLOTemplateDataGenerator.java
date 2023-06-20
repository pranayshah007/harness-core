/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.notification.services.impl;

import static io.harness.cvng.notification.utils.NotificationRuleConstants.MODULE_NAME;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.MONITORED_SERVICE_IDENTIFIER;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.MONITORED_SERVICE_URL;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.MONITORED_SERVICE_URL_FORMAT;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.PROJECT_SIMPLE_SLO_URL_FORMAT;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.SLO_NAME;

import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.notification.entities.SLONotificationRule.SLONotificationRuleCondition;
import io.harness.cvng.notification.services.api.NotificationRuleTemplateDataGenerator;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveType;
import io.harness.cvng.servicelevelobjective.entities.AbstractServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelObjectiveV2Service;

import com.google.inject.Inject;
import java.time.Instant;
import java.util.Map;

public abstract class SLOTemplateDataGenerator<T extends SLONotificationRuleCondition>
    extends NotificationRuleTemplateDataGenerator<T> {
  @Inject private ServiceLevelObjectiveV2Service serviceLevelObjectiveV2Service;

  private static final String PROJECT_COMPOSITE_SLO_URL_FORMAT =
      "%s/account/%s/%s/orgs/%s/projects/%s/slos/%s?tab=Details&sloType=Composite&notificationTime=%s";

  private static final String ACCOUNT_COMPOSITE_SLO_URL_FORMAT =
      "%s/account/%s/%s/slos/%s?tab=Details&sloType=Composite&notificationTime=%s";
  @Override
  public String getEntityName() {
    return SLO_NAME;
  }

  @Override
  public String getUrl(String baseUrl, ProjectParams projectParams, String identifier, Long endTime) {
    AbstractServiceLevelObjective serviceLevelObjective =
        serviceLevelObjectiveV2Service.getEntity(projectParams, identifier);
    if (serviceLevelObjective.getType() == ServiceLevelObjectiveType.COMPOSITE) {
      if (projectParams.getProjectIdentifier() == null) {
        return String.format(ACCOUNT_COMPOSITE_SLO_URL_FORMAT, baseUrl, projectParams.getAccountIdentifier(),
            MODULE_NAME, identifier, endTime);
      }
      return String.format(PROJECT_COMPOSITE_SLO_URL_FORMAT, baseUrl, projectParams.getAccountIdentifier(), MODULE_NAME,
          projectParams.getOrgIdentifier(), projectParams.getProjectIdentifier(), identifier, endTime);
    }
    return String.format(PROJECT_SIMPLE_SLO_URL_FORMAT, baseUrl, projectParams.getAccountIdentifier(), MODULE_NAME,
        projectParams.getOrgIdentifier(), projectParams.getProjectIdentifier(), identifier, endTime);
  }

  public String getMonitoredServiceUrl(String baseUrl, ProjectParams projectParams, String monitoredServiceIdentifier) {
    Instant currentInstant = this.clock.instant();
    Long endTime = currentInstant.toEpochMilli();
    return String.format(MONITORED_SERVICE_URL_FORMAT, baseUrl, projectParams.getAccountIdentifier(), MODULE_NAME,
        projectParams.getOrgIdentifier(), projectParams.getProjectIdentifier(), monitoredServiceIdentifier, endTime);
  }

  @Override
  public String getAnomalousMetrics(
      ProjectParams projectParams, String identifier, long startTime, SLONotificationRuleCondition condition) {
    return "";
  }

  @Override
  public Map<String, String> getTemplateData(ProjectParams projectParams, Map<String, Object> entityDetails,
      T condition, Map<String, String> notificationDataMap) {
    String vanityUrl = getVanityUrl(projectParams.getAccountIdentifier());
    String baseUrl = getBaseUrl(getPortalUrl(), vanityUrl);
    final Map<String, String> templateData =
        super.getTemplateData(projectParams, entityDetails, condition, notificationDataMap);
    templateData.put(MONITORED_SERVICE_URL,
        getMonitoredServiceUrl(
            baseUrl, projectParams, entityDetails.getOrDefault(MONITORED_SERVICE_IDENTIFIER, "").toString()));
    return templateData;
  }
}
