/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.notification.services.impl;

import static io.harness.cvng.notification.utils.NotificationRuleConstants.CURRENT_HEALTH_SCORE;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.CURRENT_SLO_TARGET;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.ERROR_BUDGET_BURN_RATE;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.MODULE_NAME;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.MONITORED_SERVICE_NAME;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.MONITORED_SERVICE_URL_FORMAT;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.MS_HEALTH_REPORT;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.NO_METRIC_ASSIGNED_TO_MONITORED_SERVICE;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.NO_SLO_ASSOCIATED_WITH_MONITORED_SERVICE;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.PAST_SLO_TARGET;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.PROJECT_SIMPLE_SLO_URL_FORMAT;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.SLO_NAME;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.SLO_PERFORMANCE;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.SLO_PERFORMANCE_SECTION;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.SLO_TARGET;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.SLO_URL;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.TOTAL_CE_COUNT;

import io.harness.cvng.beans.MSHealthReport;
import io.harness.cvng.beans.change.ChangeSummaryDTO;
import io.harness.cvng.core.beans.monitoredService.RiskData;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.beans.params.ResourceParams;
import io.harness.cvng.notification.entities.FireHydrantReportNotificationCondition;
import io.harness.cvng.notification.services.api.NotificationRuleTemplateDataGenerator;
import io.harness.cvng.utils.ScopedInformation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FireHydrantTemplateDataGenerator
    extends NotificationRuleTemplateDataGenerator<FireHydrantReportNotificationCondition> {
  @Override
  public Map<String, String> getTemplateData(ProjectParams projectParams, Map<String, Object> entityDetails,
      FireHydrantReportNotificationCondition condition, Map<String, String> notificationDataMap) {
    final Map<String, String> templateDataMap =
        super.getTemplateData(projectParams, entityDetails, condition, notificationDataMap);
    MSHealthReport msHealthReport = (MSHealthReport) entityDetails.get(MS_HEALTH_REPORT);
    ChangeSummaryDTO changeSummary = msHealthReport.getChangeSummary();
    templateDataMap.put(TOTAL_CE_COUNT, String.valueOf(changeSummary.getTotal().getCount()));
    changeSummary.getCategoryCountMap().forEach((k, v) -> templateDataMap.put(k.name(), String.valueOf(v.getCount())));
    templateDataMap.put(CURRENT_HEALTH_SCORE, String.valueOf(msHealthReport.getCurrentHealthScore()));
    templateDataMap.put(SLO_PERFORMANCE, getSloPerformanceDetails(msHealthReport.getAssociatedSLOsDetails()));

    return templateDataMap;
  }

  @Override
  protected String getEntityName() {
    return MONITORED_SERVICE_NAME;
  }

  @Override
  protected String getUrl(String baseUrl, ProjectParams projectParams, String identifier, Long endTime) {
    return String.format(MONITORED_SERVICE_URL_FORMAT, baseUrl, projectParams.getAccountIdentifier(), MODULE_NAME,
        projectParams.getOrgIdentifier(), projectParams.getProjectIdentifier(), identifier, endTime);
  }

  @Override
  protected String getTriggerMessage(FireHydrantReportNotificationCondition condition) {
    return "No Trigger Message";
  }

  @Override
  protected String getAnomalousMetrics(ProjectParams projectParams, String identifier, long startTime,
      FireHydrantReportNotificationCondition condition) {
    return NO_METRIC_ASSIGNED_TO_MONITORED_SERVICE;
  }

  @Override
  protected String getHeaderMessage(Map<String, String> notificationDataMap) {
    return "No Header Message";
  }

  private String getSloPerformanceDetails(List<MSHealthReport.AssociatedSLOsDetails> associatedSLOsDetails) {
    StringBuilder sb = new StringBuilder();

    if (associatedSLOsDetails.size() == 0) {
      sb.append(NO_SLO_ASSOCIATED_WITH_MONITORED_SERVICE);
    } else {
      associatedSLOsDetails.forEach(sloDetails -> {
        ResourceParams resourceParams =
            ScopedInformation.getResourceParamsFromScopedIdentifier(sloDetails.getScopedMonitoredServiceIdentifier());
        Map<String, String> templateDataMap = new HashMap<>() {
          {
            put(SLO_URL, getAssociatedSLOUrl(resourceParams, sloDetails.getIdentifier()));
            put(SLO_NAME, sloDetails.getName());
            put(SLO_TARGET, sloDetails.getSloTarget().toString());
            put(PAST_SLO_TARGET, String.format("%.2f", sloDetails.getPastSLOPerformance()));
            put(CURRENT_SLO_TARGET, String.format("%.2f", sloDetails.getCurrentSLOPerformance()));
            put(ERROR_BUDGET_BURN_RATE, String.format("%.2f", sloDetails.getErrorBudgetBurnRate()));
          }
        };
        final String[] sloPerformanceSection = {SLO_PERFORMANCE_SECTION};
        templateDataMap.forEach((key, value) -> {
          String variable = String.format("${%s}", key);
          sloPerformanceSection[0] = sloPerformanceSection[0].replace(variable, value);
        });
        sb.append(sloPerformanceSection[0]);
      });
    }

    return sb.toString();
  }

  private String getAssociatedSLOUrl(ProjectParams projectParams, String identifier) {
    return String.format(PROJECT_SIMPLE_SLO_URL_FORMAT,
        NotificationRuleTemplateDataGenerator.getBaseUrl(
            this.getPortalUrl(), this.getVanityUrl(projectParams.getAccountIdentifier())),
        projectParams.getAccountIdentifier(), MODULE_NAME, projectParams.getOrgIdentifier(),
        projectParams.getProjectIdentifier(), identifier, clock.instant().toEpochMilli());
  }

  private String getServiceHealthMessage(RiskData currentHealthScore) {
    switch (currentHealthScore.getRiskStatus()) {
      case HEALTHY:
        return String.format(
            "The service health remained healthy with a score of %s%%", currentHealthScore.getHealthScore());
      case OBSERVE:
        return String.format(
            "The service health needs to be observed. It has a score of %s%%", currentHealthScore.getHealthScore());
      case NEED_ATTENTION:
        return String.format(
            "The service health needs attention. It has a score of %s%%", currentHealthScore.getHealthScore());
      case UNHEALTHY:
        return String.format(
            "The service health remained unhealthy with a score of %s%%", currentHealthScore.getHealthScore());
      case NO_DATA:
      case NO_ANALYSIS:
      default:
        return "No health score data available for the last hour";
    }
  }
}
