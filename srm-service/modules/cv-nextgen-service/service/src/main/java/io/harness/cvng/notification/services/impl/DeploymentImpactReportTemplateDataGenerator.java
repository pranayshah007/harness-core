/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.notification.services.impl;

import static io.harness.cvng.notification.utils.NotificationRuleConstants.ANALYSIS_DURATION;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.ANALYSIS_ENDED_AT;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.ANALYSIS_PIPELINE_NAME;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.ANALYSIS_STARTED_AT;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.CURRENT_HEALTH_SCORE;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.MS_HEALTH_REPORT;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.NO_METRIC_ASSIGNED_TO_MONITORED_SERVICE;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.PIPELINE_ID;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.PIPELINE_NAME;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.PIPELINE_URL;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.PIPELINE_URL_FORMAT;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.PLAN_EXECUTION_ID;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.SERVICE_HEALTH_SUMMARY;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.SLO_PERFORMANCE;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.SLO_PERFORMANCE_SECTION_FOR_ANALYSIS_REPORT;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.STAGE_STEP_ID;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.TOTAL_CE_COUNT;

import io.harness.cvng.core.beans.change.ChangeSummaryDTO;
import io.harness.cvng.core.beans.change.MSHealthReport;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.notification.beans.NotificationRuleType;
import io.harness.cvng.notification.channelDetails.CVNGNotificationChannelType;
import io.harness.cvng.notification.entities.MonitoredServiceNotificationRule;
import io.harness.cvng.notification.services.api.NotificationRuleTemplateDataGenerator;
import io.harness.cvng.notification.utils.NotificationRuleCommonUtils;

import java.util.Map;

public class DeploymentImpactReportTemplateDataGenerator extends MonitoredServiceTemplateDataGenerator<
    MonitoredServiceNotificationRule.MonitoredServiceDeploymentImpactReportCondition> {
  @Override
  public Map<String, String> getTemplateData(ProjectParams projectParams, Map<String, Object> entityDetails,
      MonitoredServiceNotificationRule.MonitoredServiceDeploymentImpactReportCondition condition,
      Map<String, String> notificationDataMap) {
    final Map<String, String> templateDataMap =
        super.getTemplateData(projectParams, entityDetails, condition, notificationDataMap);
    String baseUrl = NotificationRuleTemplateDataGenerator.getBaseUrl(
        this.getPortalUrl(), this.getVanityUrl(projectParams.getAccountIdentifier()));

    MSHealthReport msHealthReport = (MSHealthReport) entityDetails.get(MS_HEALTH_REPORT);
    ChangeSummaryDTO changeSummary = msHealthReport.getChangeSummary();
    templateDataMap.put(TOTAL_CE_COUNT, String.valueOf(changeSummary.getTotal().getCount()));
    templateDataMap.put(ANALYSIS_DURATION, String.valueOf(entityDetails.get(ANALYSIS_DURATION)));
    templateDataMap.put(ANALYSIS_PIPELINE_NAME, String.valueOf(entityDetails.get(ANALYSIS_PIPELINE_NAME)));
    templateDataMap.put(PIPELINE_URL, String.valueOf(getPipelineUrl(baseUrl, projectParams, entityDetails)));
    templateDataMap.put(ANALYSIS_STARTED_AT, String.valueOf(entityDetails.get(ANALYSIS_STARTED_AT)));
    templateDataMap.put(ANALYSIS_ENDED_AT, String.valueOf(entityDetails.get(ANALYSIS_ENDED_AT)));
    changeSummary.getCategoryCountMap().forEach((k, v) -> templateDataMap.put(k.name(), String.valueOf(v.getCount())));
    templateDataMap.put(CURRENT_HEALTH_SCORE, String.valueOf(msHealthReport.getCurrentHealthScore().getHealthScore()));
    templateDataMap.put(SERVICE_HEALTH_SUMMARY,
        NotificationRuleCommonUtils.getServiceHealthMessageForReport(msHealthReport.getCurrentHealthScore()));

    templateDataMap.put(SLO_PERFORMANCE,
        NotificationRuleCommonUtils.getSloPerformanceSectionForReport(msHealthReport.getAssociatedSLOsDetails(),
            clock.instant(), baseUrl, SLO_PERFORMANCE_SECTION_FOR_ANALYSIS_REPORT));

    return templateDataMap;
  }

  @Override
  public String getTemplateId(
      NotificationRuleType notificationRuleType, CVNGNotificationChannelType notificationChannelType) {
    return String.format("cvng_%s_report_%s", notificationRuleType.getTemplateSuffixIdentifier().toLowerCase(),
        notificationChannelType.getTemplateSuffixIdentifier().toLowerCase());
  }
  @Override
  protected String getHeaderMessage(Map<String, String> notificationDataMap) {
    return "Deployment Impact Analysis Report for the pipeline " + notificationDataMap.get(PIPELINE_NAME);
  }

  @Override
  protected String getTriggerMessage(
      MonitoredServiceNotificationRule.MonitoredServiceDeploymentImpactReportCondition condition) {
    return "No Trigger Message";
  }

  @Override
  protected String getAnomalousMetrics(ProjectParams projectParams, String identifier, long startTime,
      MonitoredServiceNotificationRule.MonitoredServiceDeploymentImpactReportCondition condition) {
    return NO_METRIC_ASSIGNED_TO_MONITORED_SERVICE;
  }

  private String getPipelineUrl(String baseUrl, ProjectParams projectParams, Map<String, Object> entityDetails) {
    return String.format(PIPELINE_URL_FORMAT, baseUrl, projectParams.getAccountIdentifier(),
        projectParams.getOrgIdentifier(), projectParams.getProjectIdentifier(), entityDetails.get(PIPELINE_ID),
        entityDetails.get(PLAN_EXECUTION_ID), entityDetails.get(STAGE_STEP_ID));
  }
}
