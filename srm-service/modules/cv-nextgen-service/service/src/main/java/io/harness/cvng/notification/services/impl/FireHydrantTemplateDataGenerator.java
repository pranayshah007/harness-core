/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.notification.services.impl;

import static io.harness.cvng.notification.utils.NotificationRuleConstants.CURRENT_HEALTH_SCORE;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.MODULE_NAME;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.MONITORED_SERVICE_NAME;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.MONITORED_SERVICE_URL_FORMAT;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.MS_HEALTH_REPORT;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.NO_METRIC_ASSIGNED_TO_MONITORED_SERVICE;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.SERVICE_HEALTH_SUMMARY;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.SLO_PERFORMANCE;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.SLO_PERFORMANCE_SECTION;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.TOTAL_CE_COUNT;

import io.harness.cvng.core.beans.change.ChangeSummaryDTO;
import io.harness.cvng.core.beans.change.MSHealthReport;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.notification.entities.FireHydrantReportNotificationCondition;
import io.harness.cvng.notification.services.api.NotificationRuleTemplateDataGenerator;
import io.harness.cvng.notification.utils.NotificationRuleCommonUtils;

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
    templateDataMap.put(CURRENT_HEALTH_SCORE, String.valueOf(msHealthReport.getCurrentHealthScore().getHealthScore()));
    templateDataMap.put(SERVICE_HEALTH_SUMMARY,
        NotificationRuleCommonUtils.getServiceHealthMessageForReport(msHealthReport.getCurrentHealthScore()));

    String baseUrl = NotificationRuleTemplateDataGenerator.getBaseUrl(
        this.getPortalUrl(), this.getVanityUrl(projectParams.getAccountIdentifier()));
    templateDataMap.put(SLO_PERFORMANCE,
        NotificationRuleCommonUtils.getSloPerformanceDetailsForReport(
            msHealthReport.getAssociatedSLOsDetails(), clock.instant(), baseUrl, SLO_PERFORMANCE_SECTION));

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
}
